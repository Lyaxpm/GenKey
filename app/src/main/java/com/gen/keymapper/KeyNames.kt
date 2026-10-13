package com.gen.keymapper

import android.view.KeyEvent

/** Label pendek untuk keycode, dipakai di chip editor. */
object KeyNames {
    const val MOUSE_SCROLL_UP = -1001
    const val MOUSE_SCROLL_DOWN = -1002
    fun label(keyCode: Int): String {
        if (keyCode == MOUSE_SCROLL_UP) return "Wheel+"
        if (keyCode == MOUSE_SCROLL_DOWN) return "Wheel-"
        if (keyCode < 0) return "?"
        // A-Z
        if (keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) {
            return ('A' + (keyCode - KeyEvent.KEYCODE_A)).toString()
        }
        // 0-9
        if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            return ('0' + (keyCode - KeyEvent.KEYCODE_0)).toString()
        }
        // F1-F12
        if (keyCode in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12) {
            return "F" + (keyCode - KeyEvent.KEYCODE_F1 + 1)
        }
        // Tombol mouse
        if (keyCode in KeyEvent.KEYCODE_BUTTON_1..KeyEvent.KEYCODE_BUTTON_16) {
            return "M" + (keyCode - KeyEvent.KEYCODE_BUTTON_1 + 1)
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> "Spasi"
            KeyEvent.KEYCODE_ENTER -> "Enter"
            KeyEvent.KEYCODE_TAB -> "Tab"
            KeyEvent.KEYCODE_ESCAPE -> "Esc"
            KeyEvent.KEYCODE_DEL -> "Bksp"
            KeyEvent.KEYCODE_FORWARD_DEL -> "Del"
            KeyEvent.KEYCODE_SHIFT_LEFT -> "ShiftL"
            KeyEvent.KEYCODE_SHIFT_RIGHT -> "ShiftR"
            KeyEvent.KEYCODE_CTRL_LEFT -> "CtrlL"
            KeyEvent.KEYCODE_CTRL_RIGHT -> "CtrlR"
            KeyEvent.KEYCODE_ALT_LEFT -> "AltL"
            KeyEvent.KEYCODE_ALT_RIGHT -> "AltR"
            KeyEvent.KEYCODE_META_LEFT -> "WinL"
            KeyEvent.KEYCODE_META_RIGHT -> "WinR"
            KeyEvent.KEYCODE_CAPS_LOCK -> "Caps"
            KeyEvent.KEYCODE_DPAD_UP -> "▲"
            KeyEvent.KEYCODE_DPAD_DOWN -> "▼"
            KeyEvent.KEYCODE_DPAD_LEFT -> "◀"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "▶"
            KeyEvent.KEYCODE_DPAD_CENTER -> "OK"
            KeyEvent.KEYCODE_BUTTON_A -> "A🎮"
            KeyEvent.KEYCODE_BUTTON_B -> "B🎮"
            KeyEvent.KEYCODE_BUTTON_X -> "X🎮"
            KeyEvent.KEYCODE_BUTTON_Y -> "Y🎮"
            KeyEvent.KEYCODE_BUTTON_L1 -> "L1"
            KeyEvent.KEYCODE_BUTTON_R1 -> "R1"
            KeyEvent.KEYCODE_BUTTON_L2 -> "L2"
            KeyEvent.KEYCODE_BUTTON_R2 -> "R2"
            KeyEvent.KEYCODE_BUTTON_START -> "Start"
            KeyEvent.KEYCODE_BUTTON_SELECT -> "Select"
            KeyEvent.KEYCODE_BUTTON_THUMBL -> "L3"
            KeyEvent.KEYCODE_BUTTON_THUMBR -> "R3"
            KeyEvent.KEYCODE_GRAVE -> "`"
            KeyEvent.KEYCODE_MINUS -> "-"
            KeyEvent.KEYCODE_EQUALS -> "="
            KeyEvent.KEYCODE_LEFT_BRACKET -> "["
            KeyEvent.KEYCODE_RIGHT_BRACKET -> "]"
            KeyEvent.KEYCODE_BACKSLASH -> "\\"
            KeyEvent.KEYCODE_SEMICOLON -> ";"
            KeyEvent.KEYCODE_APOSTROPHE -> "'"
            KeyEvent.KEYCODE_COMMA -> ","
            KeyEvent.KEYCODE_PERIOD -> "."
            KeyEvent.KEYCODE_SLASH -> "/"
            KeyEvent.KEYCODE_PAGE_UP -> "PgUp"
            KeyEvent.KEYCODE_PAGE_DOWN -> "PgDn"
            KeyEvent.KEYCODE_MOVE_HOME -> "Home"
            KeyEvent.KEYCODE_MOVE_END -> "End"
            KeyEvent.KEYCODE_INSERT -> "Ins"
            else -> "K$keyCode"
        }
    }

    /** Label panjang untuk daftar. */
    fun describe(keyCode: Int): String {
        val l = label(keyCode)
        return when {
            keyCode in KeyEvent.KEYCODE_BUTTON_1..KeyEvent.KEYCODE_BUTTON_16 ->
                "Tombol Mouse $l"
            keyCode in KeyEvent.KEYCODE_BUTTON_A..KeyEvent.KEYCODE_BUTTON_MODE ->
                "Tombol Gamepad ($l)"
            else -> "Tombol \"$l\""
        }
    }
}
