package com.iman.pdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iman.pdf.pdf.LastPage
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LastPageTest {
    private val context by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Before
    fun clearSavedState() {
        context.getSharedPreferences("iman_reading_state", 0).edit().clear().commit()
    }

    @Test
    fun pagePositionIsSavedAndClampedPerDocument() {
        LastPage.save(context, "content://docs/one", 8, 5)
        LastPage.save(context, "content://docs/two", 8, 2)

        assertEquals(5, LastPage.load(context, "content://docs/one", 8))
        assertEquals(2, LastPage.load(context, "content://docs/two", 8))
        assertEquals(2, LastPage.load(context, "content://docs/one", 3))
    }

    @Test
    fun zoomIsSavedAndClampedPerDocument() {
        LastPage.saveZoom(context, "content://docs/one", 2.25f)
        LastPage.saveZoom(context, "content://docs/two", 1.5f)

        assertEquals(2.25f, LastPage.loadZoom(context, "content://docs/one"), 0.001f)
        assertEquals(1.5f, LastPage.loadZoom(context, "content://docs/two"), 0.001f)

        LastPage.saveZoom(context, "content://docs/one", 9f)
        assertEquals(3f, LastPage.loadZoom(context, "content://docs/one"), 0.001f)
    }
}
