package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AttendanceRecord
import com.example.data.AttendanceRepository
import com.example.data.AttendanceStatus
import com.example.data.LocationFix
import com.example.data.SheetsWebhookService
import com.example.data.UiState
import com.example.data.WorkplaceSettings
import com.example.util.LocationHelper
import com.example.util.PhotoWatermarkUtil
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class HistoryFilter(val label: String) {
    ALL("Semua"),
    CHECK_IN("Masuk"),
    CHECK_OUT("Pulang")
}

data class ClockDisplay(
    val timeText: String,
    val dateText: String,
    val watermarkStamp: String
)

data class CheckInFormState(
    val employeeId: String = "",
    val employeeName: String = "",
    val status: AttendanceStatus = AttendanceStatus.CHECK_IN,
    val capturedBitmap: Bitmap? = null,
    val photoBase64: String = "",
    val capturedTimeText: String = "",
    val isCapturingPhoto: Boolean = false,
    val locationFix: LocationFix? = null,
    val isLocating: Boolean = false,
    val locationError: String? = null,
    val isSubmitting: Boolean = false,
    val feedbackMessage: String? = null,
    val feedbackIsError: Boolean = false
)

class AttendanceViewModel(
    private val repository: AttendanceRepository,
    private val sheetsWebhookService: SheetsWebhookService,
    val currentUserId: String,
    initialDisplayName: String
) : ViewModel() {

    private val indonesianLocale = Locale("id", "ID")
    private val timeFormat = SimpleDateFormat("HH:mm:ss", indonesianLocale)
    private val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", indonesianLocale)
    private val watermarkFormat = SimpleDateFormat("EEE, dd MMM yyyy • HH:mm:ss", indonesianLocale)

    private val _clockState = MutableStateFlow(currentClockDisplay())
    val clockState: StateFlow<ClockDisplay> = _clockState.asStateFlow()

    private val _formState = MutableStateFlow(
        CheckInFormState(employeeName = initialDisplayName)
    )
    val formState: StateFlow<CheckInFormState> = _formState.asStateFlow()

    private val _historyFilter = MutableStateFlow(HistoryFilter.ALL)
    val historyFilter: StateFlow<HistoryFilter> = _historyFilter.asStateFlow()

    private var hasPrefilledFromSettings = false

    val attendanceHistory: StateFlow<UiState<List<AttendanceRecord>>> =
        repository.observeAttendance(currentUserId)
            .map<List<AttendanceRecord>, UiState<List<AttendanceRecord>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Gagal memuat riwayat absensi", rootError)
                emit(UiState.Error(rootError.message ?: "Gagal memuat riwayat absensi."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val workplaceSettings: StateFlow<WorkplaceSettings> =
        repository.observeWorkplaceSettings(currentUserId)
            .map { settings -> settings ?: WorkplaceSettings(userId = currentUserId) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                Log.w(TAG, "Gagal memuat pengaturan lokasi", firestoreError ?: error)
                emit(WorkplaceSettings(userId = currentUserId))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = WorkplaceSettings(userId = currentUserId)
            )

    init {
        startClockTicker()
        observeSettingsPrefill()
    }

    private fun startClockTicker() {
        viewModelScope.launch {
            while (isActive) {
                _clockState.value = currentClockDisplay()
                delay(1000L)
            }
        }
    }

    private fun observeSettingsPrefill() {
        viewModelScope.launch {
            workplaceSettings.collect { settings ->
                if (!hasPrefilledFromSettings && (settings.defaultEmployeeId.isNotBlank() || settings.defaultEmployeeName.isNotBlank())) {
                    hasPrefilledFromSettings = true
                    _formState.update { current ->
                        current.copy(
                            employeeId = current.employeeId.ifBlank { settings.defaultEmployeeId },
                            employeeName = current.employeeName.ifBlank { settings.defaultEmployeeName }
                        )
                    }
                }
            }
        }
    }

    private fun currentClockDisplay(): ClockDisplay {
        val now = Date()
        return ClockDisplay(
            timeText = timeFormat.format(now),
            dateText = dateFormat.format(now),
            watermarkStamp = watermarkFormat.format(now)
        )
    }

    fun updateEmployeeId(value: String) {
        _formState.update { it.copy(employeeId = value.take(50)) }
    }

    fun updateEmployeeName(value: String) {
        _formState.update { it.copy(employeeName = value.take(100)) }
    }

    fun updateAttendanceStatus(status: AttendanceStatus) {
        _formState.update { it.copy(status = status) }
    }

    fun setHistoryFilter(filter: HistoryFilter) {
        _historyFilter.value = filter
    }

    fun clearFeedback() {
        _formState.update { it.copy(feedbackMessage = null, feedbackIsError = false) }
    }

    fun setCapturingPhoto(capturing: Boolean) {
        _formState.update { it.copy(isCapturingPhoto = capturing) }
    }

    fun onRawSelfieCaptured(rawBitmap: Bitmap, rotationDegrees: Int, mirrorHorizontally: Boolean) {
        val snapshot = _formState.value
        val stamp = currentClockDisplay().watermarkStamp
        viewModelScope.launch {
            _formState.update { it.copy(isCapturingPhoto = true) }
            val result = withContext(Dispatchers.Default) {
                PhotoWatermarkUtil.processAndWatermarkSelfie(
                    sourceBitmap = rawBitmap,
                    rotationDegrees = rotationDegrees,
                    mirrorHorizontally = mirrorHorizontally,
                    employeeId = snapshot.employeeId,
                    employeeName = snapshot.employeeName,
                    status = snapshot.status,
                    locationFix = snapshot.locationFix,
                    capturedTimeText = stamp
                )
            }
            _formState.update {
                it.copy(
                    capturedBitmap = result.bitmap,
                    photoBase64 = result.base64Jpeg,
                    capturedTimeText = result.capturedTimeText,
                    isCapturingPhoto = false
                )
            }
        }
    }

    fun onPhotoCaptureError(message: String) {
        _formState.update {
            it.copy(
                isCapturingPhoto = false,
                feedbackMessage = message,
                feedbackIsError = true
            )
        }
    }

    fun clearCapturedPhoto() {
        _formState.update {
            it.copy(
                capturedBitmap = null,
                photoBase64 = "",
                capturedTimeText = ""
            )
        }
    }

    fun refreshLocation(context: Context) {
        if (_formState.value.isLocating) return
        viewModelScope.launch {
            _formState.update { it.copy(isLocating = true, locationError = null) }
            val result = LocationHelper.fetchCurrentLocation(context.applicationContext)
            result.fold(
                onSuccess = { fix ->
                    _formState.update {
                        it.copy(
                            locationFix = fix,
                            isLocating = false,
                            locationError = null
                        )
                    }
                },
                onFailure = { error ->
                    _formState.update {
                        it.copy(
                            isLocating = false,
                            locationError = error.localizedMessage ?: "Gagal mendapatkan lokasi GPS."
                        )
                    }
                }
            )
        }
    }

    fun computeDistanceToOffice(fix: LocationFix?, settings: WorkplaceSettings): Double {
        if (fix == null) return 0.0
        return LocationHelper.calculateDistanceMeters(
            fix.latitude,
            fix.longitude,
            settings.officeLatitude,
            settings.officeLongitude
        )
    }

    fun isWithinAllowedRadius(fix: LocationFix?, settings: WorkplaceSettings): Boolean {
        if (fix == null) return false
        if (!settings.geofenceEnabled) return true
        return computeDistanceToOffice(fix, settings) <= settings.maxRadiusMeters
    }

    fun submitAttendance() {
        val state = _formState.value
        val settings = workplaceSettings.value

        if (state.isSubmitting) return
        if (state.employeeId.isBlank()) {
            showError("Isi NIK / ID Karyawan terlebih dahulu.")
            return
        }
        if (state.employeeName.isBlank()) {
            showError("Isi Nama Lengkap terlebih dahulu.")
            return
        }
        if (state.photoBase64.isBlank()) {
            showError("Ambil foto selfie terlebih dahulu.")
            return
        }
        val fix = state.locationFix
        if (fix == null) {
            showError("Kunci lokasi GPS terlebih dahulu.")
            return
        }
        val distanceMeters = computeDistanceToOffice(fix, settings)
        val withinRadius = isWithinAllowedRadius(fix, settings)
        if (settings.geofenceEnabled && !withinRadius) {
            showError(
                String.format(
                    Locale("id", "ID"),
                    "Anda berada %.0f m dari kantor (maks %.0f m). Dekati lokasi kantor lalu perbarui GPS.",
                    distanceMeters,
                    settings.maxRadiusMeters
                )
            )
            return
        }

        val mapsUrl = LocationHelper.buildGoogleMapsUrl(fix.latitude, fix.longitude)
        val capturedStamp = state.capturedTimeText.ifBlank { currentClockDisplay().watermarkStamp }

        viewModelScope.launch {
            _formState.update { it.copy(isSubmitting = true, feedbackMessage = null) }

            val createResult = repository.createAttendanceRecord(
                userId = currentUserId,
                employeeId = state.employeeId,
                employeeName = state.employeeName,
                status = state.status,
                latitude = fix.latitude,
                longitude = fix.longitude,
                accuracyMeters = fix.accuracyMeters.toDouble(),
                distanceMeters = distanceMeters,
                withinRadius = withinRadius,
                mapsUrl = mapsUrl,
                capturedTimeText = capturedStamp,
                photoBase64 = state.photoBase64
            )

            createResult.fold(
                onSuccess = { recordId ->
                    // Save employee ID/Name defaults quietly if not yet saved
                    if (settings.defaultEmployeeId != state.employeeId.trim() ||
                        settings.defaultEmployeeName != state.employeeName.trim()
                    ) {
                        repository.saveWorkplaceSettings(
                            userId = currentUserId,
                            defaultEmployeeId = state.employeeId.trim(),
                            defaultEmployeeName = state.employeeName.trim(),
                            officeName = settings.officeName,
                            officeLatitude = settings.officeLatitude,
                            officeLongitude = settings.officeLongitude,
                            maxRadiusMeters = settings.maxRadiusMeters,
                            geofenceEnabled = settings.geofenceEnabled,
                            sheetsWebhookUrl = settings.sheetsWebhookUrl
                        )
                    }

                    // Optional forward to Google Apps Script (Google Sheets & Drive)
                    var syncedSheets = false
                    if (settings.sheetsWebhookUrl.isNotBlank()) {
                        val webhookRes = sheetsWebhookService.sendAttendance(
                            webhookUrl = settings.sheetsWebhookUrl,
                            employeeId = state.employeeId.trim(),
                            employeeName = state.employeeName.trim(),
                            status = state.status,
                            latitude = fix.latitude,
                            longitude = fix.longitude,
                            accuracyMeters = fix.accuracyMeters.toDouble(),
                            distanceMeters = distanceMeters,
                            mapsUrl = mapsUrl,
                            capturedTimeText = capturedStamp,
                            photoBase64 = state.photoBase64
                        )
                        if (webhookRes.isSuccess) {
                            syncedSheets = true
                            repository.markSyncedToSheets(currentUserId, recordId)
                        }
                    }

                    val nextStatus = if (state.status == AttendanceStatus.CHECK_IN) {
                        AttendanceStatus.CHECK_OUT
                    } else {
                        AttendanceStatus.CHECK_IN
                    }
                    _formState.update {
                        it.copy(
                            isSubmitting = false,
                            capturedBitmap = null,
                            photoBase64 = "",
                            capturedTimeText = "",
                            status = nextStatus,
                            feedbackMessage = if (syncedSheets) {
                                "Absensi tersimpan di cloud & terkirim ke Google Sheets!"
                            } else {
                                "Absensi berhasil dicatat dan tersimpan di cloud!"
                            },
                            feedbackIsError = false
                        )
                    }
                },
                onFailure = { error ->
                    _formState.update {
                        it.copy(
                            isSubmitting = false,
                            feedbackMessage = error.localizedMessage ?: "Gagal menyimpan absensi ke cloud.",
                            feedbackIsError = true
                        )
                    }
                }
            )
        }
    }

    fun deleteAttendance(recordId: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val res = repository.deleteAttendanceRecord(currentUserId, recordId)
            res.fold(
                onSuccess = { onResult("Catatan absensi dihapus.") },
                onFailure = { onResult(it.localizedMessage ?: "Gagal menghapus catatan.") }
            )
        }
    }

    fun saveWorkplaceSettings(
        defaultEmployeeId: String,
        defaultEmployeeName: String,
        officeName: String,
        officeLatitude: Double,
        officeLongitude: Double,
        maxRadiusMeters: Double,
        geofenceEnabled: Boolean,
        sheetsWebhookUrl: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.saveWorkplaceSettings(
                userId = currentUserId,
                defaultEmployeeId = defaultEmployeeId,
                defaultEmployeeName = defaultEmployeeName,
                officeName = officeName,
                officeLatitude = officeLatitude,
                officeLongitude = officeLongitude,
                maxRadiusMeters = maxRadiusMeters,
                geofenceEnabled = geofenceEnabled,
                sheetsWebhookUrl = sheetsWebhookUrl
            )
            res.fold(
                onSuccess = {
                    if (defaultEmployeeId.isNotBlank()) updateEmployeeId(defaultEmployeeId)
                    if (defaultEmployeeName.isNotBlank()) updateEmployeeName(defaultEmployeeName)
                    onResult(true, "Pengaturan lokasi & sinkronisasi berhasil disimpan.")
                },
                onFailure = { error ->
                    onResult(false, error.localizedMessage ?: "Gagal menyimpan pengaturan.")
                }
            )
        }
    }

    private fun showError(msg: String) {
        _formState.update { it.copy(feedbackMessage = msg, feedbackIsError = true) }
    }

    private companion object {
        const val TAG = "AttendanceVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
