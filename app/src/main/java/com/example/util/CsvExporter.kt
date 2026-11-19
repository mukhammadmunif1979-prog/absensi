package com.example.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.AttendanceRecord
import com.example.data.AttendanceStatus
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {

    fun generateCsvContent(records: List<AttendanceRecord>): String {
        val sb = StringBuilder()
        sb.appendLine(
            "Waktu,NIK / ID Karyawan,Nama Lengkap,Jenis Absensi,Latitude,Longitude,Akurasi (m),Jarak Kantor (m),Status Radius,Link Google Maps,Sync Google Sheets"
        )
        for (record in records) {
            val statusText = if (record.attendanceStatus == AttendanceStatus.CHECK_IN) {
                "Check-In (Masuk)"
            } else {
                "Check-Out (Pulang)"
            }
            val radiusText = if (record.withinRadius) "Dalam Radius" else "Di Luar Radius"
            val sheetsText = if (record.syncedToSheets) "Terkirim" else "Cloud Only"

            sb.appendLine(
                listOf(
                    escapeCsv(record.capturedTimeText),
                    escapeCsv(record.employeeId),
                    escapeCsv(record.employeeName),
                    escapeCsv(statusText),
                    String.format(Locale.US, "%.6f", record.latitude),
                    String.format(Locale.US, "%.6f", record.longitude),
                    String.format(Locale.US, "%.1f", record.accuracyMeters),
                    String.format(Locale.US, "%.1f", record.distanceMeters),
                    escapeCsv(radiusText),
                    escapeCsv(record.mapsUrl),
                    escapeCsv(sheetsText)
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    fun suggestedFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "absensi_karyawan_$stamp.csv"
    }

    fun createShareIntent(context: Context, records: List<AttendanceRecord>): Result<Intent> {
        return try {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(exportDir, suggestedFileName())
            file.writeText(generateCsvContent(records), Charsets.UTF_8)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Rekap Absensi Karyawan - AbsensiKu")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Result.success(Intent.createChooser(shareIntent, "Bagikan File CSV Absensi"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun escapeCsv(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        return if (needsQuotes) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
