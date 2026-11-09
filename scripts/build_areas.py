"""OSM PBF -> GraphHopper custom areas (parks.geojson, rivers.geojson).

공원(leisure=park) 20m, 하천(waterway=river|stream|canal, natural=water) 50m 버퍼.
사용: python -I build_areas.py <in.osm.pbf> <out_dir>
"""
import json, math, sys
from pathlib import Path
import osmium
import shapely
from shapely import wkb

RULES = [  # (kind, 태그 판정, 버퍼 m)
    ("park", lambda t: t.get("leisure") == "park", 20),
    ("river", lambda t: t.get("waterway") in ("river", "stream", "canal") or t.get("natural") == "water", 50),
]

def main(src, out_dir):
    fab = osmium.geom.WKBFactory()
    feats = {kind: [] for kind, _, _ in RULES}
    for o in osmium.FileProcessor(src).with_areas().with_filter(osmium.filter.EntityFilter(osmium.osm.WAY | osmium.osm.AREA)):
        for kind, match, buf in RULES:
            if not match(o.tags):
                continue
            if o.is_way() and o.is_closed():
                continue  # 닫힌 way는 area로 한 번 더 들어온다 (중복 방지)
            try:
                g = wkb.loads(fab.create_multipolygon(o) if o.is_area() else fab.create_linestring(o), hex=True)
            except Exception:  # 닫히지 않은 경계·위치 없는 노드
                continue
            scale = [math.cos(math.radians(g.centroid.y)) * 111320, 111320]  # 도 -> m (서울 위도 근사)
            back = shapely.transform(shapely.transform(g, lambda c: c * scale).buffer(buf), lambda c: c / scale)
            feats[kind].append((o.tags.get("name"), back))
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    for kind, items in feats.items():
        fc = {"type": "FeatureCollection", "features": [
            {"type": "Feature", "id": f"{kind}_{i}", "properties": {"kind": kind, "name": name},
             "geometry": json.loads(json.dumps(g.__geo_interface__))}
            for i, (name, g) in enumerate(items)]}
        (out / f"{kind}s.geojson").write_text(json.dumps(fc, ensure_ascii=False))
        print(kind, len(items))

if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
