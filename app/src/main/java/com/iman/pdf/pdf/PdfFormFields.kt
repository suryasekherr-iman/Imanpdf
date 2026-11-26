package com.iman.pdf.pdf

import com.artifex.mupdf.fitz.PDFPage
import com.artifex.mupdf.fitz.PDFWidget
import com.iman.pdf.core.pdf.MuPdfEngine

/** Reads AcroForm widget metadata without changing the source PDF. */
internal object PdfFormFields {
    fun read(engine: MuPdfEngine, pageIndex: Int): List<PdfFormField> =
        synchronized(engine) {
            val document = engine.document() ?: return@synchronized emptyList()
            if (pageIndex !in 0 until engine.pageCount) return@synchronized emptyList()

            var page: PDFPage? = null
            try {
                page = document.loadPage(pageIndex) as? PDFPage
                    ?: return@synchronized emptyList()
                val bounds = page.bounds
                val pageWidth = (bounds.x1 - bounds.x0).coerceAtLeast(1f)
                val pageHeight = (bounds.y1 - bounds.y0).coerceAtLeast(1f)

                page.widgets.orEmpty().mapIndexedNotNull { fieldIndex, widget ->
                    val rect = widget.bounds
                    val left = ((rect.x0 - bounds.x0) / pageWidth).coerceIn(0f, 1f)
                    val top = ((rect.y0 - bounds.y0) / pageHeight).coerceIn(0f, 1f)
                    val right = ((rect.x1 - bounds.x0) / pageWidth).coerceIn(0f, 1f)
                    val bottom = ((rect.y1 - bounds.y0) / pageHeight).coerceIn(0f, 1f)
                    if (right <= left || bottom <= top) return@mapIndexedNotNull null

                    PdfFormField(
                        pageIndex = pageIndex,
                        fieldIndex = fieldIndex,
                        label = widget.label?.takeIf { it.isNotBlank() } ?: "Form field",
                        type = widget.fieldType.asDisplayName(),
                        value = widget.value.orEmpty(),
                        options = widget.options?.toList().orEmpty(),
                        left = left,
                        top = top,
                        right = right,
                        bottom = bottom,
                        readOnly = widget.isReadOnly,
                        required = (widget.fieldFlags and PDFWidget.PDF_FIELD_IS_REQUIRED) != 0
                    )
                }
            } catch (e: Exception) {
                com.iman.pdf.logSwallowed("PdfFormFields:45", e)
                emptyList()
            } finally {
                try { page?.destroy() } catch (e: Exception) { com.iman.pdf.logSwallowed("PdfFormFields:49", e) }
            }
        }

    private fun Int.asDisplayName(): String = when (this) {
        PDFWidget.TYPE_BUTTON -> "Button"
        PDFWidget.TYPE_CHECKBOX -> "Checkbox"
        PDFWidget.TYPE_COMBOBOX -> "Dropdown"
        PDFWidget.TYPE_LISTBOX -> "List"
        PDFWidget.TYPE_RADIOBUTTON -> "Radio button"
        PDFWidget.TYPE_SIGNATURE -> "Signature"
        PDFWidget.TYPE_TEXT -> "Text"
        else -> "Unknown"
    }
}
