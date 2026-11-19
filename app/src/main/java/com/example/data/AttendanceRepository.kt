package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class AttendanceRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Pengguna harus masuk dengan Google terlebih dahulu.")
    }

    fun observeAttendance(userId: String): Flow<List<AttendanceRecord>> = flow {
        val path = "users/$userId/attendance"
        emitAll(
            db.collection("users").document(userId).collection("attendance")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AttendanceRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeWorkplaceSettings(userId: String): Flow<WorkplaceSettings?> = flow {
        val path = "users/$userId/settings/workplace"
        emitAll(
            db.collection("users").document(userId)
                .collection("settings").document("workplace")
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            WorkplaceSettings::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createAttendanceRecord(
        userId: String,
        employeeId: String,
        employeeName: String,
        status: AttendanceStatus,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double,
        distanceMeters: Double,
        withinRadius: Boolean,
        mapsUrl: String,
        capturedTimeText: String,
        photoBase64: String
    ): Result<String> {
        var docPath = "users/$userId/attendance"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sesi pengguna berubah; menolak penulisan lintas akun." }

            val cleanEmpId = employeeId.trim().take(50)
            val cleanEmpName = employeeName.trim().take(100)
            require(cleanEmpId.isNotEmpty()) { "NIK / ID Karyawan tidak boleh kosong." }
            require(cleanEmpName.isNotEmpty()) { "Nama Lengkap tidak boleh kosong." }
            require(photoBase64.isNotEmpty() && photoBase64.length <= 400_000) {
                "Foto selfie tidak valid atau terlalu besar."
            }
            require(latitude in -90.0..90.0 && longitude in -180.0..180.0) {
                "Koordinat GPS tidak valid."
            }

            val docRef = db.collection("users").document(uid)
                .collection("attendance").document()
            docPath = docRef.path

            val payload = mapOf(
                "userId" to uid,
                "employeeId" to cleanEmpId,
                "employeeName" to cleanEmpName,
                "status" to status.firestoreValue,
                "latitude" to latitude,
                "longitude" to longitude,
                "accuracyMeters" to accuracyMeters.coerceIn(0.0, 100_000.0),
                "distanceMeters" to distanceMeters.coerceIn(0.0, 20_050_000.0),
                "withinRadius" to withinRadius,
                "mapsUrl" to mapsUrl.take(200),
                "capturedTimeText" to capturedTimeText.take(64),
                "photoBase64" to photoBase64,
                "syncedToSheets" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun markSyncedToSheets(userId: String, recordId: String): Result<Unit> {
        val docPath = "users/$userId/attendance/$recordId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sesi pengguna berubah; menolak pembaruan lintas akun." }
            require(recordId.isNotBlank()) { "ID absensi tidak valid." }

            db.collection("users").document(uid)
                .collection("attendance").document(recordId)
                .update(
                    mapOf(
                        "syncedToSheets" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteAttendanceRecord(userId: String, recordId: String): Result<Unit> {
        val docPath = "users/$userId/attendance/$recordId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sesi pengguna berubah; menolak penghapusan lintas akun." }
            require(recordId.isNotBlank()) { "ID absensi tidak valid." }

            db.collection("users").document(uid)
                .collection("attendance").document(recordId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveWorkplaceSettings(
        userId: String,
        defaultEmployeeId: String,
        defaultEmployeeName: String,
        officeName: String,
        officeLatitude: Double,
        officeLongitude: Double,
        maxRadiusMeters: Double,
        geofenceEnabled: Boolean,
        sheetsWebhookUrl: String
    ): Result<Unit> {
        val docPath = "users/$userId/settings/workplace"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sesi pengguna berubah; menolak penyimpanan lintas akun." }

            val clampedLat = officeLatitude.coerceIn(-90.0, 90.0)
            val clampedLng = officeLongitude.coerceIn(-180.0, 180.0)
            val clampedRadius = maxRadiusMeters.coerceIn(10.0, 50_000.0)

            val docRef = db.collection("users").document(uid)
                .collection("settings").document("workplace")
            val existingSnapshot = docRef.get().await()

            if (existingSnapshot.exists()) {
                docRef.update(
                    mapOf(
                        "defaultEmployeeId" to defaultEmployeeId.trim().take(50),
                        "defaultEmployeeName" to defaultEmployeeName.trim().take(100),
                        "officeName" to officeName.trim().take(100),
                        "officeLatitude" to clampedLat,
                        "officeLongitude" to clampedLng,
                        "maxRadiusMeters" to clampedRadius,
                        "geofenceEnabled" to geofenceEnabled,
                        "sheetsWebhookUrl" to sheetsWebhookUrl.trim().take(500),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "defaultEmployeeId" to defaultEmployeeId.trim().take(50),
                        "defaultEmployeeName" to defaultEmployeeName.trim().take(100),
                        "officeName" to officeName.trim().take(100),
                        "officeLatitude" to clampedLat,
                        "officeLongitude" to clampedLng,
                        "maxRadiusMeters" to clampedRadius,
                        "geofenceEnabled" to geofenceEnabled,
                        "sheetsWebhookUrl" to sheetsWebhookUrl.trim().take(500),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }
}
