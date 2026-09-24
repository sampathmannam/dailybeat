package com.dailybeat.app.util

import android.app.Application
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class JsonInputSafetyTest {
    @Test
    fun `accepts complete objects and arrays containing ordinary JSON values`() {
        assertTrue(isBoundedJson(""" {"text":"[not nesting] \\" ,"values":[null,true,false,-12.5e2,{},[]]} """))
        assertTrue(isBoundedJson("[]"))
        assertTrue(isBoundedJson("{}", objectOnly = true))
    }

    @Test
    fun `rejects primitive roots and array roots when an object is required`() {
        listOf("", " ", "null", "true", "42", "\"text\"").forEach {
            assertFalse("Unexpected root accepted: $it", isBoundedJson(it))
        }
        assertFalse(isBoundedJson("[]", objectOnly = true))
    }

    @Test
    fun `accepts exactly 64 nested containers and rejects the next level`() {
        assertTrue(isBoundedJson("[".repeat(64) + "0" + "]".repeat(64)))
        assertFalse(isBoundedJson("[".repeat(65) + "0" + "]".repeat(65)))
        assertTrue(isBoundedJson("{\"nested\":" + "[".repeat(63) + "0" + "]".repeat(63) + "}", objectOnly = true))
        assertFalse(isBoundedJson("{\"nested\":" + "[".repeat(64) + "0" + "]".repeat(64) + "}", objectOnly = true))
    }

    @Test
    fun `very deeply nested arrays and objects are rejected without recursive parsing`() {
        assertFalse(isBoundedJson("[".repeat(20_000) + "0" + "]".repeat(20_000)))
        assertFalse(isBoundedJson("{\"n\":".repeat(20_000) + "0" + "}".repeat(20_000)))
    }

    @Test
    fun `strict parsing rejects trailing documents truncation and lenient syntax`() {
        listOf(
            "{}[]", "{} trailing", "{", "[1", "{\"a\":[1}",
            "{'a':1}", "{a:1}", "{\"a\":NaN}", "/*comment*/{}", "[1,]",
        ).forEach {
            assertFalse("Malformed JSON accepted: $it", isBoundedJson(it))
        }
    }

    @Test
    fun `deep looking escaped string content does not count as nesting`() {
        val text = "[".repeat(20_000) + "\\\"}" + "]".repeat(20_000)
        assertTrue(isBoundedJson("{\"text\":\"$text\"}", objectOnly = true))
    }
}
