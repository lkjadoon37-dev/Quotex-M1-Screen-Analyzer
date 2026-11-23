
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

        fun addButton(label: String, action: () -> Unit) {
            content.addView(Button(this).apply {
                text = label
                setOnClickListener { action() }
            })
        }

        content.addView(TextView(this).apply {
            text = "QUOTEX AI ANALYZER M1"
            textSize = 23f
            gravity = Gravity.CENTER
        })

        status = TextView(this).apply {
            text = "Screen capture band hai."
            textSize = 16f
            setPadding(4, 16, 4, 16)
        }
        content.addView(status)

        addButton("Screen Capture Shuru Karein") {
            val manager =
                getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

            startActivityForResult(
                manager.createScreenCaptureIntent(),
                captureRequest
            )
        }

        addButton("Capture Band Karein") {
            stopService(Intent(this, ScreenCaptureService::class.java))
            status.text = "Screen capture band kar di gayi."
        }

        content.addView(TextView(this).apply {
            text = "\nCANDLE DATA ANALYSIS"
            textSize = 19f
        })

        content.addView(TextView(this).apply {
            text = "Kam az kam 35 closed candles enter karein. Har line mein Open,High,Low,Close likhein. Purani candle pehle aur nayi candle aakhir mein."
            textSize = 14f
            setPadding(4, 8, 4, 8)
        })

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

        addButton("Data Clear Karein") {
            candleInput.text.clear()
            result.text = "Analysis result yahan show hoga."
        }

        result = TextView(this).apply {
            text = "\nAnalysis result yahan show hoga."
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
                analysis.explanation.forEach { reason ->
                    appendLine("- $reason")
                }
                appendLine()
                append("Strength score win probability nahi hai. Pehle demo par test karein.")
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
                "Screen capture active hai.\nFrames phone par locally process ho rahe hain."
        } else {
            status.text = "Screen capture permission nahi mili."
        }
    }
}
