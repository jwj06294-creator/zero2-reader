package com.zero2.reader

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class GamepadService : AccessibilityService() {

    private var keyMap = mapOf(
        // Android 모드 KeyCode
        KeyEvent.KEYCODE_BUTTON_A     to "next_page",
        KeyEvent.KEYCODE_BUTTON_B     to "prev_page",
        KeyEvent.KEYCODE_BUTTON_X     to "confirm",
        KeyEvent.KEYCODE_BUTTON_Y     to "back",
        KeyEvent.KEYCODE_BUTTON_L1    to "prev_page",
        KeyEvent.KEYCODE_BUTTON_R1    to "next_page",
        KeyEvent.KEYCODE_DPAD_UP      to "nav_up",
        KeyEvent.KEYCODE_DPAD_DOWN    to "nav_down",
        KeyEvent.KEYCODE_DPAD_LEFT    to "nav_left",
        KeyEvent.KEYCODE_DPAD_RIGHT   to "nav_right",
        // 혹시 다른 KeyCode로 잡힐 경우 대비
        KeyEvent.KEYCODE_BUTTON_1     to "next_page",
        KeyEvent.KEYCODE_BUTTON_2     to "prev_page",
        KeyEvent.KEYCODE_BUTTON_3     to "confirm",
        KeyEvent.KEYCODE_BUTTON_4     to "back",
    )

    private var lastAction = 0L
    private val DEBOUNCE = 250L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        val now = System.currentTimeMillis()
        if (now - lastAction < DEBOUNCE) return false
        lastAction = now

        val action = keyMap[event.keyCode] ?: return false

        when (action) {
            "next_page" -> tapScreen(right = true)
            "prev_page" -> tapScreen(right = false)
            "nav_up"    -> dispatchKey(KeyEvent.KEYCODE_DPAD_UP)
            "nav_down"  -> dispatchKey(KeyEvent.KEYCODE_DPAD_DOWN)
            "nav_left"  -> dispatchKey(KeyEvent.KEYCODE_DPAD_LEFT)
            "nav_right" -> dispatchKey(KeyEvent.KEYCODE_DPAD_RIGHT)
            "confirm"   -> dispatchKey(KeyEvent.KEYCODE_ENTER)
            "back"      -> performGlobalAction(GLOBAL_ACTION_BACK)
        }
        return true
    }

    private fun tapScreen(right: Boolean) {
        val display = resources.displayMetrics
        val x = if (right) display.widthPixels * 0.75f else display.widthPixels * 0.25f
        val y = display.heightPixels * 0.5f

        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun dispatchKey(keyCode: Int) {
        val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val up = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        rootInActiveWindow?.findFocus(
            android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT
        )?.performAction(
            android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS
        )
    }
}
