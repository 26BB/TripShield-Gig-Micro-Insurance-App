package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.authStateFlow
import com.example.auth.signOutRider
import com.example.data.configureAppCheck
import com.example.ui.TripShieldViewModel
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ClaimsScreen
import com.example.ui.screens.CoverScreen
import com.example.ui.screens.LedgerScreen
import com.example.ui.screens.LiveMapScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.theme.TripShieldTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth

private enum class TripShieldDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
) {
    COVER("cover", R.string.nav_cover, Icons.Outlined.Shield),
    MAP("live_map", R.string.nav_map, Icons.Outlined.Map),
    CLAIMS("claims", R.string.nav_claims, Icons.Outlined.Description),
    LEDGER("ledger", R.string.nav_ledger, Icons.Outlined.AccountBalanceWallet),
    PROFILE("profile", R.string.nav_profile, Icons.Outlined.Person)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent BEFORE setContent or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            TripShieldTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    TripShieldRoot()
                }
            }
        }
    }
}

@Composable
private fun TripShieldRoot() {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { /* AuthStateListener updates currentUser reactively */ })
    } else {
        key(user.uid) {
            TripShieldAppShell(user = user)
        }
    }
}

@Composable
private fun TripShieldAppShell(user: FirebaseUser) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: TripShieldViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                TripShieldViewModel.create(
                    appContext = app,
                    userId = user.uid,
                    displayName = user.displayName,
                    email = user.email
                )
            }
        }
    )

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val tripsState by viewModel.tripsState.collectAsStateWithLifecycle()
    val claimsState by viewModel.claimsState.collectAsStateWithLifecycle()
    val ledgerState by viewModel.ledgerState.collectAsStateWithLifecycle()
    val advisorState by viewModel.advisorState.collectAsStateWithLifecycle()
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()
    val actionInProgress by viewModel.actionInProgress.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()
    val routeBreadcrumbs by viewModel.routeBreadcrumbs.collectAsStateWithLifecycle()
    val incidentPin by viewModel.incidentPin.collectAsStateWithLifecycle()
    val openClaimFromPin by viewModel.openClaimSheetFromPin.collectAsStateWithLifecycle()

    var selectedDestination by rememberSaveable { mutableStateOf(TripShieldDestination.COVER) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSnackbar()
        }
    }

    // System back from secondary tabs returns cleanly to Cover home tab
    BackHandler(enabled = selectedDestination != TripShieldDestination.COVER) {
        selectedDestination = TripShieldDestination.COVER
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.surface,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavigationRail) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets(0, 0, 0, 0),
                            modifier = Modifier
                                .height(80.dp)
                                .testTag("bottom_navigation_bar")
                        ) {
                            TripShieldDestination.entries.forEach { dest ->
                                val selected = selectedDestination == dest
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { selectedDestination = dest },
                                    icon = {
                                        Icon(
                                            imageVector = dest.icon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(dest.labelRes),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag("nav_item_${dest.route}")
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxHeight()
                            .testTag("adaptive_navigation_rail")
                    ) {
                        TripShieldDestination.entries.forEach { dest ->
                            val selected = selectedDestination == dest
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectedDestination = dest },
                                icon = {
                                    Icon(
                                        imageVector = dest.icon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(dest.labelRes),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag("rail_item_${dest.route}")
                            )
                        }
                    }
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val contentModifier = if (selectedDestination == TripShieldDestination.MAP) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .fillMaxHeight()
                            .widthIn(max = 840.dp)
                            .fillMaxWidth()
                    }

                    Box(modifier = contentModifier) {
                        when (selectedDestination) {
                            TripShieldDestination.COVER -> CoverScreen(
                                profileState = profileState,
                                tripsState = tripsState,
                                ttsState = ttsState,
                                actionInProgress = actionInProgress,
                                onActivateTrip = { platform, route ->
                                    viewModel.activateTripCover(platform, route)
                                },
                                onEndTrip = { trip -> viewModel.endActiveTrip(trip) },
                                onListenStatus = { viewModel.speakCurrentCoverageStatus() },
                                onStopSpeech = { viewModel.stopSpeech() },
                                onOpenMap = { selectedDestination = TripShieldDestination.MAP },
                                onOpenLedger = { selectedDestination = TripShieldDestination.LEDGER }
                            )

                            TripShieldDestination.MAP -> LiveMapScreen(
                                tripsState = tripsState,
                                currentLocation = currentLocation,
                                routeBreadcrumbs = routeBreadcrumbs,
                                incidentPin = incidentPin,
                                onLocationUpdate = { lat, lng -> viewModel.updateLiveGps(lat, lng) },
                                onDropIncidentPin = { lat, lng -> viewModel.setIncidentPin(lat, lng) },
                                onClearIncidentPin = { viewModel.clearIncidentPin() },
                                onFileClaimAtPin = {
                                    viewModel.requestOpenClaimWithPin()
                                    selectedDestination = TripShieldDestination.CLAIMS
                                }
                            )

                            TripShieldDestination.CLAIMS -> ClaimsScreen(
                                claimsState = claimsState,
                                tripsState = tripsState,
                                advisorState = advisorState,
                                ttsState = ttsState,
                                incidentPin = incidentPin,
                                openClaimSheetImmediately = openClaimFromPin,
                                actionInProgress = actionInProgress,
                                onConsumeOpenClaimSheet = { viewModel.consumeOpenClaimWithPin() },
                                onFileClaim = { tripId, tripCode, type, desc, amount, hasPhoto, outside ->
                                    viewModel.fileClaim(
                                        tripId = tripId,
                                        tripCode = tripCode,
                                        incidentType = type,
                                        description = desc,
                                        requestedInr = amount,
                                        evidenceAttached = hasPhoto,
                                        outsideWindow = outside
                                    )
                                },
                                onWithdrawClaim = { claim -> viewModel.withdrawPendingClaim(claim) },
                                onAskAdvisor = { q -> viewModel.askClaimAdvisor(q) },
                                onResetAdvisor = { viewModel.resetAdvisor() },
                                onSpeakText = { label, text -> viewModel.speakAlert(label, text) },
                                onStopSpeech = { viewModel.stopSpeech() }
                            )

                            TripShieldDestination.LEDGER -> LedgerScreen(
                                profileState = profileState,
                                ledgerState = ledgerState,
                                actionInProgress = actionInProgress,
                                onTopUpWallet = { viewModel.topUpWallet(200.0) }
                            )

                            TripShieldDestination.PROFILE -> ProfileScreen(
                                photoUrl = user.photoUrl?.toString(),
                                profileState = profileState,
                                actionInProgress = actionInProgress,
                                onSaveSettings = { name, vehicle, fleet, voice ->
                                    viewModel.saveRiderSettings(name, vehicle, fleet, voice)
                                },
                                onSignOut = {
                                    viewModel.stopSpeech()
                                    signOutRider(credentialManager = credentialManager, scope = scope)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
