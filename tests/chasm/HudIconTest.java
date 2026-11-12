package chasm;

import java.io.DataInputStream;

/** Verify the icon loaded by the HUD is the packaged indexed armour art. */
public final class HudIconTest {
    public static void main(String[] args) throws Exception {
        DataInputStream icons = new DataInputStream(HudIconTest.class.getResourceAsStream("/hud_icons.bin"));
        byte[] data = new byte[780];
        icons.readFully(data); icons.close();
        DataInputStream palette = new DataInputStream(HudIconTest.class.getResourceAsStream("/pal.bin"));
        byte[] pal = new byte[768];
        palette.readFully(pal); palette.close();
        UiAssets.ensureLoaded();
        if (UiAssets.iconAr == null || UiAssets.iconAr.length != 256) throw new AssertionError("armour icon missing");
        for (int i = 0; i < 256; i++) {
            int idx = data[264 + i] & 255;
            int rgb = idx == 255 ? 0 : 0xff000000 | ((pal[idx*3] & 255) << 16)
                    | ((pal[idx*3+1] & 255) << 8) | (pal[idx*3+2] & 255);
            if (UiAssets.iconAr[i] != rgb) throw new AssertionError("armour pixel " + i);
        }
        System.out.println("HUD armour icon loaded from packaged art OK");
    }
}
