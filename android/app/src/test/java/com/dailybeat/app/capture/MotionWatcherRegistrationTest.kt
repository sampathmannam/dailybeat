package com.dailybeat.app.capture

import org.junit.Assert.*
import org.junit.Test

class MotionWatcherRegistrationTest {
    private class Token {
        var removed = false
        var cancelled = false
        lateinit var success: () -> Unit
        lateinit var failure: (Exception) -> Unit
    }

    private class Fixture {
        var allowed = true
        var armed = false
        val tokens = mutableListOf<Token>()
        val failures = mutableListOf<Exception>()
        val registration = MotionWatcherRegistration(
            isAllowed = { allowed },
            setArmed = { armed = it },
            createToken = { Token().also(tokens::add) },
            register = { token, success, failure -> token.success = success; token.failure = failure },
            remove = { it.removed = true },
            cancel = { it.cancelled = true },
            onFailure = failures::add,
        )
    }

    @Test fun `late success after privacy stop cannot arm watcher`() {
        val f = Fixture()
        f.registration.arm()
        val token = f.tokens.single()
        f.registration.disarm()
        token.success()
        assertFalse(f.armed)
        assertTrue(token.removed)
        assertTrue(token.cancelled)
    }

    @Test fun `superseded success does not remove or disarm replacement token`() {
        val f = Fixture()
        f.registration.arm()
        val old = f.tokens.single()
        f.registration.arm()
        val current = f.tokens.last()
        current.success()
        old.success()
        old.failure(IllegalStateException("Late service error"))
        assertTrue(f.armed)
        assertFalse(current.removed)
        assertFalse(current.cancelled)
        assertTrue(old.cancelled)
        assertTrue(f.failures.isEmpty())
    }

    @Test fun `permission or consent revoked during registration is checked on success`() {
        val f = Fixture()
        f.registration.arm()
        f.allowed = false
        f.tokens.single().success()
        assertFalse(f.armed)
        assertTrue(f.tokens.single().cancelled)
    }

    @Test fun `denied registration neither creates a capability nor marks watcher armed`() {
        val f = Fixture()
        f.allowed = false
        f.registration.arm()
        assertFalse(f.armed)
        assertTrue(f.tokens.isEmpty())
    }

    @Test fun `failed registration releases token and reports failure once`() {
        val f = Fixture()
        f.registration.arm()
        val token = f.tokens.single()
        token.failure(IllegalStateException("Unavailable"))
        token.success()
        assertFalse(f.armed)
        assertTrue(token.cancelled)
        assertEquals(1, f.failures.size)
    }
}
