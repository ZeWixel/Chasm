package chasm;

import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.game.GameCanvas;

/**
 * Main GameCanvas, Input Handler, HUD, Automap, and Game Loop for Chasm: The Rift J2ME.
 * Compatible with Nokia S60 and Sony Ericsson keypad phones (MIDP 2.0 / CLDC 1.1).
 */
public final class ChasmCanvas extends GameCanvas implements Runnable {
    public static final int SCREEN_MAIN_MENU = 0;
    public static final int SCREEN_BRIEFING  = 1;
    public static final int SCREEN_PLAYING   = 2;
    public static final int SCREEN_PAUSED    = 3;
    public static final int SCREEN_CONTROLS  = 4;
    public static final int SCREEN_VICTORY   = 5;

    // Nokia S60 & Sony Ericsson softkey keycodes
    private static final int KEY_SOFT_LEFT1  = -6;
    private static final int KEY_SOFT_RIGHT1 = -7;
    private static final int KEY_SOFT_LEFT2  = -21;
    private static final int KEY_SOFT_RIGHT2 = -22;
    private static final int KEY_SE_BACK     = -11;

    private final ChasmMIDlet midlet;
    private final SoundManager sound;
    private final RaycasterEngine raycaster;
    private final Renderer3D renderer3D;
    private final GameWorld world;
    private final Model3D[] models;

    private volatile boolean running = false;
    private Thread gameThread;

    public int screenState = SCREEN_MAIN_MENU;
    private int menuCursor = 0;
    private int selectedStartLevel = 1;
    private int menuPreviewAngle = 0;
    private boolean showAutomap = false;

    // Viewport & Framebuffer (allocated once per resolution)
    private int scrW = 0;
    private int scrH = 0;
    private int viewW = 0;
    private int viewH = 0;
    private int hudH = 32;
    private int[] screenBuf;
    private short[] zBuffer;

    // Held keypad flags for simultaneous multi-key movement on Nokia S60 & Sony Ericsson
    private boolean kUp, kDown, kLeft, kRight, kStrafeL, kStrafeR, kFire;
    private int fpsDisplay = 25;
    private int frameCounter = 0;
    private long fpsTimer = 0L;

    public ChasmCanvas(ChasmMIDlet midlet, SoundManager sound) {
        super(false);
        setFullScreenMode(true);
        this.midlet = midlet;
        this.sound = sound;
        this.raycaster = new RaycasterEngine();
        this.renderer3D = new Renderer3D();
        this.world = new GameWorld();
        this.models = Model3D.loadAllModels("/models.bin");
        this.world.initNewCampaign();
    }

    public Renderer3D getRenderer3D() {
        return renderer3D;
    }

    public GameWorld getWorld() {
        return world;
    }

    public void setSelectedStartLevel(int lvl) {
        if (lvl >= 1 && lvl <= 4) {
            selectedStartLevel = lvl;
        }
    }

    public int getSelectedStartLevel() {
        return selectedStartLevel;
    }

    public void startLoop() {
        if (running) return;
        running = true;
        sound.playMusic(0);
        gameThread = new Thread(this);
        gameThread.start();
    }

    public void stopLoop() {
        running = false;
    }

    private void ensureBuffers() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0) w = 240;
        if (h <= 0) h = 320;
        if (w != scrW || h != scrH || screenBuf == null) {
            scrW = w;
            scrH = h;
            hudH = (h >= 240) ? 34 : 26;
            viewW = w;
            viewH = h - hudH;
            screenBuf = new int[viewW * viewH];
            zBuffer = new short[viewW * viewH];
        }
    }

    public void run() {
        fpsTimer = System.currentTimeMillis();
        while (running) {
            long frameStart = System.currentTimeMillis();
            try {
                ensureBuffers();
                updateLogic();
                renderScreen();
                flushGraphics();
            } catch ( Throwable t ) {
                // Keep game loop alive on any transient device quirk
            }

            frameCounter++;
            long now = System.currentTimeMillis();
            if (now - fpsTimer >= 1000L) {
                fpsDisplay = frameCounter;
                frameCounter = 0;
                fpsTimer = now;
            }

            long elapsed = System.currentTimeMillis() - frameStart;
            long sleepMs = 36L - elapsed; // ~28 FPS target
            if (sleepMs < 4L) sleepMs = 4L;
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException ie) {}
        }
    }

    private void updateLogic() {
        menuPreviewAngle = (menuPreviewAngle + 18) & 1023;
        if (screenState != SCREEN_PLAYING) {
            return;
        }

        // Poll MIDP 2.0 GameCanvas key states + raw key flags
        int ks = getKeyStates();
        int moveF = 0;
        int strafe = 0;
        int turn = 0;

        if (kUp || (ks & UP_PRESSED) != 0) moveF = 1;
        if (kDown || (ks & DOWN_PRESSED) != 0) moveF = -1;
        if (kLeft || (ks & LEFT_PRESSED) != 0) turn = -1;
        if (kRight || (ks & RIGHT_PRESSED) != 0) turn = 1;
        if (kStrafeL) strafe = -1;
        if (kStrafeR) strafe = 1;

        if (kFire || (ks & FIRE_PRESSED) != 0) {
            if (world.playerDead) {
                kFire = false;
                world.health = 100;
                world.loadLevel(world.currentLevel, raycaster);
                return;
            }
            world.fireWeapon(sound);
        }

        world.updateTick(moveF, strafe, turn, 0, sound);

        if (world.levelCompleted) {
            world.levelCompleted = false;
            if (world.currentLevel < 4) {
                world.currentLevel++;
                selectedStartLevel = world.currentLevel;
                midlet.saveSettings();
                screenState = SCREEN_BRIEFING;
                sound.playMusic(world.currentLevel);
            } else {
                screenState = SCREEN_VICTORY;
                sound.playMusic(0);
            }
        }
    }

    private void renderScreen() {
        Graphics g = getGraphics();
        switch (screenState) {
            case SCREEN_MAIN_MENU:
                drawMainMenu(g);
                break;
            case SCREEN_BRIEFING:
                drawBriefingScreen(g);
                break;
            case SCREEN_PLAYING:
                drawPlayingView(g);
                break;
            case SCREEN_PAUSED:
                drawPlayingView(g);
                drawPauseOverlay(g);
                break;
            case SCREEN_CONTROLS:
                drawControlsScreen(g);
                break;
            case SCREEN_VICTORY:
                drawVictoryScreen(g);
                break;
        }
    }

    private void drawTiledBackground(Graphics g) {
        if (raycaster.tileRGB != null && raycaster.tileW > 0 && raycaster.tileH > 0) {
            for (int y = 0; y < scrH; y += raycaster.tileH) {
                for (int x = 0; x < scrW; x += raycaster.tileW) {
                    g.drawRGB(raycaster.tileRGB, 0, raycaster.tileW, x, y, raycaster.tileW, raycaster.tileH, false);
                }
            }
        } else {
            g.setColor(0x141814);
            g.fillRect(0, 0, scrW, scrH);
        }
    }

    private void drawMainMenu(Graphics g) {
        drawTiledBackground(g);

        // Draw authentic LOADING.CEL Chasm logo at top
        int topY = 6;
        if (raycaster.logoRGB != null && raycaster.logoW > 0) {
            int lx = (scrW - raycaster.logoW) >> 1;
            g.drawRGB(raycaster.logoRGB, 0, raycaster.logoW, lx, topY, raycaster.logoW, raycaster.logoH, false);
            topY += raycaster.logoH + 4;
        } else {
            g.setColor(0xFFCC44);
            g.drawString("CHASM: THE RIFT", scrW >> 1, 12, Graphics.TOP | Graphics.HCENTER);
            topY = 34;
        }

        // Render live rotating 3D polygonal monster preview in a mini-viewport!
        int prevW = Math.min(viewW, 110);
        int prevH = Math.min(viewH, 68);
        if (scrH >= 208 && models.length > 7) {
            int px = (scrW - prevW) >> 1;
            int py = topY;
            for (int i = 0; i < prevW * prevH; i++) {
                screenBuf[i] = 0xFF101612;
                zBuffer[i] = 30000;
            }
            int previewModelIdx = ((menuPreviewAngle >> 8) & 1) == 0 ? 7 : 6; // Joker / Punisher
            int kf = (menuPreviewAngle >> 6) % 5;
            int interp = (menuPreviewAngle & 63) << 2;
            renderer3D.renderWorldModel(
                models[previewModelIdx], kf, (kf + 1) % 5, interp,
                256, 0, 0, menuPreviewAngle, 256,
                0xFFFF, 14, false,
                0, 0, 34, 0, prevH >> 1,
                screenBuf, zBuffer, prevW, prevH
            );
            g.drawRGB(screenBuf, 0, prevW, px, py, prevW, prevH, false);
            g.setColor(0x7A8B68);
            g.drawRect(px - 1, py - 1, prevW + 1, prevH + 1);
            topY += prevH + 6;
        }

        Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
        g.setFont(font);
        int lineH = font.getHeight() + 3;

        String[] items = {
            "START CAMPAIGN",
            "LEVEL: " + GameWorld.LEVEL_NAMES[selectedStartLevel],
            "3D: " + renderer3D.getShadingModeName(),
            "AUDIO: " + (sound.musicEnabled ? "MIDI+SFX ON" : "MUTE"),
            "CONTROLS & HELP",
            "EXIT GAME"
        };

        for (int i = 0; i < items.length; i++) {
            int iy = topY + i * lineH;
            if (i == menuCursor) {
                g.setColor(0x3A4F28);
                g.fillRect(8, iy - 1, scrW - 16, lineH);
                g.setColor(0xFFE866);
                g.drawString("> " + items[i] + " <", scrW >> 1, iy, Graphics.TOP | Graphics.HCENTER);
            } else {
                g.setColor(0xB8C8A0);
                g.drawString(items[i], scrW >> 1, iy, Graphics.TOP | Graphics.HCENTER);
            }
        }
    }

    private void drawBriefingScreen(Graphics g) {
        drawTiledBackground(g);
        g.setColor(0x182218);
        g.fillRect(8, 8, scrW - 16, scrH - 16);
        g.setColor(0x88A070);
        g.drawRect(8, 8, scrW - 17, scrH - 17);

        Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
        g.setFont(font);
        int lineH = font.getHeight() + 2;

        String[] lines = GameWorld.LEVEL_BRIEFINGS[world.currentLevel];
        int y = 16;
        for (int i = 0; i < lines.length; i++) {
            g.setColor(i == 0 ? 0xFFDD55 : 0xC8DEC0);
            g.drawString(lines[i], scrW >> 1, y, Graphics.TOP | Graphics.HCENTER);
            y += lineH;
        }

        g.setColor(0xFFE066);
        g.drawString("PRESS FIRE / 5 TO DEPLOY", scrW >> 1, scrH - lineH - 12, Graphics.TOP | Graphics.HCENTER);
    }

    private void drawPlayingView(Graphics g) {
        int camX = world.playerX;
        int camY = world.playerY;
        int camZ = world.getCameraEyeZ();
        int camAng = world.playerAng;
        int horizonY = (viewH >> 1) + world.playerPitch;
        if (horizonY < 16) horizonY = 16;
        if (horizonY > viewH - 16) horizonY = viewH - 16;

        // 1. Raycast Multi-Height World (walls of varying height, steps/stairs, floors, skybox)
        raycaster.renderView(
            world, camX, camY, camZ, camAng, horizonY,
            screenBuf, zBuffer, viewW, viewH
        );

        // 2. Render 3D Polygonal Props & Explosive Barrels (.3O)
        for (int p = 0; p < world.numProps; p++) {
            if (!world.propAct[p]) continue;
            int mdlId = world.propModelId[p] & 0xFF;
            if (mdlId < models.length) {
                int px = world.propX[p];
                int py = world.propY[p];
                int cidx = ((px >> 8) & 63) + (((py >> 8) & 63) << 6);
                int pz = world.floorH[cidx];
                int plight = world.lightLvl[cidx] & 15;
                renderer3D.renderWorldModel(
                    models[mdlId], 0, 0, 0,
                    px, py, pz, world.propAng[p], 256,
                    0xFFFF, plight, false,
                    camX, camY, camZ, camAng, horizonY,
                    screenBuf, zBuffer, viewW, viewH
                );
            }
        }

        // 3. Render 3D Polygonal Pickups & Keys (.3O rotating)
        int itemSpinAng = menuPreviewAngle;
        for (int i = 0; i < world.numItems; i++) {
            if (!world.itemAct[i]) continue;
            int it = world.itemType[i] & 0xFF;
            int mdlId = 17 + it;
            if (mdlId >= 17 && mdlId < models.length) {
                int ix = world.itemX[i];
                int iy = world.itemY[i];
                int cidx = ((ix >> 8) & 63) + (((iy >> 8) & 63) << 6);
                int iz = world.floorH[cidx] + 6;
                renderer3D.renderWorldModel(
                    models[mdlId], 0, 0, 0,
                    ix, iy, iz, itemSpinAng, 220,
                    0xFFFF, 14, false,
                    camX, camY, camZ, camAng, horizonY,
                    screenBuf, zBuffer, viewW, viewH
                );
            }
        }

        // 4. Render 3D Projectiles & Flying Dismembered Gibs (.3O)
        for (int i = 0; i < GameWorld.MAX_PROJ; i++) {
            if (!world.projAct[i]) continue;
            int mdlId = world.projModelId[i] & 0xFF;
            if (mdlId < models.length) {
                renderer3D.renderWorldModel(
                    models[mdlId], 0, 0, 0,
                    world.projX[i], world.projY[i], world.projZ[i] >> 8,
                    world.projAng[i], 220,
                    0xFFFF, 15, false,
                    camX, camY, camZ, camAng, horizonY,
                    screenBuf, zBuffer, viewW, viewH
                );
            }
        }

        // 5. Render 3D Polygonal Enemies (.CAR) with Flat Shading & Vertex Colors / Painting
        for (int m = 0; m < world.numMonsters; m++) {
            int mt = world.monType[m] & 0xFF;
            if (mt >= models.length) continue;
            int st = world.monState[m];
            int timer = world.monTimer[m] & 0xFF;

            // Map AI state to extracted keyframes (0..1=Idle, 2..4=Walk, 5..6=Attack, 7=Pain, 8..9=Death)
            int frameA = 0, frameB = 1, interp = (timer & 7) << 5;
            if (st == GameWorld.STATE_IDLE) {
                frameA = 0;
                frameB = 1;
            } else if (st == GameWorld.STATE_CHASE) {
                int wIdx = (timer >> 1) % 3;
                frameA = 2 + wIdx;
                frameB = 2 + ((wIdx + 1) % 3);
                interp = (timer & 1) << 7;
            } else if (st == GameWorld.STATE_ATTACK) {
                frameA = 5;
                frameB = 6;
                interp = Math.min(255, timer * 28);
            } else if (st == GameWorld.STATE_PAIN) {
                frameA = 7;
                frameB = 7;
                interp = 0;
            } else if (st == GameWorld.STATE_DEAD) {
                if (timer < 6) {
                    frameA = 8;
                    frameB = 9;
                    interp = timer * 42;
                } else {
                    frameA = 9;
                    frameB = 9;
                    interp = 0;
                }
            }

            int mx = world.monX[m];
            int my = world.monY[m];
            int cidx = ((mx >> 8) & 63) + (((my >> 8) & 63) << 6);
            int mLight = (cidx >= 0 && cidx < 4096) ? (world.lightLvl[cidx] & 15) : 12;

            renderer3D.renderWorldModel(
                models[mt], frameA, frameB, interp,
                mx, my, world.monZ[m], world.monAng[m], 256,
                world.monGroupMask[m], mLight, world.monHitFlash[m] > 0,
                camX, camY, camZ, camAng, horizonY,
                screenBuf, zBuffer, viewW, viewH
            );
        }

        // 6. Render First-Person 3D Weapon Model (.3O) in Player's Hand
        int weapMdlIdx = 11 + world.curWeapon;
        if (weapMdlIdx < models.length && !world.playerDead) {
            Model3D wMdl = models[weapMdlIdx];
            int bobX = (RaycasterEngine.COS[(world.walkPhase << 3) & 1023] * 6) >> 16;
            int bobY = Math.abs((RaycasterEngine.SIN[(world.walkPhase << 3) & 1023] * 5) >> 16);
            int recoil = world.weapAnimTick * 2;
            int wfA = 0, wfB = 0, wInterp = 0;
            if (world.weapAnimTick > 0 && wMdl.fCount > 1) {
                wfA = (world.weapAnimTick) % wMdl.fCount;
                wfB = (wfA + 1) % wMdl.fCount;
                wInterp = 128;
            }
            renderer3D.renderFirstPersonWeapon(
                wMdl, wfA, wfB, wInterp,
                bobX, bobY, recoil,
                world.getPlayerCellLight(), world.muzzleFlashTick > 0,
                screenBuf, zBuffer, viewW, viewH
            );
        }

        // 7. Apply Damage / Pickup Screen Flash Tint
        if (world.damageFlashTick > 0) {
            int len = viewW * viewH;
            for (int i = 0; i < len; i += 2) {
                screenBuf[i] = 0xFFCC1111;
            }
        } else if (world.pickupFlashTick > 0) {
            int len = viewW * viewH;
            for (int i = 0; i < len; i += 4) {
                screenBuf[i] = 0xFFEECC44;
            }
        }

        // Blit 3D Viewport to Screen
        g.drawRGB(screenBuf, 0, viewW, 0, 0, viewW, viewH, false);

        // 8. Draw Chasm Crosshair
        int cx = viewW >> 1;
        int cy = horizonY;
        g.setColor(0x55FF55);
        g.drawLine(cx - 5, cy, cx - 2, cy);
        g.drawLine(cx + 2, cy, cx + 5, cy);
        g.drawLine(cx, cy - 5, cx, cy - 2);
        g.drawLine(cx, cy + 2, cx, cy + 5);

        // 9. Draw Automap Overlay if toggled with '0'
        if (showAutomap) {
            drawAutomapOverlay(g);
        }

        // 10. Draw Top HUD Message & Shading Indicator
        Font smallFont = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setFont(smallFont);
        if (world.hudMessageTimer > 0 && world.hudMessage != null) {
            g.setColor(0x000000);
            g.drawString(world.hudMessage, (viewW >> 1) + 1, 5, Graphics.TOP | Graphics.HCENTER);
            g.setColor(0xFFEE55);
            g.drawString(world.hudMessage, viewW >> 1, 4, Graphics.TOP | Graphics.HCENTER);
        }
        g.setColor(0x88CC88);
        g.drawString(fpsDisplay + "FPS [" + renderer3D.getShadingModeName() + "]", 3, viewH - smallFont.getHeight() - 2, Graphics.TOP | Graphics.LEFT);

        // 11. Draw Authentic STATUS2.CEL Chasm Status Bar at bottom
        drawStatusBar(g, smallFont);
    }

    private void drawStatusBar(Graphics g, Font font) {
        int barY = viewH;
        g.setColor(0x222620);
        g.fillRect(0, barY, scrW, hudH);
        if (raycaster.hudBarRGB != null && raycaster.hudBarW > 0) {
            int bx = (scrW - raycaster.hudBarW) >> 1;
            if (bx < 0) bx = 0;
            g.drawRGB(raycaster.hudBarRGB, 0, raycaster.hudBarW, bx, barY, Math.min(scrW, raycaster.hudBarW), Math.min(hudH, raycaster.hudBarH), false);
        }
        g.setColor(0x687858);
        g.drawLine(0, barY, scrW, barY);

        int textY1 = barY + 2;
        int textY2 = barY + (hudH >> 1);

        g.setColor(world.health <= 25 ? 0xFF4444 : 0x88FF66);
        g.drawString("HP:" + world.health, 4, textY1, Graphics.TOP | Graphics.LEFT);

        g.setColor(0x66CCFF);
        g.drawString("ARM:" + world.armor, 4, textY2, Graphics.TOP | Graphics.LEFT);

        g.setColor(0xFFDD44);
        g.drawString("AMMO:" + world.ammo[world.curWeapon], scrW >> 1, textY1, Graphics.TOP | Graphics.HCENTER);

        g.setColor(0xE0E8D0);
        g.drawString(GameWorld.WEAPON_NAMES[world.curWeapon], scrW >> 1, textY2, Graphics.TOP | Graphics.HCENTER);

        // Keycard indicators (Red, Green, Blue)
        int kx = scrW - 34;
        if ((world.keysMask & 1) != 0) {
            g.setColor(0xFF3333);
            g.fillRect(kx, textY1 + 2, 8, 8);
        }
        if ((world.keysMask & 2) != 0) {
            g.setColor(0x33FF33);
            g.fillRect(kx + 10, textY1 + 2, 8, 8);
        }
        if ((world.keysMask & 4) != 0) {
            g.setColor(0x3388FF);
            g.fillRect(kx + 20, textY1 + 2, 8, 8);
        }
        g.setColor(0xBBBB99);
        g.drawString("L" + world.currentLevel, scrW - 4, textY2, Graphics.TOP | Graphics.RIGHT);
    }

    private void drawAutomapOverlay(Graphics g) {
        int mapBox = Math.min(viewW, viewH) - 24;
        int cellPx = Math.max(2, mapBox / 32);
        int ox = (viewW - 32 * cellPx) >> 1;
        int oy = (viewH - 32 * cellPx) >> 1;

        g.setColor(0x101810);
        g.fillRect(ox - 2, oy - 2, 32 * cellPx + 4, 32 * cellPx + 4);
        g.setColor(0x88AA77);
        g.drawRect(ox - 2, oy - 2, 32 * cellPx + 4, 32 * cellPx + 4);

        int pcx = world.playerX >> 8;
        int pcy = world.playerY >> 8;
        int startX = Math.max(0, Math.min(32, pcx - 16));
        int startY = Math.max(0, Math.min(32, pcy - 16));

        for (int gy = 0; gy < 32; gy++) {
            int my = startY + gy;
            for (int gx = 0; gx < 32; gx++) {
                int mx = startX + gx;
                int idx = mx + (my << 6);
                int ct = world.cellType[idx] & 0xFF;
                if (ct == 1) {
                    // Color-code variable wall heights on automap
                    g.setColor(world.wallH[idx] < 100 ? 0x556644 : 0x889977);
                } else if (ct == 2) {
                    g.setColor(0xFFAA22); // Door
                } else if (ct == 4) {
                    g.setColor(0x22FFFF); // Exit Portal
                } else {
                    // Color-code step elevation (ступени) on automap!
                    int fh = world.floorH[idx];
                    if (fh > 0) {
                        int gCol = Math.min(220, 50 + fh * 3);
                        g.setColor((30 << 16) | (gCol << 8) | 40);
                    } else {
                        continue;
                    }
                }
                g.fillRect(ox + gx * cellPx, oy + gy * cellPx, cellPx, cellPx);
            }
        }

        // Draw alive monsters on automap
        g.setColor(0xFF3333);
        for (int m = 0; m < world.numMonsters; m++) {
            if (world.monState[m] == GameWorld.STATE_DEAD) continue;
            int mx = (world.monX[m] >> 8) - startX;
            int my = (world.monY[m] >> 8) - startY;
            if (mx >= 0 && mx < 32 && my >= 0 && my < 32) {
                g.fillRect(ox + mx * cellPx, oy + my * cellPx, cellPx, cellPx);
            }
        }

        // Draw player dot
        int px = pcx - startX;
        int py = pcy - startY;
        if (px >= 0 && px < 32 && py >= 0 && py < 32) {
            g.setColor(0xFFFFFF);
            g.fillRect(ox + px * cellPx, oy + py * cellPx, cellPx + 1, cellPx + 1);
        }
    }

    private void drawPauseOverlay(Graphics g) {
        int bw = scrW - 32;
        int bh = 110;
        int bx = 16;
        int by = (viewH - bh) >> 1;
        g.setColor(0x182218);
        g.fillRect(bx, by, bw, bh);
        g.setColor(0xCCDD99);
        g.drawRect(bx, by, bw, bh);

        Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
        g.setFont(font);
        int lh = font.getHeight() + 4;
        g.setColor(0xFFE855);
        g.drawString("GAME PAUSED", scrW >> 1, by + 6, Graphics.TOP | Graphics.HCENTER);

        String[] opts = {
            "RESUME MISSION",
            "3D: " + renderer3D.getShadingModeName(),
            "RESTART LEVEL",
            "MAIN MENU"
        };
        for (int i = 0; i < opts.length; i++) {
            int oy = by + 26 + i * lh;
            g.setColor(i == menuCursor ? 0xFFFF66 : 0xAABB99);
            g.drawString((i == menuCursor ? "> " : "") + opts[i], scrW >> 1, oy, Graphics.TOP | Graphics.HCENTER);
        }
    }

    private void drawControlsScreen(Graphics g) {
        drawTiledBackground(g);
        g.setColor(0x141C14);
        g.fillRect(6, 6, scrW - 12, scrH - 12);
        g.setColor(0x88A070);
        g.drawRect(6, 6, scrW - 13, scrH - 13);

        Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setFont(font);
        int lh = font.getHeight() + 1;
        String[] help = {
            "NOKIA S60 & SONY ERICSSON",
            "2 / UP    : Walk Forward",
            "8 / DOWN  : Walk Backward",
            "4 / LEFT  : Turn Left",
            "6 / RIGHT : Turn Right",
            "1 / 3     : Strafe L / R",
            "5 / FIRE  : Shoot Weapon",
            "7         : Jump Steps",
            "9         : Next Weapon",
            "# / R-SOFT: Open Door/Use",
            "*         : 3D Shading Mode",
            "0         : Automap Toggle",
            "L-SOFT    : Pause Menu",
            "Press FIRE to Return"
        };
        int y = 12;
        for (int i = 0; i < help.length; i++) {
            g.setColor(i == 0 ? 0xFFE055 : 0xD0E4C8);
            g.drawString(help[i], scrW >> 1, y, Graphics.TOP | Graphics.HCENTER);
            y += lh;
        }
    }

    private void drawVictoryScreen(Graphics g) {
        drawTiledBackground(g);
        g.setColor(0x141E14);
        g.fillRect(10, 16, scrW - 20, scrH - 32);
        g.setColor(0xFFDD55);
        g.drawRect(10, 16, scrW - 21, scrH - 33);

        Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
        g.setFont(font);
        int lh = font.getHeight() + 4;
        String[] vic = {
            "THE TIME RIFT IS SEALED!",
            "",
            "You conquered Power Station,",
            "Pyramids of Shmuher, and",
            "the Time Chasm Sanctum!",
            "",
            "FINAL SCORE: " + world.score,
            "KILLS: " + world.killsCount + "/" + world.totalMonsters,
            "",
            "PRESS FIRE FOR MENU"
        };
        int y = 28;
        for (int i = 0; i < vic.length; i++) {
            g.setColor(i == 0 ? 0xFFEE55 : 0xD0E8C8);
            g.drawString(vic[i], scrW >> 1, y, Graphics.TOP | Graphics.HCENTER);
            y += lh;
        }
    }

    protected void keyPressed(int keyCode) {
        int action = 0;
        try {
            action = getGameAction(keyCode);
        } catch (Exception e) {}

        boolean isLeftSoft = (keyCode == KEY_SOFT_LEFT1 || keyCode == KEY_SOFT_LEFT2);
        boolean isRightSoft = (keyCode == KEY_SOFT_RIGHT1 || keyCode == KEY_SOFT_RIGHT2 || keyCode == KEY_SE_BACK);

        if (screenState == SCREEN_MAIN_MENU) {
            if (keyCode == KEY_NUM2 || action == UP) {
                menuCursor = (menuCursor + 5) % 6;
            } else if (keyCode == KEY_NUM8 || action == DOWN) {
                menuCursor = (menuCursor + 1) % 6;
            } else if (keyCode == KEY_NUM4 || action == LEFT) {
                if (menuCursor == 1) selectedStartLevel = ((selectedStartLevel + 2) % 4) + 1;
                else if (menuCursor == 2) renderer3D.shadingMode = (renderer3D.shadingMode + 2) % 3;
            } else if (keyCode == KEY_NUM6 || action == RIGHT || keyCode == KEY_NUM5 || action == FIRE || isLeftSoft) {
                handleMainMenuSelect();
            }
            return;
        }

        if (screenState == SCREEN_BRIEFING) {
            if (keyCode == KEY_NUM5 || action == FIRE || isLeftSoft) {
                world.loadLevel(world.currentLevel, raycaster);
                screenState = SCREEN_PLAYING;
            }
            return;
        }

        if (screenState == SCREEN_CONTROLS || screenState == SCREEN_VICTORY) {
            if (keyCode == KEY_NUM5 || action == FIRE || isLeftSoft || isRightSoft) {
                screenState = SCREEN_MAIN_MENU;
            }
            return;
        }

        if (screenState == SCREEN_PAUSED) {
            if (keyCode == KEY_NUM2 || action == UP) {
                menuCursor = (menuCursor + 3) % 4;
            } else if (keyCode == KEY_NUM8 || action == DOWN) {
                menuCursor = (menuCursor + 1) % 4;
            } else if (keyCode == KEY_NUM5 || action == FIRE || isLeftSoft) {
                if (menuCursor == 0) {
                    screenState = SCREEN_PLAYING;
                } else if (menuCursor == 1) {
                    renderer3D.shadingMode = (renderer3D.shadingMode + 1) % 3;
                    midlet.saveSettings();
                } else if (menuCursor == 2) {
                    world.loadLevel(world.currentLevel, raycaster);
                    screenState = SCREEN_PLAYING;
                } else if (menuCursor == 3) {
                    menuCursor = 0;
                    screenState = SCREEN_MAIN_MENU;
                    sound.playMusic(0);
                }
            } else if (isRightSoft) {
                screenState = SCREEN_PLAYING;
            }
            return;
        }

        // SCREEN_PLAYING Controls
        if (isLeftSoft) {
            kUp = kDown = kLeft = kRight = kStrafeL = kStrafeR = kFire = false;
            menuCursor = 0;
            screenState = SCREEN_PAUSED;
            return;
        }
        if (isRightSoft || keyCode == KEY_POUND) {
            world.useAction(sound);
            return;
        }

        switch (keyCode) {
            case KEY_NUM2: kUp = true; break;
            case KEY_NUM8: kDown = true; break;
            case KEY_NUM4: kLeft = true; break;
            case KEY_NUM6: kRight = true; break;
            case KEY_NUM1: kStrafeL = true; break;
            case KEY_NUM3: kStrafeR = true; break;
            case KEY_NUM5:
                kFire = true;
                world.useAction(sound);
                break;
            case KEY_NUM7:
                world.triggerJump();
                break;
            case KEY_NUM9:
                world.cycleWeapon(1);
                break;
            case KEY_NUM0:
                showAutomap = !showAutomap;
                break;
            case KEY_STAR:
                renderer3D.shadingMode = (renderer3D.shadingMode + 1) % 3;
                world.showMessage("3D: " + renderer3D.getShadingModeName(), 30);
                midlet.saveSettings();
                break;
            default:
                if (action == UP) kUp = true;
                else if (action == DOWN) kDown = true;
                else if (action == LEFT) kLeft = true;
                else if (action == RIGHT) kRight = true;
                else if (action == FIRE) {
                    kFire = true;
                    world.useAction(sound);
                }
                break;
        }
    }

    protected void keyReleased(int keyCode) {
        int action = 0;
        try {
            action = getGameAction(keyCode);
        } catch (Exception e) {}

        switch (keyCode) {
            case KEY_NUM2: kUp = false; break;
            case KEY_NUM8: kDown = false; break;
            case KEY_NUM4: kLeft = false; break;
            case KEY_NUM6: kRight = false; break;
            case KEY_NUM1: kStrafeL = false; break;
            case KEY_NUM3: kStrafeR = false; break;
            case KEY_NUM5: kFire = false; break;
            default:
                if (action == UP) kUp = false;
                else if (action == DOWN) kDown = false;
                else if (action == LEFT) kLeft = false;
                else if (action == RIGHT) kRight = false;
                else if (action == FIRE) kFire = false;
                break;
        }
    }

    private void handleMainMenuSelect() {
        sound.playSfx(SoundManager.SFX_SWITCH);
        switch (menuCursor) {
            case 0:
                world.initNewCampaign();
                world.currentLevel = selectedStartLevel;
                screenState = SCREEN_BRIEFING;
                sound.playMusic(world.currentLevel);
                break;
            case 1:
                selectedStartLevel = (selectedStartLevel % 4) + 1;
                midlet.saveSettings();
                break;
            case 2:
                renderer3D.shadingMode = (renderer3D.shadingMode + 1) % 3;
                midlet.saveSettings();
                break;
            case 3:
                sound.musicEnabled = !sound.musicEnabled;
                sound.sfxEnabled = sound.musicEnabled;
                if (sound.musicEnabled) sound.playMusic(0);
                else sound.stopMusic();
                midlet.saveSettings();
                break;
            case 4:
                screenState = SCREEN_CONTROLS;
                break;
            case 5:
                midlet.exitGame();
                break;
        }
    }
}
