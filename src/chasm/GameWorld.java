package chasm;

import java.io.DataInputStream;
import java.io.InputStream;

/**
 * Chasm: The Rift GameWorld built on CovertOps3D-RE's C3D2/C3B BSP-Portal Engine
 * and PolyRenderer3D (Z-buffer-free software 3D polygon renderer with .CAR
 * multi-part dismemberment).
 */
public final class GameWorld {

    // ==================== Portal World Fields (CovertOps3D-RE) ====================
    public static int MIN_WALL_HEIGHT = 16;
    public static int MIN_CEILING_CLEARANCE = 50;
    public static int PLAYER_HEIGHT_OFFSET = 40;

    public Point2D[] vertices;
    private Point2D[] transformedVertices;
    public WallDefinition[] wallDefinitions;
    public SectorData[] sectors;
    public WallSurface[] wallSurfaces;
    public Transform3D worldOrigin;
    public BSPNode[] bspNodes;
    public Sector[] bspSectors;
    public WallSegment[] wallSegments;
    private int rootBSPNodeIndex = -1;

    private Texture[] animatedWallTargets;
    private Texture[][] animatedWallFrames;
    private Sprite[] animatedFlatTargets;
    private Sprite[][] animatedFlatFrames;
    private int textureAnimationTimer;
    private int textureAnimationFrame;

    private final Point2D collisionTestPoint = new Point2D(0, 0);

    // ==================== Chasm Gameplay Constants ====================
    // Playable levels with data present in the CSM.BIN used by this build.
    // MAP.99 is the briefing background, not a playable mission, so it is not included.
    public static final int NUM_LEVELS = 3;
    public static final String[] LEVEL_NAMES = {
        "THE POWER STATION",
        "AROUND PYRAMIDS",
        "PYRAMID OF SHMUHER"
    };

    public static final String[][] LEVEL_BRIEFINGS = {
        {
            "MISSION 1: THE POWER STATION",
            "Time-channels have opened inside",
            "the power station. Clear the",
            "corridors and reach the exit."
        },
        {
            "MISSION 2: AROUND PYRAMIDS",
            "The rift has opened near the",
            "pyramids. Clear the area and",
            "find the way out."
        },
        {
            "MISSION 3: PYRAMID OF SHMUHER",
            "Deep inside the pyramid the",
            "distortion is strongest. Clear",
            "the chambers and move on."
        }
    };

    // Monster AI states
    public static final int MSTATE_INACTIVE = 0;
    public static final int MSTATE_IDLE     = 1;
    public static final int MSTATE_CHASE    = 2;
    public static final int MSTATE_ATTACK   = 3;
    public static final int MSTATE_PAIN     = 4;
    public static final int MSTATE_DEAD     = 5;

    // Compatibility aliases for HUD / minimap
    public static final int STATE_IDLE   = MSTATE_IDLE;
    public static final int STATE_CHASE  = MSTATE_CHASE;
    public static final int STATE_ATTACK = MSTATE_ATTACK;
    public static final int STATE_PAIN   = MSTATE_PAIN;
    public static final int STATE_DEAD   = MSTATE_DEAD;

    // Order matches model slots 0..10 in models.bin (verified vs https://chasmtherift.fandom.com/wiki/Category:Enemies)
    // Matches monster order in models.bin (MONGF, WMAN, SPIDER, TT_TOWER, FAUST1, DEADMAN1, PANT1, JOKER6, SCELET3, LION4, VIKING4)
    public static final String[] MONSTER_NAMES = {
        "MONG", "STRATOS", "SCORPION", "TURRET", "FAUST", "ZOMBIE",
        "PUNISHER", "JOKER", "SKELETON", "LIONMAN", "VIKING"
    };

    public static final int[] MONSTER_MAX_HP = {
        70, 85, 95, 65, 90, 45, 80, 100, 120, 145, 180
    };

    public static final int ENEMY_SIMPLE = 0;
    public static final int ENEMY_AGGRESSIVE = 1;
    public int enemyBehavior = ENEMY_SIMPLE; // 0 = default 380, 1 = aggressive ~1.6x visibility
    public static final String[] ENEMY_BEHAVIOR_NAMES = { "SIMPLE", "AGGRESSIVE" };

    // Weapon slots follow the original Chasm arsenal (CHASM.INF [WEAPONS], keys 1..8):
    //   0 Rifle (unlimited ammo), 1 Super Shotgun, 2 Volcano, 3 BladeGun,
    //   4 Laser Crossbow, 5 Grenader. Slots 6/7 (Land Mines, Mega Destroyer) are not ported.
    // The names carry no slot digit (the HUD shows the name only, and the pickup message
    // is "GOT <name>"); the old "1:RIFT RIFLE" strings produced "GOT1:RIFT RIFLE".
    public static final String[] WEAPON_NAMES = {
        "RIFLE",
        "SUPER SHOTGUN",
        "VOLCANO",
        "BLADEGUN",
        "LASER CROSSBOW",
        "GRENADER"
    };

    // Weapon-in-hand models (models.bin 11..16 = OGUN6, RIF_H5, V8H, DISK, CROSS, GR4).
    // Slot 3 (BladeGun) uses the DISK model, slot 4 (Laser Crossbow) uses CROSS.
    public static final int[] WEAPON_MODEL_IDS = { 11, 12, 13, 14, 15, 16 };

    // Per-weapon ammo cap, from the CHASM.INF "Limit" column. Slot 0 is unlimited.
    public static final int[] WEAPON_AMMO_LIMIT = { 0, 50, 200, 50, 40, 50 };

    // ==================== Pickup models (models.bin 17..29) ====================
    // One id per model, in the exact order tools/build_assets.py writes the .3O files:
    //   17 LIFE.3O (medkit)      18 BIGLIFE.3O (mega health)
    //   19 ARMOR3.3O (body armour) 20 HELMET1.3O (helmet)
    //   21 BAG.3O (ammo backpack) 22 W_RIFLE.3O (super shotgun pickup)
    //   23 W_VCANO.3O (Volcano)   24 WDISK.3O (BladeGun)
    //   25 WCROSS.3O (crossbow)   26 W_GR.3O (Grenader)
    //   27 R_CARD.3O  28 G_CARD.3O  29 B_CARD.3O (keys)
    // The effects are the original CHASM.INF [3D_OBJECTS] a_codes (see README.md).
    public static final int ITEM_FIRST       = 17;
    public static final int ITEM_MEDKIT      = 17;
    public static final int ITEM_MEGA_HEALTH = 18;
    public static final int ITEM_BODY_ARMOR  = 19;
    public static final int ITEM_HELMET      = 20;
    public static final int ITEM_AMMO_PACK   = 21;
    public static final int ITEM_W_SHOTGUN   = 22;
    public static final int ITEM_W_VOLCANO   = 23;
    public static final int ITEM_W_BLADEGUN  = 24;
    public static final int ITEM_W_CROSSBOW  = 25;
    public static final int ITEM_W_GRENADER  = 26;
    public static final int ITEM_KEY_RED     = 27;
    public static final int ITEM_KEY_GREEN   = 28;
    public static final int ITEM_KEY_BLUE    = 29;
    public static final int ITEM_LAST        = 29;

    public static final int NOMINAL_HEALTH = 100;
    public static final int MAX_HEALTH     = 200;
    public static final int MAX_ARMOR      = 200;

    /** Display names for pickup models, indexed by model id (17..29). */
    public static final String[] ITEM_NAMES = new String[ITEM_LAST + 1];
    static {
        ITEM_NAMES[ITEM_MEDKIT]      = "MEDKIT";
        ITEM_NAMES[ITEM_MEGA_HEALTH] = "MEGA HEALTH";
        ITEM_NAMES[ITEM_BODY_ARMOR]  = "BODY ARMOR";
        ITEM_NAMES[ITEM_HELMET]      = "HELMET";
        ITEM_NAMES[ITEM_AMMO_PACK]   = "AMMO PACK";
        ITEM_NAMES[ITEM_W_SHOTGUN]   = "SUPER SHOTGUN";
        ITEM_NAMES[ITEM_W_VOLCANO]   = "VOLCANO";
        ITEM_NAMES[ITEM_W_BLADEGUN]  = "BLADEGUN";
        ITEM_NAMES[ITEM_W_CROSSBOW]  = "LASER CROSSBOW";
        ITEM_NAMES[ITEM_W_GRENADER]  = "GRENADER";
        ITEM_NAMES[ITEM_KEY_RED]     = "RED KEY";
        ITEM_NAMES[ITEM_KEY_GREEN]   = "GREEN KEY";
        ITEM_NAMES[ITEM_KEY_BLUE]    = "BLUE KEY";
    }

    // Открытый текст: переопределяет хардкод если есть res/open/texts/strings.txt
    static {
        try {
            TextTable.ensureLoaded();
            for (int i = 0; i < LEVEL_NAMES.length; i++) {
                String v = TextTable.get("LEVEL_" + (i+1) + "_NAME", null);
                if (v != null) LEVEL_NAMES[i] = v;
                for (int k = 0; k < 4; k++) {
                    String bk = TextTable.get("LEVEL_" + (i+1) + "_BRIEF_" + (k+1), null);
                    if (bk != null) LEVEL_BRIEFINGS[i][k] = bk;
                }
            }
            for (int i = 0; i < MONSTER_NAMES.length; i++) {
                String v = TextTable.get("MONSTER_" + i, null);
                if (v != null) MONSTER_NAMES[i] = v;
            }
            for (int i = 0; i < WEAPON_NAMES.length; i++) {
                String v = TextTable.get("WEAPON_" + i, null);
                if (v != null) WEAPON_NAMES[i] = v;
            }
            for (int i = ITEM_FIRST; i <= ITEM_LAST; i++) {
                if (ITEM_NAMES[i] == null) continue;
                String v = TextTable.get("ITEM_" + i, null);
                if (v != null) ITEM_NAMES[i] = v;
            }
        } catch (Throwable t) {}
    }
    public static final int[] WEAPON_COOLDOWN  = { 5, 11, 3, 9, 8, 14 };
    /** Grenader projectile damage; a player bolt with this damage explodes on impact. */
    public static final int GRENADE_DAMAGE = 85;
    public static final int[] WEAPON_DAMAGE    = { 24, 52, 18, 42, 48, GRENADE_DAMAGE };
    // Slot 0 is the rifle: unlimited, so it never consumes ammo.
    public static final int[] WEAPON_AMMO_COST = { 0, 2, 1, 1, 1, 2 };
    // Ammo added to each owned weapon by an ammo pack (BAG.3O), CHASM.INF DAm.
    public static final int[] WEAPON_AMMO_PACK = { 0, 10, 10, 20, 10, 5 };

    // Monster AI states
    // ==================== Player & Renderer State ====================
    public final PhysicsBody playerBody = new PhysicsBody(0, 0, 0, 0);
    public final PolyRenderer3D polyRenderer;

    public int currentLevel = 0;
    public int levelTick = 0;

    // Jump state (16.16 world units): '0' key launches, gravity brings the eye back down.
    public int jumpOffset = 0;
    public int jumpVel = 0;
    public int health = NOMINAL_HEALTH;
    public int armor = 0;
    public int curWeapon = 0;
    public final boolean[] weaponOwned = new boolean[6];
    public final int[] ammo = new int[6];

    // ==================== Key cards (models 27/28/29) ====================
    public boolean hasRedKey = false;
    public boolean hasGreenKey = false;
    public boolean hasBlueKey = false;

    public int weaponCooldown = 0;
    public int weaponAnimTick = 0;
    public int muzzleFlashTimer = 0;
    public int damageFlashTimer = 0;
    public int walkBobPhase = 0;

    public String hudMessage = "CHASM: THE RIFT";
    public int hudMessageTimer = 45;
    public boolean levelCompleted = false;

    /** Developer mode (pause menu toggle): god mode + extra debug HUD. Persists across levels. */
    public boolean devMode = false;
    public boolean gameOver = false;

    // ==================== 3D Monsters (with .CAR Dismemberment) ====================
    public static final int MAX_MONSTERS = 32;
    public int monCount = 0;
    public final int[] monType      = new int[MAX_MONSTERS];
    public final int[] monX         = new int[MAX_MONSTERS];
    /** Prop-avoidance state: ticks left on the current detour and its side (+1 / -1). */
    private static final int DETOUR_TICKS = 24;
    private final int[] monDetourTicks = new int[MAX_MONSTERS];
    private final int[] monDetourSide  = new int[MAX_MONSTERS];
    public final int[] monZ         = new int[MAX_MONSTERS];
    public final int[] monAngle     = new int[MAX_MONSTERS];
    public final int[] monHP        = new int[MAX_MONSTERS];
    public final int[] monMaxHP     = new int[MAX_MONSTERS];
    public final int[] monState     = new int[MAX_MONSTERS];
    public final int[] monStateTick = new int[MAX_MONSTERS];
    public final int[] monFrameA    = new int[MAX_MONSTERS];
    public final int[] monFrameB    = new int[MAX_MONSTERS];
    public final int[] monInterp    = new int[MAX_MONSTERS];
    public final int[] monPartMask  = new int[MAX_MONSTERS];
    public final int[] monPainTimer = new int[MAX_MONSTERS];
    public final Sector[] monSector = new Sector[MAX_MONSTERS];

    // ==================== Detached Severed Body Parts (Submodels 0..2) ====================
    public static final int MAX_LIMBS = 32;
    public final boolean[] limbActive      = new boolean[MAX_LIMBS];
    public final int[] limbMonsterType     = new int[MAX_LIMBS];
    public final int[] limbSubmodelIdx     = new int[MAX_LIMBS];
    public final int[] limbX               = new int[MAX_LIMBS];
    public final int[] limbY               = new int[MAX_LIMBS];
    public final int[] limbZ               = new int[MAX_LIMBS];
    public final int[] limbVX              = new int[MAX_LIMBS];
    public final int[] limbVY              = new int[MAX_LIMBS];
    public final int[] limbVZ              = new int[MAX_LIMBS];
    public final int[] limbAngle           = new int[MAX_LIMBS];
    public final int[] limbFloorY          = new int[MAX_LIMBS];
    public final int[] limbTimer           = new int[MAX_LIMBS];
    public final Sector[] limbSector       = new Sector[MAX_LIMBS];

    // ==================== 3D Items (Pickups) ====================
    public static final int MAX_ITEMS = 48;
    public int itemCount = 0;
    public final boolean[] itemActive  = new boolean[MAX_ITEMS];
    public final int[] itemType        = new int[MAX_ITEMS];
    public final int[] itemModelId     = new int[MAX_ITEMS];
    public final int[] itemX           = new int[MAX_ITEMS];
    public final int[] itemZ           = new int[MAX_ITEMS];
    public final Sector[] itemSector   = new Sector[MAX_ITEMS];

    // ==================== 3D Props (Barrels, Pillars, Crates) ====================
    public static final int MAX_PROPS = 24;
    public int propCount = 0;
    public final boolean[] propActive  = new boolean[MAX_PROPS];
    public final int[] propModelId     = new int[MAX_PROPS];
    public final int[] propX           = new int[MAX_PROPS];
    public final int[] propZ           = new int[MAX_PROPS];
    public final int[] propAngle       = new int[MAX_PROPS];
    public final Sector[] propSector   = new Sector[MAX_PROPS];
    /** Simplified collision box half-extents (world units, 16.16), read from the sidecar. */
    public final int[] propHalfX       = new int[MAX_PROPS];
    public final int[] propHalfZ       = new int[MAX_PROPS];

    // ==================== Explosive barrels (prop model 30, BAREL1.3O) ====================
    public static final int BARREL_MODEL = 30;
    public static final int BARREL_HP = 40;
    public static final int BARREL_BLAST_DAMAGE = 90;
    public static final int BARREL_BLAST_RADIUS = 192;
    private final int[] propHP = new int[MAX_PROPS];
    private final int[] blastQueue = new int[MAX_PROPS];
    public static final int MAX_BLAST_FX = 3;
    public static final int BLAST_FX_LIFE = 10;
    public static final int BLAST_FLASH_TICKS = 4;
    private final int[] blastFxX = new int[MAX_BLAST_FX];
    private final int[] blastFxY = new int[MAX_BLAST_FX];
    private final int[] blastFxZ = new int[MAX_BLAST_FX];
    private final int[] blastFxLeft = new int[MAX_BLAST_FX];
    public int blastFlashTimer = 0;
    private final int[] blastScreen = new int[2];

    // ==================== 3D Projectiles & Gibs ====================
    public static final int MAX_PROJ = 24;
    /** Projectile model ids (models.bin): 33 = gib particle, 34 = saw-disk, 35 = energy bolt. */
    public static final int PROJ_GIB  = 33;
    public static final int PROJ_DISK = 34;
    public static final int PROJ_BOLT = 35;
    public final boolean[] projActive    = new boolean[MAX_PROJ];
    public final boolean[] projFromPlayer= new boolean[MAX_PROJ];
    public final int[] projModelId       = new int[MAX_PROJ];
    public final int[] projX             = new int[MAX_PROJ];
    public final int[] projY             = new int[MAX_PROJ];
    public final int[] projZ             = new int[MAX_PROJ];
    public final int[] projVX            = new int[MAX_PROJ];
    public final int[] projVY            = new int[MAX_PROJ];
    public final int[] projVZ            = new int[MAX_PROJ];
    public final int[] projAngle         = new int[MAX_PROJ];
    public final int[] projSpin          = new int[MAX_PROJ];
    public final int[] projDamage        = new int[MAX_PROJ];
    public final int[] projLife          = new int[MAX_PROJ];
    public final Sector[] projSector     = new Sector[MAX_PROJ];

    public int monstersKilled = 0;
    public int limbsSevered = 0;
    private int rngState = 0x5A3C9E17;

    public GameWorld() {
        PortalRenderer.initialize();
        Model3D[] models = Model3D.loadAllModels("/models.bin");
        this.polyRenderer = new PolyRenderer3D(models);
        resetInventory();
    }

    public int nextRand() {
        rngState ^= (rngState << 13);
        rngState ^= (rngState >>> 17);
        rngState ^= (rngState << 5);
        return rngState & 0x7FFFFFFF;
    }

    public void resetInventory() {
        health = NOMINAL_HEALTH;
        armor = 0;
        curWeapon = 0;
        for (int i = 0; i < 6; i++) {
            // As in the original game the player starts with the rifle and nothing else;
            // every other weapon is a pickup. The rifle needs no ammo.
            weaponOwned[i] = (i == 0);
            ammo[i] = 0;
        }
        hasRedKey = false;
        hasGreenKey = false;
        hasBlueKey = false;
        limbsSevered = 0;
        monstersKilled = 0;
    }

    // ==================== CovertOps3D-RE Portal Engine Methods ====================

    public final BSPNode getRootBSPNode() {
        int root = rootBSPNodeIndex >= 0 ? rootBSPNodeIndex : this.bspNodes.length - 1;
        return this.bspNodes[root];
    }

    public final void setRootBSPNodeIndex(int rootNodeIndex) {
        if (rootNodeIndex < 0 || rootNodeIndex >= this.bspNodes.length) {
            throw new IllegalArgumentException("Invalid BSP root");
        }
        rootBSPNodeIndex = rootNodeIndex;
    }

    public final void setVertices(Point2D[] newVertices) {
        this.vertices = newVertices;
        this.transformedVertices = new Point2D[newVertices.length];
        for (int i = 0; i < this.transformedVertices.length; i++) {
            this.transformedVertices[i] = new Point2D(0, 0);
        }
    }

    public final Point2D[] transformVertices(int originX, int originY, int angle) {
        long sinAngle = (long) MathUtils.fastSin(angle);
        long cosAngle = (long) MathUtils.fastCos(angle);

        for (int i = 0; i < this.vertices.length; i++) {
            int relativeX = this.vertices[i].x - originX;
            int relativeY = this.vertices[i].y - originY;

            this.transformedVertices[i].x = (int) ((cosAngle * (long) relativeX - sinAngle * (long) relativeY) >> 16);
            this.transformedVertices[i].y = (int) ((sinAngle * (long) relativeX + cosAngle * (long) relativeY) >> 16);
        }

        return this.transformedVertices;
    }

    public final Sector getSectorAtPoint(int x, int z) {
        return this.getRootBSPNode().findSectorNodeAtPoint(x, z);
    }

    public final SectorData getSectorDataAtPoint(int x, int z) {
        return this.getRootBSPNode().findSectorAtPoint(x, z);
    }

    public final void initializeWorld() {
        for (int i = 0; i < this.bspNodes.length; i++) {
            this.bspNodes[i].initializeBSPNode(this);
        }
        for (int i = 0; i < this.wallDefinitions.length; i++) {
            this.wallDefinitions[i].initializeWall(this);
        }
        for (int i = 0; i < this.wallSurfaces.length; i++) {
            this.wallSurfaces[i].resolveSectorLink(this);
        }
        for (int i = 0; i < this.wallSegments.length; i++) {
            this.wallSegments[i].initializeWallSegment(this);
        }
        for (int i = 0; i < this.bspSectors.length; i++) {
            this.bspSectors[i].initializeWalls(this);
        }
        this.getRootBSPNode().calculateVisibleSectors();
    }

    public final void installTextureAnimations(Texture[] wallTargets, Texture[][] wallFrames,
                                               Sprite[] flatTargets, Sprite[][] flatFrames) {
        this.animatedWallTargets = wallTargets;
        this.animatedWallFrames = wallFrames;
        this.animatedFlatTargets = flatTargets;
        this.animatedFlatFrames = flatFrames;
        this.textureAnimationTimer = 0;
        this.textureAnimationFrame = 0;
    }

    public final void advanceTextureAnimations() {
        if ((animatedWallTargets == null || animatedWallTargets.length == 0)
                && (animatedFlatTargets == null || animatedFlatTargets.length == 0)) {
            return;
        }
        if (++textureAnimationTimer < 6) {
            return;
        }
        textureAnimationTimer = 0;
        ++textureAnimationFrame;

        if (animatedWallTargets != null) {
            for (int index = 0; index < animatedWallTargets.length; ++index) {
                Texture[] frames = animatedWallFrames[index];
                Texture frame = frames[textureAnimationFrame % frames.length];
                Texture target = animatedWallTargets[index];
                target.pixelData = frame.pixelData;
                target.colorPalettes = frame.colorPalettes;
            }
        }
        if (animatedFlatTargets != null) {
            for (int index = 0; index < animatedFlatTargets.length; ++index) {
                Sprite[] frames = animatedFlatFrames[index];
                Sprite frame = frames[textureAnimationFrame % frames.length];
                Sprite target = animatedFlatTargets[index];
                target.pixelData = frame.pixelData;
                target.colorPalettes = frame.colorPalettes;
                target.flatColors = frame.flatColors;
            }
        }
    }

    // ==================== Level & Entity Sidecar Loading ====================

    public void loadLevel(int levelIndex) {
        if (levelIndex < 0) levelIndex = 0;
        if (levelIndex >= NUM_LEVELS) levelIndex = 0;
        this.currentLevel = levelIndex;
        this.levelTick = 0;
        this.levelCompleted = false;
        this.gameOver = false;
        this.muzzleFlashTimer = 0;
        this.damageFlashTimer = 0;
        this.weaponCooldown = 0;
        this.weaponAnimTick = 0;

        String c3bPath = "/levels/level" + (levelIndex + 1) + ".c3b";
        CustomLevelLoader.load(c3bPath, false, this);

        if (this.worldOrigin != null) {
            this.playerBody.copyFrom(this.worldOrigin);
        }

        // Clear dynamic arrays
        this.monCount = 0;
        this.itemCount = 0;
        this.propCount = 0;
        for (int i = 0; i < MAX_LIMBS; i++) limbActive[i] = false;
        for (int i = 0; i < MAX_PROJ; i++) projActive[i] = false;
        this.blastFlashTimer = 0;
        for (int i = 0; i < MAX_BLAST_FX; i++) blastFxLeft[i] = 0;

        // Load Chasm 3D entities sidecar
        String entPath = "/levels/entities" + (levelIndex + 1) + ".bin";
        loadEntitiesSidecar(entPath);

        setMessage(LEVEL_NAMES[levelIndex], 45);
    }

    private void loadEntitiesSidecar(String entPath) {
        try {
            InputStream is = GameWorld.class.getResourceAsStream(entPath);
            if (is == null) return;
            DataInputStream dis = new DataInputStream(is);

            // 10-byte spawn + exit header: >hhhhh
            int spawnX = dis.readShort();
            int spawnZ = dis.readShort();
            int spawnAng4096 = dis.readShort() & 4095;
            int exitX = dis.readShort();
            int exitZ = dis.readShort();

            this.playerBody.x = spawnX << 16;
            this.playerBody.z = spawnZ << 16;
            this.playerBody.rotation = MathUtils.fromAngle4096(spawnAng4096);

            // Monsters: >H count, then >BhhB (mtype, mx, mz, mang)
            int mCount = dis.readUnsignedShort();
            this.monCount = mCount < MAX_MONSTERS ? mCount : MAX_MONSTERS;
            for (int i = 0; i < mCount; i++) {
                int mType = dis.readUnsignedByte();
                int xUnits = dis.readShort();
                int zUnits = dis.readShort();
                int mang = dis.readUnsignedByte();
                if (i >= MAX_MONSTERS) continue;

                if (mType < 0 || mType > 10) mType = i % 11;
                monType[i] = mType;
                monX[i] = xUnits << 16;
                monZ[i] = zUnits << 16;
                monAngle[i] = MathUtils.fastAtan2(monX[i] - playerBody.x, monZ[i] - playerBody.z);
                monMaxHP[i] = MONSTER_MAX_HP[mType];
                monHP[i] = monMaxHP[i];
                monState[i] = (i == 0) ? MSTATE_CHASE : MSTATE_IDLE;
                monStateTick[i] = i * 3;
                monFrameA[i] = 0;
                monFrameB[i] = 1;
                monInterp[i] = 0;
                monPartMask[i] = Model3D.MASK_INTACT_DEFAULT;
                monPainTimer[i] = 0;
                monSector[i] = getSectorAtPoint(monX[i], monZ[i]);
                monDetourTicks[i] = 0;
            }

            // Items: >H count, then >Bhh (modelId 17..29, ix, iz)
            int iCount = dis.readUnsignedShort();
            this.itemCount = iCount < MAX_ITEMS ? iCount : MAX_ITEMS;
            for (int i = 0; i < iCount; i++) {
                int modelId = dis.readUnsignedByte();
                int xUnits = dis.readShort();
                int zUnits = dis.readShort();
                if (i >= MAX_ITEMS) continue;

                // Do not substitute a random model for a bad id: that used to turn one
                // pickup into a different item. An id outside 17..29 is not a pickup.
                if (modelId < ITEM_FIRST || modelId > ITEM_LAST) {
                    itemActive[i] = false;
                    continue;
                }
                itemActive[i] = true;
                itemType[i] = modelId - ITEM_FIRST;
                itemModelId[i] = modelId;
                itemX[i] = xUnits << 16;
                itemZ[i] = zUnits << 16;
                itemSector[i] = getSectorAtPoint(itemX[i], itemZ[i]);
            }

            // Props: >H count, then >BhhB (modelId 30..32, px, pz, pang)
            int pCount = dis.readUnsignedShort();
            this.propCount = pCount < MAX_PROPS ? pCount : MAX_PROPS;
            for (int i = 0; i < pCount; i++) {
                int modelId = dis.readUnsignedByte();
                int xUnits = dis.readShort();
                int zUnits = dis.readShort();
                int pang = dis.readUnsignedByte();
                int halfX = dis.readShort();
                int halfZ = dis.readShort();
                if (i >= MAX_PROPS) continue;

                if (modelId < 30 || modelId > 32) modelId = 30 + (i % 3);
                propActive[i] = true;
                propHalfX[i] = halfX << 16;
                propHalfZ[i] = halfZ << 16;
                propModelId[i] = modelId;
                propHP[i] = BARREL_HP;
                propX[i] = xUnits << 16;
                propZ[i] = zUnits << 16;
                propAngle[i] = MathUtils.fromAngle4096((pang & 7) << 9);
                propSector[i] = getSectorAtPoint(propX[i], propZ[i]);
            }

            // A monster can spawn inside a prop box (e.g. the same cell as a barrel); push it out.
            for (int m = 0; m < monCount; m++) {
                pushMonsterOutOfProps(m);
            }

            dis.close();
        } catch (Exception e) {
            // Ignore malformed sidecar
        }
    }

    private boolean tryMonsterStep(int m, int dx, int dz) {
        int nx = monX[m] + dx;
        int nz = monZ[m] + dz;
        if (insidePropBox(nx, nz) || !hasLineOfSight(monX[m], monZ[m], nx, nz)) return false;
        moveMonsterTo(m, nx, nz);
        return true;
    }

    private void moveMonsterTo(int m, int x, int z) {
        monX[m] = x;
        monZ[m] = z;
        monSector[m] = getSectorAtPoint(x, z);
    }

    /**
     * Pushes monster m out of any prop box along the axis of least penetration, the same
     * rule used for the player. Two passes handle a monster that lands between two boxes.
     */
    private void pushMonsterOutOfProps(int m) {
        boolean moved = false;
        for (int pass = 0; pass < 2; pass++) {
            boolean hit = false;
            for (int p = 0; p < propCount; p++) {
                if (!propActive[p]) continue;
                int hx = propHalfX[p] + CollisionSystem.COLLISION_RADIUS;
                int hz = propHalfZ[p] + CollisionSystem.COLLISION_RADIUS;
                int ox = monX[m] - propX[p];
                int oz = monZ[m] - propZ[p];
                if (ox > -hx && ox < hx && oz > -hz && oz < hz) {
                    int penX = hx - (ox < 0 ? -ox : ox);
                    int penZ = hz - (oz < 0 ? -oz : oz);
                    if (penX < penZ) {
                        monX[m] = propX[p] + (ox >= 0 ? hx : -hx);
                    } else {
                        monZ[m] = propZ[p] + (oz >= 0 ? hz : -hz);
                    }
                    hit = true;
                }
            }
            if (!hit) break;
            moved = true;
        }
        if (moved) monSector[m] = getSectorAtPoint(monX[m], monZ[m]);
    }

    /** True when (x, z) is inside any static prop box, grown by the monster collision radius. */
    private boolean insidePropBox(int x, int z) {
        for (int p = 0; p < propCount; p++) {
            if (!propActive[p]) continue;
            int hx = propHalfX[p] + CollisionSystem.COLLISION_RADIUS;
            int hz = propHalfZ[p] + CollisionSystem.COLLISION_RADIUS;
            int ox = x - propX[p];
            int oz = z - propZ[p];
            if (ox > -hx && ox < hx && oz > -hz && oz < hz) return true;
        }
        return false;
    }

    public void setMessage(String msg, int ticks) {
        this.hudMessage = msg;
        this.hudMessageTimer = ticks;
    }

    // ==================== Barrel helpers (restored from 055acab, door-free) ====================
    public boolean isBarrel(int p) {
        return p >= 0 && p < propCount && propActive[p] && propModelId[p] == BARREL_MODEL;
    }
    public void damageBarrel(int p, int dmg) {
        if (!isBarrel(p)) return;
        propHP[p] -= dmg;
        if (propHP[p] <= 0) detonateBarrel(p);
    }
    private void detonateBarrel(int first) {
        int head = 0;
        int tail = 0;
        propActive[first] = false;
        blastQueue[tail++] = first;
        while (head < tail) {
            int b = blastQueue[head++];
            int cx = propX[b];
            int cz = propZ[b];
            SoundManager.playSfx(SoundManager.SFX_ROCKET);
            spawnBlastFx(cx, cz);
            for (int k = 0; k < 4; k++) {
                spawnProjectile(false, PROJ_GIB, cx, 8 << 16, cz, nextRand() & 0xFFFF, 0);
            }
            for (int q = 0; q < propCount; q++) {
                if (!isBarrel(q)) continue;
                int d = blastDistance(cx, cz, propX[q], propZ[q]);
                if (d >= BARREL_BLAST_RADIUS) continue;
                propHP[q] -= blastDamage(d);
                if (propHP[q] <= 0) {
                    propActive[q] = false;
                    blastQueue[tail++] = q;
                }
            }
            for (int m = 0; m < monCount; m++) {
                if (monState[m] == MSTATE_INACTIVE || monState[m] == MSTATE_DEAD) continue;
                int d = blastDistance(cx, cz, monX[m], monZ[m]);
                if (d < BARREL_BLAST_RADIUS) {
                    int dmg = blastDamage(d);
                    if (dmg > 0) damageMonster(m, dmg);
                }
            }
            int pd = blastDistance(cx, cz, playerBody.x, playerBody.z);
            if (pd < BARREL_BLAST_RADIUS) {
                blastFlashTimer = BLAST_FLASH_TICKS;
                int dmg = blastDamage(pd);
                if (dmg > 0) damagePlayer(dmg);
            }
        }
    }
    private void spawnBlastFx(int cx, int cz) {
        int slot = 0;
        for (int i = 0; i < MAX_BLAST_FX; i++) {
            if (blastFxLeft[i] == 0) { slot = i; break; }
            if (blastFxLeft[i] < blastFxLeft[slot]) slot = i;
        }
        SectorData sd = getSectorDataAtPoint(cx, cz);
        int floorFP = (sd != null ? sd.floorHeight : 0) << 16;
        blastFxX[slot] = cx;
        blastFxY[slot] = floorFP + (20 << 16);
        blastFxZ[slot] = cz;
        blastFxLeft[slot] = BLAST_FX_LIFE;
    }
    private void triggerGrenadeBlast(int cx, int cz) {
        SoundManager.playSfx(SoundManager.SFX_ROCKET);
        spawnBlastFx(cx, cz);
        for (int k = 0; k < 4; k++) spawnProjectile(false, PROJ_GIB, cx, 8 << 16, cz, nextRand() & 0xFFFF, 0);
        int head = 0, tail = 0;
        // Damage barrels in radius; those that die enter the barrel chain
        for (int q = 0; q < propCount; q++) {
            if (!isBarrel(q)) continue;
            int d = blastDistance(cx, cz, propX[q], propZ[q]);
            if (d >= BARREL_BLAST_RADIUS) continue;
            propHP[q] -= blastDamage(d);
            if (propHP[q] <= 0) { propActive[q] = false; blastQueue[tail++] = q; }
        }
        while (head < tail) {
            int b = blastQueue[head++];
            int bx = propX[b], bz = propZ[b];
            SoundManager.playSfx(SoundManager.SFX_ROCKET);
            spawnBlastFx(bx, bz);
            for (int k = 0; k < 4; k++) spawnProjectile(false, PROJ_GIB, bx, 8 << 16, bz, nextRand() & 0xFFFF, 0);
            for (int q = 0; q < propCount; q++) {
                if (!isBarrel(q)) continue;
                int d = blastDistance(bx, bz, propX[q], propZ[q]);
                if (d >= BARREL_BLAST_RADIUS) continue;
                propHP[q] -= blastDamage(d);
                if (propHP[q] <= 0) { propActive[q] = false; blastQueue[tail++] = q; }
            }
            for (int m = 0; m < monCount; m++) {
                if (monState[m] == MSTATE_INACTIVE || monState[m] == MSTATE_DEAD) continue;
                int d = blastDistance(bx, bz, monX[m], monZ[m]);
                if (d < BARREL_BLAST_RADIUS) { int dmg = blastDamage(d); if (dmg > 0) damageMonster(m, dmg); }
            }
            int pd = blastDistance(bx, bz, playerBody.x, playerBody.z);
            if (pd < BARREL_BLAST_RADIUS) { blastFlashTimer = BLAST_FLASH_TICKS; int dmg = blastDamage(pd); if (dmg > 0) damagePlayer(dmg); }
        }
        for (int m = 0; m < monCount; m++) {
            if (monState[m] == MSTATE_INACTIVE || monState[m] == MSTATE_DEAD) continue;
            int d = blastDistance(cx, cz, monX[m], monZ[m]);
            if (d < BARREL_BLAST_RADIUS) { int dmg = blastDamage(d); if (dmg > 0) damageMonster(m, dmg); }
        }
        int pd = blastDistance(cx, cz, playerBody.x, playerBody.z);
        if (pd < BARREL_BLAST_RADIUS) { blastFlashTimer = BLAST_FLASH_TICKS; int dmg = blastDamage(pd); if (dmg > 0) damagePlayer(dmg); }
    }
    private void updateBlastFx() {
        if (blastFlashTimer > 0) blastFlashTimer--;
        for (int i = 0; i < MAX_BLAST_FX; i++) {
            if (blastFxLeft[i] > 0) blastFxLeft[i]--;
        }
    }
    private void drawBlastFx(int[] buf) {
        if (blastFlashTimer > 0) {
            int add = blastFlashTimer * 22;
            int addG = add * 3 / 4;
            int addB = add / 3;
            for (int i = 0; i < PortalRenderer.SCREEN_BUFFER_SIZE; i++) {
                int c = buf[i];
                int r = ((c >> 16) & 0xFF) + add;
                int g = ((c >> 8) & 0xFF) + addG;
                int b = (c & 0xFF) + addB;
                buf[i] = (r > 255 ? 255 : r) << 16 | (g > 255 ? 255 : g) << 8 | (b > 255 ? 255 : b);
            }
        }
        for (int i = 0; i < MAX_BLAST_FX; i++) {
            int left = blastFxLeft[i];
            if (left <= 0) continue;
            if (!PortalRenderer.projectLast(blastFxX[i], blastFxY[i], blastFxZ[i], blastScreen)) continue;
            int age = BLAST_FX_LIFE - left;
            int radius = age < 6 ? 3 + age * 4 : 27 - (age - 6) * 6;
            if (radius < 2) radius = 2;
            drawFireball(buf, blastScreen[0], blastScreen[1], radius, 255 * left / BLAST_FX_LIFE);
        }
    }
    private static void drawFireball(int[] buf, int cx, int cy, int radius, int alpha) {
        final int w = PortalRenderer.VIEWPORT_WIDTH;
        final int h = PortalRenderer.VIEWPORT_HEIGHT;
        int r2 = radius * radius;
        int y0 = Math.max(0, cy - radius);
        int y1 = Math.min(h - 1, cy + radius);
        int x0 = Math.max(0, cx - radius);
        int x1 = Math.min(w - 1, cx + radius);
        for (int y = y0; y <= y1; y++) {
            int dy = y - cy;
            int row = y * w;
            for (int x = x0; x <= x1; x++) {
                int dx = x - cx;
                int d2 = dx * dx + dy * dy;
                if (d2 > r2) continue;
                int t = d2 * 256 / r2;
                int col;
                if (t < 80) col = 0xFFF2B0;
                else if (t < 170) col = 0xFF8A20;
                else col = 0xB02A10;
                int a = alpha * (256 - t / 2) >> 8;
                int old = buf[row + x];
                int or_ = (old >> 16) & 0xFF, og = (old >> 8) & 0xFF, ob = old & 0xFF;
                int nr = or_ + ((((col >> 16) & 0xFF) - or_) * a >> 8);
                int ng = og + ((((col >> 8) & 0xFF) - og) * a >> 8);
                int nb = ob + (((col & 0xFF) - ob) * a >> 8);
                buf[row + x] = (nr << 16) | (ng << 8) | nb;
            }
        }
    }
    private static int blastDamage(int d) {
        return (BARREL_BLAST_DAMAGE * (BARREL_BLAST_RADIUS - d) + (BARREL_BLAST_RADIUS >> 1)) / BARREL_BLAST_RADIUS;
    }
    private static int blastDistance(int ax, int az, int bx, int bz) {
        int dx = (ax - bx) >> 16;
        int dz = (az - bz) >> 16;
        return isqrt(dx * dx + dz * dz);
    }
    private static int isqrt(int v) {
        int res = 0;
        int bit = 1 << 30;
        while (bit > v) bit >>= 2;
        while (bit != 0) {
            if (v >= res + bit) {
                v -= res + bit;
                res = (res >> 1) + bit;
            } else {
                res >>= 1;
            }
            bit >>= 2;
        }
        return res;
    }

    // ==================== Rendering Entry Point ====================

    public void render3DView(int[] targetScreenBuffer) {
        SectorData curSecData = getSectorDataAtPoint(playerBody.x, playerBody.z);
        int floorH = curSecData != null ? curSecData.floorHeight : 0;
        int bobY = MathUtils.fastSin(walkBobPhase) >> 1;
        int cameraHeightFP = ((floorH + PLAYER_HEIGHT_OFFSET) << 16) + bobY + jumpOffset;
        playerBody.y = cameraHeightFP;

        // 1. Render BSP-Portal world + back-to-front sector 3D entities via PolyRenderer3D
        PortalRenderer.renderWorld(playerBody.x, -cameraHeightFP, playerBody.z, playerBody.rotation);

        // 2. Render first-person 3D weapon model (.3O + .ANI) without Z-buffer
        Model3D weaponModel = polyRenderer.getModel(WEAPON_MODEL_IDS[curWeapon]);
        if (weaponModel != null) {
            int fA = 0, fB = 0, interp = 0;
            if (weaponAnimTick > 0 && weaponModel.fCount > 1) {
                int totalFrames = weaponModel.fCount - 1;
                int maxTick = WEAPON_COOLDOWN[curWeapon];
                int progress = ((maxTick - weaponAnimTick) << 8) / (maxTick > 0 ? maxTick : 1);
                int scaled = progress * totalFrames;
                fA = 1 + (scaled >> 8);
                fB = fA + 1;
                if (fA >= weaponModel.fCount) fA = weaponModel.fCount - 1;
                if (fB >= weaponModel.fCount) fB = 0;
                interp = scaled & 0xFF;
            }
            int wBobX = (MathUtils.fastCos(walkBobPhase) * 6) >> 16;
            int wBobY = Math.abs((MathUtils.fastSin(walkBobPhase) * 5) >> 16);
            polyRenderer.renderViewWeapon(
                weaponModel, fA, fB, interp,
                wBobX, wBobY,
                muzzleFlashTimer > 0,
                PortalRenderer.screenBuffer
            );
        }

        // 3. Barrel blast: screen flash and fireballs, then the damage tint if the player was hit
        drawBlastFx(PortalRenderer.screenBuffer);
        // 3b. Apply damage flash tint if player was hit
        if (damageFlashTimer > 0) {
            int[] buf = PortalRenderer.screenBuffer;
            int len = PortalRenderer.SCREEN_BUFFER_SIZE;
            for (int i = 0; i < len; i++) {
                int c = buf[i];
                int r = ((c >> 16) & 0xFF) + 90;
                if (r > 255) r = 255;
                buf[i] = (c & 0xFF00FFFF) | (r << 16);
            }
        }

        if (targetScreenBuffer != null && targetScreenBuffer != PortalRenderer.screenBuffer) {
            System.arraycopy(PortalRenderer.screenBuffer, 0, targetScreenBuffer, 0, PortalRenderer.SCREEN_BUFFER_SIZE);
        }
    }

    // ==================== Player Movement, Combat & Dismemberment ====================

    public void movePlayer(int forwardFP, int strafeFP, int turnDelta) {
        if (gameOver) return;

        playerBody.rotation = MathUtils.wrapAngle(playerBody.rotation + turnDelta);

        if (forwardFP == 0 && strafeFP == 0) return;

        int sin = MathUtils.fastSin(playerBody.rotation);
        int cos = MathUtils.fastCos(playerBody.rotation);

        // In PortalRenderer: forward is (+sin, +cos), right is (+cos, -sin)
        int dx = (int) (((long) sin * forwardFP + (long) cos * strafeFP) >> 16);
        int dz = (int) (((long) cos * forwardFP - (long) sin * strafeFP) >> 16);

        int proposedX = playerBody.x + dx;
        int proposedZ = playerBody.z + dz;

        SectorData curSector = getSectorDataAtPoint(playerBody.x, playerBody.z);
        collisionTestPoint.x = proposedX;
        collisionTestPoint.y = proposedZ;

        if (wallDefinitions != null) {
            for (int i = 0; i < wallDefinitions.length; i++) {
                WallDefinition wall = wallDefinitions[i];
                if (wall.isCollidable() || CollisionSystem.isWallPassableForSector(curSector, wall)) {
                    CollisionSystem.resolveWallCollision(collisionTestPoint, wall, vertices);
                }
            }
        }

        // Static prop boxes: the player is pushed out along the axis of least penetration.
        // Axis-aligned boxes with the player's collision radius added keep this to a few
        // compares per prop, with no allocation on the frame path.
        for (int p = 0; p < propCount; p++) {
            if (!propActive[p]) continue;
            int hx = propHalfX[p] + CollisionSystem.COLLISION_RADIUS;
            int hz = propHalfZ[p] + CollisionSystem.COLLISION_RADIUS;
            int ox = collisionTestPoint.x - propX[p];
            int oz = collisionTestPoint.y - propZ[p];
            if (ox > -hx && ox < hx && oz > -hz && oz < hz) {
                int penX = hx - (ox < 0 ? -ox : ox);
                int penZ = hz - (oz < 0 ? -oz : oz);
                if (penX < penZ) {
                    collisionTestPoint.x = propX[p] + (ox >= 0 ? hx : -hx);
                } else {
                    collisionTestPoint.y = propZ[p] + (oz >= 0 ? hz : -hz);
                }
            }
        }

        // Monster cylinder collision so player cannot walk inside live monsters
        int minDist = 20 << 16;
        for (int m = 0; m < monCount; m++) {
            if (monState[m] == MSTATE_INACTIVE || monState[m] == MSTATE_DEAD) continue;
            int mx = collisionTestPoint.x - monX[m];
            int mz = collisionTestPoint.y - monZ[m];
            if (Math.abs(mx) < minDist && Math.abs(mz) < minDist) {
                if (Math.abs(mx) > Math.abs(mz)) {
                    collisionTestPoint.x = monX[m] + (mx >= 0 ? minDist : -minDist);
                } else {
                    collisionTestPoint.y = monZ[m] + (mz >= 0 ? minDist : -minDist);
                }
            }
        }

        playerBody.x = collisionTestPoint.x;
        playerBody.z = collisionTestPoint.y;
        walkBobPhase = MathUtils.wrapAngle(walkBobPhase + 26000);

        checkItemPickups();
    }

    private void checkItemPickups() {
        int pickupRadius = 26 << 16;
        for (int i = 0; i < itemCount; i++) {
            if (!itemActive[i]) continue;
            if (Math.abs(playerBody.x - itemX[i]) < pickupRadius
                    && Math.abs(playerBody.z - itemZ[i]) < pickupRadius) {
                // The effect, the name and the 3D model all come from the same model id,
                // so a model can never grant something else than what it looks like.
                if (applyItemPickup(itemModelId[i])) {
                    itemActive[i] = false;
                    SoundManager.playSfx(SoundManager.SFX_PICKUP);
                }
            }
        }
    }

    /**
     * Applies one pickup model (models.bin 17..29) to the player and shows its name.
     * Returns true when the item was consumed; a full-health medkit, a full ammo pack
     * or a weapon already at its ammo cap stays on the floor.
     *
     * Values follow the original CHASM.INF [3D_OBJECTS] a_codes / [WEAPONS] table:
     *  - life +20 up to 100, biglife +100 up to 200,
     *  - armor3 +200, helmet1 +100 (both capped at 200),
     *  - weapon pickups grant the weapon and its "Start" ammo,
     *  - BAG.3O is the ammo backpack: it tops up every owned weapon.
     */
    public boolean applyItemPickup(int modelId) {
        switch (modelId) {
            case ITEM_MEDKIT:
                if (health >= NOMINAL_HEALTH) return false;
                health = Math.min(NOMINAL_HEALTH, health + 20);
                break;
            case ITEM_MEGA_HEALTH:
                if (health >= MAX_HEALTH) return false;
                health = Math.min(MAX_HEALTH, health + 100);
                break;
            case ITEM_BODY_ARMOR:
                if (armor >= MAX_ARMOR) return false;
                armor = Math.min(MAX_ARMOR, armor + 200);
                break;
            case ITEM_HELMET:
                if (armor >= MAX_ARMOR) return false;
                armor = Math.min(MAX_ARMOR, armor + 100);
                break;
            case ITEM_AMMO_PACK:
                if (!pickupAmmoPack()) return false;
                break;
            case ITEM_W_SHOTGUN:
                // CHASM.INF ACode 2 -> weapon slot 2 (Super Shotgun), Start 10.
                if (!pickupWeapon(1, 10)) return false;
                break;
            case ITEM_W_VOLCANO:
                // ACode 3 -> Volcano, Start 50.
                if (!pickupWeapon(2, 50)) return false;
                break;
            case ITEM_W_BLADEGUN:
                // ACode 4 -> BladeGun, Start 20.
                if (!pickupWeapon(3, 20)) return false;
                break;
            case ITEM_W_CROSSBOW:
                // ACode 5 -> Laser Crossbow, Start 20.
                if (!pickupWeapon(4, 20)) return false;
                break;
            case ITEM_W_GRENADER:
                // ACode 6 -> Grenader, Start 5.
                if (!pickupWeapon(5, 5)) return false;
                break;
            case ITEM_KEY_RED:
                if (hasRedKey) return false;
                hasRedKey = true;
                break;
            case ITEM_KEY_GREEN:
                if (hasGreenKey) return false;
                hasGreenKey = true;
                break;
            case ITEM_KEY_BLUE:
                if (hasBlueKey) return false;
                hasBlueKey = true;
                break;
            default:
                // 30.. are props/effects, not pickups; leave the entity alone.
                return false;
        }
        showItemMessage(modelId);
        return true;
    }

    /** Grants weapon slot {@code slot} with {@code ammoBonus} rounds and selects it. */
    private boolean pickupWeapon(int slot, int ammoBonus) {
        int limit = WEAPON_AMMO_LIMIT[slot];
        if (weaponOwned[slot] && ammo[slot] >= limit) return false;
        weaponOwned[slot] = true;
        ammo[slot] = Math.min(limit, ammo[slot] + ammoBonus);
        // Chasm switches to a weapon as soon as it is picked up.
        curWeapon = slot;
        return true;
    }

    /**
     * BAG.3O (the backpack model) refills every weapon the player owns, using the
     * per-shot ammo values from CHASM.INF ([WEAPONS] DAm).
     */
    private boolean pickupAmmoPack() {
        boolean gained = false;
        for (int w = 1; w < 6; w++) {
            if (!weaponOwned[w]) continue;
            int limit = WEAPON_AMMO_LIMIT[w];
            if (ammo[w] >= limit) continue;
            ammo[w] = Math.min(limit, ammo[w] + WEAPON_AMMO_PACK[w]);
            gained = true;
        }
        return gained;
    }

    private void showItemMessage(int modelId) {
        String name = (modelId >= 0 && modelId < ITEM_NAMES.length) ? ITEM_NAMES[modelId] : null;
        if (name == null) name = "ITEM";
        String prefix = TextTable.get("HUD_GOT_PREFIX", "GOT").trim();
        setMessage(prefix.length() > 0 ? prefix + " " + name : name, 30);
    }

    public void switchWeapon(int delta) {
        for (int k = 1; k <= 6; k++) {
            int next = (curWeapon + delta * k + 60) % 6;
            if (weaponOwned[next]) {
                curWeapon = next;
                setMessage(WEAPON_NAMES[curWeapon], 20);
                break;
            }
        }
    }

    public boolean fireWeapon() {
        if (gameOver || weaponCooldown > 0) return false;
        int cost = WEAPON_AMMO_COST[curWeapon];
        if (cost > 0 && ammo[curWeapon] < cost) {
            // Out of ammo: fall back to the rifle, which never runs dry (slot 0, cost 0).
            if (!weaponOwned[0]) return false;
            curWeapon = 0;
            cost = 0;
            setMessage(TextTable.get("HUD_OUT_OF_AMMO", "OUT OF AMMO"), 25);
        }
        if (cost > 0) ammo[curWeapon] -= cost;
        weaponCooldown = WEAPON_COOLDOWN[curWeapon];
        weaponAnimTick = weaponCooldown;
        muzzleFlashTimer = 2;

        // Sound belongs to the weapon in the slot, in the original arsenal order:
        // 1 Rifle, 2 Super Shotgun, 3 Volcano, 4 BladeGun, 5 Laser Crossbow, 6 Grenader.
        int sfx = (curWeapon == 1) ? SoundManager.SFX_SHOTGUN
                : (curWeapon == 2) ? SoundManager.SFX_CHAINGUN
                : (curWeapon == 3) ? SoundManager.SFX_SAW
                : (curWeapon == 4) ? SoundManager.SFX_CROSSBOW
                : (curWeapon == 5) ? SoundManager.SFX_ROCKET
                : SoundManager.SFX_RIFLE;
        SoundManager.playSfx(sfx);

        if (curWeapon >= 3) {
            // Projectile weapons: BladeGun throws a saw disc, the Laser Crossbow a bolt,
            // the Grenader a grenade (bolt model, explodes on impact). Damage is applied
            // only when the projectile reaches a target, so there is no double hit.
            spawnProjectile(
                true,
                (curWeapon == 3) ? PROJ_DISK : PROJ_BOLT,
                playerBody.x,
                playerBody.y - (8 << 16),
                playerBody.z,
                playerBody.rotation,
                WEAPON_DAMAGE[curWeapon]
            );
            return true;
        }

        // Hitscan weapons (Rifle, Super Shotgun, Volcano)
        performHitscanAttack(WEAPON_DAMAGE[curWeapon]);
        return true;
    }

    private void performHitscanAttack(int damage) {
        int sin = MathUtils.fastSin(playerBody.rotation);
        int cos = MathUtils.fastCos(playerBody.rotation);

        int bestMon = -1;
        int bestDepth = Integer.MAX_VALUE;

        for (int m = 0; m < monCount; m++) {
            if (monState[m] == MSTATE_INACTIVE || monState[m] == MSTATE_DEAD) continue;
            int rx = monX[m] - playerBody.x;
            int rz = monZ[m] - playerBody.z;

            int viewX = (int) (((long) cos * rx - (long) sin * rz) >> 16);
            int viewDepth = (int) (((long) sin * rx + (long) cos * rz) >> 16);
            if (viewDepth <= (8 << 16) || viewDepth > (900 << 16)) continue;

            // Hit cylinder width scales forgivingly for keypad aiming
            int hitHalfWidth = (22 << 16) + (viewDepth >> 3);
            if (Math.abs(viewX) <= hitHalfWidth && viewDepth < bestDepth) {
                if (hasLineOfSight(playerBody.x, playerBody.z, monX[m], monZ[m])) {
                    bestDepth = viewDepth;
                    bestMon = m;
                }
            }
        }

        if (bestMon >= 0) {
            damageMonster(bestMon, damage);
        } else {
            // No monster hit: check for explosive barrels
            int bestBarrel = -1;
            int bestBarrelDepth = Integer.MAX_VALUE;
            for (int p = 0; p < propCount; p++) {
                if (!isBarrel(p)) continue;
                int rx = propX[p] - playerBody.x;
                int rz = propZ[p] - playerBody.z;
                int viewX = (int) (((long) cos * rx - (long) sin * rz) >> 16);
                int viewDepth = (int) (((long) sin * rx + (long) cos * rz) >> 16);
                if (viewDepth <= (8 << 16) || viewDepth > (900 << 16)) continue;
                int hitHalfWidth = (22 << 16) + (viewDepth >> 3);
                if (Math.abs(viewX) <= hitHalfWidth && viewDepth < bestBarrelDepth) {
                    if (hasLineOfSight(playerBody.x, playerBody.z, propX[p], propZ[p])) {
                        bestBarrelDepth = viewDepth;
                        bestBarrel = p;
                    }
                }
            }
            if (bestBarrel >= 0) {
                damageBarrel(bestBarrel, damage);
            }
        }
    }

    /**
     * Applies damage to monster m and triggers Chasm's signature .CAR multi-stage
     * dismemberment:
     *   - Stage 1: Right Arm + Weapon severed (MASK_RIGHT_HAND -> MASK_RIGHT_HAND_SEPARATED + submodels[0])
     *   - Stage 2: Left Arm severed (MASK_LEFT_HAND -> MASK_LEFT_HAND_SEPARATED + submodels[1])
     *   - Stage 3: Head severed / Decapitation (MASK_HEAD -> MASK_HEAD_SEPARATED + submodels[2])
     */
    public void damageMonster(int m, int damage) {
        if (m < 0 || m >= monCount) return;
        if (monState[m] == MSTATE_INACTIVE || monState[m] == MSTATE_DEAD) return;

        monHP[m] -= damage;
        monPainTimer[m] = 4;

        int maxHP = monMaxHP[m];
        int hp = monHP[m];
        int mask = monPartMask[m];

        // Check Stage 1 Dismemberment: Sever Right Arm + Weapon at <= 72% HP
        if (hp <= (maxHP * 72) / 100 && (mask & Model3D.MASK_RIGHT_HAND) != 0) {
            dismemberMonsterPart(m, Model3D.SUBMODEL_RIGHT_HAND);
        }

        // Check Stage 2 Dismemberment: Sever Left Arm at <= 38% HP
        if (hp <= (maxHP * 38) / 100 && (mask & Model3D.MASK_LEFT_HAND) != 0) {
            dismemberMonsterPart(m, Model3D.SUBMODEL_LEFT_HAND);
        }

        if (hp <= 0) {
            monHP[m] = 0;
            // Sever Head on overkill or BladeGun/Super Shotgun/Grenader finisher
            if ((monPartMask[m] & Model3D.MASK_HEAD) != 0
                    && (damage >= 35 || curWeapon == 1 || curWeapon == 4 || curWeapon == 5)) {
                dismemberMonsterPart(m, Model3D.SUBMODEL_HEAD);
            }
            monState[m] = MSTATE_DEAD;
            monStateTick[m] = 0;
            monFrameA[m] = 8;
            monFrameB[m] = 9;
            monInterp[m] = 0;
            monstersKilled++;
            SoundManager.playSfx(SoundManager.SFX_MON_DIE);

            // Check if all monsters in level are defeated
            checkAllMonstersCleared();
        } else {
            monState[m] = MSTATE_PAIN;
            monStateTick[m] = 0;
            monFrameA[m] = 7;
            monFrameB[m] = 7;
            monInterp[m] = 0;
            SoundManager.playSfx(SoundManager.SFX_MON_PAIN);
        }
    }

    /**
     * Severs a specific body part from monster m:
     *   submodelIdx == 0: Right Arm + Weapon
     *   submodelIdx == 1: Left Arm
     *   submodelIdx == 2: Head
     * Updates the monster's polygon bitmask to hide the limb and expose the
     * bloody stump polygon group, and spawns the detached 3D submodel mesh!
     */
    public void dismemberMonsterPart(int m, int submodelIdx) {
        int mask = monPartMask[m];
        String partLabel;
        if (submodelIdx == Model3D.SUBMODEL_RIGHT_HAND) {
            if ((mask & Model3D.MASK_RIGHT_HAND) == 0) return;
            mask = (mask & ~Model3D.MASK_RIGHT_HAND) | Model3D.MASK_RIGHT_HAND_SEPARATED;
            monFrameA[m] = 7;
            monFrameB[m] = 7;
            partLabel = "RIGHT ARM SEVERED!";
        } else if (submodelIdx == Model3D.SUBMODEL_LEFT_HAND) {
            if ((mask & Model3D.MASK_LEFT_HAND) == 0) return;
            mask = (mask & ~Model3D.MASK_LEFT_HAND) | Model3D.MASK_LEFT_HAND_SEPARATED;
            monFrameA[m] = 7;
            monFrameB[m] = 7;
            partLabel = "LEFT ARM SEVERED!";
        } else {
            if ((mask & Model3D.MASK_HEAD) == 0) return;
            mask = (mask & ~Model3D.MASK_HEAD) | Model3D.MASK_HEAD_SEPARATED;
            partLabel = "HEAD SEVERED!";
        }

        monPartMask[m] = mask;
        monInterp[m] = 0;
        limbsSevered++;
        setMessage(MONSTER_NAMES[monType[m]] + ": " + partLabel, 32);
        SoundManager.playSfx(SoundManager.SFX_GIB);

        // Spawn physical detached 3D limb submodel slightly toward player and to the limb's side
        SectorData secData = getSectorDataAtPoint(monX[m], monZ[m]);
        int floorY = (secData != null ? secData.floorHeight : 0) << 16;
        int spawnH = floorY + ((submodelIdx == Model3D.SUBMODEL_HEAD ? 34 : 22) << 16);

        int toCamAngle = MathUtils.fastAtan2(playerBody.x - monX[m], playerBody.z - monZ[m]);
        int sideOffset = (submodelIdx == 0) ? -55000 : (submodelIdx == 1) ? 55000 : 0;
        int ejectAngle = MathUtils.wrapAngle(toCamAngle + sideOffset);

        int startX = monX[m] + MathUtils.fastSin(ejectAngle) * 10;
        int startZ = monZ[m] + MathUtils.fastCos(ejectAngle) * 10;
        int vx = MathUtils.fastSin(ejectAngle) * 2;
        int vz = MathUtils.fastCos(ejectAngle) * 2;
        int vy = 3 << 16;

        // Only real detached geometry becomes a flying limb (some .CAR monsters have no separable head/arm).
        Model3D parentModel = polyRenderer.getModel(monType[m]);
        boolean hasLimbGeometry = parentModel != null && parentModel.submodels != null
                && submodelIdx < parentModel.submodels.length
                && parentModel.submodels[submodelIdx] != null
                && parentModel.submodels[submodelIdx].vCount > 0
                && parentModel.submodels[submodelIdx].fCount > 0;
        if (hasLimbGeometry) {
            spawnDetachedLimb(monType[m], submodelIdx, startX, spawnH, startZ, vx, vy, vz, monAngle[m], floorY);
        }
    }

    private void spawnDetachedLimb(
            int monsterType, int subIdx,
            int x, int y, int z,
            int vx, int vy, int vz,
            int angle, int floorY) {
        int slot = -1;
        for (int i = 0; i < MAX_LIMBS; i++) {
            if (!limbActive[i]) { slot = i; break; }
        }
        if (slot < 0) slot = limbsSevered % MAX_LIMBS;

        limbActive[slot] = true;
        limbMonsterType[slot] = monsterType;
        limbSubmodelIdx[slot] = subIdx;
        limbX[slot] = x;
        limbY[slot] = y;
        limbZ[slot] = z;
        limbVX[slot] = vx;
        limbVY[slot] = vy;
        limbVZ[slot] = vz;
        limbAngle[slot] = angle;
        limbFloorY[slot] = floorY;
        limbTimer[slot] = 400; // Stays on floor for a long time
        limbSector[slot] = getSectorAtPoint(x, z);
    }

    private void spawnProjectile(
            boolean fromPlayer, int modelId,
            int x, int y, int z,
            int angle, int damage) {
        int slot = -1;
        for (int i = 0; i < MAX_PROJ; i++) {
            if (!projActive[i]) { slot = i; break; }
        }
        if (slot < 0) slot = levelTick % MAX_PROJ;

        boolean gib = (modelId == PROJ_GIB);
        int speed = (modelId == PROJ_DISK) ? 14 : (gib ? 5 : 18);
        projActive[slot] = true;
        projFromPlayer[slot] = fromPlayer;
        projModelId[slot] = modelId;
        projX[slot] = x;
        projY[slot] = y;
        projZ[slot] = z;
        projVX[slot] = MathUtils.fastSin(angle) * speed;
        projVY[slot] = gib ? (3 << 16) : 0;
        projVZ[slot] = MathUtils.fastCos(angle) * speed;
        projAngle[slot] = angle;
        projSpin[slot] = 0;
        projDamage[slot] = damage;
        projLife[slot] = gib ? 18 : (modelId == PROJ_DISK ? 40 : 35);
        projSector[slot] = getSectorAtPoint(x, z);
    }

    public boolean hasLineOfSight(int x1, int z1, int x2, int z2) {
        if (wallDefinitions == null) return true;
        for (int i = 0; i < wallDefinitions.length; i++) {
            WallDefinition wall = wallDefinitions[i];
            if (!CollisionSystem.isWallSolid(wall)) continue;
            Point2D vStart = vertices[wall.startVertexId & 0xFFFF];
            Point2D vEnd = vertices[wall.endVertexId & 0xFFFF];
            if (CollisionSystem.doLineSegmentsIntersect(x1, z1, x2, z2, vStart.x, vStart.y, vEnd.x, vEnd.y)) {
                return false;
            }
        }
        return true;
    }

    private void checkAllMonstersCleared() {
        for (int i = 0; i < monCount; i++) {
            if (monState[i] != MSTATE_INACTIVE && monState[i] != MSTATE_DEAD) {
                return;
            }
        }
        setMessage(TextTable.get("HUD_SECTOR_CLEAR", "SECTOR CLEAR! PRESS 0 FOR NEXT LEVEL"), 90);
    }

    public boolean isSectorClear() {
        for (int i = 0; i < monCount; i++) {
            if (monState[i] != MSTATE_INACTIVE && monState[i] != MSTATE_DEAD) return false;
        }
        return monCount > 0;
    }

    /** '*' key: use. Clears the level once every enemy is down; doors are not in the portal data yet. */
    public void interactOrNextLevel() {
        int alive = 0;
        for (int i = 0; i < monCount; i++) {
            if (monState[i] != MSTATE_INACTIVE && monState[i] != MSTATE_DEAD) alive++;
        }
        if (alive == 0) {
            levelCompleted = true;
            return;
        }
        setMessage(TextTable.get("HUD_NOTHING", "NOTHING TO USE HERE - CLEAR THE SECTOR"), 40);
    }

    /** '0' key: jump. */
    public void triggerJump() {
        if (jumpOffset == 0 && jumpVel == 0) {
            jumpVel = 3 << 16;
        }
    }

    private void updateJump() {
        if (jumpOffset == 0 && jumpVel == 0) return;
        jumpOffset += jumpVel;
        jumpVel -= 1 << 14; // gravity 0.25 units/tick^2
        if (jumpOffset <= 0) {
            jumpOffset = 0;
            jumpVel = 0;
        }
    }

    // ==================== Per-Tick Game Logic Update ====================

    public void updateTick() {
        levelTick++;
        updateJump();
        advanceTextureAnimations();

        if (weaponCooldown > 0) weaponCooldown--;
        if (weaponAnimTick > 0) weaponAnimTick--;
        if (muzzleFlashTimer > 0) muzzleFlashTimer--;
        if (damageFlashTimer > 0) damageFlashTimer--;
        if (hudMessageTimer > 0) {
            // Loop SECTOR CLEAR message until player presses 0: keep timer refreshed
            if (hudMessage != null && hudMessage.indexOf("SECTOR CLEAR") >= 0) {
                if (hudMessageTimer < 10) hudMessageTimer = 90;
                else hudMessageTimer--;
            } else {
                hudMessageTimer--;
            }
        }

        updateDetachedLimbs();
        updateProjectiles();
        updateBlastFx();
        updateMonsters();
    }

    private void updateDetachedLimbs() {
        for (int i = 0; i < MAX_LIMBS; i++) {
            if (!limbActive[i]) continue;
            if (limbY[i] > limbFloorY[i]) {
                limbX[i] += limbVX[i];
                limbZ[i] += limbVZ[i];
                limbY[i] += limbVY[i];
                limbVY[i] -= (1 << 16) + 16384; // Gravity
                limbAngle[i] = MathUtils.wrapAngle(limbAngle[i] + 18000);
                if (limbY[i] <= limbFloorY[i]) {
                    limbY[i] = limbFloorY[i];
                    limbVX[i] = 0;
                    limbVZ[i] = 0;
                    limbVY[i] = 0;
                }
                limbSector[i] = getSectorAtPoint(limbX[i], limbZ[i]);
            }
            if (--limbTimer[i] <= 0) {
                limbActive[i] = false;
            }
        }
    }

    private void updateProjectiles() {
        int hitRadius = 22 << 16;
        for (int i = 0; i < MAX_PROJ; i++) {
            if (!projActive[i]) continue;
            if (--projLife[i] <= 0) {
                projActive[i] = false;
                continue;
            }

            int nx = projX[i] + projVX[i];
            int nz = projZ[i] + projVZ[i];

            if (projModelId[i] == PROJ_GIB) {
                // Gib particle with gravity
                projY[i] += projVY[i];
                projVY[i] -= (1 << 16);
                projAngle[i] = MathUtils.wrapAngle(projAngle[i] + 24000);
                SectorData sd = getSectorDataAtPoint(nx, nz);
                int fl = (sd != null ? sd.floorHeight : 0) << 16;
                if (projY[i] < fl) projY[i] = fl;
                projX[i] = nx;
                projZ[i] = nz;
                projSector[i] = getSectorAtPoint(nx, nz);
                continue;
            }

            boolean isGrenade = projFromPlayer[i] && projDamage[i] == GRENADE_DAMAGE;
            if (!hasLineOfSight(projX[i], projZ[i], nx, nz)) {
                if (isGrenade) {
                    int cx = projX[i], cz = projZ[i];
                    projActive[i] = false;
                    triggerGrenadeBlast(cx, cz);
                } else {
                    projActive[i] = false;
                }
                continue;
            }

            projX[i] = nx;
            projZ[i] = nz;
            if (projModelId[i] == PROJ_DISK) {
                projSpin[i] = MathUtils.wrapAngle(projSpin[i] + 32000);
            }
            projSector[i] = getSectorAtPoint(nx, nz);

            if (projFromPlayer[i]) {
                if (isGrenade) {
                    boolean hit = false;
                    for (int m = 0; m < monCount; m++) {
                        if (monState[m] == MSTATE_INACTIVE || monState[m] == MSTATE_DEAD) continue;
                        if (Math.abs(nx - monX[m]) < hitRadius && Math.abs(nz - monZ[m]) < hitRadius) { hit = true; break; }
                    }
                    if (!hit) for (int p = 0; p < propCount; p++) if (isBarrel(p) && Math.abs(nx - propX[p]) < hitRadius && Math.abs(nz - propZ[p]) < hitRadius) { hit = true; break; }
                    if (hit) {
                        int cx = nx, cz = nz;
                        projActive[i] = false;
                        triggerGrenadeBlast(cx, cz);
                        continue;
                    }
                } else {
                    for (int m = 0; m < monCount; m++) {
                        if (monState[m] == MSTATE_INACTIVE || monState[m] == MSTATE_DEAD) continue;
                        if (Math.abs(nx - monX[m]) < hitRadius && Math.abs(nz - monZ[m]) < hitRadius) {
                            damageMonster(m, projDamage[i]);
                            projActive[i] = false;
                            break;
                        }
                    }
                    if (projActive[i]) {
                        for (int p = 0; p < propCount; p++) {
                            if (!isBarrel(p)) continue;
                            if (Math.abs(nx - propX[p]) < hitRadius && Math.abs(nz - propZ[p]) < hitRadius) {
                                damageBarrel(p, projDamage[i]);
                                projActive[i] = false;
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private void updateMonsters() {
        for (int m = 0; m < monCount; m++) {
            int st = monState[m];
            if (st == MSTATE_INACTIVE) continue;
            pushMonsterOutOfProps(m);

            if (monPainTimer[m] > 0) monPainTimer[m]--;
            monStateTick[m]++;

            if (st == MSTATE_DEAD) {
                // Advance death frames 8 -> 9 and hold at 9 (corpse on floor)
                if (monStateTick[m] < 6) {
                    monFrameA[m] = 8;
                    monFrameB[m] = 9;
                    monInterp[m] = (monStateTick[m] * 256) / 6;
                } else {
                    monFrameA[m] = 9;
                    monFrameB[m] = 9;
                    monInterp[m] = 0;
                }
                continue;
            }

            int dx = playerBody.x - monX[m];
            int dz = playerBody.z - monZ[m];
            int distApprox = Math.abs(dx) + Math.abs(dz);

            int angleToPlayer = MathUtils.fastAtan2(-dx, -dz);

            if (st == MSTATE_PAIN) {
                monFrameA[m] = 7;
                monFrameB[m] = 7;
                monInterp[m] = 0;
                if (monStateTick[m] >= 5) {
                    monState[m] = MSTATE_CHASE;
                    monStateTick[m] = 0;
                }
                continue;
            }

            if (st == MSTATE_IDLE) {
                monFrameA[m] = (monStateTick[m] >> 3) & 1;
                monFrameB[m] = (monFrameA[m] + 1) & 1;
                monInterp[m] = (monStateTick[m] & 7) << 5;

                int visDist = (enemyBehavior == ENEMY_AGGRESSIVE) ? (900 << 16) : (380 << 16);
                if (distApprox < visDist && hasLineOfSight(monX[m], monZ[m], playerBody.x, playerBody.z)) {
                    monState[m] = MSTATE_CHASE;
                    monStateTick[m] = 0;
                    monAngle[m] = angleToPlayer;
                }
            } else if (st == MSTATE_CHASE) {
                monAngle[m] = angleToPlayer;
                // Walk frames 2..4 (3 keyframes)
                int walkStep = (monStateTick[m] >> 1) % 3;
                monFrameA[m] = 2 + walkStep;
                monFrameB[m] = 2 + ((walkStep + 1) % 3);
                monInterp[m] = (monStateTick[m] & 1) << 7;

                // Can only attack if monster still has at least one arm!
                boolean hasArm = (monPartMask[m] & (Model3D.MASK_RIGHT_HAND | Model3D.MASK_LEFT_HAND)) != 0;

                int chaseDist = (enemyBehavior == ENEMY_AGGRESSIVE) ? (320 << 16) : (150 << 16);
                if (hasArm && distApprox < chaseDist && (monStateTick[m] > 10)
                        && hasLineOfSight(monX[m], monZ[m], playerBody.x, playerBody.z)) {
                    monState[m] = MSTATE_ATTACK;
                    monStateTick[m] = 0;
                } else if (distApprox > (44 << 16) && monType[m] != 3) {
                    // Turret (type 3) is stationary: only turns, never walks.
                    int stepX = (dx >= 0 ? 1 : -1) * Math.min(Math.abs(dx), 3 << 16);
                    int stepZ = (dz >= 0 ? 1 : -1) * Math.min(Math.abs(dz), 3 << 16);
                    int nx = monX[m] + stepX;
                    int nz = monZ[m] + stepZ;
                    if (monDetourTicks[m] > 0) {
                        monDetourTicks[m]--;
                        if (!tryMonsterStep(m, -stepZ * monDetourSide[m], stepX * monDetourSide[m])) {
                            monDetourTicks[m] = 0;
                        }
                    } else if (!tryMonsterStep(m, stepX, stepZ)) {
                        if (tryMonsterStep(m, -stepZ, stepX)) {
                            monDetourSide[m] = 1;
                            monDetourTicks[m] = DETOUR_TICKS;
                        } else if (tryMonsterStep(m, stepZ, -stepX)) {
                            monDetourSide[m] = -1;
                            monDetourTicks[m] = DETOUR_TICKS;
                        }
                    }
                } else if (monType[m] == 3) {
                    // Turret idle sway while targeting (no walk).
                    monFrameA[m] = (monStateTick[m] >> 4) & 1;
                    monFrameB[m] = (monFrameA[m] + 1) & 1;
                    monInterp[m] = (monStateTick[m] & 15) << 4;
                }
            } else if (st == MSTATE_ATTACK) {
                monAngle[m] = angleToPlayer;
                // Attack frames 5..6
                int atkFrame = 5 + Math.min(1, monStateTick[m] >> 2);
                monFrameA[m] = atkFrame;
                monFrameB[m] = atkFrame;
                monInterp[m] = 0;

                // Show muzzle flash polygon group (128) on frame 7 if right arm is still attached
                if (monStateTick[m] == 3) {
                    if ((monPartMask[m] & Model3D.MASK_RIGHT_HAND) != 0) {
                        monPartMask[m] |= Model3D.MASK_WEAPON_FIRE;
                    }
                    int dmgDist = (enemyBehavior == ENEMY_AGGRESSIVE) ? (500 << 16) : (240 << 16);
                    if (distApprox < dmgDist && hasLineOfSight(monX[m], monZ[m], playerBody.x, playerBody.z)) {
                        damagePlayer(6 + (monType[m] >> 1));
                    }
                } else {
                    monPartMask[m] &= ~Model3D.MASK_WEAPON_FIRE;
                }

                if (monStateTick[m] >= 7) {
                    monPartMask[m] &= ~Model3D.MASK_WEAPON_FIRE;
                    monState[m] = MSTATE_CHASE;
                    monStateTick[m] = 0;
                }
            }
        }
    }

    public void damagePlayer(int dmg) {
        if (gameOver) return;
        if (devMode) return; // god mode
        if (armor > 0) {
            int absorb = dmg >> 1;
            if (absorb > armor) absorb = armor;
            armor -= absorb;
            dmg -= absorb;
        }
        health -= dmg;
        damageFlashTimer = 3;
        SoundManager.playSfx(SoundManager.SFX_PLAYER_PAIN);
        if (health <= 0) {
            health = 0;
            gameOver = true;
            setMessage(TextTable.get("HUD_YOUDIED", "YOU DIED! PRESS FIRE TO RESTART"), 999);
        }
    }
}
