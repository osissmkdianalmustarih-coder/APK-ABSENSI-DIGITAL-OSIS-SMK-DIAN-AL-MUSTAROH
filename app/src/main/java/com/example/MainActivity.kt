package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.OsisTopAppBar
import com.example.ui.screens.AdminPanelScreen
import com.example.ui.screens.ManualAttendanceScreen
import com.example.ui.screens.QrScannerScreen
import com.example.ui.theme.AbsensiOsisTheme
import com.example.ui.theme.DangerRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.viewmodel.AbsensiViewModel
import kotlinx.serialization.Serializable

@Serializable
data object RouteScanQr

@Serializable
data object RouteManual

@Serializable
data object RouteAdmin

private data class TopNavItem<T : Any>(
    val route: T,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AbsensiOsisApp()
        }
    }
}

@Composable
fun AbsensiOsisApp(
    viewModel: AbsensiViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val activePetugas by viewModel.activePetugas.collectAsStateWithLifecycle()
    val adminSession by viewModel.adminSession.collectAsStateWithLifecycle()
    val allSiswa by viewModel.allSiswa.collectAsStateWithLifecycle()
    val allKelas by viewModel.allKelas.collectAsStateWithLifecycle()
    val allAbsensi by viewModel.allAbsensi.collectAsStateWithLifecycle()
    val todayAbsensi by viewModel.todayAbsensi.collectAsStateWithLifecycle()
    val attendedIdsToday by viewModel.attendedIdsToday.collectAsStateWithLifecycle()
    val dashboardStats by viewModel.dashboardStats.collectAsStateWithLifecycle()
    val violationStats by viewModel.violationStats.collectAsStateWithLifecycle()
    val petugasPerformance by viewModel.petugasPerformance.collectAsStateWithLifecycle()
    val adminUsers by viewModel.allAdminUsers.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val navItems = remember {
        listOf(
            TopNavItem(
                route = RouteScanQr,
                label = "Scan QR",
                selectedIcon = Icons.Filled.QrCodeScanner,
                unselectedIcon = Icons.Outlined.QrCodeScanner,
                testTag = "nav_scan_qr"
            ),
            TopNavItem(
                route = RouteManual,
                label = "Manual",
                selectedIcon = Icons.Filled.Checklist,
                unselectedIcon = Icons.Outlined.Checklist,
                testTag = "nav_manual"
            ),
            TopNavItem(
                route = RouteAdmin,
                label = "Admin",
                selectedIcon = Icons.Filled.AdminPanelSettings,
                unselectedIcon = Icons.Outlined.AdminPanelSettings,
                testTag = "nav_admin"
            )
        )
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { msg ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = if (msg.isError) "⚠ ${msg.text}" else msg.text
            )
        }
    }

    AbsensiOsisTheme(darkTheme = settings.isDarkTheme) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            Scaffold(
                topBar = {
                    OsisTopAppBar(
                        schoolName = settings.schoolName,
                        attendanceEnabled = settings.attendanceEnabled,
                        soundEnabled = settings.soundEnabled,
                        isDarkTheme = settings.isDarkTheme,
                        onToggleSound = { viewModel.toggleSoundEnabled() },
                        onToggleTheme = { viewModel.toggleDarkTheme() }
                    )
                },
                bottomBar = {
                    if (!useNavigationRail) {
                        NavigationBar(
                            windowInsets = WindowInsets.navigationBars,
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            navItems.forEach { item ->
                                val selected = currentDestination?.hasRoute(item.route::class) == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label
                                        )
                                    },
                                    label = { Text(item.label) },
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                        }
                    }
                },
                snackbarHost = {
                    SnackbarHost(hostState = snackbarHostState) { data ->
                        val isError = data.visuals.message.startsWith("⚠")
                        Snackbar(
                            snackbarData = data,
                            containerColor = if (isError) DangerRed else SuccessGreen,
                            contentColor = Color.White,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                },
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavigationRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            navItems.forEach { item ->
                                val selected = currentDestination?.hasRoute(item.route::class) == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label
                                        )
                                    },
                                    label = { Text(item.label) },
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = RouteScanQr,
                            modifier = Modifier
                                .fillMaxHeight()
                                .widthIn(max = 760.dp)
                                .fillMaxWidth()
                        ) {
                            composable<RouteScanQr> {
                                QrScannerScreen(
                                    viewModel = viewModel,
                                    settings = settings,
                                    activePetugas = activePetugas,
                                    todayRecords = todayAbsensi,
                                    attendedIdsToday = attendedIdsToday
                                )
                            }
                            composable<RouteManual> {
                                ManualAttendanceScreen(
                                    viewModel = viewModel,
                                    settings = settings,
                                    activePetugas = activePetugas,
                                    kelasList = allKelas,
                                    siswaList = allSiswa,
                                    todayRecords = todayAbsensi,
                                    attendedIdsToday = attendedIdsToday
                                )
                            }
                            composable<RouteAdmin> {
                                AdminPanelScreen(
                                    viewModel = viewModel,
                                    settings = settings,
                                    adminSession = adminSession,
                                    dashboardStats = dashboardStats,
                                    violationStats = violationStats,
                                    allAbsensi = allAbsensi,
                                    todayAbsensi = todayAbsensi,
                                    allSiswa = allSiswa,
                                    allKelas = allKelas,
                                    petugasStats = petugasPerformance,
                                    adminUsers = adminUsers,
                                    attendedIdsToday = attendedIdsToday
                                )
                            }
                        }
                    }
                }
            }

            if (isBusy) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "Memproses data...",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
