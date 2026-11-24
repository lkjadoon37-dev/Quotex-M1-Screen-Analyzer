
package com.example.quotexm1analyzer

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {

    private val captureRequest = 4101
    private lateinit var status: TextView
    private lateinit var result: TextView
    private lateinit var candleInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        fun addText(message: String, size: Float) {
            content.addView(TextView(this).apply {
                text = message
                textSize = size
                setPadding(4, 10, 4, 10)
            })
        }

        fun addButton(label: String, action: () -> Unit) {
            content.addView(Button(this).apply {
                text = label
                setOnClickListener { action() }
            })
        }

        addText("QUOTEX AI ANALYZER M1", 23f)

        status = TextView(this).apply {
            text = "Screen capture band hai."
            textSize = 16f
            setPadding(4, 12, 4, 12)
        }
        content.addView(status)

        addButton("SCREEN CAPTURE SHURU KAREIN") {
            val manager =
                getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(
                manager.createScreenCaptureIntent(),
                captureRequest
            )
        }

        addButton("CAPTURE BAND KAREIN") {
            stopService(Intent(this, ScreenCaptureService::class.java))
            status.text = "Screen capture band hai."
        }

        addText(
            "ZAROORI: Capture abhi screen frames leta hai. Quotex candles ki automatic pehchan abhi tayyar nahi.",
            14f
        )

        addText("CANDLE DATA ANALYSIS", 19f)

        addText(
            "Kam az kam 35 CLOSED M1 candles enter karein. Har line mein Open,High,Low,Close likhein. Purani candle pehle aur nayi candle aakhir mein.",
            14f
        )

        candleInput = EditText(this).apply {
            hint = "1.1000,1.1020,1.0990,1.1010"
            minLines = 8
            gravity = Gravity.TOP
            textSize = 14f
        }
        content.addView(candleInput)

        addButton("ANALYZE CANDLES") {
            analyzeCandles()
        }

        addButton("DATA CLEAR KAREIN") {
            candleInput.text.clear()
            result.text = "Analysis result yahan show hoga."
        }

        result = TextView(this).apply {
            text = "Analysis result yahan show hoga."
            textSize = 17f
            setPadding(4, 20, 4, 20)
        }
        content.addView(result)

        val scroll = ScrollView(this)
        scroll.addView(content)
        setContentView(scroll)
    }

    private fun formatValue(value: Double?, decimals: Int): String {
        return value?.let {
            String.format(Locale.US, "%.${decimals}f", it)
        } ?: "N/A"
    }

    private fun analyzeCandles() {
        try {
            val lines = candleInput.text.toString()
                .lines()
                .filter { it.isNotBlank() }

            val candles = lines.mapIndexed { index, line ->
                val values = line.trim()
                    .split(Regex("[,;\\s]+"))
                    .map { it.toDouble() }

                require(values.size == 4) {
                    "Line ${index + 1}: 4 values zaroori hain."
                }

                val (open, high, low, close) = values

                require(
                    high >= maxOf(open, close) &&
                    low <= minOf(open, close) &&
                    high >= low
                ) {
                    "Line ${index + 1}: OHLC values check karein."
                }

                Candle(
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    closeTimeMillis = index.toLong() * 60_000L
                )
            }

            val analysis = AnalysisEngine.analyze(candles)

            result.text = buildString {
                appendLine("SIGNAL: ${analysis.signal}")
                appendLine()
                appendLine("Setup Strength: ${analysis.strength}%")
                appendLine()
                appendLine("EMA 9: ${formatValue(analysis.ema9, 5)}")
                appendLine("EMA 21: ${formatValue(analysis.ema21, 5)}")
                appendLine("RSI 14: ${formatValue(analysis.rsi14, 2)}")
                appendLine("MACD: ${formatValue(analysis.macd, 6)}")
                appendLine("MACD Signal: ${formatValue(analysis.macdSignal, 6)}")
                appendLine("Support: ${formatValue(analysis.support, 5)}")
                appendLine("Resistance: ${formatValue(analysis.resistance, 5)}")
                appendLine()
                appendLine("Explanation:")
                analysis.explanation.forEach { appendLine("- $it") }
                appendLine()
                append("Strength score win probability nahi hai. Demo par test karein.")
            }

        } catch (e: Exception) {
            result.text =
                "Error: ${e.message}\n\nHar candle mein 4 valid OHLC values dein."
        }
    }

    @Deprecated("Use Activity Result API in production")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (
            requestCode == captureRequest &&
            resultCode == RESULT_OK &&
            data != null
        ) {
            val service = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
            }

            startForegroundService(service)

            status.text =
                "Capture active hai. Frames phone par receive ho rahe hain. Candle recognition abhi tayyar nahi."
        } else {
            status.text = "Screen capture permission nahi mili."
        }
    }
}
