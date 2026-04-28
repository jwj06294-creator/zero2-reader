package com.zero2.reader

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class GamepadService : AccessibilityService() {

    companion object {
        var instance: GamepadService? = null
    }

    private var keyMap = mutableMapOf(
        KeyEvent.KEYCODE_BUTTON_A   to "next_page",
        KeyEvent.KEYCODE_BUTTON_B   to "prev_page",
        KeyEvent.KEYCODE_BUTTON_X   to "confirm",
        KeyEvent.KEYCODE_BUTTON_Y   to "back",
        KeyEvent.KEYCODE_BUTTON_L1  to "prev_page",
        KeyEvent.KEYCODE_BUTTON_R1  to "next_page",
        KeyEvent.KEYCODE_DPAD_UP    to "nav_up",
        KeyEvent.KEYCODE_DPAD_DOWN  to "nav_down",
        KeyEvent.KEYCODE_DPAD_LEFT  to "nav_left",
        KeyEvent.KEYCODE_DPAD_RIGHT to "nav_right",
        KeyEvent.KEYCODE_BUTTON_1   to "next_page",
        KeyEvent.KEYCODE_BUTTON_2   to "prev_page",
        KeyEvent.KEYCODE_BUTTON_3   to "confirm",
        KeyEvent.KEYCODE_BUTTON_4   to "back",
    )

    private var lastAction = 0L
    private val DEBOUNCE = 250L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val info = serviceInfo
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        serviceInfo = info
        // ✅ 앱 시작 시 저장된 매핑 불러오기
        loadSavedMapping()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    // 저장된 매핑 불러오기
    private fun loadSavedMapping() {
        val prefs = applicationContext.getSharedPreferences("z2map", Context.MODE_PRIVATE)
        val json = prefs.getString("mapping", null) ?: return
        try {
            updateMapping(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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
            "nav_up"    -> navigate(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            "nav_down"  -> navigate(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            "nav_left"  -> performGlobalAction(GLOBAL_ACTION_BACK)
            "nav_right" -> focusNext()
            "confirm"   -> clickFocused()
            "back"      -> performGlobalAction(GLOBAL_ACTION_BACK)
            "home"      -> performGlobalAction(GLOBAL_ACTION_HOME)
            "vol_up"    -> tapVolume(up = true)
            "vol_down"  -> tapVolume(up = false)
        }
        return true
    }

    private fun tapScreen(right: Boolean) {
        val display = resources.displayMetrics
        val x = if (right) display.widthPixels * 0.75f
                 else      display.widthPixels * 0.25f
        val y = display.heightPixels * 0.5f
        gesture(x, y)
    }

    private fun tapVolume(up: Boolean) {
        val root = rootInActiveWindow ?: return
        val action = if (up) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                     else    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        root.performAction(action)
    }

    private fun clickFocused() {
        val root = rootInActiveWindow ?: return
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            ?: root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        focused?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ?: run {
                val d = resources.displayMetrics
                gesture(d.widthPixels * 0.5f, d.heightPixels * 0.5f)
            }
    }

    private fun focusNext() {
        val root = rootInActiveWindow ?: return
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        focused?.performAction(
            AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY,
            android.os.Bundle().apply {
                putInt(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
                    AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE
                )
            }
        ) ?: root.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
    }

    private fun navigate(action: Int) {
        val root = rootInActiveWindow ?: return
        root.performAction(action)
    }

    private fun gesture(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun updateMapping(json: String) {
        val obj = org.json.JSONObject(json)
        val btnToKeyCode = mapOf(
            "a"          to KeyEvent.KEYCODE_BUTTON_A,
            "b"          to KeyEvent.KEYCODE_BUTTON_B,
            "x"          to KeyEvent.KEYCODE_BUTTON_X,
            "y"          to KeyEvent.KEYCODE_BUTTON_Y,
            "l"          to KeyEvent.KEYCODE_BUTTON_L1,
            "r"          to KeyEvent.KEYCODE_BUTTON_R1,
            "dpad_up"    to KeyEvent.KEYCODE_DPAD_UP,
            "dpad_down"  to KeyEvent.KEYCODE_DPAD_DOWN,
            "dpad_left"  to KeyEvent.KEYCODE_DPAD_LEFT,
            "dpad_right" to KeyEvent.KEYCODE_DPAD_RIGHT,
        )
        btnToKeyCode.forEach { (btnId, keyCode) ->
            if (obj.has(btnId)) keyMap[keyCode] = obj.getString(btnId)
        }
    }
}
