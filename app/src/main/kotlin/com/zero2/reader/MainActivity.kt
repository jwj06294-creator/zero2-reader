package com.zero2.reader

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    // WebView에서 window.Android로 호출되는 브릿지
    inner class AndroidBridge {
        @JavascriptInterface
        fun saveMapping(json: String) {
            // SharedPreferences에 저장
            getSharedPreferences("z2map", MODE_PRIVATE)
                .edit()
                .putString("mapping", json)
                .apply()

            // 실행 중인 GamepadService에 반영
            GamepadService.instance?.updateMapping(json)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showMainScreen()
    }

    override fun onResume() {
        super.onResume()
        showMainScreen()
    }

    private fun showMainScreen() {
        val isEnabled = isAccessibilityEnabled()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 100, 60, 60)
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        val appTitle = TextView(this).apply {
            text = "Zero2 Reader"
            textSize = 26f
            setTextColor(0xFF111111.toInt())
            setPadding(0, 0, 0, 8)
        }
        val appSub = TextView(this).apply {
            text = "8BitDo Zero2 · BOOX Palma"
            textSize = 13f
            setTextColor(0xFF888888.toInt())
            setPadding(0, 0, 0, 48)
        }
        val statusIcon = TextView(this).apply {
            text = if (isEnabled) "✅ 접근성 서비스 활성화됨" else "⚠️ 접근성 서비스 비활성화"
            textSize = 16f
            setTextColor(if (isEnabled) 0xFF111111.toInt() else 0xFF888888.toInt())
            setPadding(0, 0, 0, 12)
        }
        val statusDesc = TextView(this).apply {
            text = if (isEnabled)
                "Zero2 버튼이 E북 앱에서 작동합니다.\n부커스/교보도서관을 열고 버튼을 눌러보세요!"
            else
                "아래 버튼을 눌러 접근성 설정에서\nZero2 Reader를 활성화해주세요."
            textSize = 14f
            setTextColor(0xFF555555.toInt())
            setPadding(0, 0, 0, 48)
        }
        val accessibilityBtn = Button(this).apply {
            text = if (isEnabled) "접근성 설정 확인" else "접근성 설정 열기"
            textSize = 15f
            setPadding(0, 24, 0, 24)
            setBackgroundColor(0xFF111111.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        val divider = TextView(this).apply {
            setPadding(0, 32, 0, 32)
        }
        val mappingBtn = Button(this).apply {
            text = "버튼 매핑 설정 →"
            textSize = 15f
            setPadding(0, 24, 0, 24)
            setBackgroundColor(0xFFf4f4f4.toInt())
            setTextColor(0xFF111111.toInt())
            setOnClickListener { showMappingScreen() }
        }
        layout.addView(appTitle)
        layout.addView(appSub)
        layout.addView(statusIcon)
        layout.addView(statusDesc)
        layout.addView(accessibilityBtn)
        layout.addView(divider)
        layout.addView(mappingBtn)
        setContentView(layout)
    }

    private fun showMappingScreen() {
        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            // ✅ 브릿지 연결
            addJavascriptInterface(AndroidBridge(), "Android")
            loadUrl("file:///android_asset/mapper.html")
        }
        setContentView(webView)
    }

    private fun isAccessibilityEnabled(): Boolean {
        val service = "${packageName}/${GamepadService::class.java.canonicalName}"
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(service)
    }
}
