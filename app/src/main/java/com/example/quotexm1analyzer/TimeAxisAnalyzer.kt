
package com.example.quotexm1analyzer

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs

data class TimeAxisPoint(
    val x: Int,
    val minuteOfDay: Double
)

data class TimeAxisResult(
    val success: Boolean,
    val message: String,
    val points: List<TimeAxisPoint>
) {

    /**
     * Returns the chart time at an X coordinate.
     * Returns null if X is outside the known time labels.
     */
    fun minuteAtX(x: Int): Double? {
        if (!success || points.size < 2) return null

        val sorted = points.sortedBy { it.x }

        if (x < sorted.first().x || x > sorted.last().x) {
            return null
        }

        for (i in 0 until sorted.lastIndex) {
            val first = sorted[i]
            val second = sorted[i + 1]

            if (x in first.x..second.x) {
                val width = second.x - first.x
                if (width <= 0) return null

                val fraction =
                    (x - first.x).toDouble() / width

                return first.minuteOfDay +
                    (second.minuteOfDay - first.minuteOfDay) *
                    fraction
            }
        }

        return null
    }

    /**
     * Converts chart time into a local-device timestamp.
     * The nearest calendar day to the capture time is selected.
     */
    fun timestampAtX(
        x: Int,
        capturedAtMillis: Long
    ): Long? {

        val chartMinute = minuteAtX(x) ?: return null

        val minuteOfDay =
            ((chartMinute % 1440.0) + 1440.0) % 1440.0

        val timeZone = TimeZone.getDefault()

        val base = Calendar.getInstance(timeZone).apply {
            timeInMillis = capturedAtMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val candidates = (-1..1).map { dayOffset ->

            val calendar = base.clone() as Calendar

            calendar.add(Calendar.DAY_OF_MONTH, dayOffset)

            calendar.add(
                Calendar.MINUTE,
                minuteOfDay.toInt()
            )

            calendar.timeInMillis
        }

        return candidates.minByOrNull {
            abs(it - capturedAtMillis)
        }
    }
}

class TimeAxisAnalyzer {

    companion object {
        private val TIME_PATTERN =
            Regex("""(?<!\d)([01]?\d|2[0-3]):([0-5]\d)(?!\d)""")

        private const val MIN_LABELS = 2
        private const val MIN_BOTTOM_RATIO = 0.68
        private const val MAX_BOTTOM_RATIO = 0.94
    }

    fun analyze(
        lines: List<PriceTextLine>,
        screenWidth: Int,
        screenHeight: Int
    ): TimeAxisResult {

        if (screenWidth <= 0 || screenHeight <= 0) {
            return failure("Screen size valid nahi hai.")
        }

        val rawPoints = lines.mapNotNull { line ->

            val centerX = (line.left + line.right) / 2
            val centerY = (line.top + line.bottom) / 2

            val yRatio =
                centerY.toDouble() / screenHeight

            if (yRatio < MIN_BOTTOM_RATIO ||
                yRatio > MAX_BOTTOM_RATIO ||
                centerX < screenWidth * 0.03 ||
                centerX > screenWidth * 0.92
            ) {
                return@mapNotNull null
            }

            val match = TIME_PATTERN.find(line.text)
                ?: return@mapNotNull null

            val hour = match.groupValues[1].toIntOrNull()
                ?: return@mapNotNull null

            val minute = match.groupValues[2].toIntOrNull()
                ?: return@mapNotNull null

            val minuteOfDay = hour * 60 + minute

            TimeAxisPoint(
                x = centerX,
                minuteOfDay = minuteOfDay.toDouble()
            )
        }.sortedBy { it.x }
            .distinctBy { "${it.x}_${it.minuteOfDay}" }

        if (rawPoints.size < MIN_LABELS) {
            return failure(
                "Chart ke neeche kam az kam 2 time labels zaroori hain."
            )
        }

        val unwrapped = mutableListOf<TimeAxisPoint>()

        var dayOffset = 0.0
        var previousMinute: Double? = null

        for (point in rawPoints) {

            var minute = point.minuteOfDay + dayOffset

            val previous = previousMinute

            if (previous != null && minute < previous) {

                val difference = previous - minute

                if (difference > 720.0) {
                    dayOffset += 1440.0
                    minute += 1440.0
                } else {
                    continue
                }
            }

            if (previousMinute != null &&
                minute <= previousMinute!!
            ) {
                continue
            }

            unwrapped.add(
                TimeAxisPoint(
                    x = point.x,
                    minuteOfDay = minute
                )
            )

            previousMinute = minute
        }

        if (unwrapped.size < MIN_LABELS) {
            return failure(
                "Time labels ka order confirm nahi hua."
            )
        }

        for (i in 1 until unwrapped.size) {

            val timeGap =
                unwrapped[i].minuteOfDay -
                unwrapped[i - 1].minuteOfDay

            if (timeGap <= 0.0 || timeGap > 120.0) {
                return failure(
                    "Time labels ke darmiyan gap valid nahi hai."
                )
            }
        }

        return TimeAxisResult(
            success = true,
            message =
                "${unwrapped.size} chart time labels detect huay.",
            points = unwrapped
        )
    }

    private fun failure(message: String) =
        TimeAxisResult(
            success = false,
            message = message,
            points = emptyList()
        )
}
