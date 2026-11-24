
package com.example.quotexm1analyzer

import kotlin.math.abs

/**
 * Local-only M1 technical analysis.
 * Input: closed candles, oldest first.
 * This engine does not read screenshots or place trades.
 */

data class Candle(
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val closeTimeMillis: Long
) {
    init {
        require(
            listOf(open, high, low, close).all {
                it.isFinite() && it > 0.0
            }
        ) {
            "Candle prices must be valid positive numbers."
        }

        require(high >= maxOf(open, close, low)) {
            "Invalid candle high."
        }

        require(low <= minOf(open, close, high)) {
            "Invalid candle low."
        }
    }
}

enum class Signal {
    CALL, PUT, WAIT
}

data class AnalysisResult(
    val signal: Signal,
    val strength: Int,
    val explanation: List<String>,
    val ema9: Double?,
    val ema21: Double?,
    val rsi14: Double?,
    val macd: Double?,
    val macdSignal: Double?,
    val support: Double?,
    val resistance: Double?
)

object AnalysisEngine {

    private const val MIN_CANDLES = 35
    private const val MAX_CANDLES = 200

    fun analyze(closedCandles: List<Candle>): AnalysisResult {

        if (closedCandles.size < MIN_CANDLES) {
            return waitResult(
                "Kam az kam $MIN_CANDLES closed M1 candles zaroori hain."
            )
        }

        val candles = closedCandles.takeLast(MAX_CANDLES)

        for (i in 1 until candles.size) {
            if (candles[i].closeTimeMillis <=
                candles[i - 1].closeTimeMillis
            ) {
                return waitResult(
                    "Candle time order ghalat hai. Purani candle pehle dein."
                )
            }
        }

        val closes = candles.map { it.close }

        val ema9 = ema(closes, 9).last()
        val ema21 = ema(closes, 21).last()

        val rsi14 = calculateRsi(closes, 14)

        val macdLine = subtract(
            ema(closes, 12),
            ema(closes, 26)
        )

        val macdSignalLine = ema(macdLine, 9)

        val macd = macdLine.last()
        val macdSignal = macdSignalLine.last()

        val recent = candles.takeLast(20)

        val support = recent.minOf { it.low }
        val resistance = recent.maxOf { it.high }

        val last = candles.last()
        val previous = candles[candles.lastIndex - 1]

        val range = (resistance - support)
            .coerceAtLeast(1e-10)

        val nearSupport =
            (last.close - support) / range <= 0.18

        val nearResistance =
            (resistance - last.close) / range <= 0.18

        var callScore = 0
        var putScore = 0

        val callReasons = mutableListOf<String>()
        val putReasons = mutableListOf<String>()

        if (ema9 > ema21) {
            callScore += 2
            callReasons.add("EMA 9, EMA 21 se upar hai.")
        } else if (ema9 < ema21) {
            putScore += 2
            putReasons.add("EMA 9, EMA 21 se neeche hai.")
        }

        if (rsi14 in 52.0..68.0) {
            callScore++
            callReasons.add("RSI bullish momentum zone mein hai.")
        } else if (rsi14 in 32.0..48.0) {
            putScore++
            putReasons.add("RSI bearish momentum zone mein hai.")
        }

        if (macd > macdSignal) {
            callScore++
            callReasons.add("MACD signal line se upar hai.")
        } else if (macd < macdSignal) {
            putScore++
            putReasons.add("MACD signal line se neeche hai.")
        }

        if (last.close > last.open &&
            last.close > previous.close
        ) {
            callScore++
            callReasons.add("Aakhri candle mein buying confirmation hai.")
        }

        if (last.close < last.open &&
            last.close < previous.close
        ) {
            putScore++
            putReasons.add("Aakhri candle mein selling confirmation hai.")
        }

        if (nearSupport) {
            callScore++
            callReasons.add(
                "Price support ke qareeb hai; bullish confirmation zaroori hai."
            )
        }

        if (nearResistance) {
            putScore++
            putReasons.add(
                "Price resistance ke qareeb hai; bearish confirmation zaroori hai."
            )
        }

        val difference = abs(callScore - putScore)

        val signal = when {
            difference < 2 -> Signal.WAIT

            callScore >= 4 &&
                callScore > putScore &&
                !nearResistance -> Signal.CALL

            putScore >= 4 &&
                putScore > callScore &&
                !nearSupport -> Signal.PUT

            else -> Signal.WAIT
        }

        val strength = when (signal) {
            Signal.CALL ->
                (55 + (callScore - putScore) * 8)
                    .coerceIn(55, 85)

            Signal.PUT ->
                (55 + (putScore - callScore) * 8)
                    .coerceIn(55, 85)

            Signal.WAIT -> 0
        }

        val reasons = when (signal) {
            Signal.CALL -> callReasons
            Signal.PUT -> putReasons
            Signal.WAIT -> mutableListOf(
                "Indicators mein clear agreement nahi hai.",
                "Behtar confirmation ka intezar karein."
            )
        }.toMutableList()

        reasons.add(
            "Strength score setup alignment hai, win probability nahi."
        )

        return AnalysisResult(
            signal = signal,
            strength = strength,
            explanation = reasons,
            ema9 = ema9,
            ema21 = ema21,
            rsi14 = rsi14,
            macd = macd,
            macdSignal = macdSignal,
            support = support,
            resistance = resistance
        )
    }

    private fun ema(
        values: List<Double>,
        period: Int
    ): List<Double> {
        require(values.isNotEmpty())

        val alpha = 2.0 / (period + 1.0)
        val output = ArrayList<Double>(values.size)

        var current = values.first()
        output.add(current)

        for (i in 1 until values.size) {
            current =
                values[i] * alpha +
                current * (1.0 - alpha)

            output.add(current)
        }

        return output
    }

    private fun calculateRsi(
        values: List<Double>,
        period: Int
    ): Double {

        var gains = 0.0
        var losses = 0.0

        for (i in 1..period) {
            val change = values[i] - values[i - 1]

            if (change > 0) {
                gains += change
            } else {
                losses -= change
            }
        }

        var averageGain = gains / period
        var averageLoss = losses / period

        for (i in period + 1 until values.size) {
            val change = values[i] - values[i - 1]

            averageGain =
                (averageGain * (period - 1) +
                    change.coerceAtLeast(0.0)) / period

            averageLoss =
                (averageLoss * (period - 1) +
                    (-change).coerceAtLeast(0.0)) / period
        }

        if (averageGain == 0.0 && averageLoss == 0.0) {
            return 50.0
        }

        if (averageLoss == 0.0) return 100.0
        if (averageGain == 0.0) return 0.0

        val relativeStrength = averageGain / averageLoss

        return 100.0 -
            (100.0 / (1.0 + relativeStrength))
    }

    private fun subtract(
        first: List<Double>,
        second: List<Double>
    ): List<Double> {
        return first.indices.map {
            first[it] - second[it]
        }
    }

    private fun waitResult(message: String): AnalysisResult {
        return AnalysisResult(
            signal = Signal.WAIT,
            strength = 0,
            explanation = listOf(message),
            ema9 = null,
            ema21 = null,
            rsi14 = null,
            macd = null,
            macdSignal = null,
            support = null,
            resistance = null
        )
    }
}
