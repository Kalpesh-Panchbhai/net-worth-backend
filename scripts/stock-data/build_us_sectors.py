#!/usr/bin/env python3
"""
Build the US (S&P 500) sector universe files, mirroring the India NSE setup.

Source: datasets/s-and-p-500-companies constituents.csv (Symbol + GICS Sector).
Yahoo Finance uses '-' for share-class dots (BRK.B -> BRK-B), so we normalise.

Outputs (mirrors src/main/resources/sectors/*.txt for India):
  src/main/resources/sectors_us/<gics_sector_key>.txt   # one ticker per line
  src/main/resources/us_sp500.txt                        # full universe

Usage:
  python3 scripts/build_us_sectors.py                    # fetch fresh from web
  python3 scripts/build_us_sectors.py --csv /tmp/sp500.csv   # use a local CSV
"""
from __future__ import annotations

import argparse
import csv
import io
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SECTORS_US_DIR = ROOT / "src" / "main" / "resources" / "sectors_us"
UNIVERSE_FILE = ROOT / "src" / "main" / "resources" / "us_sp500.txt"

CONSTITUENTS_URL = (
    "https://raw.githubusercontent.com/datasets/"
    "s-and-p-500-companies/main/data/constituents.csv"
)

# GICS sector display name -> file key (snake_case), matching India's style.
SECTOR_KEY = {
    "Information Technology": "information_technology",
    "Health Care": "health_care",
    "Financials": "financials",
    "Consumer Discretionary": "consumer_discretionary",
    "Communication Services": "communication_services",
    "Industrials": "industrials",
    "Consumer Staples": "consumer_staples",
    "Energy": "energy",
    "Utilities": "utilities",
    "Real Estate": "real_estate",
    "Materials": "materials",
}


def yahoo_ticker(symbol: str) -> str:
    """Normalise a constituent symbol to a Yahoo Finance ticker."""
    return symbol.strip().upper().replace(".", "-")


def load_rows(csv_path: str | None) -> list[dict]:
    if csv_path:
        text = Path(csv_path).read_text(encoding="utf-8")
    else:
        req = urllib.request.Request(
            CONSTITUENTS_URL, headers={"User-Agent": "Mozilla/5.0"}
        )
        with urllib.request.urlopen(req, timeout=30) as resp:
            text = resp.read().decode("utf-8")
    return list(csv.DictReader(io.StringIO(text)))


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--csv", default="", help="local constituents.csv (optional)")
    args = p.parse_args()

    rows = load_rows(args.csv or None)
    if not rows:
        sys.exit("No constituent rows loaded")

    by_sector: dict[str, list[str]] = {}
    universe: list[str] = []
    unknown: set[str] = set()

    for r in rows:
        sector = (r.get("GICS Sector") or "").strip()
        symbol = (r.get("Symbol") or "").strip()
        if not sector or not symbol:
            continue
        key = SECTOR_KEY.get(sector)
        if not key:
            unknown.add(sector)
            continue
        tkr = yahoo_ticker(symbol)
        by_sector.setdefault(key, []).append(tkr := tkr)
        universe.append(tkr)

    if unknown:
        print(f"  WARNING: unmapped GICS sectors: {sorted(unknown)}")

    SECTORS_US_DIR.mkdir(parents=True, exist_ok=True)
    for key, tickers in sorted(by_sector.items()):
        tickers = sorted(set(tickers))
        (SECTORS_US_DIR / f"{key}.txt").write_text("\n".join(tickers) + "\n", encoding="utf-8")
        print(f"  {key:<26} {len(tickers):>3} stocks")

    uni = sorted(set(universe))
    UNIVERSE_FILE.write_text("\n".join(uni) + "\n", encoding="utf-8")
    print(f"\n  Sectors: {len(by_sector)}")
    print(f"  Universe: {len(uni)} unique tickers -> {UNIVERSE_FILE.relative_to(ROOT)}")
    print(f"  Sector dir: {SECTORS_US_DIR.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
