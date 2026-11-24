
package com.example.quotexm1analyzer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
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
import android.view.WindowManager

class ScreenCaptureService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        private const val CHANNEL_ID = "quotex_capture"
        private const val NOTIFICATION_ID = 51

        @Volatile
        var latestFrameWidth: Int = 0
            private set

        @Volatile
        var latestFrameHeight: Int = 0
            private set
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var worker: HandlerThread? = null
    private var workerHandler: Handler? = null
    private var lastFrameTime = 0L

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        val resultCode =
            intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0

        val resultData =
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
            stopSelf()
            return START_NOT_STICKY
        }

        val manager =
            getSystemService(MEDIA_PROJECTION_SERVICE)
                    as MediaProjectionManager

        projection = manager.getMediaProjection(
            resultCode,
            resultData
        )

        projection?.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    releaseCapture()
                    stopSelf()
                }
            },
            Handler(mainLooper)
        )

        startCapture()
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

                        if (now - lastFrameTime >= 750L) {
                            lastFrameTime = now

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

                            // Frame is now available as an in-memory bitmap.
                            // No screenshot is saved or uploaded.
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e(
                        "QuotexCapture",
                        "Local frame processing error",
                        e
                    )
                } finally {
                    frameBitmap?.recycle()
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
    }

    private fun releaseCapture() {
        reader?.setOnImageAvailableListener(null, null)
        virtualDisplay?.release()
        reader?.close()
        projection?.stop()
        worker?.quitSafely()

        virtualDisplay = null
        reader = null
        projection = null
        worker = null
        workerHandler = null
        latestFrameWidth = 0
        latestFrameHeight = 0
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
                    "Screen frames are read in phone memory only."
                )
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Quotex local capture active")
                .setContentText(
                    "Screen frames are read in phone memory only."
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
