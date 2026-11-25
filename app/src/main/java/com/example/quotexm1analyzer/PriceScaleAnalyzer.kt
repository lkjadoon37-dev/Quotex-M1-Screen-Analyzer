
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

    private val pricePattern = Regex(
        """^\s*(\d{1,3}(?:,\d{3})*(?:\.\d{1,8})?|\d+(?:[.,]\d{1,8})?)\s*$"""
    )

    private fun parsePrice(text: String): Double? {
        var cleaned = text.trim()
            .replace('，', ',')
            .replace('．', '.')
            .replace('。', '.')
            .replace(" ", "")

        // OCR aksar aakhri zero ko U ya O parh leta hai.
        cleaned = cleaned.replace(
            Regex("""(?i)(?<=\d)[UOQ]$"""), "0"
        )

        val match = pricePattern.matchEntire(cleaned) ?: return null
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
            return invalid("Screen dimensions invalid hain.")
        }

        // Price scale ke liye zyada screen area check karein.
        val minX = screenWidth * 0.80
        val maxX = screenWidth * 0.999
        val minY = screenHeight * 0.14
        val maxY = screenHeight * 0.84

        val axisLines = lines.filter { line ->
            val x = (line.left + line.right) / 2.0
            x in minX..maxX
        }

        val regionLines = axisLines.filter { line ->
            val y = (line.top + line.bottom) / 2.0
            y in minY..maxY
        }

        val points = regionLines.mapNotNull { line ->
            val price = parsePrice(line.text) ?: return@mapNotNull null

            if (!price.isFinite() || price <= 0.0) {
                return@mapNotNull null
            }

            PriceScalePoint(
                price,
                (line.top + line.bottom) / 2.0
            )
        }.distinctBy {
            "${it.price}_${it.y.toInt()}"
        }.sortedBy {
            it.y
        }

        if (points.size < 3) {
            return invalid(
                "OCR lines=${lines.size}, axis=${axisLines.size}, " +
                "region=${regionLines.size}, valid prices=${points.size}. " +
                "3 labels required.",
                points.size
            )
        }

        val meanY = points.map { it.y }.average()
        val meanPrice = points.map { it.price }.average()

        var numerator = 0.0
        var denominator = 0.0

        for (point in points) {
            numerator +=
                (point.y - meanY) * (point.price - meanPrice)

            denominator +=
                (point.y - meanY) * (point.y - meanY)
        }

        if (denominator <= 0.0) {
            return invalid("Price scale calibration failed.", points.size)
        }

        val slope = numerator / denominator
        val intercept = meanPrice - slope * meanY

        if (!slope.isFinite() || slope >= 0.0) {
            return invalid("Price scale direction invalid.", points.size)
        }

        val errors = points.map {
            kotlin.math.abs(
                (intercept + slope * it.y) - it.price
            )
        }

        val priceRange =
            points.maxOf { it.price } - points.minOf { it.price }

        if (priceRange <= 0.0 ||
            errors.average() > priceRange * 0.25
        ) {
            return invalid(
                "Price labels match nahi kar rahe.",
                points.size
            )
        }

        return PriceScaleResult(
            valid = true,
            pricePerPixel = slope,
            referencePrice = meanPrice,
            referenceY = meanY,
            pointsUsed = points.size,
            message = "Price Scale calibrated: ${points.size} labels verified."
        )
    }

    private fun invalid(
        message: String,
        points: Int = 0
    ) = PriceScaleResult(
        valid = false,
        pricePerPixel = null,
        referencePrice = null,
        referenceY = null,
        pointsUsed = points,
        message = message
    )
}
