package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.SheetsWebhookService
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.viewmodel.AttendanceViewModel
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: AttendanceViewModel,
    userEmail: String?,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.workplaceSettings.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()

    var officeName by remember { mutableStateOf(settings.officeName) }
    var officeLatText by remember { mutableStateOf(String.format(Locale.US, "%.6f", settings.officeLatitude)) }
    var officeLngText by remember { mutableStateOf(String.format(Locale.US, "%.6f", settings.officeLongitude)) }
    var maxRadiusText by remember { mutableStateOf(String.format(Locale.US, "%.0f", settings.maxRadiusMeters)) }
    var geofenceEnabled by remember { mutableStateOf(settings.geofenceEnabled) }
    var sheetsWebhookUrl by remember { mutableStateOf(settings.sheetsWebhookUrl) }
    var showCodePreview by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(settings) {
        officeName = settings.officeName
        officeLatText = String.format(Locale.US, "%.6f", settings.officeLatitude)
        officeLngText = String.format(Locale.US, "%.6f", settings.officeLongitude)
        maxRadiusText = String.format(Locale.US, "%.0f", settings.maxRadiusMeters)
        geofenceEnabled = settings.geofenceEnabled
        sheetsWebhookUrl = settings.sheetsWebhookUrl
    }

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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall
            )

            // 1. Workplace Geofence Settings Card
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
                        text = stringResource(R.string.settings_geofence_section),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_geofence_toggle),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Tolak pengiriman absensi jika di luar radius maksimal kantor.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = geofenceEnabled,
                            onCheckedChange = { geofenceEnabled = it },
                            modifier = Modifier.testTag("geofence_toggle_switch")
                        )
                    }

                    OutlinedTextField(
                        value = officeName,
                        onValueChange = { officeName = it.take(100) },
                        label = { Text(stringResource(R.string.settings_office_name)) },
                        leadingIcon = { Icon(Icons.Filled.LocationCity, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = officeLatText,
                            onValueChange = { officeLatText = it },
                            label = { Text(stringResource(R.string.settings_office_lat)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium
                        )
                        OutlinedTextField(
                            value = officeLngText,
                            onValueChange = { officeLngText = it },
                            label = { Text(stringResource(R.string.settings_office_lng)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium
                        )
                    }

                    OutlinedTextField(
                        value = maxRadiusText,
                        onValueChange = { maxRadiusText = it },
                        label = { Text(stringResource(R.string.settings_max_radius)) },
                        supportingText = { Text("Rentang yang didukung: 10 m – 50.000 m (rekomendasi: 100 m)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    )

                    OutlinedButton(
                        onClick = {
                            val fix = formState.locationFix
                            if (fix != null) {
                                officeLatText = String.format(Locale.US, "%.6f", fix.latitude)
                                officeLngText = String.format(Locale.US, "%.6f", fix.longitude)
                                Toast.makeText(
                                    context,
                                    "Koordinat kantor diisi dari GPS saat ini.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                viewModel.refreshLocation(context)
                                Toast.makeText(
                                    context,
                                    "Sedang mengambil sinyal GPS. Coba tekan lagi dalam beberapa detik.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("use_current_gps_button"),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_use_current_gps))
                    }
                }
            }

            // 2. Optional Google Sheets & Google Drive Apps Script Integration
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
                        text = stringResource(R.string.settings_sheets_section),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.settings_sheets_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = sheetsWebhookUrl,
                        onValueChange = { sheetsWebhookUrl = it.take(500) },
                        label = { Text(stringResource(R.string.settings_sheets_url_label)) },
                        placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                        leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sheets_webhook_input"),
                        shape = MaterialTheme.shapes.medium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText("Code.gs", SheetsWebhookService.CODE_GS_TEMPLATE)
                                )
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.settings_code_copied),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("copy_code_gs_button"),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.settings_copy_code_gs))
                        }

                        TextButton(
                            onClick = { showCodePreview = !showCodePreview },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (showCodePreview) "Sembunyikan" else "Lihat")
                        }
                    }

                    AnimatedVisibility(visible = showCodePreview) {
                        Text(
                            text = SheetsWebhookService.CODE_GS_TEMPLATE,
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .padding(12.dp)
                        )
                    }
                }
            }

            // 3. Save Settings CTA
            Button(
                onClick = {
                    val lat = officeLatText.trim().replace(',', '.').toDoubleOrNull()
                    val lng = officeLngText.trim().replace(',', '.').toDoubleOrNull()
                    val radius = maxRadiusText.trim().replace(',', '.').toDoubleOrNull()

                    if (lat == null || lat !in -90.0..90.0 || lng == null || lng !in -180.0..180.0) {
                        Toast.makeText(context, "Format Latitude / Longitude kantor tidak valid.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (radius == null || radius !in 10.0..50_000.0) {
                        Toast.makeText(context, "Radius maksimal harus antara 10 hingga 50.000 meter.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isSaving = true
                    viewModel.saveWorkplaceSettings(
                        defaultEmployeeId = formState.employeeId,
                        defaultEmployeeName = formState.employeeName,
                        officeName = officeName,
                        officeLatitude = lat,
                        officeLongitude = lng,
                        maxRadiusMeters = radius,
                        geofenceEnabled = geofenceEnabled,
                        sheetsWebhookUrl = sheetsWebhookUrl
                    ) { _, msg ->
                        isSaving = false
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("save_settings_button"),
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.settings_save_btn))
            }

            // 4. Account & Sign Out Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Akun Google Terhubung",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = userEmail ?: "Pengguna Terautentikasi",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = onSignOutClick,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("sign_out_button"),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.auth_sign_out),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
