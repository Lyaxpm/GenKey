package com.gen.keymapper

import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Satu ruang kode untuk semua pemicu:
 *  - kode >= 0 : KeyEvent.KEYCODE_* (keyboard / gamepad)
 *  - kode < 0  : tombol mouse atau scroll wheel (didefinisikan di sini)
 */
object KeyNames {
    const val NONE = -1

    const val WHEEL_UP = -1001
    const val WHEEL_DOWN = -1002
    const val WHEEL_LEFT = -1003
    const val WHEEL_RIGHT = -1004

    const val MOUSE_LEFT = -2001
    const val MOUSE_RIGHT = -2002
    const val MOUSE_MIDDLE = -2003
    const val MOUSE_BACK = -2004
    const val MOUSE_FORWARD = -2005

    fun isMouseButton(code: Int) = code in MOUSE_FORWARD..MOUSE_LEFT
    fun isWheel(code: Int) = code in WHEEL_RIGHT..WHEEL_UP
    fun isKeyboard(code: Int) = code >= 0

    /** MotionEvent.BUTTON_* -> kode kita. */
    fun fromActionButton(button: Int): Int = when (button) {
        MotionEvent.BUTTON_PRIMARY -> MOUSE_LEFT
        MotionEvent.BUTTON_SECONDARY -> MOUSE_RIGHT
        MotionEvent.BUTTON_TERTIARY -> MOUSE_MIDDLE
        MotionEvent.BUTTON_BACK -> MOUSE_BACK
        MotionEvent.BUTTON_FORWARD -> MOUSE_FORWARD
        else -> NONE
    }

    /** Tombol yang tidak boleh dipetakan / dikonsumsi (dibiarkan untuk sistem). */
    fun isSystemKey(code: Int): Boolean = when (code) {
        KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_APP_SWITCH, KeyEvent.KEYCODE_POWER,
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
        KeyEvent.KEYCODE_SLEEP, KeyEvent.KEYCODE_WAKEUP, KeyEvent.KEYCODE_CAMERA,
        KeyEvent.KEYCODE_ASSIST, KeyEvent.KEYCODE_SYSRQ -> true
        else -> false
    }

    fun label(code: Int): String {
        when (code) {
            NONE -> return "—"
            WHEEL_UP -> return "Wheel↑"
            WHEEL_DOWN -> return "Wheel↓"
            WHEEL_LEFT -> return "Wheel←"
            WHEEL_RIGHT -> return "Wheel→"
            MOUSE_LEFT -> return "M-Kiri"
            MOUSE_RIGHT -> return "M-Kanan"
            MOUSE_MIDDLE -> return "M-Tengah"
            MOUSE_BACK -> return "M-Back"
            MOUSE_FORWARD -> return "M-Fwd"
        }
        if (code < 0) return "?"
        if (code in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) return ('A' + (code - KeyEvent.KEYCODE_A)).toString()
        if (code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) return ('0' + (code - KeyEvent.KEYCODE_0)).toString()
        if (code in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12) return "F" + (code - KeyEvent.KEYCODE_F1 + 1)
        return when (code) {
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
            KeyEvent.KEYCODE_BREAK -> "Pause"
            KeyEvent.KEYCODE_BUTTON_A -> "Pad-A"
            KeyEvent.KEYCODE_BUTTON_B -> "Pad-B"
            KeyEvent.KEYCODE_BUTTON_X -> "Pad-X"
            KeyEvent.KEYCODE_BUTTON_Y -> "Pad-Y"
            KeyEvent.KEYCODE_BUTTON_L1 -> "L1"
            KeyEvent.KEYCODE_BUTTON_R1 -> "R1"
            KeyEvent.KEYCODE_BUTTON_L2 -> "L2"
            KeyEvent.KEYCODE_BUTTON_R2 -> "R2"
            KeyEvent.KEYCODE_BUTTON_START -> "Start"
            KeyEvent.KEYCODE_BUTTON_SELECT -> "Select"
            KeyEvent.KEYCODE_BUTTON_THUMBL -> "L3"
            KeyEvent.KEYCODE_BUTTON_THUMBR -> "R3"
            else -> KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").lowercase()
                .replaceFirstChar { it.uppercase() }
        }
    }

    /** Label panjang untuk dialog dan daftar. */
    fun describe(code: Int): String = when {
        code == NONE -> "belum diatur"
        isMouseButton(code) -> "Mouse ${label(code).removePrefix("M-")}"
        isWheel(code) -> when (code) {
            WHEEL_UP -> "Scroll atas"
            WHEEL_DOWN -> "Scroll bawah"
            WHEEL_LEFT -> "Scroll kiri"
            else -> "Scroll kanan"
        }
        else -> "Tombol ${label(code)}"
    }

    /** Pilihan tombol cepat di Pengaturan. */
    val aimKeyChoices: List<Pair<String, Int>> = listOf(
        "Klik tengah mouse (MMB)" to MOUSE_MIDDLE,
        "Tombol samping mouse (Back)" to MOUSE_BACK,
        "Tombol samping mouse (Forward)" to MOUSE_FORWARD,
        "Caps Lock" to KeyEvent.KEYCODE_CAPS_LOCK,
        "Tab" to KeyEvent.KEYCODE_TAB,
        "` (Grave)" to KeyEvent.KEYCODE_GRAVE,
        "F1" to KeyEvent.KEYCODE_F1,
        "F2" to KeyEvent.KEYCODE_F2
    )

    val pauseKeyChoices: List<Pair<String, Int>> = listOf(
        "F8" to KeyEvent.KEYCODE_F8,
        "F9" to KeyEvent.KEYCODE_F9,
        "F10" to KeyEvent.KEYCODE_F10,
        "F12" to KeyEvent.KEYCODE_F12,
        "Insert" to KeyEvent.KEYCODE_INSERT,
        "Pause/Break" to KeyEvent.KEYCODE_BREAK,
        "Scroll Lock" to KeyEvent.KEYCODE_SCROLL_LOCK
    )
}
