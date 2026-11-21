package dev.chargefuck

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import kotlin.math.*

class MistView(context: Context, val engine: ChargeEngine = ChargeEngine()) : View(context) {
    var onFrame: (() -> Unit)? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cloud = RadialGradient(0f, 0f, 1f,
        intArrayOf(Color.rgb(255, 78, 166), Color.rgb(242, 53, 157), Color.TRANSPARENT),
        floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
    private var awake = true

    fun pause() { awake = false }
    fun resume() { awake = true; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.elapsedRealtime()
        engine.tick(now)
        val level = (engine.heat / engine.threshold).coerceIn(0f, 1f)
        val t = now / 1000.0
        val w = width.toFloat()
        val h = height.toFloat()
        val span = min(w, h)
        val agitation = level * level
        val kick = engine.kick
        paint.shader = cloud
        paint.alpha = ((0.13f + level * 0.51f + kick * 0.28f).coerceAtMost(0.94f) * 255).toInt()
        for (edge in 0..3) {
            for (i in 0..6) {
                val phase = t * (0.65 + agitation * 14) + i * 1.73 + edge * 2.1
                val wave = sin(phase).toFloat()
                val position = (i + 0.5f) / 7f
                val depth = span * (0.10f + level * 0.20f + kick * 0.09f) * (1f + wave * (0.12f + agitation * 0.5f))
                val along = (if (edge < 2) h else w) * position + wave * span * agitation * 0.055f
                val x = when (edge) { 0 -> -depth * 0.2f; 1 -> w + depth * 0.2f; else -> along }
                val y = when (edge) { 2 -> -depth * 0.2f; 3 -> h + depth * 0.2f; else -> along }
                canvas.save()
                canvas.translate(x, y)
                canvas.scale(if (edge < 2) depth else w * 0.32f, if (edge < 2) h * 0.26f else depth)
                canvas.drawCircle(0f, 0f, 1f, paint)
                canvas.restore()
            }
        }
        paint.shader = null
        paint.alpha = 255
        engine.finaleStart?.let { start ->
            val elapsed = now - start
            // Three gentle flashes at 1.25 Hz, then pink floods the entire window.
            val cover = when {
                elapsed < 2400 -> (sin((elapsed % 800) / 800.0 * PI).pow(2) * 0.95).toFloat()
                elapsed < 3000 -> (elapsed - 2400) / 600f
                elapsed < 5000 -> 1f
                else -> (1f - (elapsed - 5000) / 1500f).coerceAtLeast(0f)
            }
            paint.color = Color.argb((cover * 255).toInt(), 255, 89, 177)
            canvas.drawRect(0f, 0f, w, h, paint)
        }
        onFrame?.invoke()
        if (awake && isAttachedToWindow) postInvalidateOnAnimation()
    }
}
