# Simple Zombie-NX（脏比洛圣都）

**中文** · [English](README.md)

给 **GTA V 的 Switch 移植版**（Mojso port / Ryujinx）写的僵尸末日模组，用 R\* 官方
**SanScript** 写成。中英双语两套产物。

- **版本**：0.45 preview
- **作者**：果冻骨 Jellybone
- **许可**：MIT

## 功能

- **感染模式** —— 总开关，开启下面这些系统
- **僵尸** —— 行尸 / 跑者 / 壮尸 / 巨兽 / 尖啸者；上限、刷新距离、血量、速度、变种概率各 4 档。
  野外上限减半，水里不刷，只在地面
- **感染度** —— 被咬累积，解药可解，放任会死
- **营地** —— 帐篷、篝火、35 米安全区，睡到早上 6 点
- **幸存者** —— 可招募为护卫，指令跟随或留守
- **掠夺者** —— 武装车队
- **搜刮与修理** —— 尸体上有弹药、医疗包、食物、水、护甲、汽车零件；零件可修报废车
- **环境** —— 无环境车流、世界静默、停电、54 条紧急广播、末日纪时

## 操作

| 按键 | 作用 |
|---|---|
| `RB + B` | 打开 / 关闭菜单 |
| 方向键 上 / 下 | 选择项目 |
| 方向键 左 / 右 | 修改数值 |
| 十字键 右 | 互动 —— 搜刮、招募、修车、用解药、营地休息 |
| 十字键 左 | 切换护卫跟随 / 留守 |

感染度显示在右下角，末日天数在左下角，紧急广播在上方。

## 安装

```
romfs/update/update2.rpf/switch/levels/gta5/scripts/script_rel.rpf
```

从对应语言的压缩包里取出 `error_listener.nsc` 和 `simple_zombies.nsc`（中文包：
`脏比洛圣都-v0.45-preview.zip`；英文包：`Simple-Zombie-NX-v0.45-preview.zip`），
两个都作为普通压缩文件条目加进去。**不要改名**：编译产物在偏移 `0x58` 处嵌了
`joaat(脚本名)`，加载器靠它匹配。

## 编译

需要 R\* 官方 `dev_ng` 工具链（`sc.exe`、`scriptrc_x64.exe`），以及一份同版本游戏的可用
`.nsc`。两者都不在本仓库里。

```
sc.exe            <名字>.sc   -> <名字>.sco
scriptrc_x64.exe  <名字>.sco  -> <名字>.pc.nsc     (-uncompressedresources -aeskey gta5)
头部适配          .pc.nsc     -> <名字>.nsc        从 stock .nsc 抄 0x00 / 0x18 两个字节
```

装裸载荷 `.nsc`，不要装 `.pc.nsc`。脚本名取自源文件名并嵌在 `0x58`，所以源文件与产物
必须都叫 `simple_zombies`（或 `error_listener`）。

英文文案在 `i18n/en.json`；`src/en/simple_zombies.sc` 由主源码 + 这张表生成。

开发笔记见 `docs/SANSCRIPT-NOTES.md`。

## 目录

```
src/simple_zombies.sc       主体脚本（中文）
src/en/simple_zombies.sc    主体脚本（英文）
src/error_listener.sc       入口脚本
loaders/                    加载器模板
i18n/en.json                英文文案表
docs/SANSCRIPT-NOTES.md     开发笔记
```

## 致谢

- **Simple Zombies [.NET]** —— 作者 sollaholla，本移植版的原型
- **Simple Zombies Reborn** —— 作者 Henry Tux，僵尸类型与数值参照
- **Rockstar Games** —— GTA V 与 SanScript
- **GTA V 的 Switch 移植版（Mojso port）**
- **Ryujinx** —— 测试
- **DeepSeek（V4.1 Flash）** —— AI 辅助

## 说明

- 不含任何游戏资源、R\* 官方脚本或编译工具链。
- 运行需要合法的 GTA V 与该 Switch 移植版；与 Rockstar Games 无关联。
- 许可：MIT。
