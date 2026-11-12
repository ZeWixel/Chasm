package chasm;

import java.util.Arrays;

/** Regression: compare PVS on/off and exercise both buffer configurations. */
public final class RenderProfileTest {
    private static int[] render(GameWorld w) {
        Arrays.fill(PortalRenderer.screenBuffer, 0x12345678);
        w.render3DView(PortalRenderer.screenBuffer);
        return (int[]) PortalRenderer.screenBuffer.clone();
    }
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        GameWorld w = new GameWorld();
        int comparisons = 0, rejected = 0, portalComparisons = 0;
        // Check the safety margin used by tools/pvs.py against every shipped
        // animated mesh, including limbs at their larger runtime scale.
        Model3D[] allModels = Model3D.loadAllModels("/models.bin");
        require(allModels.length > 0, "no models loaded");
        for (int id = 0; id < allModels.length; id++) {
            Model3D main = allModels[id];
            if (main == null) continue;
            for (int sub = -1; sub < 3; sub++) {
                Model3D model = sub < 0 ? main : (main.submodels != null && sub < main.submodels.length ? main.submodels[sub] : null);
                if (model == null || model.frameVerts == null) continue;
                for (int f = 0; f < model.frameVerts.length; f++) {
                    short[] verts = model.frameVerts[f];
                    if (verts == null) continue;
                    for (int v = 0; v < verts.length / 3; v++) {
                        long mx = verts[v * 3], my = verts[v * 3 + 1];
                        long scale = sub < 0 ? 1792 : 2688;
                        long limit = 192L * 65536L;
                        require((mx * mx + my * my) * scale * scale < limit * limit, "PVS model margin");
                    }
                }
            }
        }
        for (int level = 0; level < GameWorld.NUM_LEVELS; level++) {
            w.loadLevel(level);
            boolean[][] saved = new boolean[w.sectors.length][];
            for (int i = 0; i < saved.length; i++) {
                saved[i] = (boolean[]) w.sectors[i].visitedFlags.clone();
                for (int j = 0; j < saved.length; j++) if (saved[i][j]) rejected++;
            }
            // Each leaf's vertex average gives a repeatable camera point. Test
            // several angles with models and world geometry both enabled.
            for (int leaf = 0; leaf < w.bspSectors.length; leaf++) {
                Sector sec = w.bspSectors[leaf];
                if (sec.walls.length == 0) continue;
                long x = 0, z = 0;
                for (int k = 0; k < sec.walls.length; k++) {
                    Point2D p = w.vertices[sec.walls[k].startVertexIndex & 65535];
                    x += p.x; z += p.y;
                }
                int cx = (int)(x / sec.walls.length), cz = (int)(z / sec.walls.length);
                for (int shadePass = 0; shadePass < 2; shadePass++) {
                w.polyRenderer.shadingMode = shadePass == 0 ? PolyRenderer3D.SHADE_VERTEX_PAINT : PolyRenderer3D.SHADE_TEXTURE;
                for (int point = 0; point < 3; point++) {
                  Point2D corner = w.vertices[sec.walls[point % sec.walls.length].startVertexIndex & 65535];
                  w.playerBody.x = point == 0 ? cx : (int)(((long)cx * 3 + corner.x) / 4);
                  w.playerBody.z = point == 0 ? cz : (int)(((long)cz * 3 + corner.y) / 4);
                for (int angle = 0; angle < 8; angle++) {
                    w.playerBody.rotation = angle * (MathUtils.QUARTER_CIRCLE / 2);
                    for (int i = 0; i < saved.length; i++)
                        System.arraycopy(saved[i], 0, w.sectors[i].visitedFlags, 0, saved.length);
                    w.getRootBSPNode().calculateVisibleSectors();
                    int[] with = render(w);
                    if (leaf % 3 == 0 && point == 0 && angle % 2 == 0) {
                        PortalRenderer.skipEmptyPortalTextureMath = false;
                        int[] originalPortalMath = render(w);
                        PortalRenderer.skipEmptyPortalTextureMath = true;
                        require(Arrays.equals(with, originalPortalMath), "portal math mismatch: level " + level + " leaf " + leaf + " angle " + angle);
                        portalComparisons++;
                        w.polyRenderer.useSectorBuckets = false;
                        int[] unbucketed = render(w);
                        w.polyRenderer.useSectorBuckets = true;
                        require(Arrays.equals(with, unbucketed), "bucket image mismatch: level " + level + " leaf " + leaf + " angle " + angle);
                    }
                    for (int i = 0; i < saved.length; i++) Arrays.fill(w.sectors[i].visitedFlags, false);
                    w.getRootBSPNode().calculateVisibleSectors();
                    int[] without = render(w);
                    if (!Arrays.equals(with, without))
                        throw new AssertionError("PVS image mismatch: level " + level + " leaf " + leaf + " angle " + angle);
                    comparisons++;
                }
                }
                }
            }
        }
        require(rejected > 0, "PVS rejected nothing");
        require(portalComparisons > 0, "portal reference was not tested");
        // Exercise the portal optimisation with both resolutions, all shade
        // modes, flat floors and muzzle flash in each shipped level.
        for (int level = 0; level < GameWorld.NUM_LEVELS; level++) {
            w.loadLevel(level);
            for (int fast = 0; fast < 2; fast++) {
                PortalRenderer.setFastResolution(fast != 0);
                for (int flat = 0; flat < 2; flat++) {
                    PortalRenderer.flatSurfaces = flat != 0;
                    for (int shade = 0; shade < 4; shade++) {
                        w.polyRenderer.shadingMode = shade;
                        for (int flash = 0; flash < 2; flash++) {
                            w.muzzleFlashTimer = flash;
                            PortalRenderer.skipEmptyPortalTextureMath = true;
                            int[] optimized = render(w);
                            PortalRenderer.skipEmptyPortalTextureMath = false;
                            int[] reference = render(w);
                            require(Arrays.equals(optimized, reference), "portal mode mismatch");
                            portalComparisons++;
                        }
                    }
                }
            }
        }
        w.muzzleFlashTimer = 0;
        PortalRenderer.skipEmptyPortalTextureMath = true;
        for (int pass = 0; pass < 4; pass++) {
            boolean fast = (pass & 1) == 0;
            PortalRenderer.setFastResolution(fast);
            require(PortalRenderer.VIEWPORT_WIDTH == (fast ? 120 : 240), "viewport width");
            require(PortalRenderer.screenBuffer.length == PortalRenderer.SCREEN_BUFFER_SIZE, "buffer size");
            for (int flat = 0; flat < 2; flat++) {
                PortalRenderer.flatSurfaces = flat != 0;
                for (int shade = 0; shade < 4; shade++) {
                    w.polyRenderer.shadingMode = shade;
                    PortalRenderer.profiling = false;
                    int[] baseline = render(w);
                    PortalRenderer.profiling = true;
                    int[] frame = render(w);
                    require(Arrays.equals(baseline, frame), "profiler changed rendered pixels");
                    require(PortalRenderer.profileVertexMs >= 0 && PortalRenderer.profileGeometryMs >= 0
                            && PortalRenderer.profileEntitiesMs >= 0 && PortalRenderer.profileScanMs >= 0,
                            "negative profiler duration");
                    require(PortalRenderer.profileLeaves > 0 && PortalRenderer.profileWalls > 0
                            && PortalRenderer.profileModels > 0, "profiling counters");
                    boolean drawn = false;
                    for (int i = 0; i < frame.length; i++) if (frame[i] != 0x12345678) { drawn = true; break; }
                    require(drawn, "nothing rendered");
                }
            }
        }
        // Across four rotating sample phases, at least one leaf must be sampled.
        int sampled = 0;
        PortalRenderer.profiling = true;
        for (int i = 0; i < 4; i++) {
            render(w);
            sampled += PortalRenderer.profileScanSamples;
        }
        require(sampled > 0, "no entity scan samples");
        PortalRenderer.flatSurfaces = false;
        PortalRenderer.profiling = false;
        System.out.println("PVS render comparisons=" + comparisons + " rejected=" + rejected
                + "; portal reference comparisons=" + portalComparisons + "; resolution/flat/shading OK");
    }
}
