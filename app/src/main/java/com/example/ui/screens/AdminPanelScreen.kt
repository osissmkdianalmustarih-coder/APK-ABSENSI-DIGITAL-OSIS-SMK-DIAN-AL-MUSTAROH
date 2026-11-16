package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.local.AbsensiEntity
import com.example.data.local.AdminUserEntity
import com.example.data.local.KelasEntity
import com.example.data.local.SiswaEntity
import com.example.data.preferences.AppSettings
import com.example.ui.components.StatusPill
import com.example.ui.theme.AccentGold
import com.example.ui.theme.DangerRed
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.viewmodel.AbsensiViewModel
import com.example.ui.viewmodel.DashboardStats
import com.example.ui.viewmodel.PetugasPerformanceStat
import com.example.ui.viewmodel.ViolationDashboardState

private enum class AdminSubTab(val label: String) {
    REKAP("Rekap"),
    POIN("Poin"),
    SISWA("Siswa & QR"),
    KELAS("Kelas"),
    PETUGAS("Petugas"),
    BROADCAST("Broadcast WA"),
    PENGATURAN("Pengaturan"),
    USERS("Akun Admin")
}

@Composable
fun AdminPanelScreen(
    viewModel: AbsensiViewModel,
    settings: AppSettings,
    adminSession: AdminUserEntity?,
    dashboardStats: DashboardStats,
    violationStats: ViolationDashboardState,
    allAbsensi: List<AbsensiEntity>,
    todayAbsensi: List<AbsensiEntity>,
    allSiswa: List<SiswaEntity>,
    allKelas: List<KelasEntity>,
    petugasStats: List<PetugasPerformanceStat>,
    adminUsers: List<AdminUserEntity>,
    attendedIdsToday: Set<String>
) {
    if (adminSession == null) {
        AdminLoginCard(
            schoolName = settings.schoolName,
            onLogin = { u, p -> viewModel.loginAdmin(u, p) }
        )
        return
    }

    val role = adminSession.role
    val canEditMaster = role == "Pengawas" || role == "Developer"
    val visibleTabs = remember(role) {
        when (role) {
            "Developer" -> AdminSubTab.entries
            "Pengawas" -> AdminSubTab.entries
            else -> listOf(
                AdminSubTab.REKAP,
                AdminSubTab.POIN,
                AdminSubTab.SISWA,
                AdminSubTab.KELAS,
                AdminSubTab.PETUGAS
            )
        }
    }
    var selectedTab by remember(role) { mutableStateOf(AdminSubTab.REKAP) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ROLE BANNER
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = adminSession.username,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            StatusPill(
                                text = role,
                                containerColor = RoyalBlue,
                                contentColor = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when (role) {
                                "Developer" -> "Akses penuh seluruh fitur & manajemen role admin."
                                "Pengawas" -> "Akses operasional penuh, Pengaturan, & Broadcast WA."
                                else -> "Lihat data & ubah pilihan absensi siswa."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.logoutAdmin() },
                        modifier = Modifier.testTag("admin_logout_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Logout")
                    }
                }
            }
        }

        // PROGRESS CARD
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                )
                            )
                        )
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.TrendingUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Progress Kehadiran Hari Ini",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "${dashboardStats.percentage}% (${dashboardStats.attendedTodayCount}/${dashboardStats.totalActiveStudents})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { dashboardStats.percentage / 100f },
                        color = SuccessGreen,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(50))
                    )
                }
            }
        }

        // 4 KPI STATS
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    AdminKpiCard("Total Siswa", dashboardStats.totalActiveStudents.toString(), RoyalBlue, Modifier.weight(1f))
                    AdminKpiCard("Hadir", dashboardStats.attendedTodayCount.toString(), SuccessGreen, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    AdminKpiCard("Belum Absen", dashboardStats.notAttendedTodayCount.toString(), DangerRed, Modifier.weight(1f))
                    AdminKpiCard("Seragam Kurang", dashboardStats.incompleteUniformTodayCount.toString(), Color(0xFFD97706), Modifier.weight(1f))
                }
            }
        }

        // PER-CLASS MONITORING
        if (dashboardStats.perClassStats.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Monitoring Kehadiran per Kelas",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            dashboardStats.perClassStats.forEach { stat ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.width(136.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(stat.className, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = RoyalBlue)
                                            StatusPill(
                                                text = "${stat.percentage}%",
                                                containerColor = if (stat.percentage == 100 && stat.totalActive > 0) SuccessGreen else RoyalBlue,
                                                contentColor = Color.White
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("${stat.attendedToday} / ${stat.totalActive} siswa", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = { stat.percentage / 100f },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(50))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // SUB-TAB SWITCHER
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                visibleTabs.forEach { tab ->
                    FilterChip(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = { Text(tab.label, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }
        }

        // ACTIVE SUB-TAB CONTENT
        item {
            when (selectedTab) {
                AdminSubTab.REKAP -> AdminRekapTab(viewModel, settings, allAbsensi, allKelas)
                AdminSubTab.POIN -> AdminPoinTab(violationStats, allKelas)
                AdminSubTab.SISWA -> AdminSiswaTab(viewModel, settings, canEditMaster, allSiswa, allKelas, attendedIdsToday)
                AdminSubTab.KELAS -> AdminKelasTab(viewModel, canEditMaster, allKelas)
                AdminSubTab.PETUGAS -> AdminPetugasTab(viewModel, canEditMaster, petugasStats)
                AdminSubTab.BROADCAST -> AdminBroadcastTab(viewModel, settings, allSiswa, todayAbsensi, attendedIdsToday)
                AdminSubTab.PENGATURAN -> AdminSettingsTab(viewModel, settings)
                AdminSubTab.USERS -> AdminUsersTab(viewModel, role, adminUsers)
            }
        }
    }
}

@Composable
private fun AdminKpiCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

@Composable
private fun AdminLoginCard(
    schoolName: String,
    onLogin: (String, String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.AdminPanelSettings,
                            contentDescription = "Admin login",
                            tint = RoyalBlue,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Login Panel Admin", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = "Anggota OSIS • Pengawas • Developer\n$schoolName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_username_input")
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showPass = !showPass }) {
                            Icon(
                                imageVector = if (showPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = "Tampilkan password"
                            )
                        }
                    },
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onLogin(username, password) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_password_input")
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onLogin(username, password) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("admin_login_button")
                ) {
                    Text("Masuk Panel Admin", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AccentGold.copy(alpha = 0.2f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Akun awal Developer: username \"admin\" • password \"admin123\" (ubah di tab Akun Admin).",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}
