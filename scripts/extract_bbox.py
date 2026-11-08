"""bbox 안에 노드가 하나라도 있는 way 전부 + 참조 노드만 남긴 PBF를 만든다 (fixture · 서울 추출 공용).

사용: python -I extract_bbox.py <in.osm.pbf> <out.osm.pbf> <south> <west> <north> <east>
  서교동 fixture: 37.5444 126.9100 37.5660 126.9372   (서교동 37.5552,126.9236 ± 약 1.2km)
"""
import sys
import osmium

src, dst = sys.argv[1], sys.argv[2]
S, W, N, E = map(float, sys.argv[3:7])
inside = lambda l: l.valid() and S <= l.lat <= N and W <= l.lon <= E
with osmium.BackReferenceWriter(dst, ref_src=src, overwrite=True) as w:
    for o in osmium.FileProcessor(src, osmium.osm.NODE | osmium.osm.WAY).with_locations("flex_mem"):
        if o.is_way() and any(inside(n.location) for n in o.nodes):
            w.add(o)
