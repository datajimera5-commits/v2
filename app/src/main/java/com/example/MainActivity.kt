package com.example

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ControlCamera
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.ControlCamera
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.local.GenerationEntity
import com.example.data.local.LicenseStatus
import com.example.ui.components.ResultPreviewDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FireModeStudioScreen
import com.example.ui.screens.ImageStudioScreen
import com.example.ui.screens.MotionControlScreen
import com.example.ui.screens.ProfileAndSubscriptionScreen
import com.example.ui.screens.SettingsBottomSheet
import com.example.ui.screens.SplashAndLicenseScreen
import com.example.ui.theme.BackgroundObsidian
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.FireOrange
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextOffWhite
import com.example.ui.theme.VidoProTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.StudioViewModel

object VidoProRoutes {
    const val DASHBOARD = "dashboard"
    const val FIRE_STUDIO = "fire_studio"
    const val IMAGE_STUDIO = "image_studio"
    const val MOTION_STUDIO = "motion_studio"
    const val PROFILE = "profile"
}

private data class NavTabItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setContent {
            VidoProTheme {
                VidoProAppRoot()
            }
        }
    }
}

@Composable
fun VidoProAppRoot() {
    val context = LocalContext.current
    val mainViewModel: MainViewModel = viewModel(factory = MainViewModel.provideFactory(context))
    val studioViewModel: StudioViewModel = viewModel(factory = StudioViewModel.provideFactory(context))

    val splashFinished by mainViewModel.splashFinished.collectAsStateWithLifecycle()
    val session by mainViewModel.sessionPrefs.collectAsStateWithLifecycle()
    val generations by mainViewModel.generations.collectAsStateWithLifecycle()
    val bannerMessage by mainViewModel.statusBannerMessage.collectAsStateWithLifecycle()
    val licenseLoading by mainViewModel.licenseActionLoading.collectAsStateWithLifecycle()
    val plans by mainViewModel.plans.collectAsStateWithLifecycle()
    val tickets by mainViewModel.tickets.collectAsStateWithLifecycle()
    val isSyncingAccount by mainViewModel.syncingAccount.collectAsStateWithLifecycle()

    val fireState by studioViewModel.fireState.collectAsStateWithLifecycle()
    val imageState by studioViewModel.imageState.collectAsStateWithLifecycle()
    val motionState by studioViewModel.motionState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var selectedHistoryEntity by remember { mutableStateOf<GenerationEntity?>(null) }

    val isAuthorized = splashFinished &&
        (session.licenseStatus == LicenseStatus.ACTIVE || session.licenseStatus == LicenseStatus.TRIAL)

    LaunchedEffect(bannerMessage, isAuthorized) {
        val msg = bannerMessage
        if (isAuthorized && !msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            mainViewModel.clearBannerMessage()
        }
    }

    LaunchedEffect(generations, fireState.activeOrder?.orderUid) {
        val activeUid = fireState.activeOrder?.orderUid
        if (!activeUid.isNullOrBlank()) {
            studioViewModel.refreshActiveOrderFromDb(activeUid)
        }
    }

    if (!isAuthorized) {
        SplashAndLicenseScreen(
            splashFinished = splashFinished,
            session = session,
            plans = plans,
            isLoading = licenseLoading,
            bannerMessage = bannerMessage,
            onDismissBanner = mainViewModel::clearBannerMessage,
            onActivateLicense = mainViewModel::activateLicense,
            onStartTrial = mainViewModel::startSevenDayTrial,
            onRetryUnblocked = mainViewModel::resetLicenseGate
        )
        return
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: VidoProRoutes.DASHBOARD

    val navTabs = remember {
        listOf(
            NavTabItem(
                route = VidoProRoutes.DASHBOARD,
                labelRes = R.string.nav_home,
                selectedIcon = Icons.Rounded.Dashboard,
                unselectedIcon = Icons.Outlined.Dashboard,
                testTag = "nav_tab_dashboard"
            ),
            NavTabItem(
                route = VidoProRoutes.FIRE_STUDIO,
                labelRes = R.string.nav_fire,
                selectedIcon = Icons.Rounded.LocalFireDepartment,
                unselectedIcon = Icons.Outlined.LocalFireDepartment,
                testTag = "nav_tab_fire"
            ),
            NavTabItem(
                route = VidoProRoutes.MOTION_STUDIO,
                labelRes = R.string.nav_motion,
                selectedIcon = Icons.Rounded.ControlCamera,
                unselectedIcon = Icons.Outlined.ControlCamera,
                testTag = "nav_tab_motion"
            ),
            NavTabItem(
                route = VidoProRoutes.PROFILE,
                labelRes = R.string.nav_profile,
                selectedIcon = Icons.Rounded.Person,
                unselectedIcon = Icons.Outlined.Person,
                testTag = "nav_tab_profile"
            )
        )
    }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundObsidian)
    ) {
        val useNavRail = maxWidth >= 700.dp

        Scaffold(
            containerColor = BackgroundObsidian,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = SurfaceDark,
                        tonalElevation = 0.dp
                    ) {
                        navTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            val activeAccent = if (tab.route == VidoProRoutes.FIRE_STUDIO) FireOrange else ElectricBlue
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = activeAccent,
                                    selectedTextColor = activeAccent,
                                    indicatorColor = SurfaceElevated,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
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
                if (useNavRail) {
                    NavigationRail(
                        containerColor = SurfaceDark,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        navTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            val activeAccent = if (tab.route == VidoProRoutes.FIRE_STUDIO) FireOrange else ElectricBlue
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = activeAccent,
                                    selectedTextColor = activeAccent,
                                    indicatorColor = SurfaceElevated,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = VidoProRoutes.DASHBOARD
                    ) {
                        composable(VidoProRoutes.DASHBOARD) {
                            DashboardScreen(
                                session = session,
                                recentGenerations = generations,
                                onOpenFireStudio = { navigateToTab(VidoProRoutes.FIRE_STUDIO) },
                                onOpenImageStudio = { navController.navigate(VidoProRoutes.IMAGE_STUDIO) },
                                onOpenMotionStudio = { navigateToTab(VidoProRoutes.MOTION_STUDIO) },
                                onOpenProfile = { navigateToTab(VidoProRoutes.PROFILE) },
                                onOpenSettings = { showSettingsSheet = true },
                                onSelectGeneration = { selectedHistoryEntity = it }
                            )
                        }
                        composable(VidoProRoutes.FIRE_STUDIO) {
                            FireModeStudioScreen(
                                uiState = fireState,
                                session = session,
                                onPromptChange = studioViewModel::updateFirePrompt,
                                onMagicEnhance = studioViewModel::magicEnhancePrompt,
                                onAddReferenceUris = studioViewModel::addReferenceUris,
                                onRemoveReferenceUri = studioViewModel::removeReferenceUri,
                                onAspectRatioChange = studioViewModel::setFireAspectRatio,
                                onDurationChange = studioViewModel::setFireDuration,
                                onIgnite = studioViewModel::igniteFireMode,
                                onPollOrder = studioViewModel::pollActiveOrder,
                                onDismissOrder = studioViewModel::dismissActiveOrder,
                                onOpenResultModal = studioViewModel::openResultModal,
                                onCloseResultModal = studioViewModel::closeResultModal,
                                onDownloadAsset = { entity ->
                                    mainViewModel.exportGeneration(entity)
                                },
                                onUpscaleAsset = { entity ->
                                    mainViewModel.upscaleGeneration(entity)
                                }
                            )
                        }
                        composable(VidoProRoutes.IMAGE_STUDIO) {
                            ImageStudioScreen(
                                uiState = imageState,
                                session = session,
                                onPromptChange = studioViewModel::updateImagePrompt,
                                onAspectRatioChange = studioViewModel::setImageAspectRatio,
                                onStyleChange = studioViewModel::setImageStyle,
                                onGenerate = studioViewModel::generateImage,
                                onOpenEntityModal = { selectedHistoryEntity = it },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(VidoProRoutes.MOTION_STUDIO) {
                            MotionControlScreen(
                                uiState = motionState,
                                session = session,
                                onSubjectChange = studioViewModel::updateMotionSubject,
                                onCameraMoveChange = studioViewModel::setCameraMove,
                                onIntensityChange = studioViewModel::setMotionIntensity,
                                onDurationChange = studioViewModel::setMotionDuration,
                                onGenerateMotionPlan = studioViewModel::generateMotionPlan,
                                onSendToFireMode = { suggestedPrompt, dur ->
                                    studioViewModel.applyMotionToFireStudio(suggestedPrompt, dur)
                                    navigateToTab(VidoProRoutes.FIRE_STUDIO)
                                }
                            )
                        }
                        composable(VidoProRoutes.PROFILE) {
                            ProfileAndSubscriptionScreen(
                                session = session,
                                securityStatus = mainViewModel.securityStatus,
                                plans = plans,
                                tickets = tickets,
                                isSyncing = isSyncingAccount,
                                generations = generations,
                                onSyncAccount = mainViewModel::syncAccountFromServer,
                                onRequestDeviceReset = mainViewModel::requestDeviceReset,
                                onClaimReferral = mainViewModel::claimReferralCode,
                                onSubmitTicket = mainViewModel::submitSupportTicket,
                                onSelectGeneration = { selectedHistoryEntity = it },
                                onDeleteGeneration = mainViewModel::deleteGeneration,
                                onOpenSettings = { showSettingsSheet = true }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        SettingsBottomSheet(
            session = session,
            securityStatus = mainViewModel.securityStatus,
            onDismiss = { showSettingsSheet = false },
            onToggleHaptics = mainViewModel::setHapticsEnabled,
            onResetLicenseGate = mainViewModel::resetLicenseGate
        )
    }

    selectedHistoryEntity?.let { entity ->
        // Keep modal synced with latest Room record (e.g. after Download or 4K Upscale)
        val liveEntity = generations.firstOrNull { it.id == entity.id } ?: entity
        ResultPreviewDialog(
            entity = liveEntity,
            hapticsEnabled = session.hapticsEnabled,
            onDismiss = { selectedHistoryEntity = null },
            onDownload = mainViewModel::exportGeneration,
            onUpscale = mainViewModel::upscaleGeneration
        )
    }
}
