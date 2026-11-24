
package com.example.quotexm1analyzer

import android.graphics.Bitmap
import android.graphics.Color

data class DetectedCandle(
    val x: Int,
    val openY: Int,
    val closeY: Int,
    val highY: Int,
    val lowY: Int,
    val isBullish: Boolean,
    val bodyHeight: Int
)

class CandleDetector {

    private enum class CandleColor {
        GREEN, RED
    }

    private data class ColumnHit(
        val x: Int,
        val color: CandleColor,
        val ys: List<Int>
    )

    companion object {
        private const val GREEN_MIN = 145
        private const val RED_MIN = 165

        private const val MIN_COLUMN_PIXELS = 3
        private const val MIN_BODY_HEIGHT = 2

        private const val WICK_MAX_GAP = 2
    }

    fun detect(
        bitmap: Bitmap,
        bounds: PixelBounds
    ): List<DetectedCandle> {

        if (bitmap.isRecycled ||
            bitmap.width <= 0 ||
            bitmap.height <= 0 ||
            bounds.width <= 0 ||
            bounds.height <= 0
        ) {
            return emptyList()
        }

        val left = bounds.left.coerceIn(0, bitmap.width - 1)
        val right = bounds.right.coerceIn(left + 1, bitmap.width)
        val top = bounds.top.coerceIn(0, bitmap.height - 1)
        val bottom = bounds.bottom.coerceIn(top + 1, bitmap.height)

        val hits = mutableListOf<ColumnHit>()

        for (x in left until right) {

            val greenYs = mutableListOf<Int>()
            val redYs = mutableListOf<Int>()

            for (y in top until bottom) {

                val color = bitmap.getPixel(x, y)

                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)

                if (isGreen(r, g, b)) {
                    greenYs.add(y)
                }

                if (isRed(r, g, b)) {
                    redYs.add(y)
                }
            }

            val selectedColor: CandleColor
            val selectedYs: List<Int>

            if (greenYs.size > redYs.size &&
                greenYs.size >= MIN_COLUMN_PIXELS
            ) {
                selectedColor = CandleColor.GREEN
                selectedYs = greenYs

            } else if (redYs.size >= MIN_COLUMN_PIXELS) {
                selectedColor = CandleColor.RED
                selectedYs = redYs

            } else {
                continue
            }

            hits.add(
                ColumnHit(
                    x = x,
                    color = selectedColor,
                    ys = selectedYs
                )
            )
        }

        return buildCandles(
            bitmap = bitmap,
            hits = hits,
            top = top,
            bottom = bottom
        )
    }

    private fun isGreen(
        r: Int,
        g: Int,
        b: Int
    ): Boolean {

        return g >= GREEN_MIN &&
            g - r >= 45 &&
            g - b >= 30
    }

    private fun isRed(
        r: Int,
        g: Int,
        b: Int
    ): Boolean {

        return r >= RED_MIN &&
            r - g >= 55 &&
            r - b >= 45
    }

    private fun buildCandles(
        bitmap: Bitmap,
        hits: List<ColumnHit>,
        top: Int,
        bottom: Int
    ): List<DetectedCandle> {

        if (hits.isEmpty()) return emptyList()

        val result = mutableListOf<DetectedCandle>()
        var group = mutableListOf<ColumnHit>()

        for (hit in hits) {

            val previous = group.lastOrNull()

            val sameGroup =
                previous != null &&
                previous.color == hit.color &&
                hit.x - previous.x <= 2

            if (group.isEmpty() || sameGroup) {
                group.add(hit)
            } else {
                detectGroup(
                    bitmap,
                    group,
                    top,
                    bottom
                )?.let { result.add(it) }

                group = mutableListOf(hit)
            }
        }

        if (group.isNotEmpty()) {
            detectGroup(
                bitmap,
                group,
                top,
                bottom
            )?.let { result.add(it) }
        }

        return result.sortedBy { it.x }
    }

    private fun detectGroup(
        bitmap: Bitmap,
        group: List<ColumnHit>,
        top: Int,
        bottom: Int
    ): DetectedCandle? {

        if (group.isEmpty()) return null

        val color = group.first().color
        val rowCounts = IntArray(bottom - top)

        for (column in group) {
            for (y in column.ys) {
                val index = y - top

                if (index in rowCounts.indices) {
                    rowCounts[index]++
                }
            }
        }

        val requiredSupport = maxOf(
            2,
            (group.size * 35 + 99) / 100
        )

        val bodyRows = rowCounts.indices.filter {
            rowCounts[it] >= requiredSupport
        }

        if (bodyRows.isEmpty()) return null

        val bodyTop = top + bodyRows.first()
        val bodyBottom = top + bodyRows.last()

        val bodyHeight = bodyBottom - bodyTop

        if (bodyHeight < MIN_BODY_HEIGHT) return null

        val centerX = group[group.size / 2].x

        var highY = bodyTop
        var lowY = bodyBottom

        // Candle ki upper wick detect karna
        var misses = 0
        var y = bodyTop - 1

        while (y >= top && misses <= WICK_MAX_GAP) {

            if (isCandlePixel(bitmap, centerX, y, color)) {
                highY = y
                misses = 0
            } else {
                misses++
            }

            y--
        }

        // Candle ki lower wick detect karna
        misses = 0
        y = bodyBottom + 1

        while (y < bottom && misses <= WICK_MAX_GAP) {

            if (isCandlePixel(bitmap, centerX, y, color)) {
                lowY = y
                misses = 0
            } else {
                misses++
            }

            y++
        }

        val bullish = color == CandleColor.GREEN

        val openY = if (bullish) {
            bodyBottom
        } else {
            bodyTop
        }

        val closeY = if (bullish) {
            bodyTop
        } else {
            bodyBottom
        }

        return DetectedCandle(
            x = centerX,
            openY = openY,
            closeY = closeY,
            highY = highY,
            lowY = lowY,
            isBullish = bullish,
            bodyHeight = bodyHeight
        )
    }

    private fun isCandlePixel(
        bitmap: Bitmap,
        x: Int,
        y: Int,
        color: CandleColor
    ): Boolean {

        if (x !in 0 until bitmap.width ||
            y !in 0 until bitmap.height
        ) {
            return false
        }

        val pixel = bitmap.getPixel(x, y)

        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)

        return when (color) {
            CandleColor.GREEN -> isGreen(r, g, b)
            CandleColor.RED -> isRed(r, g, b)
        }
    }
}
