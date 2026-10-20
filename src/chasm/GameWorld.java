package chasm;

import java.io.DataInputStream;
import java.io.InputStream;

/**
 * Complete Game World, Physics, AI, Dismemberment & Combat State for Chasm: The Rift J2ME.
 * Zero object allocations during the game loop.
 */
public final class GameWorld {
    public static final int MAP_SIZE = 64;
    public static final int MAP_CELLS = 4096;

    // 64x64 Multi-Height Level Grid
    public final byte[] cellType  = new byte[MAP_CELLS]; // 0=Open, 1=Wall, 2=Door, 3=Window, 4=Exit
    public final short[] floorH   = new short[MAP_CELLS];
    public final short[] ceilH    = new short[MAP_CELLS];
    public final short[] wallH    = new short[MAP_CELLS];
    public final byte[] floorTex  = new byte[MAP_CELLS];
    public final byte[] ceilTex   = new byte[MAP_CELLS];
    public final byte[] wallTex   = new byte[MAP_CELLS];
    public final byte[] stepTex   = new byte[MAP_CELLS];
    public final byte[] lightLvl  = new byte[MAP_CELLS];
    public final byte[] doorOpen  = new byte[MAP_CELLS];
    public final byte[] doorState = new byte[MAP_CELLS]; // 0=closed, 1=opening, 2=open, 3=closing
    public final short[] doorWait = new short[MAP_CELLS];

    // Level Metadata
    public int currentLevel = 1;
    public int exitCellX = 21;
    public int exitCellY = 26;
    public boolean levelCompleted = false;

    public static final String[] LEVEL_NAMES = {
        "",
        "EP1: POWER STATION",
        "EP2: AROUND PYRAMIDS",
        "EP2: PYRAMID OF SHMUHER",
        "EP3: TIME CHASM SANCTUM"
    };

    public static final String[][] LEVEL_BRIEFINGS = {
        {},
        {
            "MISSION 1: POWER STATION",
            "Time-Rift anomalies have",
            "overrun the military power",
            "plant. Mutated Commandos,",
            "Spiders and Gargoyles guard",
            "the turbine complex.",
            "Navigate the stepped",
            "courtyards and reach the",
            "reactor core exit!"
        },
        {
            "MISSION 2: AROUND PYRAMIDS",
            "Through the Chasm portal,",
            "you emerge in ancient Egypt.",
            "Undead Mummies, Scorpions,",
            "and Armored Punishers",
            "patrol the desert canyon.",
            "Ascend the stepped temple",
            "terraces and breach the",
            "Great Pyramid entrance!"
        },
        {
            "MISSION 3: PYRAMID OF SHMUHER",
            "Deep inside the burial",
            "labyrinth of Pharaoh Shmuher,",
            "Deadly Jokers and Skeleton",
            "Warriors hurl saw-blades",
            "from elevated ledges.",
            "Find the ancient keys,",
            "open the stone gates, and",
            "enter the Time Sanctum!"
        },
        {
            "MISSION 4: TIME CHASM SANCTUM",
            "The heart of the Time Rift!",
            "Cyber-Faust troopers and",
            "Guardian Lions defend the",
            "grand stepped citadel.",
            "Climb the Sanctum altar,",
            "defeat the Chasm Overlord,",
            "and seal the Rift forever!"
        }
    };

    // Player State (8.8 fixed-point: 256 = 1.0 cell; height: 64 = 1.0 cell)
    public int playerX;
    public int playerY;
    public int playerFloorZ; // Smoothly interpolated floor height (8.8 fixed-point of height units)
    public int playerJumpZ;  // Jump offset in 8.8 fixed-point
    public int playerVelZ;
    public int playerAng;    // 0..1023
    public int playerPitch;  // Horizon offset (-32..+32)
    public int walkPhase;
    public int health = 100;
    public int armor = 25;
    public int keysMask = 0; // 1=Red, 2=Green, 4=Blue
    public int score = 0;
    public int killsCount = 0;
    public int totalMonsters = 0;
    public boolean playerDead = false;

    // Weapons (0..5)
    public static final int NUM_WEAPONS = 6;
    public static final String[] WEAPON_NAMES = {
        "DOUBLE RIFLE",
        "SUPER SHOTGUN",
        "VOLCANO MINIGUN",
        "BLADE LAUNCHER",
        "ENERGY CROSSBOW",
        "GRENADE LAUNCHER"
    };
    public static final int[] WEAPON_COOLDOWN = { 8, 13, 3, 9, 11, 14 };
    public static final int[] WEAPON_DAMAGE   = { 26, 64, 16, 48, 58, 85 };

    public int weaponOwnedMask = 3; // Starts with Double Rifle + Super Shotgun
    public final int[] ammo = new int[NUM_WEAPONS];
    public int curWeapon = 0;
    public int weapCooldown = 0;
    public int weapAnimTick = 0;
    public int muzzleFlashTick = 0;
    public int damageFlashTick = 0;
    public int pickupFlashTick = 0;

    // HUD Message Banner
    public String hudMessage = "";
    public int hudMessageTimer = 0;

    // Monsters Pool (Zero GC)
    public static final int MAX_MONSTERS = 40;
    public static final int STATE_IDLE   = 0;
    public static final int STATE_CHASE  = 1;
    public static final int STATE_ATTACK = 2;
    public static final int STATE_PAIN   = 3;
    public static final int STATE_DEAD   = 4;

    // Monster stats per model type (0..10) from CHASM.INF
    private static final int[] MON_HP   = { 70, 85, 45, 90, 110, 75, 140, 85, 120, 130, 260 };
    private static final int[] MON_SPD  = { 15, 18, 22,  0,  14, 13,  15, 18,  16,  19,  16 };
    private static final int[] MON_DMG  = { 10, 12,  9, 12,  16, 11,  18, 14,  15,  16,  22 };
    private static final String[] MON_NAMES = {
        "MONG SOLDIER", "WINGMAN", "GIANT SPIDER", "AUTO-TURRET", "FAUST TROOPER",
        "UNDEAD MUMMY", "PUNISHER", "JOKER", "SKELETON", "GUARDIAN LION", "CHASM OVERLORD"
    };

    public int numMonsters;
    public final byte[] monType      = new byte[MAX_MONSTERS];
    public final int[] monX          = new int[MAX_MONSTERS];
    public final int[] monY          = new int[MAX_MONSTERS];
    public final int[] monZ          = new int[MAX_MONSTERS];
    public final short[] monAng      = new short[MAX_MONSTERS];
    public final short[] monHP       = new short[MAX_MONSTERS];
    public final byte[] monState     = new byte[MAX_MONSTERS];
    public final byte[] monTimer     = new byte[MAX_MONSTERS];
    public final byte[] monHitFlash  = new byte[MAX_MONSTERS];
    // Dismemberment body-part mask: bit0(1)=LArm, bit1(2)=LStump, bit2(4)=RArm, bit3(8)=RStump, bit4(16)=Head, bit5(32)=HStump
    public final int[] monGroupMask  = new int[MAX_MONSTERS];

    // Items Pool (Zero GC)
    public static final int MAX_ITEMS = 48;
    public int numItems;
    public final byte[] itemType   = new byte[MAX_ITEMS];
    public final int[] itemX       = new int[MAX_ITEMS];
    public final int[] itemY       = new int[MAX_ITEMS];
    public final boolean[] itemAct = new boolean[MAX_ITEMS];

    // 3D Props & Explosive Barrels Pool (Zero GC)
    public static final int MAX_PROPS = 36;
    public int numProps;
    public final byte[] propModelId = new byte[MAX_PROPS];
    public final int[] propX        = new int[MAX_PROPS];
    public final int[] propY        = new int[MAX_PROPS];
    public final short[] propAng    = new short[MAX_PROPS];
    public final short[] propHP     = new short[MAX_PROPS];
    public final boolean[] propAct  = new boolean[MAX_PROPS];

    // 3D Projectiles & Flying Severed Gibs Pool (Zero GC)
    public static final int MAX_PROJ = 24;
    public final boolean[] projAct   = new boolean[MAX_PROJ];
    public final byte[] projModelId  = new byte[MAX_PROJ]; // 33=Gib, 34=Disk, 35=Rocket/Sword/Bolt
    public final boolean[] projFromPlayer = new boolean[MAX_PROJ];
    public final int[] projX         = new int[MAX_PROJ];
    public final int[] projY         = new int[MAX_PROJ];
    public final int[] projZ         = new int[MAX_PROJ]; // 8.8 fixed-point height
    public final int[] projVX        = new int[MAX_PROJ];
    public final int[] projVY        = new int[MAX_PROJ];
    public final int[] projVZ        = new int[MAX_PROJ];
    public final short[] projAng     = new short[MAX_PROJ];
    public final byte[] projDmg      = new byte[MAX_PROJ];
    public final byte[] projTTL      = new byte[MAX_PROJ];

    private int rngState = 0x13579BDF;

    public int nextRand() {
        rngState ^= (rngState << 13);
        rngState ^= (rngState >>> 17);
        rngState ^= (rngState << 5);
        return rngState & 0x7FFFFFFF;
    }

    public void initNewCampaign() {
        health = 100;
        armor = 25;
        score = 0;
        weaponOwnedMask = 3; // Double Rifle + Super Shotgun
        ammo[0] = 60;
        ammo[1] = 24;
        ammo[2] = 80;
        ammo[3] = 15;
        ammo[4] = 12;
        ammo[5] = 8;
        curWeapon = 0;
    }

    public void loadLevel(int lvl, RaycasterEngine engine) {
        currentLevel = lvl;
        levelCompleted = false;
        playerDead = false;
        keysMask = 0;
        killsCount = 0;
        weapCooldown = 0;
        weapAnimTick = 0;
        muzzleFlashTick = 0;
        damageFlashTick = 0;
        pickupFlashTick = 0;
        playerPitch = 0;
        playerJumpZ = 0;
        playerVelZ = 0;
        if (health <= 0) health = 100;

        for (int i = 0; i < MAP_CELLS; i++) {
            doorOpen[i] = 0;
            doorState[i] = 0;
            doorWait[i] = 0;
        }
        for (int i = 0; i < MAX_PROJ; i++) {
            projAct[i] = false;
        }

        engine.loadLevelTextures(lvl);

        InputStream is = null;
        try {
            is = getClass().getResourceAsStream("/map" + lvl + ".bin");
            DataInputStream dis = new DataInputStream(is);

            playerX = dis.readUnsignedShort();
            playerY = dis.readUnsignedShort();
            int sang = dis.readUnsignedByte();
            exitCellX = dis.readUnsignedByte();
            exitCellY = dis.readUnsignedByte();

            // Convert Chasm 0..7 spawn angle to 0..1023
            // In MAP.01 spawn is at (31.5, 58.5) facing North (-Y = 768)
            if (lvl == 1 || lvl == 2 || lvl == 4) {
                playerAng = 768; // North (-Y)
            } else {
                playerAng = ((8 - sang) & 7) * 128;
            }

            dis.readFully(cellType);
            for (int i = 0; i < MAP_CELLS; i++) {
                floorH[i] = dis.readShort();
                ceilH[i]  = dis.readShort();
                wallH[i]  = dis.readShort();
            }
            dis.readFully(floorTex);
            dis.readFully(ceilTex);
            dis.readFully(wallTex);
            dis.readFully(stepTex);
            dis.readFully(lightLvl);

            int mCnt = dis.readUnsignedShort();
            if (mCnt > MAX_MONSTERS) mCnt = MAX_MONSTERS;
            numMonsters = mCnt;
            totalMonsters = mCnt;
            for (int m = 0; m < mCnt; m++) {
                int mt = dis.readUnsignedByte();
                if (mt < 0 || mt > 10) mt = 0;
                monType[m] = (byte) mt;
                monX[m] = dis.readUnsignedShort();
                monY[m] = dis.readUnsignedShort();
                int ma = dis.readUnsignedByte();
                monAng[m] = (short) ((ma * 128) & 1023);
                monHP[m] = (short) MON_HP[mt];
                monState[m] = STATE_IDLE;
                monTimer[m] = (byte) (m & 7);
                monHitFlash[m] = 0;
                // Initial intact body mask: LArm(1) | RArm(4) | Head(16) = 21
                monGroupMask[m] = 1 | 4 | 16;
                int mc = (monX[m] >> 8) + ((monY[m] >> 8) << 6);
                monZ[m] = (mc >= 0 && mc < MAP_CELLS) ? floorH[mc] : 0;
            }

            int iCnt = dis.readUnsignedShort();
            if (iCnt > MAX_ITEMS) iCnt = MAX_ITEMS;
            numItems = iCnt;
            for (int i = 0; i < iCnt; i++) {
                itemType[i] = dis.readByte();
                itemX[i] = dis.readUnsignedShort();
                itemY[i] = dis.readUnsignedShort();
                itemAct[i] = true;
            }

            int pCnt = dis.readUnsignedShort();
            if (pCnt > MAX_PROPS) pCnt = MAX_PROPS;
            numProps = pCnt;
            for (int p = 0; p < pCnt; p++) {
                propModelId[p] = dis.readByte();
                propX[p] = dis.readUnsignedShort();
                propY[p] = dis.readUnsignedShort();
                propAng[p] = (short) ((dis.readUnsignedByte() * 128) & 1023);
                propHP[p] = 35;
                propAct[p] = true;
            }
        } catch (Exception e) {
        } finally {
            if (is != null) {
                try { is.close(); } catch (Exception ex) {}
            }
        }

        // Ensure a sentinel monster is near the starting area so the player sees 3D polygonal
        // enemies and stepped architecture right away
        ensureStarterEncounter();

        int startCell = (playerX >> 8) + ((playerY >> 8) << 6);
        if (startCell >= 0 && startCell < MAP_CELLS) {
            playerFloorZ = floorH[startCell] << 8;
        } else {
            playerFloorZ = 0;
        }
        showMessage(LEVEL_NAMES[lvl], 45);
    }

    private void ensureStarterEncounter() {
        int dirX = RaycasterEngine.COS[playerAng & 1023];
        int dirY = RaycasterEngine.SIN[playerAng & 1023];
        int tx = playerX + ((dirX * 3) >> 8);
        int ty = playerY + ((dirY * 3) >> 8);
        int cx = tx >> 8, cy = ty >> 8;
        if (cx > 1 && cx < 62 && cy > 1 && cy < 62) {
            int cidx = cx + (cy << 6);
            if (cellType[cidx] == 0 && numMonsters < MAX_MONSTERS) {
                int m = numMonsters++;
                totalMonsters++;
                monType[m] = (byte) (currentLevel == 1 ? 0 : (currentLevel == 2 ? 5 : (currentLevel == 3 ? 7 : 4)));
                monX[m] = (cx << 8) + 128;
                monY[m] = (cy << 8) + 128;
                monZ[m] = floorH[cidx];
                monAng[m] = (short) ((playerAng + 512) & 1023);
                monHP[m] = (short) MON_HP[monType[m]];
                monState[m] = STATE_CHASE;
                monTimer[m] = 0;
                monHitFlash[m] = 0;
                monGroupMask[m] = 1 | 4 | 16;
            }
        }
    }

    public void showMessage(String msg, int ticks) {
        hudMessage = msg;
        hudMessageTimer = ticks;
    }

    public int getCameraEyeZ() {
        int bob = (RaycasterEngine.SIN[(walkPhase << 3) & 1023] * 3) >> 16;
        return ((playerFloorZ + playerJumpZ) >> 8) + 42 + bob;
    }

    public int getPlayerCellLight() {
        int cx = playerX >> 8;
        int cy = playerY >> 8;
        if (cx < 0 || cx >= 64 || cy < 0 || cy >= 64) return 12;
        int l = lightLvl[cx + (cy << 6)] & 15;
        if (muzzleFlashTick > 0) l = 15;
        return l;
    }

    public void triggerJump() {
        if (playerJumpZ == 0 && playerVelZ == 0 && !playerDead) {
            playerVelZ = 920;
        }
    }

    public void cycleWeapon(int dir) {
        for (int i = 1; i <= NUM_WEAPONS; i++) {
            int next = (curWeapon + dir * i + NUM_WEAPONS * 2) % NUM_WEAPONS;
            if ((weaponOwnedMask & (1 << next)) != 0) {
                curWeapon = next;
                weapAnimTick = 0;
                showMessage(WEAPON_NAMES[curWeapon], 25);
                return;
            }
        }
    }

    public void useAction(SoundManager sound) {
        // Check adjacent cells in view direction for doors, switches, or exit
        int dirX = RaycasterEngine.COS[playerAng & 1023];
        int dirY = RaycasterEngine.SIN[playerAng & 1023];
        for (int dist = 160; dist <= 420; dist += 128) {
            int tx = (playerX + ((dirX * dist) >> 16)) >> 8;
            int ty = (playerY + ((dirY * dist) >> 16)) >> 8;
            if (tx < 0 || tx >= 64 || ty < 0 || ty >= 64) continue;
            int idx = tx + (ty << 6);
            int ct = cellType[idx] & 0xFF;
            if (ct == 2) {
                if (doorState[idx] == 0 || doorState[idx] == 3) {
                    doorState[idx] = 1;
                    sound.playSfx(SoundManager.SFX_DOOR);
                    showMessage("DOOR OPENING", 25);
                    return;
                }
            } else if (ct == 4) {
                levelCompleted = true;
                sound.playSfx(SoundManager.SFX_SWITCH);
                return;
            }
        }
        // Also check all 4 cardinal neighbors for doors
        int px = playerX >> 8;
        int py = playerY >> 8;
        int[] dxs = { 1, -1, 0, 0 };
        int[] dys = { 0, 0, 1, -1 };
        for (int k = 0; k < 4; k++) {
            int nx = px + dxs[k];
            int ny = py + dys[k];
            if (nx >= 0 && nx < 64 && ny >= 0 && ny < 64) {
                int nidx = nx + (ny << 6);
                if (cellType[nidx] == 2 && doorState[nidx] == 0) {
                    doorState[nidx] = 1;
                    sound.playSfx(SoundManager.SFX_DOOR);
                    showMessage("DOOR OPENING", 25);
                    return;
                }
            }
        }
    }

    public void fireWeapon(SoundManager sound) {
        if (playerDead || weapCooldown > 0) return;
        if (ammo[curWeapon] <= 0) {
            showMessage("NO AMMO!", 25);
            cycleWeapon(-1);
            return;
        }
        ammo[curWeapon]--;
        weapCooldown = WEAPON_COOLDOWN[curWeapon];
        weapAnimTick = weapCooldown;
        muzzleFlashTick = 3;

        switch (curWeapon) {
            case 0:
                sound.playSfx(SoundManager.SFX_SHOTGUN);
                sound.vibrate(35);
                fireHitscan(WEAPON_DAMAGE[0], 32);
                break;
            case 1:
                sound.playSfx(SoundManager.SFX_SUPERSHOT);
                sound.vibrate(65);
                fireHitscan(WEAPON_DAMAGE[1], 56);
                break;
            case 2:
                sound.playSfx(SoundManager.SFX_CHAINGUN);
                sound.vibrate(25);
                fireHitscan(WEAPON_DAMAGE[2], 36);
                break;
            case 3:
                sound.playSfx(SoundManager.SFX_DISK);
                spawnProjectile(true, 34, playerX, playerY, getCameraEyeZ() - 8, playerAng, 68, WEAPON_DAMAGE[3]);
                break;
            case 4:
                sound.playSfx(SoundManager.SFX_CROSSBOW);
                spawnProjectile(true, 35, playerX, playerY, getCameraEyeZ() - 8, playerAng, 82, WEAPON_DAMAGE[4]);
                break;
            case 5:
                sound.playSfx(SoundManager.SFX_EXPLODE);
                sound.vibrate(70);
                spawnProjectile(true, 35, playerX, playerY, getCameraEyeZ() - 8, playerAng, 58, WEAPON_DAMAGE[5]);
                break;
        }
    }

    private void fireHitscan(int dmg, int aimToleranceAngle) {
        int bestDist = 8192;
        int bestMon = -1;
        int bestProp = -1;

        for (int m = 0; m < numMonsters; m++) {
            if (monState[m] == STATE_DEAD) continue;
            int dx = monX[m] - playerX;
            int dy = monY[m] - playerY;
            int dist = fastDist(dx, dy);
            if (dist >= bestDist || dist < 32) continue;

            int angTo = atan2Angle1024(dy, dx);
            int diff = angleDiff(playerAng, angTo);
            int tol = aimToleranceAngle + (3072 / (dist + 64));
            if (diff <= tol && hasLineOfSight(playerX, playerY, monX[m], monY[m])) {
                bestDist = dist;
                bestMon = m;
            }
        }

        for (int p = 0; p < numProps; p++) {
            if (!propAct[p] || propModelId[p] != 30) continue; // Explosive barrel
            int dx = propX[p] - playerX;
            int dy = propY[p] - playerY;
            int dist = fastDist(dx, dy);
            if (dist < bestDist && dist >= 32) {
                int angTo = atan2Angle1024(dy, dx);
                int diff = angleDiff(playerAng, angTo);
                if (diff <= aimToleranceAngle + 20 && hasLineOfSight(playerX, playerY, propX[p], propY[p])) {
                    bestDist = dist;
                    bestProp = p;
                    bestMon = -1;
                }
            }
        }

        if (bestMon >= 0) {
            damageMonster(bestMon, dmg, curWeapon == 1 || curWeapon == 3);
        } else if (bestProp >= 0) {
            propHP[bestProp] -= dmg;
            if (propHP[bestProp] <= 0) {
                detonateBarrel(bestProp);
            }
        }
    }

    private void detonateBarrel(int pIdx) {
        propAct[pIdx] = false;
        int bx = propX[pIdx];
        int by = propY[pIdx];
        int bc = (bx >> 8) + ((by >> 8) << 6);
        int bz = (bc >= 0 && bc < MAP_CELLS) ? floorH[bc] + 24 : 24;
        showMessage("BARREL DETONATED!", 30);
        for (int k = 0; k < 4; k++) {
            spawnGib(bx, by, bz, (k * 256 + (nextRand() & 127)) & 1023);
        }
        // Splash damage to nearby monsters
        for (int m = 0; m < numMonsters; m++) {
            if (monState[m] == STATE_DEAD) continue;
            int dist = fastDist(monX[m] - bx, monY[m] - by);
            if (dist < 768) {
                damageMonster(m, 95 - (dist >> 4), true);
            }
        }
    }

    public void damageMonster(int m, int dmg, boolean canSeverLimb) {
        if (monState[m] == STATE_DEAD) return;
        monHP[m] -= dmg;
        monHitFlash[m] = 4;

        // Authentic Chasm Limb Dismemberment (.CAR group bitmask)
        int maxHp = MON_HP[monType[m]];
        if ((canSeverLimb || dmg >= 25) && monHP[m] < (maxHp * 2) / 3) {
            int mask = monGroupMask[m];
            if ((mask & 1) != 0) {
                // Sever Left Arm -> show bloody Left Stump (bit 2)
                monGroupMask[m] = (mask & ~1) | 2;
                spawnGib(monX[m], monY[m], monZ[m] + 36, (monAng[m] + 256) & 1023);
                showMessage(MON_NAMES[monType[m]] + " ARM SEVERED!", 28);
            } else if ((mask & 4) != 0 && monHP[m] < maxHp / 2) {
                // Sever Right Weapon Arm -> show bloody Right Stump (bit 8)
                monGroupMask[m] = (mask & ~4) | 8;
                spawnGib(monX[m], monY[m], monZ[m] + 36, (monAng[m] - 256) & 1023);
                showMessage(MON_NAMES[monType[m]] + " DISARMED!", 28);
            }
        }

        if (monHP[m] <= 0) {
            monHP[m] = 0;
            monState[m] = STATE_DEAD;
            monTimer[m] = 0;
            killsCount++;
            score += maxHp * 10;
            if ((monGroupMask[m] & 16) != 0 && canSeverLimb) {
                monGroupMask[m] = (monGroupMask[m] & ~16) | 32;
            }
            spawnGib(monX[m], monY[m], monZ[m] + 40, nextRand() & 1023);
            if (monType[m] == 10 && currentLevel == 4) {
                showMessage("CHASM OVERLORD DEFEATED! PORTAL OPEN!", 70);
            }
        } else {
            monState[m] = STATE_PAIN;
            monTimer[m] = 5;
        }
    }

    private void spawnGib(int x, int y, int z, int ang) {
        for (int i = 0; i < MAX_PROJ; i++) {
            if (!projAct[i]) {
                projAct[i] = true;
                projModelId[i] = 33; // GIB1.3O
                projFromPlayer[i] = true;
                projX[i] = x;
                projY[i] = y;
                projZ[i] = z << 8;
                projVX[i] = (RaycasterEngine.COS[ang] * 22) >> 16;
                projVY[i] = (RaycasterEngine.SIN[ang] * 22) >> 16;
                projVZ[i] = 640 + (nextRand() & 255);
                projAng[i] = (short) ang;
                projDmg[i] = 0;
                projTTL[i] = 28;
                return;
            }
        }
    }

    private void spawnProjectile(boolean fromPlayer, int mdlId, int x, int y, int z, int ang, int speed, int dmg) {
        for (int i = 0; i < MAX_PROJ; i++) {
            if (!projAct[i]) {
                projAct[i] = true;
                projModelId[i] = (byte) mdlId;
                projFromPlayer[i] = fromPlayer;
                projX[i] = x;
                projY[i] = y;
                projZ[i] = z << 8;
                projVX[i] = (RaycasterEngine.COS[ang & 1023] * speed) >> 16;
                projVY[i] = (RaycasterEngine.SIN[ang & 1023] * speed) >> 16;
                projVZ[i] = 0;
                projAng[i] = (short) (ang & 1023);
                projDmg[i] = (byte) dmg;
                projTTL[i] = 45;
                return;
            }
        }
    }

    public void updateTick(int moveForward, int strafeDir, int turnDir, int pitchDir, SoundManager sound) {
        if (hudMessageTimer > 0) hudMessageTimer--;
        if (weapCooldown > 0) weapCooldown--;
        if (weapAnimTick > 0) weapAnimTick--;
        if (muzzleFlashTick > 0) muzzleFlashTick--;
        if (damageFlashTick > 0) damageFlashTick--;
        if (pickupFlashTick > 0) pickupFlashTick--;

        if (playerDead) return;

        // 1. Player Rotation & Pitch
        if (turnDir != 0) {
            playerAng = (playerAng + turnDir * 28) & 1023;
        }
        if (pitchDir != 0) {
            playerPitch += pitchDir * 4;
            if (playerPitch < -32) playerPitch = -32;
            if (playerPitch > 32) playerPitch = 32;
        }

        // 2. Player Movement & Step Climbing (Ступени)
        int dx = 0, dy = 0;
        int moveSpeed = 42;
        if (moveForward != 0) {
            dx += (RaycasterEngine.COS[playerAng] * moveForward * moveSpeed) >> 16;
            dy += (RaycasterEngine.SIN[playerAng] * moveForward * moveSpeed) >> 16;
        }
        if (strafeDir != 0) {
            int sAng = (playerAng + 256) & 1023;
            dx += (RaycasterEngine.COS[sAng] * strafeDir * moveSpeed) >> 16;
            dy += (RaycasterEngine.SIN[sAng] * strafeDir * moveSpeed) >> 16;
        }

        if (dx != 0 || dy != 0) {
            walkPhase = (walkPhase + 1) & 127;
            tryMovePlayer(dx, dy, sound);
        }

        // Smooth interpolation of playerFloorZ up/down steps + Jump physics
        int curCell = (playerX >> 8) + ((playerY >> 8) << 6);
        int targetFloorZ = (curCell >= 0 && curCell < MAP_CELLS) ? (floorH[curCell] << 8) : 0;
        if (playerFloorZ < targetFloorZ) {
            playerFloorZ += ((targetFloorZ - playerFloorZ) >> 1) + 256;
            if (playerFloorZ > targetFloorZ) playerFloorZ = targetFloorZ;
        } else if (playerFloorZ > targetFloorZ) {
            playerFloorZ -= ((playerFloorZ - targetFloorZ) >> 1) + 256;
            if (playerFloorZ < targetFloorZ) playerFloorZ = targetFloorZ;
        }

        if (playerJumpZ > 0 || playerVelZ != 0) {
            playerJumpZ += playerVelZ;
            playerVelZ -= 220;
            if (playerJumpZ <= 0) {
                playerJumpZ = 0;
                playerVelZ = 0;
            }
        }

        // 3. Update Sliding / Rising Doors
        for (int i = 0; i < MAP_CELLS; i++) {
            if (cellType[i] == 2) {
                int st = doorState[i];
                int op = doorOpen[i] & 0xFF;
                if (st == 1) {
                    op += 12;
                    if (op >= 120) {
                        op = 120;
                        doorState[i] = 2;
                        doorWait[i] = 90;
                    }
                    doorOpen[i] = (byte) op;
                } else if (st == 2) {
                    if (doorWait[i] > 0) {
                        doorWait[i]--;
                    } else if ((i != curCell)) {
                        doorState[i] = 3;
                    }
                } else if (st == 3) {
                    op -= 8;
                    if (op <= 0) {
                        op = 0;
                        doorState[i] = 0;
                    }
                    doorOpen[i] = (byte) op;
                }
            }
        }

        // 4. Check Item Pickups
        checkItemPickups(sound);

        // 5. Update Projectiles & Flying 3D Gibs
        updateProjectiles(sound);

        // 6. Update Monsters AI & Animations
        updateMonsters(sound);
    }

    private void tryMovePlayer(int dx, int dy, SoundManager sound) {
        int curZ = (playerFloorZ + playerJumpZ) >> 8;
        int nx = playerX + dx;
        if (canEnterPos(nx, playerY, curZ, sound)) {
            playerX = nx;
        }
        int ny = playerY + dy;
        if (canEnterPos(playerX, ny, curZ, sound)) {
            playerY = ny;
        }
    }

    private boolean canEnterPos(int x, int y, int curFootZ, SoundManager sound) {
        int radius = 56;
        int minCX = (x - radius) >> 8;
        int maxCX = (x + radius) >> 8;
        int minCY = (y - radius) >> 8;
        int maxCY = (y + radius) >> 8;

        for (int cy = minCY; cy <= maxCY; cy++) {
            for (int cx = minCX; cx <= maxCX; cx++) {
                if (cx < 1 || cx >= 63 || cy < 1 || cy >= 63) return false;
                int idx = cx + (cy << 6);
                int ct = cellType[idx] & 0xFF;
                if (ct == 1) return false;
                if (ct == 4) {
                    levelCompleted = true;
                    sound.playSfx(SoundManager.SFX_SWITCH);
                    return true;
                }
                if (ct == 2) {
                    if (doorState[idx] == 0) {
                        doorState[idx] = 1;
                        sound.playSfx(SoundManager.SFX_DOOR);
                        showMessage("DOOR OPENING", 20);
                    }
                    if ((doorOpen[idx] & 0xFF) < 80) return false;
                }
                // Check step height (max climbable step without jumping is 20 height units)
                int fh = floorH[idx];
                int ch = ceilH[idx];
                if (fh - curFootZ > 20) return false;
                if (ch - fh < 44) return false;
            }
        }
        return true;
    }

    private void checkItemPickups(SoundManager sound) {
        for (int i = 0; i < numItems; i++) {
            if (!itemAct[i]) continue;
            int dx = itemX[i] - playerX;
            int dy = itemY[i] - playerY;
            if (dx > -140 && dx < 140 && dy > -140 && dy < 140) {
                int it = itemType[i] & 0xFF;
                itemAct[i] = false;
                pickupFlashTick = 3;
                score += 150;
                sound.playSfx(SoundManager.SFX_PICKUP);

                switch (it) {
                    case 0:
                        health = health + 25 > 100 ? 100 : health + 25;
                        showMessage("PICKED UP MEDKIT (+25 HP)", 30);
                        break;
                    case 1:
                        health = health + 100 > 200 ? 200 : health + 100;
                        showMessage("MEGAHEALTH! (+100 HP)", 35);
                        break;
                    case 2:
                        armor = armor + 50 > 200 ? 200 : armor + 50;
                        showMessage("BODY ARMOR (+50)", 30);
                        break;
                    case 3:
                        armor = armor + 25 > 200 ? 200 : armor + 25;
                        showMessage("COMBAT HELMET (+25)", 30);
                        break;
                    case 4:
                        for (int w = 0; w < NUM_WEAPONS; w++) ammo[w] += 10;
                        showMessage("AMMO PACK COLLECTED", 25);
                        break;
                    case 5:
                        weaponOwnedMask |= (1 << 1);
                        ammo[1] += 16;
                        curWeapon = 1;
                        showMessage("GOT SUPER SHOTGUN!", 35);
                        break;
                    case 6:
                        weaponOwnedMask |= (1 << 2);
                        ammo[2] += 60;
                        curWeapon = 2;
                        showMessage("GOT VOLCANO MINIGUN!", 35);
                        break;
                    case 7:
                        weaponOwnedMask |= (1 << 3);
                        ammo[3] += 12;
                        curWeapon = 3;
                        showMessage("GOT BLADE LAUNCHER!", 35);
                        break;
                    case 8:
                        weaponOwnedMask |= (1 << 4);
                        ammo[4] += 10;
                        curWeapon = 4;
                        showMessage("GOT ENERGY CROSSBOW!", 35);
                        break;
                    case 9:
                        weaponOwnedMask |= (1 << 5);
                        ammo[5] += 8;
                        curWeapon = 5;
                        showMessage("GOT GRENADE LAUNCHER!", 35);
                        break;
                    case 10:
                        keysMask |= 1;
                        showMessage("FOUND RED KEY!", 40);
                        break;
                    case 11:
                        keysMask |= 2;
                        showMessage("FOUND GREEN KEY!", 40);
                        break;
                    case 12:
                        keysMask |= 4;
                        showMessage("FOUND BLUE KEY!", 40);
                        break;
                }
            }
        }
    }

    private void updateProjectiles(SoundManager sound) {
        for (int i = 0; i < MAX_PROJ; i++) {
            if (!projAct[i]) continue;
            projTTL[i]--;
            if (projTTL[i] <= 0) {
                projAct[i] = false;
                continue;
            }

            projX[i] += projVX[i];
            projY[i] += projVY[i];
            projAng[i] = (short) ((projAng[i] + 96) & 1023);

            int cx = projX[i] >> 8;
            int cy = projY[i] >> 8;
            if (cx < 1 || cx >= 63 || cy < 1 || cy >= 63) {
                projAct[i] = false;
                continue;
            }
            int cidx = cx + (cy << 6);

            if (projModelId[i] == 33) {
                // Flying 3D Gib chunk with gravity
                projZ[i] += projVZ[i];
                projVZ[i] -= 180;
                int fz = floorH[cidx] << 8;
                if (projZ[i] <= fz) {
                    projZ[i] = fz;
                    projVX[i] >>= 1;
                    projVY[i] >>= 1;
                    projVZ[i] = 0;
                }
                continue;
            }

            if (cellType[cidx] == 1 || (cellType[cidx] == 2 && (doorOpen[cidx] & 0xFF) < 60)) {
                projAct[i] = false;
                continue;
            }

            if (projFromPlayer[i]) {
                for (int m = 0; m < numMonsters; m++) {
                    if (monState[m] == STATE_DEAD) continue;
                    int dx = monX[m] - projX[i];
                    int dy = monY[m] - projY[i];
                    if (dx > -110 && dx < 110 && dy > -110 && dy < 110) {
                        damageMonster(m, projDmg[i] & 0xFF, true);
                        sound.playSfx(SoundManager.SFX_GIB);
                        projAct[i] = false;
                        break;
                    }
                }
            } else {
                int dx = playerX - projX[i];
                int dy = playerY - projY[i];
                if (dx > -110 && dx < 110 && dy > -110 && dy < 110) {
                    damagePlayer(projDmg[i] & 0xFF, sound);
                    projAct[i] = false;
                }
            }
        }
    }

    private void updateMonsters(SoundManager sound) {
        for (int m = 0; m < numMonsters; m++) {
            if (monHitFlash[m] > 0) monHitFlash[m]--;
            int st = monState[m];
            if (st == STATE_DEAD) {
                if (monTimer[m] < 20) monTimer[m]++;
                continue;
            }

            int dx = playerX - monX[m];
            int dy = playerY - monY[m];
            int dist = fastDist(dx, dy);
            if (dist > 4800) continue; // Sleep far monsters for CPU efficiency

            monTimer[m]++;
            int mt = monType[m];

            if (st == STATE_PAIN) {
                if (monTimer[m] >= 6) {
                    monState[m] = STATE_CHASE;
                    monTimer[m] = 0;
                }
                continue;
            }

            if (st == STATE_IDLE) {
                if (dist < 2200 && ((m + monTimer[m]) & 3) == 0) {
                    if (hasLineOfSight(monX[m], monY[m], playerX, playerY)) {
                        monState[m] = STATE_CHASE;
                        monTimer[m] = 0;
                        sound.playSfx(SoundManager.SFX_MONALERT);
                    }
                }
                continue;
            }

            int angToPlayer = atan2Angle1024(dy, dx);
            monAng[m] = (short) angToPlayer;

            if (st == STATE_ATTACK) {
                if (monTimer[m] == 5) {
                    if (dist < 280) {
                        damagePlayer(MON_DMG[mt], sound);
                    } else if ((monGroupMask[m] & 4) != 0) {
                        // Monster can only shoot ranged projectiles if its Right Weapon Arm (bit 4) is intact!
                        int mdlProj = (mt == 7) ? 34 : 35;
                        spawnProjectile(false, mdlProj, monX[m], monY[m], monZ[m] + 34, angToPlayer, 48, MON_DMG[mt]);
                    }
                }
                if (monTimer[m] >= 10) {
                    monState[m] = STATE_CHASE;
                    monTimer[m] = 0;
                }
                continue;
            }

            // STATE_CHASE
            boolean hasRightArm = (monGroupMask[m] & 4) != 0;
            boolean rangedCapable = (mt == 0 || mt == 1 || mt == 3 || mt == 4 || mt == 6 || mt == 7 || mt == 8 || mt == 10) && hasRightArm;
            if (dist < 230 || (rangedCapable && dist < 1600 && (monTimer[m] & 15) == 12 && hasLineOfSight(monX[m], monY[m], playerX, playerY))) {
                monState[m] = STATE_ATTACK;
                monTimer[m] = 0;
                continue;
            }

            int spd = MON_SPD[mt];
            if (spd > 0 && dist > 180) {
                int stepX = (RaycasterEngine.COS[angToPlayer] * spd) >> 16;
                int stepY = (RaycasterEngine.SIN[angToPlayer] * spd) >> 16;
                int nx = monX[m] + stepX;
                int ny = monY[m] + stepY;
                int ncx = nx >> 8, ncy = ny >> 8;
                if (ncx > 1 && ncx < 62 && ncy > 1 && ncy < 62) {
                    int nidx = ncx + (ncy << 6);
                    if (cellType[nidx] == 0 && Math.abs(floorH[nidx] - monZ[m]) <= 20) {
                        monX[m] = nx;
                        monY[m] = ny;
                        monZ[m] = floorH[nidx];
                    }
                }
            }
        }
    }

    public void damagePlayer(int dmg, SoundManager sound) {
        if (playerDead) return;
        damageFlashTick = 4;
        sound.playSfx(SoundManager.SFX_PAIN);
        sound.vibrate(80);

        if (armor > 0) {
            int absorb = dmg >> 1;
            if (absorb > armor) absorb = armor;
            armor -= absorb;
            dmg -= absorb;
        }
        health -= dmg;
        if (health <= 0) {
            health = 0;
            playerDead = true;
            sound.playSfx(SoundManager.SFX_MONDIE);
            showMessage("YOU FELL INTO THE CHASM! PRESS FIRE", 120);
        }
    }

    public boolean hasLineOfSight(int x0, int y0, int x1, int y1) {
        int steps = 12;
        int dx = (x1 - x0) / steps;
        int dy = (y1 - y0) / steps;
        int cx = x0, cy = y0;
        for (int i = 1; i < steps; i++) {
            cx += dx;
            cy += dy;
            int gx = cx >> 8, gy = cy >> 8;
            if (gx < 0 || gx >= 64 || gy < 0 || gy >= 64) return false;
            int idx = gx + (gy << 6);
            if (cellType[idx] == 1) return false;
            if (cellType[idx] == 2 && (doorOpen[idx] & 0xFF) < 60) return false;
        }
        return true;
    }

    public static int fastDist(int dx, int dy) {
        if (dx < 0) dx = -dx;
        if (dy < 0) dy = -dy;
        int min = dx < dy ? dx : dy;
        return dx + dy - (min >> 1);
    }

    public static int atan2Angle1024(int dy, int dx) {
        if (dx == 0 && dy == 0) return 0;
        int ax = dx < 0 ? -dx : dx;
        int ay = dy < 0 ? -dy : dy;
        int angle;
        if (ax >= ay) {
            angle = (ay * 128) / (ax == 0 ? 1 : ax);
        } else {
            angle = 256 - (ax * 128) / (ay == 0 ? 1 : ay);
        }
        if (dx < 0) angle = 512 - angle;
        if (dy < 0) angle = 1024 - angle;
        return angle & 1023;
    }

    public static int angleDiff(int a, int b) {
        int d = (a - b) & 1023;
        return d > 512 ? 1024 - d : d;
    }
}
