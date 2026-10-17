#!/usr/bin/env python3
"""Export old (debuggable) Pokémon Card Finder Android preferences to JSON backup v1.

Example, BEFORE uninstalling the old APK:
    python3 scripts/migrate_legacy_android_prefs.py --adb
    # Then copy the resulting JSON onto the phone and import in the new APK.

Requires Android platform-tools (adb), USB debugging enabled and an unlocked device.
The script only uses 'run-as' on the app's private, debuggable data; no root or server.
It will fail safely on non-debuggable builds.
"""
import argparse
import json
import subprocess
import sys
import time
from pathlib import Path
from xml.etree import ElementTree as ET

PACKAGE = "ch.pokemoncardfinder.local"


def parse_prefs_xml(data: bytes) -> dict:
    root = ET.fromstring(data)
    if root.tag != "map":
        raise ValueError("The input is not an Android SharedPreferences XML file")
    exported = {}
    types = {"string": "string", "boolean": "boolean", "int": "int",
             "long": "long", "float": "float", "set": "string_set"}
    for field in root:
        key = field.get("name")
        if not key or field.tag not in types:
            continue
        if field.tag == "string":
            value = field.text or ""
        elif field.tag == "boolean":
            if field.get("value") not in {"true", "false"}:
                raise ValueError(f"Invalid boolean preference: {key}")
            value = field.get("value") == "true"
        elif field.tag in {"int", "long"}:
            value = int(field.attrib["value"])
        elif field.tag == "float":
            value = float(field.attrib["value"])
        else:
            value = []
            for member in field:
                if member.tag != "string":
                    raise ValueError(f"Unexpected string-set element: {key}")
                value.append(member.text or "")
        exported[key] = {"type": types[field.tag], "value": value}
    return exported


def adb_run(*args: str) -> bytes:
    result = subprocess.run(["adb", *args], stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, check=False)
    if result.returncode:
        msg = result.stderr.decode("utf-8", errors="replace").strip()
        raise RuntimeError(msg or "ADB did not permit reading the app's private data")
    return result.stdout


def collect_from_adb() -> tuple[dict, dict]:
    listing = adb_run("shell", "run-as", PACKAGE, "ls", "shared_prefs")
    filenames = set(listing.decode("utf-8", errors="replace").splitlines())
    migros_names = ["MainActivity.xml", "ch.pokemoncardfinder.local.MainActivity.xml"]
    selected = next((name for name in migros_names if name in filenames), None)
    if selected is None:
        raise RuntimeError("Migros preferences not found; available files: "
                           + ", ".join(sorted(filenames)))
    migros = parse_prefs_xml(adb_run("exec-out", "run-as", PACKAGE,
                                     "cat", "shared_prefs/" + selected))
    coop = {}
    if "coop_finder.xml" in filenames:
        coop = parse_prefs_xml(adb_run("exec-out", "run-as", PACKAGE,
                                       "cat", "shared_prefs/coop_finder.xml"))
    return migros, coop


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    choice = parser.add_mutually_exclusive_group(required=True)
    choice.add_argument("--adb", action="store_true",
                        help="Read old app's preferences directly from connected Android device")
    choice.add_argument("--migros", type=Path,
                        help="Read a previously extracted Migros SharedPreferences XML")
    parser.add_argument("--coop", type=Path,
                        help="Optional coop_finder.xml when using --migros")
    parser.add_argument("--output", type=Path,
                        default=Path("pokemon-card-finder-legacy-backup.json"))
    args = parser.parse_args()
    if args.adb and args.coop:
        parser.error("--coop is only valid with --migros")
    try:
        if args.adb:
            migros, coop = collect_from_adb()
        else:
            migros = parse_prefs_xml(args.migros.read_bytes())
            coop = parse_prefs_xml(args.coop.read_bytes()) if args.coop else {}
        if not migros and not coop:
            raise ValueError("Nothing to back up")
        doc = {"format": "pokemon-card-finder-backup",
               "format_version": 1, "created_at_ms": int(time.time() * 1000),
               "migros": migros, "coop": coop}
        # 'x' prevents unintentionally overwriting a precious earlier backup.
        with args.output.open("x", encoding="utf-8") as fh:
            json.dump(doc, fh, ensure_ascii=False, indent=2)
        print(f"Backup saved: {args.output} (Migros {len(migros)} keys, Coop {len(coop)} keys)")
        print("Keep it safe. Copy the JSON to the phone, then import using the new APK.")
        return 0
    except (OSError, ValueError, ET.ParseError, RuntimeError) as exc:
        print(f"Backup failed: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
