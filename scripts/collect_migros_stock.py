#!/usr/bin/env python3
"""Read Migros' public product availability dialog for every configured branch.

This is a UI collector, not an undocumented inventory API. It writes the feed only
when every configured product/branch pair was parsed successfully.
"""
from __future__ import annotations
import asyncio, json, os, re, sys, tempfile
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo
from playwright.async_api import TimeoutError as PlaywrightTimeoutError, async_playwright

ROOT = Path(__file__).resolve().parents[1]
BRANCH_FILE = ROOT / 'data/branches.json'
CATALOG_FILE = ROOT / 'data/migros_pokemon_catalog_fribourg_20261011.json'
OUTPUT = ROOT / 'data/stock-latest.json'
BASE = 'https://www.migros.ch/de/product/'


def norm(s: str) -> str:
    return re.sub(r'[^a-z0-9]+', ' ', s.casefold().replace('ä','a').replace('ö','o').replace('ü','u').replace('é','e').replace('è','e').replace('ê','e')).strip()


def parse_dialog(text: str, branch: dict) -> dict | None:
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    name, address = norm(branch['name']), norm(branch['address'].split(',')[0])
    index = next((i for i, line in enumerate(lines) if norm(line) == name or name in norm(line)), None)
    if index is None and len(address) > 5:
        index = next((i for i, line in enumerate(lines) if norm(line).startswith(address)), None)
    if index is None:
        return None
    raw = ' | '.join(lines[index:index+5])
    if re.search(r'nicht auf lager', raw, re.I):
        return {'status':'out_of_stock','approximate_quantity':None,'source_text':'Nicht auf Lager'}
    match = re.search(r'(?:nur\s*)?ca\.?\s*(\d+)\s*auf lager', raw, re.I) or re.search(r'(\d+)\s*auf lager', raw, re.I)
    if match:
        qty = int(match.group(1))
        return {'status':'estimated_in_stock','approximate_quantity':qty,'source_text':f'Ca. {qty} auf Lager'}
    if re.search(r'\bauf lager\b|verfügbar', raw, re.I):
        return {'status':'in_stock','approximate_quantity':None,'source_text':'Auf Lager (Menge nicht angegeben)'}
    return None


async def capture_product(page, product, branches):
    await page.goto(BASE + product['id'], wait_until='domcontentloaded', timeout=60000)
    button = page.get_by_role('button', name='Verfügbarkeit in Filialen prüfen')
    try:
        await button.wait_for(state='visible', timeout=30000)
    except PlaywrightTimeoutError as exc:
        # Migros may serve a changed page or an access notice to automated clients.
        # Record enough public page context in Actions to diagnose it without guessing.
        title = await page.title()
        body = (await page.locator('body').inner_text(timeout=5000)).strip()
        print(f'Migros-Seitendiagnose: URL={page.url!r}; Titel={title!r}; Text={body[:1800]!r}', flush=True)
        raise RuntimeError('Migros-Verfügbarkeitsbutton nach Laden der Produktseite nicht gefunden.') from exc
    await button.click(timeout=20000)
    modal = page.locator('#bottomSmallModal')
    field = page.locator('#store-search-autocomplete-form-input')
    await field.wait_for(state='visible', timeout=15000)
    seen = {}
    text = await modal.inner_text(timeout=15000)

    def scrape(body):
        for branch in branches:
            if branch['id'] in seen:
                continue
            value = parse_dialog(body, branch)
            if value:
                seen[branch['id']] = value

    scrape(text)
    searches = 0
    while len(seen) < len(branches):
        branch = next((b for b in branches if b['id'] not in seen), None)
        if branch is None:
            break
        await field.fill(branch['address'], timeout=12000)
        searches += 1
        previous = text
        for _ in range(60):
            await page.wait_for_timeout(250)
            text = await modal.inner_text(timeout=12000)
            if text != previous:
                scrape(text)
                # Give the UI a short settle period so a delayed availability response is read too.
                if branch['id'] in seen:
                    await page.wait_for_timeout(350)
                    text = await modal.inner_text(timeout=12000)
                    scrape(text)
                    break
        if branch['id'] not in seen:
            raise RuntimeError(f"{product['id']}: keine eindeutige Migros-Angabe für {branch['name']}")
        if searches > len(branches):
            raise RuntimeError('Abbruch: Filialabfragen liefen in eine Schleife.')
    return seen


async def main():
    branches = json.loads(BRANCH_FILE.read_text())['branches']
    catalog = json.loads(CATALOG_FILE.read_text())
    # The catalog uses `in_store_assortment`; older snapshots may use `store`.
    # Do not silently discard every product when the schema uses the newer key.
    products = [
        p for p in catalog.get('products', [])
        if p.get('in_store_assortment', p.get('store', False))
    ]
    if not branches or not products:
        raise RuntimeError('Filialliste oder Filialproduktkatalog ist leer.')
    # Existing regions are inferred from branch IDs; all other/new IDs are assigned
    # to the group explicitly stored on the roster entry or to "weitere".
    grouped = {}
    for b in branches:
        key = b.get('region') or ('fribourg' if b['id'] in {x['id'] for x in json.loads((ROOT/'data/migros_pokemon_stock_fribourg_20261011.json').read_text())['branches']} else 'bern')
        grouped.setdefault(key, []).append(b)
    tz = ZoneInfo('Europe/Zurich')
    now = datetime.now(tz).replace(microsecond=0)
    stamp = now.isoformat()
    async with async_playwright() as pw:
        browser = await pw.chromium.launch(headless=True)
        page = await browser.new_page(locale='de-CH')
        page.set_default_timeout(20000)
        product_results = {}
        for product in products:
            print(f"Prüfe {product['name']} ({len(branches)} Filialen)…", flush=True)
            all_records = {}
            for region, stores in grouped.items():
                values = await capture_product(page, product, stores)
                all_records.update({bid:{**value,'checked_at_local_approx':stamp} for bid,value in values.items()})
            if set(all_records) != {b['id'] for b in branches}:
                raise RuntimeError(f"{product['id']}: Filialabdeckung unvollständig")
            product_results[product['id']] = {'name':product['name'],'checked_at_local_approx':stamp,'branches':all_records}
        await browser.close()
    regions = {}
    for region, stores in grouped.items():
        regional_products = {}
        for p in products:
            records = product_results[p['id']]['branches']
            regional_products[p['id']] = {'name':p['name'],'checked_at_local_approx':stamp,'branches':{b['id']:records[b['id']] for b in stores}}
        regions[region] = {'checked_at_local_approx':stamp,'branches':stores,'products':regional_products}
    payload = {'retailer':'Migros','checked_on':now.date().isoformat(),'checked_at_local_approx':stamp,'source_url_pattern':BASE+'{product_id}','capture_method':'Öffentliche Migros-Produktseite > Verfügbarkeit in Filialen prüfen; alle in data/branches.json eingetragenen Filialen einzeln abgedeckt; ohne Login.','source_notice':'Migros-Verfügbarkeitsdialog: Bestände sind Momentaufnahmen und ohne Gewähr. Ca.-Mengen sind Schätzwerte.','quantity_precision':'Nur von Migros angezeigte ungefähre Mengen werden übernommen.','total_branches':len(branches),'total_products':len(products),'checked_pairs':len(branches)*len(products),'regions':regions}
    # Atomic replacement prevents the app from receiving a partial capture.
    with tempfile.NamedTemporaryFile('w',encoding='utf-8',dir=OUTPUT.parent,delete=False) as f:
        json.dump(payload,f,ensure_ascii=False,indent=2); f.write('\n'); tmp=f.name
    os.replace(tmp,OUTPUT)
    print(f"Vollständig: {len(branches)} Filialen × {len(products)} Produkte = {len(branches)*len(products)} geprüfte Kombinationen.")

if __name__ == '__main__':
    try:
        asyncio.run(main())
    except Exception as exc:
        print(f"ERFASSUNG ABGEBROCHEN – vorhandener Feed bleibt erhalten: {exc}", file=sys.stderr)
        raise
