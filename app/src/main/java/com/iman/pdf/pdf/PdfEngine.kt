// MuPdfEngine.kt mein renderPageToBitmap function ko aise update karein:

fun renderPageToBitmap(pageNumber: Int, targetWidth: Int): Bitmap? {
    val doc = document ?: return null
    if (pageNumber < 0 || pageNumber >= doc.countPages()) return null

    return try {
        currentPage?.destroy()
        currentPage = doc.loadPage(pageNumber)
        val page = currentPage ?: return null

        // PDF page ka original width nikaalein
        val pageBounds = page.bounds
        val originalWidth = pageBounds.width()
        
        // Scale calculate karein taaki PDF screen ki width mein fit ho jaye
        val scale = targetWidth.toFloat() / originalWidth

        val matrix = Matrix(scale, scale)
        val pixmap = page.toPixmap(matrix, Color.WHITE, false, true)
        
        val bitmap = Bitmap.createBitmap(pixmap.width, pixmap.height, Bitmap.Config.ARGB_8888)
        pixmap.toBitmap(bitmap, 0, 0)
        pixmap.destroy()
        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
