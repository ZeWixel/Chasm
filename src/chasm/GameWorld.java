package chasm;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.util.Vector;

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
    // Levels 1-3: MAP.01, MAP.05, MAP.06 of the CSM.BIN used by this build (MAP.99 is the briefing
    // background, not a mission). Level 4: the first build's "Pyramid of Shmuher" (grid map 3 in
    // tools/legacy, with the steps and walls added by hand there). It is not game data.
    public static final int NUM_LEVELS = 4;
    /** Full level names (level index 0..3). The menu adds the number: "4 PYRAMID OF SHMUHER". */
    public static final String[] LEVEL_NAMES = {
        "THE POWER STATION",
        "AROUND PYRAMIDS",
        "PYRAMID OF SHMUHER",
        "SHMUHER (FIRST BUILD)"
    };
    /** Short names for places where the full name does not fit a 240-pixel menu line. */
    public static final String[] LEVEL_SHORT_NAMES = {
        "POWER STATION",
        "PYRAMIDS",
        "SHMUHER",
        "SHMUHER 1ST BUILD"
    };

    /** Level label with its number, e.g. "4 SHMUHER (FIRST BUILD)" (number = level index + 1). */
    public static String levelLabel(int levelIndex, boolean shortName) {
        String name = shortName ? LEVEL_SHORT_NAMES[levelIndex] : LEVEL_NAMES[levelIndex];
        return (levelIndex + 1) + " " + name;
    }

    /** Music track (SoundManager: 1..4 = mus_lvl1..4) for a 0-based level index. */
    public static int musicTrackFor(int levelIndex) {
        return (levelIndex % 4) + 1;
    }

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
        },
        {
            "MISSION 4: SHMUHER (1ST BUILD)",
            "First-build layout of the pyramid",
            "with hand-made steps and walls",
            "(not game data). Reach the exit."
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

    public static final String[] MONSTER_NAMES = {
        "GRUNT", "SKELETON", "GOBLIN", "SCORPION", "ZOMBIE", "RAT",
        "PHANTOM", "ORC", "MUMMY", "OGRE", "DEMON"
    };

    public static final int[] MONSTER_MAX_HP = {
        70, 85, 65, 95, 90, 45, 80, 100, 120, 145, 180
    };

    public static final String[] WEAPON_NAMES = {
        "1:RIFT RIFLE",
        "2:DOUBLE SHOTGUN",
        "3:CHAINGUN",
        "4:CROSSBOW",
        "5:SAW LAUNCHER",
        "6:MEGAZAPPER"
    };

    public static final int[] WEAPON_MODEL_IDS = { 11, 12, 13, 14, 15, 16 };
    public static final int[] WEAPON_COOLDOWN  = { 5, 11, 3, 9, 8, 14 };
    public static final int[] WEAPON_DAMAGE    = { 24, 52, 18, 42, 48, 85 };
    public static final int[] WEAPON_AMMO_COST = { 1, 2, 1, 1, 1, 2 };

    // Monster AI states
    // ==================== Player & Renderer State ====================
    public final PhysicsBody playerBody = new PhysicsBody(0, 0, 0, 0);
    public final PolyRenderer3D polyRenderer;

    public int currentLevel = 0;
    public int levelTick = 0;

    // Jump state (16.16 world units): '0' key launches, gravity brings the eye back down.
    public int jumpOffset = 0;
    public int jumpVel = 0;
    public int health = 100;
    public int armor = 25;
    public int curWeapon = 0;
    public final boolean[] weaponOwned = new boolean[6];
    public final int[] ammo = new int[6];

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
    /** Prop model id of the explosive barrel. */
    public static final int BARREL_MODEL = 30;
    /** Barrel hit points: two Rift Rifle hits, three Volcano Chaingun hits, or one Double Shotgun blast. */
    public static final int BARREL_HP = 40;
    /** Blast damage at the barrel centre; it falls off linearly to 0 at BARREL_BLAST_RADIUS. */
    public static final int BARREL_BLAST_DAMAGE = 90;
    /** Blast radius in world units (two 96-unit cells). */
    public static final int BARREL_BLAST_RADIUS = 192;
    /** Remaining hit points per prop (used by barrels only). */
    private final int[] propHP = new int[MAX_PROPS];
    /** Barrels queued for detonation during one chain reaction. */
    private final int[] blastQueue = new int[MAX_PROPS];

    /** Fireballs drawn at detonation points. Each lives BLAST_FX_LIFE ticks. */
    public static final int MAX_BLAST_FX = 3;
    public static final int BLAST_FX_LIFE = 10;
    /** Ticks of whole-screen flash when the player is inside a blast. */
    public static final int BLAST_FLASH_TICKS = 4;
    /** World position (16.16) of each fireball: floor + barrel centre height. */
    private final int[] blastFxX = new int[MAX_BLAST_FX];
    private final int[] blastFxY = new int[MAX_BLAST_FX];
    private final int[] blastFxZ = new int[MAX_BLAST_FX];
    /** Ticks left for each fireball; 0 = slot free. */
    private final int[] blastFxLeft = new int[MAX_BLAST_FX];
    /** Whole-screen flash ticks left (player inside a blast). */
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
        health = 100;
        armor = 25;
        curWeapon = 0;
        for (int i = 0; i < 6; i++) {
            weaponOwned[i] = (i <= 1); // Start with Rift Rifle + Double Shotgun
            ammo[i] = 0;
        }
        ammo[0] = 60;
        ammo[1] = 24;
        ammo[2] = 50;
        ammo[3] = 12;
        ammo[4] = 12;
        ammo[5] = 10;
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

    // ==================== Doors, Gates, Switches & Keys (PROCESS.XX) ====================
    // Nothing needs a use key: a proc is activated by touch. Activation follows the original engine
    // (Chasm-Reverse map.cpp, ProcedureProcess*): it runs only when the proc is unlocked, or when the
    // key it needs is held. 'Lock' is removed by an 'unlock' action from another proc.
    public static final int MAX_PROCS = 40;
    public static final int TRIG_LINK = 0;       // proximity (3x3 cells)
    public static final int TRIG_FLOOR = 1;      // stepped on (the cell itself)
    public static final int TRIG_RETURN = 2;     // resets the proc to its start state
    public static final int TRIG_ONOFF = 3;      // proximity; a second touch closes it
    public static final int TRIG_DESTROY = 4;    // a shot destroys the gate lock on its cell (no proximity)
    /** Gate lock prop (models.bin id 38): shot to open. */
    public static final int LOCK_MODEL = 38;
    public static final int LOCK_HP = 60;
    /**
     * Squared radius (world units, 24 units = 0.25 cell) within which the player touches a floor
     * trigger's cell. The original uses the player radius (60/256 cell) against the cell square, so
     * a floor cell in a closed door is touched when the player bumps the door from outside.
     */
    private static final int FLOOR_TOUCH_R2 = 24 * 24;
    private static final int PROC_LOCKED = 1;
    private static final int PROC_CHECKBACK = 2;
    private static final int PROC_TOGGLE = 4;
    private static final int DSTATE_IDLE = 0;
    private static final int DSTATE_DELAY = 1;
    private static final int DSTATE_OPENING = 2;
    private static final int DSTATE_OPEN = 3;
    private static final int DSTATE_CLOSING = 4;
    private static final int STAGE_ONE = 256;
    /** Original engine ran about this many game ticks per second (ChasmCanvas ~28 FPS). */
    private static final int TICKS_PER_SECOND = 28;
    /** Cell size in world units; matches the compiler's SCALE. */
    private static final int CELL_UNITS = 96;
    /** Closed door/gate ceiling sits this far above the floor (0: level with it, so it blocks the player). */
    private static final int DOOR_CLOSED_GAP = 0;

    public int procCount = 0;
    private final int[] procId = new int[MAX_PROCS];
    /** True for a proc that moves a 3O platform (its stage drives the platform, see updateMovers). */
    private final boolean[] procMoves = new boolean[MAX_PROCS];
    private final int[] procKey = new int[MAX_PROCS];       // 0 none, 1 green, 2 red, 3 blue
    private final int[] procFlags = new int[MAX_PROCS];
    private final int[] procDelay = new int[MAX_PROCS];     // ticks before opening
    private final int[] procBackWait = new int[MAX_PROCS];  // ticks open before returning; 0 = never
    private final int[] procSpeed = new int[MAX_PROCS];
    private final int[] procOnMsg = new int[MAX_PROCS];
    private final int[] procLockMsg = new int[MAX_PROCS];
    private final int[] procState = new int[MAX_PROCS];
    private final int[] procTimer = new int[MAX_PROCS];
    private final int[] procStage = new int[MAX_PROCS];     // 0 closed .. STAGE_ONE open
    private final int[][] procTrig = new int[MAX_PROCS][];     // kind, x, y triples
    private final boolean[][] procTrigIn = new boolean[MAX_PROCS][];
    private final int[][] procUnlock = new int[MAX_PROCS][];
    private final int[][] procCell = new int[MAX_PROCS][];     // x, y, sectorId, openCeil quads
    private final String[] procMsg = new String[256];

    // Moving platforms: 3O models whose prop box follows the stage of their proc (PROCESS 'move').
    // A mover occupies a prop slot after the static props; propMovProc is -1 for static props.
    private final int[] propMovProc = new int[MAX_PROPS];
    private final int[] propBaseX = new int[MAX_PROPS];     // world units, stage 0
    private final int[] propBaseZ = new int[MAX_PROPS];
    private final int[] propMoveDX = new int[MAX_PROPS];    // world units, total travel at stage 256
    private final int[] propMoveDZ = new int[MAX_PROPS];
    private int movCount = 0;
    private final int[] movPid = new int[MAX_PROPS];
    private final int[] movModel = new int[MAX_PROPS];
    private final int[] movBX = new int[MAX_PROPS];
    private final int[] movBZ = new int[MAX_PROPS];
    private final int[] movDX = new int[MAX_PROPS];
    private final int[] movDZ = new int[MAX_PROPS];
    private final int[] movHX = new int[MAX_PROPS];
    private final int[] movHZ = new int[MAX_PROPS];
    private final int[] movAng = new int[MAX_PROPS];
    public boolean hasGreenKey = false;
    public boolean hasRedKey = false;
    public boolean hasBlueKey = false;
    /** Level exit point in world units (16.16 is applied when used). */
    private int exitX = 0;
    private int exitZ = 0;

    private void loadProcsSidecar(String path) {
        procCount = 0;
        movCount = 0;
        for (int i = 0; i < procMsg.length; i++) procMsg[i] = null;
        for (int i = 0; i < MAX_PROCS; i++) procMoves[i] = false;
        panelCount = 0;
        try {
            InputStream is = GameWorld.class.getResourceAsStream(path);
            if (is == null) return;
            DataInputStream dis = new DataInputStream(is);
            int n = dis.readUnsignedShort();
            for (int i = 0; i < n; i++) {
                int pid = dis.readUnsignedByte();
                int key = dis.readUnsignedByte();
                int flags = dis.readUnsignedByte();
                int delay = dis.readUnsignedShort();
                int back = dis.readUnsignedShort();
                int speed = dis.readUnsignedByte();
                int onMsg = dis.readUnsignedByte();
                int lockMsg = dis.readUnsignedByte();
                int tc = dis.readUnsignedByte();
                int[] trig = new int[tc * 3];
                for (int t = 0; t < trig.length; t++) trig[t] = dis.readUnsignedByte();
                int uc = dis.readUnsignedByte();
                int[] unl = new int[uc];
                for (int u = 0; u < uc; u++) unl[u] = dis.readUnsignedByte();
                int cc = dis.readUnsignedByte();
                int[] cells = new int[cc * 4];
                for (int c = 0; c < cc; c++) {
                    cells[c * 4] = dis.readUnsignedByte();
                    cells[c * 4 + 1] = dis.readUnsignedByte();
                    cells[c * 4 + 2] = dis.readUnsignedShort();
                    cells[c * 4 + 3] = dis.readUnsignedShort();
                }
                if (procCount >= MAX_PROCS) continue;
                int k = procCount++;
                procId[k] = pid;
                procKey[k] = key;
                procFlags[k] = flags;
                procDelay[k] = delay * TICKS_PER_SECOND / 10;
                procBackWait[k] = back * TICKS_PER_SECOND / 10;
                procSpeed[k] = speed;
                procOnMsg[k] = onMsg;
                procLockMsg[k] = lockMsg;
                procState[k] = DSTATE_IDLE;
                procTimer[k] = 0;
                procStage[k] = 0;
                procTrig[k] = trig;
                procTrigIn[k] = new boolean[tc];
                procUnlock[k] = unl;
                procCell[k] = cells;
            }
            int mc = dis.readUnsignedByte();
            for (int m = 0; m < mc; m++) {
                int id = dis.readUnsignedByte();
                int len = dis.readUnsignedByte();
                byte[] text = new byte[len];
                dis.readFully(text);
                if (id > 0) procMsg[id] = new String(text, "US-ASCII");
            }
            int vc = dis.readUnsignedByte();
            for (int v = 0; v < vc; v++) {
                int pid = dis.readUnsignedByte();
                int model = dis.readUnsignedByte();
                int bx = dis.readShort();
                int bz = dis.readShort();
                int ddx = dis.readShort();
                int ddz = dis.readShort();
                int hx = dis.readShort();
                int hz = dis.readShort();
                int ang = dis.readUnsignedByte();
                if (movCount >= MAX_PROPS) continue;
                int k = movCount++;
                movPid[k] = pid;
                movModel[k] = model;
                movBX[k] = bx;
                movBZ[k] = bz;
                movDX[k] = ddx;
                movDZ[k] = ddz;
                movHX[k] = hx;
                movHZ[k] = hz;
                movAng[k] = ang;
            }
            // Panels (door panels and rotating walls). Older sidecars end after the movers.
            int pc = 0;
            try {
                pc = dis.readUnsignedShort();
            } catch (EOFException e) {
                pc = 0;
            }
            for (int q = 0; q < pc; q++) {
                int kind = dis.readUnsignedByte();
                int pid = dis.readUnsignedByte();
                int slot = dis.readUnsignedByte();
                int[] raw = new int[7];
                for (int j = 0; j < 7; j++) raw[j] = dis.readShort();
                if (panelCount >= MAX_PANELS) continue;
                int k = panelCount++;
                panelKind[k] = kind;
                panelPid[k] = pid;
                panelSlot[k] = slot;
                for (int j = 0; j < 7; j++) panelRaw[k * 7 + j] = raw[j];
            }
            dis.close();
        } catch (Exception e) {
            procCount = 0;
        }
    }

    /** Cell index of a 16.16 world coordinate (cells are indexed from 32 at the origin). */
    private static int cellOf(int fp) {
        int u = fp >> 16;
        int q = u / CELL_UNITS;
        if (u < 0 && q * CELL_UNITS != u) q--;
        return q + 32;
    }

    private static boolean cellNear(int x, int y, int cx, int cz) {
        return Math.abs(x - cx) <= 1 && Math.abs(y - cz) <= 1;
    }

    /** True if the player's circle (16.16 world position) touches the square of cell (x, y). */
    private static boolean touchesCell(int fx, int fz, int x, int y) {
        int px = fx >> 16;
        int pz = fz >> 16;
        int x0 = (x - 32) * CELL_UNITS;
        int z0 = (y - 32) * CELL_UNITS;
        int dx = px < x0 ? x0 - px : (px > x0 + CELL_UNITS ? px - x0 - CELL_UNITS : 0);
        int dz = pz < z0 ? z0 - pz : (pz > z0 + CELL_UNITS ? pz - z0 - CELL_UNITS : 0);
        return dx * dx + dz * dz <= FLOOR_TOUCH_R2;
    }

    private int procIndex(int pid) {
        for (int p = 0; p < procCount; p++) {
            if (procId[p] == pid) return p;
        }
        return -1;
    }

    /**
     * Door cells for the automap, as triples (cell x, cell y, key id). Key id 0 = no key (a door
     * that only an 'unlock' opens); 1 green, 2 red, 3 blue. Moving platforms are not doors.
     */
    public int[] automapDoorCells() {
        int n = 0;
        for (int p = 0; p < procCount; p++) {
            if (procMoves[p]) continue;
            n += procCell[p].length / 4;
        }
        int[] out = new int[n * 3];
        int j = 0;
        for (int p = 0; p < procCount; p++) {
            if (procMoves[p]) continue;
            int[] cells = procCell[p];
            for (int c = 0; c < cells.length; c += 4) {
                out[j++] = cells[c];
                out[j++] = cells[c + 1];
                out[j++] = procKey[p];
            }
        }
        return out;
    }

    private boolean hasKey(int k) {
        if (k == 1) return hasGreenKey;
        if (k == 2) return hasRedKey;
        if (k == 3) return hasBlueKey;
        return false;
    }

    /** The engine's test: a keyed proc needs its key; an unkeyed proc needs to be unlocked. */
    private boolean canOpen(int p) {
        if (procKey[p] == 0) return (procFlags[p] & PROC_LOCKED) == 0;
        return hasKey(procKey[p]);
    }

    private boolean playerInProcCells(int p, int cx, int cz) {
        int[] cells = procCell[p];
        for (int c = 0; c < cells.length; c += 4) {
            if (cells[c] == cx && cells[c + 1] == cz) return true;
        }
        return false;
    }

    /**
     * Handles one touch. Returns false when the proc refused (locked, no key), so the caller
     * keeps checking while the player stays in range (a key picked up nearby then opens it).
     */
    private boolean triggerProc(int p, int kind) {
        int st = procState[p];
        boolean active = st == DSTATE_DELAY || st == DSTATE_OPENING || st == DSTATE_OPEN;
        if (kind == TRIG_RETURN) {
            if (active) procState[p] = DSTATE_CLOSING;
            return true;
        }
        if (kind == TRIG_ONOFF && active && (procFlags[p] & PROC_TOGGLE) != 0) {
            procState[p] = DSTATE_CLOSING;
            return true;
        }
        if (!canOpen(p)) {
            int lm = procLockMsg[p];
            if (lm != 0 && procMsg[lm] != null) {
                setMessage(procMsg[lm], 40);
            } else if (procKey[p] != 0) {
                setMessage(procKey[p] == 1 ? "LOCKED - NEED GREEN KEY"
                        : (procKey[p] == 2 ? "LOCKED - NEED RED KEY" : "LOCKED - NEED BLUE KEY"), 40);
            } else {
                setMessage("LOCKED", 40);   // unkeyed door still locked by an 'unlock' from another proc
            }
            return false;
        }
        openProc(p);
        return true;
    }

    /**
     * The activation half of a touch: on-message, the 'unlock' actions of this proc, and the start
     * of its movement. Shared by triggerProc (keys/locks already checked) and destroyProc.
     */
    private void openProc(int p) {
        int st = procState[p];
        boolean active = st == DSTATE_DELAY || st == DSTATE_OPENING || st == DSTATE_OPEN;
        int om = procOnMsg[p];
        if (om != 0 && procMsg[om] != null) setMessage(procMsg[om], 60);
        int[] unl = procUnlock[p];
        for (int u = 0; u < unl.length; u++) {
            int q = procIndex(unl[u]);
            if (q >= 0) procFlags[q] &= ~PROC_LOCKED;
        }
        if (active) {
            if (st == DSTATE_OPEN && procBackWait[p] > 0) procTimer[p] = procBackWait[p];
            return;
        }
        if (procCell[p].length > 0 || procMoves[p] || procHasRotor[p]) {
            procState[p] = procDelay[p] > 0 ? DSTATE_DELAY : DSTATE_OPENING;
            procTimer[p] = procDelay[p];
        }
    }

    /**
     * The original's 'destroy' link (ProcedureProcessDestroy): shooting the 3O on the link's cell
     * clears the proc's lock and opens it. Keys are not checked.
     */
    private void destroyProc(int p) {
        procFlags[p] &= ~PROC_LOCKED;
        openProc(p);
    }

    /**
     * Manual use (key 7): acts as a touch on the nearest door or switch within one cell. Locks
     * and keys are checked as usual, so a locked door still refuses with its message.
     */
    public void manualOpenDoor() {
        if (sectors == null) return;
        int cx = cellOf(playerBody.x);
        int cz = cellOf(playerBody.z);
        int best = -1;
        int bestD = 2;   // one cell: the door, its trigger cell, or a cell next to it
        for (int p = 0; p < procCount; p++) {
            if (procMoves[p]) continue;
            int d = 99;
            int[] cells = procCell[p];
            for (int c = 0; c < cells.length; c += 4) d = Math.min(d, cellDistance(cx, cz, cells[c], cells[c + 1]));
            int[] tr = procTrig[p];
            for (int t = 0; t < tr.length; t += 3) {
                if (tr[t] == TRIG_RETURN || tr[t] == TRIG_DESTROY) continue;
                d = Math.min(d, cellDistance(cx, cz, tr[t + 1], tr[t + 2]));
            }
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        if (best < 0) {
            setMessage("NO DOOR NEAR", 40);
            return;
        }
        triggerProc(best, TRIG_LINK);
    }

    private static int cellDistance(int ax, int az, int bx, int bz) {
        return Math.max(Math.abs(ax - bx), Math.abs(az - bz));
    }

    /** Sets the ceilings of a proc's cells for its current stage (closed = floor + gap). */
    private void applyProc(int p) {
        int[] cells = procCell[p];
        for (int c = 0; c < cells.length; c += 4) {
            int sid = cells[c + 2];
            if (sectors == null || sid < 0 || sid >= sectors.length) continue;
            SectorData sd = sectors[sid];
            int closed = sd.floorHeight + DOOR_CLOSED_GAP;
            int open = Math.max(closed, cells[c + 3]);
            sd.ceilingHeight = (short) (closed + (open - closed) * procStage[p] / STAGE_ONE);
        }
    }

    private void advanceProc(int p, int cx, int cz) {
        int st = procState[p];
        if (st == DSTATE_IDLE) return;
        boolean blocked = playerInProcCells(p, cx, cz);
        int step = Math.max(4, Math.min(64, procSpeed[p] * 3));
        if (procMoves[p]) {
            // The original: stage = seconds * speed / 10, so a full move takes 10 / speed seconds.
            step = Math.max(1, procSpeed[p] * STAGE_ONE / (10 * TICKS_PER_SECOND));
        }
        switch (st) {
            case DSTATE_DELAY:
                if (--procTimer[p] <= 0) procState[p] = DSTATE_OPENING;
                break;
            case DSTATE_OPENING:
                procStage[p] = Math.min(STAGE_ONE, procStage[p] + step);
                applyProc(p);
                if (procStage[p] >= STAGE_ONE) {
                    procState[p] = DSTATE_OPEN;
                    procTimer[p] = procBackWait[p];
                }
                break;
            case DSTATE_OPEN:
                if (procBackWait[p] > 0 && --procTimer[p] <= 0) {
                    // Never close on the player: wait until the cells are clear.
                    if (blocked) procTimer[p] = 1;
                    else procState[p] = DSTATE_CLOSING;
                }
                break;
            case DSTATE_CLOSING:
                if (blocked) {
                    procState[p] = DSTATE_OPEN;
                    procTimer[p] = 1;
                    break;
                }
                procStage[p] = Math.max(0, procStage[p] - step);
                applyProc(p);
                if (procStage[p] == 0) procState[p] = DSTATE_IDLE;
                break;
            default:
                break;
        }
    }

    /** Runs once per game tick: touch triggers, then the movement of every proc. */
    private void updateProcs() {
        if (sectors == null) return;
        int cx = cellOf(playerBody.x);
        int cz = cellOf(playerBody.z);
        for (int p = 0; p < procCount; p++) {
            int[] tr = procTrig[p];
            boolean[] in = procTrigIn[p];
            for (int t = 0; t < in.length; t++) {
                int kind = tr[t * 3];
                int x = tr[t * 3 + 1];
                int y = tr[t * 3 + 2];
                if (kind == TRIG_DESTROY) continue;   // fired by a shot (destroyLocksAt)
                boolean inside = kind == TRIG_FLOOR ? touchesCell(playerBody.x, playerBody.z, x, y)
                        : cellNear(x, y, cx, cz);
                if (!inside) {
                    in[t] = false;
                } else if (!in[t]) {
                    // Remember the touch only when the proc accepted it; a refused touch is retried
                    // on the next tick while the player stays in range.
                    in[t] = triggerProc(p, kind);
                }
            }
        }
        for (int p = 0; p < procCount; p++) advanceProc(p, cx, cz);
        updateMovers();
        updatePanels();
    }

    /**
     * Moves each mover's prop box to its proc's stage. Entities that the box reaches are shoved
     * to its leading face (the original's GetFloorLevel ignores dynamic models, so nothing rides
     * the platform; the box only blocks and pushes).
     */
    private void updateMovers() {
        for (int i = 0; i < propCount; i++) {
            int p = propMovProc[i];
            if (!propActive[i] || p < 0) continue;
            int stage = procStage[p];
            int nx = (propBaseX[i] + propMoveDX[i] * stage / STAGE_ONE) << 16;
            int nz = (propBaseZ[i] + propMoveDZ[i] * stage / STAGE_ONE) << 16;
            if (nx == propX[i] && nz == propZ[i]) continue;
            int frameDX = nx - propX[i];
            int frameDZ = nz - propZ[i];
            propX[i] = nx;
            propZ[i] = nz;
            propSector[i] = getSectorAtPoint(nx, nz);
            shoveFromMover(i, frameDX, frameDZ);
        }
    }

    /** Pushes the player and live monsters inside mover box i to its leading face. */
    private void shoveFromMover(int i, int frameDX, int frameDZ) {
        int hx = propHalfX[i] + CollisionSystem.COLLISION_RADIUS;
        int hz = propHalfZ[i] + CollisionSystem.COLLISION_RADIUS;
        int sx = frameDX > 0 ? 1 : (frameDX < 0 ? -1 : 0);
        int sz = frameDZ > 0 ? 1 : (frameDZ < 0 ? -1 : 0);
        int px = propX[i];
        int pz = propZ[i];

        int ox = playerBody.x - px;
        int oz = playerBody.z - pz;
        if (ox > -hx && ox < hx && oz > -hz && oz < hz) {
            int tx = sx != 0 ? px + sx * hx : playerBody.x;
            int tz = sx != 0 ? playerBody.z : pz + sz * hz;
            // Never shove the player into a wall: a blocked shove leaves the box pushout to act.
            if (getSectorAtPoint(tx, tz) != null) {
                playerBody.x = tx;
                playerBody.z = tz;
            }
        }
        for (int m = 0; m < monCount; m++) {
            if (monState[m] == MSTATE_INACTIVE || monState[m] == MSTATE_DEAD) continue;
            int mx = monX[m] - px;
            int mz = monZ[m] - pz;
            if (mx > -hx && mx < hx && mz > -hz && mz < hz) {
                if (sx != 0) monX[m] = px + sx * hx;
                else monZ[m] = pz + sz * hz;
                monSector[m] = getSectorAtPoint(monX[m], monZ[m]);
            }
        }
    }

    /** Adds the level's movers as prop slots after the static props (after both sidecars load). */
    private void attachMovers() {
        for (int k = 0; k < movCount; k++) {
            int i = propCount;
            if (i >= MAX_PROPS) break;
            propCount++;
            propHP[i] = BARREL_HP;
            propActive[i] = true;
            propModelId[i] = movModel[k];
            propHalfX[i] = movHX[k] << 16;
            propHalfZ[i] = movHZ[k] << 16;
            propAngle[i] = MathUtils.fromAngle4096((movAng[k] & 7) << 9);
            propBaseX[i] = movBX[k];
            propBaseZ[i] = movBZ[k];
            propMoveDX[i] = movDX[k];
            propMoveDZ[i] = movDZ[k];
            propMovProc[i] = procIndex(movPid[k]);
            if (propMovProc[i] >= 0) procMoves[propMovProc[i]] = true;
            propX[i] = movBX[k] << 16;
            propZ[i] = movBZ[k] << 16;
            propSector[i] = getSectorAtPoint(propX[i], propZ[i]);
        }
    }

    /** The level ends when every enemy is down and the player reaches the exit point. */
    // ==================== Panels: door panels and rotating walls ====================
    // The thin walls inside a door's cells and inside a rotating proc's cells are not part of the
    // baked geometry (only cell edges are). At load each becomes a solid textured wall in the sector
    // on each side of it (both faces), with its own vertices, so it blocks, shows its texture and
    // is cut by the portal clipping like any other wall. A door panel is solid only while its proc
    // is closed (stage 0); the portal walls between the door and its neighbours are hidden then, so
    // the panel is the door's face. A rotating panel turns about its pivot with the proc stage.
    private static final int MAX_PANELS = 160;
    private static final int PANEL_KIND_DOOR = 0;
    private static final int PANEL_KIND_ROTATE = 1;
    /** Sample distance past each side of a panel, to find the sector it stands in (0.75 cell). */
    private static final int PANEL_SIDE_UNITS = 72;
    /** World units per panel coordinate step: the sidecar stores (cell - 32) * 256. */
    private static final int PANEL_RAW_UNITS = CELL_UNITS << 16;

    private int panelCount = 0;
    private final int[] panelKind = new int[MAX_PANELS];
    private final int[] panelPid = new int[MAX_PANELS];
    private final int[] panelSlot = new int[MAX_PANELS];
    /** Per panel: x0, y0, x1, y1, pivot x, pivot y, angle in degrees (raw sidecar values). */
    private final int[] panelRaw = new int[MAX_PANELS * 7];
    private final int[] panelProc = new int[MAX_PANELS];     // index into procs, -1 none
    private final int[] panelV0 = new int[MAX_PANELS];       // vertex indices
    private final int[] panelV1 = new int[MAX_PANELS];
    private final int[] panelLastStage = new int[MAX_PANELS];
    private final boolean[] panelClosed = new boolean[MAX_PANELS];
    private final WallDefinition[] panelCollide = new WallDefinition[MAX_PANELS];
    /** Every WallDefinition of a panel: its collision definition and one per side it is seen from. */
    private final Vector[] panelDefs = new Vector[MAX_PANELS];
    /** Per proc: the solid walls on a door's sides (hidden while closed; they still block). */
    private final Vector[] procHideWalls = new Vector[MAX_PROCS];
    /** Per proc: the surfaces of its portal walls (their texture becomes the door panel's while closed). */
    private final Vector[] procFaces = new Vector[MAX_PROCS];
    /** Per proc: the original (upper, main, lower) texture ids of procFaces, three ints per surface. */
    private final Vector[] procFaceSaved = new Vector[MAX_PROCS];
    private final boolean[] procFaceShowsDoor = new boolean[MAX_PROCS];
    private final int[] procDoorSlot = new int[MAX_PROCS];
    /** True for a proc that turns a rotating panel: it opens on a touch like a door. */
    private final boolean[] procHasRotor = new boolean[MAX_PROCS];

    /** Raw sidecar value -> 16.16 world coordinate. */
    private static int panelToFixed(int raw) {
        return raw * (PANEL_RAW_UNITS >> 8);
    }

    private void attachPanels() {
        for (int p = 0; p < MAX_PROCS; p++) procHasRotor[p] = false;
        if (panelCount == 0 || vertices == null || wallDefinitions == null || sectors == null) return;
        int oldV = vertices.length;
        int oldW = wallDefinitions.length;
        Point2D[] nv = new Point2D[oldV + 2 * panelCount];
        System.arraycopy(vertices, 0, nv, 0, oldV);
        for (int i = 0; i < panelCount; i++) {
            panelV0[i] = oldV + 2 * i;
            panelV1[i] = oldV + 2 * i + 1;
            nv[panelV0[i]] = new Point2D(0, 0);
            nv[panelV1[i]] = new Point2D(0, 0);
        }
        setVertices(nv);

        WallDefinition[] nw = new WallDefinition[oldW + panelCount];
        System.arraycopy(wallDefinitions, 0, nw, 0, oldW);
        for (int i = 0; i < panelCount; i++) {
            int pid = panelPid[i];
            panelProc[i] = procIndex(pid);
            panelLastStage[i] = -1;
            panelClosed[i] = false;
            placePanel(i, 0);

            WallDefinition coll = new WallDefinition((short) panelV0[i], (short) panelV1[i],
                    (short) 0, (short) -1, (byte) 0, (byte) 0, (byte) 0);
            Vector defs = new Vector();
            defs.addElement(coll);
            panelCollide[i] = coll;
            panelDefs[i] = defs;
            nw[oldW + i] = coll;

            // Mid point and unit normal of the panel, in 16.16 world units.
            Point2D a = vertices[panelV0[i]];
            Point2D b = vertices[panelV1[i]];
            long mx = ((long) a.x + b.x) >> 1;
            long mz = ((long) a.y + b.y) >> 1;
            int dx = b.x - a.x;
            int dz = b.y - a.y;
            int len = MathUtils.preciseHypot(dx, dz);
            if (len == 0) continue;
            int nx = MathUtils.fixedPointDivide(-dz, len);
            int nz = MathUtils.fixedPointDivide(dx, len);
            Sector[] leaves = new Sector[2];
            int leafCount = 0;
            for (int side = -1; side <= 1; side += 2) {
                int sx = (int) (mx + side * (long) ((long) nx * PANEL_SIDE_UNITS));
                int sz = (int) (mz + side * (long) ((long) nz * PANEL_SIDE_UNITS));
                Sector leaf = getSectorAtPoint(sx, sz);
                if (leaf == null) continue;
                SectorData sd = leaf.getSectorData();
                if (sd == null || sd.ceilingHeight - sd.floorHeight <= 0) continue;
                boolean seen = false;
                for (int k = 0; k < leafCount; k++) seen |= leaves[k] == leaf;
                if (seen) continue;
                leaves[leafCount++] = leaf;

                WallSurface surf = new WallSurface((byte) panelSlot[i], (byte) panelSlot[i], (byte) panelSlot[i],
                        0, (short) 0, (short) 0);
                surf.linkedSector = sd;
                WallDefinition rd = new WallDefinition((short) panelV0[i], (short) panelV1[i],
                        (short) 0, (short) -1, (byte) 0, (byte) 0, (byte) 0);
                rd.frontSurface = surf;
                rd.backSurface = null;
                defs.addElement(rd);
                if (coll.frontSurface == null) coll.frontSurface = surf;
                WallSegment sa = new WallSegment((short) panelV0[i], (short) panelV1[i], (short) 0, true, (short) 0);
                WallSegment sb = new WallSegment((short) panelV1[i], (short) panelV0[i], (short) 0, true, (short) 0);
                sa.wallDefinition = rd;
                sb.wallDefinition = rd;
                WallSegment[] old = leaf.walls;
                WallSegment[] grown = new WallSegment[old.length + 2];
                System.arraycopy(old, 0, grown, 0, old.length);
                grown[old.length] = sa;
                grown[old.length + 1] = sb;
                leaf.walls = grown;
            }
        }
        wallDefinitions = nw;

        for (int i = 0; i < panelCount; i++) {
            if (panelKind[i] == PANEL_KIND_ROTATE && panelProc[i] >= 0) procHasRotor[panelProc[i]] = true;
        }

        // Door faces. While a door is closed its face is its panel texture: the portal walls on its
        // sides keep drawing (they close the view through the zero-height door), with the panel's
        // texture; the solid walls on its sides are hidden (they still block).
        for (int p = 0; p < procCount; p++) {
            procHideWalls[p] = null;
            procFaces[p] = null;
            procFaceSaved[p] = null;
            procFaceShowsDoor[p] = false;
            procDoorSlot[p] = -1;
            int[] cells = procCell[p];
            for (int i = 0; i < panelCount; i++) {
                if (panelKind[i] == PANEL_KIND_DOOR && panelProc[i] == p) {
                    procDoorSlot[p] = panelSlot[i];
                    break;
                }
            }
            if (procDoorSlot[p] < 0) continue;
            Vector hide = new Vector();
            Vector faces = new Vector();
            Vector saved = new Vector();
            for (int w = 0; w < oldW; w++) {
                WallDefinition wd = wallDefinitions[w];
                if (wd.frontSurface == null) continue;
                for (int c = 0; c < cells.length; c += 4) {
                    int sid = cells[c + 2];
                    if (sid < 0 || sid >= sectors.length) continue;
                    SectorData door = sectors[sid];
                    if (wd.frontSurface.linkedSector == door
                            || (wd.backSurface != null && wd.backSurface.linkedSector == door)) {
                        if (wd.backSurface == null) {
                            hide.addElement(wd);
                        } else {
                            addFace(faces, saved, wd.frontSurface);
                            addFace(faces, saved, wd.backSurface);
                        }
                        break;
                    }
                }
            }
            procHideWalls[p] = hide;
            procFaces[p] = faces;
            procFaceSaved[p] = saved;
        }
        updatePanels();
    }

    private static void addFace(Vector faces, Vector saved, WallSurface surf) {
        faces.addElement(surf);
        saved.addElement(new int[] {surf.upperTextureId, surf.mainTextureId, surf.lowerTextureId});
    }

    /**
     * Sets the vertices of panel i for a proc stage (0..STAGE_ONE) and the normal of its collision
     * definition. A door panel keeps its stage-0 position.
     */
    private void placePanel(int i, int stage) {
        int r = i * 7;
        int x0 = panelToFixed(panelRaw[r]);
        int z0 = panelToFixed(panelRaw[r + 1]);
        int x1 = panelToFixed(panelRaw[r + 2]);
        int z1 = panelToFixed(panelRaw[r + 3]);
        if (panelKind[i] == PANEL_KIND_ROTATE && stage != 0) {
            int cx = panelToFixed(panelRaw[r + 4]);
            int cz = panelToFixed(panelRaw[r + 5]);
            int angle = (int) ((long) panelRaw[r + 6] * stage * MathUtils.FULL_CIRCLE / (360L * STAGE_ONE));
            int sin = MathUtils.fastSin(angle);
            int cos = MathUtils.fastCos(angle);
            int ox0 = x0 - cx, oz0 = z0 - cz, ox1 = x1 - cx, oz1 = z1 - cz;
            x0 = cx + (int) (((long) cos * ox0 - (long) sin * oz0) >> 16);
            z0 = cz + (int) (((long) sin * ox0 + (long) cos * oz0) >> 16);
            x1 = cx + (int) (((long) cos * ox1 - (long) sin * oz1) >> 16);
            z1 = cz + (int) (((long) sin * ox1 + (long) cos * oz1) >> 16);
        }
        Point2D a = vertices[panelV0[i]];
        Point2D b = vertices[panelV1[i]];
        a.x = x0;
        a.y = z0;
        b.x = x1;
        b.y = z1;
        WallDefinition coll = panelCollide[i];
        if (coll == null) return;
        // Same normal as WallDefinition.initializeWall, for the collision code.
        int dy = a.y - b.y;
        int dx = b.x - a.x;
        int length = MathUtils.preciseHypot(dx, dy);
        if (length == 0) return;
        coll.normalVector.x = MathUtils.fixedPointDivide(dy, length);
        coll.normalVector.y = MathUtils.fixedPointDivide(dx, length);
    }

    /** Runs once per tick: rotating panels follow their proc, door panels follow open/closed. */
    private void updatePanels() {
        for (int i = 0; i < panelCount; i++) {
            int p = panelProc[i];
            int stage = p >= 0 ? procStage[p] : 0;
            if (panelKind[i] == PANEL_KIND_ROTATE) {
                if (stage != panelLastStage[i]) {
                    panelLastStage[i] = stage;
                    placePanel(i, stage);
                }
            } else {
                boolean closed = stage == 0;
                if (closed != panelClosed[i]) {
                    panelClosed[i] = closed;
                    Vector defs = panelDefs[i];
                    for (int d = 0; d < defs.size(); d++) {
                        ((WallDefinition) defs.elementAt(d)).disabled = !closed;
                    }
                }
            }
        }
        for (int p = 0; p < procCount; p++) {
            boolean closed = procStage[p] == 0;
            Vector hide = procHideWalls[p];
            if (hide != null) {
                for (int h = 0; h < hide.size(); h++) {
                    ((WallDefinition) hide.elementAt(h)).hidden = closed;
                }
            }
            Vector faces = procFaces[p];
            if (faces != null && closed != procFaceShowsDoor[p]) {
                procFaceShowsDoor[p] = closed;
                Vector saved = procFaceSaved[p];
                for (int f = 0; f < faces.size(); f++) {
                    WallSurface surf = (WallSurface) faces.elementAt(f);
                    if (closed) {
                        surf.upperTextureId = (byte) procDoorSlot[p];
                        surf.mainTextureId = (byte) procDoorSlot[p];
                        surf.lowerTextureId = (byte) procDoorSlot[p];
                    } else {
                        int[] sv = (int[]) saved.elementAt(f);
                        surf.upperTextureId = (byte) sv[0];
                        surf.mainTextureId = (byte) sv[1];
                        surf.lowerTextureId = (byte) sv[2];
                    }
                }
            }
        }
    }

    private void updateLevelExit() {
        int alive = 0;
        for (int i = 0; i < monCount; i++) {
            if (monState[i] != MSTATE_INACTIVE && monState[i] != MSTATE_DEAD) alive++;
        }
        boolean near = Math.abs((playerBody.x - (exitX << 16)) >> 16) < CELL_UNITS
                && Math.abs((playerBody.z - (exitZ << 16)) >> 16) < CELL_UNITS;
        if (!near) return;
        if (alive == 0) {
            levelCompleted = true;
        } else if (hudMessageTimer <= 0) {
            setMessage("SECTOR NOT CLEAR", 30);
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
        this.blastFlashTimer = 0;
        for (int i = 0; i < MAX_BLAST_FX; i++) blastFxLeft[i] = 0;
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
        for (int i = 0; i < MAX_PROPS; i++) propMovProc[i] = -1;
        for (int i = 0; i < MAX_LIMBS; i++) limbActive[i] = false;
        for (int i = 0; i < MAX_PROJ; i++) projActive[i] = false;

        // Load Chasm 3D entities sidecar
        String entPath = "/levels/entities" + (levelIndex + 1) + ".bin";
        loadEntitiesSidecar(entPath);

        // Doors, switches and keys are per level; the player starts each level without keys
        loadProcsSidecar("/levels/procs" + (levelIndex + 1) + ".bin");
        attachMovers();
        attachPanels();
        for (int m = 0; m < monCount; m++) {
            pushMonsterOutOfProps(m);   // a platform may stand where a monster was placed
        }
        hasGreenKey = false;
        hasRedKey = false;
        hasBlueKey = false;

        setMessage(levelLabel(levelIndex, true), 45);
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
            this.exitX = dis.readShort();
            this.exitZ = dis.readShort();

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

            // Items: >H count, then >Bhh (modelId 17..28, ix, iz)
            int iCount = dis.readUnsignedShort();
            this.itemCount = iCount < MAX_ITEMS ? iCount : MAX_ITEMS;
            for (int i = 0; i < iCount; i++) {
                int modelId = dis.readUnsignedByte();
                int xUnits = dis.readShort();
                int zUnits = dis.readShort();
                if (i >= MAX_ITEMS) continue;

                if (modelId < 17 || modelId > 29) modelId = 17 + (i % 12);
                itemActive[i] = true;
                itemType[i] = modelId - 17;
                itemModelId[i] = modelId;
                itemX[i] = xUnits << 16;
                itemZ[i] = zUnits << 16;
                itemSector[i] = getSectorAtPoint(itemX[i], itemZ[i]);
            }

            // Props: >H count, then >BhhB (modelId 30..32 or 38 gate lock, px, pz, pang)
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

                if (modelId != LOCK_MODEL && (modelId < 30 || modelId > 32)) modelId = 30 + (i % 3);
                propActive[i] = true;
                propHP[i] = modelId == LOCK_MODEL ? LOCK_HP : BARREL_HP;
                propHalfX[i] = halfX << 16;
                propHalfZ[i] = halfZ << 16;
                propModelId[i] = modelId;
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
                if (wall.disabled) continue;
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
                itemActive[i] = false;
                if (itemModelId[i] == 27) {
                    hasRedKey = true;
                    setMessage("RED KEY", 30);
                    continue;
                }
                if (itemModelId[i] == 28) {
                    hasGreenKey = true;
                    setMessage("GREEN KEY", 30);
                    continue;
                }
                if (itemModelId[i] == 29) {
                    hasBlueKey = true;
                    setMessage("BLUE KEY", 30);
                    continue;
                }
                int t = itemType[i] % 8;
                if (t == 0) {
                    health = Math.min(100, health + 25);
                    setMessage("MEDKIT +25 HP", 25);
                } else if (t == 1) {
                    armor = Math.min(100, armor + 25);
                    setMessage("ARMOR +25", 25);
                } else {
                    int w = t - 2;
                    if (w >= 0 && w < 6) {
                        weaponOwned[w] = true;
                        ammo[w] = Math.min(200, ammo[w] + 15);
                        if (curWeapon < w) curWeapon = w;
                        setMessage("GOT " + WEAPON_NAMES[w], 30);
                    }
                }
                SoundManager.playSfx(SoundManager.SFX_PICKUP);
            }
        }
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
        if (ammo[curWeapon] < cost) {
            // Auto-fallback to Rift Rifle with emergency cell recharge
            if (curWeapon != 0) {
                curWeapon = 0;
            }
            if (ammo[0] < 1) ammo[0] = 15;
            cost = 1;
        }
        ammo[curWeapon] -= cost;
        weaponCooldown = WEAPON_COOLDOWN[curWeapon];
        weaponAnimTick = weaponCooldown;
        muzzleFlashTimer = 2;

        int sfx = (curWeapon == 1) ? SoundManager.SFX_SHOTGUN
                : (curWeapon == 2) ? SoundManager.SFX_CHAINGUN
                : (curWeapon == 3) ? SoundManager.SFX_CROSSBOW
                : (curWeapon == 4) ? SoundManager.SFX_SAW
                : (curWeapon == 5) ? SoundManager.SFX_ROCKET
                : SoundManager.SFX_RIFLE;
        SoundManager.playSfx(sfx);

        if (curWeapon >= 3) {
            // Projectile weapons (Crossbow / Megazapper bolt, Sawblade disc). Damage is applied
            // only when the projectile reaches a monster, so there is no double hit.
            spawnProjectile(
                true,
                (curWeapon == 4) ? PROJ_DISK : PROJ_BOLT,
                playerBody.x,
                playerBody.y - (8 << 16),
                playerBody.z,
                playerBody.rotation,
                WEAPON_DAMAGE[curWeapon]
            );
            return true;
        }

        // Hitscan weapons (Rift Rifle, Double Shotgun, Volcano Chaingun)
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

        // Barrels and gate locks are targets too. A prop only counts if it is nearer than the best monster.
        int bestProp = -1;
        for (int p = 0; p < propCount; p++) {
            if (!isShootable(p)) continue;
            int rx = propX[p] - playerBody.x;
            int rz = propZ[p] - playerBody.z;
            int viewX = (int) (((long) cos * rx - (long) sin * rz) >> 16);
            int viewDepth = (int) (((long) sin * rx + (long) cos * rz) >> 16);
            if (viewDepth <= (8 << 16) || viewDepth > (900 << 16) || viewDepth >= bestDepth) continue;
            int half = Math.max(propHalfX[p], propHalfZ[p]) + (12 << 16) + (viewDepth >> 3);
            if (Math.abs(viewX) <= half && hasLineOfSight(playerBody.x, playerBody.z, propX[p], propZ[p])) {
                bestDepth = viewDepth;
                bestProp = p;
                bestMon = -1;
            }
        }

        if (bestProp >= 0) {
            damageProp(bestProp, damage);
        } else if (bestMon >= 0) {
            damageMonster(bestMon, damage);
        }
    }

    /** True if prop p is a live explosive barrel. */
    public boolean isBarrel(int p) {
        return p >= 0 && p < propCount && propActive[p] && propModelId[p] == BARREL_MODEL;
    }

    /** True if prop p is a live gate lock (shot to open its 'destroy' proc). */
    public boolean isLock(int p) {
        return p >= 0 && p < propCount && propActive[p] && propModelId[p] == LOCK_MODEL;
    }

    /** True if a shot or projectile can damage prop p (barrels and gate locks). */
    public boolean isShootable(int p) {
        return isBarrel(p) || isLock(p);
    }

    /** Damages a shootable prop: a barrel may explode; a lock that breaks fires its 'destroy' procs. */
    public void damageProp(int p, int dmg) {
        if (isLock(p)) {
            propHP[p] -= dmg;
            if (propHP[p] <= 0) {
                propActive[p] = false;
                destroyLocksAt(cellOf(propX[p]), cellOf(propZ[p]));
            }
        } else {
            damageBarrel(p, dmg);
        }
    }

    /** Opens every proc whose 'destroy' link is on cell (cx, cz) (the lock there was shot). */
    private void destroyLocksAt(int cx, int cz) {
        for (int p = 0; p < procCount; p++) {
            int[] tr = procTrig[p];
            for (int t = 0; t < tr.length; t += 3) {
                if (tr[t] == TRIG_DESTROY && tr[t + 1] == cx && tr[t + 2] == cz) {
                    destroyProc(p);
                    break;
                }
            }
        }
    }

    /** Applies damage to barrel p. A barrel at 0 HP explodes and may set off barrels nearby. */
    public void damageBarrel(int p, int dmg) {
        if (!isBarrel(p)) return;
        propHP[p] -= dmg;
        if (propHP[p] <= 0) detonateBarrel(p);
    }

    /**
     * Explodes barrel `first`. Each blast deals round(base * (1 - d / radius)) to monsters and the
     * player within the radius, and to other barrels (which chain if they reach 0 HP). Chained
     * barrels are processed from a queue, so the chain is iterative and each barrel explodes once.
     */
    private void detonateBarrel(int first) {
        int head = 0;
        int tail = 0;
        propActive[first] = false;    // no longer collidable, rendered, or targetable
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

            for (int p = 0; p < propCount; p++) {
                if (!isBarrel(p)) continue;
                int d = blastDistance(cx, cz, propX[p], propZ[p]);
                if (d >= BARREL_BLAST_RADIUS) continue;
                propHP[p] -= blastDamage(d);
                if (propHP[p] <= 0) {
                    propActive[p] = false;
                    blastQueue[tail++] = p;
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

    /** Starts a fireball at (cx, cz), reusing the oldest slot when all are busy. */
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

    /** Ages the fireballs and the screen flash by one tick. */
    private void updateBlastFx() {
        if (blastFlashTimer > 0) blastFlashTimer--;
        for (int i = 0; i < MAX_BLAST_FX; i++) {
            if (blastFxLeft[i] > 0) blastFxLeft[i]--;
        }
    }

    /**
     * Draws the barrel blast on the finished 3D frame: a whole-screen warm flash while the
     * player is inside the blast radius, then the fireballs at the blast points. Fireballs are
     * projected with the camera of the current frame; they are not occluded by walls.
     */
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
            // Grows to about 27 px, then shrinks while it fades.
            int radius = age < 6 ? 3 + age * 4 : 27 - (age - 6) * 6;
            if (radius < 2) radius = 2;
            drawFireball(buf, blastScreen[0], blastScreen[1], radius, 255 * left / BLAST_FX_LIFE);
        }
    }

    /** Alpha-blends one fireball disc: white-yellow core, orange ring, dark red edge. */
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
                int or = (old >> 16) & 0xFF, og = (old >> 8) & 0xFF, ob = old & 0xFF;
                int nr = or + ((((col >> 16) & 0xFF) - or) * a >> 8);
                int ng = og + ((((col >> 8) & 0xFF) - og) * a >> 8);
                int nb = ob + (((col & 0xFF) - ob) * a >> 8);
                buf[row + x] = (nr << 16) | (ng << 8) | nb;
            }
        }
    }

    /** Blast damage at distance d (world units), rounded, linear falloff to 0 at the radius. */
    private static int blastDamage(int d) {
        return (BARREL_BLAST_DAMAGE * (BARREL_BLAST_RADIUS - d) + (BARREL_BLAST_RADIUS >> 1)) / BARREL_BLAST_RADIUS;
    }

    /** Horizontal distance in whole world units between two 16.16 points. */
    private static int blastDistance(int ax, int az, int bx, int bz) {
        int dx = (ax - bx) >> 16;
        int dz = (az - bz) >> 16;
        return isqrt(dx * dx + dz * dz);
    }

    /** Integer square root (floor) of a non-negative int. */
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
            // Sever Head on overkill or Sawblade/Shotgun/Megazapper finisher
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
            if (wall.disabled || !CollisionSystem.isWallSolid(wall)) continue;
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
        setMessage("SECTOR CLEAR! PRESS * FOR NEXT LEVEL", 90);
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
        updateBlastFx();
        if (hudMessageTimer > 0) hudMessageTimer--;

        updateDetachedLimbs();
        updateProjectiles();
        updateMonsters();
        updateProcs();
        updateLevelExit();
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

            if (!hasLineOfSight(projX[i], projZ[i], nx, nz)) {
                projActive[i] = false;
                continue;
            }

            projX[i] = nx;
            projZ[i] = nz;
            // Bolts keep their heading; saw-disks spin around their travel direction.
            if (projModelId[i] == PROJ_DISK) {
                projSpin[i] = MathUtils.wrapAngle(projSpin[i] + 32000);
            }
            projSector[i] = getSectorAtPoint(nx, nz);

            if (projFromPlayer[i]) {
                boolean hit = false;
                for (int m = 0; m < monCount; m++) {
                    if (monState[m] == MSTATE_INACTIVE || monState[m] == MSTATE_DEAD) continue;
                    if (Math.abs(nx - monX[m]) < hitRadius && Math.abs(nz - monZ[m]) < hitRadius) {
                        damageMonster(m, projDamage[i]);
                        projActive[i] = false;
                        hit = true;
                        break;
                    }
                }
                if (!hit) {
                    for (int p = 0; p < propCount; p++) {
                        if (!isShootable(p)) continue;
                        if (Math.abs(nx - propX[p]) < hitRadius + propHalfX[p]
                                && Math.abs(nz - propZ[p]) < hitRadius + propHalfZ[p]) {
                            projActive[i] = false;
                            damageProp(p, projDamage[i]);
                            break;
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

                if (distApprox < (380 << 16) && hasLineOfSight(monX[m], monZ[m], playerBody.x, playerBody.z)) {
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

                if (hasArm && distApprox < (150 << 16) && (monStateTick[m] > 10)
                        && hasLineOfSight(monX[m], monZ[m], playerBody.x, playerBody.z)) {
                    monState[m] = MSTATE_ATTACK;
                    monStateTick[m] = 0;
                } else if (distApprox > (44 << 16)) {
                    // Step toward player
                    int stepX = (dx >= 0 ? 1 : -1) * Math.min(Math.abs(dx), 3 << 16);
                    int stepZ = (dz >= 0 ? 1 : -1) * Math.min(Math.abs(dz), 3 << 16);
                    int nx = monX[m] + stepX;
                    int nz = monZ[m] + stepZ;
                    // Direct step first. When a prop box blocks it, the monster commits to a
                    // detour side (+90 or -90 degrees) for DETOUR_TICKS so it does not
                    // oscillate at the box corner.
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
                    if (distApprox < (240 << 16) && hasLineOfSight(monX[m], monZ[m], playerBody.x, playerBody.z)) {
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
            setMessage("YOU DIED! PRESS FIRE TO RESTART", 999);
        }
    }
}
