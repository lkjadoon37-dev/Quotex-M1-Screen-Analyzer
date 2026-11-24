
package com.example.quotexm1analyzer

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.os.Build
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
    private lateinit var frameInfo: TextView
    private lateinit var ocrInfo: TextView
    private lateinit var analysisInfo: TextView
    private lateinit var result: TextView
    private lateinit var candleInput: EditText

    private var receiverRegistered = false
    private var frameCount = 0

    private val frameReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {

                ScreenCaptureService.ACTION_FRAME_UPDATE -> {

                    if (intent.hasExtra(ScreenCaptureService.EXTRA_SCREEN_ANALYSIS)) {
                        val message = intent.getStringExtra(
                            ScreenCaptureService.EXTRA_SCREEN_ANALYSIS
                        ).orEmpty()

                        val candleCount = intent.getIntExtra(
                            ScreenCaptureService.EXTRA_CANDLE_COUNT, 0
                        )

                        val scalePoints = intent.getIntExtra(
                            ScreenCaptureService.EXTRA_SCALE_POINTS, 0
                        )

                        analysisInfo.text = buildString {
                            appendLine("LOCAL CANDLE SCREEN ANALYSIS")
                            appendLine()
                            appendLine("Detected candle candidates: $candleCount")
                            appendLine("Price scale points: $scalePoints")
                            appendLine()
                            appendLine(message)
                            appendLine()
                            append("Note: Candle timing aur OHLC abhi verify hona baqi hai. Yeh CALL/PUT signal nahi hai.")
                        }
                        return
                    }

                    if (intent.hasExtra(ScreenCaptureService.EXTRA_OCR_TEXT)) {
                        val text = intent.getStringExtra(
                            ScreenCaptureService.EXTRA_OCR_TEXT
                        ).orEmpty()

                        val numbers = intent.getStringExtra(
                            ScreenCaptureService.EXTRA_OCR_NUMBERS
                        ).orEmpty()

                        ocrInfo.text = buildString {
                            appendLine("PRICE TEXT READING")
                            appendLine()
                            appendLine("Detected numbers:")
                            appendLine(
                                if (numbers.isBlank()) "Abhi koi number detect nahi hua."
                                else numbers
                            )
                            appendLine()
                            appendLine("Detected text and screen positions:")
                            appendLine(
                                if (text.isBlank()) "Koi text detect nahi hua."
                                else text
                            )
                            appendLine()
                            append("Note: OCR text abhi verified market price ya OHLC nahi hai.")
                        }
                        return
                    }

                    frameCount++

                    val width = intent.getIntExtra(
                        ScreenCaptureService.EXTRA_FRAME_WIDTH, 0
                    )
                    val height = intent.getIntExtra(
                        ScreenCaptureService.EXTRA_FRAME_HEIGHT, 0
                    )
                    val brightness = intent.getDoubleExtra(
                        ScreenCaptureService.EXTRA_FRAME_BRIGHTNESS, 0.0
                    )
                    val contrast = intent.getDoubleExtra(
                        ScreenCaptureService.EXTRA_FRAME_CONTRAST, 0.0
                    )
                    val pixels = intent.getIntExtra(
                        ScreenCaptureService.EXTRA_FRAME_PIXELS, 0
                    )

                    status.text =
                        "SCREEN CAPTURE ACTIVE\nPhone par local processing chal rahi hai."

                    frameInfo.text = buildFrameInfo(
                        width,
                        height,
                        brightness,
                        contrast,
                        pixels,
                        frameCount
                    )
                }

                ScreenCaptureService.ACTION_CAPTURE_ERROR -> {
                    val message = intent.getStringExtra(
                        ScreenCaptureService.EXTRA_ERROR_MESSAGE
                    ) ?: "Unknown capture error"
                    status.text = "CAPTURE ERROR:\n$message"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createScreen()
    }

    private fun createScreen() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        fun addText(message: String, size: Float): TextView {
            val view = TextView(this).apply {
                text = message
                textSize = size
                setPadding(4, 10, 4, 10)
            }
            content.addView(view)
            return view
        }

        fun addButton(label: String, action: () -> Unit) {
            content.addView(Button(this).apply {
                text = label
                setOnClickListener { action() }
            })
        }

        addText("QUOTEX AI ANALYZER M1", 23f)
        addText("LOCAL SCREEN ANALYSIS", 17f)

        status = addText("Screen capture band hai.", 16f)

        addButton("SCREEN CAPTURE SHURU KAREIN") {
            startScreenCapture()
        }

        addButton("CAPTURE BAND KAREIN") {
            stopService(Intent(this, ScreenCaptureService::class.java))
            status.text = "Screen capture band hai."
            frameInfo.text = "Capture stopped. Dobara start karne ke liye button dabayein."
            ocrInfo.text = "PRICE TEXT READING: Capture band hai."
            analysisInfo.text = "LOCAL CANDLE SCREEN ANALYSIS: Capture band hai."
            frameCount = 0
        }

        frameInfo = addText(
            "FRAME STATUS: WAITING\nAbhi koi frame receive nahi hua.",
            14f
        )

        ocrInfo = addText(
            "PRICE TEXT READING\nAbhi OCR reading nahi mili.",
            14f
        )

        analysisInfo = addText(
            "LOCAL CANDLE SCREEN ANALYSIS\nAbhi analysis result nahi mila.",
            15f
        )

        addText(
            "PRIVACY: Screen frames phone par process hote hain. Is app mein screenshot upload karne ka code nahi hai.",
            14f
        )

        addText("CANDLE DATA ANALYSIS", 19f)

        addText(
            "Kam az kam 35 CLOSED M1 candles enter karein. Har line mein Open, High, Low, Close likhein. Purani candle pehle aur nayi candle aakhir mein.",
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

        result = addText("Analysis result yahan show hoga.", 17f)

        val scroll = ScrollView(this)
        scroll.addView(content)
        setContentView(scroll)
    }

    private fun buildFrameInfo(
        width: Int,
        height: Int,
        brightness: Double,
        contrast: Double,
        pixels: Int,
        count: Int
    ): String {
        return buildString {
            appendLine("FRAME STATUS: RECEIVING")
            appendLine("Received frames: $count")
            appendLine("Screen size: $width x $height")
            appendLine("Brightness: ${String.format(Locale.US, "%.2f", brightness)}")
            appendLine("Contrast: ${String.format(Locale.US, "%.2f", contrast)}")
            appendLine("Sampled pixels: $pixels")
            appendLine()
            append("Screen frames phone par locally process ho rahe hain.")
        }
    }

    private fun startScreenCapture() {
        try {
            val manager =
                getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

            startActivityForResult(
                manager.createScreenCaptureIntent(),
                captureRequest
            )
            status.text = "Screen capture permission ka intezar hai."
        } catch (e: Exception) {
            status.text = "Capture start error: ${e.message}"
        }
    }

    private fun registerFrameReceiver() {
        if (receiverRegistered) return

        val filter = IntentFilter().apply {
            addAction(ScreenCaptureService.ACTION_FRAME_UPDATE)
            addAction(ScreenCaptureService.ACTION_CAPTURE_ERROR)
        }

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                frameReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(frameReceiver, filter)
        }

        receiverRegistered = true

        // Restore latest frame information from the running service.
        val width = ScreenCaptureService.latestFrameWidth
        val height = ScreenCaptureService.latestFrameHeight
        val brightness = ScreenCaptureService.latestFrameBrightness
        val contrast = ScreenCaptureService.latestFrameContrast
        val pixels = ScreenCaptureService.latestFramePixels

        val previousOcr = ScreenCaptureService.latestOcrText
        val previousNumbers = ScreenCaptureService.latestOcrNumbers

        val previousAnalysis = ScreenCaptureService.latestScreenAnalysis
        val candleCount = ScreenCaptureService.latestCandleCount
        val scalePoints = ScreenCaptureService.latestScalePoints

        if (width > 0 && height > 0) {
            status.text =
                "SCREEN CAPTURE ACTIVE\nPhone par local processing chal rahi hai."

            frameCount = 1

            frameInfo.text = buildFrameInfo(
                width,
                height,
                brightness,
                contrast,
                pixels,
                frameCount
            )
        }

        if (previousOcr.isNotBlank() || previousNumbers.isNotBlank()) {
            ocrInfo.text = buildString {
                appendLine("PRICE TEXT READING")
                appendLine()
                appendLine("Detected numbers:")
                appendLine(
                    if (previousNumbers.isBlank()) "Abhi koi number detect nahi hua."
                    else previousNumbers
                )
                appendLine()
                appendLine("Detected text and screen positions:")
                appendLine(
                    if (previousOcr.isBlank()) "Koi text detect nahi hua."
                    else previousOcr
                )
                appendLine()
                append("Note: OCR text abhi verified market price ya OHLC nahi hai.")
            }
        }

        if (previousAnalysis.isNotBlank()) {
            analysisInfo.text = buildString {
                appendLine("LOCAL CANDLE SCREEN ANALYSIS")
                appendLine()
                appendLine("Detected candle candidates: $candleCount")
                appendLine("Price scale points: $scalePoints")
                appendLine()
                appendLine(previousAnalysis)
                appendLine()
                append("Note: Candle timing aur OHLC abhi verify hona baqi hai. Yeh CALL/PUT signal nahi hai.")
            }
        }
    }

    override fun onStart() {
        super.onStart()
        registerFrameReceiver()
    }

    override fun onStop() {
        if (receiverRegistered) {
            unregisterReceiver(frameReceiver)
            receiverRegistered = false
        }
        super.onStop()
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
                appendLine("EXPLANATION:")
                analysis.explanation.forEach {
                    appendLine("- $it")
                }
                appendLine()
                append("Strength score win probability nahi hai. Demo par test karein.")
            }

        } catch (e: Exception) {
            result.text =
                "Error: ${e.message}\n\nHar candle mein 4 valid OHLC values dein."
        }
    }

    @Deprecated("Legacy screen capture permission result")
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
            val serviceIntent =
                Intent(this, ScreenCaptureService::class.java).apply {
                    putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                    putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
                }

            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }

                frameCount = 0
                status.text = "Capture permission mil gayi. Frames ka intezar hai."
                frameInfo.text = "FRAME STATUS: STARTING..."
                ocrInfo.text = "PRICE TEXT READING: Intezaar hai..."
                analysisInfo.text = "LOCAL CANDLE SCREEN ANALYSIS: Intezaar hai..."

            } catch (e: Exception) {
                status.text = "Service start error: ${e.message}"
            }
        } else {
            status.text = "Screen capture permission nahi mili."
        }
    }
}
