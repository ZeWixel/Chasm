package chasm;

import java.io.DataInputStream;
import java.io.InputStream;

/**
 * Multi-Height Sector & Step Raycasting Engine for Chasm: The Rift J2ME.
 * Features:
 *  - 16.16 / 8.8 Fixed-Point 2D Grid DDA with per-column vertical clipping [yTop..yBot]
 *  - Variable-height walls (wallH = 48..160): rays pass over shorter walls to render
 *    taller walls, towers, pyramids, and skyboxes behind them
 *  - Multi-step floors & staircases (ступени: floorH = 0..84) with textured step risers
 *  - Variable ceiling heights & upper lintels/arches (ceilH = 72..176)
 *  - Perspective floor/ceiling texturing & parallax Chasm skyboxes (SKY.CEL / SKY2.CEL)
 *  - Precomputed 16-level lighting palette table (zero bit-shifts per pixel)
 *  - Per-pixel 16-bit Z-buffer for accurate occlusion of 3D polygonal models
 */
public final class RaycasterEngine {
    public static final int ANG_MASK = 1023;
    public static final int[] SIN = new int[1024];
    public static final int[] COS = new int[1024];

    static {
        // Initialize 1024-entry 16.16 fixed-point sine/cosine table using Bhaskara I's approximation
        for (int i = 0; i < 1024; i++) {
            int deg10 = (i * 3600) / 1024;
            SIN[i] = fastSin16(deg10);
            COS[i] = fastSin16((deg10 + 900) % 3600);
        }
    }

    private static int fastSin16(int deg10) {
        deg10 %= 3600;
        if (deg10 < 0) deg10 += 3600;
        int sign = 1;
        if (deg10 >= 1800) {
            deg10 -= 1800;
            sign = -1;
        }
        // Bhaskara I formula: sin(x) ~= 4*x*(180-x) / (40500 - x*(180-x))
        long x = deg10;
        long p = x * (1800L - x);
        long num = 4L * p * 65536L;
        long den = 4050000L - p;
        return (int) (sign * (num / den));
    }

    // Base palette & 16-level pre-lit lookup table: lightPal[0..15][0..255]
    public final int[] basePal = new int[256];
    public final int[][] lightPal = new int[16][256];

    // Skybox 128x64 indexed pixels
    public final byte[] skyPixels = new byte[128 * 64];

    // Level texture pack: 24 wall textures (64x64 transposed u*64+v) + 12 floor textures (64x64)
    public static final int MAX_WALL_TEX = 24;
    public static final int MAX_FLOOR_TEX = 12;
    public final byte[] wallAtlas = new byte[MAX_WALL_TEX * 4096];
    public final byte[] wallZBot = new byte[MAX_WALL_TEX];
    public final byte[] wallZTop = new byte[MAX_WALL_TEX];
    public final byte[] wallTrans = new byte[MAX_WALL_TEX];
    public final byte[] floorAtlas = new byte[MAX_FLOOR_TEX * 4096];

    // UI textures from /ui.bin (LOADING.CEL, M_TILE1.CEL, STATUS2.CEL)
    public int logoW, logoH;
    public int[] logoRGB;
    public int tileW, tileH;
    public int[] tileRGB;
    public int hudBarW, hudBarH;
    public int[] hudBarRGB;

    public RaycasterEngine() {
        loadPaletteAndLightTable();
        loadUiGraphics();
    }

    private void loadPaletteAndLightTable() {
        InputStream is = null;
        try {
            is = getClass().getResourceAsStream("/pal.bin");
            DataInputStream dis = new DataInputStream(is);
            for (int i = 0; i < 256; i++) {
                int r = dis.readUnsignedByte();
                int g = dis.readUnsignedByte();
                int b = dis.readUnsignedByte();
                basePal[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
                for (int l = 0; l < 16; l++) {
                    int scale = 44 + l * 14; // 44..254
                    int lr = (r * scale) >> 8;
                    int lg = (g * scale) >> 8;
                    int lb = (b * scale) >> 8;
                    if (lr > 255) lr = 255;
                    if (lg > 255) lg = 255;
                    if (lb > 255) lb = 255;
                    lightPal[l][i] = 0xFF000000 | (lr << 16) | (lg << 8) | lb;
                }
            }
        } catch (Exception e) {
        } finally {
            if (is != null) {
                try { is.close(); } catch (Exception ex) {}
            }
        }
    }

    private void loadUiGraphics() {
        InputStream is = null;
        try {
            is = getClass().getResourceAsStream("/ui.bin");
            DataInputStream dis = new DataInputStream(is);

            logoW = dis.readUnsignedShort();
            logoH = dis.readUnsignedShort();
            logoRGB = readIndexedImage(dis, logoW * logoH);

            tileW = dis.readUnsignedShort();
            tileH = dis.readUnsignedShort();
            tileRGB = readIndexedImage(dis, tileW * tileH);

            hudBarW = dis.readUnsignedShort();
            hudBarH = dis.readUnsignedShort();
            hudBarRGB = readIndexedImage(dis, hudBarW * hudBarH);
        } catch (Exception e) {
        } finally {
            if (is != null) {
                try { is.close(); } catch (Exception ex) {}
            }
        }
    }

    private int[] readIndexedImage(DataInputStream dis, int count) throws Exception {
        byte[] raw = new byte[count];
        dis.readFully(raw);
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            int idx = raw[i] & 0xFF;
            out[i] = (idx == 255) ? 0x00000000 : basePal[idx];
        }
        return out;
    }

    public void loadLevelTextures(int levelIdx) {
        InputStream is = null;
        try {
            String skyFile = (levelIdx == 2 || levelIdx == 4) ? "/sky2.bin" : "/sky1.bin";
            is = getClass().getResourceAsStream(skyFile);
            if (is != null) {
                DataInputStream sdis = new DataInputStream(is);
                sdis.readFully(skyPixels);
                is.close();
            }

            is = getClass().getResourceAsStream("/tex" + levelIdx + ".bin");
            DataInputStream dis = new DataInputStream(is);
            int numW = dis.readUnsignedByte();
            int numF = dis.readUnsignedByte();
            if (numW > MAX_WALL_TEX) numW = MAX_WALL_TEX;
            if (numF > MAX_FLOOR_TEX) numF = MAX_FLOOR_TEX;

            for (int w = 0; w < numW; w++) {
                wallZBot[w] = dis.readByte();
                wallZTop[w] = dis.readByte();
                wallTrans[w] = dis.readByte();
                dis.readFully(wallAtlas, w * 4096, 4096);
            }
            for (int f = 0; f < numF; f++) {
                dis.readFully(floorAtlas, f * 4096, 4096);
            }
        } catch (Exception e) {
        } finally {
            if (is != null) {
                try { is.close(); } catch (Exception ex) {}
            }
        }
    }

    /**
     * Renders the entire 3D multi-height level view into screenBuf and populates zBuffer.
     *
     * @param world      Active GameWorld containing the 64x64 multi-height grid
     * @param camX       Camera X in 8.8 fixed-point (256 = 1.0 cell)
     * @param camY       Camera Y in 8.8 fixed-point (256 = 1.0 cell)
     * @param camZ       Camera eye Z in height units (64 = 1.0 cell)
     * @param camAng     Camera yaw angle (0..1023)
     * @param horizonY   Screen Y coordinate of the horizon line
     * @param screenBuf  Destination ARGB pixel buffer (viewW * viewH)
     * @param zBuffer    Destination 16-bit depth buffer (in 8.8 fixed-point distance)
     * @param viewW      Viewport width
     * @param viewH      Viewport height
     */
    public void renderView(
        GameWorld world,
        int camX, int camY, int camZ, int camAng, int horizonY,
        int[] screenBuf, short[] zBuffer, int viewW, int viewH
    ) {
        byte[] cellType = world.cellType;
        short[] floorH  = world.floorH;
        short[] ceilH   = world.ceilH;
        short[] wallH   = world.wallH;
        byte[] floorTex = world.floorTex;
        byte[] ceilTex  = world.ceilTex;
        byte[] wallTex  = world.wallTex;
        byte[] stepTex  = world.stepTex;
        byte[] lightLvl = world.lightLvl;
        byte[] doorOpen = world.doorOpen;

        int halfW = viewW >> 1;
        int focalZ = (viewH * 140) >> 6;
        int dirX = COS[camAng & 1023];
        int dirY = SIN[camAng & 1023];
        // Camera plane perpendicular to direction (~70 deg FOV)
        int planeX = (-dirY * 90) >> 7;
        int planeY = (dirX * 90) >> 7;

        int startCellX = camX >> 8;
        int startCellY = camY >> 8;
        if (startCellX < 0) startCellX = 0;
        if (startCellX > 63) startCellX = 63;
        if (startCellY < 0) startCellY = 0;
        if (startCellY > 63) startCellY = 63;
        int startIdx = startCellX + (startCellY << 6);

        int initFloorH = floorH[startIdx];
        int initCeilH  = ceilH[startIdx];

        int[] skyPal = lightPal[14];

        // Cast one ray per vertical column (or 2px pair on very wide screens)
        for (int x = 0; x < viewW; x++) {
            int camScreenX = ((x - halfW) << 16) / halfW;
            int rayDirX = dirX + (int) (((long) planeX * camScreenX) >> 16);
            int rayDirY = dirY + (int) (((long) planeY * camScreenX) >> 16);

            int mapX = startCellX;
            int mapY = startCellY;

            int absRayX = rayDirX < 0 ? -rayDirX : rayDirX;
            int absRayY = rayDirY < 0 ? -rayDirY : rayDirY;
            if (absRayX < 8) absRayX = 8;
            if (absRayY < 8) absRayY = 8;

            // Distance in 8.8 fixed-point to cross 1 full grid cell along X or Y
            int deltaDistX = (256 << 16) / absRayX;
            int deltaDistY = (256 << 16) / absRayY;

            int stepX, sideDistX;
            if (rayDirX < 0) {
                stepX = -1;
                sideDistX = ((camX & 0xFF) * deltaDistX) >> 8;
            } else {
                stepX = 1;
                sideDistX = ((256 - (camX & 0xFF)) * deltaDistX) >> 8;
            }

            int stepY, sideDistY;
            if (rayDirY < 0) {
                stepY = -1;
                sideDistY = ((camY & 0xFF) * deltaDistY) >> 8;
            } else {
                stepY = 1;
                sideDistY = ((256 - (camY & 0xFF)) * deltaDistY) >> 8;
            }

            int yTop = 0;
            int yBot = viewH - 1;
            int curCell = startIdx;
            int curFloorH = initFloorH;
            int curCeilH  = initCeilH;
            int skyU = ((camAng << 1) + (x >> 1)) & 127;

            int steps = 0;
            while (steps < 44 && yTop <= yBot) {
                steps++;
                int side;
                int perpDist;
                if (sideDistX < sideDistY) {
                    perpDist = sideDistX;
                    sideDistX += deltaDistX;
                    mapX += stepX;
                    side = 0;
                } else {
                    perpDist = sideDistY;
                    sideDistY += deltaDistY;
                    mapY += stepY;
                    side = 1;
                }

                if (perpDist < 12) perpDist = 12;
                short distZ16 = (perpDist > 32000) ? (short) 32000 : (short) perpDist;

                // Project current cell's floor and ceiling at the boundary distance perpDist
                int curFloorY = horizonY - (((curFloorH - camZ) * focalZ) / perpDist);
                int curCeilY  = horizonY - (((curCeilH  - camZ) * focalZ) / perpDist);

                int cLight = lightLvl[curCell] & 15;
                int distDim = perpDist >> 9;
                int effLight = cLight - distDim;
                if (effLight < 1) effLight = 1;
                int[] curPal = lightPal[effLight];

                // 1. Render Ceiling or Skybox span from yTop down to curCeilY
                if (curCeilY > yTop) {
                    int cEnd = curCeilY < yBot ? curCeilY : yBot;
                    int cTex = ceilTex[curCell] & 0xFF;
                    if (cTex == 62) {
                        for (int y = yTop; y <= cEnd; y++) {
                            int idx = y * viewW + x;
                            int sv = (y * 64) / viewH;
                            screenBuf[idx] = skyPal[skyPixels[((sv & 63) << 7) + skyU] & 0xFF];
                            zBuffer[idx] = 30000;
                        }
                    } else {
                        int fOff = (cTex % MAX_FLOOR_TEX) << 12;
                        drawFloorOrCeilSpan(
                            x, yTop, cEnd, curCeilH - camZ, focalZ,
                            camX, camY, rayDirX, rayDirY,
                            fOff, curPal, screenBuf, zBuffer, viewW, horizonY
                        );
                    }
                    yTop = cEnd + 1;
                }

                // 2. Render Floor span from curFloorY down to yBot
                if (curFloorY < yBot && yTop <= yBot) {
                    int fStart = curFloorY > yTop ? curFloorY : yTop;
                    int fTex = floorTex[curCell] & 0xFF;
                    int fOff = (fTex % MAX_FLOOR_TEX) << 12;
                    drawFloorOrCeilSpan(
                        x, fStart, yBot, curFloorH - camZ, focalZ,
                        camX, camY, rayDirX, rayDirY,
                        fOff, curPal, screenBuf, zBuffer, viewW, horizonY
                    );
                    yBot = fStart - 1;
                }

                if (yTop > yBot) break;
                if (mapX < 0 || mapX >= 64 || mapY < 0 || mapY >= 64) break;

                int nextCell = mapX + (mapY << 6);
                int nType = cellType[nextCell] & 0xFF;

                // Compute exact wall hit U coordinate (0..63)
                int wallHitPos;
                if (side == 0) {
                    wallHitPos = camY + (int) (((long) perpDist * rayDirY) >> 16);
                } else {
                    wallHitPos = camX + (int) (((long) perpDist * rayDirX) >> 16);
                }
                int texU = (wallHitPos >> 2) & 63;

                int wLight = (lightLvl[nextCell] & 15) - distDim - (side << 1);
                if (wLight < 1) wLight = 1;
                int[] wPal = lightPal[wLight];

                if (nType == 1 || nType == 4) {
                    // Solid Wall or Level Exit Wall: check variable wall height wallH[nextCell]
                    int wh = wallH[nextCell];
                    int wTex = wallTex[nextCell] % MAX_WALL_TEX;
                    int colOff = (wTex << 12) | (texU << 6);

                    int wTopY = horizonY - (((wh - camZ) * focalZ) / perpDist);
                    int drawStart = wTopY > yTop ? wTopY : yTop;
                    int drawEnd = yBot;

                    if (drawStart <= drawEnd) {
                        drawWallColumn(
                            x, drawStart, drawEnd,
                            curFloorH, wh, wTopY, yBot + 1,
                            colOff, wPal, distZ16,
                            screenBuf, zBuffer, viewW, nType == 4
                        );
                    }

                    // If this wall is shorter than the ceiling (variable-height wall!),
                    // clip yBot to the top of this wall and let the ray continue behind it!
                    if (wh < curCeilH && wTopY > yTop) {
                        yBot = wTopY - 1;
                        curFloorH = wh;
                        curCell = nextCell;
                        continue;
                    } else {
                        break;
                    }
                } else if (nType == 2) {
                    // Sliding / Rising Door Cell
                    int openAmt = doorOpen[nextCell] & 0xFF; // 0=closed, 120=open
                    int doorBotH = curFloorH + openAmt;
                    int doorTopH = ceilH[nextCell];
                    if (doorBotH < doorTopH) {
                        int dBotY = horizonY - (((doorBotH - camZ) * focalZ) / perpDist);
                        int dTopY = horizonY - (((doorTopH - camZ) * focalZ) / perpDist);
                        int drawStart = dTopY > yTop ? dTopY : yTop;
                        int drawEnd = dBotY < yBot ? dBotY : yBot;
                        int wTex = wallTex[nextCell] % MAX_WALL_TEX;
                        int dU = (texU + (openAmt >> 1)) & 63;
                        int colOff = (wTex << 12) | (dU << 6);
                        if (drawStart <= drawEnd) {
                            drawWallColumn(
                                x, drawStart, drawEnd,
                                doorBotH, doorTopH, dTopY, dBotY,
                                colOff, wPal, distZ16,
                                screenBuf, zBuffer, viewW, false
                            );
                        }
                        if (openAmt == 0) {
                            break;
                        }
                        if (dBotY > yTop) {
                            yTop = dBotY + 1;
                        }
                    }
                }

                // 3. Handle Multi-Step Floor Risers (Ступени: nextFloorH > curFloorH)
                int nextFloorH = floorH[nextCell];
                int nextCeilH  = ceilH[nextCell];

                if (nextFloorH > curFloorH) {
                    int stepTopY = horizonY - (((nextFloorH - camZ) * focalZ) / perpDist);
                    if (stepTopY <= yBot) {
                        int drawStart = stepTopY > yTop ? stepTopY : yTop;
                        int sTex = stepTex[nextCell] % MAX_WALL_TEX;
                        int colOff = (sTex << 12) | (texU << 6);
                        drawWallColumn(
                            x, drawStart, yBot,
                            curFloorH, nextFloorH, stepTopY, curFloorY,
                            colOff, wPal, distZ16,
                            screenBuf, zBuffer, viewW, false
                        );
                        yBot = stepTopY - 1;
                    }
                }

                // 4. Handle Upper Wall / Lowered Ceiling Transitions (nextCeilH < curCeilH)
                if (nextCeilH < curCeilH && yTop <= yBot) {
                    int upperBotY = horizonY - (((nextCeilH - camZ) * focalZ) / perpDist);
                    if (upperBotY >= yTop) {
                        int drawEnd = upperBotY < yBot ? upperBotY : yBot;
                        int uTex = wallTex[nextCell] % MAX_WALL_TEX;
                        int colOff = (uTex << 12) | (texU << 6);
                        drawWallColumn(
                            x, yTop, drawEnd,
                            nextCeilH, curCeilH, curCeilY, upperBotY,
                            colOff, wPal, distZ16,
                            screenBuf, zBuffer, viewW, false
                        );
                        yTop = upperBotY + 1;
                    }
                }

                curFloorH = nextFloorH;
                curCeilH  = nextCeilH;
                curCell   = nextCell;
            }

            // Fill any remaining unclosed pixels between yTop and yBot with horizon fog
            if (yTop <= yBot) {
                int fogColor = lightPal[2][16];
                for (int y = yTop; y <= yBot; y++) {
                    int idx = y * viewW + x;
                    screenBuf[idx] = fogColor;
                    zBuffer[idx] = 30000;
                }
            }
        }
    }

    private void drawWallColumn(
        int x, int yStart, int yEnd,
        int zBotWorld, int zTopWorld, int unclippedTopY, int unclippedBotY,
        int colOff, int[] pal, short distZ16,
        int[] screenBuf, short[] zBuffer, int viewW, boolean isExitPortal
    ) {
        int spanPixels = unclippedBotY - unclippedTopY;
        if (spanPixels <= 0) spanPixels = 1;
        int worldHeight = zTopWorld - zBotWorld;
        if (worldHeight <= 0) worldHeight = 64;

        // Map world Z height to texture V (64 texels per 128 height units = 16.16 fixed-point)
        int vRange = (worldHeight * 64) / 96;
        if (vRange < 16) vRange = 16;
        int vStep = (vRange << 16) / spanPixels;
        int vPos = (yStart - unclippedTopY) * vStep;

        int idx = yStart * viewW + x;
        for (int y = yStart; y <= yEnd; y++) {
            int tv = (vPos >> 16) & 63;
            int texel = wallAtlas[colOff + tv] & 0xFF;
            if (texel != 255) {
                int c = pal[texel];
                if (isExitPortal && ((y + x) & 3) == 0) {
                    c = 0xFF40E0FF; // Glowing cyan rift energy shimmer on exit trigger
                }
                screenBuf[idx] = c;
                zBuffer[idx] = distZ16;
            }
            vPos += vStep;
            idx += viewW;
        }
    }

    private void drawFloorOrCeilSpan(
        int x, int yStart, int yEnd, int relZ, int focalZ,
        int camX, int camY, int rayDirX, int rayDirY,
        int floorTexOff, int[] pal, int[] screenBuf, short[] zBuffer,
        int viewW, int horizonY
    ) {
        int absRelZ = relZ < 0 ? -relZ : relZ;
        if (absRelZ < 2) absRelZ = 2;
        int num = absRelZ * focalZ;

        int idx = yStart * viewW + x;
        for (int y = yStart; y <= yEnd; y++) {
            int dy = y - horizonY;
            if (dy < 0) dy = -dy;
            if (dy == 0) dy = 1;
            int rowDist = num / dy;
            if (rowDist > 30000) rowDist = 30000;

            int wx = camX + (int) (((long) rowDist * rayDirX) >> 16);
            int wy = camY + (int) (((long) rowDist * rayDirY) >> 16);
            int tx = (wx >> 2) & 63;
            int ty = (wy >> 2) & 63;

            int texel = floorAtlas[floorTexOff + (ty << 6) + tx] & 0xFF;
            screenBuf[idx] = pal[texel];
            zBuffer[idx] = (short) rowDist;
            idx += viewW;
        }
    }
}
