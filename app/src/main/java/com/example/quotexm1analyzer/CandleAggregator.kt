
package com.example.quotexm1analyzer

/**
 * Builds one-minute candles from validated local price samples.
 * Samples must represent actual chart prices, not axis labels.
 */
class CandleAggregator(
    private val maxClosedCandles: Int = 200
) {
    private val closedCandles = mutableListOf<Candle>()

    private var currentMinute: Long? = null
    private var currentOpen = 0.0
    private var currentHigh = 0.0
    private var currentLow = 0.0
    private var currentClose = 0.0

    @Synchronized
    fun addPrice(
        price: Double,
        timestampMillis: Long
    ): Candle? {
        require(price.isFinite() && price > 0.0) {
            "Invalid market price."
        }

        require(timestampMillis >= 0L) {
            "Invalid timestamp."
        }

        val minute = timestampMillis / 60_000L

        if (currentMinute == null) {
            startMinute(minute, price)
            return null
        }

        val activeMinute = currentMinute!!

        if (minute < activeMinute) {
            return null
        }

        if (minute == activeMinute) {
            currentHigh = maxOf(currentHigh, price)
            currentLow = minOf(currentLow, price)
            currentClose = price
            return null
        }

        val completed = Candle(
            open = currentOpen,
            high = currentHigh,
            low = currentLow,
            close = currentClose,
            closeTimeMillis = (activeMinute + 1L) * 60_000L
        )

        closedCandles.add(completed)

        while (closedCandles.size > maxClosedCandles) {
            closedCandles.removeAt(0)
        }

        startMinute(minute, price)

        return completed
    }

    private fun startMinute(
        minute: Long,
        price: Double
    ) {
        currentMinute = minute
        currentOpen = price
        currentHigh = price
        currentLow = price
        currentClose = price
    }

    @Synchronized
    fun getClosedCandles(): List<Candle> {
        return closedCandles.toList()
    }

    @Synchronized
    fun getCurrentPrice(): Double? {
        return if (currentMinute == null) null
        else currentClose
    }

    @Synchronized
    fun clear() {
        closedCandles.clear()
        currentMinute = null
        currentOpen = 0.0
        currentHigh = 0.0
        currentLow = 0.0
        currentClose = 0.0
    }
}
