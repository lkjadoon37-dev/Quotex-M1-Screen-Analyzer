
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

    // Only accept complete numeric price labels.
    // This rejects times such as 22:56 and percentages.
    private val pricePattern = Regex(
        """^\s*(\d{1,3}(?:,\d{3})*(?:\.\d{1,8})?|\d+(?:[.,]\d{1,8})?)\s*$"""
    )

    private fun parsePrice(text: String): Double? {
        val match = pricePattern.matchEntire(text)
            ?: return null

        val token = match.groupValues[1]

        val normalized = when {
            token.contains('.') && token.contains(',') ->
                token.replace(",", "")
            token.contains(',') ->
                token.replace(',', '.')
            else -> token
        }

        return normalized.toDoubleOrNull()
    }

    fun analyze(
        lines: List<PriceTextLine>,
        screenWidth: Int,
        screenHeight: Int
    ): PriceScaleResult {

        if (screenWidth <= 0 || screenHeight <= 0) {
            return invalid("Screen size valid nahi hai.")
        }

        // Calibrated to the supplied Quotex screenshot.
        val minX = screenWidth * 0.84
        val maxX = screenWidth * 0.995
        val minY = screenHeight * 0.22
        val maxY = screenHeight * 0.75

        val points = lines.mapNotNull { line ->

            val centerX = (line.left + line.right) / 2.0
            val centerY = (line.top + line.bottom) / 2.0

            if (centerX < minX ||
                centerX > maxX ||
                centerY < minY ||
                centerY > maxY
            ) {
                return@mapNotNull null
            }

            val price = parsePrice(line.text)
                ?: return@mapNotNull null

            if (!price.isFinite() || price <= 0.0) {
                return@mapNotNull null
            }

            PriceScalePoint(price, centerY)
        }.distinctBy {
            "${it.price}_${it.y.toInt()}"
        }.sortedBy {
            it.y
        }

        if (points.size < 3) {
            return invalid(
                "Price Scale ke kam az kam 3 valid labels zaroori hain."
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

        if (denominator <= 0.0) {
            return invalid("Price Scale calibration fail hui.")
        }

        val slope = numerator / denominator
        val intercept = meanPrice - slope * meanY

        if (!slope.isFinite() || slope >= 0.0) {
            return invalid(
                "Price Scale ki direction confirm nahi hui."
            )
        }

        val errors = points.map { point ->
            kotlin.math.abs(
                (intercept + slope * point.y) - point.price
            )
        }

        val averageError = errors.average()

        val priceRange =
            points.maxOf { it.price } -
            points.minOf { it.price }

        if (priceRange <= 0.0 ||
            averageError > priceRange * 0.20
        ) {
            return invalid(
                "Price labels aapas mein match nahi kar rahe."
            )
        }

        return PriceScaleResult(
            valid = true,
            pricePerPixel = slope,
            referencePrice = meanPrice,
            referenceY = meanY,
            pointsUsed = points.size,
            message =
                "Price Scale calibrated. ${points.size} labels verified."
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
