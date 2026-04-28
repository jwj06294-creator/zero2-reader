package com.zero2.reader

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class GamepadService : AccessibilityService() {

    private var keyMap = mutableMapOf(
        KeyEvent.KEYCODE_BUTTON_A  to "next_page",
        KeyEvent.KEYCODE_BUTTON_B  to "prev_page",
        KeyEvent.KEYCODE_BUTTON_X  to "confirm",
        KeyEvent.KEYCODE_BUTTON_Y  to "back",
        KeyEvent.KEYCODE_BUTTON_L1 to "prev_page",
        KeyEvent.KEYCODE_BUTTON_R1 to "next_page",
        KeyEvent.KEYCODE_DPAD_UP   to "nav_up",
        KeyEvent.KEYCODE_DPAD_DOWN to "nav_down",
        KeyEvent.KEYCODE_DPAD_LEFT to "nav_left",
        KeyEvent.KEYCODE_DPAD_RIGHT to "nav_right",
        KeyEvent.KEYCODE_BUTTON_1  to "next_page",
        KeyEvent.KEYCODE_BUTTON_2  to "prev_page",
        KeyEvent.KEYCODE_BUTTON_3  to "confirm",
        KeyEvent.KEYCODE_BUTTON_4  to "back",
    )

    private var lastAction = 0L
    private val DEBOUNCE = 250L

    // 서비스 연결 시 플래그 강제 설정 → 부커스에서도 유지
    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = serviceInfo
        info.flags = info.flags or
            AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        serviceInfo = info
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
            "nav_up"    -> sendKey(KeyEvent.KEYCODE_DPAD_UP)
            "nav_down"  -> sendKey(KeyEvent.KEYCODE_DPAD_DOWN)
            "nav_left"  -> sendKey(KeyEvent.KEYCODE_DPAD_LEFT)
            "nav_right" -> sendKey(KeyEvent.KEYCODE_DPAD_RIGHT)
            "confirm"   -> sendKey(KeyEvent.KEYCODE_ENTER)
            "back"      -> performGlobalAction(GLOBAL_ACTION_BACK)
            "home"      -> performGlobalAction(GLOBAL_ACTION_HOME)
            "vol_up"    -> sendKey(KeyEvent.KEYCODE_VOLUME_UP)
            "vol_down"  -> sendKey(KeyEvent.KEYCODE_VOLUME_DOWN)
        }
        return true
    }

    // 실제로 키 이벤트를 전송
    private fun sendKey(keyCode: Int) {
        val path = Path().apply {
            moveTo(0f, 0f)  // 더미 경로 (키 전송용 아님)
        }
        // GestureDescription 대신 performGlobalAction 계열 사용
        val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val up   = KeyEvent(KeyEvent.ACTION_UP,   keyCode)
        rootInActiveWindow?.performAction(0) // 포커스 유지
        Handler(Looper.getMainLooper()).post {
            dispatchGesture(
                GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(
                        Path().apply { moveTo(1f, 1f) }, 0, 1
                    ))
                    .build(),
                null, null
            )
        }
        // 실제 키 전송
        performGlobalAction(when(keyCode) {
            KeyEvent.KEYCODE_DPAD_UP    -> GLOBAL_ACTION_ACCESSIBILITY_BUTTON
            else -> GLOBAL_ACTION_ACCESSIBILITY_BUTTON
        })
    }

    private fun tapScreen(right: Boolean) {
        val display = resources.displayMetrics
        val x = if (right) display.widthPixels * 0.75f
                 else       display.widthPixels * 0.25f
        val y = display.heightPixels * 0.5f
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        dispatchGesture(gesture, null, null)
    }

    // HTML mapper에서 window.Android.saveMapping() 호출 시 반영
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
