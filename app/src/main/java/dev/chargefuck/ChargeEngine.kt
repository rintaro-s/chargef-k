package dev.chargefuck

import kotlin.math.exp

/** All time values use a monotonic clock. No Android dependency. */
class ChargeEngine {
    var heat = 0f
        private set
    var kick = 0f
        private set
    var finaleStart: Long? = null
        private set
    private var lastTick: Long? = null
    private var lastEvent: Long? = null
    val threshold = 6f

    fun tick(now: Long) {
        val dt = ((now - (lastTick ?: now)).coerceAtLeast(0)) / 1000f
        lastTick = now
        kick *= exp(-dt * 5.5f)
        if (finaleStart != null) {
            if (now - finaleStart!! >= FINALE_MS) reset(now)
        } else {
            heat = (heat - dt * 0.30f).coerceAtLeast(0f)
        }
    }

    fun pulse(now: Long) {
        tick(now)
        if (finaleStart != null) return
        val gap = lastEvent?.let { now - it } ?: Long.MAX_VALUE
        // Reject duplicate delivery; real changes of the charging state are also filtered by the service.
        if (gap < 120) return
        lastEvent = now
        heat += if (gap < 1400) 1.35f else 1f
        kick = 1f
        if (heat >= threshold) finaleStart = now
    }

    fun reset(now: Long) {
        heat = 0f
        kick = 0f
        finaleStart = null
        lastEvent = null
        lastTick = now
    }

    companion object { const val FINALE_MS = 6500L }
}
