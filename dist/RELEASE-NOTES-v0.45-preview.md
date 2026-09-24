## Simple Zombie-NX `v0.45 preview`

A zombie apocalypse mod for the **GTA V Switch port** (Mojso port / Ryujinx), written in
Rockstar's **SanScript**. Comes in **two builds — Chinese and English** (pick one).

给 **GTA V 的 Switch 移植版**写的僵尸末日模组，**中文 / 英文两套产物**（二选一）。

---

### Install / 安装

1. **Back up** your original `script_rel.rpf` first. **先备份原版**
2. Add these as **normal compressed-file entries** into
   `romfs/update/update2.rpf/switch/levels/gta5/scripts/script_rel.rpf`:
   - `error_listener.nsc` (entry script / 入口脚本)
   - `simple_zombies.nsc` — from the zip of your language / 取对应语言包里的这一个
3. **Do not rename the files.** The compiled image embeds `joaat(script name)` at offset
   `0x58` and the loader matches on it. **文件名不能改** —— 脚本名烧在产物里。

### What's in it / 本版内容

- **Infection mode** — master switch: hordes, survivors, raiders, dead-city ambience
- **Zombies** — Walker / Runner / Brute / Behemoth / Screamer; 4 tiers of cap, spawn
  distance, health, speed, mutant chance. Half the cap in the wilderness, nothing in
  water, ground only (no rooftops, no interiors)
- **Camp** — tent + campfire + 35 m safe zone, sleep until 6 AM
- **Survivors & guards** — recruit, order them to follow or hold
- **Looting & wrecks** — search bodies, collect car parts, repair wrecks
- **Ambience** — traffic stops, world goes quiet, power outages, 54 emergency broadcasts,
  apocalypse day counter

### Keys / 按键

`RB+B` menu · D-pad **right** interact / loot / recruit / rest · D-pad **left** guard hold

### Downloads / 下载

| | |
|---|---|
| 中文版 | `脏比洛圣都-v0.45-preview.zip` |
| English build | `Simple-Zombie-NX-v0.45-preview.zip` |

### Notes

- Source code, dev notes and tooling requirements: see the repo README.
  （源码、技术笔记与所需工具见仓库 README）
- No game assets are included. Requires a legitimate copy of GTA V and the Switch port.
