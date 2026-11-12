package chasm;

import java.util.Arrays;

public final class ViewportScalerTest {
    private static void check(int sw, int sh, int dw, int dh) {
        int[] src = new int[sw * sh], actual = new int[dw * dh], expected = new int[dw * dh];
        int[] xm = new int[dw], ym = new int[dh];
        for (int i = 0; i < src.length; i++) src[i] = i * 131 + 0x12340000;
        for (int x = 0; x < dw; x++) xm[x] = x * sw / dw;
        for (int y = 0; y < dh; y++) ym[y] = (y * sh / dh) * sw;
        for (int y = 0; y < dh; y++)
            for (int x = 0; x < dw; x++) expected[y * dw + x] = src[(y * sh / dh) * sw + x * sw / dw];
        ViewportScaler.scale(src, actual, sw, dw, dh, xm, ym);
        if (!Arrays.equals(actual, expected))
            throw new AssertionError(sw + "x" + sh + " -> " + dw + "x" + dh);
    }
    public static void main(String[] args) {
        for (int sw = 1; sw <= 12; sw++) for (int sh = 1; sh <= 12; sh++)
            for (int dw = 1; dw <= 15; dw++) for (int dh = 1; dh <= 15; dh++) check(sw, sh, dw, dh);
        check(120, 144, 240, 288);
        check(120, 144, 240, 286);
        check(240, 288, 240, 286);
        check(240, 288, 176, 208);
        System.out.println("Viewport scaling pixel-exact across 32404 sizes");
    }
}
