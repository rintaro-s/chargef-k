package dev.chargefuck

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*

class MainActivity : Activity() {
    private val pink = Color.rgb(255, 111, 185)
    private val muted = Color.rgb(168, 155, 177)
    private lateinit var preview: MistView
    private lateinit var start: Button
    private lateinit var state: TextView
    private lateinit var meter: ProgressBar
    private lateinit var heatLabel: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() { updateStatus(); handler.postDelayed(this, 500) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(16, 13, 22)); isFillViewport = true }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(28))
        }
        scroll.addView(body)
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        setContentView(scroll)

        body.addView(label("USELESS LAB     /     EXPERIMENT 001", 10, pink).apply { letterSpacing = 0.15f }, row(bottom = 22))
        body.addView(label("ChargeF**k", 40, Color.WHITE, true))
        body.addView(label("充電に、無駄な感情を。", 16, muted), row(top = 6, bottom = 24))

        val previewCard = FrameLayout(this).apply {
            background = shape(Color.rgb(25, 18, 33), 24, Color.rgb(69, 40, 61))
            clipToOutline = true
        }
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        }
        center.addView(label("ϟ", 72, pink, true).apply { gravity = Gravity.CENTER })
        center.addView(label("抜いて。挿して。ざわついて。", 15, Color.WHITE, true).apply { gravity = Gravity.CENTER })
        center.addView(label("この中でモヤを試せます", 11, muted).apply { gravity = Gravity.CENTER }, row(top = 10))
        previewCard.addView(center, FrameLayout.LayoutParams(-1, -1))
        preview = MistView(this)
        preview.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        previewCard.addView(preview, FrameLayout.LayoutParams(-1, -1))
        val badge = label("●  MIST PREVIEW", 9, pink).apply { setPadding(dp(16), dp(14), 0, 0); letterSpacing = 0.12f }
        previewCard.addView(badge)
        body.addView(previewCard, LinearLayout.LayoutParams(-1, dp(240)))

        val heatRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        heatRow.addView(label("ざわつきゲージ", 11, muted), LinearLayout.LayoutParams(0, -2, 1f))
        heatLabel = label("0%", 12, pink, true)
        heatRow.addView(heatLabel)
        body.addView(heatRow, row(top = 16))
        meter = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(pink)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(49, 35, 54))
        }
        body.addView(meter, LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(7) })
        var lastPercent = -1
        preview.onFrame = {
            val percent = (preview.engine.heat / preview.engine.threshold * 100).toInt().coerceIn(0, 100)
            if (percent != lastPercent) {
                meter.progress = percent
                heatLabel.text = if (preview.engine.finaleStart != null) "PINK OUT!" else "$percent%"
                lastPercent = percent
            }
        }
        val demo = button("ϟ  抜き差しを試す", false) {
            preview.engine.pulse(SystemClock.elapsedRealtime())
        }
        body.addView(demo, row(top = 14))
        body.addView(label("連打すると濃く、激しく。約6回でピンクアウト。", 11, muted), row(top = 8, bottom = 24))

        val guide = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(Color.rgb(28, 23, 35), 18)
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        guide.addView(label("HOW TO PLAY", 10, pink, true).apply { letterSpacing = 0.14f })
        guide.addView(label("01   オーバーレイを許可して、起動\n02   充電ケーブルを抜いたり挿したり\n03   急ぐほどモヤが暴れて、画面がピンクに", 12, Color.rgb(224, 212, 231)).apply {
            setLineSpacing(dp(9).toFloat(), 1f)
        }, row(top = 12))
        body.addView(guide)

        state = label("●  停止中", 12, muted)
        body.addView(state, row(top = 22, bottom = 10))
        start = button("モヤを起動する", true) { begin() }
        body.addView(start)
        body.addView(button("停止してモヤを消す", false) {
            stopService(Intent(this, OverlayService::class.java))
            preview.engine.reset(SystemClock.elapsedRealtime())
            updateStatus()
        }, row(top = 8))
        body.addView(label("全画面演出は3回のピンクの明滅 → ピンクで覆って自然にリセット。通知からもいつでも停止できます。", 11, muted).apply {
            setLineSpacing(dp(3).toFloat(), 1f)
        }, row(top = 18))
        body.addView(label("MADE FOR ABSOLUTELY NO REASON.", 9, Color.rgb(103, 87, 116)).apply {
            gravity = Gravity.CENTER; letterSpacing = 0.1f
        }, row(top = 26))
    }

    private fun begin() {
        if (!Settings.canDrawOverlays(this)) {
            try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
            catch (_: android.content.ActivityNotFoundException) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
        } else launchOverlay()
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code == 7) launchOverlay()
    }

    private fun launchOverlay() {
        try { startForegroundService(Intent(this, OverlayService::class.java)) }
        catch (_: RuntimeException) { Toast.makeText(this, "起動できませんでした。オーバーレイの許可を確認してください。", Toast.LENGTH_LONG).show() }
        handler.postDelayed({ updateStatus() }, 250)
    }

    private fun updateStatus() {
        val running = OverlayService.running
        state.text = when {
            running -> "●  起動中 · ケーブルの抜き差し待ち"
            !Settings.canDrawOverlays(this) -> "○  他のアプリの上に表示する許可が必要です"
            else -> "●  準備OK · 停止中"
        }
        state.setTextColor(if (running) pink else muted)
        start.text = when {
            running -> "モヤは起動中"
            !Settings.canDrawOverlays(this) -> "オーバーレイを許可する ↗"
            else -> "モヤを起動する"
        }
        start.isEnabled = !running
        start.alpha = if (running) 0.55f else 1f
    }

    override fun onResume() { super.onResume(); preview.resume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacksAndMessages(null); preview.pause(); super.onPause() }

    private fun label(value: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    private fun button(value: String, filled: Boolean, action: () -> Unit) = Button(this).apply {
        text = value; textSize = 14f; isAllCaps = false
        setTextColor(if (filled) Color.rgb(35, 12, 31) else pink)
        background = shape(if (filled) pink else Color.rgb(35, 25, 42), 14)
        backgroundTintList = null
        minHeight = dp(52)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        setOnClickListener { action() }
    }
    private fun shape(color: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        stroke?.let { setStroke(dp(1), it) }
    }
    private fun row(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply {
        topMargin = dp(top); bottomMargin = dp(bottom)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
