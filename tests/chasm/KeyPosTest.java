package chasm;

/**
 * Regression test: every shipped level must contain its key card, in a real sector,
 * with the expected model id (1 = green G_CARD, 2/3 = red KEY).
 * The key model id is what GameWorld uses for the HUD name, the effect and the mesh.
 */
public final class KeyPosTest {
    public static void main(String[] args) {
        GameWorld w = new GameWorld();
        int[] expected = { GameWorld.ITEM_KEY_GREEN, GameWorld.ITEM_KEY_RED, GameWorld.ITEM_KEY_RED };
        boolean ok = true;
        for (int lv = 0; lv < GameWorld.NUM_LEVELS; lv++) {
            w.resetInventory();
            w.loadLevel(lv);
            int keys = 0;
            for (int i = 0; i < w.itemCount; i++) {
                int id = w.itemModelId[i];
                if (id < GameWorld.ITEM_KEY_RED || id > GameWorld.ITEM_KEY_BLUE) continue;
                keys++;
                Sector s = w.itemSector[i];
                boolean good = (s != null) && (id == expected[lv]);
                System.out.println("level " + (lv + 1) + " key model " + id + " name "
                        + GameWorld.ITEM_NAMES[id] + " at " + (w.itemX[i] >> 16) + ","
                        + (w.itemZ[i] >> 16) + " sector=" + (s != null ? "found" : "null")
                        + (good ? "" : " <-- WRONG"));
                if (!good) ok = false;
            }
            if (keys == 0) {
                System.out.println("level " + (lv + 1) + ": NO KEY <-- WRONG");
                ok = false;
            }
        }
        System.out.println(ok ? "key cards valid" : "KEY CARDS INVALID");
        if (!ok) System.exit(1);
    }
}
