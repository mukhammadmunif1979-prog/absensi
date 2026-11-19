package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SheetsWebhookService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {
    suspend fun sendAttendance(
        webhookUrl: String,
        employeeId: String,
        employeeName: String,
        status: AttendanceStatus,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double,
        distanceMeters: Double,
        mapsUrl: String,
        capturedTimeText: String,
        photoBase64: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val trimmedUrl = webhookUrl.trim()
        if (!trimmedUrl.startsWith("https://")) {
            return@withContext Result.failure(IllegalArgumentException("URL Web App harus diawali https://"))
        }

        try {
            val json = JSONObject().apply {
                put("employeeId", employeeId)
                put("employeeName", employeeName)
                put("status", if (status == AttendanceStatus.CHECK_IN) "Masuk" else "Pulang")
                put("latitude", latitude)
                put("longitude", longitude)
                put("accuracyMeters", accuracyMeters)
                put("distanceMeters", distanceMeters)
                put("mapsUrl", mapsUrl)
                put("capturedTimeText", capturedTimeText)
                put("photoBase64", photoBase64)
            }

            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(trimmedUrl)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("HTTP ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val CODE_GS_TEMPLATE = """// Tempel kode ini di Google Apps Script (Extensions -> Apps Script pada Google Sheets)
// lalu Deploy -> New Deployment -> Web App (Execute as: Me, Who has access: Anyone).

const FOLDER_ID = 'ISI_FOLDER_ID_GOOGLE_DRIVE_ANDA';
const SHEET_NAME = 'Absensi';

function doPost(e) {
  try {
    const data = JSON.parse(e.postData.contents);
    const folder = DriveApp.getFolderById(FOLDER_ID);
    const bytes = Utilities.base64Decode(data.photoBase64);
    const fileName = 'Selfie_' + data.employeeId + '_' + Date.now() + '.jpg';
    const blob = Utilities.newBlob(bytes, 'image/jpeg', fileName);
    const file = folder.createFile(blob);
    file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
    const photoUrl = file.getUrl();

    const ss = SpreadsheetApp.getActiveSpreadsheet();
    const sheet = ss.getSheetByName(SHEET_NAME) || ss.insertSheet(SHEET_NAME);
    if (sheet.getLastRow() === 0) {
      sheet.appendRow([
        'Waktu Server', 'ID Karyawan', 'Nama', 'Status',
        'Koordinat GPS', 'Link Google Maps', 'Jarak (m)', 'Link Foto Drive'
      ]);
    }
    sheet.appendRow([
      new Date(),
      data.employeeId,
      data.employeeName,
      data.status,
      data.latitude + ', ' + data.longitude,
      data.mapsUrl,
      Math.round(data.distanceMeters * 10) / 10,
      photoUrl
    ]);

    return ContentService
      .createTextOutput(JSON.stringify({ ok: true, photoUrl: photoUrl }))
      .setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService
      .createTextOutput(JSON.stringify({ ok: false, error: err.message }))
      .setMimeType(ContentService.MimeType.JSON);
  }
}"""
    }
}
