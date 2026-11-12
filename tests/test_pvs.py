#!/usr/bin/env python3
"""Unit tests for conservative solid-wall PVS; no external compiler needed."""
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
from pvs import build_pvs

left = (0, 0, 1, 1, False, 0)
right = (4, 0, 5, 1, False, 0)
top = (0, 4, 1, 5, False, 0)
rects = [left, right, top]

def hidden(segments):
    return build_pvs(rects, segments, margin=0)

p = hidden([(2, -1, 2, 2)])
assert p[0][1] and p[1][0] and not p[0][0]
assert not p[0][2]
assert not hidden([(2, -1, 2, 0), (2, 1, 2, 2)])[0][1], 'gap cannot occlude'
assert hidden([(2, -1, 2, 0), (2, 0, 2, 2)])[0][1], 'adjacent segments should merge'
assert not hidden([(2, 0, 2, 1)])[0][1], 'touching endpoints are not a proof'
assert hidden([(-1, 2, 2, 2)])[0][2], 'horizontal wall'
# Slanted ray bundle can fit behind a short central wall even though the
# combined AABB's perpendicular extent is much taller than that wall.
a = (0, 0, 1, 1, False, 0)
b = (9, 9, 10, 10, False, 0)
assert build_pvs([a, b], [(5, 3, 5, 7)], margin=0)[0][1]
assert not build_pvs([a, b], [(5, 4, 5, 6)], margin=0)[0][1]
assert not build_pvs([left, right], [(2, -1, 2, 2)], margin=1)[0][1], 'expanded bounds'
print('PVS unit tests passed')
