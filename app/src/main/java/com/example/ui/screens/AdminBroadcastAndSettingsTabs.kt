package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.AbsensiEntity
import com.example.data.local.AdminUserEntity
import com.example.data.local.SiswaEntity
import com.example.data.local.UniformLabels
import com.example.data.preferences.AppSettings
import com.example.ui.components.StatusPill
import com.example.ui.theme.DangerRed
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.viewmodel.AbsensiViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminBroadcastTab(
    viewModel: AbsensiViewModel,
    settings: AppSettings,
    siswaList: List<SiswaEntity>,
    todayAbsensi: List<AbsensiEntity>,
    attendedIdsToday: Set<String>
) {
    val context = LocalContext.current
    var targetMode by remember { mutableStateOf("ALL") }
    var targetKelas by remember { mutableStateOf("") }
    var manualSelectedIds by remember { mutableStateOf(setOf<String>()) }
    var messageText by remember {
        mutableStateOf(
            "📢 *INFORMASI SEKOLAH*\nAssalamu’alaikum.\nYth. Orang Tua/Wali *{nama}* (Kelas *{kelas}*),\n[Isi pengumuman di sini]\nTerima kasih."
        )
    }

    val classOptions = remember(siswaList) {
        siswaList.map { it.kelas }.distinct().filter { it.isNotBlank() }.sorted()
    }

    val seragamKurangIds = remember(todayAbsensi) {
        todayAbsensi.filter { it.statusSeragam == "Tidak Lengkap" }.map { it.idSiswa }.toSet()
    }

    val eligibleRecipients = remember(
        siswaList,
        targetMode,
        targetKelas,
        attendedIdsToday,
        seragamKurangIds,
        manualSelectedIds
    ) {
        siswaList.filter { s ->
            if (!s.status.equals("Aktif", ignoreCase = true) || s.waOrtu.isBlank()) return@filter false
            when (targetMode) {
                "ALL" -> true
                "KELAS" -> targetKelas.isEmpty() || s.kelas.equals(targetKelas, ignoreCase = true)
                "BELUM_ABSEN" -> (targetKelas.isEmpty() || s.kelas.equals(targetKelas, ignoreCase = true)) && !attendedIdsToday.contains(s.id)
                "SERAGAM_KURANG" -> (targetKelas.isEmpty() || s.kelas.equals(targetKelas, ignoreCase = true)) && seragamKurangIds.contains(s.id)
                "MANUAL_SELECT" -> manualSelectedIds.contains(s.id)
                else -> false
            }
        }
    }

    val sampleStudent = eligibleRecipients.firstOrNull() ?: SiswaEntity("SIS-0001", "Ahmad", "XI KK")
    val previewText = messageText
        .replace("{nama}", sampleStudent.nama, ignoreCase = true)
        .replace("{kelas}", sampleStudent.kelas, ignoreCase = true)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Broadcast WhatsApp Ortu", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    StatusPill(
                        text = "${eligibleRecipients.size} Siap",
                        containerColor = SuccessGreen,
                        contentColor = Color.White
                    )
                }

                Text("Target Penerima:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val modes = listOf(
                        "ALL" to "Semua Aktif",
                        "KELAS" to "Per Kelas",
                        "BELUM_ABSEN" to "Belum Absen",
                        "SERAGAM_KURANG" to "Seragam Kurang",
                        "MANUAL_SELECT" to "Pilih Manual"
                    )
                    modes.forEach { (key, label) ->
                        FilterChip(
                            selected = targetMode == key,
                            onClick = { targetMode = key },
                            label = { Text(label) }
                        )
                    }
                }

                if (targetMode in listOf("KELAS", "BELUM_ABSEN", "SERAGAM_KURANG")) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(selected = targetKelas.isEmpty(), onClick = { targetKelas = "" }, label = { Text("Semua Kelas") })
                        classOptions.forEach { k ->
                            FilterChip(selected = targetKelas == k, onClick = { targetKelas = k }, label = { Text(k) })
                        }
                    }
                }

                if (targetMode == "MANUAL_SELECT") {
                    val withWa = siswaList.filter { it.status == "Aktif" && it.waOrtu.isNotBlank() }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (withWa.isEmpty()) {
                                Text("Belum ada siswa dengan nomor WA orang tua.", style = MaterialTheme.typography.bodySmall)
                            } else {
                                withWa.forEach { s ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                manualSelectedIds = if (manualSelectedIds.contains(s.id)) {
                                                    manualSelectedIds - s.id
                                                } else {
                                                    manualSelectedIds + s.id
                                                }
                                            }
                                    ) {
                                        Checkbox(
                                            checked = manualSelectedIds.contains(s.id),
                                            onCheckedChange = { checked ->
                                                manualSelectedIds = if (checked) manualSelectedIds + s.id else manualSelectedIds - s.id
                                            }
                                        )
                                        Text("${s.nama} (${s.kelas})", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }

                Text("Template Cepat:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(onClick = {
                        messageText = "📢 *INFORMASI SEKOLAH*\nAssalamu’alaikum.\nYth. Orang Tua/Wali *{nama}* (Kelas *{kelas}*),\n[Isi pesan]\nTerima kasih."
                    }) { Text("Pengumuman") }
                    OutlinedButton(onClick = {
                        messageText = "⏰ *KONFIRMASI KEHADIRAN*\nYth. Orang Tua/Wali dari *{nama}* (*{kelas}*),\nAnanda belum tercatat hadir di sekolah hari ini.\nTerima kasih."
                    }) { Text("Belum Hadir") }
                    OutlinedButton(onClick = {
                        messageText = "👔 *KEDISIPLINAN SERAGAM*\nYth. Orang Tua/Wali dari *{nama}* (*{kelas}*),\nTerdapat atribut seragam ananda yang kurang lengkap hari ini.\nTerima kasih."
                    }) { Text("Seragam") }
                }

                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    label = { Text("Isi Pesan (gunakan {nama} & {kelas})") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Pratinjau:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(previewText, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Button(
                    onClick = { viewModel.sendBroadcastWa(eligibleRecipients, messageText) },
                    enabled = eligibleRecipients.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Kirim Broadcast via Fonnte (${eligibleRecipients.size})", fontWeight = FontWeight.Bold)
                }

                if (settings.fonnteToken.isBlank() && eligibleRecipients.isNotEmpty()) {
                    val first = eligibleRecipients.first()
                    OutlinedButton(
                        onClick = {
                            val msg = messageText
                                .replace("{nama}", first.nama, ignoreCase = true)
                                .replace("{kelas}", first.kelas, ignoreCase = true)
                            val uri = Uri.parse("https://wa.me/${first.waOrtu}?text=${Uri.encode(msg)}")
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Buka WhatsApp Langsung (${first.nama})")
                    }
                }
            }
        }
    }
}

@Composable
fun AdminSettingsTab(
    viewModel: AbsensiViewModel,
    settings: AppSettings
) {
    var schoolName by remember(settings.schoolName) { mutableStateOf(settings.schoolName) }
    var fonnteToken by remember(settings.fonnteToken) { mutableStateOf(settings.fonnteToken) }
    var labels by remember(settings.uniformLabels) { mutableStateOf(settings.uniformLabels) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Status Pengisian Absensi", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (settings.attendanceEnabled) "ABSENSI DIBUKA untuk petugas OSIS" else "ABSENSI DITUTUP sementara",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (settings.attendanceEnabled) SuccessGreen else DangerRed
                    )
                }
                Switch(
                    checked = settings.attendanceEnabled,
                    onCheckedChange = { viewModel.setAttendanceEnabled(it) }
                )
            }
        }

        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Identitas Sekolah & Gateway Fonnte", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = schoolName,
                    onValueChange = { schoolName = it },
                    label = { Text("Nama Sekolah") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fonnteToken,
                    onValueChange = { fonnteToken = it },
                    label = { Text("Token API Fonnte (Opsional)") },
                    placeholder = { Text("Masukkan token Fonnte untuk WA otomatis") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { viewModel.saveSchoolAndGatewaySettings(schoolName, fonnteToken) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Simpan Pengaturan Sekolah & WA")
                }
            }
        }

        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Nama Atribut Seragam", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                OutlinedTextField(value = labels.badgeSekolah, onValueChange = { labels = labels.copy(badgeSekolah = it) }, label = { Text("Atribut 1") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = labels.badgeOsis, onValueChange = { labels = labels.copy(badgeOsis = it) }, label = { Text("Atribut 2") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = labels.dasi, onValueChange = { labels = labels.copy(dasi = it) }, label = { Text("Atribut 3") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = labels.ikatPinggang, onValueChange = { labels = labels.copy(ikatPinggang = it) }, label = { Text("Atribut 4") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = labels.kaosKaki, onValueChange = { labels = labels.copy(kaosKaki = it) }, label = { Text("Atribut 5") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = labels.sepatu, onValueChange = { labels = labels.copy(sepatu = it) }, label = { Text("Atribut 6") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = labels.atributLainnya, onValueChange = { labels = labels.copy(atributLainnya = it) }, label = { Text("Atribut 7") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = labels.catatan, onValueChange = { labels = labels.copy(catatan = it) }, label = { Text("Label Catatan") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = { viewModel.saveUniformLabels(labels) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Simpan Nama Atribut")
                }
            }
        }
    }
}

@Composable
fun AdminUsersTab(
    viewModel: AbsensiViewModel,
    actorRole: String,
    adminUsers: List<AdminUserEntity>
) {
    var showDialog by remember { mutableStateOf(false) }
    var editingAdmin by remember { mutableStateOf<AdminUserEntity?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Kelola Role Admin", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Button(onClick = {
                    editingAdmin = null
                    showDialog = true
                }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tambah Akun")
                }
            }
        }

        if (actorRole == "Developer") {
            adminUsers.forEach { u ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(u.username, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("${u.id} • Role: ${u.role}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusPill(
                                text = u.status,
                                containerColor = if (u.status == "Aktif") RoyalBlue else Color(0xFF64748B),
                                contentColor = Color.White
                            )
                            IconButton(onClick = {
                                editingAdmin = u
                                showDialog = true
                            }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit akun ${u.username}")
                            }
                            if (u.username != "admin") {
                                IconButton(onClick = { viewModel.deleteAdminUser(u.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Hapus akun ${u.username}", tint = DangerRed)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Sebagai Pengawas, Anda dapat menambahkan akun Pengawas atau Anggota OSIS baru menggunakan tombol Tambah Akun di atas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }

    if (showDialog) {
        var username by remember { mutableStateOf(editingAdmin?.username ?: "") }
        var password by remember { mutableStateOf("") }
        var role by remember { mutableStateOf(editingAdmin?.role ?: "Anggota OSIS") }
        var status by remember { mutableStateOf(editingAdmin?.status ?: "Aktif") }

        val allowedRoles = if (actorRole == "Developer") {
            listOf("Anggota OSIS", "Pengawas", "Developer")
        } else {
            listOf("Anggota OSIS", "Pengawas")
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (editingAdmin == null) "Tambah Akun Admin" else "Edit Akun Admin") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(if (editingAdmin == null) "Password" else "Password Baru (opsional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        allowedRoles.forEach { r ->
                            FilterChip(selected = role == r, onClick = { role = r }, label = { Text(r) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = status == "Aktif", onClick = { status = "Aktif" }, label = { Text("Aktif") })
                        FilterChip(selected = status == "Tidak Aktif", onClick = { status = "Tidak Aktif" }, label = { Text("Tidak Aktif") })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveAdminUser(editingAdmin?.id, username, password, role, status) {
                        showDialog = false
                    }
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Batal") } }
        )
    }
}
