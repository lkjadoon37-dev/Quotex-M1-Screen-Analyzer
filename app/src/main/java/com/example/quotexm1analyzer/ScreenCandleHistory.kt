
package com.example.quotexm1analyzer

/**
 * Stores candles inferred from Quotex screen frames.
 *
 * The rightmost detected candle is treated as active.
 * Only earlier candles are added to closed history.
 *
 * This class does not create fake price ticks.
 */
class ScreenCandleHistory(
    private val maxClosedCandles: Int = 200
) {

    private val closedCandles =
        linkedMapOf<Long, Candle>()

    init {
        require(maxClosedCandles >= 35) {
            "History must allow at least 35 candles."
        }
    }

    /**
     * Processes one screen-analysis frame.
     *
     * Returns only newly stored closed candles.
     */
    @Synchronized
    fun update(
        screenResult: ScreenAnalysisResult,
        timeAxis: TimeAxisResult,
        capturedAtMillis: Long
    ): List<Candle> {

        if (!screenResult.success ||
            !timeAxis.success ||
            screenResult.candles.size < 2 ||
            capturedAtMillis <= 0L
        ) {
            return emptyList()
        }

        val currentMinute =
            (capturedAtMillis / 60_000L) * 60_000L

        val mapped = screenResult.candles
            .sortedBy { it.x }
            .mapNotNull { candidate ->

                val timestamp =
                    timeAxis.timestampAtX(
                        candidate.x,
                        capturedAtMillis
                    ) ?: return@mapNotNull null

                val minuteStart =
                    (timestamp / 60_000L) * 60_000L

                // Do not accept future chart candles.
                if (minuteStart > currentMinute) {
                    return@mapNotNull null
                }

                val high = candidate.topPrice
                val low = candidate.bottomPrice
                val open = candidate.openPrice
                val close = candidate.closePrice

                if (!open.isFinite() ||
                    !close.isFinite() ||
                    !high.isFinite() ||
                    !low.isFinite() ||
                    low <= 0.0 ||
                    high < maxOf(open, close, low) ||
                    low > minOf(open, close, high)
                ) {
                    return@mapNotNull null
                }

                minuteStart to candidate
            }

        if (mapped.size < 2) {
            return emptyList()
        }

        // The latest mapped minute is treated as the active candle.
        val latestMinute = mapped.maxOf { it.first }

        val newlyClosed = mutableListOf<Candle>()

        // Keep only one candidate for each minute.
        val onePerMinute = mapped
            .groupBy { it.first }
            .mapValues { (_, candidates) ->
                candidates.last()
            }

        for ((minuteStart, candidate) in onePerMinute.toSortedMap()) {

            // Never store the currently active candle.
            if (minuteStart >= latestMinute) continue

            val closeTime = minuteStart + 60_000L

            if (closedCandles.containsKey(closeTime)) {
                continue
            }

            val candle = try {
                Candle(
                    open = candidate.openPrice,
                    high = candidate.topPrice,
                    low = candidate.bottomPrice,
                    close = candidate.closePrice,
                    closeTimeMillis = closeTime
                )
            } catch (_: IllegalArgumentException) {
                continue
            }

            closedCandles[closeTime] = candle
            newlyClosed.add(candle)
        }

        while (closedCandles.size > maxClosedCandles) {
            val oldestKey = closedCandles.keys.first()
            closedCandles.remove(oldestKey)
        }

        return newlyClosed
    }

    /**
     * Returns stored candles oldest first.
     */
    @Synchronized
    fun getClosedCandles(): List<Candle> {
        return closedCandles.values.toList()
    }

    @Synchronized
    fun getCount(): Int {
        return closedCandles.size
    }

    @Synchronized
    fun clear() {
        closedCandles.clear()
    }
}
