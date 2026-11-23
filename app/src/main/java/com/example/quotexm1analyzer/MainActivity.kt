
package com.example.quotexm1analyzer

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.view.Gravity

class MainActivity : Activity() {
    private val captureRequest = 4101
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        status = TextView(this).apply {
            text = "Quotex M1 AI Analyzer\n\nCapture abhi band hai."
            textSize = 18f
            gravity = Gravity.CENTER
        }

        val start = Button(this).apply {
            text = "Screen capture shuru karein"
            setOnClickListener {
                val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                startActivityForResult(
                    manager.createScreenCaptureIntent(),
                    captureRequest
                )
            }
        }

        val stop = Button(this).apply {
            text = "Capture band karein"
            setOnClickListener {
                stopService(Intent(this@MainActivity, ScreenCaptureService::class.java))
                status.text = "Capture band kar di gayi."
            }
        }

        layout.addView(status)
        layout.addView(start)
        layout.addView(stop)
        setContentView(layout)
    }

    @Deprecated("Use Activity Result API in production")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == captureRequest && resultCode == RESULT_OK && data != null) {
            val service = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
            }

            startForegroundService(service)

            status.text =
                "Screen capture active hai.\n\n" +
                "Quotex chart phone par locally capture ho raha hai.\n\n" +
                "Note: Candle recognition aur CALL/PUT/WAIT analysis abhi complete nahi hai."
        } else {
            status.text = "Screen capture ki permission nahi mili."
        }
    }
}
