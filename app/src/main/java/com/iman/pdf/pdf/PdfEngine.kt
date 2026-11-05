package com.imran.pdf.core.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix as MuMatrix
import com.artifex.mupdf.fitz.Page
import java.io.File

class MuPdfEngine {
    private var document: Document? = null
    private var currentPage: Page? = null

    /**
     * PDF File ko open karne ke liye
     */
    fun openDocument(filePath: String): Boolean {
        return try {
            closeDocument()
            document = Document(filePath)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Total pages nikalne ke liye
     */
    fun getPageCount(): Int {
        return document?.countPages() ?: 0
    }

    /**
     * Specific page ko Bitmap mein render karne ke liye
     * @param pageNumber 0-based index (0 = page 1)
     * @param scale Zoom level (1.0f = 100%)
     */
    fun renderPageToBitmap(pageNumber: Int, scale: Float = 1.0f): Bitmap? {
        val doc = document ?: return null
        if (pageNumber < 0 || pageNumber >= doc.countPages()) return null

        return try {
            // Purane page ko clean karein
            currentPage?.destroy()

            // Naya page load karein
            currentPage = doc.loadPage(pageNumber)
            val page = currentPage ?: return null

            // Page bounds nikaalein
            val bounds = page.bounds
            val matrix = MuMatrix(scale, scale)
            
            // Render pixels nikaalein
            val pixmap = page.toPixmap(
                matrix,
                Color.WHITE, // Background color
                false, // No alpha
                true   // Show annotations
            )

            // Pixmap ko Android Bitmap mein convert karein
            val bitmap = Bitmap.createBitmap(
                pixmap.width,
                pixmap.height,
                Bitmap.Config.ARGB_8888
            )

            // MuPDF pixmap data ko bitmap mein copy karein
            pixmap.toBitmap(bitmap, 0, 0)
            
            // Memory free karein
            pixmap.destroy()
            
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Resources free karne ke liye
     */
    fun closeDocument() {
        currentPage?.destroy()
        currentPage = null
        
        document?.destroy()
        document = null
    }
}
