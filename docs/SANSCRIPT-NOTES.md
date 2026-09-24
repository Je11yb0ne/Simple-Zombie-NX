> 本仓库主体是**源码**。笔记里提到的 `build_nsc.py` / `check_source.py` / `native_lookup.py` 等
> 只是开发时用的自动化脚本，**不随仓库提供** —— 写在这里是为了让上面的流水线可复现、可自查。

# SanScript 技术笔记（脏比洛圣都 / GTA5-NX 端口）

> 从 `MEMORY.md` 拆出来的细节部分。主记忆只留协作习惯、硬约束、流水线；
> 动到**任务/AI、语言细节、载具、HUD、玩家状态**时**先读这个文件**。
> 配套技能：`~/.workbuddy/skills/gta5-nx-nsc-scripting/`（工具链与通用坑）。

## SanScript 语言
- PROC 用 `EXIT` 返回；FUNC 用 `RETURN 值`。**PROC 里不能裸 RETURN**。
- ★★ **`EXIT` 在 FUNC 里非法**（*"Cannot use EXIT in a FUNC (use RETURN instead)"*）；
  **`BREAK` 只认 SWITCH**（*"BREAK outside any SWITCH"*）⇒ 在 FUNC 里想跳出循环，
  只能**用标志位让循环条件自己结束**。
- **必须先定义后调用**（否则 "Unrecognized variable or command name"）——插入大段代码前先
  确认插入点之后没有它依赖的函数。
- 无 `CONST_STRING`；**float 不能隐式转 int**（用 `ROUND/FLOOR/CEIL`）；**字符串不能与
  函数调用拼接**；局部变量别用内建名（`night`/`phase`/`surge` 会 parse error）。
- 主循环**绝不能 WHILE 等游戏就绪**（卡死脚本线程），用 IF 每帧放行。
- **局部数组可用**；但"边遍历名单边删名单"要先把候选收进本地数组（swap-remove 会漏人）。
- 向量字面量 `<<x, y, z>>` 可用。
- `TASK_PLAY_ANIM`：flags 用 `INT_TO_ENUM(ANIMATION_FLAGS, 48)`（16 `AF_UPPERBODY` +
  32 `AF_SECONDARY`）。**只加 AF_UPPERBODY 会占主任务槽、顶掉 `TASK_COMBAT_PED`**
  → "咬一口就站着不动"。`nTimeToPlay` 写 **-1**（播完整段），冷却 ≥ 动画时长
  （写 1000 会半途被打断 = "攻击做一半又重来"）。
- ★★ **SanScript 不区分大小写** → 局部变量名会和**函数名**撞车，声明行直接
  `parse error, expecting NEWSYM or VAR_REF`。踩过：`isWreck` vs `IsWreck()`（v0.22）、
  `nearCamp` vs `NearCamp()`（v0.24）。
  **命名策略：局部变量用"数据名"，别用"动作名"** —— `atCamp` / `wreckFix` / `holdPos`
  这种。另外也别撞内建名（`night` / `phase` / `surge` / `post` / `time` / `light`）。
  实在要短名就分两步：`VECTOR v` 然后 `v = ...`。
- ★ **循环跳出用 `BREAK`**（官方 `.sc` 里大量使用），不是 `EXIT` —— `EXIT` 是"退出过程"。
  之前本工程用"把循环变量推到末尾"来跳出，那是绕路。
  另一个更稳的写法：**局部 BOOL + 走完整圈**（本项目 `StartCampRest` 就是），
  不依赖任何语义细节，而且不会有"借全局变量传结果"那种脆性。
- ★ **局部变量名会撞"内建名"和"函数名"** -> `parse error, expecting NEWSYM or VAR_REF`
  （报在**声明那一行**）。已踩：`night` / `phase` / `surge` / `post` / **`isWreck`（撞函数
  `IsWreck()`）** —— SanScript **大小写不敏感**，所以 `isWreck` 与 `IsWreck` 是同一个名字。
  命名避开英文常用词，必要时换个说法（`wreckFix` 这种）。
- **枚举/签名与 PC 不同，必须查 `.sch`**：无 `CA_CAN_ATTACK_FRIENDLY`（可用
  `CA_ALWAYS_FIGHT=5`、`CA_AGGRESSIVE=13`、`CA_CAN_INVESTIGATE=14`，后者 =FALSE 让警察
  不查枪声）；`SET_ENTITY_COORDS(e, VECTOR, DoDeadCheck, KeepTasks, KeepIK, DoWarp)`；
  `CREATE_OBJECT(m, VECTOR, RegNet, ScriptHost, ForceToBeObject=TRUE 才不可推)`；
  `GET_HEADING_FROM_VECTOR_2D(FLOAT, FLOAT)`；车门锁 `VEHICLELOCK_*`。

## 任务与 AI（踩坑最多）
- ★ **铁律：`TASK_*` 只在"状态切换"时下，绝不在维护循环里周期重下**（任何 `TASK_*` 都会
  顶掉引擎的跟随/移动任务）。踩过两次：护卫每 1s、幸存者每 0.5s 重下 combat → **走路一走一停**。
  正确分工：跟随交群组 AI + **显式长效跟随任务**，交战交关系组 + `CA_ALWAYS_FIGHT`。
- ★ **"挂进群组"≠"会跟随"**：`SET_PED_AS_GROUP_MEMBER` 不够 —— 分离范围设得极大时引擎认为
  "已在位"，**不给移动任务** → 招募完定在原地。要**分离范围设很大**（抑制站位微调）
  **+ 显式长效跟随任务**（`TASK_FOLLOW_TO_OFFSET_OF_ENTITY(p, 队长, <<1.2,-2.0,0>>,
  PEDMOVEBLENDRATIO_RUN, -1, 3.0, TRUE)`）+ `SET_PED_KEEP_TASK`。
- ★ **"跟随"任务不会被战斗打断**：要队友既跟随又打仗必须自己分阶段 —— 有敌人
  `TASK_COMBAT_HATED_TARGETS_AROUND_PED` / 敌人走远 重下跟随。**只在阶段变化时重下**
  （滞回 45m 进 / 70m 出）。
- ★★ **"守卫任务"是被动的，"防守一个区域"要用防御区 + 区域索敌**
  `TASK_GUARD_CURRENT_POSITION` / `TASK_STAND_GUARD` 的官方 PARAM NOTES：
  "Will stay within fPositionProximity metres to the given position, **unless attacked**;
  then ped will react ... then will return to given position." —— **不被打就不还手**
  （只会来回巡逻）。要"守着某点并且主动打靠近的敌人"必须自己拼两件套：
  `SET_PED_SPHERE_DEFENSIVE_AREA(p, 坐标, 30.0, TRUE, FALSE)`
  （把作战范围钉住、不许追出圈）+ `TASK_COMBAT_HATED_TARGETS_IN_AREA(p, **坐标**, 35.0)`
  —— 后者的索敌中心是**坐标**而不是 ped 自己，所以他边走边打范围也不漂。
  （`..._AROUND_PED` 是"以 ped 为中心"，会一路追出去，守点场景不能用。）
- ★★ **防御区（defensive area）是 ped AI 的独立状态，`CLEAR_PED_TASKS` 清不掉它**。
  `TASK_GUARD_CURRENT_POSITION(..., bSetDefensiveArea = TRUE)` 会顺手挂一个防御区
  （官方原话："the peds defensive area will be set to the position and radius of
  fGuardAreaRadius"）。**不清它，ped 就会一直自己跑回那个点站着** —— 本项目
  "切回跟随后他还回老位置"的根因。所有"离开该模式"的路径都要
  `REMOVE_PED_DEFENSIVE_AREA(p, FALSE)`（换模式 / 解雇 / 清场）。
  同类"清不掉的独立状态"还有：群组、assigned vehicle、weapon 的当前状态 ——
  切模式时**要把这个模式碰过的所有子系统都还原**，不能只清任务。
- ★ **`TASK_COMBAT_PED` 对"人在车里的目标"无解**：引擎认为打不到 → ped **原地保持敌对姿态**
  不动。必须自己驱动：`TASK_GO_TO_ENTITY(z, 车辆实体, 5000, 1.2, PEDMOVEBLENDRATIO_RUN, 2.0)`
  （2.5s 最多重下），贴到近处自己播攻击动画 + 伤害；**看门狗要跳过"目标在车里"**，
  否则补发的 combat 会顶掉接近任务。（弄下车见"载具"节）
- 入队三动作（Reborn `PedExtended.Recruit`）：先 `REMOVE_PED_FROM_GROUP` + `CLEAR_PED_TASKS`；
  `SET_PED_RAGDOLL_ON_COLLISION(FALSE)` + `SET_PED_CAN_RAGDOLL_FROM_PLAYER_IMPACT(FALSE)`。
- 查 ped 有无任务用 `IS_PED_IN_COMBAT`（世界状态比"自己记账"可靠），但**补任务要去抖**
  （`g_zNudge[]` 2.5s）。
- ★ **"某字段不匹配就丢掉实体"是危险反模式**：曾写"关系组 ≠ 自己人 → 从名单+地图移除"，
  任何组不一致都当场清场（"蓝点闪一下就没了"）。应**先纠偏**（重设关系组），
  只有"死了 / 真变敌人"才移除。
- **状态可逆**：招募/解除必须能还原（关系组 + 地图标记 + 名单 + 任务）；只做
  `REMOVE_PED_FROM_GROUP` + `TASK_WANDER` 会让对象降级成路人，回不去。
- **自己人在 ped 类型上就是普通市民**：`IsProtectedPed()` 含
  `IS_ENTITY_A_MISSION_ENTITY`（自己人会被误判 → 挡在候选外），所以要另写只查家人/
  剧情模型的窄判定 `IsStoryPed`；所有"抓路人"过滤（`InfectNearbyPed`）必须显式排除
  `g_sGroup`，否则盟友会在视线外被同化成僵尸。
- **看门狗/超时必须覆盖所有分支**：追击超时若放在"优先玩家"分支之后，目标是玩家时永不执行
  → 僵尸锁死在够不到的目标上发呆。

## 通用设计原则
- ★ **"瞬间发生"就是 bug**：判定命中的**同一帧**直接改状态 = 玩家看不到任何动作表现。
  改成两段式：先播动作/给反馈，**延迟一段时间**再执行结果（本项目：抓住玩家 → 播抓取动画
  + 把摩托拖慢 → 1.1 秒后才拽下车），期间要能"松脱"（僵尸死 / 玩家下车 / 被甩开 >3.5m）。
- ★★ **"某个功能在该关掉的模式下仍然生效" → 先查那个函数有没有模式门槛**。
  最典型的藏身处是**被无条件调用的"慢速系统汇总入口"**：本项目 `UpdateSlowSystems()`
  里串了十来个子系统，其中 `UpdateThreat()` 漏了 `IF NOT g_infection EXIT` —— 结果
  没开感染模式也会推进平静/紧张阶段、照掷尸潮骰子，弹出"尸潮来袭"而场上没有僵尸。
  排查法：**点开汇总入口里调用的每一个子过程，逐个确认模式门槛**（本项目其余几个
  都有）。这类漏检编译不报错、运行也不报错，只能靠逐个核对。
- ★ **多个子系统跟随"主开关"联动时，初始化必须集中在一处**。本项目把五项开关
  （快速僵尸/幸存者/玩家感染/末日氛围/生存需求）改为随「感染模式」联动，于是原来
  散在各菜单项里的"开启初始化"全部搬到模式边沿 —— 集中之后才容易看出漏了谁
  （第一版就漏了"尸潮压力状态复位"，会导致下次一开模式立刻尸潮）。
- **状态"清零"和状态"复位"要分清**：`g_pressure` 这类在 EXIT 之前累积的标量，
  光加门槛是不够的，还要在关闭路径上**显式清零**，否则残留值会在下次开启时立刻生效。
- ★ **"只在事件开始时推进计时器"是隐形 bug**：事件持续很久才结束时，计时器早已过期
  → 一结束就**立刻**再来一次（观感"太频繁"）。要在**结束时也补一段保底冷却**，
  且用 max 而非覆盖（否则会缩短原本的间隔）。
- ★★ **加"新模式"必须先 grep 所有会把它推翻的既有逻辑**。本项目加"护卫留守"时，
  `UpdateGuards` 里有**两处**会在下一拍撤销它：① "掉队 >25m 就重下跟随任务"
  → 刚下令留守，1 秒后全队被拽回身边；② 每 3 秒"补 combat 任务"（半径 70m）
  → 他们追出老远，留守形同虚设。
  **只加"设置"不加"保护"，功能会被静默撤销。** 做法是让所有下发同一个任务/状态的
  地方都走一个**按模式分流**的入口（本项目 `OrderGuardTask`），并给每处加模式门禁。
- **菜单里改状态、主循环里应用**（脏标志模式）：菜单处理函数（`MenuAdjust`）通常在
  文件很靠前，而新过程的定义要放在它**之前**才能被调用。若不想搬代码，
  就让菜单只置一个 `Dirty` 标志，**主循环（文件最末尾，所有定义之后）下一帧应用** ——
  事件驱动、零轮询成本。

## 生成 / 世界
- ★★ **世界里的"可交互物件"（帐篷/篝火/箱子）要四件套**：
  `CREATE_OBJECT(model, pos, FALSE, TRUE, TRUE)`（ForceToBeObject 必须 TRUE）
  + `SET_ENTITY_DYNAMIC(obj, FALSE)` + `FREEZE_ENTITY_POSITION(obj, TRUE)`
  + **`SET_ENTITY_AS_MISSION_ENTITY(obj, TRUE, TRUE)`**。
  最后那条是防"走远之后被引擎当环境物件回收"（和报废车同一个坑）。
  删除时**先 `SET_ENTITY_AS_MISSION_ENTITY(obj, FALSE, FALSE)` 再 `DELETE_OBJECT`**。
- ★ **"安全区"的完整做法**（本项目营地 35m）要三处一起做，少一处就漏：
  ① 玩家在区内时**整条刷怪链不跑**（和"室内"同一个门禁写法）；
  ② **落点否决** —— 玩家在区外、但落点落在区内时也要拒绝（僵尸/动物/报废车/车队各一处）；
  ③ **区内不再被"新"盯上**（已有追兵照旧留在列表里 —— 安全区是"不再冒新的"，
     不是无敌泡泡；想休息得先把附近清干净）。
- **"跳时间"这类动作走两段式**：`DO_SCREEN_FADE_OUT(700)` → 0.9 秒后 `SET_CLOCK_TIME`
  → `DO_SCREEN_FADE_IN(700)`。直接改时间会"天突然从中午变晚上"，很出戏。
  顺带一个好处：跳时间不会消耗饥饿/口渴（那两者按真实秒走）。
- ★★ **`GET_GROUND_Z_FOR_3D_COORD` 返回的是"最高那一层地面"** —— 楼顶、高架、
  桥面在它眼里都是地面。想做到"只在地面生成"必须另找参照：用
  `GET_CLOSEST_VEHICLE_NODE(点, &节点坐标, NF_NONE, 1.0, 1.0)` 取**最近的车行节点**
  （只存在于街道 / 车道 / 车库通道，**永远不会在楼顶**），落点与它高差 >2.5m 即否决；
  查不到节点（深山 / 荒地）时退化为"与玩家高差 ≤6m"的严格版。
  ★ 参数细节：`zMeasureMult=1.0, zTolerance=1.0` 才让它偏好**同层**节点；
  落点起点**别用"地面 + 30"**（会把节点搜索引到很远那个街区），要用玩家高度。
- ★★ **室内判定用 `GET_INTERIOR_FROM_ENTITY(ped) != NULL`**（官方惯用写法：
  `AM_CONTACT_REQUESTS.sc:633` 等 6 处），落点用 `GET_INTERIOR_AT_COORDS(点) != NULL`。
  想让"室内是安全的"必须**同时**做四件事，少一条就漏：
  ① 落点否决（室内不生成）；② 玩家在室内时**整条刷怪链都别跑** ——
     "感染附近的人"（`InfectNearbyPed`）和"生成僵尸"是**两条独立路径**，只堵一条没用；
  ③ 已有目标要**主动放弃**（否则僵尸继续挤在门口）；
  ④ 目标筛选里排除"在室内的人"（`IsZombieTarget` / `InfectNearbyPed` 都要）。
- ★ **让实体"自然出现"**：生成前用 `IS_SPHERE_VISIBLE(坐标, 半径)` 做**视野否决**
  （在视野里就换角度重试，最多 6 次）+ 方向偏到**身后/侧后** + 距离下限 30m。
  只做"前方偏置"而无视野判定 = 在你眼前凭空出现（需 `USING "commands_camera.sch"`）。
  实体死亡腾出上限后**延后几秒**再补刷（否则"打死一只立刻又冒一只"）。
- ★ **全局 VECTOR 默认 (0,0,0)，而那是真实坐标**（洛圣都中部）：凡"数组存坐标 + 用距离判断
  有效性"必须初始化成哨兵值（`(0,0,-9000)`）或另配 `BOOL Valid[i]`。踩过两次。

- ★★ **"随机方向 + 视锥否决"叠在一起 = 通过率极低（踩过一次）**
  想让实体"别凭空出现在玩家眼前"，加 `IS_SPHERE_VISIBLE` 否决是**对的**；
  但如果落点方向是**纯随机角度**（`GET_RANDOM_FLOAT_IN_RANGE(0, 360)`），
  在城市街道上就会连续失败：35~150m 内的道路节点**几乎全在你正看着的那条街上**。
  现象是"逛了很久一个都没生成"，而代码看起来完全正常。
  **正确做法：方向也要偏置** —— 和刷怪一样用"优先身后/侧后"
  （本项目：70% 落在 `heading + 120°~240°`，其余两侧后方），再叠视锥否决。
  另外**兜底路径要有**：随机尝试全部失败时，沿玩家正后方固定距离（本项目 55~95m）
  找一个节点强行生成，不再要求"不可见" —— 那个距离属于余光之外，
  即使被看到也只是远处停着的东西，观感远好于"从来刷不出来"。
- ★ **刷怪成功的"可观测性"**：给刷不出来这类问题留一条确定性路径（兜底），
  否则玩家反馈"没有"时，你无法区分**没生成 / 生成了被清掉 / 生成在看不见的地方**。

## 载具
- ★ **"开不走但能进能坐"用 `SET_VEHICLE_UNDRIVEABLE(v, TRUE)`** —— 注意拼写是
  **UNDRIVEABLE**（不是 UNDRIVABLE），照正常拼写去查会查不到。官方注释：
  *Sets the vehicle to be undriveable (but still enterable)*；R\* 自己在
  `re_crashrescue.sc` / `Towing.sc` 里就用它标记事故车 / 故障车。
- ★ **`SET_VEHICLE_ENGINE_HEALTH` 的数值语义**：`1000 = 完好`、**`0 = 着火`**、
  `-1000 = 烧毁`。所以"发动机报废但别烧掉"光设负值不够 —— 必须同时
  `SET_ENTITY_PROOFS(v, FALSE, TRUE, FALSE, FALSE, FALSE)`（第 2 个参数 = 火免）。
  烧掉的车会**直接消失**，做"可修复的报废车"时这条是必要条件。
- 视觉受损配方（照官方 `re_crashrescue.sc`）：`SET_VEHICLE_TYRE_BURST`（爆胎，看得见）、
  `SMASH_VEHICLE_WINDOW` + `POP_OUT_VEHICLE_WINDSCREEN`、
  `SET_VEHICLE_DAMAGE(v, 世界坐标, 伤害, 形变, FALSE)`（**只有它产生形变**）、
  `SET_VEHICLE_DIRT_LEVEL`。修好时对称地 `SET_VEHICLE_UNDRIVEABLE(FALSE)` +
  `SET_VEHICLE_FIXED` + `SET_VEHICLE_TYRE_FIXED`（每个轮子一次）+ proofs 设回全 FALSE。
- ★ **"看得见的车损"必须用 `SET_VEHICLE_DAMAGE`**：改 `BODY_HEALTH`/`ENGINE_HEALTH` 只是
  **数值**（车没有血条）且**不产生形变**，玩家什么都看不见。凹陷/掉漆/冒烟用
  `SET_VEHICLE_DAMAGE(veh, VECTOR, 伤害, 形变, FALSE)`；砸窗 `SMASH_VEHICLE_WINDOW(veh, SC_WINDOW_LIST 0..5)`。
- ★★ **"把骑手从车上弄下来"用引擎自带接口**（R\* 官方 `Paparazzo1.sc` 的 KBAS_SUCCESSFUL_ATTACK）：
  `CAN_KNOCK_PED_OFF_VEHICLE(ped)` → `KNOCK_PED_OFF_VEHICLE(ped)` →
  `APPLY_FORCE_TO_ENTITY(ped, APPLY_TYPE_IMPULSE, <<2.0,0,-4.0>>, <<0.0,0.21,0.0>>,
  GET_PED_RAGDOLL_BONE_INDEX(ped, RAGDOLL_HEAD), TRUE, TRUE, TRUE)`。
  引擎自己处理"离车→翻滚落地"，比硬设 ragdoll 自然。配套
  `SET_PED_CAN_BE_KNOCKED_OFF_VEHICLE(ped, KNOCKOFFVEHICLE_NEVER/EASY/DEFAULT)`：
  汽车 NEVER、摩托 EASY（`CAN_KNOCK...` 尊重它），与 `PCF_NO_DRAG_OUT` 双保险。
- **交互判定距离按车型区分**：官方"打下骑手"用 **0.8m**；本项目摩托 1.8m、有车厢的车 4.0m
  （砸车）。**统一 4.0m 会导致"骑车擦身而过就被拽下车"**。车内保护同理按
  `IS_THIS_MODEL_A_BIKE/BICYCLE` 自动判断：有车厢 → 拽不出但被围砸。

- ★★ **脚本创建的车辆必须 `SET_ENTITY_AS_MISSION_ENTITY(v, TRUE, TRUE)`** ——
  这是"街上见不到我放的车"最隐蔽的根因。没有 mission entity 标记的车辆属于
  **环境车**，引擎会在它**离开玩家视野后自行回收**：车确实放下了，
  但你还没走到跟前就被清掉 —— 玩家看到的现象和"从来没生成过"**一模一样**。
  配套三条（缺一条就会出问题）：
  · **不再需要时交还引擎**：`SET_ENTITY_AS_MISSION_ENTITY(v, FALSE, TRUE)` ——
    否则每做一辆就永久多一辆不会被回收的实体（本项目用在"报废车修好之后"）。
  · **对 mission entity 调 `SET_VEHICLE_AS_NO_LONGER_NEEDED` 是无效的** ——
    结果更糟：车还在世界里，但从脚本名单上消失了（变成一辆"永远修不好的废车"）。
    所以**别为了"省资源"对 mission entity 做距离释放**。
  · 真正删除用 `SET_ENTITY_AS_MISSION_ENTITY(v, TRUE, TRUE)` + `DELETE_VEHICLE(v)`
    （`DELETE_VEHICLE` 是强制的，任务实体也删得掉）。
- ★ **模型池和就绪检查必须一一对应**：`RandomWreckModel()` 里有 30% 会挑 `EMPEROR`，
  而 `g_wreckReady` 的就绪检查只查了 ASEA / PREMIER / RUMPO —— 那 30% 的尝试
  每次都因为 `HAS_MODEL_LOADED` 为假而白费。**加一个模型进池子，就要加一条就绪检查。**

## 物件与营地（世界里的静态物件）
- ★★★ **物件贴地定稿（18 版，已实机验证）：导航面 → 闭环 → 固定偏移。**
  **一句话**：创建前用它取"**人能站的地面**"高度，把它当作 `CREATE_OBJECT` 的坐标
  （那个坐标对应物件**底部**），创建后立刻降温冻结。

  **十二版试错清单（每条都实测过）**：
  · `pc.z + 1.0` → 浮 1 米；· `pc.z` → **只在玩家自己的 x/y 上**是真表面（挪 3 米外地面可低 0.5）；
  · `GET_GROUND_Z_FOR_3D_COORD` → 读**地形网格**，不认人行道/台阶/坡面，误差 1 米量级且符号不定；
  · `GET_ENTITY_HEIGHT_ABOVE_GROUND` / `GET_MODEL_DIMENSIONS` 校正 → 量的是**实体原点**，各模型不同；
  · shape test 射线 → 本端口**零命中**；· 抬高 + `ACTIVATE_PHYSICS` 等落地 → **静态道具叫不醒**；
  · ✅ **`GET_SAFE_COORD_FOR_PED`** → 官方 *"find a safe bit of ground to place a ped"*，
    查**导航网格**并直接返回吸地坐标（R\* 自己在 `re_abandonedcar.sc` 里就这么用）。

  **两个关键语义差**：
  · `CREATE_OBJECT` 的坐标 = 物件**底部**（官方 *"with an offset (from the root the base)"*）；
  · `SET_ENTITY_COORDS` **没有**该偏移（实测请求 73.80 读回 73.80）—— 混用就差一个模型高度。
  · `GET_SAFE_COORD_FOR_PED` 官方提醒**开销不小**，只在需要时调；荒郊取不到就退回 `pc.z`。
  · 诊断读数：`脚底 / 地表 / 帐篷 / 篝火 / 源`（`脚底 − 地表` = 落点地形落差；`源` = 有没有取到）。

  **v0.34 → v0.35：导航面之上还要补三件事**（v0.34 实测"底部确实落在面上"却仍浮 ~0.5 米；
  ★ 待实机确认）。三条都做成**可测的口径**，而不是继续猜公式：
  · **包围盒 = "最低几何"的口径**：`GET_MODEL_DIMENSIONS(mdl, mn, mx)` 给的是模型**局部**
    包围盒 ⇒ 最低几何的世界高度 = `读回 z + mn.z`；`SET_ENTITY_COORDS` **无偏移**（实测
    请求 73.80 读回 73.80）⇒ 能按差值精确平移，两轮收敛。
    ★ 单次修正 > 1 米就**放弃不动**（防一头扎进地里）。
  · **玩家脚下 = 活标尺**：玩家正站在真实地面上，`pc.z` 是场景里**唯一**一块确知的地面高度；
    在 `(pc.x, pc.y)` 也取一次导航面 ⇒ `量到的 − pc.z` = 导航面的**系统偏差**，减掉它。
    只接受 0.02~1.0 的**正值**（负值会把营地往上抬）。
  · **返回点的横向距离必须查**：导航面会把点**吸到最近的多边形**上；只取 z、把 x/y 丢掉的话，
    那个 z 可能根本不是脚下那点的 —— 用 `VDIST2`（两个点都压到 z=0）量水平距离，
    诊断 `源 = 2` 就是"吸走 >1.5 米"。

  **签名 + 一个容易漏的参数**：
  `GET_SAFE_COORD_FOR_PED(VECTOR VecCoors, BOOL bOnlyOnPavement, VECTOR &out, GET_SAFE_COORD_FLAGS iFlags=默认)`
  · flags：`ONLY_PAVEMENT`=1 / `NOT_ISOLATED`=2 / `NOT_INTERIOR`=4 / `NOT_WATER`=8 /
    `ONLY_NETWORK_SPAWN`=16 / `USE_FLOOD_FILL`=32；
  · ★ 官方 INFO：**失败时返回 FALSE，且返回坐标 = 传入坐标**（兜底可以直接用传进去的那个）；
  · `CREATE_OBJECT`（*with an offset*）/ `CREATE_OBJECT_NO_OFFSET`（*no offset*）是一对，
    官方 PURPOSE 行各自写明了语义。
  · ★ 教训：**一个综合数字不够用** —— 只打一个读数，出问题时仍然不知道该动哪一边；
    要让**每个假设各有一个独立的读数**（模型底 / 模型高 / 偏差 / 源）。

  **★ v0.36 定稿（第十四版）：三条实测把根因钉死了，"借来的高度"一律不用**
  · `模型底 = -1.53` **正好等于**引擎的创建偏移 ⇒ 引擎认的"底部"**就是**模型最低几何
    （v0.35 那个包围盒修正是空操作：`need = 71.75 - 71.75 = 0`）；
  · `源 = 2` ⇒ 导航面**横向吸走 >1.5 米**，返回的 z 是**别处**的地面。两轮 `帐篷/篝火`
    读数完全一样（73.28/72.07）而 `脚底` 变了 0.26 ⇒ 物件高度**根本没跟着脚下地面走**；
  · `偏差 = 0.00` ⇒ 导航面在**玩家自己那点**是准的（== 脚下高度）—— 坏只坏在"吸走"。
  ⇒ 做法：
    ① 从 `pc.z` 起**往下**试三个探测高度（`refZ` / `-0.8` / `-1.6`），
       **只接受水平距离 ≤ 0.75 米的返回点**（`VDIST2` 两点压平 z）—— 面在正下方才可信；
    ② 都没有就退回 `pc.z`（唯一确知的地面），**不从别处借高度**；
    ③ 摆好后用 `GET_ENTITY_HEIGHT_ABOVE_GROUND` 实测"原点离地"做闭环
       （正确值 == `|模型底|`；浮了往下、埋了往上，限幅 ±0.8 米；读数为 0 或离谱就自动不动）；
    ④ 顺手 `PLACE_OBJECT_ON_GROUND_PROPERLY`（官方 *orient an object to match the terrain*）
       —— 万一"浮"其实是斜坡上物件不贴坡；它不动高度，所以不干扰精确摆位
       （兜底：真被挪走了就用 `原点 = 面 - 模型底` 摆回来，x/y 用传进去的值）。
  · 诊断行口径改为**能和"已知真值"直接相减**：`脚底 / 地表 / 帐篷底 / 篝火底 / 离地 / 修正 / 源`，
    其中"帐篷底 = 读回 z + 模型底"是最低几何的世界高度，和"脚底"同口径。

  **★ v0.37（第十五版）：闭环那条接口是**有效**的，是我的保险上限把修法挡住了**
  · v0.36 实测：四个数是 `71.57`（脚底/地表/帐篷底/篝火底，`源=2` 正确退回了 `pc.z`，
    摆位链路完全按设计工作），而 `离地 = 2.52`、`修正 = 0.00`。
  · `GET_ENTITY_HEIGHT_ABOVE_GROUND` **在本端口有效**：`2.52 - 1.53 = 0.99` =
    帐篷**真实浮空 0.99 米**（1.53 = `|模型底|` = 原点本该离地的高度）。
  · 我没修，是因为单次修正上限被我拍成 **±0.80 米**，`0.99` 被判"离谱" ⇒ **自动不动**。
  · 改成 **±2.0 米 + 两轮收敛（容差 ±0.10）**；保险保留（读数 < 0.35 或 > 4.0 一律不动）。
  · ★★ **通用教训：保险丝的阈值必须来自"实测幅度"**（实测 0.99 → 上限设 1.5~2 倍），
    **不能凭感觉拍一个"看起来安全"的数 —— 否则护栏会把要修的量挡在外面。**
  · ⇒ 结论：`GET_ENTITY_HEIGHT_ABOVE_GROUND` 是**就地、引擎口径**的地面测量；
    两个物件**各自量、各自落**，斜坡上也能各贴各的。别再"从别处借高度"。

  **★ v0.38（第十六版）：闭环成功了、可还是浮 ⇒ 把"人眼基准"交给用户**
  · v0.37 实测：`帐篷底 70.55` **正好等于引擎认的地面**（`离地 1.51 ≈ |模型底| 1.53`、
    `修正 -0.82` 生效）⇒ "把最低几何放到**那个**地面上"已经做到了。
  · 但同一位置四个"地面来源"差近一米：`玩家脚下 71.57` / `导航面(正下方,源1) 71.38` /
    `引擎离地口径 70.55` / `地形网格(±1米,符号不定)` —— **无法互相证伪**。
  · ★ 用户的一句观察排掉了一半可能：**"篝火一直和帐篷一起浮"** ⇒ 两底面同步（实测差 0.14）
    ⇒ **不是模型各自的毛病，是"我们选的那块地面整体偏高"**。
  · ⇒ 菜单加「**营地高度**」：**左 下沉 / 右 抬高**，调它**立刻生效**
    （`ApplyCampSink` 平移物件 + 安全区圆心，不重建不闪）。
    **用户看着调到贴地为止 —— 那就是唯一可信的基准。**
  · ★ v0.39 细化：**拆成两项**（第 10 项「帐篷高度」/ 第 11 项「篝火高度」，
    因为两个模型的 `|模型底|` 本来就不同：帐篷 1.53 / 篝火 0.69），
    步长 **0.25 → 0.01 米**（左右键长按连发 ~7 步/秒，按住 14 秒走 1 米），范围 +0.50 ~ -2.00。
  · ★ 技术点：0.01 一档有 250 档 ⇒ **菜单值不能写死字符串**，要用**数字组件**右对齐画：
    `SET_TEXT_RIGHT_JUSTIFY(TRUE)` + `BEGIN_TEXT_COMMAND_DISPLAY_TEXT("NUMBER")` +
    `ADD_TEXT_COMPONENT_FLOAT(v, 2)`（本工程封装为 `DEC_RIGHT()`）。
  · ★ 菜单项改动的四处：`MENU_COUNT` / `MenuLabel` / `MenuValue` / `MenuAdjust` ——
    少一处 `check_source.py` 的"菜单索引"那节就会报出来（这次就报了）。
  · 实现要点：① 微调必须在**闭环之后**施加（闭环会把偏移拉回去）；
    ② 平移 frozen 物件前 `FREEZE_ENTITY_POSITION(FALSE)`、挪完冻回去；
    ③ 菜单处理在文件前段、应用过程在后段 ⇒ 走"**脏标志 + 主循环执行**"。
  · ★★ 教训：**"用户看到的现象"是最高优先级的测量** —— 比任何 native 读数都硬；
    一条"两个物件表现是否一致"的观察，往往比一整轮仪表读数更能定位问题。
    当客观测量互相矛盾又无法判定时，**把基准交给用户（做成可调），别再猜**。

  **★ v0.40 定稿（第十八版）—— 最终形态与那两个常数**
  · 果冻骨在可调版（v0.39）上调出并实测确认：**帐篷 -0.92 米 / 篝火 -0.61 米**，
    原话"**这样子就正常了，还可以贴着斜坡**"（实测读数：`脚底 73.77 / 地表 73.77 /
    帐篷底 71.89 / 篝火底 72.20 / 离地 0.63 / 修正 -0.97 / 源 2`）。
  · **四步链路（照抄）**：① 落点高度 = 导航面（只在"就在正下方"≤0.75 米时才信）/ 否则玩家脚下
    → ② 创建（坐标 = 物件底部）→ ③ 用 `GET_ENTITY_HEIGHT_ABOVE_GROUND` 闭环压到"引擎认的地面"
    （两轮、容差 ±0.10）→ ④ **减去固定偏移**（帐篷 -0.92 / 篝火 -0.61）。
  · **分工**：闭环管**地形形状**（每个物件就地测 ⇒ 斜坡/台阶/路缘各贴各的）；
    固定偏移管**"引擎口径 vs 人眼表面"那近 1 米的系统性差**（四种来源互相矛盾、无法证伪）。
  · 两个物件**各一个偏移**（差 0.31 米）：两个模型的 `|模型底|` 不同（帐篷 1.53 / 篝火 0.69），
    一个值管两个调不准。
  · ★ 备份：`zombies_nx/backup/v0.39_verified/`（可调版源码 + 已验证产物 + 参数说明），
    以后若要重调，拿它改即可。
  · ★★ **"调出来"比"算出来"可靠**：这个常数是让用户**看着调到贴地**得到的 ——
    凡是"没有可信仪器可测、但人眼能判定"的量，都该走这条路。

  ★ 教训：**同一个坑反复两版后要换"工具"而不是"公式"**；**官方注释的 PURPOSE/PARAM NOTES 行就是规格**
  （这句话本身就是答案）；**能找现成接口就别自己造测量**；**native 存在 ≠ 端口能用**（shape test 零命中）。

- ★★ **通用原则：交付前先问"我看到的现象，能不能被一个模型同时解释完？"**
  这次能收敛，靠的是把七轮现象摆到一张表上、逐个对着模型验证 ——
  只要有一条对不上，模型就还不对。单看最新一次现象很容易一直往错方向查。

- ★ **`PLACE_OBJECT_ON_GROUND_PROPERLY` 在本端口对高度没有可观测效果 —— 别依赖它。**
  官方注释是贴坡度（*"Used to **orient** an object to match the terrain."*）；
  官方脚本里它是和 `CREATE_OBJECT_NO_OFFSET` 配对用的（"给个大概坐标、让引擎贴地"的
  任务箱/降落伞场合）。我们这种"坐标已经算准"的场景不需要它 —— 留着只是多一个变量。
- ★ **`GET_ENTITY_HEIGHT_ABOVE_GROUND` 量的是"实体原点"离地高度，不是底部。**
  各模型原点位置不同（帐篷在中间、篝火在底部），拿它当"底部高度"去做校正，
  必然把一个推对、把另一个推歪 —— v0.25 就是这么把帐篷埋掉一半的。
- ★★ **别指望"抬高 + 打开重力"让道具自己落地 —— 这个端口上物理不接管。**
  v0.27 实测：`SET_ENTITY_DYNAMIC(obj, TRUE)` + 离地 1.4m + 0.9 秒后冻结 → **物件悬空不动**。
- ★ **`SET_ENTITY_PROOFS` 第 4 个参数是"防碰撞"**：需要落地/可推的物件传 FALSE；
  冻结不动的（本项目解药箱）才适合全 TRUE。
- ★ **"安全区"圆心取物件实际摆放后的平面位置**（引擎可能微调过），高度用落点地面高度
  （`VDIST2` 是三维距离，用物件原点高度会让旁边的玩家白算进半米误差）。
- ★★ **这一处最有价值的教训（比上面任何一条都值钱）**
  1. **先去找一句能解释"全部"现象的文档，再考虑自己算。** 四次尝试里三次是
     "我算得比引擎准"，结果都是把它推歪了。
  2. **官方注释里的 `PURPOSE:` 行是规格，不是广告词。** 那句 `CREATE_OBJECT` 注释一直都在，
     我们查它时只看到 "offset" 就绕开了，没把它当"语义定义"去验证。
  3. ★ **观测技巧：多个同类物件里"表现是否一致"是最强的线索。**
     · 一个正常一个不对 ⇒ 差异来自**模型自身**（或某层取决于模型的计算）；
     · 两个一起偏、偏量还一样 ⇒ 那个位移是**我们算出来的常数**。
     只看单个物件，很容易一直往"落点算错"的方向查。
  4. **连着两版都在"猜公式"时，立刻停下来 —— 拿已经工作的同类物件当参照，把差异抹平。**
     这次的正解就是用户那句话："为什么不参考之前成功放置篝火的经验"。
  5. ★ **同一个坑迭代超过两轮，就该换方法 —— 从"猜公式"改成"加测量"或"找参照物"。**
     这次第 3、4 轮仍在猜公式（包围盒、物理落地），其实第 3 轮就该给物件加一行诊断读数，
     用实机数字把"是探测错、还是锚点错"一次分开。
  6. ★ **交付物必须给一个"版本标记"**：产物被补齐到 49152 字节，从 v0.25 起
     **每一版的文件大小完全一样** —— "看文件大小对不对"是无效的确认手段。
     要留一个只有新版才有的可见标记（本次是那行诊断），告诉用户"看到它 = 新版在跑"。
- ★ **多个物件现在共用同一个 z（`pc.z`）**，所以"一个浮一个埋"这类不一致不会再出现。
  （若将来恢复探测，记住：**每个物件要各自探一次** —— 它们不在同一点上。）
- **静态物件的四件套**（缺一条就有后患）：
  ```
  obj = CREATE_OBJECT(model, pos, FALSE, TRUE, TRUE)   // 第 5 参 ForceToBeObject 必须 TRUE
                                                       // 否则被当普通物理道具 -> 一撞就滚
  SET_ENTITY_DYNAMIC(obj, FALSE)
  FREEZE_ENTITY_POSITION(obj, TRUE)
  SET_ENTITY_AS_MISSION_ENTITY(obj, TRUE, TRUE)        // 防"走远之后被引擎回收"
  ```
  删除时**先解除标记再删**：`SET_ENTITY_AS_MISSION_ENTITY(obj, FALSE, FALSE)` → `DELETE_OBJECT(obj)`。
- **"安全区"（营地/避难所）要三处一起做**：① 玩家在区内时整条刷怪链不跑；
  ② 落点否决（玩家在区外但落点落在区内也要拒）；③ 区内不再被"新"盯上。
  ②要对**每一个**生成入口做（僵尸 / 尸潮补波 / 野生动物 / 报废车 / 敌对车队）——
  它们各自独立，不共用同一条路径。
  另加一条**休息前置条件**（本项目：20m 内有僵尸就拒绝休息），
  否则就成了"被围住也能一觉到天亮"的逃课手段。

## 交互与 HUD
- ★ **画进度条要注意 `DRAW_RECT` 用的是中心点坐标**：填充条必须**按长度重算中心**
  （`left + w*0.5`），否则会变成"从中间对称伸缩"而不是"从左边长出来"。
  底槽同理。本项目 `DRAW_BAR()` 是现成实现（`SURVIVAL_HUD` 的饥饿/口渴条）。
- ★ **"看不见的效果"必须给指示**：潜行是"僵尸没发现我"，可它在屏幕上和
  "附近根本没僵尸"长得一模一样 —— 玩家无法确认功能是否生效。
  加一行小字（潜行中 / 枪声暴露）成本极低，收益是"功能可被验证"。
- ★ **同一互动键多个语义时优先级要写死 + 半径分层**。本项目：解药箱 > **招募（2.5m，优先于
  解雇）** > 解雇（1.5m）> 搜刮尸体。解雇半径必须**小于队友跟随站位**（右后 2.33m），
  否则走路一直弹"解除护卫"、手一抖踢人；再加 0.8s 防连按。
- **1Hz 逻辑不能采样按键边沿**（`IS_CONTROL_JUST_PRESSED` 只在按下那帧为真）
  → 每帧采样成标志位，低速逻辑只消费标志位。
- `DISABLE_CONTROL_ACTION` 会让 `IS_CONTROL_PRESSED` 也读不到；被禁过的输入用
  `IS_DISABLED_CONTROL_JUST_PRESSED`，且**只在 PLAYER/CAMERA 组禁，绝不禁 FRONTEND**。
- 改菜单项数时 `MenuLabel`/`MenuValue`/`MenuAdjust`/`MenuActivate` **四处索引同时改**，
  改完按函数范围把索引抽出来核对。
- ★ **HUD 字号一改大，"手写坐标"就会让中文字和数字叠在一起** —— 必须**算字宽再定位**。
  本项目实测比例：`g_cjkW = 0.119`（一个中文字宽 ≈ 0.119 × 字号）、
  `g_digitW = 0.060`（一个半角数字）。位移 = `字数 × 比例 × 字号 + 间距`；
  位数会变的数值（天数 1/2/3 位）还要按位数动态算宽度。
- **可读性三件套**：字号够大（本项目信息行 0.36）、alpha 拉满（250，不是 205）、
  投影加深（235，亮背景下才看得清）。颜色用**高饱和**而不是淡色。
- **HUD 元素布局要自动化检查**（本项目 `check_source.py` 第 5 节）：只看 y 不够 ——
  左下「末日第 N 天」和右下「感染 NN」y 都是 0.958，但 x 相距 0.67 属于并排两块。
  正确规则是"**同一 x 区域内** y 间距 < 0.025 才判为重叠"。
- **信息只在"异常时"显示**（本项目生存三柱只在低于阈值时出现）—— 常驻计数器会毁掉沉浸感，
  用户明确提过两次。唯一例外是"无法靠观察得知、不管就会死"的状态（感染度）。

## 玩家状态 / 通缉 / 声音
- **脚本全局状态必须有"死亡"复位**：复活会换 ped → 用 `PLAYER_PED_ID()` 变化或死→活边沿
  （`CheckPlayerRespawn()`）。**`IS_PLAYER_PLAYING` 在死亡画面为 FALSE**，检测必须放该判断**外面**。
- 无敌检测：`GET_PLAYER_INVINCIBLE` + `GET_ENTITY_CAN_BE_DAMAGED`（覆盖 `SET_ENTITY_INVINCIBLE`）。
- **通缉抑制三件套**：`SET_MAX_WANTED_LEVEL(0)` + `SET_WANTED_LEVEL_MULTIPLIER(0.0)` +
  `SET_POLICE_IGNORE_PLAYER(TRUE)`。只压上限不够（犯罪照样派星）；清星要**每帧**，1Hz 会"闪星"。
- ★ **僵尸"会吼 + 不说人话"定稿**：`SET_AMBIENT_VOICE_NAME(z,"ALIENS")`（换怪物声道，
  RottenV 做法）+ `DISABLE_PED_PAIN_AUDIO(z,FALSE)` + 自己 `ZombieRoar()` 调 `PLAY_PAIN`。
  **不要**用 `PRF_DisableTalk(240)`/`PRF_DisableCombatAudio(262)` 闭嘴 —— 每帧重置且关的是
  **整条发声通道**，会把嘶吼一起封掉（v0.8 哑巴根因）。
- **`PLAY_PAIN` 挑音**：`DYING_MOAN/WHIMPER/EXHAUSTION` 太轻听不见（会误判"僵尸不叫"），
  用 `SCREAM_PANIC(3)/SCREAM_PANIC_SHORT(4)/SCREAM_SCARED(5)/SCREAM_SHOCKED(6)/SCREAM_TERROR(7)`；
  `RawDamage` 别传 0.0。**别紧跟 `PLAY_FACIAL_ANIM`**（共用通道，会掐断音频）。

## 入口脚本（loader / error_listener）

- ★★ **引擎一次只认一个 `REQUEST_SCRIPT_*`**。两个 REQUEST **并排写** ⇒ 第二个被静默忽略；
  **更糟的是若其中一个是"没装进 RPF 的脚本名"，整条请求链都会坏** ⇒
  现象是"文件装了、进游戏完全没反应、连报错都没有"。
  踩过：旧 loader 从实验超英脚本时留下一句 `REQUEST_SCRIPT_WITH_NAME_HASH(HASH("flight"))`。
- **正确写法**（顺序化，一帧里最多一个脚本处于"被请求中"）：
  ```san
  IF GET_NUMBER_OF_THREADS_RUNNING_THE_SCRIPT_WITH_THIS_HASH(H) > 0
      up = TRUE
  ELSE
      up = FALSE
      REQUEST_SCRIPT_WITH_NAME_HASH(H)              // 每帧发一次，幂等
      IF HAS_SCRIPT_WITH_NAME_HASH_LOADED(H)
          IF GET_GAME_TIMER() - lastStart > 1000    // ★ 1 秒节流，必须有
              START_NEW_SCRIPT_WITH_NAME_HASH(H, STACK)
              lastStart = GET_GAME_TIMER()
          ENDIF
      ENDIF
  ENDIF
  ```
  **1 秒节流不能省**：`START_NEW_SCRIPT` 之后线程不是当帧就登记，
  不加节流会在"已加载但线程数仍为 0"的那几帧里反复拉起 ⇒ 同一脚本跑出好几份
  （菜单重复响应、效果翻倍），而且**静默发生**。
- **拉多个模组**：把上面这段**依次排列**，用前一个的就绪标志当前一个的门禁
  （模块1 → m1Up → 模块2 → m2Up → 模块3）。可编译模板见 `zombies_nx/loaders/`。
- **loader 必须能表达自己的状态**：原来"成功时静默、失败时也静默"，于是"没反应"
  完全无从定位。现在"没起来"时常驻橙色告警横幅 ⇒
  **完全没有横幅 = loader 自己没跑（RPF 条目问题）；橙色常驻 = 条目名/文件问题；
  绿色 = 装载正常（按不出菜单就是按键或脚本内部问题）**。
- **栈大小**（`START_NEW_SCRIPT_*` 第二参数）看官方 `core/game/data/stack_sizes.sch`：
  512 MINI / 1424 DEFAULT / 9800 INTERACTION_MENU / 14100 WAREHOUSE / 19400 PROPERTY /
  24000 FMMC_LAUNCHER / 53000 MISSION。模组脚本用 **8032**（TRANSITION_STACK_SIZE）通常够
  （本项目 5300 行 / 193 native 实测可用）。**"栈不够"的症状是脚本启动即死、无任何报错。**
- **产物名 = 源文件名，改不得**：脚本名嵌在产物 0x58（`joaat(名字)`），
  要顶替游戏自带脚本，**源文件名必须完全一致**。

## 叙事时钟与通知
- ★★ **时间推进用 `ADVANCE_CLOCK_TIME_TO`，不要用 `SET_CLOCK_TIME`**。
  官方注释：`ADVANCE_CLOCK_TIME_TO` —— *"if the new time is before the current time
  **a day will pass on the date**"*，也就是"推进到目标时刻，若已过去就顺延一天"，
  正是"睡觉睡到早上 6 点"该有的语义；官方 Family5 任务就是用它干这个的。
  而 `SET_CLOCK_TIME` 是"设成某时刻"，在当前时间已过目标点时会让时钟**倒退**。
  （同族：`ADD_TO_CLOCK_TIME(h,m,s)` 可负可越界，官方在出租车/昏迷场景用。）
- ★★ **"事件跳过了时间"要自己推进日计数，别指望轮询检测跨天**。
  本项目按"游戏内跨过 0 点"检测天数，看起来没问题 —— 但它的成立条件是
  "跳时间之前的钟点 > 目标钟点"：**晚上 9 点睡到早上 6 点会涨一天，凌晨 2 点睡到 6 点
  属于同一个日历日、天数不动**。玩家于是报告"睡了一觉天数没变，像是坏了"。
  正确做法：**在事件里直接 `day + 1`**（睡觉本来就是"结束这一天"的动作），
  再配一个**时钟锁**（几秒内让跨天检测只同步、不判定），否则会被检测逻辑再算一次。
- ★★ **多条通知共用一个显示通道 -> 必须有唯一出口 + 排队**。
  屏幕提示普遍是一组 `msg` + `直到时间` 变量，**后写的那条会把前一条顶掉**。
  本项目实例：跨天广播（立刻播）把刚写好的"睡醒那句话"覆盖了 ——
  玩家只看到广播，不知道自己是睡醒还是被吵醒。
  解法：所有生产者只**登记待播时刻**，播报交给**唯一出口**统一判断
  （时间到了 **且屏幕上没有别的消息** 才播）。顺带把"午夜广播顶掉尸潮提示"也一起修了。
  以后加新提示只要走这个出口，就不会再互相覆盖。

## 生成与校验（v0.42~v0.45：统一口径 / 水里不刷 / 野外减半）

- ★★★ **所有"生成点"必须走同一套口径**（v0.45 审查结论）：僵尸那套修了两版之后，
  **警察巡逻 / 解药箱 / 营地还在用旧口径** —— 警察只用地形查询（山上整队刷不出 + 可能刷海里）、
  解药箱刷在 250~500 米外只用地形查询（几乎必然刷不出）、营地没排除水面（可能搭在水上）。
  ⇒ 改一个生成点就 `grep` 所有生成点一起改。统一口径 =
  **导航面优先（`GSC_FLAG_NOT_WATER`）→ 地形兜底 → `GroundSpotOK()` 全套校验**。
- ★ **水**：`GSC_FLAG_NOT_WATER` + `GET_WATER_HEIGHT_NO_WAVES` 复核（落点要比水面高 0.3m）。
  ☠ 别用含波浪的 `GET_WATER_HEIGHT`（官方：结果随帧变化）。
- ★★ **判"城市/野外"用"有没有人行道导航面"**（`GSC_FLAG_ONLY_PAVEMENT`），
  **别用"最近车行节点距离"**（乡村土路会误判成城里）。本项目野外僵尸上限减半就靠这个判据。
- ⚠️ **校验代码的"顺序"也会出错**：v0.45 我把水源判定插在"取地面之前"，
  那时 `spot.z` 还是探测高度（`pc.z+60`）⇒ 拿它跟水面比等于白判（不会报错，只能靠审查发现）。

## 生成与校验（v0.42~v0.44：山上不刷僵尸，修了两版才对）

- ★★★ **落点的"地面来源"不能只依赖碰撞流式加载**（v0.44 才找到的真因）：
  · 原来只用 `GET_GROUND_Z_FOR_3D_COORD`（读地形网格）取落点高度 ⇒ **野外经常查不到**
    （碰撞还没流式加载好）⇒ 几次尝试全废 ⇒ **一只不刷**；城里流式密集所以从没暴露。
  · ⇒ **先用 `GET_SAFE_COORD_FOR_PED`**（官方：*find a safe bit of ground to place a ped*，
    不依赖碰撞），取不到再退回地形查询。
  · ★ **与玩家高差死守 ±12 米**在山区同样致命（30~130 米外的地形动辄差 15~30 米）⇒ 野外放宽到 ±25。
  · `GroundSpotOK()` 现在**第一步**先算"最近车行节点的水平距离"（>10 米 = 野外），
    再按城里/野外分别选口径。**环境校验必须有"环境类型"分支。**
- ★★ **连续两轮修不对时别再猜，加"只在失败时才出现"的诊断**：本项目 v0.44 加了
  原因码行（连续 3 轮刷不出才显示 `刷不出僵尸：<原因>`，成功一次清零），
  成本极低、直接定位 —— **自限制诊断**比再猜一轮划算。
- ⚠️ **"就地改造环境车"（v0.43）本项目已移除**：技术可行（`GET_CLOSEST_VEHICLE` +
  `SET_VEHICLE_DOOR_OPEN` + `IS_VEHICLE_DOOR_FULLY_OPEN` 幂等），但用户觉得观感粗糙。
  **氛围类小活先出效果给用户看，再谈实现。**

## 生成与校验（v0.42 两个真 bug + 一条通用模式）

- ★★ **"挡楼顶"的校验别对野外生效**（本项目 v0.22 引入、v0.42 才发现）：
  `GroundSpotOK()` 用 `GET_CLOSEST_VEHICLE_NODE` 做道路层校验 —— 城里挡楼顶很好用，
  但**山上的玩家附近往往就是有条山下公路**（节点在几十米外、并低几十米）⇒ 高差远超容差
  ⇒ **那一带所有落点被否** ⇒ "在山上站老久也不刷僵尸"。
  修法：先量横向距离，≤10m 才算"在路边"（严格口径 2.5m），否则按野外口径（±6m）。
  ★ 通用模式：**环境校验必须有"环境类型"分支**（城里/野外、室内/室外），否则某类地形会全灭。
- ★★ **生成实体的落点必须做地面校验**：幸存者原来直接 `pc.z + 0.5` 放人 ⇒
  起伏地形/楼边会生成在半空或楼里 ⇒ 引擎把他落下去/挤出来 + 一声惨叫
  （用户以为是"把僵尸的嚎叫套上去了"）。改为
  `REQUEST_COLLISION_AT_COORD` + `GET_GROUND_Z_FOR_3D_COORD` 落地、并挡
  `GET_INTERIOR_AT_COORDS != NULL`（室内）与高差 >12m（楼顶/深坑）。
- ★ **排查"声音/动画不对"先 grep 机制的调用点**：`SET_AMBIENT_VOICE_NAME("ALIENS")`
  只在 `MakeIntoZombie()`（3 处调用）⇒ 一查就能排除"幸存者被套了僵尸发声通道"，
  把注意力转到落点/物理上，一次命中。

## 路边遗弃车辆（v0.43）：就地改造 + 幂等

- ★ **别凭空生成车**：引擎本来就在路边停放车（停放车密度保 1.0），**就地改造**它们 ——
  车型/朝向/位置都是引擎摆的，不会"四不像"、也不会凭空冒车。
- 找车：`GET_CLOSEST_VEHICLE(pos, radius, DUMMY_MODEL_FOR_SCRIPT, vflags)`，
  flags 至少 `VEHICLE_SEARCH_FLAG_RETURN_RANDOM_VEHICLES`（v0.22 修车那套写法可直接抄）。
  ★★ **它只返回最近一辆** ⇒ 固定从一个点扫会永远只找到同一辆（空转）⇒ **扫描中心要随机取**。
- 过滤三条：`NOT IS_ENTITY_A_MISSION_ENTITY`（别动自家的车）/
  `IS_VEHICLE_STOPPED`（别动在开的车）/ `IS_VEHICLE_SEAT_FREE(v, VS_DRIVER)`（车里有人不碰）。
- 门/窗枚举（**必须查 .sch**）：`SC_DOOR_FRONT_LEFT=0/FRONT_RIGHT=1/REAR_LEFT=2/REAR_RIGHT=3/
  BONNET=4/BOOT=5`；`SC_WINDOW_FRONT_LEFT=0...`；`VS_DRIVER=-1`/`VS_ANY_PASSENGER=-2`。
  `SET_VEHICLE_DOOR_OPEN(v, 门, SwingFree=TRUE, Instant=TRUE)` 出来的是"松垮挂着"的效果。
- ★★ **幂等靠"先查再动"**：`IS_VEHICLE_DOOR_OPEN` **不存在**；用
  `IS_VEHICLE_DOOR_FULLY_OPEN` + `IS_VEHICLE_WINDOW_INTACT` ⇒ 扫到已处理的车不会重复开门、
  更不会反复砸窗出玻璃声（否则玩家会一直听到碎玻璃）。

## 末日环境：车流 / 人流 / 调度（v0.41 定稿）

★ **要不要"人"先想清楚**（v0.41 → v0.42 的教训）：本项目僵尸有一部分来自
`InfectNearbyPed()`（感染街上的人）——**把行人密度清零会顺手废掉那条路径**。
正解：只压车流（`VEHICLE`/`RANDOM`=0），行人留比例（0.4）；
想留路边停放车就单独把 `SET_PARKED_VEHICLE_DENSITY_MULTIPLIER_THIS_FRAME(1.0)` 拉回。
☠ `CLEAR_AREA_OF_VEHICLES` **分不清"在开的车"和路边停放车**，别用它"清街"。

- ★★ **"刷新"和"存量"是两件事**：密度倍率归零只停"新刷"，**已经在街上走/开的不会消失** ——
  「末日里还有人慢悠悠开车」的元凶通常就是这批存量 ⇒ 另外用
  `CLEAR_AREA_OF_PEDS(pos, 130)` / `CLEAR_AREA_OF_VEHICLES(pos, 130, ...)` 清一次。
  · `CLEAR_AREA_OF_VEHICLES` 官方 PURPOSE：*Clears all **non-mission** vehicles*
    ⇒ 我们自己标了 mission entity 的僵尸/护卫/道具安全；玩家自己的车不一定，
    所以**只在玩家没在车里时清车**。
  · 本项目实现在两处：`ApplyEnvironment()`（每帧：密度）+
    `ApplyAmbience()`（模式刚开启时一次性：清场 + 音频/调度）。
- ★★ **环境密度只有 `*_THIS_FRAME` 版本可用**（持久版在官方 .sch 里被注释掉、标着
  *BEING REMOVED SOON*，写了报 `Unrecognized variable or command name`）⇒ **必须每帧重申**；
  好处是"停掉就自动恢复"，不需要还原代码。默认值 1.0。
  想"没车流但保留路边停放车"：主倍率 0 + `SET_PARKED_..._THIS_FRAME(1.0)`。
- ☠ **别用 `SET_PED_PATHS_IN_AREA(..., FALSE)` 清人** —— 它连路网一起停，
  自己的僵尸要寻路追人会跟着瘫。
- ★ **改环境前先 grep 同一个 native**：本项目 `ApplyEnvironment()` 早就设了车流密度
  （行人**故意**留 0.4 给"感染路人"用），两处各设一遍会互相顶掉。
- ⚠️ **`native_lookup.py` 曾把 `//NATIVE`（官方注释掉的废弃接口）当可用** —— 已修为
  "要求行首才算定义"。查签名时若结果可疑，先去 .sch 里看那行有没有 `//`。

## 参考与工具
- ★★ **"某类车/人还在刷"先查调度服务，不是查脚本**：僵尸砸车起火会招来消防车
  （`DT_FIRE_DEPARTMENT`）、满街尸体招来救护车（`DT_AMBULANCE_DEPARTMENT`）。
  光 `TERMINATE_ALL_SCRIPTS_WITH_THIS_NAME("emergencycall")` **挡不住**，必须
  `ENABLE_DISPATCH_SERVICE(DT_XXX, FALSE)`。枚举 `DISPATCH_TYPE` 在
  `core/common/native/commands_misc.sch`（DT_POLICE_AUTOMOBILE / DT_FIRE_DEPARTMENT /
  DT_SWAT_AUTOMOBILE / DT_AMBULANCE_DEPARTMENT / DT_GANGS / DT_ARMY_VEHICLE …）。
- `GTAV Source/script/dev_ng/` —— 官方工具链 + 权威 native 字典；`singleplayer/scripts/` ——
  **R\* 官方脚本源码**（写新机制前先来这找现成做法，例："把骑手打下摩托" = `Paparazzo1.sc`）
- `ZombiesMod Reborn/` —— Reborn v1.0.5f 反编译；`refer/fivem/RottenV-master/` —— FiveM 僵尸服；
  `GTA5NX-MG-Menu-main/` —— 修改器 SDK（含 stock `.nsc`）
- `D:/Tools/GTA dev/…/cheat 9.10/简体/` —— 用户在用的修改器；`.workbuddy/probe_blackout.py` —— 解码 native 表
- ★ **`dev_ng/**/*.sch` = 权威 native 字典**：8558 条声明带 64 位哈希 + 参数签名；实测
  **PC 侧与本端口哈希一致**（ragemenu 352/352），可反查任意 GTA5 二进制的 native 常量。
  解析要**逐行**（`PROC` 无返回类型）—— 代码见 `Downloads/mods/.workbuddy/memory/MEMORY.md` 第 6 节

