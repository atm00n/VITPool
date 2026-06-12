package com.vitpool.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthRepositoryTest {
    @Test
    fun normalizeEmailInput_trimsAndLowercasesEmail() {
        assertEquals("student@vit.edu", normalizeEmailInput("  Student@VIT.EDU  "))
    }
}
