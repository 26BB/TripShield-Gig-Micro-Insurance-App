package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLocationAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.R
import com.example.data.BengaluruPartnerHospitals
import com.example.data.TripRecord
import com.example.data.TripStatus
import com.example.data.UiState
import com.example.ui.GpsCoordinate
import com.example.ui.components.BadgeTone
import com.example.ui.components.MonospaceValue
import com.example.ui.components.StatusBadge
import com.example.ui.components.TripShieldCard
import com.example.ui.theme.MonoTypography
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

@SuppressLint("MissingPermission")
@Composable
fun LiveMapScreen(
    tripsState: UiState<List<TripRecord>>,
    currentLocation: GpsCoordinate,
    routeBreadcrumbs: List<GpsCoordinate>,
    incidentPin: GpsCoordinate?,
    onLocationUpdate: (lat: Double, lng: Double) -> Unit,
    onDropIncidentPin: (lat: Double, lng: Double) -> Unit,
    onClearIncidentPin: () -> Unit,
    onFileClaimAtPin: () -> Unit
) {
    val context = LocalContext.current
    val trips = (tripsState as? UiState.Success)?.data.orEmpty()
    val activeTrip = trips.firstOrNull { it.tripStatus == TripStatus.ACTIVE }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var showHospitals by remember { mutableStateOf(true) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasLocationPermission =
            result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    // Real hardware GPS stream via FusedLocationProviderClient when granted
    DisposableEffect(hasLocationPermission) {
        if (!hasLocationPermission) {
            onDispose { }
        } else {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    onLocationUpdate(loc.latitude, loc.longitude)
                }
            }
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
                .setMinUpdateIntervalMillis(3000L)
                .build()
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return
                    onLocationUpdate(loc.latitude, loc.longitude)
                }
            }
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            onDispose {
                fusedClient.removeLocationUpdates(callback)
            }
        }
    }

    val currentLatLng = LatLng(currentLocation.lat, currentLocation.lng)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(currentLatLng, 14.5f)
    }

    LaunchedEffect(currentLocation.lat, currentLocation.lng) {
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLng(LatLng(currentLocation.lat, currentLocation.lng))
        )
    }

    val mapsKeyMissing = BuildConfig.MAPS_API_KEY.isBlank() ||
        BuildConfig.MAPS_API_KEY == "YOUR_GOOGLE_MAPS_API_KEY"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("live_map_screen")
    ) {
        if (mapsKeyMissing) {
            // Graceful fallback when Google Maps API key is not configured yet
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                TripShieldCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = "Maps key required",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Google Maps API Key Required",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Add MAPS_API_KEY in the AI Studio Secrets panel to render live Google Maps tiles. GPS tracking and incident pinning remain active below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                TripShieldCard {
                    Text("Live Rider Telemetry", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    MonospaceValue(
                        text = "GPS: ${"%.5f".format(currentLocation.lat)}, ${"%.5f".format(currentLocation.lng)}",
                        style = MonoTypography.Medium
                    )
                    if (activeTrip != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        StatusBadge(
                            text = "${activeTrip.tripCode} · Protected",
                            tone = BadgeTone.PROTECTED,
                            icon = Icons.Outlined.GppGood
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Nearby Cashless Trauma Partners",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    BengaluruPartnerHospitals.forEach { hospital ->
                        Text(
                            text = "• ${hospital.name} (${hospital.area})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
                uiSettings = MapUiSettings(
                    myLocationButtonEnabled = false,
                    zoomControlsEnabled = false,
                    compassEnabled = true
                ),
                onMapClick = { latLng ->
                    onDropIncidentPin(latLng.latitude, latLng.longitude)
                }
            ) {
                // Rider live position marker
                Marker(
                    state = MarkerState(position = currentLatLng),
                    title = if (activeTrip != null) "${activeTrip.tripCode} · Covered" else "Your Position",
                    snippet = activeTrip?.routeSummary ?: "Tap map to drop an incident pin",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN)
                )

                // Active trip coverage zone circle + live breadcrumb polyline
                if (activeTrip != null) {
                    Circle(
                        center = currentLatLng,
                        radius = 450.0,
                        fillColor = Color(0x33006B78),
                        strokeColor = Color(0xFF006B78),
                        strokeWidth = 3f
                    )
                    val routePoints = buildList {
                        add(LatLng(activeTrip.startLat, activeTrip.startLng))
                        routeBreadcrumbs.forEach { add(LatLng(it.lat, it.lng)) }
                        add(currentLatLng)
                    }
                    if (routePoints.size >= 2) {
                        Polyline(
                            points = routePoints,
                            color = Color(0xFF006B78),
                            width = 10f
                        )
                    }
                }

                // Cashless hospital markers
                if (showHospitals) {
                    BengaluruPartnerHospitals.forEach { hospital ->
                        Marker(
                            state = MarkerState(position = LatLng(hospital.lat, hospital.lng)),
                            title = hospital.name,
                            snippet = "Cashless ER · ${hospital.area}",
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                        )
                    }
                }

                // Incident pin marker
                if (incidentPin != null) {
                    Marker(
                        state = MarkerState(position = LatLng(incidentPin.lat, incidentPin.lng)),
                        title = "Pinned Incident Location",
                        snippet = "Tap 'File Claim at Pin' to attach coordinates",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                    )
                }
            }
        }

        // Top floating status & hospital filter overlay
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusBadge(
                        text = if (activeTrip != null) {
                            "${activeTrip.tripCode} · Live Cover"
                        } else {
                            "No Active Trip"
                        },
                        tone = if (activeTrip != null) BadgeTone.PROTECTED else BadgeTone.PENDING,
                        icon = Icons.Outlined.GppGood
                    )
                    MonospaceValue(
                        text = "${"%.4f".format(currentLocation.lat)}, ${"%.4f".format(currentLocation.lng)}",
                        style = MonoTypography.Small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = showHospitals,
                    onClick = { showHospitals = !showHospitals },
                    label = { Text(stringResource(R.string.map_hospitals_toggle)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.LocalHospital,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.testTag("map_hospitals_chip")
                )
                if (!hasLocationPermission) {
                    FilledTonalButton(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        shape = CircleShape,
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .testTag("map_enable_gps_button")
                    ) {
                        Icon(Icons.Outlined.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.map_enable_gps), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Bottom floating incident pin action dock
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.97f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (incidentPin != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Incident Pin Ready",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            MonospaceValue(
                                text = "${"%.5f".format(incidentPin.lat)}, ${"%.5f".format(incidentPin.lng)}",
                                style = MonoTypography.Small,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = onClearIncidentPin,
                            modifier = Modifier.testTag("clear_incident_pin_button")
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear incident pin")
                        }
                    }
                    Button(
                        onClick = onFileClaimAtPin,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("file_claim_at_pin_button")
                    ) {
                        Icon(Icons.Outlined.ReportProblem, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.map_file_claim_here))
                    }
                } else {
                    Text(
                        text = "Tap anywhere on the map or use your live GPS to mark an incident location.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { onDropIncidentPin(currentLocation.lat, currentLocation.lng) },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("drop_incident_pin_button")
                    ) {
                        Icon(Icons.Outlined.AddLocationAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.map_drop_pin))
                    }
                }
            }
        }
    }
}
