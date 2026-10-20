package chasm;

/**
 * Software 3D Polygonal Rasterizer for Chasm: The Rift J2ME.
 * Supports:
 *  - Mode 0: Hybrid Flat Shading + Vertex Colors / Vertex Painting
 *  - Mode 1: Classic Flat Shading (Одноцветные полигоны)
 *  - Mode 2: Smooth Gouraud Vertex Colors / Vertex Painting
 * Uses 16.16 / 8.8 fixed-point math, zero runtime allocations, keyframe vertex
 * interpolation, limb dismemberment group masks, and per-pixel Z-buffer occlusion.
 */
public final class Renderer3D {
    public static final int SHADE_FLAT_AND_VERTEX = 0;
    public static final int SHADE_FLAT_ONLY       = 1;
    public static final int SHADE_VERTEX_GOURAUD  = 2;

    private static final int MAX_VERTS = 256;
    private static final int MAX_TRIS  = 256;

    // Pre-allocated vertex transformation buffers (zero GC)
    private final int[] viewX = new int[MAX_VERTS];
    private final int[] viewY = new int[MAX_VERTS];
    private final int[] viewZ = new int[MAX_VERTS];
    private final int[] scrX  = new int[MAX_VERTS];
    private final int[] scrY  = new int[MAX_VERTS];

    // Pre-allocated visible triangle sorting buffers
    private final int[] visTriIdx  = new int[MAX_TRIS];
    private final int[] visTriDist = new int[MAX_TRIS];

    public int shadingMode = SHADE_FLAT_AND_VERTEX;

    public String getShadingModeName() {
        if (shadingMode == SHADE_FLAT_ONLY) return "FLAT SHADING";
        if (shadingMode == SHADE_VERTEX_GOURAUD) return "VERTEX PAINTING";
        return "FLAT + VERTEX COLOR";
    }

    /**
     * Renders a 3D polygonal model in world space (enemy, item, prop, projectile, gib).
     *
     * @param mdl         The Model3D mesh
     * @param frameA      Start keyframe index
     * @param frameB      End keyframe index
     * @param interp256   Interpolation factor (0..255)
     * @param worldX      Entity world X in 8.8 fixed-point (256 = 1 cell)
     * @param worldY      Entity world Y in 8.8 fixed-point (256 = 1 cell)
     * @param worldZ      Entity floor Z in height units (64 = 1 cell)
     * @param entAng      Entity yaw angle (0..1023)
     * @param scale256    Scale factor (256 = 1.0x)
     * @param groupMask   Dismemberment bitmask for .CAR body parts
     * @param sectorLight Sector ambient light (0..15)
     * @param hitFlash    True if entity is flashing from damage
     */
    public void renderWorldModel(
        Model3D mdl,
        int frameA, int frameB, int interp256,
        int worldX, int worldY, int worldZ,
        int entAng, int scale256,
        int groupMask, int sectorLight, boolean hitFlash,
        int camX, int camY, int camZ, int camAng, int horizonY,
        int[] screenBuf, short[] zBuffer, int viewW, int viewH
    ) {
        if (mdl == null || mdl.vCount == 0 || mdl.tCount == 0) return;

        int dx = worldX - camX;
        int dy = worldY - camY;
        int camCos = RaycasterEngine.COS[camAng & 1023];
        int camSin = RaycasterEngine.SIN[camAng & 1023];

        // Transform entity origin into camera view space (8.8 fixed-point: 256 = 1 cell)
        int baseVZ = (int) (((long) dx * camCos + (long) dy * camSin) >> 16);
        int baseVX = (int) (((long) -dx * camSin + (long) dy * camCos) >> 16);

        // Quick frustum / distance reject
        if (baseVZ < 40 || baseVZ > 8192) return;
        int boundMargin = 180 + (baseVZ >> 1);
        if (baseVX < -boundMargin || baseVX > boundMargin) return;

        int relAng = (entAng - camAng + 256) & 1023;
        int relCos = RaycasterEngine.COS[relAng];
        int relSin = RaycasterEngine.SIN[relAng];

        int fc = mdl.fCount;
        if (frameA < 0) frameA = 0;
        if (frameA >= fc) frameA = fc - 1;
        if (frameB < 0) frameB = 0;
        if (frameB >= fc) frameB = fc - 1;

        byte[] vertsA = mdl.frameVerts[frameA];
        byte[] vertsB = mdl.frameVerts[frameB];
        int vc = mdl.vCount;
        if (vc > MAX_VERTS) vc = MAX_VERTS;

        int halfW = viewW >> 1;
        int focalX = halfW;
        int focalZ = (viewH * 140) >> 6; // Matches raycaster vertical wall scale

        for (int v = 0; v < vc; v++) {
            int v3 = v * 3;
            int mx = vertsA[v3]     + (((vertsB[v3]     - vertsA[v3])     * interp256) >> 8);
            int my = vertsA[v3 + 1] + (((vertsB[v3 + 1] - vertsA[v3 + 1]) * interp256) >> 8);
            int mz = vertsA[v3 + 2] + (((vertsB[v3 + 2] - vertsA[v3 + 2]) * interp256) >> 8);

            mx = (mx * scale256) >> 8;
            my = (my * scale256) >> 8;
            mz = (mz * scale256) >> 8;

            // Rotate vertex in XY plane and add to baseVX, baseVZ
            int rx = (mx * relCos - my * relSin) >> 15;
            int rz = (mx * relSin + my * relCos) >> 15;

            int vx = baseVX + rx;
            int vz = baseVZ + rz;
            int vy = (worldZ + mz) - camZ;

            viewX[v] = vx;
            viewY[v] = vy;
            viewZ[v] = vz;

            if (vz > 24) {
                scrX[v] = halfW + (vx * focalX) / vz;
                scrY[v] = horizonY - (vy * focalZ) / vz;
            } else {
                scrX[v] = -9999;
                scrY[v] = -9999;
            }
        }

        drawModelTriangles(
            mdl, groupMask, sectorLight, hitFlash,
            relCos, relSin, baseVZ,
            screenBuf, zBuffer, viewW, viewH, false
        );
    }

    /**
     * Renders the first-person 3D weapon model in the player's hands using
     * Flat Shading + Vertex Colors / Vertex Painting.
     */
    public void renderFirstPersonWeapon(
        Model3D mdl,
        int frameA, int frameB, int interp256,
        int bobX, int bobY, int recoilZ,
        int sectorLight, boolean muzzleFlash,
        int[] screenBuf, short[] zBuffer, int viewW, int viewH
    ) {
        if (mdl == null || mdl.vCount == 0 || mdl.tCount == 0) return;

        int fc = mdl.fCount;
        if (frameA < 0 || frameA >= fc) frameA = 0;
        if (frameB < 0 || frameB >= fc) frameB = 0;

        byte[] vertsA = mdl.frameVerts[frameA];
        byte[] vertsB = mdl.frameVerts[frameB];
        int vc = mdl.vCount;
        if (vc > MAX_VERTS) vc = MAX_VERTS;

        int halfW = (viewW >> 1) + bobX + (viewW >> 3);
        int baseH = viewH - (viewH >> 3) + bobY + recoilZ;
        int focal = viewW;

        for (int v = 0; v < vc; v++) {
            int v3 = v * 3;
            int mx = vertsA[v3]     + (((vertsB[v3]     - vertsA[v3])     * interp256) >> 8);
            int my = vertsA[v3 + 1] + (((vertsB[v3 + 1] - vertsA[v3 + 1]) * interp256) >> 8);
            int mz = vertsA[v3 + 2] + (((vertsB[v3 + 2] - vertsA[v3 + 2]) * interp256) >> 8);

            int vz = 78 + (my >> 1) - (recoilZ >> 1);
            if (vz < 28) vz = 28;
            int vx = mx - 10;
            int vy = mz - 18;

            viewX[v] = vx;
            viewY[v] = vy;
            viewZ[v] = vz;
            scrX[v] = halfW + (vx * focal) / vz;
            scrY[v] = baseH - (vy * focal) / vz;
        }

        int mask = muzzleFlash ? 0xFFFF : (0xFFFF & ~128);
        drawModelTriangles(
            mdl, mask, muzzleFlash ? 15 : sectorLight, false,
            65536, 0, 64,
            screenBuf, zBuffer, viewW, viewH, true
        );
    }

    private void drawModelTriangles(
        Model3D mdl, int groupMask, int sectorLight, boolean hitFlash,
        int relCos, int relSin, int baseDist,
        int[] screenBuf, short[] zBuffer, int viewW, int viewH,
        boolean isWeapon
    ) {
        int tc = mdl.tCount;
        if (tc > MAX_TRIS) tc = MAX_TRIS;

        byte[] tV0 = mdl.triV0;
        byte[] tV1 = mdl.triV1;
        byte[] tV2 = mdl.triV2;
        byte[] tGrp = mdl.triGroup;

        int visCount = 0;
        for (int t = 0; t < tc; t++) {
            int grp = tGrp[t] & 0xFF;
            if (grp != 0 && (groupMask & grp) == 0) {
                continue;
            }
            int i0 = tV0[t] & 0xFF;
            int i1 = tV1[t] & 0xFF;
            int i2 = tV2[t] & 0xFF;

            int z0 = viewZ[i0];
            int z1 = viewZ[i1];
            int z2 = viewZ[i2];
            if (z0 <= 24 || z1 <= 24 || z2 <= 24) continue;

            int x0 = scrX[i0], y0 = scrY[i0];
            int x1 = scrX[i1], y1 = scrY[i1];
            int x2 = scrX[i2], y2 = scrY[i2];

            // Screen-space bounding box reject
            int minX = x0 < x1 ? (x0 < x2 ? x0 : x2) : (x1 < x2 ? x1 : x2);
            int maxX = x0 > x1 ? (x0 > x2 ? x0 : x2) : (x1 > x2 ? x1 : x2);
            if (maxX < 0 || minX >= viewW) continue;

            int minY = y0 < y1 ? (y0 < y2 ? y0 : y2) : (y1 < y2 ? y1 : y2);
            int maxY = y0 > y1 ? (y0 > y2 ? y0 : y2) : (y1 > y2 ? y1 : y2);
            if (maxY < 0 || minY >= viewH) continue;

            // Degenerate zero-area polygon reject
            int cross = (x1 - x0) * (y2 - y0) - (y1 - y0) * (x2 - x0);
            if (cross == 0) continue;

            visTriIdx[visCount] = t;
            visTriDist[visCount] = z0 + z1 + z2;
            visCount++;
        }

        // Painter's algorithm sort (far to near) for clean polygon layering
        for (int i = 1; i < visCount; i++) {
            int keyIdx = visTriIdx[i];
            int keyDist = visTriDist[i];
            int j = i - 1;
            while (j >= 0 && visTriDist[j] < keyDist) {
                visTriIdx[j + 1] = visTriIdx[j];
                visTriDist[j + 1] = visTriDist[j];
                j--;
            }
            visTriIdx[j + 1] = keyIdx;
            visTriDist[j + 1] = keyDist;
        }

        byte[] tNX = mdl.triNX;
        byte[] tNY = mdl.triNY;
        byte[] tNZ = mdl.triNZ;
        int[] flatRGB = mdl.triFlatRGB;
        int[] vRGB0 = mdl.triVertRGB0;
        int[] vRGB1 = mdl.triVertRGB1;
        int[] vRGB2 = mdl.triVertRGB2;

        // Distance attenuation factor
        int distAtten = 256 - (baseDist >> 5);
        if (distAtten < 72) distAtten = 72;
        if (distAtten > 256) distAtten = 256;
        int ambBase = ((sectorLight & 15) * 11) + 56; // 56..221

        for (int k = 0; k < visCount; k++) {
            int t = visTriIdx[k];
            int i0 = tV0[t] & 0xFF;
            int i1 = tV1[t] & 0xFF;
            int i2 = tV2[t] & 0xFF;

            int x0 = scrX[i0], y0 = scrY[i0];
            int x1 = scrX[i1], y1 = scrY[i1];
            int x2 = scrX[i2], y2 = scrY[i2];

            short z16 = isWeapon ? (short) 8 : (short) (visTriDist[k] / 3);

            // Compute 3D face normal lighting (Flat Shading intensity)
            int nx = tNX[t];
            int ny = tNY[t];
            int nz = tNZ[t];
            int rnx = (nx * relCos - ny * relSin) >> 16;
            int rnz = (nx * relSin + ny * relCos) >> 16;
            int diff = (-rnz + (rnx >> 1) + nz) >> 1;
            if (diff < -40) diff = -diff >> 1; // Two-sided normal wrap for thin wings/legs

            int shade = ((ambBase + (diff >> 1)) * distAtten) >> 8;
            if (hitFlash) shade += 96;
            if ((tGrp[t] & 0xFF) == 128) shade = 256; // Muzzle flash fullbright
            if (shade < 44) shade = 44;
            if (shade > 256) shade = 256;

            if (shadingMode == SHADE_FLAT_ONLY) {
                int cFlat = shadeColor(flatRGB[t], shade, hitFlash);
                rasterizeTriangleFlat(
                    x0, y0, x1, y1, x2, y2,
                    cFlat, z16, screenBuf, zBuffer, viewW, viewH
                );
            } else {
                // SHADE_FLAT_AND_VERTEX or SHADE_VERTEX_GOURAUD
                int fRgb = flatRGB[t];
                int c0 = vRGB0[t];
                int c1 = vRGB1[t];
                int c2 = vRGB2[t];
                if (shadingMode == SHADE_FLAT_AND_VERTEX) {
                    // Blend polygon Flat Shading color (50%) with Vertex Painted colors (50%)
                    c0 = blendRGB(fRgb, c0);
                    c1 = blendRGB(fRgb, c1);
                    c2 = blendRGB(fRgb, c2);
                }
                int sc0 = shadeColor(c0, shade, hitFlash);
                int sc1 = shadeColor(c1, shade, hitFlash);
                int sc2 = shadeColor(c2, shade, hitFlash);
                rasterizeTriangleVertexColored(
                    x0, y0, sc0,
                    x1, y1, sc1,
                    x2, y2, sc2,
                    z16, screenBuf, zBuffer, viewW, viewH
                );
            }
        }
    }

    private static int blendRGB(int cA, int cB) {
        int r = (((cA >> 16) & 0xFF) + ((cB >> 16) & 0xFF)) >> 1;
        int g = (((cA >> 8)  & 0xFF) + ((cB >> 8)  & 0xFF)) >> 1;
        int b = ((cA & 0xFF)         + (cB & 0xFF))         >> 1;
        return (r << 16) | (g << 8) | b;
    }

    private static int shadeColor(int rgb, int shade256, boolean hitFlash) {
        int r = (((rgb >> 16) & 0xFF) * shade256) >> 8;
        int g = (((rgb >> 8)  & 0xFF) * shade256) >> 8;
        int b = ((rgb & 0xFF)         * shade256) >> 8;
        if (hitFlash) {
            r += 70;
            g += 15;
        }
        if (r > 255) r = 255;
        if (g > 255) g = 255;
        if (b > 255) b = 255;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * Fast scanline triangle filler for pure Flat Shading (single color per polygon).
     */
    private static void rasterizeTriangleFlat(
        int x0, int y0, int x1, int y1, int x2, int y2,
        int color, short z16, int[] screenBuf, short[] zBuffer, int viewW, int viewH
    ) {
        // Sort vertices by Y: y0 <= y1 <= y2
        if (y0 > y1) { int t = x0; x0 = x1; x1 = t; t = y0; y0 = y1; y1 = t; }
        if (y1 > y2) { int t = x1; x1 = x2; x2 = t; t = y1; y1 = y2; y2 = t; }
        if (y0 > y1) { int t = x0; x0 = x1; x1 = t; t = y0; y0 = y1; y1 = t; }

        if (y2 < 0 || y0 >= viewH || y0 == y2) return;

        int totalH = y2 - y0;
        int yStart = y0 < 0 ? 0 : y0;
        int yEnd = y2 >= viewH ? viewH - 1 : y2;

        for (int y = yStart; y <= yEnd; y++) {
            boolean secondHalf = (y > y1) || (y1 == y0);
            int segH = secondHalf ? (y2 - y1) : (y1 - y0);
            if (segH == 0) continue;

            int xa = x0 + ((x2 - x0) * (y - y0)) / totalH;
            int xb = secondHalf
                ? x1 + ((x2 - x1) * (y - y1)) / segH
                : x0 + ((x1 - x0) * (y - y0)) / segH;

            if (xa > xb) { int t = xa; xa = xb; xb = t; }
            if (xb < 0 || xa >= viewW) continue;
            if (xa < 0) xa = 0;
            if (xb >= viewW) xb = viewW - 1;

            int rowOff = y * viewW;
            for (int x = xa; x <= xb; x++) {
                int idx = rowOff + x;
                if (z16 <= zBuffer[idx]) {
                    zBuffer[idx] = z16;
                    screenBuf[idx] = color;
                }
            }
        }
    }

    /**
     * Fast scanline Gouraud triangle filler for Vertex Colors / Vertex Painting.
     * Interpolates RGB vertex colors across edges and horizontal scanlines in 8.8 fixed-point.
     */
    private static void rasterizeTriangleVertexColored(
        int x0, int y0, int c0,
        int x1, int y1, int c1,
        int x2, int y2, int c2,
        short z16, int[] screenBuf, short[] zBuffer, int viewW, int viewH
    ) {
        // Sort vertices by Y: y0 <= y1 <= y2
        if (y0 > y1) {
            int t = x0; x0 = x1; x1 = t;
            t = y0; y0 = y1; y1 = t;
            t = c0; c0 = c1; c1 = t;
        }
        if (y1 > y2) {
            int t = x1; x1 = x2; x2 = t;
            t = y1; y1 = y2; y2 = t;
            t = c1; c1 = c2; c2 = t;
        }
        if (y0 > y1) {
            int t = x0; x0 = x1; x1 = t;
            t = y0; y0 = y1; y1 = t;
            t = c0; c0 = c1; c1 = t;
        }

        if (y2 < 0 || y0 >= viewH || y0 == y2) return;

        int r0 = (c0 >> 16) & 0xFF, g0 = (c0 >> 8) & 0xFF, b0 = c0 & 0xFF;
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;

        int totalH = y2 - y0;
        int yStart = y0 < 0 ? 0 : y0;
        int yEnd = y2 >= viewH ? viewH - 1 : y2;

        for (int y = yStart; y <= yEnd; y++) {
            boolean secondHalf = (y > y1) || (y1 == y0);
            int segH = secondHalf ? (y2 - y1) : (y1 - y0);
            if (segH == 0) continue;

            int dyTotal = y - y0;
            int xa = x0 + ((x2 - x0) * dyTotal) / totalH;
            int ra = r0 + ((r2 - r0) * dyTotal) / totalH;
            int ga = g0 + ((g2 - g0) * dyTotal) / totalH;
            int ba = b0 + ((b2 - b0) * dyTotal) / totalH;

            int xb, rb, gb, bb;
            if (secondHalf) {
                int dySeg = y - y1;
                xb = x1 + ((x2 - x1) * dySeg) / segH;
                rb = r1 + ((r2 - r1) * dySeg) / segH;
                gb = g1 + ((g2 - g1) * dySeg) / segH;
                bb = b1 + ((b2 - b1) * dySeg) / segH;
            } else {
                xb = x0 + ((x1 - x0) * dyTotal) / segH;
                rb = r0 + ((r1 - r0) * dyTotal) / segH;
                gb = g0 + ((g1 - g0) * dyTotal) / segH;
                bb = b0 + ((b1 - b0) * dyTotal) / segH;
            }

            if (xa > xb) {
                int t = xa; xa = xb; xb = t;
                t = ra; ra = rb; rb = t;
                t = ga; ga = gb; gb = t;
                t = ba; ba = bb; bb = t;
            }
            if (xb < 0 || xa >= viewW) continue;

            int spanW = xb - xa;
            if (spanW <= 0) spanW = 1;
            int dr = ((rb - ra) << 8) / spanW;
            int dg = ((gb - ga) << 8) / spanW;
            int db = ((bb - ba) << 8) / spanW;

            int curR = ra << 8;
            int curG = ga << 8;
            int curB = ba << 8;

            if (xa < 0) {
                int skip = -xa;
                curR += dr * skip;
                curG += dg * skip;
                curB += db * skip;
                xa = 0;
            }
            if (xb >= viewW) xb = viewW - 1;

            int rowOff = y * viewW;
            for (int x = xa; x <= xb; x++) {
                int idx = rowOff + x;
                if (z16 <= zBuffer[idx]) {
                    zBuffer[idx] = z16;
                    screenBuf[idx] = 0xFF000000
                        | ((curR & 0xFF00) << 8)
                        | (curG & 0xFF00)
                        | ((curB >> 8) & 0xFF);
                }
                curR += dr;
                curG += dg;
                curB += db;
            }
        }
    }
}
