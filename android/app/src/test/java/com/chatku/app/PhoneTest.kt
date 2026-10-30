package com.chatku.app

import com.chatku.app.util.normalizePhone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneTest {
    @Test fun localNumberGetsCountryCode() = assertEquals("+628123456789", normalizePhone("0812-3456-789", "62"))
    @Test fun internationalKept() = assertEquals("+628123456789", normalizePhone("+62 812 3456 789", "62"))
    @Test fun doubleZeroPrefix() = assertEquals("+628123456789", normalizePhone("00628123456789", "62"))
    @Test fun withoutPlusButCountryCode() = assertEquals("+628123456789", normalizePhone("628123456789", "62"))
    @Test fun invalidReturnsNull() = assertNull(normalizePhone("abc", "62"))
}
