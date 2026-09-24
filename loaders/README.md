# loaders/ —— 入口脚本（`error_listener`）的几个版本

装模组时，游戏只会自动加载**它认识的脚本名**。新增的脚本名（如 `simple_zombies`）
不会被自动执行，所以必须借一个「游戏自带脚本」当入口 —— 我们用 `error_listener`。

| 文件 | 用途 | 用不用 |
|---|---|---|
| `../src/error_listener.sc` | **只加载「脏比洛圣都」**（生产版） | ★ 日常就用这个 |
| `error_listener.dual.sc` | 同时拉起**两个**模组的模板 | 要和其他模组共存时才用 |

> `error_listener.sc` 的**文件名不能改** —— 脚本名取自源文件名（产物 0x58 处嵌
> `joaat(脚本名)`），改了就顶替不上游戏自带的入口。

---

## ★ 唯一的硬规则：引擎一次只认一个脚本请求

两个 `REQUEST_SCRIPT_WITH_NAME_HASH` **并排写**是不行的：

```san
// ✗ 错误写法：第二个请求被静默忽略
REQUEST_SCRIPT_WITH_NAME_HASH(HASH("simple_zombies"))
REQUEST_SCRIPT_WITH_NAME_HASH(HASH("flight"))
```

更糟的是，如果其中一个是**根本没装进 RPF 的脚本名**，整条请求链都会坏掉。
表现是 **"文件装了、游戏里完全没反应，连报错都没有"** —— 我们正是踩了这个坑
（旧版 loader 里请求了一个没安装的 `flight`）。

正确写法是**顺序化**，一帧里最多只有一个脚本处于"被请求中"：

```san
IF GET_NUMBER_OF_THREADS_RUNNING_THE_SCRIPT_WITH_THIS_HASH(H) > 0
    up = TRUE                       // 已经在跑，什么都不用做
ELSE
    up = FALSE
    REQUEST_SCRIPT_WITH_NAME_HASH(H)          // 每帧发一次，幂等
    IF HAS_SCRIPT_WITH_NAME_HASH_LOADED(H)
        IF GET_GAME_TIMER() - lastStart > 1000     // 1 秒节流，防止重复拉起多份
            START_NEW_SCRIPT_WITH_NAME_HASH(H, STACK)
            lastStart = GET_GAME_TIMER()
        ENDIF
    ENDIF
ENDIF
```

### 拉两个（或更多）模组

把上面这段**依次排列**，用前一个的就绪标志当前一个的门禁：

```
模块1（无条件拉）
   └─ m1Up ──> 模块2
                 └─ m2Up ──> 模块3
```

完整可编译的版本见 `error_listener.dual.sc`（已按这个结构写好，含注释掉的
第三个模块槽位）。

### 为什么要 1 秒节流

`START_NEW_SCRIPT_WITH_NAME_HASH` 之后线程不是当帧就登记的。不加节流的话，
`HAS_SCRIPT_LOADED` 为真、线程数还是 0 的那几帧会被**反复拉起** ——
同一个脚本跑出好几份，菜单会重复响应、效果翻倍。这个问题**静默发生**，很难查。

---

## 栈大小（`START_NEW_SCRIPT_*` 的第二个参数）

官方 `dev_ng/core/game/data/stack_sizes.sch` 是权威参考：

| 常量 | 值 |
|---|---|
| `MINI_STACK_SIZE` | 512 |
| `DEFAULT_STACK_SIZE` | 1424 |
| `INTERACTION_MENU_STACK_SIZE` | 9800 |
| `WAREHOUSE_STACK_SIZE` | 14100 |
| `PROPERTY_INT_STACK_SIZE` | 19400 |
| `FMMC_LAUNCHER_STACK_SIZE` | 24000 |
| `MISSION_STACK_SIZE` | 53000 |

模组脚本一般 **8032**（`TRANSITION_STACK_SIZE`）就够 —— 「脏比洛圣都」
（5300 行 / 115 个过程 / 193 个 native）实测用 8032 正常。起不来再往上调。

> **栈不够的症状**：`START_NEW_SCRIPT` 静默失败，脚本"启动即死"，
> 没有任何报错。所以 loader 里一定要有可见的失败提示（见下）。

---

## 诊断横幅：别再让失败无声无息

入口脚本**必须能表达自己的状态**，否则"没反应"根本没法定位。现在的做法：

| 情形 | 画面 |
|---|---|
| 主体线程在跑 | 绿色横幅「已载入 - 按住 RB+B 打开菜单」，8 秒后消失 |
| 文件加载了但线程起不来 | 橙色横幅**一直显示**「主体未启动 …」 |
| 文件根本没加载 | 橙色横幅**一直显示**「主体未启动 …」 |

于是看一眼就知道该往哪个方向查：

- **完全没有横幅** → loader 本身没跑起来 → RPF 条目/替换方式的问题
- **橙色横幅常驻** → 两个 `.nsc` 的**脚本名与条目名对不上**，或主体文件坏了
- **绿色横幅** → 装载正常；按不出菜单就是**按键**或脚本内部的问题

横幅位置比主体自己画的横幅**高一格**（`y≈0.30` vs `0.38`），两者同时出现不叠字。
