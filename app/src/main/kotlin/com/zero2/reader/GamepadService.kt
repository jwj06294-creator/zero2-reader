package com.zero2.reader

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.media.AudioManager
import android.os.Build
import android.os.Environment
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class GamepadService : AccessibilityService() {

    companion object {
        var instance: GamepadService? = null
    }

    private var keyMap = mutableMapOf(
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
        KeyEvent.KEYCODE_BUTTON_1     to "next_page",
        KeyEvent.KEYCODE_BUTTON_2     to "prev_page",
        KeyEvent.KEYCODE_BUTTON_3     to "confirm",
        KeyEvent.KEYCODE_BUTTON_4     to "back",
    )

    private var lastAction = 0L
    private val DEBOUNCE = 250L
    private lateinit var audioManager: AudioManager
    private val logList = mutableListOf<JSONObject>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val info = serviceInfo
        info.flags = info.flags or
                AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        serviceInfo = info
        loadSavedMapping()
        applicationContext.getSharedPreferences("z2map", Context.MODE_PRIVATE)
            .edit().putBoolean("service_running", true).apply()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        applicationContext.getSharedPreferences("z2map", Context.MODE_PRIVATE)
            .edit().putBoolean("service_running", false).apply()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    private fun loadSavedMapping() {
        val prefs = applicationContext.getSharedPreferences("z2map", Context.MODE_PRIVATE)
        val json = prefs.getString("mapping", null) ?: return
        try { updateMapping(json) } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            addLog(event.keyCode, KeyEvent.keyCodeToString(event.keyCode))
        }

        if (event.action != KeyEvent.ACTION_DOWN) return false
        val now = System.currentTimeMillis()
        if (now - lastAction < DEBOUNCE) return false
        lastAction = now

        val action = keyMap[event.keyCode] ?: return false
        when (action) {
            "next_page"  -> tapScreen(right = true)
            "prev_page"  -> tapScreen(right = false)
            "nav_up"     -> navigate(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            "nav_down"   -> navigate(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            "nav_left"   -> performGlobalAction(GLOBAL_ACTION_BACK)
            "nav_right"  -> focusNext()
            "confirm"    -> clickFocused()
            "back"       -> performGlobalAction(GLOBAL_ACTION_BACK)
            "home"       -> performGlobalAction(GLOBAL_ACTION_HOME)
            "vol_up"     -> tapVolume(up = true)
            "vol_down"   -> tapVolume(up = false)
            "screenshot" -> takeScreenshot()
        }
        return true
    }

    // ── 스크린샷 ──
    private fun takeScreenshot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val bitmap = Bitmap.wrapHardwareBuffer(
                            result.hardwareBuffer, result.colorSpace
                        )
                        saveBitmap(bitmap)
                        result.hardwareBuffer.close()
                    }
                    override fun onFailure(errorCode: Int) {}
                }
            )
        }
    }

    private fun saveBitmap(bitmap: Bitmap?) {
        bitmap ?: return
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val name = "Zero2_${sdf.format(Date())}.jpg"
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        dir.mkdirs()
        val file = File(dir, name)
        FileOutputStream(file).use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
        }
        // 갤러리에 표시
        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        mediaScanIntent.data = android.net.Uri.fromFile(file)
        applicationContext.sendBroadcast(mediaScanIntent)
    }

    // ── 로그 (JSON 파일로 저장) ──
    private fun addLog(keyCode: Int, keyName: String) {
        val obj = JSONObject().apply {
            put("time", SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()))
            put("keyCode", keyCode)
            put("keyName", keyName)
        }
        logList.add(0, obj)
        if (logList.size > 200) logList.removeLast()
        saveLogFile()
    }

    private fun saveLogFile() {
        try {
            val arr = JSONArray()
            logList.forEach { arr.put(it) }
            val file = File(applicationContext.filesDir, "keylog.json")
            file.writeText(arr.toString(2))
        } catch (e: Exception) { e.printStackTrace() }
    }

    // ── 나머지 동작 ──
    private fun tapScreen(right: Boolean) {
        val display = resources.displayMetrics
        val x = if (right) display.widthPixels * 0.75f else display.widthPixels * 0.25f
        val y = display.heightPixels * 0.5f
        gesture(x, y)
    }

    private fun tapVolume(up: Boolean) {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI
        )
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
        val obj = JSONObject(json)
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
