package dev.chargefuck

import org.junit.Assert.*
import org.junit.Test

class ChargeEngineTest {
    @Test fun startsWithFaintMistAndNoFinale() {
        val engine = ChargeEngine()
        engine.tick(1000)
        assertEquals(0f, engine.heat, 0f)
        assertNull(engine.finaleStart)
    }

    @Test fun sixQuickChangesTriggerFinale() {
        val engine = ChargeEngine()
        repeat(6) { engine.pulse(1000L + it * 500) }
        assertNotNull(engine.finaleStart)
        assertEquals(1f, engine.kick, 0f)
    }

    @Test fun fasterChangesBuildMoreHeat() {
        val fast = ChargeEngine()
        val slow = ChargeEngine()
        repeat(4) {
            fast.pulse(1000L + it * 400)
            slow.pulse(1000L + it * 3000)
        }
        assertTrue(fast.heat > slow.heat)
        assertNull(slow.finaleStart)
    }

    @Test fun idleHeatAndKickDecay() {
        val engine = ChargeEngine()
        engine.pulse(1000)
        engine.tick(6000)
        assertEquals(0f, engine.heat, 0f)
        assertTrue(engine.kick < 0.01f)
    }

    @Test fun duplicateBroadcastDoesNotAddHeat() {
        val engine = ChargeEngine()
        engine.pulse(1000)
        engine.pulse(1000)
        assertEquals(1f, engine.heat, 0f)
        engine.pulse(1050)
        assertTrue(engine.heat <= 1f)
    }

    @Test fun finaleIgnoresNewPulsesAndResetsAtEnd() {
        val engine = ChargeEngine()
        repeat(6) { engine.pulse(1000L + it * 500) }
        val start = engine.finaleStart!!
        val heat = engine.heat
        engine.pulse(start + 500)
        assertEquals(start, engine.finaleStart)
        assertEquals(heat, engine.heat, 0f)
        engine.tick(start + ChargeEngine.FINALE_MS - 1)
        assertNotNull(engine.finaleStart)
        engine.tick(start + ChargeEngine.FINALE_MS)
        assertNull(engine.finaleStart)
        assertEquals(0f, engine.heat, 0f)
        engine.pulse(start + ChargeEngine.FINALE_MS + 500)
        assertEquals(1f, engine.heat, 0f)
    }

    @Test fun manualResetClearsEverything() {
        val engine = ChargeEngine()
        engine.pulse(1000)
        engine.reset(1100)
        assertEquals(0f, engine.heat, 0f)
        assertEquals(0f, engine.kick, 0f)
        assertNull(engine.finaleStart)
        engine.pulse(1150)
        assertEquals(1f, engine.heat, 0f)
    }
}
