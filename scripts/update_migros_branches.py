#!/usr/bin/env python3
"""Build a current roster of ordinary M/MM/MMM supermarkets from Migros' public store finder.

VOI, Migros Partner, M-Outlet, Daily, M-Express and other formats are excluded by
the official store type label in each public store-finder result.
"""
from __future__ import annotations

import argparse
import asyncio
import json
import re
import unicodedata
from collections import Counter
from datetime import date
from pathlib import Path

from playwright.async_api import async_playwright

START_URL = "https://filialen.migros.ch/de/"
FILTERED_URL = "https://filialen.migros.ch/de/filter:markets-%5Bsuper.voi.mp%5D/center:46.8182,8.2275/zoom:8/"
ALLOWED_TYPES = {"M", "MM", "MMM"}
EXCLUDED_NAME_RE = re.compile(r"\bdaily\b|\bm[\s-]?express\b|\bm[\s-]?outlet\b|\bvoi\b|\bteo\b|\bmigros partner\b", re.I)
MIN_EXPECTED = 650
MIN_FRIBOURG_EXPECTED = 25

# The three digit prefix is Migros' official store/cooperative code. It gives
# useful selectable areas across Switzerland; FR postal codes are reassigned
# to Fribourg even when they belong to the neighbouring Vaud cooperative.
REGION_BY_COOPERATIVE = {
    "002": "basel",
    "003": "aare",
    "004": "geneve",
    "006": "vaud-lausanne",
    "007": "zentralschweiz",
    "008": "neuchatel",
    "009": "ostschweiz-st-gallen",
    "012": "ticino",
    "013": "wallis",
    "015": "zuerich",
}
FRIBOURG_POSTCODE = re.compile(
    r"^(?:147[0-9]|154[0-9]|156[0-9]|161[1456789]|16[2-9][0-9]|"
    r"17(?:[0-8][0-9]|9[0-7])|3210|3280)$"
)
BERN_POSTCODE = re.compile(r"^(?:25[0-9]{2}|26[0-9]{2}|27[0-9]{2}|3[0-8][0-9]{2}|49[0-5][0-9])$")


def region_for_branch(official_store_id: str, address: str, old_branch: dict | None) -> str:
    old_region = (old_branch or {}).get("region", "").casefold()
    if old_region in {"fribourg", "freiburg"}:
        return "fribourg"
    if old_region == "bern":
        return "bern"

    postcode_match = re.search(r",\s*(\d{4})\s+", address)
    postcode = postcode_match.group(1) if postcode_match else ""
    cooperative = official_store_id[:3]
    if FRIBOURG_POSTCODE.fullmatch(postcode):
        return "fribourg"
    if cooperative == "003" and BERN_POSTCODE.fullmatch(postcode):
        return "bern"
    return REGION_BY_COOPERATIVE.get(cooperative, f"region-{cooperative or 'unbekannt'}")


def norm(value: str) -> str:
    value = unicodedata.normalize("NFKD", value.casefold())
    return re.sub(r"[^a-z0-9]+", " ", "".join(c for c in value if not unicodedata.combining(c))).strip()


async def main(output: Path, baseline: Path) -> None:
    old = json.loads(baseline.read_text(encoding="utf-8")).get("branches", [])
    old_by_name = {norm(item.get("name", "")): item for item in old if item.get("name")}
    old_by_address = {norm(item.get("address", "").split(",")[0]): item for item in old if item.get("address")}

    async with async_playwright() as pw:
        browser = await pw.chromium.launch(headless=True)
        page = await browser.new_page(locale="de-CH")
        page.set_default_timeout(20000)
        await page.goto(START_URL, wait_until="domcontentloaded", timeout=60000)
        await page.wait_for_timeout(3000)
        necessary_cookies = page.locator("#onetrust-reject-all-handler")
        if await necessary_cookies.is_visible():
            await necessary_cookies.click()
            await page.wait_for_load_state("domcontentloaded", timeout=20000)
        await page.wait_for_timeout(2500)
        # This exact official route was captured after selecting "Supermarkt" in the Migros finder.
        await page.goto(FILTERED_URL, wait_until="domcontentloaded", timeout=60000)
        await page.wait_for_timeout(4000)
        necessary_cookies = page.locator("#onetrust-reject-all-handler")
        if await necessary_cookies.is_visible():
            await necessary_cookies.click()
            await page.wait_for_timeout(1500)
        if await necessary_cookies.is_visible():
            raise RuntimeError("Der Migros-Cookiebanner blieb trotz Auswahl notwendiger Cookies geöffnet.")
        count_button = page.get_by_role("button", name=re.compile(r"\d+\s+STANDORTE ANZEIGEN", re.I))
        await count_button.wait_for(state="visible", timeout=20000)
        filtered_count_match = re.search(r"(\d+)", await count_button.inner_text())
        if not filtered_count_match:
            raise RuntimeError("Die Standortzahl im offiziellen Migros-Filialfinder fehlt.")
        filtered_count = int(filtered_count_match.group(1))
        await count_button.evaluate("el => el.click()")
        list_root = page.locator("#msrc-storefinder-storelist")
        await list_root.wait_for(state="visible", timeout=20000)
        store_list = list_root.locator("ul[role=list]")
        await store_list.wait_for(state="visible", timeout=20000)
        box = await store_list.bounding_box()
        if not box:
            raise RuntimeError("Die offizielle Filialliste ist nicht sichtbar.")
        await page.mouse.move(box["x"] + box["width"] / 2, box["y"] + box["height"] / 2)

        seen: dict[int, dict] = {}
        stagnant = 0
        previous_size = 0
        for _ in range(2500):
            rows = await list_root.locator('li[data-react-window-index]').evaluate_all(
                """els => els.map(li => {
                  const a=li.querySelector('a');
                  const img=li.querySelector('img');
                  const name=li.querySelector('[data-test-id*="item-name"]');
                  const address=li.querySelector('[data-test-id*="item-address"]');
                  const logo=li.querySelector('[data-test-id*="item-logo"]');
                  const testId=logo?.getAttribute('data-test-id')||'';
                  const id=testId.match(/item-logo-(\\d+)/)?.[1]||'';
                  return {
                    index:Number(li.getAttribute('data-react-window-index')),
                    slug:(a?.getAttribute('href')||'').split('/').filter(Boolean).pop()||'',
                    official_store_id:id,
                    name:(name?.textContent||'').trim(),
                    address:(address?.textContent||'').trim(),
                    store_type:(img?.getAttribute('alt')||'').trim()
                  };
                })"""
            )
            for row in rows:
                if row["index"] >= 0 and row["slug"] and row["official_store_id"]:
                    seen[row["index"]] = row
            if len(seen) >= filtered_count:
                break
            if len(seen) == previous_size:
                stagnant += 1
            else:
                stagnant = 0
                previous_size = len(seen)
            if stagnant > 20:
                break
            await page.mouse.wheel(0, 500)
            await page.wait_for_timeout(450)
        await browser.close()

    if len(seen) != filtered_count:
        raise RuntimeError(
            f"Der offizielle Filialfinder meldet {filtered_count} Standorte, "
            f"aber nur {len(seen)} konnten aus der Liste gelesen werden."
        )

    branches = []
    for row in seen.values():
        store_type = row["store_type"]
        if store_type not in ALLOWED_TYPES or EXCLUDED_NAME_RE.search(row["name"]):
            continue
        old_branch = old_by_name.get(norm(row["name"])) or old_by_address.get(norm(row["address"].split(",")[0]))
        slug = row["slug"]
        branch_id = old_branch.get("id") if old_branch else f"migros-{row['official_store_id']}"
        region = region_for_branch(row["official_store_id"], row["address"], old_branch)
        branch = {
            "id": branch_id,
            "name": row["name"],
            "address": row["address"],
            "region": region,
            "store_type": store_type,
            "official_store_id": row["official_store_id"],
            "official_url": f"https://filialen.migros.ch/de/{slug}/",
        }
        if old_branch and old_branch.get("coordinates"):
            branch["coordinates"] = old_branch["coordinates"]
        branches.append(branch)

    branches.sort(key=lambda item: (item["region"], item["name"].casefold(), item["official_store_id"]))
    region_counts = Counter(item["region"] for item in branches)
    if len(branches) < MIN_EXPECTED or any(item["store_type"] not in ALLOWED_TYPES for item in branches):
        raise RuntimeError(f"Unplausible Migros-Supermarktliste: {len(branches)} reguläre Filialen.")
    if region_counts.get("fribourg", 0) < MIN_FRIBOURG_EXPECTED:
        raise RuntimeError(
            f"Fribourg-Abdeckung unplausibel: nur {region_counts.get('fribourg', 0)} Filialen "
            f"(mindestens {MIN_FRIBOURG_EXPECTED} erwartet)."
        )

    payload = {
        "retailer": "Migros",
        "updated_on": date.today().isoformat(),
        "source": START_URL,
        "source_filter": "Offizieller Filialfinder: Supermarkt; Store-Typ M, MM oder MMM",
        "excluded_formats": ["VOI", "Migros Partner", "M-Outlet", "Daily", "M-Express", "weitere Nicht-Supermarkt-Formate"],
        "official_finder_matches": filtered_count,
        "regular_supermarkets": len(branches),
        "branches": branches,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(
        f"Erfasst: {len(branches)} reguläre M/MM/MMM-Supermärkte "
        f"aus {filtered_count} offiziellen Finder-Treffern. "
        f"Formate: {dict(Counter(item['store_type'] for item in branches))}; alle Finder-Typen: {dict(Counter(row['store_type'] for row in seen.values()))}"
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=Path("data/branches.json"))
    parser.add_argument("--baseline", type=Path, default=Path("data/branches.json"))
    args = parser.parse_args()
    asyncio.run(main(args.output, args.baseline))
