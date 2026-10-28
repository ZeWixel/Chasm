# Chasm J2ME — прогресс ветки `arena/3c5b53b8-chasm` (2026-10-28)

> **Ветка:** `arena/3c5b53b8-chasm` · **статус:** `pr_open` · **HEAD:** `f94b01d` (поверх `1f1f292 Merge PR #2`) · **JAR:** `releases/ChasmTheRift.jar ~2.36 MB` + `ChasmTheRift.jad`
> Сборка: `./build.sh` (JDK8 + LLM-J2ME-SUITE, ProGuard, preverifier). Ресурсы: `python3 tools/build_assets.py && ./build.sh`.

Этот файл — единственная точка входа для новой сессии. `SESSION_NOTES.md` (2026-10-27) и `MISSING_RESOURCES.md` — черновики, их содержимое ниже консолидировано.

---

## Эволюция задачи

**Итерация 2026-10-27 (камера=игрок, враги 0/255, BMP 12,12,16→скайбокс) — выполнено:**
- Камера опущена до уровня глаз: `PLAYER_HEIGHT_OFFSET = 40<<16 = 2621440` (`GameWorld:758/762`), было `740k≈11.29u` обрезало голову (`scrY -13`). `MONG/JOKER/VIKING h 89/91/94px` теперь влезают в `winH132 Y32-121`.
- Прозрачность врагов `PNG modelType 0..10 th 891-989` с `alpha0` (texel 0/255) — `PolyRenderer3D` `transparentTexHoles` для `Model0 211v / Model7 193v / Model10 199v`, оружие `14/15` (Crossbow/Saw).
- Скайбокс: `build_assets.py` `BMP 12,12,16` больше не `0,0,0`, `CustomMaterialSet first16Colors` маска `1<<idx`, `PortalRenderer` раньше пропускал прозрачные texels → «небо плывёт», теперь `getSkyboxColorForWallHole` с `viewAngle = doubleAngle + playerAngle<<1` рисует цилиндр `64x128` на каждый `screenY`.

**Итерация 2026-10-28 (предметы, враги, турель, взрыв, PNG):**
> Пользователь: «подойдя к модельке брони подбираю патроны для Rift Rifle, а к снарядам Crossbow — Megazapper… имена врагов были перепутаны… турель ведёт себя не как турель. Добавь взрыв как у бочки от снаряда Megazapper. Прозрачность на всех png, не только враги+Crossbow.»

Все 4 пункта закрыты в `f94b01d`.

---

## Что сделано 2026-10-28 — детали

### 1) Предметы: `checkItemPickups` переписан на `modelId`
**Баг:** `t=itemType%8` давал wrap. Дамп `models.bin` world-pickup моделей 17-29 (`/tmp/item_tex/17..29_ITEM*.png 64x26..646`):
| modelId | tex H | что на картинке | старый `t%8` → что давал | правильно |
|---|---|---|---|---|
| 17 | 26 | medkit | t0 medkit ok | **17 medkit +25HP** |
| 18 | 252 | armor vest зелёный | t1 armor ok | **18 armor +25** |
| 19 | 563 | Rifle | t? weapon ok | **19 Rifle w0** |
| 20 | 646 | Shotgun | | **20 Shotgun w1** |
| 21 | 79 | small box Chaingun ammo | `21%8=5→w3 Crossbow` | **21 Chaingun ammo w2** |
| 22 | 508 | Crossbow | t6? ok | **22 Crossbow w3** |
| 23 | 325 | Saw Launcher | | **23 Saw w4** |
| 24 | 53 | Disk Crossbow ammo | `24%8=0→Medkit` | **24 Crossbow ammo w3** |
| 25 | 205 | Saw discs ammo | `25%8=1→Armor` | **25 Saw ammo w4** |
| 26 | 347 | Megazapper | `26%8=2→Chaingun` | **26 Megazapper w5** |
| 27-29 | 112 | keys red/green/blue | | ignore / +5 ammo |

Также GIF-доказательства: `frame_gif-20261028-090509.png 36f 356x469` — на брони-модели (model 18) всплывает `GOT1:RIFT RIFLE`; `frame_gif-20261028-090749.png 63f 360x462` — `5:SAW LAUNCHER` корректен, но Crossbow→Megazapper подтверждён дампом выше. Список `essential_items [22,23,24,25,26,17,19,21,18,20]` усугублял wrap.

**Фикс `GameWorld.java:882`:**
```java
int modelId = itemModelId[i];
if (modelId==17 && health>=100) continue;
if (modelId==17) health=min(100,h+25)
else if (18) armor=min(100,a+25)
else if (19) weaponOwned[0]=true ammo[0]+=15 curWeapon max
else if (20) w1
else if (21) ammo[2]+=15 // Chaingun ammo
else if (22) w3 Crossbow
else if (23) w4 Saw
else if (24) ammo[3]+=8  // Crossbow ammo Disk
else if (25) ammo[4]+=8  // Saw discs
else if (26) w5 Megazapper
else if (27-29) keys
else fallback t%8
```
Имена врагов `MONSTER_NAMES` уже были исправлены (MONG/STRATOS/SCORPION/TURRET/FAUST/ZOMBIE/…), перепроверены — порядок совпадает с `models.bin: MONGF,WMAN,SPIDER,TT_TOWER,FAUST1,DEADMAN1,PANT1,JOKER6,SCELET3,LION4,VIKING4`.

### 2) Враги: турель стационарна
`GameWorld.updateMonsters` — `MSTATE_CHASE` шагание `tryMonsterStep/detour` теперь `if (dist>44<<16 && monType!=3)`. Для `monType==3 TURRET`:
```java
} else if (monType[m]==3) { // idle sway while targeting
  monFrameA = (tick>>4)&1; monFrameB=(A+1)&1; monInterp=(tick&15)<<4;
}
```
Турель только поворачивает `monAngle=angleToPlayer` + стреляет, не преследует. `ENEMY_AGGRESSIVE`/`SIMPLE` visibility `900/380` и `chaseDist 320/150` сохранены и уже исправлены (агрессивность не инвертирована).

Набор врагов на 1-3 уровнях — из `MAP.01/05/06` через `build_assets.py` → `entities*.bin` → `GameWorld` spawn; враги 0/255 прозрачность уже в `f94b01d` первом этапе.

### 3) Megazapper взрыв как у бочки
`fireWeapon` w5: `PROJ_BOLT dmg85 speed12`. Раньше `damageMonster(m,85)` точечно. Теперь:
- `updateProjectiles`: `isMegazapper = projFromPlayer && projDamage==85`
- `!hasLineOfSight(...)` → `triggerMegazapperBlast(projX,projZ)` вместо просто `active=false`
- hit check: `if(mon||barrel in hitRadius) → triggerMegazapperBlast(nx,nz)`
- `triggerMegazapperBlast(cx,cz)` (после `spawnBlastFx` в `GameWorld:660`): `SFX_ROCKET + spawnBlastFx + 4*PROJ_GIB`, damage barrels в `BARREL_BLAST_RADIUS 192` с `blastDamage=90*(R-d)/R` (центр 90, край 0), queue chain `blastQueue[]` как `detonateBarrel`, затем monsters + player в том же радиусе (`blastFlashTimer=BLAST_FLASH_TICKS` + `damagePlayer`). Полная копия логики бочки, без требования `BARREL_MODEL` в центре.

### 4) Прозрачность всех PNG-текстур
Было только `ENT_MONSTER/LIMB` + weapons `14/15`. Теперь:
- `PolyRenderer3D.renderViewWeapon`: `needTransparent = weaponModel!=null && texData!=null` (любое из 11-16, не только 14/15).
- `renderSectorEntities`: `ENT_ITEM/PROP/PROJ` обёрнуты `wasTrans = transparentTexHoles; transparentTexHoles = model.texData!=null; renderWorldModel(); transparentTexHoles=wasTrans;` — все world-пикапы, бочки, снаряды (пользователь дорисует дыры alpha0).

---

## Камера / скайбокс / стены (сохранено с 2026-10-27)

- `PLAYER_HEIGHT_OFFSET 40<<16` vs `740k` — голова не обрезается, ноги влезают.
- `build_assets.py` `12,12,16` помечены прозрачными (раньше `0,0,0`), `PortalRenderer.getSkyboxColorForWallHole` — цилиндр, `viewAngle doubleAngle`.
- Стены `16/64/128` строго (проверка `CustomMaterialSet:218`), без автопада `32→64` (был `scaleBmpHeight32To64`, откачен по решению пользователя — ломает `adjustedTextureOffsetY=height-wallHeight`). Если понадобится 32px — вернуть код из `6951998`.

---

## Сборка и проверка

```bash
python3 tools/build_assets.py   # только при смене MAP/FLOORS/PROCESS/CEL
./build.sh                      # → releases/ChasmTheRift.jar (2359976 B) + .jad
unzip -l releases/ChasmTheRift.jar | grep -E "level|models.bin|open/"
```
Текущие уровни: `level1.c3b 4640 (MAP.01 ~23 сектора) / level2 10615 (MAP.05 ~51) / level3 7342 (MAP.06 ~35)` + `entities*.bin 267/368/272` + `procs*.bin 907/1145/1570` — L4 удалён (level4.c3b/procs4/entities4). `models.bin 1430564` (11 монстров 0-10 + 6 оружий 11-16 + pickups 17-29 + props 30-38).

Верификация 2026-10-28: `pip3 install Pillow && python dump GIF every5 + models.bin extract 17..29` → `/tmp/item_tex/*.png`, `/tmp/gif_gif-20261028-*-f015/f020.png` — скриншоты подборов сверены с таблицей выше.

---

## Файлы — что где лежит

| Путь | Что | Зачем |
|---|---|---|
| `src/chasm/GameWorld.java` | `checkItemPickups` switch modelId, `updateMonsters` TURRET guard, `updateProjectiles`+`triggerMegazapperBlast`, `detonateBarrel`/`blast*` | геймплей |
| `src/chasm/PolyRenderer3D.java` | `transparentTexHoles` для всех `weapons` + `ENT_ITEM/PROP/PROJ` | прозрачность |
| `src/chasm/PortalRenderer.java` | `getSkyboxColorForWallHole` | небо |
| `src/chasm/CustomMaterialSet.java` | `first16Colors 1<<idx`, `Wall height 16/64/128` | скайбокс/стены |
| `tools/build_assets.py` | `max_cells=256`, `save_rgb_as_bmp4` magenta→0, `compile_chasm_map_to_c3b` | уровни |
| `res/levels/*.c3b/*.bin` | 3 уровня C3B1 BSP-portal | данные |
| `res/models.bin` + `res/open/textures/{monsters,weapons}/*.png` | 11+6+~15 моделей, атласы 64×th | модели |
| `/tmp/item_tex/17..29.png` `/tmp/gif_*_f*.png` | дампы 2026-10-28 | отладка, не в git |

Игнорируемые артефакты снапшота: `.cache`, `build`, `node_modules`, `dist`, `__pycache__` + креды `.git/config`.

---

## Ошибки и тупики — не повторять

- `git push fb3a723/8e991fa non-fast-forward` — `merge-base 1f1f292≠e8ab719`, `commit` от `1f1f292` вместо `b57e8d5`; правило: `fetch+reset --hard origin/arena/3c5b53b8-chasm` перед edit.
- Полный `resample` любых высот — отклонён, ломает `PortalRenderer adjustedTextureOffsetY`.
- `palette.txt/README.txt` в `res/open/` — удалены, палитра остаётся в `res/pal.bin`.
- `res/open/L4/materials.c3m`, `entities4/level4/procs4` — удалены (L4 тестовая комната).

---

## Внешние источники

- https://moddingwiki.shikadi.net/wiki/Chasm:_The_Rift — CSM/MAP/FLOORS/CEL
- https://github.com/Panzerschrek/Chasm-Reverse map_loader — MapWall 11B 64×64
- https://github.com/ZeWixel/Chasm/blob/main/EDITDAT.zip — 4.5M `git show origin/main:EDITDAT.zip` → LEVELA/B/C MAP/FLOORS/RESOURCE/GFX CEL/3D (L4-L6 тестовые 1 сектор)
- gh api `88cfaffa` `da17d5c` — патч дверей до отката

---

## Что дальше

- Preview снова режет — править только `camH` в `ChasmCanvas` (+20-40k), не `winH/gap/topY`.
- Кастом-стены 32px — вернуть `scaleBmpHeight32To64` из `6951998`.
- Полный порт: добыть retail `MAP.02-04,07-16` + `FLOORS` (отсутствуют в CSM.BIN/EDITDAT.zip), доделать `shoot`/`light`/`playani`/`waitout`/`death`, CEL→BMP4 с альфой для заборов, звуки `LEVEL*/SOUND/*.WAV`.

