package chasm;

import java.lang.reflect.Field;
import java.util.Arrays;

/** Check fresh bucket membership, stable slot ordering and reference rendering. */
public final class EntityBucketsTest {
    private static int[] next(PolyRenderer3D renderer, String name) throws Exception {
        Field field = PolyRenderer3D.class.getDeclaredField(name);
        field.setAccessible(true);
        return (int[])field.get(renderer);
    }
    private static void checkList(int head, int[] next, Sector target,
                                  Sector[] sectors, boolean[] active, int count) {
        int cursor = head;
        for (int i = 0; i < count; i++) {
            if (active[i] && sectors[i] == target) {
                if (cursor != i) throw new AssertionError("bucket order/membership: expected " + i + " got " + cursor);
                cursor = next[cursor];
            }
        }
        if (cursor != -1) throw new AssertionError("bucket contains extra entries");
    }
    private static void verify(GameWorld w) throws Exception {
        w.polyRenderer.prepareSectorBuckets(w);
        boolean[] mons = new boolean[GameWorld.MAX_MONSTERS];
        for (int i = 0; i < w.monCount; i++) mons[i] = w.monState[i] != GameWorld.MSTATE_INACTIVE;
        int[] nm = next(w.polyRenderer, "nextMon"), nl = next(w.polyRenderer, "nextLimb");
        int[] ni = next(w.polyRenderer, "nextItem"), np = next(w.polyRenderer, "nextProp");
        int[] nj = next(w.polyRenderer, "nextProj");
        for (int i = 0; i < w.bspSectors.length; i++) {
            Sector s = w.bspSectors[i];
            checkList(s.renderMonHead, nm, s, w.monSector, mons, w.monCount);
            checkList(s.renderLimbHead, nl, s, w.limbSector, w.limbActive, GameWorld.MAX_LIMBS);
            checkList(s.renderItemHead, ni, s, w.itemSector, w.itemActive, w.itemCount);
            checkList(s.renderPropHead, np, s, w.propSector, w.propActive, w.propCount);
            checkList(s.renderProjHead, nj, s, w.projSector, w.projActive, GameWorld.MAX_PROJ);
        }
    }
    private static int[] render(GameWorld w, boolean buckets) {
        w.polyRenderer.useSectorBuckets = buckets;
        Arrays.fill(PortalRenderer.screenBuffer, 0x12345678);
        w.render3DView(PortalRenderer.screenBuffer);
        return (int[])PortalRenderer.screenBuffer.clone();
    }
    public static void main(String[] args) throws Exception {
        GameWorld w = new GameWorld();
        for (int level = 0; level < GameWorld.NUM_LEVELS; level++) {
            w.loadLevel(level);
            verify(w);
            // Move, activate, deactivate and clear sector pointers across frames.
            for (int pass = 0; pass < 3; pass++) {
                for (int i = 0; i < w.monCount; i++) {
                    w.monSector[i] = i % 7 == 0 ? null : w.bspSectors[(i + pass) % w.bspSectors.length];
                    w.monState[i] = (i + pass) % 3 == 0 ? GameWorld.MSTATE_INACTIVE : GameWorld.MSTATE_IDLE;
                }
                for (int i = 0; i < w.itemCount; i++) {
                    w.itemSector[i] = i % 7 == 0 ? null : w.bspSectors[(i + pass) % w.bspSectors.length];
                    w.itemActive[i] = (i + pass) % 3 != 0;
                }
                for (int i = 0; i < w.propCount; i++) {
                    w.propSector[i] = i % 7 == 0 ? null : w.bspSectors[(i + pass) % w.bspSectors.length];
                    w.propActive[i] = (i + pass) % 3 != 0;
                }
                for (int i = 0; i < GameWorld.MAX_LIMBS; i++) {
                    w.limbSector[i] = i % 7 == 0 ? null : w.bspSectors[(i + pass) % w.bspSectors.length];
                    w.limbActive[i] = (i + pass) % 3 != 0;
                }
                for (int i = 0; i < GameWorld.MAX_PROJ; i++) {
                    w.projSector[i] = i % 7 == 0 ? null : w.bspSectors[(i + pass) % w.bspSectors.length];
                    w.projActive[i] = (i + pass) % 3 != 0;
                }
                verify(w);
            }
            w.loadLevel(level);
            // All categories together at one depth exercises stable tie ordering
            // and the 96-entry per-sector cap with the actual rasterizer.
            int x = w.playerBody.x, z = w.playerBody.z + (80 << 16);
            Sector dest = w.getSectorAtPoint(x, z);
            w.monCount = GameWorld.MAX_MONSTERS;
            w.itemCount = GameWorld.MAX_ITEMS;
            w.propCount = GameWorld.MAX_PROPS;
            for (int i = 0; i < w.monCount; i++) {
                w.monSector[i] = dest; w.monState[i] = GameWorld.MSTATE_IDLE;
                w.monX[i] = x; w.monZ[i] = z; w.monType[i] = i % GameWorld.MONSTER_NAMES.length;
                w.monFrameA[i] = w.monFrameB[i] = w.monInterp[i] = 0;
                w.monPartMask[i] = Model3D.MASK_INTACT_DEFAULT;
            }
            for (int i = 0; i < w.itemCount; i++) {
                w.itemSector[i] = dest; w.itemActive[i] = true;
                w.itemX[i] = x; w.itemZ[i] = z; w.itemModelId[i] = GameWorld.ITEM_MEDKIT;
            }
            for (int i = 0; i < w.propCount; i++) {
                w.propSector[i] = dest; w.propActive[i] = true;
                w.propX[i] = x; w.propZ[i] = z; w.propModelId[i] = 30;
            }
            for (int i = 0; i < GameWorld.MAX_LIMBS; i++) {
                w.limbSector[i] = dest; w.limbActive[i] = true;
                w.limbX[i] = x; w.limbZ[i] = z;
                w.limbMonsterType[i] = 0; w.limbSubmodelIdx[i] = 0;
                w.limbY[i] = w.limbFloorY[i] = 0;
            }
            for (int i = 0; i < GameWorld.MAX_PROJ; i++) {
                w.projSector[i] = dest; w.projActive[i] = true;
                w.projX[i] = x; w.projZ[i] = z; w.projY[i] = 0; w.projModelId[i] = 34;
            }
            verify(w);
            w.playerBody.rotation = 0;
            for (int shade = 0; shade < 4; shade++) {
                w.polyRenderer.shadingMode = shade;
                PortalRenderer.profiling = true;
                int[] before = render(w, false);
                int models = PortalRenderer.profileModels;
                int[] after = render(w, true);
                if (models < 2 || models != PortalRenderer.profileModels || !Arrays.equals(before, after))
                    throw new AssertionError("dense mixed-entity mismatch, level=" + level + " shade=" + shade);
            }
        }
        PortalRenderer.profiling = false;
        System.out.println("Sector bucket membership, mutation, ties and 96-entry cap OK");
    }
}
