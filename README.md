# Chasm: The Rift — J2ME Edition (Nokia S60 & Sony Ericsson)

A Java ME (`CLDC 1.1` / `MIDP 2.0`) port of the 1997 DOS 3D first-person shooter **Chasm: The Rift** (Action Forms), engineered specifically for **Nokia S60** and **Sony Ericsson** keypad feature phones.

## Key Engine Architecture

1. **C3D2/C3B BSP-Portal World Renderer (`PortalRenderer`)**:
   - Adapted from `CovertOps3D-RE`'s BSP-portal engine (`PortalRenderer`, `CustomLevelLoader`, `CustomMaterialSet`, `BSPNode`, `Sector`, `WallDefinition`, `RenderUtils`).
  - Compiles the playable Chasm levels from `CSM.BIN` (`MAP.01` → level 1 "Power Station", `MAP.05` → level 2 "Around Pyramids", `MAP.06` → level 3 "Pyramid of Shmuher") into T-junction-free `C3B1` binary BSP-portal packages (`/res/levels/level1.c3b` .. `/res/levels/level3.c3b`) with `0` BSP partition mismatches. `MAP.99` is the briefing background and is not built.
   - Textures follow each map's own IDs: a wall tile ID is looked up in the map's `#GFX` table in `RESOURCE.XX` (e.g. `19` → `sewer01.cel`) and the matching PNG from `tex.zip` is used, or the original `.CEL` from `CSM.BIN` when no PNG exists. Floor and ceiling values index the 64 tiles of `FLOORS.XX`. Each level has its own `/res/textures/custom/L<n>/materials.c3m`; the BMP4 textures themselves are shared under `/res/textures/custom/shared/`. Textures are 16-colour 4-bit `BMP4`; tall 64×128 walls are squashed to 64×64.

2. **Software 3D Polygon Renderer Without a Z-Buffer (`PolyRenderer3D`)**:
   - Zero Z-buffer allocation or per-pixel depth buffer reads/writes.
   - Integrates directly into `PortalRenderer`'s back-to-front visible sector loop, sorting 3D entities per sector by view depth and sorting front-facing triangles per model back-to-front (Painter's Algorithm).
   - Clips every rasterized 3D polygon scanline against the active sector's exact portal column occlusion window (`[Sector.floorClip[x], Sector.ceilingClip[x]]`).
   - Supports four shading modes: Vertex Colors / Vertex Painting, Flat Shading, Flat + Vertex Colors, and affine Texture Mapping from the original model atlases (see *Polygonal 3D render modes* below).

3. **Authentic `.CAR` Multi-Part Dismemberment System (`Model3D` & `GameWorld`)**:
   - Preserves all 8 original `.CAR` body-part polygon bitmask groups (`1=LeftHand`, `2=LeftHandSeparated`, `4=RightHand`, `8=RightHandSeparated`, `16=Head`, `32=HeadSeparated`, `64=Body`, `128=WeaponFire`) and all 3 embedded detached body-part submodels (`submodels[0..2]`: severed Right Arm + Weapon, severed Left Arm, and severed Head).
   - Damaging enemies progressively severs their Right Arm + Weapon (disabling ranged attacks and exposing the bloody right shoulder stump), Left Arm (disabling all attacks and exposing the left shoulder stump), and Head (decapitation), while spawning the corresponding 3D detached limb submodels that fly through the air and come to rest on the sector floor.

4. **General MIDI Music & Chasm WAV Sound Effects (`SoundManager`)**:
   - General MIDI tracks (`/res/mus_menu.mid`, `/res/mus_lvl1..4.mid`) and 8 kHz mono PCM WAV sound effects (`/res/sfx_*.wav`, 14 files) built from the original Chasm sound archive.

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
| 1 / 3 | Strafe left / right |
| 5 | Fire |
| 7 | Use: open / interact; next level once the sector is clear |
| 9 | Level map (automap) toggle |
| * / # | Previous / next weapon |
| 0 | Jump |
| Left soft key | Pause menu |

The main menu's **CONTROLS & HELP** entry shows the same table on the device.

## HUD and developer mode

- **Bottom strip:** HP and armour (icons from the original `NSTAT.CEL` art), the active weapon with its ammo (shell icon from `WICONS.CEL`), enemies still alive on the level, and the level number.
- **MUSIC** and **SOUND FX** (pause menu, items 4 and 5): switch background music and sound effects on or off separately. Choices are saved. Turning SOUND FX off also skips the sound-effect work on each shot, which helps on slower phones.
- **DEV MODE** (pause menu, third item): toggles god mode and a debug overlay across the top of the 3D view with the player's X/Z position, facing angle, FPS, dismembered-limb count (`LIMBS`) and the shading tag. FPS and LIMBS are shown only in this mode. The setting persists across levels.
- The icons are cropped at build time by `tools/build_assets.py` into `res/hud_icons.bin`.

## Polygonal 3D render modes

Monsters, detached limbs, weapons and props use `PolyRenderer3D` (no Z-buffer, painter's order). Cycle the mode from the main menu or pause menu:

| Mode | HUD tag | Description |
|------|---------|-------------|
| VERTEX COLOR | VC | Gouraud-interpolated per-vertex colours (vertex painting) |
| FLAT | FL | One flat colour per polygon with directional shading |
| FLAT+VERTEX | FV | One colour per polygon: the average of its vertex colours |
| TEXTURE | TX | Affine texture mapping from the model's palette atlas (texel 0/255 uses the flat colour) |

A hit flashes the enemy with an additive red glow for a few ticks instead of overwriting its colours.

## Performance notes

- Sound effects are played by a background audio worker with cached, pre-realized players. The frame loop never calls `Manager.createPlayer()` or `realize()`, which removed the shot micro-stalls on S60 / Sony Ericsson phones.
- Projectile weapons (Crossbow, Saw-Disk, Megazapper) damage only on impact (no duplicate hitscan).

## Prop collision

Static 3D props (explosive barrels, pharaoh statues, obelisks) block movement through simplified axis-aligned boxes. Each box's half-extents are the largest horizontal vertex offsets of the model, scaled with the renderer's model-to-world factor, and are stored in the entity sidecar (`>BhhBhh` per prop). The player is pushed out along the axis of least penetration, and monsters do not step into a box. Projectiles and line of sight still ignore props.

## Known limitations

- **Campaign coverage is incomplete.** The `CSM.BIN` used by this build contains only the map bundles `MAP.01`, `MAP.05`, `MAP.06` and `MAP.99` (`MAP.99` is the briefing background, not a playable mission). The game has three playable levels here. Episode 1 "The CondemNATION" needs `MAP.01`–`MAP.04` (Power Station, Military Base, Underground Base, Pit of Sarcophagus), and `MAP.02`–`MAP.04` are absent. Levels 2–3 are therefore not the Episode 1 missions; they are the data that exists. Level names come from each map's `RESOURCE` (`#name`). The briefing texts are short neutral descriptions, not the original scripts.

- Doors are not compiled from the MAP data yet, so `7` only advances the level after the sector is cleared. The original door, lift, switch and key logic lives in `PROCESS.XX` scripts; `tools/parse_process.py` parses them (procs, messages, `move`/`rotate`/`up`/`xmove`/`ymove`/`wind`/`death` actions, key and switch links), but the runtime does not execute them yet.
- Some portal walls still show angle-dependent distortion; the root cause has not been isolated.
- Transparent grate textures are drawn as opaque (no masked mid-texture pass yet).
- The first build's staircase floor steps are only partially restored.
