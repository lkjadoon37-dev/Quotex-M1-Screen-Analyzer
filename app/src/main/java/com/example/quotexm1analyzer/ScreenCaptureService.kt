
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
        var latestOcrText: String = ""
            private set
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var worker: HandlerThread? = null
    private var workerHandler: Handler? = null
    private var priceReader: PriceTextReader? = null

    private var lastFrameTime = 0L
    private var isReleased = false

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (projection != null) return START_NOT_STICKY

        createNotificationChannel()

        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
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
                sendError("Screen capture permission data nahi mili.")
                stopSelf()
                return START_NOT_STICKY
            }

            val manager =
                getSystemService(MEDIA_PROJECTION_SERVICE)
                    as MediaProjectionManager

            projection = manager.getMediaProjection(resultCode, resultData)

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
            sendError(e.message ?: "Capture start nahi ho saka.")
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

        worker = HandlerThread("LocalFrameReader").also {
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
                    image = imageReader.acquireLatestImage()

                    if (image != null) {
                        val now = System.currentTimeMillis()

                        if (now - lastFrameTime >= 1000L) {
                            lastFrameTime = now

                            val plane = image.planes[0]
                            val buffer = plane.buffer
                            val pixelStride = plane.pixelStride
                            val rowStride = plane.rowStride

                            val paddedWidth = rowStride / pixelStride

                            paddedBitmap = Bitmap.createBitmap(
                                paddedWidth,
                                image.height,
                                Bitmap.Config.ARGB_8888
                            )

                            buffer.rewind()
                            paddedBitmap.copyPixelsFromBuffer(buffer)

                            frameBitmap = Bitmap.createBitmap(
                                paddedBitmap,
                                0,
                                0,
                                image.width,
                                image.height
                            )

                            latestFrameWidth = frameBitmap.width
                            latestFrameHeight = frameBitmap.height

                            val health =
                                FrameProcessor.inspect(frameBitmap)

                            val update = Intent(ACTION_FRAME_UPDATE).apply {
                                setPackage(packageName)

                                putExtra(EXTRA_FRAME_WIDTH, health.width)
                                putExtra(EXTRA_FRAME_HEIGHT, health.height)
                                putExtra(EXTRA_FRAME_BRIGHTNESS, health.meanLuma)
                                putExtra(EXTRA_FRAME_CONTRAST, health.contrast)
                                putExtra(EXTRA_FRAME_PIXELS, health.sampledPixels)
                            }

                            sendBroadcast(update)

                            // OCR runs locally on the phone.
                            // PriceTextReader makes its own bitmap copy.
                            priceReader?.read(frameBitmap) { result ->

                                if (result.error != null) {
                                    Log.e(TAG, "OCR error: ${result.error}")
                                } else {
                                    val text = result.lines.joinToString("\n") {
                                        "${it.text} [${it.left},${it.top},${it.right},${it.bottom}]"
                                    }

                                    val numbers =
                                        result.numericValues.joinToString(",")

                                    latestOcrText = text

                                    val ocrUpdate =
                                        Intent(ACTION_FRAME_UPDATE).apply {
                                            setPackage(packageName)
                                            putExtra(EXTRA_OCR_TEXT, text)
                                            putExtra(EXTRA_OCR_NUMBERS, numbers)
                                        }

                                    sendBroadcast(ocrUpdate)
                                }
                            }
                        }
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Local frame processing error", e)
                    sendError(e.message ?: "Frame processing error")

                } finally {
                    if (frameBitmap != null &&
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

        virtualDisplay = projection?.createVirtualDisplay(
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
        val errorIntent = Intent(ACTION_CAPTURE_ERROR).apply {
            setPackage(packageName)
            putExtra(EXTRA_ERROR_MESSAGE, message)
        }
        sendBroadcast(errorIntent)
    }

    private fun releaseCapture(
        stopProjection: Boolean = true
    ) {
        if (isReleased) return
        isReleased = true

        try {
            reader?.setOnImageAvailableListener(null, null)
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

        latestFrameWidth = 0
        latestFrameHeight = 0
        latestOcrText = ""
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
                .setContentTitle("Quotex local capture active")
                .setContentText(
                    "Screen frames phone par locally process ho rahe hain."
                )
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Quotex local capture active")
                .setContentText(
                    "Screen frames phone par locally process ho rahe hain."
                )
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build()
        }
    }

    override fun onDestroy() {
        releaseCapture()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
