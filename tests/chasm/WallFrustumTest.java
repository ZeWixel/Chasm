package chasm;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Random;

/** Compare early wall rejection with the original near/side clipping path. */
public final class WallFrustumTest {
    private static Method clip;
    private static Field[] fields;
    private static int comparisons, eligible;

    private static int[] result(Point2D a, Point2D b, boolean optimized) throws Exception {
        PortalRenderer.earlyWallFrustumReject = optimized;
        boolean visible = ((Boolean) clip.invoke(null, a, b, 40 << 16, -(20 << 16), 31)).booleanValue();
        if (!visible) return new int[] { 0 }; // rejected calls do not consume output globals
        int[] out = new int[11];
        out[0] = 1;
        int n = 1;
        for (int i = 0; i < fields.length; i++) {
            Object value = fields[i].get(null);
            if (value instanceof Point2D) {
                Point2D p = (Point2D) value;
                out[n++] = p.x;
                out[n++] = p.y;
            } else out[n++] = ((Integer) value).intValue();
        }
        return out;
    }

    private static void check(int x0, int z0, int x1, int z1) throws Exception {
        // fastHypot in the original path cannot safely take abs(MIN_VALUE).
        // Such overflowed segment deltas are outside the shipped map range.
        if (x0 - x1 == Integer.MIN_VALUE || z0 - z1 == Integer.MIN_VALUE) return;
        Point2D a = new Point2D(x0, z0), b = new Point2D(x1, z1);
        int[] reference = result(a, b, false), optimized = result(a, b, true);
        if (!Arrays.equals(reference, optimized))
            throw new AssertionError("wall mismatch: " + x0 + "," + z0 + " -> " + x1 + "," + z1);
        if (z0 >= 327680 && z1 >= 327680 && (z0 > 327680 || z1 > 327680)
                && (((long)z0 + x0 < 0 && (long)z1 + x1 < 0)
                    || ((long)z0 - x0 < 0 && (long)z1 - x1 < 0))) eligible++;
        comparisons++;
    }

    public static void main(String[] args) throws Exception {
        MathUtils.initializeMathTables();
        clip = PortalRenderer.class.getDeclaredMethod("clipAndProjectWallSegment",
                Point2D.class, Point2D.class, Integer.TYPE, Integer.TYPE, Integer.TYPE);
        clip.setAccessible(true);
        String[] names = { "clippedWallStart", "clippedWallEnd", "projectedCeilingStart",
                "projectedFloorStart", "projectedCeilingEnd", "projectedFloorEnd",
                "clippedTextureStartU", "clippedTextureEndU" };
        fields = new Field[names.length];
        for (int i = 0; i < names.length; i++) {
            fields[i] = PortalRenderer.class.getDeclaredField(names[i]);
            fields[i].setAccessible(true);
        }
        Random random = new Random(0x595);
        for (int fast = 0; fast < 2; fast++) {
            PortalRenderer.setFastResolution(fast != 0);
            // Exact near and side boundaries, with one fixed-point unit on either side.
            int[] depths = { 327679, 327680, 327681, 655360, 10000000 };
            for (int z0 : depths) for (int z1 : depths) {
                int[] xs0 = { -z0 - 1, -z0, -z0 + 1, 0, z0 - 1, z0, z0 + 1 };
                int[] xs1 = { -z1 - 1, -z1, -z1 + 1, 0, z1 - 1, z1, z1 + 1 };
                for (int x0 : xs0) for (int x1 : xs1) check(x0, z0, x1, z1);
            }
            for (int i = 0; i < 50000; i++) {
                int z0 = random.nextInt(20000000) - 1000000;
                int z1 = random.nextInt(20000000) - 1000000;
                check(random.nextInt(40000001) - 20000000, z0,
                      random.nextInt(40000001) - 20000000, z1);
            }
            // Exercise long half-plane arithmetic near the integer limits.
            int[] extremes = { Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -327681, -327680,
                    0, 327680, 327681, Integer.MAX_VALUE - 1, Integer.MAX_VALUE };
            int[] frontDepths = { 327680, 327681, 10000000, Integer.MAX_VALUE };
            for (int z0 : frontDepths) for (int z1 : frontDepths)
                for (int x0 : extremes) for (int x1 : extremes) check(x0, z0, x1, z1);
        }
        PortalRenderer.earlyWallFrustumReject = true;
        if (eligible == 0) throw new AssertionError("no early-reject cases");
        System.out.println("Wall clipping reference comparisons=" + comparisons + ", early-reject candidates=" + eligible);
    }
}
