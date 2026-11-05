package com.imran.pdf.core.pdf

import android.graphics.Bitmap
import android.graphics.Color
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix as MuMatrix
import com.artifex.mupdf.fitz.Page

class MuPdfEngine {
    private var document: Document? = null
    private var currentPage: Page? = null

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

    fun getPageCount(): Int = document?.countPages() ?: 0

    fun renderPageToBitmap(pageNumber: Int, scale: Float = 1.0f): Bitmap? {
        val doc = document ?: return null
        if (pageNumber < 0 || pageNumber >= doc.countPages()) return null
        return try {
            currentPage?.destroy()
            currentPage = doc.loadPage(pageNumber)
            val page = currentPage ?: return null
            val pixmap = page.toPixmap(MuMatrix(scale, scale), Color.WHITE, false, true)
            val bitmap = Bitmap.createBitmap(pixmap.width, pixmap.height, Bitmap.Config.ARGB_8888)
            pixmap.destroy()
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun closeDocument() {
        currentPage?.destroy()
        currentPage = null
        document?.destroy()
        document = null
    }
}
