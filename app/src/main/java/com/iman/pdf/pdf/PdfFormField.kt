package com.iman.pdf.pdf

/** Interactive AcroForm field metadata in page-relative coordinates. */
data class PdfFormField(
    val pageIndex: Int,
    val fieldIndex: Int,
    val label: String,
    val type: String,
    val value: String,
    val options: List<String>,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val readOnly: Boolean,
    val required: Boolean
)

data class PdfFormFieldUpdate(
    val pageIndex: Int,
    val fieldIndex: Int,
    val value: String
)
