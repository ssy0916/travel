#!/usr/bin/env python3
"""Measure concurrent trip-list API latency against a running local server."""

import argparse
import json
import statistics
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed


def percentile(values: list[float], percentile_value: float) -> float:
    ordered = sorted(values)
    index = min(len(ordered) - 1, round((len(ordered) - 1) * percentile_value))
    return ordered[index]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="http://127.0.0.1:3200/api/travel/trips?page=0&size=10")
    parser.add_argument("--requests", type=int, default=200)
    parser.add_argument("--concurrency", type=int, default=20)
    parser.add_argument("--device-id", default="load-test-device")
    args = parser.parse_args()
    if args.requests <= 0 or args.concurrency <= 0:
        parser.error("--requests and --concurrency must be positive")

    def request_once(_: int) -> tuple[float, str | None]:
        request = urllib.request.Request(
            args.url,
            headers={"X-Device-Id": args.device_id, "Accept": "application/json"},
        )
        started = time.perf_counter()
        try:
            with urllib.request.urlopen(request, timeout=15) as response:
                body = response.read()
                if response.status != 200:
                    return time.perf_counter() - started, f"HTTP {response.status}"
                payload = json.loads(body)
                if not isinstance(payload, dict) or payload.get("success") is not True:
                    return time.perf_counter() - started, f"API error: {payload!r}"
                return time.perf_counter() - started, None
        except (urllib.error.URLError, TimeoutError, ValueError) as error:
            return time.perf_counter() - started, str(error)

    warmup_count = min(args.requests, args.concurrency)
    with ThreadPoolExecutor(max_workers=args.concurrency) as executor:
        warmup = list(executor.map(request_once, range(warmup_count)))
    warmup_errors = [error for _, error in warmup if error]
    if warmup_errors:
        print(json.dumps({"warmup_failures": warmup_errors[:5]}, ensure_ascii=False))
        return 1

    started = time.perf_counter()
    latencies = []
    errors = []
    with ThreadPoolExecutor(max_workers=args.concurrency) as executor:
        futures = [executor.submit(request_once, index) for index in range(args.requests)]
        for future in as_completed(futures):
            elapsed, error = future.result()
            latencies.append(elapsed)
            if error:
                errors.append(error)

    duration = time.perf_counter() - started
    result = {
        "requests": args.requests,
        "concurrency": args.concurrency,
        "warmup_requests": warmup_count,
        "successes": len(latencies) - len(errors),
        "failures": len(errors),
        "throughput_rps": round(args.requests / duration, 2),
        "latency_ms": {
            "p50": round(percentile(latencies, 0.50) * 1000, 2),
            "p95": round(percentile(latencies, 0.95) * 1000, 2),
            "max": round(max(latencies) * 1000, 2),
            "mean": round(statistics.fmean(latencies) * 1000, 2),
        },
        "sample_errors": errors[:5],
    }
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
