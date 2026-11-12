package com.cameratowerdefense.farmbot

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.*
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

data class MacroStep(
    val kind: String, val x1: Float, val y1: Float,
    val x2: Float, val y2: Float, val duration: Long
)

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var panel: View? = null
    private lateinit var status: TextView
    private val steps = mutableListOf<MacroStep>()
    private val handler = Handler(Looper.getMainLooper())
    private var playing = false
    private var looping = false
    private var stepIndex = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        loadSteps()
        if (panel == null) showPanel()
        return START_STICKY
    }

    private fun showPanel() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 10, 12, 10)
            background = GradientDrawable().apply {
                setColor(0xEE211B2D.toInt()); cornerRadius = 22f
                setStroke(2, 0xFF8B72C7.toInt())
            }
        }
        val header = TextView(this).apply {
            text = "FarmBot Mobile  •  перетащи"
            setTextColor(Color.WHITE); textSize = 14f; setPadding(8, 8, 8, 8)
        }
        box.addView(header)
        header.setOnTouchListener(DragTouch(wm))
        status = TextView(this).apply {
            text = "Шагов: ${steps.size}"; setTextColor(0xFFE4DDF4.toInt())
            textSize = 12f; setPadding(6, 6, 6, 6)
        }
        box.addView(status)
        addButton(box, "+ Тап в центре экрана") { addStep(MacroStep("tap", .5f, .5f, .5f, .5f, 100)) }
        addButton(box, "+ Поворот камеры (свайп)") {
            // Generic right-side drag; adjust to the game's camera area if needed.
            addStep(MacroStep("camera", .78f, .56f, .43f, .56f, 650))
        }
        addButton(box, "+ Движение (виртуальный джойстик)") {
            // Generic mobile joystick location and forward direction.
            addStep(MacroStep("move", .15f, .82f, .23f, .82f, 1800))
        }
        addButton(box, "▶ Запустить маршрут") { startPlayback(true) }
        addButton(box, "↻ Повторять маршрут") { startPlayback(false) }
        addButton(box, "■ Стоп") { stopPlayback() }
        addButton(box, "Очистить маршрут") {
            stopPlayback(); steps.clear(); saveSteps(); updateStatus("Маршрут очищен")
        }
        addButton(box, "Скрыть панель") { stopSelf() }

        panel = box
        val type = if (android.os.Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 14; y = 110 }
        wm.addView(box, params)
    }

    private fun addButton(parent: LinearLayout, label: String, action: () -> Unit) {
        parent.addView(Button(this).apply {
            text = label
            textSize = 12f
            setOnClickListener { action() }
        })
    }

    private fun addStep(step: MacroStep) {
        steps.add(step)
        saveSteps()
        updateStatus("Добавлено: ${step.kind}. Всего шагов: ${steps.size}")
    }

    private fun updateStatus(text: String) { status.text = text }

    private fun saveSteps() {
        val array = JSONArray()
        steps.forEach {
            array.put(JSONObject().apply {
                put("kind", it.kind); put("x1", it.x1); put("y1", it.y1)
                put("x2", it.x2); put("y2", it.y2); put("duration", it.duration)
            })
        }
        getSharedPreferences("farmbot", MODE_PRIVATE).edit().putString("steps", array.toString()).apply()
    }

    private fun loadSteps() {
        steps.clear()
        val raw = getSharedPreferences("farmbot", MODE_PRIVATE).getString("steps", "[]") ?: "[]"
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                steps.add(MacroStep(o.getString("kind"), o.getDouble("x1").toFloat(),
                    o.getDouble("y1").toFloat(), o.getDouble("x2").toFloat(),
                    o.getDouble("y2").toFloat(), o.getLong("duration")))
            }
        } catch (_: Exception) { steps.clear() }
    }

    private fun startPlayback(repeat: Boolean) {
        if (steps.isEmpty()) { updateStatus("Сначала добавь хотя бы один шаг"); return }
        if (FarmAccessibilityService.instance == null) {
            updateStatus("Сначала включи службу специальных возможностей Android")
            return
        }
        looping = !repeat
        playing = true
        stepIndex = 0
        updateStatus(if (looping) "Повтор маршрута запущен" else "Выполняется маршрут")
        runNext()
    }

    private fun runNext() {
        if (!playing) return
        if (stepIndex >= steps.size) {
            if (looping) stepIndex = 0 else {
                playing = false
                updateStatus("Маршрут завершён")
                return
            }
        }
        val step = steps[stepIndex]
        val service = FarmAccessibilityService.instance
        if (service == null) { stopPlayback(); updateStatus("Служба Android отключена"); return }
        val w = resources.displayMetrics.widthPixels.toFloat()
        val h = resources.displayMetrics.heightPixels.toFloat()
        service.gesture(step.x1 * w, step.y1 * h, step.x2 * w, step.y2 * h, step.duration) { ok ->
            if (!playing) return@gesture
            if (!ok) {
                stopPlayback()
                updateStatus("Жест отменён Android. Проверь разрешения.")
            } else {
                stepIndex++
                handler.postDelayed({ runNext() }, 220L)
            }
        }
    }

    private fun stopPlayback() {
        playing = false
        looping = false
        handler.removeCallbacksAndMessages(null)
        updateStatus("Остановлено")
    }

    override fun onDestroy() {
        stopPlayback()
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        panel = null
        super.onDestroy()
    }

    private class DragTouch(private val wm: WindowManager) : View.OnTouchListener {
        private var sx = 0; private var sy = 0; private var wx = 0; private var wy = 0
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            val lp = v.rootView.layoutParams as? WindowManager.LayoutParams ?: return false
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.rawX.toInt(); sy = e.rawY.toInt(); wx = lp.x; wy = lp.y
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = wx + e.rawX.toInt() - sx
                    lp.y = wy + e.rawY.toInt() - sy
                    wm.updateViewLayout(v.rootView, lp)
                    return true
                }
            }
            return true
        }
    }
}
