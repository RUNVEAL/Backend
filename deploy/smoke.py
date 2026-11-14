#!/usr/bin/env python3
"""배포 스모크 테스트. 표준 라이브러리만 쓴다.

사용: python3 deploy/smoke.py <BASE_URL> [--version 2026-10-27] [--spoof-check]
체크마다 PASS/FAIL 한 줄. 하나라도 FAIL 이면 exit 1.
좌표는 서교동 테스트 지점 고정 (서울 지도에서도 안쪽).
"""

import argparse
import json
import math
import random
import sys
import time
import urllib.error
import urllib.request

START = {"lat": 37.5552, "lng": 126.9236}
STEP_M = 25  # 10초 간격 × 6분 30초/km ≈ 25.6m


def call(base, method, path, body=None, headers=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(base + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            raw = r.read()
            return r.status, dict(r.headers), raw
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers), e.read()


def resample(path, step):
    """[lng, lat] 경로를 step(m) 간격 점으로 다시 뽑는다."""
    out, carry = [path[0]], 0.0
    for (x1, y1), (x2, y2) in zip(path, path[1:]):
        seg = math.hypot((x2 - x1) * 111_320 * math.cos(math.radians(y1)), (y2 - y1) * 111_320)
        carry += seg
        if carry >= step:
            out.append([x2, y2])
            carry = 0.0
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--version")
    ap.add_argument("--spoof-check", action="store_true")
    a = ap.parse_args()
    base = a.base.rstrip("/")
    failed = []

    def check(name, ok, detail=""):
        print(("PASS " if ok else "FAIL ") + name + (f"  ({detail})" if detail and not ok else ""))
        if not ok:
            failed.append(name)

    # 첫 배포는 HTTPS 인증서 발급(수십 초)이 끝나야 연결된다 → 연결 오류만 60초까지 재시도
    for attempt in range(12):
        try:
            s, _, raw = call(base, "GET", "/api/v1/meta")
            break
        except urllib.error.URLError:
            if attempt == 11:
                raise
            time.sleep(5)
    meta = json.loads(raw) if s == 200 else {}
    version = a.version or meta.get("mapVersions", {}).get("current")
    current = meta.get("mapVersions", {}).get("current")
    check("meta_ok", s == 200 and (a.version is None or current == a.version), f"{s} current={current}")

    s, _, raw = call(base, "POST", "/api/v1/routes",
                     {"mapVersion": version, "start": START, "distanceM": 3000})
    routes = json.loads(raw).get("routes", []) if s == 200 else []
    check("routes_ok", s == 200 and len(routes) >= 1, f"{s} routes={len(routes)}")

    if routes:
        points = resample(routes[0]["path"], STEP_M)
        s, _, raw = call(base, "POST", "/api/v1/match",
                         {"mapVersion": version, "purpose": "LIVE", "points": points})
        ids = json.loads(raw).get("segmentIds", []) if s == 200 else []
        check("match_ok", s == 200 and len(ids) >= 1, f"{s} points={len(points)} segments={len(ids)}")
    else:
        check("match_ok", False, "routes 가 없어 건너뜀")

    hidden = {p: call(base, "GET", p)[0] for p in ("/livez", "/readyz", "/actuator/health")}
    check("internal_hidden", all(c == 404 for c in hidden.values()), str(hidden))

    s, h, raw = call(base, "GET", f"/maps/{version}/manifest.json")
    cache = h.get("Cache-Control", "")
    ok = s == 200 and "immutable" in cache and json.loads(raw).get("version") == version
    check("maps_public", ok, f"{s} cache={cache!r}")

    if a.spoof_check:  # 마지막에: /routes 한도(분당 10)를 다 쓴다
        codes = []
        for _ in range(12):
            fake = f"203.0.113.{random.randint(1, 254)}"
            codes.append(call(base, "POST", "/api/v1/routes",
                              {"mapVersion": version, "start": START, "distanceM": 1000},
                              {"X-Forwarded-For": fake})[0])
        check("rate_limit_not_spoofable", 429 in codes, str(codes))

    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
