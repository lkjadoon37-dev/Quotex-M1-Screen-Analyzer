
package com.example.quotexm1analyzer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager

class ScreenCaptureService : Service() {

    companion object {

        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        const val ACTION_FRAME_UPDATE =
            "com.example.quotexm1analyzer.FRAME_UPDATE"

        const val ACTION_CAPTURE_ERROR =
            "com.example.quotexm1analyzer.CAPTURE_ERROR"

        const val EXTRA_FRAME_WIDTH = "frame_width"
        const val EXTRA_FRAME_HEIGHT = "frame_height"
        const val EXTRA_FRAME_BRIGHTNESS = "frame_brightness"
        const val EXTRA_FRAME_CONTRAST = "frame_contrast"
        const val EXTRA_FRAME_PIXELS = "frame_pixels"
        const val EXTRA_ERROR_MESSAGE = "error_message"

        const val EXTRA_OCR_TEXT = "ocr_text"
        const val EXTRA_OCR_NUMBERS = "ocr_numbers"

        const val EXTRA_SCREEN_ANALYSIS = "screen_analysis"
        const val EXTRA_CANDLE_COUNT = "candle_count"
        const val EXTRA_SCALE_POINTS = "scale_points"

        const val EXTRA_TIME_AXIS_STATUS = "time_axis_status"
        const val EXTRA_CLOSED_CANDLE_COUNT = "closed_candle_count"

        private const val CHANNEL_ID = "quotex_capture"
        private const val NOTIFICATION_ID = 51
        private const val TAG = "QuotexCapture"

        @Volatile
        var latestFrameWidth: Int = 0
            private set

        @Volatile
        var latestFrameHeight: Int = 0
            private set

        @Volatile
        var latestFrameBrightness: Double = 0.0
            private set

        @Volatile
        var latestFrameContrast: Double = 0.0
            private set

        @Volatile
        var latestFramePixels: Int = 0
            private set

        @Volatile
        var latestOcrText: String = ""
            private set

        @Volatile
        var latestOcrNumbers: String = ""
            private set

        @Volatile
        var latestScreenAnalysis: String = ""
            private set

        @Volatile
        var latestCandleCount: Int = 0
            private set

        @Volatile
        var latestScalePoints: Int = 0
            private set

        @Volatile
        var latestTimeAxisStatus: String = ""
            private set

        @Volatile
        var latestClosedCandleCount: Int = 0
            private set
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var worker: HandlerThread? = null
    private var workerHandler: Handler? = null
    private var priceReader: PriceTextReader? = null

    // Existing screen candle detector
    private val screenAnalyzer = CandleScreenAnalyzer()

    // New chart time reader
    private val timeAxisAnalyzer = TimeAxisAnalyzer()

    // New closed candle history
    private val candleHistory = ScreenCandleHistory()

    private var lastFrameTime = 0L
    private var isReleased = false

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (projection != null) return START_NOT_STICKY

        isReleased = false
        createNotificationChannel()

        try {

            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification()
                )
            }

            val resultCode =
                intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0

            val resultData: Intent? =
                if (Build.VERSION.SDK_INT >= 33) {
                    intent?.getParcelableExtra(
                        EXTRA_RESULT_DATA,
                        Intent::class.java
                    )
                } else {
                    @Suppress("DEPRECATION")
                    intent?.getParcelableExtra(EXTRA_RESULT_DATA)
                }

            if (resultData == null) {
                sendError(
                    "Screen capture permission data nahi mili."
                )
                stopSelf()
                return START_NOT_STICKY
            }

            val manager =
                getSystemService(MEDIA_PROJECTION_SERVICE)
                    as MediaProjectionManager

            projection =
                manager.getMediaProjection(resultCode, resultData)

            projection?.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        releaseCapture(stopProjection = false)
                        stopSelf()
                    }
                },
                Handler(mainLooper)
            )

            priceReader = PriceTextReader()

            startCapture()

        } catch (e: Exception) {

            Log.e(TAG, "Capture start failed", e)

            sendError(
                e.message ?: "Capture start nahi ho saka."
            )

            releaseCapture()
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun startCapture() {

        val metrics = DisplayMetrics()

        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager)
            .defaultDisplay.getRealMetrics(metrics)

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        require(width > 0 && height > 0) {
            "Screen dimensions invalid hain."
        }

        worker = HandlerThread(
            "LocalFrameReader"
        ).also {
            it.start()
        }

        workerHandler = Handler(worker!!.looper)

        reader = ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            2
        )

        reader?.setOnImageAvailableListener(
            { imageReader ->

                var image: Image? = null
                var paddedBitmap: Bitmap? = null
                var frameBitmap: Bitmap? = null

                try {

                    image =
                        imageReader.acquireLatestImage()

                    if (image != null) {

                        val capturedAtMillis =
                            System.currentTimeMillis()

                        if (
                            capturedAtMillis - lastFrameTime >= 1000L
                        ) {

                            lastFrameTime = capturedAtMillis

                            val plane = image.planes[0]
                            val buffer = plane.buffer
                            val pixelStride = plane.pixelStride
                            val rowStride = plane.rowStride

                            val paddedWidth =
                                rowStride / pixelStride

                            paddedBitmap = Bitmap.createBitmap(
                                paddedWidth,
                                image.height,
                                Bitmap.Config.ARGB_8888
                            )

                            buffer.rewind()

                            paddedBitmap.copyPixelsFromBuffer(
                                buffer
                            )

                            frameBitmap = Bitmap.createBitmap(
                                paddedBitmap,
                                0,
                                0,
                                image.width,
                                image.height
                            )

                            latestFrameWidth =
                                frameBitmap.width

                            latestFrameHeight =
                                frameBitmap.height

                            val health =
                                FrameProcessor.inspect(frameBitmap)

                            latestFrameBrightness =
                                health.meanLuma

                            latestFrameContrast =
                                health.contrast

                            latestFramePixels =
                                health.sampledPixels

                            val update =
                                Intent(ACTION_FRAME_UPDATE).apply {

                                    setPackage(packageName)

                                    putExtra(
                                        EXTRA_FRAME_WIDTH,
                                        health.width
                                    )

                                    putExtra(
                                        EXTRA_FRAME_HEIGHT,
                                        health.height
                                    )

                                    putExtra(
                                        EXTRA_FRAME_BRIGHTNESS,
                                        health.meanLuma
                                    )

                                    putExtra(
                                        EXTRA_FRAME_CONTRAST,
                                        health.contrast
                                    )

                                    putExtra(
                                        EXTRA_FRAME_PIXELS,
                                        health.sampledPixels
                                    )
                                }

                            sendBroadcast(update)

                            val analysisBitmap =
                                frameBitmap.copy(
                                    Bitmap.Config.ARGB_8888,
                                    false
                                )

                            priceReader?.read(frameBitmap) { result ->

                                if (result.error != null) {

                                    Log.e(
                                        TAG,
                                        "OCR error: ${result.error}"
                                    )

                                    latestOcrText =
                                        "OCR error: ${result.error}"

                                    latestOcrNumbers = ""

                                    if (!analysisBitmap.isRecycled) {
                                        analysisBitmap.recycle()
                                    }

                                } else {

                                    val text =
                                        result.lines.joinToString("\n") {
                                            "${it.text} " +
                                                "[${it.left}," +
                                                "${it.top}," +
                                                "${it.right}," +
                                                "${it.bottom}]"
                                        }

                                    val numbers =
                                        result.numericValues.joinToString(",")

                                    latestOcrText = text
                                    latestOcrNumbers = numbers

                                    val ocrUpdate =
                                        Intent(ACTION_FRAME_UPDATE).apply {

                                            setPackage(packageName)

                                            putExtra(
                                                EXTRA_OCR_TEXT,
                                                text
                                            )

                                            putExtra(
                                                EXTRA_OCR_NUMBERS,
                                                numbers
                                            )
                                        }

                                    sendBroadcast(ocrUpdate)

                                    val handler = workerHandler

                                    if (handler != null) {

                                        handler.post {

                                            try {

                                                // 1. Detect screen candles
                                                val analysis =
                                                    screenAnalyzer.analyzeFrame(
                                                        analysisBitmap,
                                                        result.lines
                                                    )

                                                latestCandleCount =
                                                    analysis.candles.size

                                                latestScalePoints =
                                                    analysis.scalePoints

                                                // 2. Read chart time labels
                                                val timeAxis =
                                                    timeAxisAnalyzer.analyze(
                                                        lines = result.lines,
                                                        screenWidth =
                                                            analysisBitmap.width,
                                                        screenHeight =
                                                            analysisBitmap.height
                                                    )

                                                latestTimeAxisStatus =
                                                    timeAxis.message

                                                // 3. Store inferred closed candles
                                                val newlyClosed =
                                                    candleHistory.update(
                                                        screenResult = analysis,
                                                        timeAxis = timeAxis,
                                                        capturedAtMillis =
                                                            capturedAtMillis
                                                    )

                                                latestClosedCandleCount =
                                                    candleHistory.getCount()

                                                // 4. Prepare diagnostic status
                                                val combinedMessage =
                                                    analysis.message +
                                                        " | Time: " +
                                                        timeAxis.message +
                                                        " | New closed: " +
                                                        newlyClosed.size +
                                                        " | History: " +
                                                        latestClosedCandleCount

                                                latestScreenAnalysis =
                                                    combinedMessage

                                                // 5. Send screen update
                                                val analysisUpdate =
                                                    Intent(
                                                        ACTION_FRAME_UPDATE
                                                    ).apply {

                                                        setPackage(packageName)

                                                        putExtra(
                                                            EXTRA_SCREEN_ANALYSIS,
                                                            combinedMessage
                                                        )

                                                        putExtra(
                                                            EXTRA_CANDLE_COUNT,
                                                            analysis.candles.size
                                                        )

                                                        putExtra(
                                                            EXTRA_SCALE_POINTS,
                                                            analysis.scalePoints
                                                        )

                                                        putExtra(
                                                            EXTRA_TIME_AXIS_STATUS,
                                                            timeAxis.message
                                                        )

                                                        putExtra(
                                                            EXTRA_CLOSED_CANDLE_COUNT,
                                                            latestClosedCandleCount
                                                        )
                                                    }

                                                sendBroadcast(
                                                    analysisUpdate
                                                )

                                                Log.i(
                                                    TAG,
                                                    "Screen: ${analysis.message}"
                                                )

                                                Log.i(
                                                    TAG,
                                                    "Time axis: ${timeAxis.message}"
                                                )

                                                Log.i(
                                                    TAG,
                                                    "New closed candles: ${newlyClosed.size}"
                                                )

                                                Log.i(
                                                    TAG,
                                                    "Stored history: ${candleHistory.getCount()}"
                                                )

                                            } catch (error: Exception) {

                                                Log.e(
                                                    TAG,
                                                    "Screen analysis failed",
                                                    error
                                                )

                                                latestScreenAnalysis =
                                                    "Analysis error: " +
                                                        (error.message
                                                            ?: "Unknown error")

                                            } finally {

                                                if (
                                                    !analysisBitmap.isRecycled
                                                ) {
                                                    analysisBitmap.recycle()
                                                }
                                            }
                                        }

                                    } else {

                                        if (!analysisBitmap.isRecycled) {
                                            analysisBitmap.recycle()
                                        }
                                    }
                                }
                            }
                        }
                    }

                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "Local frame processing error",
                        e
                    )

                    sendError(
                        e.message ?: "Frame processing error"
                    )

                } finally {

                    if (
                        frameBitmap != null &&
                        frameBitmap !== paddedBitmap
                    ) {
                        frameBitmap.recycle()
                    }

                    paddedBitmap?.recycle()

                    image?.close()
                }
            },
            workerHandler
        )

        virtualDisplay =
            projection?.createVirtualDisplay(
                "QuotexLocalCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader?.surface,
                null,
                workerHandler
            )

        if (virtualDisplay == null) {
            throw IllegalStateException(
                "Virtual display create nahi hua."
            )
        }
    }

    private fun sendError(message: String) {

        val errorIntent =
            Intent(ACTION_CAPTURE_ERROR).apply {

                setPackage(packageName)

                putExtra(
                    EXTRA_ERROR_MESSAGE,
                    message
                )
            }

        sendBroadcast(errorIntent)
    }

    private fun releaseCapture(
        stopProjection: Boolean = true
    ) {

        if (isReleased) return

        isReleased = true

        try {
            reader?.setOnImageAvailableListener(
                null,
                null
            )
        } catch (_: Exception) {}

        try {
            virtualDisplay?.release()
        } catch (_: Exception) {}

        try {
            reader?.close()
        } catch (_: Exception) {}

        val oldProjection = projection
        projection = null

        if (stopProjection) {
            try {
                oldProjection?.stop()
            } catch (_: Exception) {}
        }

        try {
            priceReader?.close()
        } catch (_: Exception) {}

        priceReader = null

        try {
            worker?.quitSafely()
        } catch (_: Exception) {}

        virtualDisplay = null
        reader = null
        worker = null
        workerHandler = null

        // Clear temporary candle history when capture stops
        candleHistory.clear()

        latestFrameWidth = 0
        latestFrameHeight = 0
        latestFrameBrightness = 0.0
        latestFrameContrast = 0.0
        latestFramePixels = 0

        latestOcrText = ""
        latestOcrNumbers = ""

        latestScreenAnalysis = ""
        latestCandleCount = 0
        latestScalePoints = 0

        latestTimeAxisStatus = ""
        latestClosedCandleCount = 0
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= 26) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Quotex screen capture",
                NotificationManager.IMPORTANCE_LOW
            )

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {

        return if (Build.VERSION.SDK_INT >= 26) {

            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(
                    "Quotex local capture active"
                )
                .setContentText(
                    "Screen frames phone par locally process ho rahe hain."
                )
                .setSmallIcon(
                    android.R.drawable.ic_menu_view
                )
                .setOngoing(true)
                .build()

        } else {

            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(
                    "Quotex local capture active"
                )
                .setContentText(
                    "Screen frames phone par locally process ho rahe hain."
                )
                .setSmallIcon(
                    android.R.drawable.ic_menu_view
                )
                .setOngoing(true)
                .build()
        }
    }

    override fun onDestroy() {
        releaseCapture()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
