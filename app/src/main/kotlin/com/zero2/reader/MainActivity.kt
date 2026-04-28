package com.zero2.reader

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 간단한 UI 코드로 직접 생성
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(60, 100, 60, 60)
        }

        val title = TextView(this).apply {
            text = "Zero2 Reader"
            textSize = 24f
            setPadding(0, 0, 0, 20)
        }

        val status = TextView(this).apply {
            text = if (isAccessibilityEnabled())
                "✅ 접근성 서비스 활성화됨\n\n부커스/교보도서관을 열고\nZero2 버튼을 눌러보세요!"
            else
                "⚠️ 접근성 서비스를 활성화해야 합니다"
            textSize = 16f
            setPadding(0, 0, 0, 40)
        }

        val btn = Button(this).apply {
            text = "접근성 설정 열기"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        layout.addView(title)
        layout.addView(status)
        layout.addView(btn)
        setContentView(layout)
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
