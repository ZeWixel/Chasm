package chasm;

/** Nearest-neighbour viewport scaling with cached coordinate maps. */
final class ViewportScaler {
    private ViewportScaler() {}

    static void scale(int[] src, int[] dst, int srcW, int dstW, int dstH,
                      int[] xMap, int[] yRow) {
        int previousSrcRow = -1;
        for (int y = 0; y < dstH; y++) {
            int source = yRow[y];
            int target = y * dstW;
            if (source == previousSrcRow) {
                // Nearest-neighbour upscale often repeats rows. Copy the
                // completed row instead of calculating the same pixels again.
                System.arraycopy(dst, target - dstW, dst, target, dstW);
            } else if (srcW == dstW) {
                System.arraycopy(src, source, dst, target, dstW);
            } else if (dstW == srcW * 2) {
                // W595 fast mode: each source pixel becomes two columns.
                int end = source + srcW;
                while (source < end) {
                    int pixel = src[source++];
                    dst[target++] = pixel;
                    dst[target++] = pixel;
                }
            } else {
                for (int x = 0; x < dstW; x++) {
                    dst[target + x] = src[source + xMap[x]];
                }
            }
            previousSrcRow = yRow[y];
        }
    }
}
