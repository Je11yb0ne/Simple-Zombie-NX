# Simple Zombie-NX

**English** · [中文](README.md)

A zombie apocalypse mod for the **GTA V Switch port** (Mojso port / Ryujinx), written in
Rockstar's **SanScript**. Chinese and English builds.

- **Version**: 0.45 preview
- **Author**: 果冻骨 Jellybone
- **License**: MIT

## Features

- **Infection mode** — master switch; enables the systems below
- **Zombies** — Walker / Runner / Brute / Behemoth / Screamer, four tiers of cap, spawn
  distance, health, speed and mutant chance. Half cap in the wilderness, none in water,
  ground only
- **Infection** — bites raise infection level; antidotes cure; lethal if ignored
- **Camp** — tent, campfire, 35 m safe zone, sleep until 6 AM
- **Survivors** — recruit guards; order them to follow or hold position
- **Raiders** — armed convoys
- **Loot and repair** — bodies carry ammo, medkits, food, water, armour and car parts;
  parts repair wrecks
- **Ambience** — no ambient traffic, silenced world, power outages, 54 emergency
  broadcasts, day counter

## Controls

| Input | Action |
|---|---|
| `RB + B` | Open / close menu |
| D-pad up / down | Select item |
| D-pad left / right | Change value |
| D-pad right | Interact — loot, recruit, repair, antidote, camp rest |
| D-pad left | Toggle guard follow / hold |

Infection is displayed bottom-right, the day counter bottom-left, broadcasts at the top.

## Install

```
romfs/update/update2.rpf/switch/levels/gta5/scripts/script_rel.rpf
```

Take `error_listener.nsc` and `simple_zombies.nsc` from the zip of your language
(Chinese: `脏比洛圣都-v0.45-preview.zip` · English: `Simple-Zombie-NX-v0.45-preview.zip`),
and add both as normal compressed-file entries. Do not rename them: the compiled image embeds
`joaat(script name)` at offset `0x58` and the loader matches on it.

## Build

Requires Rockstar's `dev_ng` toolchain (`sc.exe`, `scriptrc_x64.exe`) and any working
stock `.nsc` from the same game build. Neither is included here.

```
sc.exe            <name>.sc   -> <name>.sco
scriptrc_x64.exe  <name>.sco  -> <name>.pc.nsc     (-uncompressedresources -aeskey gta5)
header adapt      .pc.nsc     -> <name>.nsc        copy bytes 0x00 and 0x18 from the stock .nsc
```

Install the plain `.nsc`, not the `.pc.nsc`. The script name is taken from the source file
name and is embedded at `0x58`, so the source and the output must both be named
`simple_zombies` (or `error_listener`).

English strings live in `i18n/en.json`; `src/en/simple_zombies.sc` is generated from the
master source plus that table.

Development notes: `docs/SANSCRIPT-NOTES.md` (Chinese).

## Layout

```
src/simple_zombies.sc       main script, Chinese
src/en/simple_zombies.sc    main script, English
src/error_listener.sc       entry script
loaders/                    loader template
i18n/en.json                English text table
docs/SANSCRIPT-NOTES.md     development notes
```

## Credits

- **Simple Zombies [.NET]** by sollaholla — the original mod this port follows
- **Simple Zombies Reborn** by Henry Tux — reference for zombie types and balance
- **Rockstar Games** — GTA V and SanScript
- **GTA V Switch port (Mojso port)**
- **Ryujinx** — testing
- **DeepSeek (V4.1 Flash)** — AI pair-programming

## Notes

- No game assets, Rockstar scripts or compiler toolchain are included.
- Requires a legitimate copy of GTA V and the Switch port. Not affiliated with Rockstar Games.
- MIT licensed.
