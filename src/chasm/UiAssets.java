package chasm;

import java.io.DataInputStream;
import java.io.InputStream;
import javax.microedition.lcdui.Image;

/**
 * UI images restored from the first build's /ui.bin (LOADING.CEL logo, M_TILE1.CEL menu tile,
 * STATUS2.CEL HUD strip). Index 255 is transparent (ARGB alpha 0, drawn with processAlpha).
 */
public final class UiAssets {
    public static int logoW, logoH;
    public static int[] logo;
    public static int tileW, tileH;
    public static int[] tile;
    public static int hudW, hudH;
    public static int[] hud;
    /** 16x16 status icons from /hud_icons.bin: HP (marine face), AR (armour ring), AMMO (shells). */
    public static final int ICON_SIZE = 16;
    public static int[] iconHp, iconAr, iconAmmo;

    private static boolean loaded = false;

    private UiAssets() {}

    public static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        int[] pal = loadPalette();
        loadIcons(pal);
        // Попытка загрузить открытые PNG из res/open/graphics/ui/ (редактируемые)
        try {
            int[] oLogo = loadPngIconWithSize("/open/graphics/ui/logo_128x76.png");
            int[] oTile = loadPngIconWithSize("/open/graphics/ui/tile_64x64.png");
            int[] oHud = loadPngIconWithSize("/open/graphics/ui/hud_160x32.png");
            if (oLogo != null) { logo = oLogo; logoW = 128; logoH = 76; }
            if (oTile != null) { tile = oTile; tileW = 64; tileH = 64; }
            if (oHud != null) { hud = oHud; hudW = 160; hudH = 32; }
            if (oLogo != null || oTile != null || oHud != null) return;
        } catch (Throwable t) {}
        InputStream is = null;
        try {
            is = UiAssets.class.getResourceAsStream("/ui.bin");
            if (is == null) return;
            DataInputStream dis = new DataInputStream(is);
            logoW = dis.readUnsignedShort();
            logoH = dis.readUnsignedShort();
            logo = readImage(dis, logoW * logoH, pal);
            tileW = dis.readUnsignedShort();
            tileH = dis.readUnsignedShort();
            tile = readImage(dis, tileW * tileH, pal);
            hudW = dis.readUnsignedShort();
            hudH = dis.readUnsignedShort();
            hud = readImage(dis, hudW * hudH, pal);
        } catch (Exception e) {
            logo = null;
            tile = null;
            hud = null;
        } finally {
            if (is != null) {
                try { is.close(); } catch (Exception ex) {}
            }
        }
    }

    private static int[] loadPngIconWithSize(String path) {
        try {
            InputStream is = UiAssets.class.getResourceAsStream(path);
            if (is == null) return null;
            Image img = Image.createImage(is);
            int w = img.getWidth();
            int h = img.getHeight();
            int[] argb = new int[w * h];
            img.getRGB(argb, 0, w, 0, 0, w, h);
            return argb;
        } catch (Exception e) {
            return null;
        }
    }

    private static void loadIcons(int[] pal) {
        // Сначала пробуем открытые PNG из res/open/graphics/hud/ (редактируемые)
        try {
            int[] hp = loadPngIcon("/open/graphics/hud/hp.png");
            int[] ar = loadPngIcon("/open/graphics/hud/armor.png");
            int[] am = loadPngIcon("/open/graphics/hud/ammo.png");
            if (hp != null && ar != null && am != null) {
                iconHp = hp;
                iconAr = ar;
                iconAmmo = am;
                return;
            }
        } catch (Throwable t) {}
        InputStream is = null;
        try {
            is = UiAssets.class.getResourceAsStream("/hud_icons.bin");
            if (is == null) return;
            DataInputStream dis = new DataInputStream(is);
            int[][] out = new int[3][];
            for (int k = 0; k < 3; k++) {
                int w = dis.readUnsignedShort();
                int h = dis.readUnsignedShort();
                out[k] = readImage(dis, w * h, pal);
            }
            iconHp = out[0];
            iconAr = out[1];
            iconAmmo = out[2];
        } catch (Exception e) {
            iconHp = null;
            iconAr = null;
            iconAmmo = null;
        } finally {
            if (is != null) {
                try { is.close(); } catch (Exception ex) {}
            }
        }
    }

    private static int[] loadPngIcon(String path) {
        try {
            InputStream is = UiAssets.class.getResourceAsStream(path);
            if (is == null) return null;
            Image img = Image.createImage(is);
            int w = img.getWidth();
            int h = img.getHeight();
            int[] argb = new int[w * h];
            img.getRGB(argb, 0, w, 0, 0, w, h);
            return argb;
        } catch (Exception e) {
            return null;
        }
    }

    private static int[] readImage(DataInputStream dis, int count, int[] pal) throws Exception {
        byte[] raw = new byte[count];
        dis.readFully(raw);
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            int idx = raw[i] & 0xFF;
            out[i] = (idx == 255) ? 0x00000000 : (0xFF000000 | pal[idx]);
        }
        return out;
    }

    private static int[] loadPalette() {
        int[] pal = new int[256];
        InputStream is = null;
        try {
            is = UiAssets.class.getResourceAsStream("/pal.bin");
            byte[] raw = new byte[768];
            int got = 0;
            if (is != null) {
                while (got < 768) {
                    int n = is.read(raw, got, 768 - got);
                    if (n <= 0) break;
                    got += n;
                }
            }
            for (int i = 0; i < 256; i++) {
                pal[i] = (got >= 768)
                        ? (((raw[i * 3] & 0xFF) << 16) | ((raw[i * 3 + 1] & 0xFF) << 8) | (raw[i * 3 + 2] & 0xFF))
                        : ((i << 16) | (i << 8) | i);
            }
        } catch (Exception e) {
            for (int i = 0; i < 256; i++) pal[i] = (i << 16) | (i << 8) | i;
        } finally {
            if (is != null) {
                try { is.close(); } catch (Exception ex) {}
            }
        }
        return pal;
    }
}
