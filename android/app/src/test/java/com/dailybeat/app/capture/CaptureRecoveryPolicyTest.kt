package com.dailybeat.app.capture

import org.junit.Assert.assertEquals
import org.junit.Test
import com.dailybeat.app.capture.CaptureRecoveryPolicy.Action.*

class CaptureRecoveryPolicyTest {
    @Test fun `no usable fixes trigger recovery after three minutes`() {
        val policy = CaptureRecoveryPolicy(10_000)
        assertEquals(NONE, policy.tick(189_999))
        assertEquals(START_BURST, policy.tick(190_000))
        assertEquals(NONE, policy.tick(190_001))
    }

    @Test fun `indoor outage cannot keep high accuracy running indefinitely`() {
        val policy = CaptureRecoveryPolicy(0)
        assertEquals(START_BURST, policy.tick(180_000))
        assertEquals(NONE, policy.tick(269_999))
        assertEquals(END_BURST, policy.tick(270_000))
        assertEquals(NONE, policy.tick(569_999))
        assertEquals(START_BURST, policy.tick(570_000))
    }

    @Test fun `fresh accepted fix ends the recovery burst early`() {
        val policy = CaptureRecoveryPolicy(0)
        assertEquals(START_BURST, policy.tick(180_000))
        policy.accepted(185_000)
        assertEquals(END_BURST, policy.tick(195_000))
        assertEquals(NONE, policy.tick(400_000))
    }

    @Test fun `regular accepted fixes avoid recovery`() {
        val policy = CaptureRecoveryPolicy(0)
        for (now in 90_000L..3_600_000L step 90_000L) {
            policy.accepted(now)
            assertEquals(NONE, policy.tick(now))
        }
    }

    @Test fun `replayed historical fixes do not disguise an ongoing outage`() {
        val policy = CaptureRecoveryPolicy(1_000_000)
        policy.accepted(800_000)
        assertEquals(START_BURST, policy.tick(1_180_000))
        policy.accepted(900_000)
        assertEquals(NONE, policy.tick(1_195_000))
        assertEquals(END_BURST, policy.tick(1_270_000))
    }
}
