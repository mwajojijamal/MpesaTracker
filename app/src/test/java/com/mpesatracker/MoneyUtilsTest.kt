package com.mpesatracker

import com.mpesatracker.data.MoneyUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyUtilsTest {

    @Test
    fun `parseKshToCents converts formatted Ksh strings to exact cents`() {
        assertEquals(50000L, MoneyUtils.parseKshToCents("Ksh500.00"))
        assertEquals(125050L, MoneyUtils.parseKshToCents("Ksh 1,250.50"))
        assertEquals(1200L, MoneyUtils.parseKshToCents("12.00"))
        assertEquals(0L, MoneyUtils.parseKshToCents("invalid"))
    }

    @Test
    fun `formatCentsToKsh formats cents into readable Kenyan Shillings`() {
        assertEquals("Ksh 500.00", MoneyUtils.formatCentsToKsh(50000L))
        assertEquals("Ksh 1,250.50", MoneyUtils.formatCentsToKsh(125050L))
    }
}