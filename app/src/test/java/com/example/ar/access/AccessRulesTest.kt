package com.example.ar.access

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessRulesTest {

    private val ahora = 1_000_000L

    @Test
    fun `un usuario Pro puede usar aunque no tenga desbloqueo`() {
        assertTrue(AccessRules.canUse(isPro = true, expiryMillis = 0L, nowMillis = ahora))
    }

    @Test
    fun `un desbloqueo vigente permite el acceso`() {
        assertTrue(AccessRules.canUse(isPro = false, expiryMillis = ahora + 1, nowMillis = ahora))
    }

    @Test
    fun `un desbloqueo vencido deniega el acceso`() {
        assertFalse(AccessRules.canUse(isPro = false, expiryMillis = ahora - 1, nowMillis = ahora))
    }

    @Test
    fun `un desbloqueo que vence justo ahora deniega el acceso`() {
        assertFalse(AccessRules.canUse(isPro = false, expiryMillis = ahora, nowMillis = ahora))
    }

    @Test
    fun `sin desbloqueo y sin Pro deniega el acceso`() {
        assertFalse(AccessRules.canUse(isPro = false, expiryMillis = 0L, nowMillis = ahora))
    }

    @Test
    fun `remaining devuelve el tiempo que falta`() {
        assertEquals(5_000L, AccessRules.remaining(expiryMillis = ahora + 5_000L, nowMillis = ahora))
    }

    @Test
    fun `remaining nunca es negativo`() {
        assertEquals(0L, AccessRules.remaining(expiryMillis = ahora - 5_000L, nowMillis = ahora))
    }

    @Test
    fun `la duracion del desbloqueo es de 30 minutos`() {
        assertEquals(30L * 60L * 1000L, AccessRules.UNLOCK_DURATION_MS)
    }
}
