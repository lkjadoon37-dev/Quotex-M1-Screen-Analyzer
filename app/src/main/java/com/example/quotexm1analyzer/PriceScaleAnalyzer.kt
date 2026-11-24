
package com.example.quotexm1analyzer

data class PriceScalePoint(
    val price: Double,
    val y: Double
)

data class PriceScaleResult(
    val valid: Boolean,
    val pricePerPixel: Double?,
    val referencePrice: Double?,
    val referenceY: Double?,
    val pointsUsed: Int,
    val message: String
)

object PriceScaleAnalyzer {

    private val pricePattern =
        Regex("""\b\d+\.\d{4,6}\b""")

    fun analyze(
        lines: List<PriceTextLine>,
        screenWidth: Int,
        screenHeight: Int
    ): PriceScaleResult {

        if (screenWidth <= 0 || screenHeight <= 0) {
            return invalid("Screen size valid nahi hai.")
        }

        val minX = screenWidth * 0.80
        val minY = screenHeight * 0.20
        val maxY = screenHeight * 0.68

        val points = lines.mapNotNull { line ->

            val centerX = (line.left + line.right) / 2.0
            val centerY = (line.top + line.bottom) / 2.0

            if (centerX < minX ||
                centerY < minY ||
                centerY > maxY
            ) {
                return@mapNotNull null
            }

            val match = pricePattern.find(line.text)
                ?: return@mapNotNull null

            val price = match.value.toDoubleOrNull()
                ?: return@mapNotNull null

            if (!price.isFinite() || price <= 0.0) {
                return@mapNotNull null
            }

            PriceScalePoint(price, centerY)
        }.distinctBy {
            "${it.price}_${it.y.toInt()}"
        }

        if (points.size < 3) {
            return invalid(
                "Price Scale ke kam az kam 3 price labels zaroori hain."
            )
        }

        val meanY = points.map { it.y }.average()
        val meanPrice = points.map { it.price }.average()

        var numerator = 0.0
        var denominator = 0.0

        for (point in points) {
            numerator +=
                (point.y - meanY) *
                (point.price - meanPrice)

            denominator +=
                (point.y - meanY) *
                (point.y - meanY)
        }

        if (denominator == 0.0) {
            return invalid("Price Scale calibration fail hui.")
        }

        val slope = numerator / denominator
        val intercept = meanPrice - slope * meanY

        if (!slope.isFinite() || slope >= 0.0) {
            return invalid(
                "Price Scale ki direction confirm nahi hui."
            )
        }

        val predicted = points.map {
            intercept + slope * it.y
        }

        val averageError = points.indices.map {
            kotlin.math.abs(predicted[it] - points[it].price)
        }.average()

        val tolerance =
            points.map { it.price }.maxOrNull()!! -
            points.map { it.price }.minOrNull()!!

        if (averageError > tolerance * 0.20) {
            return invalid(
                "Price labels aapas mein match nahi kar rahe."
            )
        }

        return PriceScaleResult(
            valid = true,
            pricePerPixel = slope,
            referencePrice = intercept + slope * meanY,
            referenceY = meanY,
            pointsUsed = points.size,
            message = "Price Scale calibration available hai."
        )
    }

    private fun invalid(message: String) =
        PriceScaleResult(
            valid = false,
            pricePerPixel = null,
            referencePrice = null,
            referenceY = null,
            pointsUsed = 0,
            message = message
        )
}
