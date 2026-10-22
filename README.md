# Chasm: The Rift — J2ME Edition (Nokia S60 & Sony Ericsson)

A Java ME (`CLDC 1.1` / `MIDP 2.0`) port of the 1997 DOS 3D first-person shooter **Chasm: The Rift** (Action Forms), engineered specifically for **Nokia S60** and **Sony Ericsson** keypad feature phones.

## Key Engine Architecture

1. **C3D2/C3B BSP-Portal World Renderer (`PortalRenderer`)**:
   - Adapted from `CovertOps3D-RE`'s BSP-portal engine (`PortalRenderer`, `CustomLevelLoader`, `CustomMaterialSet`, `BSPNode`, `Sector`, `WallDefinition`, `RenderUtils`).
  - Compiles the playable levels into T-junction-free `C3B1` binary BSP-portal packages (`/res/levels/level1.c3b` .. `/res/levels/level4.c3b`) with `0` BSP partition mismatches. Levels 1–3 come from `CSM.BIN` (`MAP.01` → "Power Station", `MAP.05` → "Around Pyramids", `MAP.06` → "Pyramid of Shmuher"). `MAP.99` is the briefing background and is not built.
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
| 7 | Open the nearest door or switch within one cell (fallback if the automatic opening fails; locks still apply) |
| 9 | Level map (automap) toggle; the player is a triangle pointing where you face |
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

Static 3D props (explosive barrels, pharaoh statues, obelisks) block movement through simplified axis-aligned boxes. Each box's half-extents are the largest horizontal vertex offsets of the model, scaled with the renderer's model-to-world factor, and are stored in the entity sidecar (`>BhhBhh` per prop). The player is pushed out along the axis of least penetration. Monsters are pushed out the same way when they spawn or update inside a box, so a spawn on top of a barrel is recoverable. A chasing monster that is blocked by a box detours around it on one side for a fixed number of ticks, which avoids oscillating at the corner. Line of sight still ignores props. Player projectiles and hitscan shots now hit explosive barrels (see below).

## Explosive barrels

Barrels are prop model 30 (`BAREL1.3O`), one per `BAREL` object on the map. They are shot, not walked around: hitscan weapons take the nearest target (barrel or monster) in the crosshair, and player projectiles (bolts, saw-discs) stop on the first barrel they reach.

- **Hit points:** 40. Two Rift Rifle hits (24 each), three Volcano Chaingun hits (18 each), or one Double Shotgun blast (52).
- **Blast:** when a barrel reaches 0 HP it explodes. Monsters and the player within 192 world units (two 96-unit cells) take `round(90 * (1 - d / 192))`, so 90 at the centre and 0 at the edge. Damage to the player goes through the normal armour rule. Blast damage has no line-of-sight check.
- **Chain:** every other barrel inside the blast takes the same damage. A barrel that reaches 0 HP explodes too, through a queue, so a chain is iterative and each barrel explodes once.
- **Feedback:** a blast plays the rocket sound and throws four gib particles. Each detonation draws a fireball at the barrel's position: it grows for six ticks, then shrinks and fades over ten ticks. If the player is inside the blast radius, the whole screen flashes warm for four ticks. The fireball is projected with the frame's camera and is not hidden by walls. A destroyed barrel stops colliding, is not drawn, and cannot be targeted.

The HP and blast values above are the values chosen for this port. They were not read from the original data. The blast uses the damage formula of the Chasm-Reverse engine (`DoExplosionDamage`), but its constants are not extracted from `CSM.BIN`. `GameWorld.BARREL_HP`, `BARREL_BLAST_DAMAGE` and `BARREL_BLAST_RADIUS` hold them.

## Doors, gates, switches and keys

There is no use key. Doors, gates, switches and the exit work on touch, as in the original game. `tools/build_assets.py` compiles each map's `PROCESS.XX` (parsed by `tools/parse_process.py`, including the `#links` section) into `res/levels/procs{N}.bin`.

- **Triggers** (from `#links` and `LinkSwitchAt`): `link` fires when the player comes within one cell (3x3 block), `floor` when the player steps on the cell, `onofflink` the same as `link` but a second touch closes the proc, and `return` resets the proc to its start state.
- **Floor links** fire when the player's circle touches the trigger cell (radius 24 units), as in the original, so a door whose floor link sits in its own cell opens when the player bumps it from outside.
- **Destroy links** (`destroy X Y N`) are fired by shooting the gate lock (`LOCK01.3O`, prop model 38) on cell X,Y: hitscan and projectiles both damage it (60 HP). When it breaks, proc N unlocks and opens, ignoring keys, as in `ProcedureProcessDestroy`.
- **Manual use (key 7):** opens the nearest door or switch within one cell, with the usual lock checks. Far from any door it shows `NO DOOR NEAR`. This is a fallback for the automatic opening, and it goes against the no-use-key rule above only for this key.
- **Activation** follows the original engine (`Chasm-Reverse`, `ProcedureProcess*`). A proc runs only if it is unlocked, or if the key it needs (green, red or blue) is held. `Lock` is removed by an `unlock N` action from another proc. A refused touch is retried every tick while the player stays in range, so a key picked up nearby opens the door.
- **Doors** are sectors whose ceiling is level with the floor when closed (blocks the player) and rises to the open height. `StartDelay` and `BackWait` are in seconds (converted at 28 ticks per second). `BackWait 0` means the door never returns. A door never closes on the player: it waits until the player leaves its cells.
- **Gates** (`up X Y H`) raise a transparent wall by H, so the cell opens to floor + H.
- **Keys** are items 27 (red), 28 (green) and 29 (blue). The HUD shows the green and red keys in the top-right corner of the 3D view. The blue key is collected but has no HUD slot yet.
- **Exit**: when every enemy is down, walking to the level's exit point completes the level. Nothing else needs to be pressed.

- **Moving platforms** (`move`, `xmove`, `ymove` on a cell that holds a 3O model, for example `MAP.06` procs 2, 3 and 16). Their model is not a static prop: it is a prop box that the proc moves (see below).

Not implemented: `shoot` links, `rotate`, `light`, `playani`, `waitout`, `death`, and vertical lifts. Four rotating doors/walls therefore never move: `MAP.01` proc 3 (house with window) and proc 18 (restricted area #1), and `MAP.06` proc 18 and proc 26. Rotating walls need a sector-geometry change, which the C3B build does not provide yet. In the `CSM.BIN` data, the `up` action only raises fence and frame walls; no lift platform uses it. These are parsed and ignored.

### Moving platforms

- **Build:** `tools/build_assets.py` finds each `move`/`xmove`/`ymove` whose cell holds a model in `MOVER_MODEL_IDS` (`M-STAR.3O`, `V2.3O`, `FARAON.3O`). Each one becomes a mover record in `res/levels/procs{N}.bin` (proc id, model id, base position, total travel dX/dZ in world units, collision half-extents, angle). The move amounts are the proc's own arguments, scaled by 96/256 (the cell is 256 map units). `M-STAR.3O` and `V2.3O` are added to `models.bin` as models 36 and 37. The model's position comes from its object record and is not snapped to the cell.
- **Runtime:** a mover is a prop slot whose box follows its proc's stage each tick. The proc uses the original's time scale (stage = seconds × speed / 10, so a full move takes 10 / speed seconds). A mover proc starts on the same touch triggers as doors.
- **Blocking and pushing:** the box blocks the player and monsters like a static prop. When the box moves onto the player or a live monster, that entity is shoved to the box's leading face. The shove is skipped if the target point has no sector, and the box pushout then acts instead.
- **Deviation:** nothing rides the platform. The original's `GetFloorLevel` ignores dynamic models, so the player is not carried by a platform; the platform only blocks and pushes.

Each level compiles its whole walkable area, so levels 1–7 have 156, 92, 254, 245, 357, 417 and 39 sectors.

## Levels

The `CSM.BIN` used by this build has four maps: `MAP.01`, `MAP.05`, `MAP.06` and `MAP.99`. `MAP.99` is the briefing background, so it is not a level. The playable levels are:

| Level | Map | Name (from `RESOURCE`) | Keys |
|---|---|---|---|
| 1 | MAP.01 | Power Station | green (`G_CARD.3O`) |
| 2 | MAP.05 | Around Pyramids | red (`KEY.3O`) |
| 3 | MAP.06 | Pyramid of Shmuher | red (`KEY.3O`) |

| 4 | first build `map3` | Pyramid of Shmuher (first build layout, hand-made steps and walls, **not game data**) | none |

Level 4 is the first build's "Pyramid of Shmuher" (`b8d28e9`, PR #1; grid map 3), restored from `tools/legacy/map3.bin` and compiled by `build_legacy_levels` with the textures of `MAP.06`. It is the level that was 6th in the first build. The first build's other levels (grid copies of MAP.01/05/06 and the Time Chasm Sanctum, `map4`) stay removed. The grid-to-C3B converter (`parse_legacy_map`, `compile_legacy_level`) is in `tools/build_assets.py`.

Keys are pickups (models 27 red, 28 green, 29 blue). The converter places them first and never cuts them from the item list, so a level keeps its keys even when it has more than 48 pickups.

## Known limitations

- **Campaign coverage is incomplete.** The `CSM.BIN` used by this build contains only the map bundles `MAP.01`, `MAP.05`, `MAP.06` and `MAP.99` (`MAP.99` is the briefing background, not a playable mission). The game has three playable levels from the data, plus level 4 (the first build's Pyramid of Shmuher, not game data). Episode 1 "The CondemNATION" needs `MAP.01`–`MAP.04` (Power Station, Military Base, Underground Base, Pit of Sarcophagus), and `MAP.02`–`MAP.04` are absent. Levels 2–3 are therefore not the Episode 1 missions; they are the data that exists. Level names come from each map's `RESOURCE` (`#name`). The briefing texts are short neutral descriptions, not the original scripts.

- Only links (proximity, floor, toggle, return, destroy on the gate lock), `unlock` chains, doors, gates (`up`) and moving platforms are executed from `PROCESS.XX`. `shoot` links (walls), rotating parts (see Doors), lights and animations are not.
- Door textures are not verified. In a render of L1 `MAP.01` proc 8 (helipad door) the closed door shows as a black column where the door is, and the open door shows the stone texture. The cause is not fixed yet. Door wall textures come from the nearest wall object (`cell_wall_tex`, 3×3 stamp), so a door can take a neighbour's texture.
- The automap (key 9) has not been checked on a device.
- The full-area geometry has not been measured on a device. Frame rate and memory with 150–230 sectors are unknown.
- Some portal walls still show angle-dependent distortion; the root cause has not been isolated.
- Transparent grate textures are drawn as opaque (no masked mid-texture pass yet).
- The first build's staircase floor steps are only partially restored.
