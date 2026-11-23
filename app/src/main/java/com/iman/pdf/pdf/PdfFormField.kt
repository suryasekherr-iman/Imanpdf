package com.iman.pdf.pdf

/** Read-only metadata for an interactive AcroForm field discovered on a PDF page. */
data class PdfFormField(
    val pageIndex: Int,
    val label: String,
    val type: String,
    val value: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val readOnly: Boolean,
    val required: Boolean
)
