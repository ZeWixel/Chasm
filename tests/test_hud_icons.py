#!/usr/bin/env python3
"""Protect the custom armour icon and the asset compiler's icon ordering."""
import hashlib
import struct
import sys
from pathlib import Path

root = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(root / 'tools'))
from hud_icons import build_hud_icons

armor = (root / 'tools/assets/hud_armor.idx').read_bytes()
assert hashlib.sha256(armor).hexdigest() == 'cae9fc8a409088201188c2146e74d7482232b9055ba21d32d2e1c44ec56074d1'
current = (root / 'res/hud_icons.bin').read_bytes()
assert len(current) == 780
assert all(current[i:i+4] == b'\x00\x10\x00\x10' for i in (0, 260, 520))
assert current[264:520] == armor

# Synthetic CELs test the crop and packing without requiring the retail archive.
def cel(seed):
    width, height = 160, 64
    return struct.pack('<3H', 0, width, height) + bytes(794) + bytes(
        (x * 3 + y * 7 + seed) % 256 for y in range(height) for x in range(width))

sources = {'NSTAT.CEL': cel(11), 'WICONS.CEL': cel(29)}
pack = build_hud_icons(sources.__getitem__, armor)
assert len(pack) == 780 and pack[264:520] == armor
for start, seed, cx, cy, cw, ch in ((0, 11, 5, 16, 24, 24), (520, 29, 3, 3, 20, 20)):
    assert pack[start:start+4] == b'\x00\x10\x00\x10'
    for y in range(16):
        for x in range(16):
            assert pack[start+4+y*16+x] == ((cx+x*cw//16)*3 + (cy+y*ch//16)*7 + seed) % 256
try:
    build_hud_icons(sources.__getitem__, armor[:-1])
    raise AssertionError('short custom icon was accepted')
except ValueError:
    pass
print('HUD custom armour and generator packing OK')
