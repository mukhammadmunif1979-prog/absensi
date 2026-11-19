package com.example.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.util.Base64
import com.example.data.AttendanceStatus
import com.example.data.LocationFix
import java.io.ByteArrayOutputStream
import java.util.Locale

data class WatermarkedSelfieResult(
    val bitmap: Bitmap,
    val base64Jpeg: String,
    val capturedTimeText: String
)

object PhotoWatermarkUtil {

    fun processAndWatermarkSelfie(
        sourceBitmap: Bitmap,
        rotationDegrees: Int,
        mirrorHorizontally: Boolean,
        employeeId: String,
        employeeName: String,
        status: AttendanceStatus,
        locationFix: LocationFix?,
        capturedTimeText: String
    ): WatermarkedSelfieResult {
        val oriented = transformBitmap(sourceBitmap, rotationDegrees, mirrorHorizontally)
        val scaled = scaleToMaxWidth(oriented, maxWidth = 540)
        if (scaled !== oriented && oriented !== sourceBitmap) {
            oriented.recycle()
        }

        val mutable = scaled.copy(Bitmap.Config.ARGB_8888, true)
        if (scaled !== sourceBitmap) {
            scaled.recycle()
        }

        drawWatermarkOverlay(
            bitmap = mutable,
            capturedTimeText = capturedTimeText,
            employeeId = employeeId,
            employeeName = employeeName,
            status = status,
            locationFix = locationFix
        )

        val base64 = encodeJpegBase64(mutable, quality = 68)
        return WatermarkedSelfieResult(
            bitmap = mutable,
            base64Jpeg = base64,
            capturedTimeText = capturedTimeText
        )
    }

    private fun transformBitmap(
        source: Bitmap,
        rotationDegrees: Int,
        mirrorHorizontally: Boolean
    ): Bitmap {
        val matrix = Matrix()
        if (rotationDegrees != 0) {
            matrix.postRotate(rotationDegrees.toFloat())
        }
        if (mirrorHorizontally) {
            matrix.postScale(-1f, 1f)
        }
        return if (!matrix.isIdentity) {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } else {
            source
        }
    }

    private fun scaleToMaxWidth(source: Bitmap, maxWidth: Int): Bitmap {
        if (source.width <= maxWidth) return source
        val ratio = maxWidth.toFloat() / source.width.toFloat()
        val targetHeight = (source.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, maxWidth, targetHeight, true)
    }

    private fun drawWatermarkOverlay(
        bitmap: Bitmap,
        capturedTimeText: String,
        employeeId: String,
        employeeName: String,
        status: AttendanceStatus,
        locationFix: LocationFix?
    ) {
        val canvas = Canvas(bitmap)
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val bannerHeight = (h * 0.24f).coerceIn(106f, 160f)
        val top = h - bannerHeight

        // Translucent deep teal-black gradient backing for crisp legibility
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, top, 0f, h,
                Color.argb(165, 11, 35, 36),
                Color.argb(232, 7, 22, 23),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, top, w, h, bgPaint)

        // Accent top border stripe (Mint for Check-In, Amber for Check-Out)
        val accentColor = if (status == AttendanceStatus.CHECK_IN) {
            Color.rgb(52, 211, 153)
        } else {
            Color.rgb(245, 158, 11)
        }
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }
        canvas.drawRect(0f, top, w, top + 4f, accentPaint)

        val padX = w * 0.04f
        val line1Size = (w * 0.042f).coerceIn(18f, 24f)
        val line2Size = (w * 0.033f).coerceIn(14f, 19f)
        val line3Size = (w * 0.029f).coerceIn(12f, 16f)

        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = line1Size
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(226, 242, 238)
            textSize = line2Size
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        val gpsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = line3Size
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }

        val statusLabel = if (status == AttendanceStatus.CHECK_IN) "MASUK" else "PULANG"
        val cleanName = employeeName.trim().ifBlank { "Karyawan" }
        val cleanId = employeeId.trim().ifBlank { "-" }
        val identityLine = "$statusLabel • $cleanName ($cleanId)"
        val gpsLine = if (locationFix != null) {
            String.format(
                Locale.US,
                "GPS %.6f, %.6f (±%.0fm)",
                locationFix.latitude,
                locationFix.longitude,
                locationFix.accuracyMeters
            )
        } else {
            "GPS: Koordinat belum terkunci"
        }

        val y1 = top + bannerHeight * 0.34f
        val y2 = top + bannerHeight * 0.62f
        val y3 = top + bannerHeight * 0.86f

        canvas.drawText(capturedTimeText, padX, y1, timePaint)
        canvas.drawText(truncateText(identityLine, infoPaint, w - padX * 2), padX, y2, infoPaint)
        canvas.drawText(truncateText(gpsLine, gpsPaint, w - padX * 2), padX, y3, gpsPaint)
    }

    private fun truncateText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 3 && paint.measureText(text.substring(0, end) + "...") > maxWidth) {
            end--
        }
        return text.substring(0, end) + "..."
    }

    private fun encodeJpegBase64(bitmap: Bitmap, quality: Int): String {
        var currentQuality = quality
        var bytes: ByteArray
        do {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, currentQuality, stream)
            bytes = stream.toByteArray()
            currentQuality -= 10
        } while (bytes.size > 220_000 && currentQuality >= 25)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun decodeBase64ToBitmap(base64: String): Bitmap? {
        if (base64.isBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }
}
