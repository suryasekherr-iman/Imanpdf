package com.iman.pdf.pdf

/** One glyph of a page, in page-relative coordinates (0..1). */
data class TextGlyph(
    val code: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

/** One selected area, in page-relative coordinates (0..1). */
data class TextArea(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Selects text the way a text reader does: from the word under the finger-down point,
 * along the reading order, to the word under the finger-up point.
 */
object TextSelection {
    /** How far (in line heights) the first touch may be from text and still select it. */
    private const val START_REACH = 1.5f

    /** Vertical distance counts more than horizontal, so a drag into a margin stays on its line. */
    private const val VERTICAL_WEIGHT = 4f

    private class Position(val line: Int, val glyph: Int)

    /** One area per selected line; empty when the drag did not start on or near text. */
    fun select(
        lines: List<List<TextGlyph>>,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float
    ): List<TextArea> {
        val boxes = lines.map { bounds(it) }
        val anchor = nearestLine(boxes, startX, startY) ?: return emptyList()
        val anchorBox = boxes[anchor] ?: return emptyList()
        val reach = (anchorBox.bottom - anchorBox.top) * START_REACH
        if (gap(startX, anchorBox.left, anchorBox.right) > reach ||
            gap(startY, anchorBox.top, anchorBox.bottom) > reach
        ) {
            return emptyList()
        }
        val focus = nearestLine(boxes, endX, endY) ?: return emptyList()

        var first = Position(anchor, nearestGlyph(lines[anchor], startX))
        var last = Position(focus, nearestGlyph(lines[focus], endX))
        if (first.line > last.line || (first.line == last.line && first.glyph > last.glyph)) {
            first = last.also { last = first }
        }

        val from = wordStart(lines[first.line], first.glyph)
        val to = wordEnd(lines[last.line], last.glyph)
        return (first.line..last.line).mapNotNull { index ->
            val line = lines[index]
            val begin = if (index == first.line) from else 0
            val end = if (index == last.line) to else line.lastIndex
            if (begin > end) {
                null
            } else {
                bounds(line.subList(begin, end + 1).filterNot { isSpace(it) })
            }
        }
    }

    private fun isSpace(glyph: TextGlyph): Boolean =
        Character.isWhitespace(glyph.code) || Character.isSpaceChar(glyph.code)

    private fun bounds(glyphs: List<TextGlyph>): TextArea? {
        val visible = glyphs.filter { it.right > it.left && it.bottom > it.top }
        if (visible.isEmpty() || visible.all { isSpace(it) }) return null
        return TextArea(
            visible.minOf { it.left },
            visible.minOf { it.top },
            visible.maxOf { it.right },
            visible.maxOf { it.bottom }
        )
    }

    private fun gap(value: Float, low: Float, high: Float): Float =
        maxOf(low - value, value - high, 0f)

    private fun nearestLine(boxes: List<TextArea?>, x: Float, y: Float): Int? {
        var best: Int? = null
        var bestScore = Float.MAX_VALUE
        var bestCentre = Float.MAX_VALUE
        boxes.forEachIndexed { index, box ->
            if (box == null) return@forEachIndexed
            val score = gap(y, box.top, box.bottom) * VERTICAL_WEIGHT + gap(x, box.left, box.right)
            val centre = kotlin.math.abs(y - (box.top + box.bottom) / 2f)
            if (score < bestScore || (score == bestScore && centre < bestCentre)) {
                best = index
                bestScore = score
                bestCentre = centre
            }
        }
        return best
    }

    private fun nearestGlyph(line: List<TextGlyph>, x: Float): Int {
        var best = 0
        var bestGap = Float.MAX_VALUE
        line.forEachIndexed { index, glyph ->
            if (!isSpace(glyph) && glyph.right > glyph.left) {
                val distance = gap(x, glyph.left, glyph.right)
                if (distance < bestGap) {
                    best = index
                    bestGap = distance
                }
            }
        }
        return best
    }

    private fun wordStart(line: List<TextGlyph>, at: Int): Int {
        var index = at
        while (index > 0 && !isSpace(line[index - 1])) index--
        return index
    }

    private fun wordEnd(line: List<TextGlyph>, at: Int): Int {
        var index = at
        while (index < line.lastIndex && !isSpace(line[index + 1])) index++
        return index
    }
}
