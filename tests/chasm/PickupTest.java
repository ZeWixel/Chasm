package chasm;

public final class PickupTest {
    private static int failed = 0;
    private static int passed = 0;

    private static void check(String what, boolean ok) {
        if (ok) { passed++; }
        else { failed++; System.out.println("FAIL: " + what); }
    }

    private static void eq(String what, int got, int want) {
        check(what + " (got " + got + ", want " + want + ")", got == want);
    }

    public static void main(String[] args) {
        GameWorld w = new GameWorld();

        for (int id = GameWorld.ITEM_FIRST; id <= GameWorld.ITEM_LAST; id++) {
            check("ITEM_NAMES[" + id + "] not null", GameWorld.ITEM_NAMES[id] != null);
        }

        eq("weapon count", GameWorld.WEAPON_NAMES.length, 6);
        check("weapon name 0", "RIFLE".equals(GameWorld.WEAPON_NAMES[0]));
        check("weapon name 3", "BLADEGUN".equals(GameWorld.WEAPON_NAMES[3]));
        check("weapon name 4", "LASER CROSSBOW".equals(GameWorld.WEAPON_NAMES[4]));
        for (int i = 0; i < GameWorld.WEAPON_NAMES.length; i++) {
            check("no ':' in weapon name " + i, GameWorld.WEAPON_NAMES[i].indexOf(':') < 0);
        }

        w.resetInventory(); w.health = 50; w.armor = 0;
        check("medkit consumed", w.applyItemPickup(GameWorld.ITEM_MEDKIT));
        check("pickup message is 'GOT <name>' (was 'GOT1:RIFT RIFLE')",
                "GOT MEDKIT".equals(w.hudMessage));
        eq("medkit +20", w.health, 70);
        w.health = 100;
        check("medkit refused at full health", !w.applyItemPickup(GameWorld.ITEM_MEDKIT));
        eq("medkit does not over-heal", w.health, 100);

        w.health = 100;
        check("mega health consumed", w.applyItemPickup(GameWorld.ITEM_MEGA_HEALTH));
        eq("mega health to 200", w.health, 200);
        check("mega health refused when full", !w.applyItemPickup(GameWorld.ITEM_MEGA_HEALTH));

        w.armor = 0;
        check("armor consumed", w.applyItemPickup(GameWorld.ITEM_BODY_ARMOR));
        eq("body armor -> 200", w.armor, 200);
        check("armor refused at 200", !w.applyItemPickup(GameWorld.ITEM_BODY_ARMOR));
        w.armor = 0;
        check("helmet consumed", w.applyItemPickup(GameWorld.ITEM_HELMET));
        eq("helmet -> 100", w.armor, 100);
        w.armor = 200;
        check("helmet refused at 200", !w.applyItemPickup(GameWorld.ITEM_HELMET));

        w.resetInventory();
        w.applyItemPickup(GameWorld.ITEM_HELMET);
        check("helmet grants no weapon", !w.weaponOwned[1] && w.curWeapon == 0);

        w.resetInventory();
        check("ammo pack refused with rifle only", !w.applyItemPickup(GameWorld.ITEM_AMMO_PACK));
        w.weaponOwned[1] = true; w.ammo[1] = 0;
        w.weaponOwned[2] = true; w.ammo[2] = 190;
        check("ammo pack consumed", w.applyItemPickup(GameWorld.ITEM_AMMO_PACK));
        eq("shotgun +10 shells", w.ammo[1], 10);
        eq("volcano +10 capped at 200", w.ammo[2], 200);
        w.ammo[1] = 50; w.ammo[2] = 200;
        check("ammo pack refused when all full", !w.applyItemPickup(GameWorld.ITEM_AMMO_PACK));

        int[] ids = { GameWorld.ITEM_W_SHOTGUN, GameWorld.ITEM_W_VOLCANO, GameWorld.ITEM_W_BLADEGUN,
                      GameWorld.ITEM_W_CROSSBOW, GameWorld.ITEM_W_GRENADER };
        int[] slots = { 1, 2, 3, 4, 5 };
        int[] start = { 10, 50, 20, 20, 5 };
        for (int k = 0; k < ids.length; k++) {
            w.resetInventory();
            check("weapon pickup " + ids[k], w.applyItemPickup(ids[k]));
            check("weapon pickup message " + slots[k],
                    ("GOT " + GameWorld.WEAPON_NAMES[slots[k]]).equals(w.hudMessage));
            check("weapon " + slots[k] + " owned", w.weaponOwned[slots[k]]);
            eq("weapon " + slots[k] + " start ammo", w.ammo[slots[k]], start[k]);
            eq("weapon " + slots[k] + " selected", w.curWeapon, slots[k]);
            boolean stray = false;
            for (int q = 1; q < 6; q++) if (q != slots[k] && w.weaponOwned[q]) stray = true;
            check("weapon " + slots[k] + " no stray slot", !stray);
        }
        w.resetInventory(); w.weaponOwned[1] = true; w.ammo[1] = 50;
        check("shotgun pickup refused at cap", !w.applyItemPickup(GameWorld.ITEM_W_SHOTGUN));

        w.resetInventory(); w.ammo[0] = 0;
        check("red key", w.applyItemPickup(GameWorld.ITEM_KEY_RED) && w.hasRedKey);
        check("green key", w.applyItemPickup(GameWorld.ITEM_KEY_GREEN) && w.hasGreenKey);
        check("blue key", w.applyItemPickup(GameWorld.ITEM_KEY_BLUE) && w.hasBlueKey);
        check("second red key refused", !w.applyItemPickup(GameWorld.ITEM_KEY_RED));
        eq("keys give no rifle ammo", w.ammo[0], 0);

        w.resetInventory(); w.weaponOwned[1] = true; w.ammo[1] = 0;
        w.curWeapon = 1; w.weaponCooldown = 0; w.ammo[0] = 0;
        check("dry shotgun falls back", w.fireWeapon());
        eq("fell back to rifle", w.curWeapon, 0);
        eq("rifle ammo untouched", w.ammo[0], 0);
        w.weaponCooldown = 0; w.ammo[0] = 7;
        check("rifle fires", w.fireWeapon());
        eq("rifle never consumes ammo", w.ammo[0], 7);

        check("prop id not a pickup", !w.applyItemPickup(GameWorld.BARREL_MODEL));

        System.out.println("passed=" + passed + " failed=" + failed);
        if (failed != 0) System.exit(1);
    }
}
