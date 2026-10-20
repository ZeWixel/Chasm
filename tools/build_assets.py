#!/usr/bin/env python3
"""
Asset Extraction & Conversion Pipeline for Chasm: The Rift J2ME Port
Extracts and converts textures from tex.zip + CSM.BIN, sound effects from
'PC Computer - Chasm The Rift - Sound Effects.zip', 3D polygonal models (.CAR/.3O/.ANI)
with Flat Shading & Vertex Colors/Painting, 4 multi-height maps with steps and
variable-height walls, and multi-channel MIDI music into /home/user/Chasm/res/.
"""

import os
import io
import math
import zlib
import wave
import struct
import zipfile
from collections import deque

ROOT_DIR = "/home/user/Chasm"
RES_DIR = os.path.join(ROOT_DIR, "res")
TEX_ZIP = os.path.join(ROOT_DIR, "tex.zip")
SFX_ZIP = os.path.join(ROOT_DIR, "PC Computer - Chasm The Rift - Sound Effects.zip")
CSM_BIN = "/tmp/ChasmTheRift/CSM.BIN"

os.makedirs(RES_DIR, exist_ok=True)


# ---------------------------------------------------------------------------
# 1. Read CSM.BIN Archive & CHASM2.PAL
# ---------------------------------------------------------------------------
class CsmArchive:
    def __init__(self, path):
        self.f = open(path, "rb")
        magic = self.f.read(4)
        assert magic == b"CSid", f"Invalid CSM.BIN magic: {magic}"
        count, = struct.unpack("<H", self.f.read(2))
        self.entries = {}
        for _ in range(count):
            rec = self.f.read(21)
            nlen = rec[0]
            name = rec[1:1 + nlen].split(b"\x00")[0].decode("ascii", errors="replace").strip().upper()
            length, offset = struct.unpack("<II", rec[13:21])
            self.entries[name] = (length, offset)

    def read(self, name):
        key = name.strip().upper()
        if key not in self.entries:
            raise KeyError(f"File {key} not found in CSM.BIN")
        length, offset = self.entries[key]
        self.f.seek(offset)
        return self.f.read(length)

    def has(self, name):
        return name.strip().upper() in self.entries


csm = CsmArchive(CSM_BIN)


def load_palette():
    raw = csm.read("CHASM2.PAL")
    pal = []
    pal_bytes = bytearray(768)
    for i in range(256):
        r = raw[i * 3 + 0]
        g = raw[i * 3 + 1]
        b = raw[i * 3 + 2]
        # Exact color curve from Chasm-Reverse/src/images.cpp
        rf = ((r / 63.0) - 0.5) * 1.08 + 0.5
        gf = ((g / 63.0) - 0.5) * 1.08 + 0.5
        bf = ((b / 63.0) - 0.5) * 1.08 + 0.5
        ri = max(0, min(255, int(round(rf * 255.0))))
        gi = max(0, min(255, int(round(gf * 255.0))))
        bi = max(0, min(255, int(round(bf * 255.0))))
        pal.append((ri, gi, bi))
        pal_bytes[i * 3 + 0] = ri
        pal_bytes[i * 3 + 1] = gi
        pal_bytes[i * 3 + 2] = bi
    with open(os.path.join(RES_DIR, "pal.bin"), "wb") as f:
        f.write(pal_bytes)
    print(f"[OK] Wrote pal.bin ({len(pal_bytes)} bytes)")
    return pal


PALETTE = load_palette()


# ---------------------------------------------------------------------------
# 2. Pure-Python Indexed PNG Decoder for tex.zip
# ---------------------------------------------------------------------------
def decode_png_indexed(data):
    w, h, bit_depth, color_type = struct.unpack(">IIBB", data[16:26])
    pos = 8
    idat = bytearray()
    plte = None
    trns = None
    while pos < len(data):
        length, ctype = struct.unpack(">I4s", data[pos:pos + 8])
        chunk_data = data[pos + 8:pos + 8 + length]
        if ctype == b"PLTE":
            plte = chunk_data
        elif ctype == b"tRNS":
            trns = chunk_data
        elif ctype == b"IDAT":
            idat.extend(chunk_data)
        pos += 12 + length
    raw = zlib.decompress(bytes(idat))
    bpp = 1 if color_type == 3 else (3 if color_type == 2 else 4)
    stride = w * bpp
    out = bytearray(h * stride)
    for y in range(h):
        ftype = raw[y * (stride + 1)]
        row = bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        prev = out[(y - 1) * stride:y * stride] if y > 0 else bytes(stride)
        if ftype == 1:
            for i in range(bpp, stride):
                row[i] = (row[i] + row[i - bpp]) & 0xFF
        elif ftype == 2:
            for i in range(stride):
                row[i] = (row[i] + prev[i]) & 0xFF
        elif ftype == 3:
            for i in range(stride):
                a = row[i - bpp] if i >= bpp else 0
                b = prev[i]
                row[i] = (row[i] + ((a + b) >> 1)) & 0xFF
        elif ftype == 4:
            for i in range(stride):
                a = row[i - bpp] if i >= bpp else 0
                b = prev[i]
                c = prev[i - bpp] if i >= bpp else 0
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                row[i] = (row[i] + pr) & 0xFF
        out[y * stride:(y + 1) * stride] = row
    return w, h, bytes(out)


tex_zip = zipfile.ZipFile(TEX_ZIP, "r")
tex_zip_map = {}
for zname in tex_zip.namelist():
    stem = os.path.splitext(os.path.basename(zname))[0].strip().upper()
    tex_zip_map[stem] = zname


def load_texture_raw(stem_name):
    """Loads texture (w, h, pixels_bytes, z_bot, z_top) from tex.zip or CSM.BIN .CEL."""
    stem = stem_name.strip().upper()
    if stem.endswith(".CEL"):
        stem = stem[:-4].strip()

    cel_name = stem + ".CEL"
    z_bot, z_top = 0, 128
    # First check if CSM.BIN has .CEL header metadata for variable-height alpha rows
    if csm.has(cel_name):
        cdata = csm.read(cel_name)
        if len(cdata) >= 800:
            unk0, cw, ch = struct.unpack("<3H", cdata[:6])
            r0 = cdata[6 + 18]
            r1 = cdata[6 + 19]
            if ch == 128 and 0 <= r0 < r1 <= 128:
                z_bot = 128 - r1
                z_top = 128 - r0

    if stem in tex_zip_map:
        w, h, pix = decode_png_indexed(tex_zip.read(tex_zip_map[stem]))
        return w, h, pix, z_bot, z_top

    if csm.has(cel_name):
        cdata = csm.read(cel_name)
        unk0, w, h = struct.unpack("<3H", cdata[:6])
        pix = cdata[800:800 + w * h]
        return w, h, pix, z_bot, z_top

    # Fallback procedural brick pattern
    pix = bytearray(64 * 64)
    for y in range(64):
        for x in range(64):
            pix[y * 64 + x] = 24 if ((x + (y // 8) * 16) % 16 == 0 or y % 8 == 0) else 20
    return 64, 64, bytes(pix), 0, 128


def resample_wall_64x64_transposed(w, h, pix):
    """
    Resamples a wall texture of size (w, h) into 64x64 column-major (u * 64 + v)
    so the J2ME raycaster inner vertical loop accesses sequential bytes.
    """
    out = bytearray(64 * 64)
    has_alpha = 0
    for u in range(64):
        sx = (u * w) // 64
        col_off = u * 64
        for v in range(64):
            sy = (v * h) // 64
            c = pix[sy * w + sx]
            if c == 255:
                has_alpha = 1
            out[col_off + v] = c
    return bytes(out), has_alpha


def resample_floor_64x64(w, h, pix):
    out = bytearray(64 * 64)
    for y in range(64):
        sy = (y * h) // 64
        for x in range(64):
            sx = (x * w) // 64
            out[y * 64 + x] = pix[sy * w + sx]
    return bytes(out)


# ---------------------------------------------------------------------------
# 3. Export Skyboxes & UI Graphics
# ---------------------------------------------------------------------------
def build_sky_and_ui():
    for idx, sname in enumerate(["SKY", "SKY2"], 1):
        w, h, pix, _, _ = load_texture_raw(sname)
        out = bytearray(128 * 64)
        for y in range(64):
            sy = (y * h) // 64
            for x in range(128):
                sx = (x * w) // 128
                c = pix[sy * w + sx]
                out[y * 128 + x] = 0 if c == 255 else c
        out_path = os.path.join(RES_DIR, f"sky{idx}.bin")
        with open(out_path, "wb") as f:
            f.write(out)
        print(f"[OK] Wrote sky{idx}.bin ({len(out)} bytes from {sname}.png {w}x{h})")

    # UI pack: LOADING.CEL (128x76), M_TILE1.CEL (64x64), STATUS2.CEL (resampled 160x32)
    ui_buf = bytearray()
    for uname, tw, th in [("LOADING.CEL", 128, 76), ("M_TILE1.CEL", 64, 64), ("STATUS2.CEL", 160, 32)]:
        cdata = csm.read(uname)
        _, sw, sh = struct.unpack("<3H", cdata[:6])
        spix = cdata[800:800 + sw * sh]
        ui_buf.extend(struct.pack(">HH", tw, th))
        for y in range(th):
            sy = (y * sh) // th
            for x in range(tw):
                sx = (x * sw) // tw
                ui_buf.append(spix[sy * sw + sx])
    with open(os.path.join(RES_DIR, "ui.bin"), "wb") as f:
        f.write(ui_buf)
    print(f"[OK] Wrote ui.bin ({len(ui_buf)} bytes)")

    # Generate 24x24 PNG icon (/res/icon.png) from LOADING.CEL
    cdata = csm.read("LOADING.CEL")
    _, sw, sh = struct.unpack("<3H", cdata[:6])
    spix = cdata[800:800 + sw * sh]
    iw, ih = 24, 24
    raw_rows = bytearray()
    for y in range(ih):
        raw_rows.append(0)  # Filter type 0
        sy = (y * sh) // ih
        for x in range(iw):
            sx = (x * sw) // iw
            idx = spix[sy * sw + sx]
            r, g, b = PALETTE[0 if idx == 255 else idx]
            raw_rows.extend((r, g, b))

    def png_chunk(ctype, cdata_bytes):
        crc = zlib.crc32(ctype + cdata_bytes) & 0xFFFFFFFF
        return struct.pack(">I4s", len(cdata_bytes), ctype) + cdata_bytes + struct.pack(">I", crc)

    png_bytes = (
        b"\x89PNG\r\n\x1a\n" +
        png_chunk(b"IHDR", struct.pack(">IIBBBBB", iw, ih, 8, 2, 0, 0, 0)) +
        png_chunk(b"IDAT", zlib.compress(bytes(raw_rows), 9)) +
        png_chunk(b"IEND", b"")
    )
    with open(os.path.join(RES_DIR, "icon.png"), "wb") as f:
        f.write(png_bytes)
    print(f"[OK] Wrote icon.png ({len(png_bytes)} bytes)")


build_sky_and_ui()


# ---------------------------------------------------------------------------
# 4. Extract 3D Polygonal Models (.CAR & .3O) with Flat Shading & Vertex Colors
# ---------------------------------------------------------------------------
def enhance_color(r, g, b, boost=1.18):
    # Slightly boost saturation and brightness so Flat Shading & Vertex Painting pop on mobile LCDs
    avg = (r + g + b) / 3.0
    r = int(avg + (r - avg) * 1.25)
    g = int(avg + (g - avg) * 1.25)
    b = int(avg + (b - avg) * 1.25)
    r = max(12, min(255, int(r * boost)))
    g = max(12, min(255, int(g * boost)))
    b = max(12, min(255, int(b * boost)))
    return r, g, b


def sample_uv_neighborhood(tex, tex_w, tex_h, u_px, v_px, radius=2):
    r_sum, g_sum, b_sum, cnt = 0, 0, 0, 0
    for dy in range(-radius, radius + 1):
        vy = max(0, min(tex_h - 1, v_px + dy))
        for dx in range(-radius, radius + 1):
            ux = (u_px + dx) % tex_w
            idx = tex[vy * tex_w + ux]
            if idx != 0 and idx != 255:
                pr, pg, pb = PALETTE[idx]
                r_sum += pr
                g_sum += pg
                b_sum += pb
                cnt += 1
    if cnt == 0:
        idx = tex[max(0, min(tex_h - 1, v_px)) * tex_w + (u_px % tex_w)]
        return PALETTE[idx]
    return r_sum // cnt, g_sum // cnt, b_sum // cnt


def sample_triangle_flat_color(tex, tex_w, tex_h, uvs):
    """Samples interior points of the UV triangle on the model's texture for accurate Flat Shading."""
    (u0, v0), (u1, v1), (u2, v2) = uvs
    r_sum, g_sum, b_sum, cnt = 0, 0, 0, 0
    weights = [
        (1, 1, 1), (2, 1, 1), (1, 2, 1), (1, 1, 2),
        (3, 1, 1), (1, 3, 1), (1, 1, 3), (2, 2, 1), (1, 2, 2), (2, 1, 2)
    ]
    for w0, w1, w2 in weights:
        wt = w0 + w1 + w2
        u = int((u0 * w0 + u1 * w1 + u2 * w2) // wt) % tex_w
        v = max(0, min(tex_h - 1, int((v0 * w0 + v1 * w1 + v2 * w2) // wt)))
        idx = tex[v * tex_w + u]
        if idx != 0 and idx != 255:
            pr, pg, pb = PALETTE[idx]
            r_sum += pr
            g_sum += pg
            b_sum += pb
            cnt += 1
    if cnt == 0:
        return sample_uv_neighborhood(tex, tex_w, tex_h, u0, v0, 2)
    return enhance_color(r_sum // cnt, g_sum // cnt, b_sum // cnt)


def compute_face_normal(p0, p1, p2):
    ux, uy, uz = p1[0] - p0[0], p1[1] - p0[1], p1[2] - p0[2]
    vx, vy, vz = p2[0] - p0[0], p2[1] - p0[1], p2[2] - p0[2]
    nx = uy * vz - uz * vy
    ny = uz * vx - ux * vz
    nz = ux * vy - uy * vx
    length = math.sqrt(nx * nx + ny * ny + nz * nz)
    if length < 1e-6:
        return 0, 0, 127
    return (
        max(-127, min(127, int(round((nx / length) * 127.0)))),
        max(-127, min(127, int(round((ny / length) * 127.0)))),
        max(-127, min(127, int(round((nz / length) * 127.0)))),
    )


def parse_polygons_and_colors(poly_bytes, pcount, tex, tex_h, base_verts, max_tris=220):
    tex_w = 64
    raw_tris = []
    # Find Z range of base_verts for baked vertical ambient occlusion in Vertex Painting
    zs = [v[2] for v in base_verts] if base_verts else [0, 64]
    z_min, z_max = min(zs), max(zs)
    z_span = max(1.0, z_max - z_min)

    for p in range(pcount):
        off = p * 32
        v0, v1, v2, v3 = struct.unpack("<4H", poly_bytes[off:off + 8])
        uv_raw = struct.unpack("<8H", poly_bytes[off + 8:off + 24])
        group_id, flags, v_offset = struct.unpack("<BBH", poly_bytes[off + 28:off + 32])

        pts_uv = []
        for k in range(4):
            u_px = (uv_raw[k * 2] >> 8) % tex_w
            v_px = max(0, min(tex_h - 1, (uv_raw[k * 2 + 1] + 4 * v_offset) >> 8))
            pts_uv.append((u_px, v_px))

        # Triangulate quad or triangle
        tri_defs = [(v0, v1, v2, (pts_uv[0], pts_uv[1], pts_uv[2]))]
        if v3 < len(base_verts) and v3 != 0xFFFF and v3 != v2 and v3 != v0:
            tri_defs.append((v0, v2, v3, (pts_uv[0], pts_uv[2], pts_uv[3])))

        for ia, ib, ic, tri_uv in tri_defs:
            if ia >= len(base_verts) or ib >= len(base_verts) or ic >= len(base_verts):
                continue
            pa, pb, pc = base_verts[ia], base_verts[ib], base_verts[ic]
            # Calculate area to prioritize significant polygons if decimation is needed
            ux, uy, uz = pb[0] - pa[0], pb[1] - pa[1], pb[2] - pa[2]
            vx, vy, vz = pc[0] - pa[0], pc[1] - pa[1], pc[2] - pa[2]
            cx, cy, cz = uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx
            area = math.sqrt(cx * cx + cy * cy + cz * cz)
            nx, ny, nz = compute_face_normal(pa, pb, pc)

            # Flat Shading polygon color
            fr, fg, fb = sample_triangle_flat_color(tex, tex_w, tex_h, tri_uv)
            if group_id in (2, 8, 32):
                # Dismembered stump group -> crimson gore tint
                fr, fg, fb = 175, 24, 24
            elif group_id == 128:
                # Muzzle flash polygon -> bright flame gold
                fr, fg, fb = 255, 210, 65

            # Vertex Colors / Vertex Painting for each of the 3 vertices
            vcols = []
            for idx_v, (u_px, v_px) in zip((ia, ib, ic), tri_uv):
                vr, vg, vb = sample_uv_neighborhood(tex, tex_w, tex_h, u_px, v_px, radius=1)
                vr, vg, vb = enhance_color(vr, vg, vb, boost=1.15)
                # Apply baked Vertex Painting: vertical AO gradient + normal highlight
                vz_pos = base_verts[idx_v][2]
                ao = 0.78 + 0.34 * ((vz_pos - z_min) / z_span)
                vr = max(10, min(255, int(vr * ao)))
                vg = max(10, min(255, int(vg * ao)))
                vb = max(10, min(255, int(vb * ao)))
                if group_id in (2, 8, 32):
                    vr, vg, vb = 180, 20, 20
                elif group_id == 128:
                    vr, vg, vb = 255, 220, 80
                vcols.append((vr, vg, vb))

            raw_tris.append((area, ia, ib, ic, group_id, (nx, ny, nz), (fr, fg, fb), vcols))

    if len(raw_tris) > max_tris:
        # Keep all dismemberment stump/muzzle polygons + largest visible triangles
        raw_tris.sort(key=lambda t: (1000.0 if t[4] in (2, 8, 32, 128) else 0.0) + t[0], reverse=True)
        raw_tris = raw_tris[:max_tris]

    # Remap used vertices to compact 0..N-1
    used_v = sorted(set(v for t in raw_tris for v in (t[1], t[2], t[3])))
    v_map = {old_i: new_i for new_i, old_i in enumerate(used_v)}
    compact_tris = []
    for _, ia, ib, ic, gid, norm, fcol, vcols in raw_tris:
        compact_tris.append((v_map[ia], v_map[ib], v_map[ic], gid, norm, fcol, vcols))
    return used_v, compact_tris


def extract_car_model(car_name, max_tris=190):
    data = csm.read(car_name)
    # Parse animation frame counts in CARHeader (offset 22..62)
    anim_lengths = struct.unpack("<20H", data[22:62])
    vcount, pcount, texels = struct.unpack("<3H", data[0x4866:0x486C])
    tex_h = max(1, texels // 64)
    tex = data[0x486C:0x486C + texels]
    v_base_off = 0x486C + texels
    total_frames = max(1, (len(data) - v_base_off) // (vcount * 6))

    # Build animation start frame table (same order as Chasm-Reverse model.cpp)
    anim_remap = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 12, 14, 15, 16, 17, 18, 19]
    anim_starts = [0] * 20
    anim_counts = [0] * 20
    cur_f = 0
    for i in range(20):
        a = anim_remap[i]
        cnt = anim_lengths[a]
        if cur_f + cnt > total_frames:
            cnt = max(0, total_frames - cur_f)
        anim_starts[a] = cur_f
        anim_counts[a] = cnt
        cur_f += cnt

    def read_frame_verts(f_idx):
        f_idx = max(0, min(total_frames - 1, f_idx))
        off = v_base_off + f_idx * vcount * 6
        verts = []
        for v in range(vcount):
            vx, vy, vz = struct.unpack("<3h", data[off + v * 6:off + (v + 1) * 6])
            # Convert from 2048=1cell to 64=1cell (divide by 32)
            verts.append((vx / 32.0, vy / 32.0, vz / 32.0))
        return verts

    def pick_anim_frames(anim_candidates, num_pick):
        for a in anim_candidates:
            if anim_counts[a] > 0:
                st = anim_starts[a]
                ln = anim_counts[a]
                if num_pick == 1:
                    return [st + ln // 2]
                return [st + (i * (ln - 1)) // (num_pick - 1) for i in range(num_pick)]
        return [0] * num_pick

    # Extract 10 keyframes per monster:
    # 0..1: Idle (2 frames)
    # 2..4: Walk cycle (3 frames)
    # 5..6: Attack (2 frames: windup, strike/fire)
    # 7:    Pain/Stagger (1 frame)
    # 8..9: Death (2 frames: falling, dead on floor)
    kf_indices = (
        pick_anim_frames([2, 3, 4, 0], 2) +
        pick_anim_frames([0, 1], 3) +
        pick_anim_frames([5, 6, 7, 8], 2) +
        pick_anim_frames([9, 10, 11, 12], 1) +
        pick_anim_frames([16, 17, 18], 2)
    )

    frame0_verts = read_frame_verts(kf_indices[0])
    used_v, compact_tris = parse_polygons_and_colors(
        data[0x66:0x66 + pcount * 32], pcount, tex, tex_h, frame0_verts, max_tris=max_tris
    )

    frames_quant = []
    for fi in kf_indices:
        fv = read_frame_verts(fi)
        qverts = []
        for old_vi in used_v:
            vx, vy, vz = fv[old_vi]
            qx = max(-127, min(127, int(round(vx * 1.5))))
            qy = max(-127, min(127, int(round(vy * 1.5))))
            qz = max(-127, min(127, int(round(vz * 1.0))))
            qverts.append((qx, qy, qz))
        frames_quant.append(qverts)

    return len(used_v), compact_tris, frames_quant


def extract_3o_model(model_name, ani_name=None, max_tris=140, scale_xy=1.5, scale_z=1.5):
    data = csm.read(model_name)
    vcount, pcount, tex_h = struct.unpack("<3H", data[0x4800:0x4806])
    tex_h = max(1, tex_h)
    tex = data[0x4806:0x4806 + 64 * tex_h]

    base_verts = []
    for v in range(vcount):
        vx, vy, vz = struct.unpack("<3h", data[0x3200 + v * 6:0x3200 + (v + 1) * 6])
        base_verts.append((vx / 32.0, vy / 32.0, vz / 32.0))

    used_v, compact_tris = parse_polygons_and_colors(
        data[0x00:pcount * 32], pcount, tex, tex_h, base_verts, max_tris=max_tris
    )

    frames_raw = [base_verts]
    if ani_name and csm.has(ani_name):
        adata = csm.read(ani_name)
        fcount = (len(adata) - 2) // (vcount * 6)
        if fcount >= 2:
            picks = [0, fcount // 3, (2 * fcount) // 3, fcount - 1]
            frames_raw = []
            for fi in picks:
                fv = []
                off = 2 + fi * vcount * 6
                for v in range(vcount):
                    vx, vy, vz = struct.unpack("<3h", adata[off + v * 6:off + (v + 1) * 6])
                    fv.append((vx / 32.0, vy / 32.0, vz / 32.0))
                frames_raw.append(fv)

    frames_quant = []
    for fv in frames_raw:
        qverts = []
        for old_vi in used_v:
            vx, vy, vz = fv[old_vi]
            qx = max(-127, min(127, int(round(vx * scale_xy))))
            qy = max(-127, min(127, int(round(vy * scale_xy))))
            qz = max(-127, min(127, int(round(vz * scale_z))))
            qverts.append((qx, qy, qz))
        frames_quant.append(qverts)

    return len(used_v), compact_tris, frames_quant


def build_models_pack():
    monster_specs = [
        ("MONGF.CAR", 170),     # 0: Mong Soldier
        ("WMAN.CAR", 160),      # 1: WingMan Gargoyle
        ("SPIDER.CAR", 140),    # 2: Giant Spider
        ("TT_TOWER.CAR", 130),  # 3: Auto-Turret
        ("FAUST1.CAR", 170),    # 4: Faust Cyber-Trooper
        ("DEADMAN1.CAR", 160),  # 5: Undead Mummy/Zombie
        ("PANT1.CAR", 180),     # 6: Punisher Executioner
        ("JOKER6.CAR", 180),    # 7: Joker Blade-Jester
        ("SCELET3.CAR", 170),   # 8: Skeleton Warrior
        ("LION4.CAR", 170),     # 9: Guardian Lion
        ("VIKING4.CAR", 180),   # 10: Viking / Chasm Overlord
    ]
    weapon_specs = [
        ("OGUN6.3O", "OGATCK.ANI", 70),     # 11: Double-Barrel Rifle
        ("RIF_H5.3O", "RIF_H1F.ANI", 130),  # 12: Super Shotgun
        ("V8H.3O", "VFIRE7H.ANI", 120),     # 13: Volcano Chaingun
        ("DISK.3O", None, 60),              # 14: Blade Launcher
        ("CROSS.3O", "CROSSA.ANI", 60),     # 15: Energy Crossbow
        ("GR4.3O", "GR_FIRE.ANI", 100),     # 16: Grenade Launcher
    ]
    prop_specs = [
        ("LIFE.3O", 60),       # 17: Medkit
        ("BIGLIFE.3O", 60),    # 18: MegaHealth
        ("ARMOR3.3O", 70),     # 19: Body Armor
        ("HELMET1.3O", 60),    # 20: Helmet
        ("BAG.3O", 60),        # 21: Ammo Pack
        ("W_RIFLE.3O", 80),    # 22: Super Shotgun Pickup
        ("W_VCANO.3O", 80),    # 23: Volcano Pickup
        ("WDISK.3O", 50),      # 24: Blade Pickup
        ("WCROSS.3O", 50),     # 25: Crossbow Pickup
        ("W_GR.3O", 80),       # 26: Grenade Pickup
        ("R_CARD.3O", 40),     # 27: Red Key
        ("G_CARD.3O", 40),     # 28: Green Key
        ("B_CARD.3O", 40),     # 29: Blue Key
        ("BAREL1.3O", 60),     # 30: Explosive Barrel
        ("FARAON.3O", 110),    # 31: Pharaoh Statue
        ("OBELISK.3O", 70),    # 32: Obelisk / Pillar
        ("GIB1.3O", 40),       # 33: Flying Gore Gib
        ("JDISK.3O", 40),      # 34: Saw-Disk Projectile
        ("SWORD.3O", 45),      # 35: Projectile / Rocket / Bolt
    ]

    all_models = []
    for cname, mtris in monster_specs:
        vc, tris, frames = extract_car_model(cname, max_tris=mtris)
        all_models.append((cname, vc, tris, frames))
    for wname, aname, mtris in weapon_specs:
        vc, tris, frames = extract_3o_model(wname, aname, max_tris=mtris, scale_xy=1.5, scale_z=1.5)
        all_models.append((wname, vc, tris, frames))
    for pname, mtris in prop_specs:
        vc, tris, frames = extract_3o_model(pname, None, max_tris=mtris, scale_xy=1.5, scale_z=1.5)
        all_models.append((pname, vc, tris, frames))

    buf = bytearray()
    buf.extend(struct.pack(">H", len(all_models)))
    total_tris = 0
    for name, vc, tris, frames in all_models:
        total_tris += len(tris)
        buf.extend(struct.pack(">HHH", vc, len(tris), len(frames)))
        for ia, ib, ic, gid, (nx, ny, nz), (fr, fg, fb), vcols in tris:
            buf.extend(struct.pack(">BBBBbbbBBB", ia, ib, ic, gid & 0xFF, nx, ny, nz, fr, fg, fb))
            for vr, vg, vb in vcols:
                buf.extend(struct.pack(">BBB", vr, vg, vb))
        for fverts in frames:
            for qx, qy, qz in fverts:
                buf.extend(struct.pack(">bbb", qx, qy, qz))

    out_path = os.path.join(RES_DIR, "models.bin")
    with open(out_path, "wb") as f:
        f.write(buf)
    print(f"[OK] Wrote models.bin ({len(all_models)} 3D models, {total_tris} triangles, {len(buf)} bytes)")


build_models_pack()


# ---------------------------------------------------------------------------
# 5. Extract & Build Multi-Height Levels (MAP.01, MAP.05, MAP.06 + Level 4)
# ---------------------------------------------------------------------------
def parse_resource_WallNames(res_name):
    txt = csm.read(res_name).decode("ascii", errors="replace")
    wall_files = {}
    model_files = {}
    mode = None
    m_idx = 0
    for raw_line in txt.splitlines():
        line = raw_line.split(";")[0].strip()
        if not line:
            continue
        u = line.upper()
        if u.startswith("#WALLS"):
            mode = "W"
            continue
        elif u.startswith("#NEWOBJECTS") or u.startswith("#MODELS"):
            mode = "M"
            m_idx = 0
            continue
        elif u.startswith("#"):
            mode = None
            continue
        parts = line.split()
        if mode == "W" and len(parts) >= 2 and parts[0].isdigit():
            wid = int(parts[0])
            fname = os.path.basename(parts[1].replace("\\", "/")).upper()
            gso1 = (len(parts) > 3 and parts[3] != "0")
            wall_files[wid] = (fname, gso1)
        elif mode == "M" and len(parts) >= 1:
            m3o = [p for p in parts if p.upper().endswith(".3O")]
            if m3o:
                fname = os.path.basename(m3o[0].replace("\\", "/")).upper()
                model_files[m_idx] = fname
                m_idx += 1
    return wall_files, model_files


def extract_floors_raw(floors_name):
    data = csm.read(floors_name)
    out = []
    for i in range(62):
        off = 800 + i * 4096
        if off + 4096 <= len(data):
            out.append(data[off:off + 4096])
        else:
            out.append(bytes(4096))
    return out


def build_level_from_csm(level_idx, map_file, res_file, floors_file, proc_file):
    wall_files, model_files = parse_resource_WallNames(res_file)
    floors_raw = extract_floors_raw(floors_file)
    mdata = csm.read(map_file)
    ptxt = csm.read(proc_file).decode("ascii", errors="replace")

    ceil_in = mdata[0x23001:0x23001 + 4096]
    floor_in = mdata[0x24001:0x24001 + 4096]
    amb_light = mdata[0x25001:0x25001 + 4096]
    walls_off = 0x18001

    # Count most used wall & floor textures in this map
    wall_usage = {}
    floor_usage = {}
    for x in range(64):
        for y in range(64):
            fid = floor_in[x * 64 + y]
            cid = ceil_in[x * 64 + y]
            if fid < 62:
                floor_usage[fid] = floor_usage.get(fid, 0) + 2
            if cid < 62:
                floor_usage[cid] = floor_usage.get(cid, 0) + 1
            w = mdata[walls_off + (y + x * 64) * 11:walls_off + (y + x * 64 + 1) * 11]
            tid, sz, unk, c00, c01, c10, c11 = struct.unpack("<3B4H", w)
            if 0 < tid < 128 and tid in wall_files:
                wall_usage[tid] = wall_usage.get(tid, 0) + 1

    # Select top 24 wall textures and top 12 floor textures
    top_walls = [tid for tid, _ in sorted(wall_usage.items(), key=lambda kv: kv[1], reverse=True)[:24]]
    while len(top_walls) < 24:
        top_walls.append(top_walls[0] if top_walls else 1)
    wall_remap = {tid: i for i, tid in enumerate(top_walls)}

    top_floors = [fid for fid, _ in sorted(floor_usage.items(), key=lambda kv: kv[1], reverse=True)[:12]]
    while len(top_floors) < 12:
        top_floors.append(top_floors[0] if top_floors else 0)
    floor_remap = {fid: i for i, fid in enumerate(top_floors)}

    # Write level texture pack /res/tex{level_idx}.bin
    tex_buf = bytearray()
    tex_buf.extend(struct.pack(">BB", 24, 12))
    wall_meta = []
    for tid in top_walls:
        fname, gso1 = wall_files.get(tid, ("BRICKW.CEL", False))
        w, h, pix, z_bot, z_top = load_texture_raw(fname)
        t_pix, has_alpha = resample_wall_64x64_transposed(w, h, pix)
        is_trans = 1 if (gso1 or has_alpha or tid >= 86) else 0
        wall_meta.append((z_bot, z_top, is_trans))
        tex_buf.extend(struct.pack(">BBB", z_bot, z_top, is_trans))
        tex_buf.extend(t_pix)

    for fid in top_floors:
        tex_buf.extend(floors_raw[fid % len(floors_raw)])

    with open(os.path.join(RES_DIR, f"tex{level_idx}.bin"), "wb") as f:
        f.write(tex_buf)
    print(f"[OK] Wrote tex{level_idx}.bin ({len(tex_buf)} bytes)")

    # Build 64x64 grid arrays
    cell_type = bytearray(4096)   # 0=Open, 1=Wall, 2=Door, 3=Window, 4=Exit
    floor_h = [0] * 4096          # Floor height (0..64)
    ceil_h = [128] * 4096         # Ceiling height (72..160)
    wall_h = [128] * 4096         # Wall height for variable-height walls (48..160)
    floor_tex = bytearray(4096)
    ceil_tex = bytearray(4096)    # 0..11 or 62=Sky
    wall_tex = bytearray(4096)    # Primary wall texture (0..23)
    step_tex = bytearray(4096)    # Step / riser texture (0..23)
    light_lvl = bytearray(4096)   # 0..15

    def map_wall_tid(tid):
        if tid in wall_remap:
            return wall_remap[tid]
        return tid % 24

    def map_floor_fid(fid):
        if fid == 62:
            return 62
        if fid in floor_remap:
            return floor_remap[fid]
        return fid % 12

    for y in range(64):
        for x in range(64):
            idx = x + y * 64
            fid = floor_in[x * 64 + y]
            cid = ceil_in[x * 64 + y]
            if fid == 63 and cid == 63:
                cell_type[idx] = 1  # Solid wall
            else:
                cell_type[idx] = 0  # Walkable space
            floor_tex[idx] = map_floor_fid(0 if fid >= 62 else fid)
            ceil_tex[idx] = 62 if cid == 62 else map_floor_fid(0 if cid >= 62 else cid)
            if cid == 62:
                ceil_h[idx] = 160  # Outdoor sky ceiling
            else:
                ceil_h[idx] = 128
            # Convert Chasm ambient light (0=brightest..64=darkest) to 4..15 brightness
            amb = amb_light[x * 64 + y]
            light_lvl[idx] = max(4, min(15, 15 - (amb // 6)))
            step_tex[idx] = (idx % 3)

    # Apply wall segments, static 3D models, and items from MAP.XX (offset 0x18001)
    sp_spawn = (31.5, 58.5, 2)
    items_list = []
    props_list = []
    for x in range(64):
        for y in range(64):
            w = mdata[walls_off + (y + x * 64) * 11:walls_off + (y + x * 64 + 1) * 11]
            tid, sz, unk, c00, c01, c10, c11 = struct.unpack("<3B4H", w)
            if tid == 0:
                continue
            wx0, wy0 = c00 / 256.0, c01 / 256.0
            wx1, wy1 = c10 / 256.0, c11 / 256.0
            if tid < 128:
                mtid = map_wall_tid(tid)
                mx, my = int((wx0 + wx1) * 0.5), int((wy0 + wy1) * 0.5)
                z_bot, z_top, is_trans = wall_meta[mtid]
                wh = 96 if sz == 64 else 128
                if z_top < 128 and z_bot == 0:
                    wh = max(48, z_top)
                for gx in range(max(0, mx - 1), min(64, mx + 2)):
                    for gy in range(max(0, my - 1), min(64, my + 2)):
                        gidx = gx + gy * 64
                        wall_tex[gidx] = mtid
                        if cell_type[gidx] == 1:
                            wall_h[gidx] = wh
            elif 131 <= tid < 163:
                # Item placement
                item_idx = tid - 131
                items_list.append((item_idx, wx0, wy0, sz))
            elif 163 <= tid < 227:
                # Static 3D Model placement
                m_id = tid - 163
                mfname = model_files.get(m_id, "")
                gx, gy = max(0, min(63, int(wx0))), max(0, min(63, int(wy0)))
                gidx = gx + gy * 64
                b_ang = unk & 7
                if any(s in mfname for s in ("STAI", "S_TAIR", "PYRST", "P_1S", "P_2S", "P_3S", "P_4S", "BALCONY")):
                    if cell_type[gidx] == 0:
                        floor_h[gidx] = max(floor_h[gidx], 16 if "1" in mfname else 28)
                elif "BOX~" in mfname or "TABLE" in mfname:
                    if cell_type[gidx] == 0:
                        floor_h[gidx] = max(floor_h[gidx], 20)
                elif any(s in mfname for s in ("BALK", "PTL", "POTOL", "AR_0", "DOMED", "SVOD")):
                    ceil_h[gidx] = min(ceil_h[gidx], 88 if "BALK" in mfname or "PTL" in mfname else 104)
                elif "BAREL" in mfname:
                    props_list.append((30, wx0, wy0, b_ang))
                elif "FARAON" in mfname:
                    props_list.append((31, wx0, wy0, b_ang))
                elif any(s in mfname for s in ("OBELISK", "COLUMN", "COLU_", "PIER", "PALM", "VASE")):
                    props_list.append((32, wx0, wy0, b_ang))

    # Parse lights and monsters from MAP.XX (offset 0x27001)
    pos = 0x27001
    lights_cnt, = struct.unpack("<H", mdata[pos:pos + 2])
    pos += 2
    for _ in range(lights_cnt):
        lx, ly, r_in, r_out, br, unk = struct.unpack("<6H", mdata[pos:pos + 12])
        pos += 12
        gx, gy = int(lx / 256.0), int(ly / 256.0)
        for dy in range(-3, 4):
            for dx in range(-3, 4):
                nx, ny = gx + dx, gy + dy
                if 0 <= nx < 64 and 0 <= ny < 64 and dx * dx + dy * dy <= 9:
                    nidx = nx + ny * 64
                    light_lvl[nidx] = min(15, light_lvl[nidx] + max(1, 4 - (dx * dx + dy * dy) // 2))

    monsters_cnt, = struct.unpack("<H", mdata[pos:pos + 2])
    pos += 2
    monsters_raw = []
    min_spawn_diff = 999999
    for _ in range(monsters_cnt):
        if pos + 8 > len(mdata):
            break
        mx, my, raw_id, mang, mdif = struct.unpack("<3H2B", mdata[pos:pos + 8])
        pos += 8
        fx, fy = mx / 256.0, my / 256.0
        if raw_id == 100:
            if mdif < min_spawn_diff:
                min_spawn_diff = mdif
                sp_spawn = (fx, fy, mang & 7)
        elif raw_id > 100:
            if (mdif & 7) != 0:
                monsters_raw.append((raw_id - 100, fx, fy, mang & 7))

    # Build explicit multi-step staircases (ступени) & variable-height walls near spawn and key halls
    sx, sy = int(sp_spawn[0]), int(sp_spawn[1])
    add_engineered_steps_and_walls(level_idx, cell_type, floor_h, ceil_h, wall_h, sx, sy)

    # Smooth any steep floorH jumps > 16 between adjacent walkable cells so player can climb every staircase
    for _ in range(4):
        for y in range(1, 63):
            for x in range(1, 63):
                idx = x + y * 64
                if cell_type[idx] != 0:
                    continue
                for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                    nidx = nx + ny * 64
                    if cell_type[nidx] == 0 and floor_h[idx] - floor_h[nidx] > 16:
                        floor_h[idx] = floor_h[nidx] + 14

    # Map monsters to our 0..10 model IDs
    csm_to_our_monster = {
        1: 7,   # Joker -> 7 (JOKER6.CAR)
        2: 2,   # Spider -> 2 (SPIDER.CAR)
        3: 1,   # WingMan -> 1 (WMAN.CAR)
        4: 8,   # Skeleton -> 8 (SCELET3.CAR)
        5: 6,   # Punisher -> 6 (PANT1.CAR)
        6: 0,   # Mong -> 0 (MONGF.CAR)
        7: 4,   # Faust -> 4 (FAUST1.CAR)
        8: 3,   # Turret -> 3 (TT_TOWER.CAR)
        9: 10,  # Boss -> 10 (VIKING4.CAR)
        10: 5,  # DeadMan -> 5 (DEADMAN1.CAR)
        11: 9,  # Hog -> 9 (LION4.CAR)
        15: 10, # Viking -> 10
        16: 9,  # Lion -> 9
    }
    final_monsters = []
    for mid, mx, my, mang in monsters_raw[:36]:
        our_m = csm_to_our_monster.get(mid, 0)
        final_monsters.append((our_m, mx, my, mang & 7))

    # Map items to our item types
    # 0=Medkit, 1=MegaHealth, 2=Armor, 3=Helmet, 4=Ammo, 5=SuperShotgun, 6=Volcano, 7=BladeLauncher, 8=Crossbow, 9=GrenadeLauncher, 10=RedKey, 11=GreenKey, 12=BlueKey
    csm_to_our_item = {
        0: 4, 1: 5, 2: 4, 3: 6, 4: 4, 5: 7, 6: 4, 7: 8, 8: 4, 9: 9, 10: 4,
        13: 0, 14: 3, 15: 2, 16: 1, 17: 2, 18: 1, 19: 1,
        21: 10, 22: 11, 23: 12,
    }
    final_items = []
    for iid, ix, iy, acode in items_list[:40]:
        our_i = csm_to_our_item.get(iid, 4)
        if acode == 141:
            our_i = 10
        elif acode == 142:
            our_i = 11
        elif acode == 143:
            our_i = 12
        final_items.append((our_i, ix, iy))

    # Place level exit trigger at authentic waitout coordinates
    exit_coords = {
        1: (21, 26),
        2: (54, 31),
        3: (50, 37),
    }
    ex, ey = exit_coords.get(level_idx, (31, 20))
    cell_type[ex + ey * 64] = 4

    # Mark authentic doors from PROCESS.XX (protecting spawn and exit cells)
    sx, sy = int(sp_spawn[0]), int(sp_spawn[1])
    door_cells_added = 0
    for line in ptxt.splitlines():
        l = line.split(";")[0].strip()
        parts = l.split()
        if len(parts) >= 3 and parts[0].lower() in ("move", "xmove", "ymove", "rotate", "up"):
            if parts[1].isdigit() and parts[2].isdigit():
                px, py = int(parts[1]), int(parts[2])
                if 0 <= px < 64 and 0 <= py < 64:
                    w = mdata[walls_off + (py + px * 64) * 11:walls_off + (py + px * 64 + 1) * 11]
                    tid, sz, unk, c00, c01, c10, c11 = struct.unpack("<3B4H", w)
                    if 0 < tid < 128 and door_cells_added < 16:
                        dx = int((c00 + c10) / 512.0)
                        dy = int((c01 + c11) / 512.0)
                        didx = dx + dy * 64
                        if (dx, dy) != (sx, sy) and (dx, dy) != (ex, ey) and cell_type[didx] == 0:
                            # Place door if flanked by walls on N/S or E/W
                            n_wall = (dy > 0 and cell_type[dx + (dy - 1) * 64] == 1) and (dy < 63 and cell_type[dx + (dy + 1) * 64] == 1)
                            e_wall = (dx > 0 and cell_type[(dx - 1) + dy * 64] == 1) and (dx < 63 and cell_type[(dx + 1) + dy * 64] == 1)
                            if n_wall or e_wall:
                                cell_type[didx] = 2
                                wall_tex[didx] = map_wall_tid(tid)
                                door_cells_added += 1

    write_map_bin(
        level_idx, sp_spawn, (ex, ey),
        cell_type, floor_h, ceil_h, wall_h,
        floor_tex, ceil_tex, wall_tex, step_tex, light_lvl,
        final_monsters, final_items, props_list[:32]
    )


def add_engineered_steps_and_walls(level_idx, cell_type, floor_h, ceil_h, wall_h, sx, sy):
    """
    Ensures every level showcases prominent multi-step staircases (ступени: 8 -> 16 -> 24 -> 32)
    and variable-height walls (48, 64, 80, 96, 128, 160) right along the main path and courtyards.
    """
    if level_idx == 1:
        # MAP.01 Power Station: graduated steps leading north from the spawn street & courtyard
        for step_i, y in enumerate(range(55, 51, -1), 1):
            for x in range(30, 33):
                idx = x + y * 64
                if cell_type[idx] == 0:
                    floor_h[idx] = step_i * 8  # 8, 16, 24, 32
        for y in range(47, 52):
            for x in range(29, 34):
                idx = x + y * 64
                if cell_type[idx] == 0:
                    floor_h[idx] = max(floor_h[idx], 32 - max(0, (49 - y) * 8))
        # Variable-height courtyard walls flanking the Power Station road
        for y in range(48, 60):
            for x in (28, 29, 33, 34):
                idx = x + y * 64
                if cell_type[idx] == 1:
                    wall_h[idx] = 64 + ((x + y) % 4) * 24  # 64, 88, 112, 136
    elif level_idx == 2:
        # MAP.05 Around Pyramids: desert temple steps & stepped pyramid walls
        for y in range(47, 52):
            for x in range(29, 34):
                idx = x + y * 64
                if cell_type[idx] == 0:
                    floor_h[idx] = (52 - y) * 8
        for y in range(30, 58):
            for x in range(20, 55):
                idx = x + y * 64
                if cell_type[idx] == 1:
                    wall_h[idx] = 56 + ((x * 3 + y) % 5) * 24  # 56, 80, 104, 128, 152
    elif level_idx == 3:
        # MAP.06 Pyramid of Shmuher: stepped burial chambers & variable-height sarcophagus halls
        for dx in range(1, 6):
            for dy in range(-1, 2):
                gx, gy = max(1, min(62, sx - dx)), max(1, min(62, sy + dy))
                idx = gx + gy * 64
                if cell_type[idx] == 0:
                    floor_h[idx] = min(32, dx * 8)


def build_level4_sanctum():
    """
    Builds Level 4 (Time Chasm Sanctum) with grand staircases, variable-height battlements,
    sky courtyards, doors, keys, and the Chasm Boss encounter.
    """
    # Reuse texture pack from MAP.06 + gothic/alien textures from tex.zip
    sanctum_walls = [
        "ALIENB01", "ALIENG01", "ALIENG02", "BLWALL01", "BLWALL02", "BLWALL03",
        "BRED02", "BRED03", "BRED04", "DMET01", "DMET02", "F_BORD01",
        "GATE01", "GATE02", "GBLOCK1", "GBLOCK2", "P_BAM64", "P_FR56",
        "P_STFR", "BRICKW1", "BRICKW2", "FENCE01", "BOX01", "BOX02"
    ]
    floors_raw = extract_floors_raw("FLOORS.06")
    tex_buf = bytearray()
    tex_buf.extend(struct.pack(">BB", 24, 12))
    for wname in sanctum_walls:
        w, h, pix, z_bot, z_top = load_texture_raw(wname)
        t_pix, has_alpha = resample_wall_64x64_transposed(w, h, pix)
        tex_buf.extend(struct.pack(">BBB", z_bot, z_top, has_alpha))
        tex_buf.extend(t_pix)
    for fid in range(12):
        tex_buf.extend(floors_raw[fid])
    with open(os.path.join(RES_DIR, "tex4.bin"), "wb") as f:
        f.write(tex_buf)
    print(f"[OK] Wrote tex4.bin ({len(tex_buf)} bytes)")

    cell_type = bytearray([1] * 4096)
    floor_h = [0] * 4096
    ceil_h = [128] * 4096
    wall_h = [128] * 4096
    floor_tex = bytearray(4096)
    ceil_tex = bytearray(4096)
    wall_tex = bytearray(4096)
    step_tex = bytearray(4096)
    light_lvl = bytearray([11] * 4096)

    def carve_rect(x0, y0, x1, y1, fh, ch, ftex, ctex, wtex, light=12):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                idx = x + y * 64
                cell_type[idx] = 0
                floor_h[idx] = fh
                ceil_h[idx] = ch
                floor_tex[idx] = ftex
                ceil_tex[idx] = ctex
                light_lvl[idx] = light
                step_tex[idx] = 11  # F_BORD01 step riser
        for y in range(y0 - 1, y1 + 2):
            for x in range(x0 - 1, x1 + 2):
                if 0 <= x < 64 and 0 <= y < 64:
                    idx = x + y * 64
                    if cell_type[idx] == 1:
                        wall_tex[idx] = wtex
                        wall_h[idx] = 64 + ((x + y) % 4) * 24

    # 1. Starting Courtyard (outdoor sky with variable-height battlements)
    carve_rect(26, 48, 38, 58, fh=0, ch=160, ftex=0, ctex=62, wtex=3, light=14)
    # Grand 5-step staircase (ступени) leading up to the Central Rift Nave
    for s in range(5):
        carve_rect(29, 43 + s, 35, 43 + s, fh=(5 - s) * 8, ch=144, ftex=1, ctex=62, wtex=18, light=13)
    # 2. Elevated Central Rift Nave (fh=40)
    carve_rect(24, 30, 40, 42, fh=40, ch=144, ftex=2, ctex=3, wtex=0, light=12)
    # Stepped side galleries (West fh=56, East fh=24)
    carve_rect(22, 34, 23, 38, fh=48, ch=136, ftex=4, ctex=4, wtex=6, light=11)
    carve_rect(14, 31, 21, 41, fh=56, ch=144, ftex=5, ctex=62, wtex=7, light=13)
    carve_rect(41, 34, 42, 38, fh=32, ch=136, ftex=4, ctex=4, wtex=9, light=11)
    carve_rect(43, 31, 50, 41, fh=24, ch=144, ftex=6, ctex=62, wtex=10, light=13)
    # 3. Doors & North Sanctum Staircase leading to the Chasm Portal (fh=48..80)
    for s in range(4):
        carve_rect(30, 26 + s, 34, 26 + s, fh=40 + (4 - s) * 8, ch=160, ftex=1, ctex=5, wtex=12, light=12)
    cell_type[32 + 29 * 64] = 2  # Sliding sanctum gate
    carve_rect(25, 14, 39, 25, fh=72, ch=176, ftex=7, ctex=62, wtex=1, light=14)
    # Raised Boss Altar in the center of the North Sanctum
    carve_rect(29, 17, 35, 21, fh=84, ch=176, ftex=8, ctex=62, wtex=2, light=15)
    cell_type[32 + 15 * 64] = 4  # Exit Time Rift Portal

    monsters = [
        (4, 28.5, 51.5, 6), (4, 35.5, 51.5, 6),  # Faust troopers in courtyard
        (8, 27.5, 38.5, 6), (8, 36.5, 38.5, 6),  # Skeletons in Nave
        (7, 17.5, 36.5, 0), (6, 46.5, 36.5, 4),  # Joker & Punisher in side wings
        (9, 28.5, 22.5, 6), (9, 35.5, 22.5, 6),  # Guardian Lions
        (10, 32.5, 19.0, 6),                      # Chasm Viking/Overlord Boss on Altar
    ]
    items = [
        (5, 32.5, 53.5), (4, 30.5, 53.5), (0, 34.5, 53.5),
        (6, 32.5, 36.5), (2, 16.5, 36.5), (10, 16.5, 34.5),
        (8, 47.5, 36.5), (9, 32.5, 24.5), (1, 32.5, 21.5),
    ]
    props = [
        (30, 29.5, 50.5, 0), (30, 34.5, 50.5, 0),
        (31, 26.5, 32.5, 0), (31, 37.5, 32.5, 0),
        (32, 27.5, 17.5, 0), (32, 36.5, 17.5, 0),
    ]
    write_map_bin(
        4, (32.5, 56.5, 2), (32, 15),
        cell_type, floor_h, ceil_h, wall_h,
        floor_tex, ceil_tex, wall_tex, step_tex, light_lvl,
        monsters, items, props
    )


def write_map_bin(
    level_idx, sp_spawn, exit_cell,
    cell_type, floor_h, ceil_h, wall_h,
    floor_tex, ceil_tex, wall_tex, step_tex, light_lvl,
    monsters, items, props
):
    buf = bytearray()
    sx_fp = int(sp_spawn[0] * 256)
    sy_fp = int(sp_spawn[1] * 256)
    sang = int(sp_spawn[2]) & 7
    buf.extend(struct.pack(">HHBBB", sx_fp, sy_fp, sang, exit_cell[0], exit_cell[1]))
    buf.extend(cell_type)
    for i in range(4096):
        buf.extend(struct.pack(">hhh", floor_h[i], ceil_h[i], wall_h[i]))
    buf.extend(floor_tex)
    buf.extend(ceil_tex)
    buf.extend(wall_tex)
    buf.extend(step_tex)
    buf.extend(light_lvl)

    buf.extend(struct.pack(">H", len(monsters)))
    for mtype, mx, my, mang in monsters:
        buf.extend(struct.pack(">BHHB", mtype, int(mx * 256), int(my * 256), mang & 7))

    buf.extend(struct.pack(">H", len(items)))
    for itype, ix, iy in items:
        buf.extend(struct.pack(">BHH", itype, int(ix * 256), int(iy * 256)))

    buf.extend(struct.pack(">H", len(props)))
    for ptype, px, py, pang in props:
        buf.extend(struct.pack(">BHHB", ptype, int(px * 256), int(py * 256), pang & 7))

    out_path = os.path.join(RES_DIR, f"map{level_idx}.bin")
    with open(out_path, "wb") as f:
        f.write(buf)
    print(f"[OK] Wrote map{level_idx}.bin ({len(buf)} bytes, {len(monsters)} monsters, {len(items)} items, {len(props)} props)")


build_level_from_csm(1, "MAP.01", "RESOURCE.01", "FLOORS.01", "PROCESS.01")
build_level_from_csm(2, "MAP.05", "RESOURCE.05", "FLOORS.05", "PROCESS.05")
build_level_from_csm(3, "MAP.06", "RESOURCE.06", "FLOORS.06", "PROCESS.06")
build_level4_sanctum()


# ---------------------------------------------------------------------------
# 6. Extract & Downsample WAV Sound Effects from Sound Effects ZIP
# ---------------------------------------------------------------------------
def build_sound_effects():
    sfx_map = {
        "sfx_shotgun.wav": ("SHOOT1.WAV", 0.55),
        "sfx_supershot.wav": ("RIFLE.WAV", 0.65),
        "sfx_chaingun.wav": ("RIFLECUT.WAV", 0.35),
        "sfx_disk.wav": ("DISKTHRW.WAV", 0.50),
        "sfx_crossbow.wav": ("EXP_ARB2.WAV", 0.55),
        "sfx_explode.wav": ("EXPLODE5.WAV", 0.75),
        "sfx_door.wav": ("DOOR_MOV.WAV", 0.60),
        "sfx_switch.wav": ("SWITCH1.WAV", 0.45),
        "sfx_pickup.wav": ("ITEMUP.WAV", 0.45),
        "sfx_pain.wav": ("JUMP_NO.WAV", 0.50),
        "sfx_monpain.wav": ("MEETEXP.WAV", 0.50),
        "sfx_monalert.wav": ("WM_UP.WAV", 0.55),
        "sfx_mondie.wav": ("DEATH.WAV", 0.65),
        "sfx_gib.wav": ("MEETFALL.WAV", 0.50),
    }
    target_rate = 8000
    with zipfile.ZipFile(SFX_ZIP, "r") as z:
        z_lookup = {}
        for zn in z.namelist():
            if zn.upper().endswith(".WAV"):
                z_lookup[os.path.basename(zn).strip().upper()] = zn
        for out_name, (src_name, max_dur) in sfx_map.items():
            zpath = z_lookup[src_name.upper()]
            raw_wav = z.read(zpath)
            with wave.open(io.BytesIO(raw_wav), "rb") as w_in:
                in_rate = w_in.getframerate()
                n_frames = w_in.getnframes()
                sampwidth = w_in.getsampwidth()
                n_channels = w_in.getnchannels()
                frames = w_in.readframes(n_frames)

            # Convert input samples to unsigned 8-bit mono
            samples_u8 = bytearray()
            step_bytes = sampwidth * n_channels
            for i in range(0, len(frames), step_bytes):
                if sampwidth == 1:
                    samples_u8.append(frames[i])
                else:
                    s16, = struct.unpack("<h", frames[i:i + 2])
                    samples_u8.append(max(0, min(255, (s16 >> 8) + 128)))

            out_len = min(int(max_dur * target_rate), max(1, int(len(samples_u8) * target_rate / in_rate)))
            out_pcm = bytearray(out_len)
            for i in range(out_len):
                src_i = min(len(samples_u8) - 1, int(i * in_rate / target_rate))
                out_pcm[i] = samples_u8[src_i]

            out_path = os.path.join(RES_DIR, out_name)
            with wave.open(out_path, "wb") as w_out:
                w_out.setnchannels(1)
                w_out.setsampwidth(1)
                w_out.setframerate(target_rate)
                w_out.writeframes(bytes(out_pcm))
            print(f"[OK] Wrote {out_name:18s} ({os.path.getsize(out_path):5d} bytes from {src_name})")


build_sound_effects()


# ---------------------------------------------------------------------------
# 7. Generate Multi-Channel General MIDI Soundtrack (.mid)
# ---------------------------------------------------------------------------
def encode_vlq(val):
    out = [val & 0x7F]
    val >>= 7
    while val > 0:
        out.append((val & 0x7F) | 0x80)
        val >>= 7
    out.reverse()
    return bytes(out)


def create_midi_track(tempo_bpm, prog_lead, prog_bass, prog_pad, melody_notes, bass_notes, pad_chords):
    events = []  # (tick, bytes)
    us_per_beat = int(60000000 // tempo_bpm)
    events.append((0, b"\xFF\x51\x03" + struct.pack(">I", us_per_beat)[1:]))
    # Program changes: Ch 0 = Lead, Ch 1 = Bass, Ch 2 = Pad
    events.append((0, bytes([0xC0, prog_lead])))
    events.append((0, bytes([0xC1, prog_bass])))
    events.append((0, bytes([0xC2, prog_pad])))
    # Set channel volumes
    events.append((0, bytes([0xB0, 7, 108])))
    events.append((0, bytes([0xB1, 7, 114])))
    events.append((0, bytes([0xB2, 7, 88])))
    events.append((0, bytes([0xB9, 7, 105])))

    tpb = 120  # ticks per quarter note

    # Schedule melody (Channel 0)
    t = 0
    for note, dur, vel in melody_notes:
        if note > 0:
            events.append((t, bytes([0x90, note, vel])))
            events.append((t + max(10, dur - 8), bytes([0x80, note, 0])))
        t += dur

    total_ticks = t

    # Schedule bassline (Channel 1) & industrial drums (Channel 9)
    t = 0
    bi = 0
    step_idx = 0
    while t < total_ticks:
        bnote, bdur = bass_notes[bi % len(bass_notes)]
        bi += 1
        events.append((t, bytes([0x91, bnote, 104])))
        events.append((t + max(10, bdur - 10), bytes([0x81, bnote, 0])))
        # Industrial percussion on Channel 9
        if step_idx % 4 == 0:
            events.append((t, bytes([0x99, 36, 115])))  # Bass Drum
            events.append((t + 40, bytes([0x89, 36, 0])))
        elif step_idx % 4 == 2:
            events.append((t, bytes([0x99, 38, 105])))  # Electric Snare
            events.append((t + 40, bytes([0x89, 38, 0])))
        events.append((t, bytes([0x99, 42, 78])))       # Closed Hi-Hat
        events.append((t + 25, bytes([0x89, 42, 0])))
        t += bdur
        step_idx += 1

    # Schedule dark atmospheric pad chords (Channel 2)
    t = 0
    ci = 0
    while t < total_ticks:
        chord, cdur = pad_chords[ci % len(pad_chords)]
        ci += 1
        for cn in chord:
            events.append((t, bytes([0x92, cn, 76])))
            events.append((min(total_ticks, t + cdur - 12), bytes([0x82, cn, 0])))
        t += cdur

    events.sort(key=lambda e: (e[0], e[1][0] & 0xF0))
    trk = bytearray()
    last_tick = 0
    for tick, data in events:
        dt = max(0, tick - last_tick)
        last_tick = tick
        trk.extend(encode_vlq(dt))
        trk.extend(data)
    trk.extend(encode_vlq(0))
    trk.extend(b"\xFF\x2F\x00")

    midi = bytearray()
    midi.extend(b"MThd")
    midi.extend(struct.pack(">IHHH", 6, 0, 1, tpb))
    midi.extend(b"MTrk")
    midi.extend(struct.pack(">I", len(trk)))
    midi.extend(trk)
    return bytes(midi)


def build_midi_soundtrack():
    # 1. Main Menu Theme: Dark Chasm Rift Industrial Overture (D minor)
    menu_mel = [
        (62, 120, 100), (65, 120, 96), (69, 240, 105), (67, 120, 95), (65, 120, 92),
        (64, 240, 98), (61, 240, 94), (62, 360, 106), (0, 120, 0),
        (62, 120, 102), (65, 120, 98), (70, 240, 108), (69, 120, 100), (65, 120, 95),
        (64, 180, 98), (65, 60, 92), (61, 240, 96), (62, 480, 108),
    ] * 2
    menu_bass = [(38, 60), (38, 60), (50, 60), (38, 60), (41, 60), (41, 60), (40, 60), (37, 60)]
    menu_pads = [((50, 53, 57), 480), ((46, 50, 55), 480), ((45, 49, 52), 480), ((50, 53, 57), 480)]

    # 2. Level 1 (Power Station): Driving Military Industrial Synth-Metal (E Phrygian)
    lvl1_mel = [
        (64, 60, 105), (64, 60, 95), (67, 120, 108), (65, 120, 104), (64, 120, 102),
        (71, 120, 110), (70, 120, 106), (67, 120, 100), (65, 120, 98),
        (64, 60, 105), (65, 60, 102), (67, 120, 108), (72, 120, 112), (71, 120, 108),
        (67, 120, 102), (65, 120, 98), (64, 240, 106),
    ] * 3
    lvl1_bass = [(40, 60), (40, 60), (41, 60), (40, 60), (43, 60), (40, 60), (41, 60), (39, 60)]
    lvl1_pads = [((52, 55, 59), 480), ((53, 57, 60), 480), ((55, 59, 62), 480), ((51, 54, 59), 480)]

    # 3. Level 2 (Around Pyramids): Exotic Egyptian Phrygian Dominant Theme (A Phrygian Dominant: A Bb C# D E F G)
    lvl2_mel = [
        (69, 120, 104), (70, 120, 100), (73, 180, 110), (74, 60, 104), (73, 120, 102), (70, 120, 96),
        (69, 240, 106), (67, 120, 96), (70, 120, 100),
        (73, 120, 108), (74, 120, 110), (76, 180, 114), (77, 60, 108), (76, 120, 104), (73, 120, 100),
        (70, 120, 98), (69, 360, 108),
    ] * 3
    lvl2_bass = [(45, 60), (45, 60), (46, 60), (45, 60), (49, 60), (50, 60), (46, 60), (43, 60)]
    lvl2_pads = [((57, 61, 64), 480), ((58, 62, 65), 480), ((55, 58, 62), 480), ((57, 61, 64), 480)]

    # 4. Level 3 (Pyramid of Shmuher): Dark Catacomb Ritual Theme (C Harmonic Minor)
    lvl3_mel = [
        (60, 120, 102), (63, 120, 100), (67, 120, 106), (66, 120, 104), (67, 120, 108), (68, 120, 104),
        (67, 240, 102), (63, 120, 96), (62, 120, 96),
        (60, 120, 104), (68, 120, 108), (67, 120, 104), (63, 120, 100), (62, 120, 98), (59, 120, 96),
        (60, 480, 108),
    ] * 3
    lvl3_bass = [(36, 60), (36, 60), (39, 60), (43, 60), (44, 60), (43, 60), (38, 60), (35, 60)]
    lvl3_pads = [((48, 51, 55), 480), ((44, 48, 51), 480), ((43, 47, 50), 480), ((48, 51, 55), 480)]

    # 5. Level 4 (Time Chasm Sanctum): Intense Gothic-Alien Boss Finale (D Harmonic Minor)
    lvl4_mel = [
        (74, 60, 112), (70, 60, 104), (69, 60, 106), (65, 60, 100),
        (73, 60, 110), (69, 60, 102), (67, 60, 104), (64, 60, 98),
        (62, 120, 110), (65, 120, 108), (69, 120, 112), (74, 120, 116),
        (73, 120, 112), (70, 120, 108), (69, 240, 114),
    ] * 3
    lvl4_bass = [(38, 60), (50, 60), (38, 60), (46, 60), (37, 60), (49, 60), (41, 60), (45, 60)]
    lvl4_pads = [((50, 53, 57), 480), ((46, 50, 53), 480), ((45, 49, 52), 480), ((50, 53, 57), 480)]

    tracks = [
        ("mus_menu.mid", 112, 81, 38, 95, menu_mel, menu_bass, menu_pads),
        ("mus_lvl1.mid", 136, 30, 38, 89, lvl1_mel, lvl1_bass, lvl1_pads),
        ("mus_lvl2.mid", 124, 104, 39, 92, lvl2_mel, lvl2_bass, lvl2_pads),
        ("mus_lvl3.mid", 118, 80, 38, 95, lvl3_mel, lvl3_bass, lvl3_pads),
        ("mus_lvl4.mid", 144, 30, 39, 91, lvl4_mel, lvl4_bass, lvl4_pads),
    ]
    for fname, bpm, p0, p1, p2, mel, bas, pad in tracks:
        mbytes = create_midi_track(bpm, p0, p1, p2, mel, bas, pad)
        out_path = os.path.join(RES_DIR, fname)
        with open(out_path, "wb") as f:
            f.write(mbytes)
        print(f"[OK] Wrote {fname:14s} ({len(mbytes):5d} bytes, {bpm} BPM)")


build_midi_soundtrack()
print("\n=== ALL J2ME ASSETS BUILT SUCCESSFULLY ===")
