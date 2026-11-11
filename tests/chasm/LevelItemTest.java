package chasm;

public final class LevelItemTest {
    public static void main(String[] args) {
        GameWorld w = new GameWorld();
        boolean ok = true;
        for (int lv = 0; lv < GameWorld.NUM_LEVELS; lv++) {
            w.resetInventory();
            w.loadLevel(lv);
            System.out.print("level " + (lv + 1) + ": " + w.itemCount + " pickups [");
            for (int i = 0; i < w.itemCount; i++) {
                int id = w.itemModelId[i];
                String name = (id >= 0 && id < GameWorld.ITEM_NAMES.length) ? GameWorld.ITEM_NAMES[id] : null;
                if (i > 0) System.out.print(", ");
                System.out.print(id + "=" + name);
                if (name == null || !w.itemActive[i]) {
                    System.out.print(" <-- INVALID");
                    ok = false;
                }
            }
            System.out.println("]");
        }
        System.out.println(ok ? "all shipped pickups valid" : "INVALID PICKUPS FOUND");
        if (!ok) System.exit(1);
    }
}
