package com.cameratowerdefense.farmbot

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    private lateinit var info: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 24)
            setBackgroundColor(0xFFF5F2FA.toInt())
        }
        val title = TextView(this).apply {
            text = "CAMERA TOWER DEFENSE\\nFARMBOT MOBILE"
            textSize = 23f; gravity = Gravity.CENTER; setTextColor(0xFF241B35.toInt())
        }
        root.addView(title, lp())
        info = TextView(this).apply {
            text = "Android-макросы касаний. Для запуска включи службу специальных возможностей."
            textSize = 15f; setPadding(0, 20, 0, 20)
        }
        root.addView(info, lp())
        addButton(root, "1. Включить службу управления") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        addButton(root, "2. Разрешить плавающее окно") {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            } else {
                Toast.makeText(this, "Разрешение уже выдано", Toast.LENGTH_SHORT).show()
            }
        }
        addButton(root, "Открыть панель FarmBot") {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Сначала разреши плавающее окно", Toast.LENGTH_LONG).show()
            } else {
                startService(Intent(this, OverlayService::class.java))
                info.text = "Панель запущена. Открой игру и настрой точки касаний."
            }
        }
        addButton(root, "Остановить панель") { stopService(Intent(this, OverlayService::class.java)) }
        val note = TextView(this).apply {
            text = "Важно: Android не даёт приложению автоматически читать все касания в другой игре. Поэтому первая версия использует ручную настройку точек и жестов. Автоматизация может быть запрещена правилами конкретной игры."
            textSize = 13f; setPadding(0, 24, 0, 0); setTextColor(0xFF5E5868.toInt())
        }
        root.addView(note, lp())
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun lp() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = 12 }

    private fun addButton(parent: LinearLayout, label: String, action: () -> Unit) {
        val b = Button(this).apply { text = label; setOnClickListener { action() } }
        parent.addView(b, lp())
    }
}
