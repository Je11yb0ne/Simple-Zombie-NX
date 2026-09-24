// ============================================================================
// error_listener.dual.sc —— 入口脚本模板（**同时拉起两个模组**）
//
// ★ 这个文件**不是**「脏比洛圣都」用的 loader，别直接编译它。
//   生产用的单模组版在 src/error_listener.sc。
//   这里放的是"要和其他模组共存时"的写法，需要时再启封。
//
// 编译（注意：输出名必须是 error_listener，因为它要顶替游戏自带的入口）：
//     1) 把这个文件复制成 src/error_listener.sc（或另建工程）
//     2) python build_nsc.py <路径>/error_listener.sc error_listener
//     3) 把产物 error_listener.nsc + 各模组自己的 .nsc 一起放进 script_rel.rpf
//
// ============================================================================
// 【核心规则】引擎**一次只认一个脚本请求**
//
//   两个 REQUEST 并排写 = 只有一个生效、另一个被静默忽略；
//   若其中一个是**根本不存在的脚本名**，整条请求链都会坏掉 ——
//   现象是"文件装了、游戏里完全没反应，连报错都没有"（我们踩过这个坑）。
//
//   所以必须**顺序化**：
//       IF 该脚本线程在跑          -> 已就绪，跳过
//       ELSE
//           REQUEST（每帧发一次，幂等）
//           IF 已加载 -> START
//       ENDIF
//   并把各模块**依次排列**：前一个起来之后，才轮到下一个。
//   要加第三个模组，就在后面照着复制一段、把它的就绪标志作为门禁。
//
// 【栈大小】官方 core/game/data/stack_sizes.sch 的参考值：
//     MINI 512 / DEFAULT 1424 / INTERACTION_MENU 9800 / WAREHOUSE 14100 /
//     FMMC_LAUNCHER 24000 / MISSION 53000
//   模组脚本一般用 8032（TRANSITION_STACK_SIZE）就够；起不来再往上调。
// ============================================================================

USING "rage_builtins.sch"
USING "commands_script.sch"
USING "commands_hud.sch"
USING "commands_graphics.sch"
USING "commands_misc.sch"

// ---- 模块 1：脏比洛圣都（主体）----
CONST_INT M1_STACK 8032
CONST_INT M1_HASH (HASH("simple_zombies"))

// ---- 模块 2：另一个模组 ----
//   把 "motherlander" 换成那个模组**产物文件名**（也就是它的脚本名）。
CONST_INT M2_STACK 8032
CONST_INT M2_HASH (HASH("motherlander"))

// ---- 模块 3（需要时启封）----
//CONST_INT M3_STACK 8032
//CONST_INT M3_HASH (HASH("第三个模组的脚本名"))

// 屏幕中上部的横幅
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

    BOOL m1Up = FALSE       // 模块 1 起来没
    BOOL m2Up = FALSE       // 模块 2 起来没
    //BOOL m3Up = FALSE
    INT  lastM1 = 0
    INT  lastM2 = 0
    INT  bannerUntil = 0
    BOOL announced = FALSE

    WHILE TRUE
        WAIT(0)

        // ---- 模块 1：无条件拉（一帧里只有它处于"被请求"状态）----
        IF GET_NUMBER_OF_THREADS_RUNNING_THE_SCRIPT_WITH_THIS_HASH(M1_HASH) > 0
            m1Up = TRUE
        ELSE
            m1Up = FALSE
            REQUEST_SCRIPT_WITH_NAME_HASH(M1_HASH)
            IF HAS_SCRIPT_WITH_NAME_HASH_LOADED(M1_HASH)
                IF GET_GAME_TIMER() - lastM1 > 1000
                    START_NEW_SCRIPT_WITH_NAME_HASH(M1_HASH, M1_STACK)
                    lastM1 = GET_GAME_TIMER()
                ENDIF
            ENDIF
        ENDIF

        // ---- 模块 2：**模块 1 起来之后**才拉（门禁 = m1Up）----
        IF m1Up
            IF GET_NUMBER_OF_THREADS_RUNNING_THE_SCRIPT_WITH_THIS_HASH(M2_HASH) > 0
                m2Up = TRUE
            ELSE
                m2Up = FALSE
                REQUEST_SCRIPT_WITH_NAME_HASH(M2_HASH)
                IF HAS_SCRIPT_WITH_NAME_HASH_LOADED(M2_HASH)
                    IF GET_GAME_TIMER() - lastM2 > 1000
                        START_NEW_SCRIPT_WITH_NAME_HASH(M2_HASH, M2_STACK)
                        lastM2 = GET_GAME_TIMER()
                    ENDIF
                ENDIF
            ENDIF
        ENDIF

        // ---- 模块 3：门禁 = m2Up（需要时启封）----
        //IF m2Up
        //    ... 同模块 2 的写法，换成 M3_HASH / M3_STACK / lastM3 / m3Up ...
        //ENDIF

        // ---- 诊断横幅：没起来的模块会一直提示 ----
        IF m1Up AND m2Up
            IF NOT announced
                announced = TRUE
                bannerUntil = GET_GAME_TIMER() + 8000
            ENDIF
            IF GET_GAME_TIMER() < bannerUntil
                BANNER("入口运行中", "两个模组都已载入", 90, 235, 140)
            ENDIF
        ELSE
            IF m1Up
                BANNER("入口运行中", "模组1已载入 · 正在等待模组2", 255, 215, 110)
            ELSE
                BANNER("入口运行中", "正在拉起模组1…", 255, 170, 90)
            ENDIF
        ENDIF
    ENDWHILE
ENDSCRIPT
