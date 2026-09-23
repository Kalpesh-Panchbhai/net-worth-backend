#!/usr/bin/env python3
"""
Download daily OHLCV for Nifty 500 stocks from 2000-01-01 to today.

Requirements:
  - Universe: src/main/resources/nifty500.txt (one ticker per line, .NS suffix)
  - Range: 2000-01-01 -> today, daily, auto_adjust=True
  - Skip delisted: last bar older than STALE_DAYS calendar days -> not saved
  - Cache: strategies/data/cache/<TICKER>_NS_1d.csv
  - Resume: skip already-cached files (unless --force)
  - Parallel downloads via ThreadPoolExecutor

Usage:
  python download_nifty500.py                 # download missing only
  python download_nifty500.py --force         # re-download all
  python download_nifty500.py --workers 16    # tune concurrency (default 12)
"""
import argparse
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timedelta
from pathlib import Path

import yfinance as yf

ROOT = Path(__file__).resolve().parent.parent
CACHE_DIR = ROOT / "strategies" / "data" / "cache"
NIFTY_FILE = ROOT / "src" / "main" / "resources" / "nifty500.txt"
START_DATE = "2000-01-01"
MIN_BARS = 5
STALE_DAYS = 30


def ticker_to_filename(ticker: str) -> str:
    return (
        ticker.replace(".NS", "_NS")
        .replace(".BO", "_BO")
        .replace(".", "_")
        .replace("&", "_")
        + "_1d.csv"
    )


def clean_dataframe(df):
    if df is None or df.empty:
        return df
    price_cols = ["Open", "High", "Low", "Close"]
    df = df[(df[price_cols] >= 0).all(axis=1)].copy()
    df.loc[:, "High"] = df[price_cols].max(axis=1)
    df.loc[:, "Low"] = df[price_cols].min(axis=1)
    is_weekend = df.index.dayofweek.isin([5, 6])
    is_zero_vol = df["Volume"] == 0
    df = df[~(is_weekend & is_zero_vol)]
    return df


def download_one(ticker: str):
    """Returns (ticker, bars, first, last, status)."""
    try:
        df = yf.Ticker(ticker).history(
            start=START_DATE, interval="1d", auto_adjust=True, timeout=30
        )
        if df is None or df.empty:
            return (ticker, 0, None, None, "no_data")

        df = clean_dataframe(df)
        if df is None or df.empty or len(df) < MIN_BARS:
            return (ticker, len(df) if df is not None else 0, None, None, "too_few_bars")

        first_date = df.index[0]
        last_date = df.index[-1]
        cutoff = datetime.now(tz=last_date.tzinfo) - timedelta(days=STALE_DAYS)
        if last_date < cutoff:
            return (
                ticker,
                len(df),
                first_date.strftime("%Y-%m-%d"),
                last_date.strftime("%Y-%m-%d"),
                "delisted",
            )

        df.index = df.index.strftime("%Y-%m-%d")
        df = df[["Open", "High", "Low", "Close", "Volume"]]
        df.index.name = "Date"
        df.to_csv(CACHE_DIR / ticker_to_filename(ticker))

        return (
            ticker,
            len(df),
            first_date.strftime("%Y-%m-%d"),
            last_date.strftime("%Y-%m-%d"),
            "ok",
        )
    except Exception as e:
        return (ticker, 0, None, None, f"error: {str(e)[:100]}")


def run_pool(tickers, workers):
    results = {"ok": [], "no_data": [], "too_few_bars": [], "delisted": [], "error": []}
    total = len(tickers)
    done = 0
    t0 = time.time()
    with ThreadPoolExecutor(max_workers=workers) as ex:
        futures = {ex.submit(download_one, t): t for t in tickers}
        for fut in as_completed(futures):
            res = fut.result()
            status = res[4]
            key = "error" if status.startswith("error") else status
            results[key].append(res)
            done += 1
            if done % 25 == 0 or done == total:
                el = time.time() - t0
                rate = done / el if el else 0
                eta = (total - done) / rate if rate else 0
                print(
                    f"  [{done}/{total}] {el:.0f}s "
                    f"ok={len(results['ok'])} "
                    f"delisted={len(results['delisted'])} "
                    f"nodata={len(results['no_data'])} "
                    f"err={len(results['error'])} "
                    f"eta={eta:.0f}s"
                )
    return results


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--force", action="store_true", help="re-download all")
    p.add_argument("--workers", type=int, default=12, help="parallel downloads")
    args = p.parse_args()

    if not NIFTY_FILE.exists():
        sys.exit(f"missing {NIFTY_FILE}")
    CACHE_DIR.mkdir(parents=True, exist_ok=True)

    symbols = [s.strip() for s in NIFTY_FILE.read_text().splitlines() if s.strip()]

    if args.force:
        to_download = symbols
        print(f"Force mode: {len(to_download)} stocks")
    else:
        to_download, skipped = [], 0
        for s in symbols:
            f = CACHE_DIR / ticker_to_filename(s)
            if f.exists() and f.stat().st_size > 100:
                skipped += 1
            else:
                to_download.append(s)
        print(f"Cached: {skipped} | To download: {len(to_download)}")

    if not to_download:
        return

    print(f"Range: {START_DATE} -> today | Workers: {args.workers}")
    print(f"Cache: {CACHE_DIR}\n")

    t0 = time.time()
    results = run_pool(to_download, args.workers)

    # Retry errors once with smaller pool
    errs = results["error"][:]
    if errs:
        print(f"\nRetrying {len(errs)} errored...")
        results["error"] = []
        retry_tickers = [r[0] for r in errs]
        retry_res = run_pool(retry_tickers, max(2, args.workers // 3))
        for k, v in retry_res.items():
            results[k].extend(v)

    elapsed = time.time() - t0
    print(f"\n{'='*70}")
    print(f"  DONE in {elapsed:.0f}s")
    print(f"{'='*70}")
    print(f"  OK (saved):           {len(results['ok'])}")
    print(f"  Delisted/suspended:   {len(results['delisted'])}")
    print(f"  No data (not listed): {len(results['no_data'])}")
    print(f"  Too few bars:         {len(results['too_few_bars'])}")
    print(f"  Errors:               {len(results['error'])}")
    print(f"{'='*70}")

    if results["delisted"]:
        print(f"\nDelisted (last trade >{STALE_DAYS}d ago):")
        for t, b, first, last, _ in sorted(results["delisted"], key=lambda x: x[3] or ""):
            print(f"  {t:25s} {b:>5} bars  {first} -> {last}")

    if results["no_data"]:
        print(f"\nNo data:")
        for t, *_ in sorted(results["no_data"]):
            print(f"  {t}")

    if results["error"]:
        print(f"\nErrors:")
        for t, _, _, _, s in sorted(results["error"]):
            print(f"  {t}: {s}")

    summary = CACHE_DIR / "download_summary.txt"
    with summary.open("w") as f:
        f.write(f"Download date: {datetime.now():%Y-%m-%d %H:%M}\n")
        f.write(f"Elapsed: {elapsed:.0f}s\n")
        f.write(f"OK: {len(results['ok'])}\n")
        f.write(f"Delisted: {len(results['delisted'])}\n")
        f.write(f"No data: {len(results['no_data'])}\n")
        f.write(f"Errors: {len(results['error'])}\n\n")
        if results["delisted"]:
            f.write("DELISTED:\n")
            for t, b, first, last, _ in sorted(results["delisted"], key=lambda x: x[0]):
                f.write(f"  {t} ({b} bars, {first} -> {last})\n")
        if results["no_data"]:
            f.write("\nNO DATA:\n")
            for t, *_ in sorted(results["no_data"]):
                f.write(f"  {t}\n")
        if results["error"]:
            f.write("\nERRORS:\n")
            for t, _, _, _, s in sorted(results["error"]):
                f.write(f"  {t}: {s}\n")
    print(f"\nSummary: {summary}")


if __name__ == "__main__":
    main()
