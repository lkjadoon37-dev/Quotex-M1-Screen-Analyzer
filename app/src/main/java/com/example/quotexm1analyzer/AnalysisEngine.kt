package com.example.quotexm1analyzer

import kotlin.math.abs

/**
 * Local-only M1 technical analysis core.
 * Input must be CLOSED one-minute candles in chronological order (oldest first).
 * This class does not read the screen or place trades.
 */
data class Candle(
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val closeTimeMillis: Long
) {
    init {
        require(high >= maxOf(open, close, low)) { "Invalid candle high" }
        require(low <= minOf(open, close, high)) { "Invalid candle low" }
    }
}

enum class Signal { CALL, PUT, WAIT }

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

    fun analyze(closedCandles: List<Candle>): AnalysisResult {
        val candles = closedCandles.takeLast(200)
        if (candles.size < MIN_CANDLES) return waitResult(
            "Kam az kam $MIN_CANDLES band M1 candles darkar hain; abhi signal nahi diya gaya."
        )

        val closes = candles.map { it.close }
        val ema9 = ema(closes, 9).last()
        val ema21 = ema(closes, 21).last()
        val rsi = rsi(closes, 14)
        val macdLine = subtract(ema(closes, 12), ema(closes, 26))
        val signalLine = ema(macdLine, 9)
        val macd = macdLine.last()
        val macdSignal = signalLine.last()

        val recent = candles.takeLast(20)
        val support = recent.minOf { it.low }
        val resistance = recent.maxOf { it.high }
        val last = candles.last()
        val previous = candles[candles.lastIndex - 1]
        val range = (resistance - support).coerceAtLeast(1e-10)
        val nearSupport = (last.close - support) / range <= 0.18
        val nearResistance = (resistance - last.close) / range <= 0.18

        var call = 0
        var put = 0
        val callReasons = mutableListOf<String>()
        val putReasons = mutableListOf<String>()

        if (ema9 > ema21) { call += 2; callReasons += "EMA 9, EMA 21 se upar: short trend bullish." }
        if (ema9 < ema21) { put += 2; putReasons += "EMA 9, EMA 21 se neeche: short trend bearish." }
        if (rsi >= 52.0 && rsi <= 68.0) { call++; callReasons += "RSI ${rsi.oneDecimal()} bullish momentum zone mein hai." }
        if (rsi <= 48.0 && rsi >= 32.0) { put++; putReasons += "RSI ${rsi.oneDecimal()} bearish momentum zone mein hai." }
        if (macd > macdSignal) { call++; callReasons += "MACD signal line se upar hai." }
        if (macd < macdSignal) { put++; putReasons += "MACD signal line se neeche hai." }
        if (last.close > last.open && last.close > previous.close) { call++; callReasons += "Aakhri band candle mein buying confirmation hai." }
        if (last.close < last.open && last.close < previous.close) { put++; putReasons += "Aakhri band candle mein selling confirmation hai." }
        if (nearSupport) { call++; callReasons += "Price recent support ke qareeb hai; bullish confirmation zaroori hai." }
        if (nearResistance) { put++; putReasons += "Price recent resistance ke qareeb hai; bearish confirmation zaroori hai." }

        val difference = abs(call - put)
        val chosen = when {
            difference < 2 -> Signal.WAIT
            call > put && call >= 4 && !nearResistance -> Signal.CALL
            put > call && put >= 4 && !nearSupport -> Signal.PUT
            else -> Signal.WAIT
        }
        val score = when (chosen) {
            Signal.CALL -> (55 + (call - put) * 8).coerceIn(55, 85)
            Signal.PUT -> (55 + (put - call) * 8).coerceIn(55, 85)
            Signal.WAIT -> 0
        }
        val reasons = when (chosen) {
            Signal.CALL -> callReasons
            Signal.PUT -> putReasons
            Signal.WAIT -> listOf("Indicators mein clear agreement nahi; confirmation ka intezar karein.")
        }.toMutableList()
        if (chosen == Signal.CALL && nearResistance) reasons += "Resistance bohat qareeb hai; entry avoid karein."
        if (chosen == Signal.PUT && nearSupport) reasons += "Support bohat qareeb hai; entry avoid karein."
        reasons += "Strength score setup alignment hai, jeetne ka percent ya guarantee nahi."

        return AnalysisResult(chosen, score, reasons, ema9, ema21, rsi, macd, macdSignal, support, resistance)
    }

    private fun ema(values: List<Double>, period: Int): List<Double> {
        require(values.isNotEmpty())
        val alpha = 2.0 / (period + 1.0)
        val out = ArrayList<Double>(values.size)
        var current = values.first()
        out += current
        for (i in 1 until values.size) {
            current = values[i] * alpha + current * (1.0 - alpha)
            out += current
        }
        return out
    }

    private fun rsi(values: List<Double>, period: Int): Double {
        var gains = 0.0
        var losses = 0.0
        for (i in 1..period) {
            val change = values[i] - values[i - 1]
            if (change >= 0) gains += change else losses -= change
        }
        var avgGain = gains / period
        var avgLoss = losses / period
        for (i in period + 1 until values.size) {
            val change = values[i] - values[i - 1]
            avgGain = (avgGain * (period - 1) + change.coerceAtLeast(0.0)) / period
            avgLoss = (avgLoss * (period - 1) + (-change).coerceAtLeast(0.0)) / period
        }
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }

    private fun subtract(a: List<Double>, b: List<Double>) = a.indices.map { a[it] - b[it] }
    private fun Double.oneDecimal() = "%.1f".format(this)

    private fun waitResult(message: String) = AnalysisResult(
        Signal.WAIT, 0, listOf(message), null, null, null, null, null, null, null
    )
}
