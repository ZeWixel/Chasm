package chasm;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Random;

/** Compare the incremental rasterizer against the previous exact formula. */
public final class FlatRasterTest {
    private static void referenceFillTriangleFlat(
            int x0, int y0,
            int x1, int y1,
            int x2, int y2,
            int color,
            boolean usePortalClips,
            int[] screenBuffer) {

        // Sort vertices by Y ascending (y0 <= y1 <= y2)
        if (y0 > y1) { int t = x0; x0 = x1; x1 = t; t = y0; y0 = y1; y1 = t; }
        if (y1 > y2) { int t = x1; x1 = x2; x2 = t; t = y1; y1 = y2; y2 = t; }
        if (y0 > y1) { int t = x0; x0 = x1; x1 = t; t = y0; y0 = y1; y1 = t; }

        int totalH = y2 - y0;
        if (totalH <= 0) return;

        int startY = y0 < 0 ? 0 : y0;
        int endY = y2 >= PortalRenderer.VIEWPORT_HEIGHT ? PortalRenderer.VIEWPORT_HEIGHT - 1 : y2;
        if (startY > endY) return;

        short[] floorClip = Sector.floorClip;
        short[] ceilClip = Sector.ceilingClip;
        int width = PortalRenderer.VIEWPORT_WIDTH;

        // Edge steps in 16.16, computed once per triangle (no per-row divide).
        long lxS = ((long) (x2 - x0) << 16) / totalH;
        int seg1H = y1 - y0;
        long s1S = seg1H > 0 ? ((long) (x1 - x0) << 16) / seg1H : 0;
        int seg2H = y2 - y1;
        long s2S = seg2H > 0 ? ((long) (x2 - x1) << 16) / seg2H : 0;

        for (int y = startY; y <= endY; y++) {
            int dy = y - y0;
            long xa16 = ((long) x0 << 16) + lxS * dy;
            long xb16;
            if (y <= y1) {
                xb16 = seg1H > 0 ? ((long) x0 << 16) + s1S * dy : (long) x1 << 16;
            } else {
                xb16 = ((long) x1 << 16) + s2S * (y - y1);
            }

            int xa = (int) (xa16 >> 16);
            int xb = (int) (xb16 >> 16);
            if (xa > xb) { int t = xa; xa = xb; xb = t; }
            if (xb < 0 || xa >= width) continue;
            if (xa < 0) xa = 0;
            if (xb >= width) xb = width - 1;

            int rowOffset = y * width;
            if (usePortalClips) {
                for (int x = xa; x <= xb; x++) {
                    if (y >= floorClip[x] && y <= ceilClip[x]) {
                        screenBuffer[rowOffset + x] = color;
                    }
                }
            } else {
                for (int x = xa; x <= xb; x++) {
                    screenBuffer[rowOffset + x] = color;
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        new GameWorld();
        Method method = PolyRenderer3D.class.getDeclaredMethod("fillTriangleFlat",
                Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE,
                Integer.TYPE, Boolean.TYPE, int[].class);
        method.setAccessible(true);
        Random random = new Random(28271L);
        int cases = 0;
        for (int mode = 0; mode < 2; mode++) {
            PortalRenderer.setFastResolution(mode == 1);
            int w = PortalRenderer.VIEWPORT_WIDTH, h = PortalRenderer.VIEWPORT_HEIGHT;
            Sector.resetClipArrays();
            int[] actual = new int[w * h], expected = new int[w * h];
            for (int i = 0; i < 6000; i++) {
                Arrays.fill(actual, 0x12345678); Arrays.fill(expected, 0x12345678);
                for (int x = 0; x < w; x++) {
                    Sector.floorClip[x] = (short)(random.nextInt(h + 25) - 12);
                    Sector.ceilingClip[x] = (short)(random.nextInt(h + 25) - 12);
                }
                int x0 = random.nextInt(w * 4 + 1) - w * 2;
                int x1 = random.nextInt(w * 4 + 1) - w * 2;
                int x2 = random.nextInt(w * 4 + 1) - w * 2;
                int y0 = random.nextInt(h * 4 + 1) - h * 2;
                int y1 = random.nextInt(h * 4 + 1) - h * 2;
                int y2 = random.nextInt(h * 4 + 1) - h * 2;
                // Include horizontal edges and degenerate triangles frequently.
                if (i % 5 == 0) y1 = y0;
                if (i % 11 == 0) y2 = y1;
                boolean clip = (i & 1) == 0;
                int color = random.nextInt();
                referenceFillTriangleFlat(x0,y0,x1,y1,x2,y2,color,clip,expected);
                method.invoke(null, new Object[] {Integer.valueOf(x0), Integer.valueOf(y0),
                    Integer.valueOf(x1), Integer.valueOf(y1), Integer.valueOf(x2), Integer.valueOf(y2),
                    Integer.valueOf(color), Boolean.valueOf(clip), actual});
                if (!Arrays.equals(actual, expected)) throw new AssertionError("raster mismatch case=" + i + " mode=" + mode);
                cases++;
            }
        }
        PortalRenderer.setFastResolution(false);
        System.out.println("Flat rasterizer pixel-exact: " + cases + " clipped/degenerate cases");
    }
}
