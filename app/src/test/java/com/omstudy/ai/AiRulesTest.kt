package com.omstudy.ai

import com.omstudy.ai.data.AiRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AiRulesTest {
    @Test fun httpsAccepted() = assertEquals("https://x.workers.dev", AiRules.normalizeServerUrl(" https://x.workers.dev/ "))
    @Test fun httpRejected() = assertNull(AiRules.normalizeServerUrl("http://x.workers.dev"))
    @Test fun noDotRejected() = assertNull(AiRules.normalizeServerUrl("https://localhost"))
    @Test fun spaceRejected() = assertNull(AiRules.normalizeServerUrl("https://a b.com"))
    @Test fun emptyRejected() = assertNull(AiRules.normalizeServerUrl(""))
    @Test fun trimKeepsLastTenStartingWithUser() {
        val roles = List(14) { if (it % 2 == 0) "user" else "assistant" }
        val from = AiRules.trimHistory(roles)
        assertEquals("user", roles[from])
        assertEquals(true, roles.size - from <= 10)
    }
    @Test fun trimShortHistoryUntouched() = assertEquals(0, AiRules.trimHistory(listOf("user")))
    @Test fun knownCodeFriendly() = assertNotNull(AiRules.friendlyCode("daily_limit"))
    @Test fun unknownCodeStillFriendly() = assertEquals(AiRules.friendlyCode("zzz"), AiRules.friendlyCode("other"))
}
