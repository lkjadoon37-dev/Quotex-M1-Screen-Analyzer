package com.example.quotexm1analyzer

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

data class PriceTextLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

data class PriceReadResult(
    val lines: List<PriceTextLine>,
    val numericValues: List<String>,
    val error: String? = null
)

class PriceTextReader {

    private val recognizer = TextRecognition.getClient(
        TextRecognizerOptions.DEFAULT_OPTIONS
    )

    private val isReading = AtomicBoolean(false)

    fun read(
        source: Bitmap,
        callback: (PriceReadResult) -> Unit
    ) {
        if (!isReading.compareAndSet(false, true)) {
            return
        }

        val bitmap = source.copy(
            Bitmap.Config.ARGB_8888,
            false
        )

        if (bitmap == null) {
            isReading.set(false)
            callback(
                PriceReadResult(
                    lines = emptyList(),
                    numericValues = emptyList(),
                    error = "Frame copy nahi ho saka."
                )
            )
            return
        }

        try {
            val image = InputImage.fromBitmap(bitmap, 0)

            recognizer.process(image)
                .addOnSuccessListener { result ->

                    val lines = mutableListOf<PriceTextLine>()

                    for (block in result.textBlocks) {
                        for (line in block.lines) {

                            val bounds = line.boundingBox ?: continue

                            lines.add(
                                PriceTextLine(
                                    text = line.text.trim(),
                                    left = bounds.left,
                                    top = bounds.top,
                                    right = bounds.right,
                                    bottom = bounds.bottom
                                )
                            )
                        }
                    }

                    val numericValues = lines
                        .map { it.text }
                        .filter {
                            Regex("""[-+]?\d[\d,.]*""")
                                .containsMatchIn(it)
                        }
                        .distinct()

                    callback(
                        PriceReadResult(
                            lines = lines,
                            numericValues = numericValues
                        )
                    )
                }
                .addOnFailureListener { error ->

                    callback(
                        PriceReadResult(
                            lines = emptyList(),
                            numericValues = emptyList(),
                            error = error.message
                                ?: "OCR reading failed."
                        )
                    )
                }
                .addOnCompleteListener {
                    bitmap.recycle()
                    isReading.set(false)
                }

        } catch (error: Exception) {

            bitmap.recycle()
            isReading.set(false)

            callback(
                PriceReadResult(
                    lines = emptyList(),
                    numericValues = emptyList(),
                    error = error.message
                        ?: "OCR processing error."
                )
            )
        }
    }

    fun close() {
        recognizer.close()
    }
}
