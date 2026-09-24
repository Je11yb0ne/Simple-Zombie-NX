## Simple Zombie-NX `v0.45 preview`

[中文](#中文) · [English](#english)

---

# 中文

给 **GTA V 的 Switch 移植版**（Mojso port / Ryujinx）写的僵尸末日模组，用 R\* 官方
**SanScript** 写成。提供中文 / 英文两套产物，二选一安装。

## 下载

| 语言 | 文件 |
|---|---|
| 中文版 | `Simple-Zombie-NX-v0.45-preview-zh-CN.zip` |
| English | `Simple-Zombie-NX-v0.45-preview.zip` |

## 安装

1. **先备份**原版 `script_rel.rpf`。
2. 把压缩包里的两个文件放进：
   `romfs/update/update2.rpf/switch/levels/gta5/scripts/script_rel.rpf`
   - `error_listener.nsc`（入口脚本）
   - `simple_zombies.nsc`（主体脚本）
3. 两个都作为**普通压缩文件条目**添加。
4. **文件名不能改** —— 产物在偏移 `0x58` 处嵌了 `joaat(脚本名)`，加载器靠它匹配，改名即加载失败。
5. 只装裸载荷 `.nsc`，不要装任何中间产物。

## 本版内容

- **感染模式** —— 总开关
- **僵尸** —— 行者 / 奔跑者 / 蛮兽 / 巨兽 / 尖啸者；四档上限、刷新距离、血量、速度、变异概率
- **刷新规则** —— 野外上限减半；水里、楼顶、室内不刷
- **营地** —— 帐篷 + 篝火 + 35 米安全区，睡觉到早上 6 点
- **幸存者与护卫** —— 可招募，可命令跟随或留守
- **搜刮与报废车** —— 搜尸体、收汽车零件、修车
- **末日氛围** —— 车流停止、世界静默、停电、54 条紧急广播、末日天数
- **中英双语** —— 两套产物，界面文案各自语言

## 操作

| 按键 | 作用 |
|---|---|
| `RB + B` | 打开 / 关闭菜单 |
| 方向键 上 / 下 | 选择项目 |
| 方向键 左 / 右 | 修改数值 |
| 十字键 右 | 互动（搜刮 / 招募 / 修车 / 用解药 / 营地休息） |
| 十字键 左 | 切换护卫跟随 / 留守 |

## 说明

- 本仓库**不含任何游戏资源或 R\* 官方脚本**，需要你自己有合法的 GTA V 与 Switch 移植版。
- 从源码构建需要 R\* 官方 `dev_ng` 工具链（不随仓库提供），细节见 `docs/SANSCRIPT-NOTES.md`。

---

# English

A zombie apocalypse mod for the **GTA V Switch port** (Mojso port / Ryujinx), written in
Rockstar's **SanScript**. Comes in **two builds — Chinese and English** (pick one).

## Downloads

| Language | File |
|---|---|
| Chinese | `Simple-Zombie-NX-v0.45-preview-zh-CN.zip` |
| English | `Simple-Zombie-NX-v0.45-preview.zip` |

## Install

1. **Back up** your original `script_rel.rpf` first.
2. Put the two files from the zip into:
   `romfs/update/update2.rpf/switch/levels/gta5/scripts/script_rel.rpf`
   - `error_listener.nsc` (entry script)
   - `simple_zombies.nsc` (main script)
3. Add both as **normal compressed-file entries**.
4. **Do not rename the files.** The compiled image embeds `joaat(script name)` at offset
   `0x58` and the loader matches on it; renaming breaks loading.
5. Install the plain `.nsc` only — not any intermediate file.

## What's in it

- **Infection mode** — master switch
- **Zombies** — Walker / Runner / Brute / Behemoth / Screamer; 4 tiers of cap, spawn
  distance, health, speed, mutant chance
- **Spawn rules** — half the cap in the wilderness; nothing in water, on rooftops or indoors
- **Camp** — tent + campfire + 35 m safe zone, sleep until 6 AM
- **Survivors & guards** — recruit, order them to follow or hold
- **Looting & wrecks** — search bodies, collect car parts, repair wrecks
- **Ambience** — traffic stops, world goes quiet, power outages, 54 emergency broadcasts,
  apocalypse day counter
- **Bilingual** — two builds, each with its own language strings

## Keys

| Key | Action |
|---|---|
| `RB + B` | open / close menu |
| D-pad up / down | select item |
| D-pad left / right | change value |
| D-pad right | interact (loot / recruit / repair / antidote / camp rest) |
| D-pad left | toggle guard follow / hold |

## Notes

- **No game assets or Rockstar scripts are included.** You need a legitimate copy of GTA V
  and the Switch port.
- Building from source requires Rockstar's official `dev_ng` toolchain (not shipped here);
  see `docs/SANSCRIPT-NOTES.md`.
