package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import com.example.data.LocationFix
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import java.util.Locale

object LocationHelper {

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocation(context: Context): Result<LocationFix> {
        return try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()
            val freshLocation = try {
                fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
            } catch (_: Exception) {
                null
            }

            val location = freshLocation
                ?: try {
                    fusedClient.lastLocation.await()
                } catch (_: Exception) {
                    null
                }
                ?: getFallbackPlatformLocation(context)

            if (location != null) {
                Result.success(
                    LocationFix(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyMeters = location.accuracy.coerceAtLeast(0f),
                        timestampMillis = System.currentTimeMillis()
                    )
                )
            } else {
                Result.failure(
                    IllegalStateException("Sinyal GPS belum ditemukan. Pastikan layanan lokasi aktif lalu coba Perbarui Lokasi.")
                )
            }
        } catch (e: SecurityException) {
            Result.failure(IllegalStateException("Izin lokasi belum diberikan."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun getFallbackPlatformLocation(context: Context): Location? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        var best: Location? = null
        for (provider in providers) {
            val loc = try {
                manager.getLastKnownLocation(provider)
            } catch (_: Exception) {
                null
            }
            if (loc != null && (best == null || loc.accuracy < best.accuracy)) {
                best = loc
            }
        }
        return best
    }

    fun calculateDistanceMeters(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double
    ): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, results)
        return results[0].toDouble().coerceAtLeast(0.0)
    }

    fun buildGoogleMapsUrl(latitude: Double, longitude: Double): String {
        return String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", latitude, longitude)
    }
}
