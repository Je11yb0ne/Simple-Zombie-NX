// ============================================================================
// _selftest.sc —— 备用自检脚本（已实机验证过能弹消息）
//
// 用途：如果僵尸脚本实机出问题，用它二分定位。
//   本站自检能正常弹 "ZC TEST OK"  -> 加载链路/native/容器都没问题，
//                                     故障在僵尸逻辑里，把现象告诉我即可。
//   本站也弹不出来                  -> 故障在加载层面（rpf 条目/加密/头字段）。
//
// 注意：本文件带下划线前缀，不参与 build.bat 的默认构建。
//       要用它，请先复制成 error_listener.sc（脚本名必须和 RPG 条目名一致，
//       否则产物 0x58 处的 joaat(脚本名) 对不上）：
//           copy src\_selftest.sc src\error_listener.sc
//           build.bat
// ============================================================================

USING "rage_builtins.sch"
USING "commands_hud.sch"
USING "commands_misc.sch"

INT g_next

PROC PING()
    BEGIN_TEXT_COMMAND_THEFEED_POST("STRING")
    ADD_TEXT_COMPONENT_SUBSTRING_PLAYER_NAME("ZC TEST OK - official toolchain works")
    END_TEXT_COMMAND_THEFEED_POST_TICKER(FALSE, TRUE)
ENDPROC

SCRIPT
    g_next = GET_GAME_TIMER() + 5000
    PING()

    WHILE TRUE
        IF GET_GAME_TIMER() > g_next
            PING()
            g_next = GET_GAME_TIMER() + 5000
        ENDIF
        WAIT(0)
    ENDWHILE
ENDSCRIPT
