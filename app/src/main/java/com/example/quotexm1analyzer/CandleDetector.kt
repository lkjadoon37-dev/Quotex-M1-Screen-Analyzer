
package com.example.quotexm1analyzer

import android.graphics.Bitmap
import kotlin.math.abs

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

    companion object {
        private const val GREEN_MIN = 100
        private const val RED_MIN = 100
        private const val MIN_BODY_HEIGHT = 2
        private const val MIN_WICK_HEIGHT = 1
    }

    fun detect(
        bitmap: Bitmap,
        bounds: PixelBounds
    ): List<DetectedCandle> {

        if (bitmap.isRecycled ||
            bounds.width <= 0 ||
            bounds.height <= 0
        ) return emptyList()

        val left = bounds.left.coerceIn(0, bitmap.width - 1)
        val right = bounds.right.coerceIn(left + 1, bitmap.width)
        val top = bounds.top.coerceIn(0, bitmap.height - 1)
        val bottom = bounds.bottom.coerceIn(top + 1, bitmap.height)

        val candles = mutableListOf<DetectedCandle>()

        var x = left

        while (x < right) {

            val green = mutableListOf<Int>()
            val red = mutableListOf<Int>()

            for (y in top until bottom) {
                val color = bitmap.getPixel(x, y)

                val r = android.graphics.Color.red(color)
                val g = android.graphics.Color.green(color)
                val b = android.graphics.Color.blue(color)

                if (g > GREEN_MIN && g > r * 1.25 &&
                    g > b * 1.10
                ) {
                    green.add(y)
                }

                if (r > RED_MIN && r > g * 1.25 &&
                    r > b * 1.10
                ) {
                    red.add(y)
                }
            }

            val bullish = green.size > red.size
            val selected = if (bullish) green else red

            if (selected.size >= MIN_BODY_HEIGHT) {

                val highY = selected.minOrNull()!!
                val lowY = selected.maxOrNull()!!

                val bodyHeight = lowY - highY

                if (bodyHeight >= MIN_BODY_HEIGHT) {

                    candles.add(
                        DetectedCandle(
                            x = x,
                            openY = if (bullish) lowY else highY,
                            closeY = if (bullish) highY else lowY,
                            highY = highY,
                            lowY = lowY,
                            isBullish = bullish,
                            bodyHeight = bodyHeight
                        )
                    )
                }
            }

            x++
        }

        return mergeNearbyPixels(candles)
    }

    private fun mergeNearbyPixels(
        input: List<DetectedCandle>
    ): List<DetectedCandle> {

        if (input.isEmpty()) return emptyList()

        val result = mutableListOf<DetectedCandle>()
        var group = mutableListOf<DetectedCandle>()

        for (candle in input) {

            if (group.isEmpty() ||
                candle.x - group.last().x <= 3
            ) {
                group.add(candle)
            } else {
                result.add(combine(group))
                group = mutableListOf(candle)
            }
        }

        if (group.isNotEmpty()) {
            result.add(combine(group))
        }

        return result
    }

    private fun combine(
        group: List<DetectedCandle>
    ): DetectedCandle {

        val first = group.first()
        val last = group.last()

        val high = group.minOf { it.highY }
        val low = group.maxOf { it.lowY }

        return DetectedCandle(
            x = group.map { it.x }.average().toInt(),
            openY = first.openY,
            closeY = last.closeY,
            highY = high,
            lowY = low,
            isBullish = last.closeY < first.openY,
            bodyHeight = abs(first.openY - last.closeY)
        )
    }
}
