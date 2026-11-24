
package com.example.quotexm1analyzer

import android.graphics.Bitmap
import kotlin.math.sqrt

data class FrameHealth(
    val width: Int,
    val height: Int,
    val sampledPixels: Int,
    val meanLuma: Double,
    val contrast: Double
)

object FrameProcessor {

    fun inspect(bitmap: Bitmap): FrameHealth {
        require(!bitmap.isRecycled) {
            "Frame bitmap is already recycled."
        }

        val width = bitmap.width
        val height = bitmap.height

        require(width > 0 && height > 0) {
            "Invalid frame dimensions."
        }

        val stepX = (width / 80).coerceAtLeast(1)
        val stepY = (height / 80).coerceAtLeast(1)

        var count = 0
        var sum = 0.0
        var sumSquares = 0.0

        var y = 0
        while (y < height) {
            var x = 0

            while (x < width) {
                val color = bitmap.getPixel(x, y)

                val r = (color shr 16) and 0xff
                val g = (color shr 8) and 0xff
                val b = color and 0xff

                val luma =
                    0.2126 * r + 0.7152 * g + 0.0722 * b

                sum += luma
                sumSquares += luma * luma
                count++

                x += stepX
            }

            y += stepY
        }

        val mean =
            if (count == 0) 0.0 else sum / count

        val variance =
            if (count == 0) 0.0
            else (sumSquares / count - mean * mean)
                .coerceAtLeast(0.0)

        return FrameHealth(
            width = width,
            height = height,
            sampledPixels = count,
            meanLuma = mean,
            contrast = sqrt(variance)
        )
    }
}
