package com.iman.pdf.pdf

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class LiquidLoader(private val context: Context, private val file: File) {

    private val mutex = Mutex()
    private var document: PDDocument? = null
    private val stripper = PDFTextStripper()
    private val cache = HashMap<Int, List<String>>()

    suspend fun page(index: Int): List<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val cached = cache[index]
            if (cached != null) {
                cached
            } else {
                var result: List<String> = emptyList()
                try {
                    PDFBoxResourceLoader.init(context.applicationContext)
                    val doc = document ?: PDDocument.load(file).also { document = it }
                    stripper.startPage = index + 1
                    stripper.endPage = index + 1
                    result = reflow(stripper.getText(doc) ?: "")
                } catch (e: Exception) {
                }
                cache[index] = result
                result
            }
        }
    }

    fun close() {
        try {
            document?.close()
        } catch (e: Exception) {
        }
        document = null
    }

    private fun reflow(raw: String): List<String> {
        val lines = raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
            return emptyList()
        }
        val longest = lines.maxOf { it.length }
        val result = ArrayList<String>()
        val current = StringBuilder()
        for (line in lines) {
            if (current.isNotEmpty()) {
                if (current.endsWith("-")) {
                    current.setLength(current.length - 1)
                } else {
                    current.append(' ')
                }
            }
            current.append(line)
            val endsSentence = line.endsWith(".") || line.endsWith("?") ||
                line.endsWith("!") || line.endsWith(":") || line.endsWith("।")
            if (endsSentence && line.length < longest * 0.6) {
                result.add(current.toString())
                current.setLength(0)
            }
        }
        if (current.isNotEmpty()) {
            result.add(current.toString())
        }
        return result
    }
}
