
package com.example.quotexm1analyzer

import android.graphics.Bitmap

data class ScreenCandleCandidate(
    val x: Int,
    val openPrice: Double,
    val closePrice: Double,
    val topPrice: Double,
    val bottomPrice: Double,
    val bullish: Boolean
)

data class ScreenAnalysisResult(
    val success: Boolean,
    val message: String,
    val scalePoints: Int,
    val candles: List<ScreenCandleCandidate>
)

class CandleScreenAnalyzer(
    private val regionConfig: ChartRegionConfig = ChartRegionConfig()
) {

    private val candleDetector = CandleDetector()

    fun analyzeFrame(
        bitmap: Bitmap,
        priceLines: List<PriceTextLine>
    ): ScreenAnalysisResult {

        if (bitmap.isRecycled) {
            return failure("Screen image available nahi hai.")
        }

        if (bitmap.width < 100 || bitmap.height < 100) {
            return failure("Screen size bohat chhota hai.")
        }

        val scale = PriceScaleAnalyzer.analyze(
            lines = priceLines,
            screenWidth = bitmap.width,
            screenHeight = bitmap.height
        )

        if (!scale.valid ||
            scale.pricePerPixel == null ||
            scale.referencePrice == null ||
            scale.referenceY == null
        ) {
            return failure(scale.message)
        }

        val bounds = regionConfig.chartPixelBounds(
            bitmap.width,
            bitmap.height
        )

        val detected = try {
            candleDetector.detect(bitmap, bounds)
        } catch (error: Exception) {
            return failure(
                "Candle detection error: ${error.message}"
            )
        }

        if (detected.isEmpty()) {
            return failure(
                "Chart par candle shapes detect nahi huin."
            )
        }

        val pricePerPixel = scale.pricePerPixel
        val referencePrice = scale.referencePrice
        val referenceY = scale.referenceY

        fun priceAt(y: Int): Double {
            return referencePrice +
                (y - referenceY) * pricePerPixel
        }

        val candidates = detected.mapNotNull { candle ->

            val open = priceAt(candle.openY)
            val close = priceAt(candle.closeY)
            val top = priceAt(candle.highY)
            val bottom = priceAt(candle.lowY)

            if (!open.isFinite() ||
                !close.isFinite() ||
                !top.isFinite() ||
                !bottom.isFinite() ||
                minOf(open, close, top, bottom) <= 0.0
            ) {
                null
            } else {
                ScreenCandleCandidate(
                    x = candle.x,
                    openPrice = open,
                    closePrice = close,
                    topPrice = top,
                    bottomPrice = bottom,
                    bullish = candle.isBullish
                )
            }
        }

        if (candidates.isEmpty()) {
            return failure("Valid price conversion nahi hui.")
        }

        return ScreenAnalysisResult(
            success = true,
            message =
                "Price scale calibrated. ${candidates.size} candle candidates mile. " +
                "Abhi wick aur candle timing verify karna baqi hai.",
            scalePoints = scale.pointsUsed,
            candles = candidates
        )
    }

    private fun failure(message: String): ScreenAnalysisResult {
        return ScreenAnalysisResult(
            success = false,
            message = message,
            scalePoints = 0,
            candles = emptyList()
        )
    }
}
