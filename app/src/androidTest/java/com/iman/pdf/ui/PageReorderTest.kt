package com.iman.pdf.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageReorderTest {

    @Test
    fun movesPageForwardInThumbnailOrder() {
        assertEquals(listOf(1, 3, 4, 2), moveItem(listOf(1, 2, 3, 4), 1, 3))
    }

    @Test
    fun movesPageBackwardInThumbnailOrder() {
        assertEquals(listOf(1, 3, 2, 4), moveItem(listOf(1, 2, 3, 4), 2, 1))
    }

    @Test
    fun invalidOrSameIndexesLeaveOriginalListUntouched() {
        val original = listOf("A", "B", "C")
        assertSame(original, moveItem(original, -1, 1))
        assertSame(original, moveItem(original, 0, 3))
        assertSame(original, moveItem(original, 1, 1))
    }
}
