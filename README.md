# Chasm: The Rift — J2ME Edition (Nokia S60 & Sony Ericsson)

A Java ME (`CLDC 1.1` / `MIDP 2.0`) port of the 1997 DOS 3D first-person shooter **Chasm: The Rift** (Action Forms), engineered specifically for **Nokia S60** and **Sony Ericsson** keypad feature phones.

## Key Engine Architecture

1. **C3D2/C3B BSP-Portal World Renderer (`PortalRenderer`)**:
   - Adapted from `CovertOps3D-RE`'s BSP-portal engine (`PortalRenderer`, `CustomLevelLoader`, `CustomMaterialSet`, `BSPNode`, `Sector`, `WallDefinition`, `RenderUtils`).
   - Compiles original Chasm levels (`MAP.01`, `MAP.05`, `MAP.06`, and Rift Sanctum) into T-junction-free `C3B1` binary BSP-portal packages (`/res/levels/level1.c3b` .. `/res/levels/level4.c3b`) with `0` BSP partition mismatches and 16-color 4-bit `BMP4` paletted wall, floor/ceiling flat, and skybox textures (`/res/textures/custom/materials.c3m`).

2. **Software 3D Polygon Renderer Without a Z-Buffer (`PolyRenderer3D`)**:
   - Zero Z-buffer allocation or per-pixel depth buffer reads/writes.
   - Integrates directly into `PortalRenderer`'s back-to-front visible sector loop, sorting 3D entities per sector by view depth and sorting front-facing triangles per model back-to-front (Painter's Algorithm).
   - Clips every rasterized 3D polygon scanline against the active sector's exact portal column occlusion window (`[Sector.floorClip[x], Sector.ceilingClip[x]]`).
   - Supports both **Vertex Colors / Vertex Painting** (Gouraud-interpolated per-vertex RGB sampled from the original `.CAR`/`.3O` model textures and `CHASM2.PAL`) and **Flat Shading** (single-color polygon shading with directional normal lighting), toggleable anytime via `*` or the Pause menu.

3. **Authentic `.CAR` Multi-Part Dismemberment System (`Model3D` & `GameWorld`)**:
   - Preserves all 8 original `.CAR` body-part polygon bitmask groups (`1=LeftHand`, `2=LeftHandSeparated`, `4=RightHand`, `8=RightHandSeparated`, `16=Head`, `32=HeadSeparated`, `64=Body`, `128=WeaponFire`) and all 3 embedded detached body-part submodels (`submodels[0..2]`: severed Right Arm + Weapon, severed Left Arm, and severed Head).
   - Damaging enemies progressively severs their Right Arm + Weapon (disabling ranged attacks and exposing the bloody right shoulder stump), Left Arm (disabling all attacks and exposing the left shoulder stump), and Head (decapitation), while spawning the corresponding 3D detached limb submodels that fly through the air and come to rest on the sector floor.

4. **General MIDI Music & Chasm WAV Sound Effects (`SoundManager`)**:
   - Multi-channel General MIDI tracks (`/res/m0.mid` .. `/res/m3.mid`) and 8 kHz mono PCM WAV sound effects (`/res/s0.wav` .. `/res/s9.wav`) extracted from the original Chasm sound archive.

## Release Artifacts

- `/releases/ChasmTheRift.jar` — Preverified J2ME MIDlet suite (`CLDC-1.1` / `MIDP-2.0`)
- `/releases/ChasmTheRift.jad` — JAD descriptor

## Building from Source

```bash
./build.sh
```


## Controls (keypad)

| Key | Action |
|-----|--------|
| 2 / 8 | Forward / backward |
| 4 / 6 | Turn left / right |
| 7 / 9 | Strafe left / right |
| 5 | Fire |
| 1 / 3 | Previous / next weapon |
| 0 | Jump |
| * | Use; clears the level and advances once all enemies are down |
| # | Automap toggle |
| Left soft key | Pause menu |

## Known limitations

- Doors are not in the portal data yet, so `*` only advances the level after the sector is cleared.
- The pain-flash tint is applied to the whole enemy body, so a hit briefly reads as solid red.
- Level layouts are compiled from the MAP data without the engineered staircases from the first build.
- The main menu uses the 3D preview and text only; the first build's tiled background and logo are not restored.
