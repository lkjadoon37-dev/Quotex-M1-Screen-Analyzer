
package com.example.quotexm1analyzer

/**
 * Defines chart and price-scale regions in normalized coordinates.
 * Coordinates range from 0.0 to 1.0.
 */
data class ChartRegionConfig(
    val chartLeft: Float = 0.05f,
    val chartTop: Float = 0.10f,
    val chartRight: Float = 0.88f,
    val chartBottom: Float = 0.88f,

    val priceScaleLeft: Float = 0.88f,
    val priceScaleTop: Float = 0.10f,
    val priceScaleRight: Float = 0.99f,
    val priceScaleBottom: Float = 0.88f
) {
    init {
        require(chartLeft in 0f..1f)
        require(chartTop in 0f..1f)
        require(chartRight in 0f..1f)
        require(chartBottom in 0f..1f)

        require(priceScaleLeft in 0f..1f)
        require(priceScaleTop in 0f..1f)
        require(priceScaleRight in 0f..1f)
        require(priceScaleBottom in 0f..1f)

        require(chartLeft < chartRight)
        require(chartTop < chartBottom)

        require(priceScaleLeft < priceScaleRight)
        require(priceScaleTop < priceScaleBottom)
    }

    fun chartPixelBounds(
        width: Int,
        height: Int
    ): PixelBounds {
        return PixelBounds(
            left = (chartLeft * width).toInt(),
            top = (chartTop * height).toInt(),
            right = (chartRight * width).toInt(),
            bottom = (chartBottom * height).toInt()
        )
    }

    fun priceScalePixelBounds(
        width: Int,
        height: Int
    ): PixelBounds {
        return PixelBounds(
            left = (priceScaleLeft * width).toInt(),
            top = (priceScaleTop * height).toInt(),
            right = (priceScaleRight * width).toInt(),
            bottom = (priceScaleBottom * height).toInt()
        )
    }
}

data class PixelBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int
        get() = right - left

    val height: Int
        get() = bottom - top
}
