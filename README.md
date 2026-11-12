# Chasm: The Rift — J2ME Edition (Nokia S60 & Sony Ericsson)

A Java ME (`CLDC 1.1` / `MIDP 2.0`) port of the 1997 DOS 3D first-person shooter **Chasm: The Rift** (Action Forms), engineered specifically for **Nokia S60** and **Sony Ericsson** keypad feature phones.

## Key Engine Architecture

1. **C3D2/C3B BSP-Portal World Renderer (`PortalRenderer`)**:
   - Adapted from `CovertOps3D-RE`'s BSP-portal engine (`PortalRenderer`, `CustomLevelLoader`, `CustomMaterialSet`, `BSPNode`, `Sector`, `WallDefinition`, `RenderUtils`).
  - Compiles the playable levels into T-junction-free `C3B1` binary BSP-portal packages (`/res/levels/level1.c3b` .. `/res/levels/level3.c3b`) with `0` BSP partition mismatches. Levels 1–3 come from `CSM.BIN` (`MAP.01` → "Power Station", `MAP.05` → "Around Pyramids", `MAP.06` → "Pyramid of Shmuher"). `MAP.99` is the briefing background and is not built.
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

## Branch status (2026-11-11)

`src/chasm` contains the renderer, the C3B levels, the 3D entities (monsters, limbs,
pickups, props, projectiles), the weapon/pickup system and the HUD. The **doors, gates,
switches, locked exits, moving platforms, rotating parts and lights** described further
down are *not wired into `GameWorld` in this branch*: `tools/build_assets.py` still writes
`res/levels/procs*.bin`, but nothing loads them, and the key cards are currently pure
pickups (they set `has*Key` for the HUD, they do not open anything). Read the Doors,
Gates, Panels, Switches and Moving platforms sections as compiler/design notes.
Only three levels ship: `level1.c3b` (MAP.01), `level2.c3b` (MAP.05), `level3.c3b`
(MAP.06); the legacy level 4 is not built by `build_all_levels()`.

## Release Artifacts

- `/releases/ChasmTheRift.jar` — Preverified J2ME MIDlet suite (`CLDC-1.1` / `MIDP-2.0`)
- `/releases/ChasmTheRift.jad` — JAD descriptor

## Building from Source

```bash
./build.sh
```

`build.sh` does everything: it downloads a JDK 8 and the `LLM-J2ME-SUITE` stubs into
`/tmp` on the first run, compiles `src/` with `-source 1.3 -target 1.1`, runs ProGuard
(shrinking/obfuscation, the MIDlet entry point is kept) and writes:

- `releases/ChasmTheRift.jar` — the suite (about 1.06 MB, well under the usual 2 MB limit)
- `releases/ChasmTheRift.jad` — descriptor with the JAR size and the MIDlet class

Requirements: Linux/macOS shell with `bash`, `curl`, `unzip`, `python3` and network access
on the first build. There is no `javac` requirement on `PATH`; the script uses its own JDK.
Assets are already committed under `res/`, so `./build.sh` alone is enough for a normal
build.

## Installing and running

1. Copy **both** `ChasmTheRift.jar` and `ChasmTheRift.jad` into the same folder on the
   phone (memory card or `Applications`), then install the JAD. The JAR alone also works
   on Nokia S60: *Menu → Applications → Install*, point it at the JAR.
2. On Android/desktop use a MIDP 2.0 emulator, e.g. **J2ME Loader** (Android) or
   **KEmulator** (Windows). Load the JAR and give the emulator a keypad (the game uses
   `1`–`9`, `*`, `0`, `#` and the left soft key).
3. Device requirements: CLDC 1.1, MIDP 2.0, a colour screen. The canvas scales the 3D
   view and lays out the HUD for any size (the HUD height follows the device font); the
   target device is a 240×320 Nokia S60. Sound uses MIDP audio and is optional: if
   `jsr135` or the sound files are missing the game still runs.
4. The first start shows the loading screen and the main menu. *NEW GAME* starts at the
   selected level (level 1 = Power Station by default), *CONTROLS & HELP* shows the key
   table, *OPTIONS* switches 3D shading, music, sound effects and enemy behaviour.
   Those four options and the start level are saved in the MIDlet's record store; level
   progress itself is not saved, so the game always begins a level from its spawn.

## Tests

```bash
./tests/run_tests.sh
```

Builds the suite and runs the headless regression tests in `tests/chasm` against
`releases/ChasmTheRift.jar` (J2ME stubs on the classpath, no device needed):

- `PickupTest` — 85 checks of the pickup table: each model id's effect, its HUD name and
  its `GOT …` message, heal/armour caps, ammo limits, weapon switching, the rifle's
  unlimited ammo, dry-weapon fallback and key flags.
- `LevelItemTest` — loads all three shipped levels and fails on any pickup whose model id
  has no name or is not active.
- `KeyPosTest` — every level must contain its key card, in a real sector, with the
  expected model id (level 1 green 28; levels 2–3 red 27).

## Rebuilding the assets

The committed `res/` is complete, so this is only needed after editing
`tools/build_assets.py`. It reads the retail data, which is **not** in this repository:

```bash
# 1. retail Chasm data (CSM.BIN, 21.6 MB)
git clone --depth 1 https://github.com/Spastic-Dev/ChasmTheRift.git /tmp/ChasmTheRift
# 2. the C3B/BSP compiler and BMP4 helpers used by build_assets.py
git clone --depth 1 -b arena/019ff1eb-covertops3d-re \
    https://github.com/klaxons1/CovertOps3D-RE.git /tmp/CovertOps3D-RE
# 3. textures and sound effects are already in the repo (tex.zip, *.zip)
python3 tools/build_assets.py
./build.sh
```

`tools/build_assets.py` writes `res/models.bin`, `res/ui.bin`, `res/hud_icons.bin`,
`res/levels/level{1,2,3}.c3b`, `res/levels/entities{1,2,3}.bin`,
`res/levels/procs{1,2,3}.bin` and the `res/textures/custom/**` materials; it prints a
`[OK] Level …` line per map and `[SUCCESS]` at the end. Paths are absolute
(`ROOT_DIR = /home/user/Chasm`, `CSM_BIN = /tmp/ChasmTheRift/CSM.BIN`) — adjust the three
constants at the top if your checkout lives elsewhere. Models are packed in a fixed order
(0–10 monsters, 11–16 weapons in hand, 17–29 pickups, 30–35 props/projectiles); the pickup
order must match the constants in `GameWorld` (`ITEM_MEDKIT = 17` … `ITEM_KEY_BLUE = 29`).


## Controls (keypad)

| Key | Action |
|-----|--------|
| 2 / 8 | Forward / backward |
| 4 / 6 | Turn left / right |
| 1 / 3 | Strafe left / right |
| 5 | Fire |
| 7 / 9 | Previous / next weapon (only owned weapons are selectable) |
| * | Use / next level once the sector is clear |
| # | Level map (automap) toggle; the player is a triangle pointing where you face |
| 0 | Jump; when the sector is clear it also starts the next level |
| Left soft key | Pause menu |

The main menu's **CONTROLS & HELP** entry shows the same table on the device.

## Pickups, weapons and keys

This is the table the port enforces since 2026-11-11. A pickup is one model in
`res/models.bin`; **the model id alone decides the 3D model, the effect and the HUD
message**, so the three can no longer disagree (the old bug: the `ARMOR3.3O` model gave a
"RIFT RIFLE" because `GameWorld` used an unrelated invented id list).

| id | model (`models.bin`) | HUD name | Effect |
|----|----------------------|----------|--------|
| 17 | `LIFE.3O` | MEDKIT | +20 HP, up to 100 |
| 18 | `BIGLIFE.3O` | MEGA HEALTH | +100 HP, up to 200 |
| 19 | `ARMOR3.3O` | BODY ARMOR | +200 armour, up to 200 |
| 20 | `HELMET1.3O` | HELMET | +100 armour, up to 200 |
| 21 | `BAG.3O` | AMMO PACK | Tops up every owned weapon (see below) |
| 22 | `W_RIFLE.3O` | SUPER SHOTGUN | Grants slot 2 +10 shells |
| 23 | `W_VCANO.3O` | VOLCANO | Grants slot 3 +50 rounds |
| 24 | `WDISK.3O` | BLADEGUN | Grants slot 4 +20 discs |
| 25 | `WCROSS.3O` | LASER CROSSBOW | Grants slot 5 +20 bolts |
| 26 | `W_GR.3O` | GRENADER | Grants slot 6 +5 grenades |
| 27 | `R_CARD.3O` | RED KEY | Sets the red key flag |
| 28 | `G_CARD.3O` | GREEN KEY | Sets the green key flag |
| 29 | `B_CARD.3O` | BLUE KEY | Sets the blue key flag |

The effects are the `ACode` values of the original `CHASM.INF` `[3D_OBJECTS]` table
(102 life, 120 big life, 128 armour, 124 helmet, 141/142/143 keys, weapon pickups 2..6).
A pickup that cannot be used (medkit at full health, weapon already at its ammo cap,
ammo pack with nothing to refill, key already held) stays on the floor.

**Weapons** use the original Chasm order; the HUD shows the name of the slot in hand:

| Slot | HUD name | Model | Ammo cap | Behaviour |
|------|----------|-------|----------|-----------|
| 1 | RIFLE | `OGUN6.3O` | unlimited | Hitscan, 24 damage. The HUD shows `INF` |
| 2 | SUPER SHOTGUN | `RIF_H5.3O` | 50 | Hitscan, 52 damage, slow |
| 3 | VOLCANO | `V8H.3O` | 200 | Hitscan, fast, 18 damage |
| 4 | BLADEGUN | `DISK.3O` | 50 | Fires the saw-disc projectile |
| 5 | LASER CROSSBOW | `CROSS.3O` | 40 | Fires the energy-bolt projectile |
| 6 | GRENADER | `GR4.3O` | 50 | Fires a bolt that explodes on impact |

The player starts with the rifle only, as in Chasm (armour 0, no keys). If a weapon runs
dry the game switches back to the rifle instead of inventing ammo. An ammo pack adds
10 shells / 10 Volcano rounds / 20 discs / 10 bolts / 5 grenades to each owned weapon
(`CHASM.INF` `DAm`). The map compiler writes `models.bin` pickups 17..29 in exactly this
order and maps the original item ids (`map texture 131..162`) to them; key cards are
static `.3O` models with `ACode` 141/142/143 and are compiled into the item list first,
so the 48-item cap can never drop a key. In the shipped data level 1 has the green key
(`G_CARD.3O`, `MAP.01`), and levels 2–3 have the red key (`KEY.3O`, `MAP.05`/`MAP.06`).

The HUD shows collected keys as small red/green/blue chips under the message banner in
the top-right corner of the 3D view.

## HUD and developer mode

- **Bottom strip, two rows:** HP / armour / ammo with icons from the original `NSTAT.CEL` art (`WICONS.CEL` for the ammo icon), then the name of the weapon in hand and the enemies still alive. The rifle shows `INF` ammo. The strip is sized from the device font (`16 px icons + font height + 2`) so the second row is not clipped.
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

## W595 performance profile and measurements

The **OPTIONS** menu contains two independent controls (also available from Pause):

- **FAST PROFILE: ON** switches the internal 3D viewport from 240×288 to 120×144
  and selects **FLAT** model shading. The view is nearest-neighbour scaled to the
  screen. Switching it off restores the previous shading and full resolution.
  Shading can still be changed independently while the profile is on. The profile,
  shading and flat-surface setting are saved in RMS; older saves remain readable.
- **FLAT FLOORS: ON** replaces textured floors and ceilings with a representative
  lit colour. It works in either profile; walls and the sky remain textured.

For profiling, turn on **DEV MODE** in the pause menu (it also enables god mode).
While playing, the overlay updates about once per second with averages per sampled
frame. `L` = game logic, `R` = complete 3D world/models/effects, `B` = viewport
scaling plus `drawRGB`, `F` = `flushGraphics`, all in milliseconds. `LEAF` counts
BSP leaves actually processed, `WALL` counts wall segments submitted from those
leaves (not necessarily visible), and `MODEL` counts models submitted after the
coarse view test, including the view weapon. The FPS row also shows `FAST`/`FULL`
resolution and `SF` when flat surfaces are enabled. HUD drawing, other UI work, the frame
sleep, and profiler overhead are outside these four timings. Device clocks may have
coarse granularity, and `flushGraphics` timing may not include display work queued
by the phone. The next row breaks down `R`: `V` = transforming world vertices;
`G` = BSP traversal, walls, floor/ceiling and their setup; `E` = sector entity pass,
including lookup, sorting, clip restoration and drawing models. The view weapon and
some effects are included in `R` but not `E`. `Q~` estimates the lookup portion of
`E`: the one-time per-frame sector-bucket build plus a rotating quarter of leaf
lookups extrapolated to all processed leaves. It is noisy on coarse clocks, can be
zero, and is not an additional cost to add to `E`. `S` = scaling and `D` = the remainder of `B` (mainly `drawRGB`).
Rounding means the displayed components need not sum exactly.

All these timings include some instrumentation overhead. In particular, `Q~` adds
clock calls per sampled leaf, so compare FPS with DEV MODE off when testing actual
performance. FPS is measured over elapsed time, including the sleep. Movement and
game timers are still tied to frames.

For a useful comparison on the same device and build, disable music/SFX; stand at
one position and face the same direction for at least several seconds, note FPS and
all four timings/counters, then repeat with FAST on and optionally FLAT FLOORS on.
Repeat in a corridor, in a large view, and with several enemies. A desktop result is
not a substitute for the phone: the latest portal and triangle changes have not yet
been measured on W595. After the scaling and bucket changes, the user measured at
X=-1776, Z=-1200: TX FULL at 12° showed 5 FPS, R/G/E/Q~/S/D =
149/68/59/1/6/6 ms, 85 leaves, 386 walls and 32 models; FL FAST at 18° showed
9 FPS, 67/30/32/1/9/7 ms and 85/386/34; FL FAST SF at 25° showed 9 FPS,
69/27/37/3/9/6 ms and 85/386/36. These are snapshots at different angles, not a
controlled benchmark. In particular, Q~ is a noisy estimate and V rounded to 0–1 ms.
On the preceding build, the user measured at X=-1776,
Z=-1200: TEXTURE at 6° showed 5 FPS, R/G/E/Q~/S/D=149/68/59/6/24/6 ms,
85 leaves, 386 walls, 29 models; FLAT at 12° showed 8 FPS,
68/30/34/3/22/7 ms and 85/386/31; FLAT with flat floors at 18° showed 8 FPS,
67/28/35/8/22/7 ms and 85/386/33. All three displayed V=0 ms (rounded), which
does not mean zero cost. Resolution/profile cannot be inferred from those overlays.
The previous build was measured by the user at X=-1776, Z=-1200: TEXTURE at angle
353° showed 4 FPS, L/R/B/F=0/158/29/3 ms, 85 leaves, 386 walls, 23 models; FLAT
at 0° showed 8 FPS, 0/68/29/3 ms and 85/386/26; FLAT with flat floors at 6° showed
8 FPS, 0/66/29/4 ms and 85/386/26. Resolution/profile cannot be inferred from the
old overlay alone. These snapshots do not independently identify the cost of model
shading, resolution, vertex transforms or entity lookup.

PVS now conservatively rejects pairs only when immutable solid boundary segments
completely separate expanded sector bounding boxes and cover every possible ray
between them. The two-cell margin protects model extents in the shipped data; portals
are never considered occluders. This is intentionally conservative: the current levels
reject 1220/19044, 388/6889 and 2198/29584 sector pairs, respectively, so it will not
by itself eliminate most BSP work. The headless tests compare PVS on/off at multiple points and angles, but do not
prove that every camera position, moving entity or device is artifact-free. If wall
or entity pop-in appears, report the level, coordinates and direction.

## Performance notes

- Empty portal columns skip perspective texture and lighting calculations when no
  upper or lower wall is drawn. Floor, sky and clip updates still run normally.
- FLAT model triangles advance fixed-point edges by addition per scanline, instead
  of recalculating them with long multiplication. Image equivalence is tested against
  the previous rasterizer; actual W595 impact has not yet been measured.
- Viewport scaling copies repeated output rows and uses bulk copies for identical
  widths or a specialised 2× horizontal path. It produces the same nearest-neighbour
  pixels as the general map-based path; speed depends on the device's `arraycopy`.
- Active entities are grouped by BSP leaf once per frame before the entity pass,
  rather than scanning every entity array for every leaf. Per-type slot order is
  preserved, including equal-depth ties and the 96-entity leaf limit. `Q~` includes
  the bucket setup time, so old and new measurements can be compared approximately.

- Sound effects are played by a background audio worker with cached, pre-realized players. The frame loop never calls `Manager.createPlayer()` or `realize()`, which removed the shot micro-stalls on S60 / Sony Ericsson phones.
- Projectile weapons (BladeGun disc, Laser Crossbow bolt, Grenader grenade) damage only on impact (no duplicate hitscan).

## Prop collision

Static 3D props (explosive barrels, pharaoh statues, obelisks) block movement through simplified axis-aligned boxes. Each box's half-extents are the largest horizontal vertex offsets of the model, scaled with the renderer's model-to-world factor, and are stored in the entity sidecar (`>BhhBhh` per prop). The player is pushed out along the axis of least penetration. Monsters are pushed out the same way when they spawn or update inside a box, so a spawn on top of a barrel is recoverable. A chasing monster that is blocked by a box detours around it on one side for a fixed number of ticks, which avoids oscillating at the corner. Line of sight still ignores props. Player projectiles and hitscan shots now hit explosive barrels (see below).

## Explosive barrels

Barrels are prop model 30 (`BAREL1.3O`), one per `BAREL` object on the map. They are shot, not walked around: hitscan weapons take the nearest target (barrel or monster) in the crosshair, and player projectiles (bolts, saw-discs) stop on the first barrel they reach.

- **Hit points:** 40. Two rifle hits (24 each), three Volcano hits (18 each), or one Super Shotgun blast (52).
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
- **Keys** are pickup models 27 (red), 28 (green) and 29 (blue). All three are shown as colour chips in the top-right corner of the 3D view (red, green, blue from left to right) and the `has*Key` flags are set. In the shipped data level 1 carries the green `G_CARD.3O` and levels 2–3 the red `KEY.3O`.
- **Exit**: when every enemy is down, walking to the level's exit point completes the level. Nothing else needs to be pressed.

- **Moving platforms** (`move`, `xmove`, `ymove` on a cell that holds a 3O model, for example `MAP.06` procs 2, 3 and 16). Their model is not a static prop: it is a prop box that the proc moves (see below).

Not implemented: `shoot` links, `light`, `playani`, `waitout`, `death`, and vertical lifts. In the `CSM.BIN` data, the `up` action only raises fence and frame walls; no lift platform uses it. These are parsed and ignored.

- **Static thin walls:** every other thin wall (`tid < 128`) between two walkable cells is a solid textured panel (kind 2, no proc, always solid). The baked C3B has walls only on sector edges, so these were missing. Walls on the edge of a solid cell are already baked, and walls touching a door, rotating or moving cell belong to that proc, so they are not added. Levels 1–3 get 139, 64 and 261 of them. `build_assets.py` checks reachability with a 1/8-cell flood fill that counts a wall's neighbours as blocked, and places monsters, items and the exit only in the reachable cells. Reachable: about 74% of the walkable cells of level 1, 99% of level 2 and 85% of level 3. The rest is walled off by the level's own walls and is not visited. Not checked by playing on a phone.
- **Panels (door panels and rotating walls):** the thin walls inside a door's cells, and inside a rotating proc's cells, are not in the baked C3B geometry (only cell edges are). `build_assets.py` writes them to the end of `procs{N}.bin` as panel records (kind, proc, texture slot, endpoints, pivot, angle). At load each becomes a solid textured wall, with its own vertices, in the sector on each side of it. A door panel is solid only while its proc is closed; while closed, the door's side walls are hidden (they still block). A rotating panel turns about its pivot by `angle × stage / 256` (`rotate X Y cx cy angle`, Chasm-Reverse `Action::Rotate`; the sign was checked against the solid cells the panel sweeps through). Rotating procs open on a touch like doors and stay open (`BackWait 0`). Panel placement is approximate: a panel is drawn in the sector next to it, so a rotating panel that swings far can be cut at a sector boundary.

### Moving platforms

- **Build:** `tools/build_assets.py` finds each `move`/`xmove`/`ymove` whose cell holds a model in `MOVER_MODEL_IDS` (`M-STAR.3O`, `V2.3O`, `FARAON.3O`). Each one becomes a mover record in `res/levels/procs{N}.bin` (proc id, model id, base position, total travel dX/dZ in world units, collision half-extents, angle). The move amounts are the proc's own arguments, scaled by 96/256 (the cell is 256 map units). `M-STAR.3O` and `V2.3O` are added to `models.bin` as models 36 and 37. The model's position comes from its object record and is not snapped to the cell.
- **Runtime:** a mover is a prop slot whose box follows its proc's stage each tick. The proc uses the original's time scale (stage = seconds × speed / 10, so a full move takes 10 / speed seconds). A mover proc starts on the same touch triggers as doors.
- **Blocking and pushing:** the box blocks the player and monsters like a static prop. When the box moves onto the player or a live monster, that entity is shoved to the box's leading face. The shove is skipped if the target point has no sector, and the box pushout then acts instead.
- **Deviation:** nothing rides the platform. The original's `GetFloorLevel` ignores dynamic models, so the player is not carried by a platform; the platform only blocks and pushes.

Each level is compiled from a **1024-cell flood fill** around the spawn cell
(`compile_chasm_map_to_c3b(..., max_cells=1024)`), not from the whole map: the current
build has 138, 83 and 172 sectors and 507, 338 and 721 walls for levels 1–3, with
`bsp_err=0`. Monsters, items and props are placed only inside that compiled area, so a
map's outer rooms can still be missing. The cap is the first thing to raise if a level
feels cut off (or lower if a device runs out of memory): one constant per level call.
Changing it re-compiles `level*.c3b` and `entities*.bin`.

## Levels

The `CSM.BIN` used by this build has four maps: `MAP.01`, `MAP.05`, `MAP.06` and `MAP.99`. `MAP.99` is the briefing background, so it is not a level. The playable levels are:

| Level | Map | Name (from `RESOURCE`) | Sectors / walls | Pickups | Monsters | Key |
|---|---|---|---|---|---|---|
| 1 | MAP.01 | Power Station | 138 / 507 | 32 | 21 | green, model 28 (`G_CARD.3O`) |
| 2 | MAP.05 | Around Pyramids | 83 / 338 | 48 | 24 | red, model 27 (`KEY.3O`) |
| 3 | MAP.06 | Pyramid of Shmuher | 172 / 721 | 40 | 24 | red, model 27 (`KEY.3O`) |

Each level gets all five weapons, body armour, helmet, mega health, a medkit and an ammo
pack before the remaining slots are filled with the map's own duplicate weapons, medkits
and ammo boxes, so the item cap can never remove a weapon the level needs. Pickups are
taken from the real `MAP.xx` placements; when a map has no instance of an essential type at
all, the compiler adds one synthetic pickup in a sector centre.

Keys are regular pickups (models 27 red, 28 green, 29 blue) and are written into the
item list **before** any other pickup, so the 48-item cap can never drop one. The legacy
level 4 builder (`parse_legacy_map` / `compile_legacy_level`, `tools/legacy/map3.bin`)
still exists but is not called by `build_all_levels()` and no `level4.c3b` is shipped;
without `entities4.bin` the runtime has nothing to load for it.

## Known limitations

- **Wall layout is approximate.** The C3B compiler decides which cells are open from the
  floor grid alone (`floors < 62`) and turns wall objects into sector textures, not solid
  geometry. Cells that carry a wall object but still have a floor texture are therefore
  compiled as floor (323 / 156 / 556 cells in MAP.01 / MAP.05 / MAP.06), so some of the
  original internal walls are missing and rooms can merge. Excluding those cells from the
  flood fill was tried and collapses the levels to 11 / 4 / 5 sectors: a wall object is a
  line segment inside an open cell, not a solid cell. A real fix has to build geometry
  from the wall segment endpoints (and then re-validate spawn, keys, items, monsters).

- **Campaign coverage is incomplete.** The `CSM.BIN` used by this build contains only the map bundles `MAP.01`, `MAP.05`, `MAP.06` and `MAP.99` (`MAP.99` is the briefing background, not a playable mission). The game has three playable levels from the data. Episode 1 "The CondemNATION" needs `MAP.01`–`MAP.04` (Power Station, Military Base, Underground Base, Pit of Sarcophagus), and `MAP.02`–`MAP.04` are absent. Levels 2–3 are therefore not the Episode 1 missions; they are the data that exists. Level names come from each map's `RESOURCE` (`#name`). The briefing texts are short neutral descriptions, not the original scripts.

- Only links (proximity, floor, toggle, return, destroy on the gate lock), `unlock` chains, doors, gates (`up`) and moving platforms are executed from `PROCESS.XX`. `shoot` links (walls), rotating parts (see Doors), lights and animations are not.
- Door textures: a closed door now shows its own panel texture (the thin wall inside the door cell, see Doors), instead of the black column of the zero-height sector. Checked only with the desktop render harness, not on a phone. Every other thin wall in walkable cells is now solid geometry too (see Panels). Monsters, items and the exit are placed only in cells that the player can reach with these walls in place.
- The automap (key 9) has not been checked on a device.
- The full-area geometry has not been measured on a device. Frame rate and memory with 150–230 sectors are unknown.
- Some portal walls still show angle-dependent distortion; the root cause has not been isolated.
- Transparent grate textures are drawn as opaque (no masked mid-texture pass yet).
- The first build's staircase floor steps are only partially restored.
