package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class AttendanceStatus(val firestoreValue: String, val labelId: String) {
    CHECK_IN("CHECK_IN", "Check-In (Masuk)"),
    CHECK_OUT("CHECK_OUT", "Check-Out (Pulang)");

    companion object {
        fun fromWireValue(raw: String?): AttendanceStatus =
            entries.firstOrNull { it.firestoreValue == raw } ?: CHECK_IN
    }
}

@Keep
@IgnoreExtraProperties
data class AttendanceRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val employeeId: String = "",
    val employeeName: String = "",
    val status: String = AttendanceStatus.CHECK_IN.firestoreValue,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracyMeters: Double = 0.0,
    val distanceMeters: Double = 0.0,
    val withinRadius: Boolean = true,
    val mapsUrl: String = "",
    val capturedTimeText: String = "",
    val photoBase64: String = "",
    val syncedToSheets: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val attendanceStatus: AttendanceStatus
        get() = AttendanceStatus.fromWireValue(status)
}

@Keep
@IgnoreExtraProperties
data class WorkplaceSettings(
    @DocumentId val id: String = "workplace",
    val userId: String = "",
    val defaultEmployeeId: String = "",
    val defaultEmployeeName: String = "",
    val officeName: String = "Kantor Pusat",
    val officeLatitude: Double = -6.2088,
    val officeLongitude: Double = 106.8456,
    val maxRadiusMeters: Double = 100.0,
    val geofenceEnabled: Boolean = false,
    val sheetsWebhookUrl: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timestampMillis: Long = System.currentTimeMillis()
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
