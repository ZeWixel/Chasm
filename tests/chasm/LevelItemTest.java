package chasm;

public final class LevelItemTest {
    public static void main(String[] args) {
        GameWorld w = new GameWorld();
        boolean ok = true;
        // Every level must be playable with the full arsenal plus armour, helmet,
        // mega health, a medkit and an ammo pack.
        int[] required = {
            GameWorld.ITEM_MEDKIT, GameWorld.ITEM_MEGA_HEALTH, GameWorld.ITEM_BODY_ARMOR,
            GameWorld.ITEM_HELMET, GameWorld.ITEM_AMMO_PACK,
            GameWorld.ITEM_W_SHOTGUN, GameWorld.ITEM_W_VOLCANO, GameWorld.ITEM_W_BLADEGUN,
            GameWorld.ITEM_W_CROSSBOW, GameWorld.ITEM_W_GRENADER
        };
        for (int lv = 0; lv < GameWorld.NUM_LEVELS; lv++) {
            w.resetInventory();
            w.loadLevel(lv);
            boolean[] seen = new boolean[GameWorld.ITEM_LAST + 1];
            System.out.print("level " + (lv + 1) + ": " + w.itemCount + " pickups [");
            for (int i = 0; i < w.itemCount; i++) {
                int id = w.itemModelId[i];
                String name = (id >= 0 && id < GameWorld.ITEM_NAMES.length) ? GameWorld.ITEM_NAMES[id] : null;
                if (i > 0) System.out.print(", ");
                System.out.print(id + "=" + name);
                if (name == null || !w.itemActive[i]) {
                    System.out.print(" <-- INVALID");
                    ok = false;
                } else {
                    seen[id] = true;
                }
            }
            System.out.println("]");
            for (int r = 0; r < required.length; r++) {
                if (!seen[required[r]]) {
                    System.out.println("level " + (lv + 1) + ": missing "
                            + GameWorld.ITEM_NAMES[required[r]] + " <-- WRONG");
                    ok = false;
                }
            }
            int keys = 0;
            for (int k = GameWorld.ITEM_KEY_RED; k <= GameWorld.ITEM_KEY_BLUE; k++) if (seen[k]) keys++;
            if (keys == 0) {
                System.out.println("level " + (lv + 1) + ": missing key <-- WRONG");
                ok = false;
            }
        }
        System.out.println(ok ? "all shipped pickups valid" : "INVALID PICKUPS FOUND");
        if (!ok) System.exit(1);
    }
}
