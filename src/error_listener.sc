// ============================================================================
// error_listener.sc —— 入口脚本（**只加载"脏比洛圣都"** · 生产版）
//
// 【为什么必须是这个名字】
//   游戏只会自动加载它**认识的脚本名**。新增的脚本名不会被自动执行，所以必须
//   借一个「游戏自带脚本」当入口。原版 error_listener 在单机下没有实际作用
//   （560 行里绝大部分被 #IF IS_DEBUG_BUILD 排除，正式构建只做事件缓存），
//   顶替它零副作用。
//
// 【文件名】必须是 error_listener.sc —— 脚本名来自**源文件名**
//   （产物镜像 0x58 处嵌 joaat(脚本名)）。
//
// 编译：python build_nsc.py src/error_listener.sc error_listener
// 安装：两个文件一起放进 script_rel.rpf（**都要是普通压缩文件条目**）
//         error_listener.nsc  <- 顶替原版（先备份）
//         simple_zombies.nsc  <- 新增条目
//
// ============================================================================
// v0.16 重写 —— 修"装进去完全没反应"
//
//   【根因】★ 引擎**一次只认一个脚本请求**，两个 REQUEST 不能并排写。
//     上一版在 SCRIPT 开头连着写了两行：
//         REQUEST_SCRIPT_WITH_NAME_HASH(BODY_HASH)     // simple_zombies
//         REQUEST_SCRIPT_WITH_NAME_HASH(FLIGHT_HASH)   // flight（超英参考脚本，并未安装）
//     第二个请求（一个**根本不存在的脚本**）把整条请求链带坏了 ——
//     表现就是：文件装了、游戏里却完全没反应（连错误横幅都没有）。
//     （这个结论有实机证据：同结构、但"一次只 request 一个"的 loader
//       能正常拉起本模组。）
//
//   【正确写法】**顺序化**：一帧里最多只有一个脚本处于"被请求中"。
//         IF 线程在跑 -> 已就绪
//         ELSE
//             REQUEST（每帧发一次，幂等）
//             IF 已加载 -> START
//         ENDIF
//     要拉多个模组时，就把这段按模块**依次**排列，前一个起来之后再拉下一个
//     （模板见 loaders/error_listener.dual.sc）。
//
//   【另外删掉了 flight 引用】那是从反编译的超英脚本学来的演示脚本，不属于
//     本模组；请求一个没装的脚本正是上一条的祸根。要它请用 dual 模板。
//
//   【栈大小保持 8032】官方 stack_sizes.sch 里这是 TRANSITION_STACK_SIZE。
//     曾有"栈不够导致脚本启动即死"的怀疑，但已被实机证据否定：用 8032 的那个
//     loader 能正常拉起本主体（5300 行 / 115 个过程 / 193 个 native）。
//     万一将来主体大到起不来，再按官方表往上调：
//       9800 INTERACTION_MENU / 14100 WAREHOUSE / 24000 FMMC_LAUNCHER / 53000 MISSION
//
//   【诊断横幅】主体没起来时**一直显示**橙色告警（这是上一轮"没反应"却查不出
//     原因的教训：原来成功时静默、失败时也静默，等于没有信息）。正常时只在
//     就绪后显示 8 秒绿色确认，不长期占屏。横幅位置比主体自己的横幅**高一格**
//     （y≈0.30 vs 0.38），两者同时出现也不会叠字。
//
//   【v0.17 小修】绿色确认的 8 秒**从"玩家可以操作"那一刻才开始计时**。
//     原来一上来就计时，而那时玩家多半还在读盘 —— 所以"有时候看得到有时候看不到"。
//     这个横幅存在的意义就是"永远看得见"，不能开窗过早。
// ============================================================================

USING "rage_builtins.sch"
USING "commands_script.sch"
USING "commands_hud.sch"
USING "commands_graphics.sch"
USING "commands_misc.sch"
USING "commands_player.sch"

CONST_INT BODY_STACK 8032                          // 官方 TRANSITION_STACK_SIZE（见头部说明）
CONST_INT BODY_HASH (HASH("simple_zombies"))       // 必须与 simple_zombies.sc 的源文件名一致

// ---------------------------------------------------------------------------
// 屏幕中上部的横幅（自己画，位置比主体横幅高一格，避免叠字）
// ---------------------------------------------------------------------------
PROC BANNER(STRING title, STRING sub, INT r, INT g, INT b)
    DRAW_RECT(0.500, 0.295, 0.460, 0.092, 6, 8, 12, 205)
    DRAW_RECT(0.500, 0.2545, 0.460, 0.007, r, g, b, 255)

    SET_TEXT_FONT(FONT_STANDARD)
    SET_TEXT_CENTRE(TRUE)
    SET_TEXT_WRAP(0.0, 1.0)
    SET_TEXT_DROPSHADOW(2, 0, 0, 0, 210)

    SET_TEXT_SCALE(0.52, 0.52)
    SET_TEXT_COLOUR(255, 255, 255, 255)
    BEGIN_TEXT_COMMAND_DISPLAY_TEXT("STRING")
    ADD_TEXT_COMPONENT_SUBSTRING_KEYBOARD_DISPLAY(title)
    END_TEXT_COMMAND_DISPLAY_TEXT(0.500, 0.271)

    SET_TEXT_SCALE(0.36, 0.36)
    SET_TEXT_COLOUR(r, g, b, 255)
    BEGIN_TEXT_COMMAND_DISPLAY_TEXT("STRING")
    ADD_TEXT_COMPONENT_SUBSTRING_KEYBOARD_DISPLAY(sub)
    END_TEXT_COMMAND_DISPLAY_TEXT(0.500, 0.301)

    SET_TEXT_CENTRE(FALSE)
ENDPROC

SCRIPT
    NETWORK_SET_SCRIPT_IS_SAFE_FOR_NETWORK_GAME()

    BOOL zUp = FALSE            // 主体线程是否在跑
    BOOL announced = FALSE      // 是否已经播过"已载入"确认
    BOOL playing = FALSE        // 玩家是否已经可以操作
    INT  bannerUntil = 0        // 绿色确认的截止时间
    INT  lastStart = 0          // 上次 START 的时间（防止连续帧重复拉起）

    WHILE TRUE
        WAIT(0)

        // ---- 主体：一次只 request 这一个脚本 ----
        IF GET_NUMBER_OF_THREADS_RUNNING_THE_SCRIPT_WITH_THIS_HASH(BODY_HASH) > 0
            zUp = TRUE
        ELSE
            zUp = FALSE
            REQUEST_SCRIPT_WITH_NAME_HASH(BODY_HASH)
            IF HAS_SCRIPT_WITH_NAME_HASH_LOADED(BODY_HASH)
                // 1 秒节流：防止"已加载但线程还没登记"的那几帧里被重复拉起
                //（重复拉起会让脚本跑出好几份 —— 菜单会重复响应）
                IF GET_GAME_TIMER() - lastStart > 1000
                    START_NEW_SCRIPT_WITH_NAME_HASH(BODY_HASH, BODY_STACK)
                    lastStart = GET_GAME_TIMER()
                ENDIF
            ENDIF
        ENDIF

        // ---- 诊断横幅 ----
        // ★ 绿色确认的计时**从"玩家真的能操作"那一刻才开始**。
        //   原来一上来就计时 8 秒，而那时玩家多半还在读盘 —— 结果"有时候看得到
        //   有时候看不到"。这个横幅存在的意义就是"永远看得见"，所以不能开窗过早。
        IF IS_PLAYER_PLAYING(PLAYER_ID())
            playing = TRUE
        ENDIF

        IF NOT zUp
            // 没起来就**一直**显示：再也不会出现"装了却不知道哪里错"
            BANNER("脏比洛圣都", "主体未启动 - 请检查 simple_zombies.nsc", 255, 140, 90)
        ELSE
            IF NOT announced AND playing
                announced = TRUE
                bannerUntil = GET_GAME_TIMER() + 8000
            ENDIF
            IF GET_GAME_TIMER() < bannerUntil
                BANNER("脏比洛圣都", "已载入 - 按住 RB+B 打开菜单", 90, 235, 140)
            ENDIF
        ENDIF
    ENDWHILE
ENDSCRIPT
