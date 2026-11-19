package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.AttendanceStatus
import com.example.data.LocationFix
import com.example.data.WorkplaceSettings
import com.example.ui.components.SelfieCameraCard
import com.example.ui.theme.AmberCheckOut
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MintGlow
import com.example.util.LocationHelper
import com.example.viewmodel.AttendanceViewModel
import com.example.viewmodel.ClockDisplay
import java.util.Locale

@Composable
fun CheckInScreen(
    viewModel: AttendanceViewModel,
    userEmail: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clock by viewModel.clockState.collectAsStateWithLifecycle()
    val form by viewModel.formState.collectAsStateWithLifecycle()
    val settings by viewModel.workplaceSettings.collectAsStateWithLifecycle()

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
            (result[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
        hasLocationPermission = granted
        if (granted) {
            viewModel.refreshLocation(context)
        }
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission && form.locationFix == null && !form.isLocating) {
            viewModel.refreshLocation(context)
        }
    }

    val distanceMeters = remember(form.locationFix, settings) {
        viewModel.computeDistanceToOffice(form.locationFix, settings)
    }
    val withinRadius = remember(form.locationFix, settings) {
        viewModel.isWithinAllowedRadius(form.locationFix, settings)
    }

    val hasValidFormText = form.employeeId.isNotBlank() && form.employeeName.isNotBlank()
    val hasSelfie = form.photoBase64.isNotBlank()
    val hasGpsLock = form.locationFix != null
    val radiusOk = !settings.geofenceEnabled || withinRadius
    val canSubmit = hasValidFormText && hasSelfie && hasGpsLock && radiusOk && !form.isSubmitting

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Real-Time Digital Clock Hero Card
            RealTimeClockCard(clock = clock, userEmail = userEmail, officeName = settings.officeName)

            // 2. Feedback Banner (Success / Error)
            AnimatedVisibility(visible = form.feedbackMessage != null) {
                val isError = form.feedbackIsError
                Surface(
                    color = if (isError) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isError) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = form.feedbackMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isError) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.clearFeedback() }) {
                            Text("OK")
                        }
                    }
                }
            }

            // 3. Employee Input Form Card
            EmployeeFormCard(
                employeeId = form.employeeId,
                employeeName = form.employeeName,
                selectedStatus = form.status,
                onEmployeeIdChange = viewModel::updateEmployeeId,
                onEmployeeNameChange = viewModel::updateEmployeeName,
                onStatusChange = viewModel::updateAttendanceStatus
            )

            // 4. Front Selfie Camera Card with Auto Watermark
            SelfieCameraCard(
                capturedBitmap = form.capturedBitmap,
                isCapturing = form.isCapturingPhoto,
                onRawPhotoCaptured = viewModel::onRawSelfieCaptured,
                onCaptureError = viewModel::onPhotoCaptureError,
                onRetake = viewModel::clearCapturedPhoto
            )

            // 5. GPS Location & Geofence Card
            GpsLocationCard(
                hasLocationPermission = hasLocationPermission,
                locationFix = form.locationFix,
                isLocating = form.isLocating,
                locationError = form.locationError,
                settings = settings,
                distanceMeters = distanceMeters,
                withinRadius = withinRadius,
                onRequestPermission = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                onRefreshLocation = { viewModel.refreshLocation(context) },
                onOpenMaps = { fix ->
                    val uri = Uri.parse(LocationHelper.buildGoogleMapsUrl(fix.latitude, fix.longitude))
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
            )

            // 6. Validation Checklist & Submit CTA
            ValidationAndSubmitCard(
                hasValidFormText = hasValidFormText,
                hasSelfie = hasSelfie,
                hasGpsLock = hasGpsLock,
                geofenceEnabled = settings.geofenceEnabled,
                radiusOk = radiusOk,
                selectedStatus = form.status,
                canSubmit = canSubmit,
                isSubmitting = form.isSubmitting,
                onSubmit = viewModel::submitAttendance
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun RealTimeClockCard(
    clock: ClockDisplay,
    userEmail: String?,
    officeName: String
) {
    val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(EmeraldDeep, EmeraldPrimary)
                )
            )
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val step = 28.dp.toPx()
            var x = step
            while (x < size.width) {
                var y = step
                while (y < size.height) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.06f),
                        radius = 1.6.dp.toPx(),
                        center = Offset(x, y)
                    )
                    y += step
                }
                x += step
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .alpha(pulseAlpha)
                                .clip(CircleShape)
                                .background(MintGlow)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.clock_live_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MintGlow,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = officeName.ifBlank { "Kantor Pusat" },
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = clock.timeText,
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                modifier = Modifier.testTag("realtime_clock_text")
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = clock.dateText,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.92f)
            )

            if (!userEmail.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Akun: $userEmail",
                    style = MaterialTheme.typography.bodySmall,
                    color = MintGlow.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
private fun EmployeeFormCard(
    employeeId: String,
    employeeName: String,
    selectedStatus: AttendanceStatus,
    onEmployeeIdChange: (String) -> Unit,
    onEmployeeNameChange: (String) -> Unit,
    onStatusChange: (AttendanceStatus) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.section_employee_data),
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = employeeId,
                onValueChange = onEmployeeIdChange,
                label = { Text(stringResource(R.string.label_employee_id)) },
                placeholder = { Text(stringResource(R.string.placeholder_employee_id)) },
                leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("employee_id_input"),
                shape = MaterialTheme.shapes.medium
            )

            OutlinedTextField(
                value = employeeName,
                onValueChange = onEmployeeNameChange,
                label = { Text(stringResource(R.string.label_employee_name)) },
                placeholder = { Text(stringResource(R.string.placeholder_employee_name)) },
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("employee_name_input"),
                shape = MaterialTheme.shapes.medium
            )

            Text(
                text = stringResource(R.string.label_attendance_type),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val isCheckIn = selectedStatus == AttendanceStatus.CHECK_IN
                Surface(
                    onClick = { onStatusChange(AttendanceStatus.CHECK_IN) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .testTag("status_check_in_button"),
                    shape = MaterialTheme.shapes.medium,
                    color = if (isCheckIn) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = if (isCheckIn) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Login,
                            contentDescription = null,
                            tint = if (isCheckIn) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Masuk",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isCheckIn) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val isCheckOut = selectedStatus == AttendanceStatus.CHECK_OUT
                Surface(
                    onClick = { onStatusChange(AttendanceStatus.CHECK_OUT) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .testTag("status_check_out_button"),
                    shape = MaterialTheme.shapes.medium,
                    color = if (isCheckOut) AmberCheckOut
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = if (isCheckOut) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            tint = if (isCheckOut) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Pulang",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isCheckOut) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GpsLocationCard(
    hasLocationPermission: Boolean,
    locationFix: LocationFix?,
    isLocating: Boolean,
    locationError: String?,
    settings: WorkplaceSettings,
    distanceMeters: Double,
    withinRadius: Boolean,
    onRequestPermission: () -> Unit,
    onRefreshLocation: () -> Unit,
    onOpenMaps: (LocationFix) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (locationFix != null) Icons.Filled.GpsFixed else Icons.Filled.GpsOff,
                        contentDescription = null,
                        tint = if (locationFix != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.section_gps_location),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Surface(
                    color = if (locationFix != null) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = if (locationFix != null) stringResource(R.string.location_locked)
                        else stringResource(R.string.location_unlocked),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (locationFix != null) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!hasLocationPermission) {
                Text(
                    text = stringResource(R.string.location_permission_rationale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("grant_location_permission_button"),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Filled.GpsFixed, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.btn_grant_location))
                }
            } else {
                if (locationFix != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Lat / Lng",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "%.6f, %.6f",
                                        locationFix.latitude,
                                        locationFix.longitude
                                    ),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Akurasi GPS",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format(Locale("id", "ID"), "±%.1f meter", locationFix.accuracyMeters),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                            if (settings.geofenceEnabled) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = String.format(
                                            Locale("id", "ID"),
                                            "Jarak ke %s",
                                            settings.officeName.ifBlank { "Kantor" }
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Surface(
                                        color = if (withinRadius) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.errorContainer,
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Text(
                                            text = String.format(
                                                Locale("id", "ID"),
                                                "%.0f m / %.0f m • %s",
                                                distanceMeters,
                                                settings.maxRadiusMeters,
                                                if (withinRadius) "Valid" else "Di Luar Radius"
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (withinRadius) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (isLocating) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.location_searching),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (locationError != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = locationError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onRefreshLocation,
                        enabled = !isLocating,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("refresh_location_button"),
                        shape = MaterialTheme.shapes.medium,
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        if (isLocating) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.btn_refresh_location))
                    }

                    if (locationFix != null) {
                        OutlinedButton(
                            onClick = { onOpenMaps(locationFix) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("open_current_maps_button"),
                            shape = MaterialTheme.shapes.medium,
                            contentPadding = PaddingValues(horizontal = 14.dp)
                        ) {
                            Icon(Icons.Filled.Map, contentDescription = "Buka Google Maps", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Maps")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ValidationAndSubmitCard(
    hasValidFormText: Boolean,
    hasSelfie: Boolean,
    hasGpsLock: Boolean,
    geofenceEnabled: Boolean,
    radiusOk: Boolean,
    selectedStatus: AttendanceStatus,
    canSubmit: Boolean,
    isSubmitting: Boolean,
    onSubmit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Checklist Validasi Pengiriman",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ValidationChecklistItem("NIK & Nama Karyawan terisi", hasValidFormText)
            ValidationChecklistItem("Foto selfie ber-watermark diambil", hasSelfie)
            ValidationChecklistItem("Titik koordinat GPS terkunci", hasGpsLock)
            if (geofenceEnabled) {
                ValidationChecklistItem("Berada di dalam radius lokasi kantor", hasGpsLock && radiusOk)
            }

            Spacer(modifier = Modifier.height(4.dp))

            val buttonColor = if (selectedStatus == AttendanceStatus.CHECK_IN) {
                MaterialTheme.colorScheme.primary
            } else {
                AmberCheckOut
            }
            val buttonLabel = if (selectedStatus == AttendanceStatus.CHECK_IN) {
                "Kirim Absensi Masuk (Check-In)"
            } else {
                "Kirim Absensi Pulang (Check-Out)"
            }

            Button(
                onClick = onSubmit,
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .testTag("submit_attendance_button"),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Menyimpan ke Cloud...")
                } else {
                    Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = buttonLabel, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
private fun ValidationChecklistItem(label: String, satisfied: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (satisfied) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (satisfied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (satisfied) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
