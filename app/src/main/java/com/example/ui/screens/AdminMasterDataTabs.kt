package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.KelasEntity
import com.example.data.local.PetugasEntity
import com.example.data.local.SiswaEntity
import com.example.data.preferences.AppSettings
import com.example.ui.components.StatusPill
import com.example.ui.theme.AccentGold
import com.example.ui.theme.DangerRed
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SuccessContainerLight
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessTextLight
import com.example.ui.viewmodel.AbsensiViewModel
import com.example.ui.viewmodel.PetugasPerformanceStat
import com.example.util.ExportUtils
import com.example.util.QrUtils

@Composable
fun AdminSiswaTab(
    viewModel: AbsensiViewModel,
    settings: AppSettings,
    canEditMaster: Boolean,
    siswaList: List<SiswaEntity>,
    kelasList: List<KelasEntity>,
    attendedIdsToday: Set<String>
) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    var filterKelas by remember { mutableStateOf("") }
    var filterHadir by remember { mutableStateOf("") }

    var showStudentDialog by remember { mutableStateOf(false) }
    var editingSiswa by remember { mutableStateOf<SiswaEntity?>(null) }
    var qrPreviewSiswa by remember { mutableStateOf<SiswaEntity?>(null) }

    val classNames = remember(kelasList, siswaList) {
        (kelasList.map { it.namaKelas } + siswaList.map { it.kelas }).distinct().filter { it.isNotBlank() }
    }

    val filtered = remember(siswaList, search, filterKelas, filterHadir, attendedIdsToday) {
        val q = search.trim().lowercase()
        siswaList.filter { s ->
            val isHadir = attendedIdsToday.contains(s.id)
            (q.isEmpty() || s.nama.lowercase().contains(q) || s.id.lowercase().contains(q)) &&
                (filterKelas.isEmpty() || s.kelas == filterKelas) &&
                (filterHadir.isEmpty() || (filterHadir == "SUDAH" && isHadir) || (filterHadir == "BELUM" && !isHadir))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Data Siswa & QR (${filtered.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                val toPrint = siswaList.filter {
                                    it.status.equals("Aktif", ignoreCase = true) &&
                                        (filterKelas.isEmpty() || it.kelas == filterKelas)
                                }
                                if (toPrint.isEmpty()) {
                                    viewModel.notifyInvalidQr("Tidak ada siswa aktif untuk dicetak QR!")
                                } else {
                                    ExportUtils.exportQrCardsPdfAndShare(
                                        context,
                                        settings.schoolName,
                                        toPrint,
                                        filterKelas.ifBlank { "Semua_Kelas" }
                                    )
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cetak QR")
                        }
                        if (canEditMaster) {
                            Button(onClick = {
                                editingSiswa = null
                                showStudentDialog = true
                            }) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Tambah")
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Cari nama atau ID siswa") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = filterHadir == "SUDAH",
                        onClick = { filterHadir = if (filterHadir == "SUDAH") "" else "SUDAH" },
                        label = { Text("Hadir Hari Ini") }
                    )
                    FilterChip(
                        selected = filterHadir == "BELUM",
                        onClick = { filterHadir = if (filterHadir == "BELUM") "" else "BELUM" },
                        label = { Text("Belum Hadir") }
                    )
                    classNames.forEach { k ->
                        FilterChip(
                            selected = filterKelas == k,
                            onClick = { filterKelas = if (filterKelas == k) "" else k },
                            label = { Text(k) }
                        )
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Belum ada data siswa. Tekan Tambah untuk mendaftarkan siswa baru dan menghasilkan QR Code otomatis.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            filtered.forEach { s ->
                val isHadir = attendedIdsToday.contains(s.id)
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
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(s.nama, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                if (s.status != "Aktif") {
                                    StatusPill(text = s.status, containerColor = Color(0xFF64748B), contentColor = Color.White)
                                }
                            }
                            Text(
                                text = "${s.id} • ${s.kelas}" + if (s.waOrtu.isNotBlank()) " • WA: ${s.waOrtu}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            StatusPill(
                                text = if (isHadir) "Hadir Hari Ini" else "Belum Absen",
                                containerColor = if (isHadir) SuccessContainerLight else AccentGold.copy(alpha = 0.25f),
                                contentColor = if (isHadir) SuccessTextLight else Color(0xFF78350F)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { qrPreviewSiswa = s }) {
                                Icon(Icons.Filled.QrCode2, contentDescription = "Lihat QR ${s.nama}", tint = RoyalBlue)
                            }
                            if (canEditMaster) {
                                IconButton(onClick = {
                                    editingSiswa = s
                                    showStudentDialog = true
                                }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit ${s.nama}")
                                }
                                IconButton(onClick = { viewModel.deleteSiswa(s.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Hapus ${s.nama}", tint = DangerRed)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (qrPreviewSiswa != null) {
        val st = qrPreviewSiswa!!
        val qrBmp = remember(st.qrToken) { QrUtils.generateQrBitmap(st.qrToken, 512) }
        AlertDialog(
            onDismissRequest = { qrPreviewSiswa = null },
            title = { Text("Kartu QR - ${st.nama}") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("OSIS ${settings.schoolName}", style = MaterialTheme.typography.labelMedium, color = RoyalBlue, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    if (qrBmp != null) {
                        Image(
                            bitmap = qrBmp.asImageBitmap(),
                            contentDescription = "QR Code ${st.nama}",
                            modifier = Modifier.size(200.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(st.nama, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${st.kelas} • ${st.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = {
                    ExportUtils.exportQrCardsPdfAndShare(context, settings.schoolName, listOf(st), st.id)
                }) {
                    Text("Bagikan PDF")
                }
            },
            dismissButton = {
                TextButton(onClick = { qrPreviewSiswa = null }) { Text("Tutup") }
            }
        )
    }

    if (showStudentDialog) {
        SiswaFormDialog(
            initial = editingSiswa,
            classOptions = classNames,
            onDismiss = { showStudentDialog = false },
            onSave = { nama, kelas, status, waOrtu ->
                viewModel.saveSiswa(editingSiswa?.id, nama, kelas, status, waOrtu) {
                    showStudentDialog = false
                }
            }
        )
    }
}

@Composable
private fun SiswaFormDialog(
    initial: SiswaEntity?,
    classOptions: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var nama by remember { mutableStateOf(initial?.nama ?: "") }
    var kelas by remember { mutableStateOf(initial?.kelas ?: classOptions.firstOrNull().orEmpty()) }
    var waOrtu by remember { mutableStateOf(initial?.waOrtu ?: "") }
    var status by remember { mutableStateOf(initial?.status ?: "Aktif") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Tambah Siswa" else "Edit Siswa") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = nama,
                    onValueChange = { nama = it },
                    label = { Text("Nama Siswa") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Pilih Kelas:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    classOptions.forEach { k ->
                        FilterChip(selected = kelas == k, onClick = { kelas = k }, label = { Text(k) })
                    }
                }
                OutlinedTextField(
                    value = waOrtu,
                    onValueChange = { waOrtu = it },
                    label = { Text("WhatsApp Orang Tua/Wali") },
                    placeholder = { Text("Contoh: 081234567890") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = status == "Aktif", onClick = { status = "Aktif" }, label = { Text("Aktif") })
                    FilterChip(selected = status == "Tidak Aktif", onClick = { status = "Tidak Aktif" }, label = { Text("Tidak Aktif") })
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(nama, kelas, status, waOrtu) }) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@Composable
fun AdminKelasTab(
    viewModel: AbsensiViewModel,
    canEditMaster: Boolean,
    kelasList: List<KelasEntity>
) {
    var showDialog by remember { mutableStateOf(false) }
    var editingKelas by remember { mutableStateOf<KelasEntity?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Manajemen Kelas (${kelasList.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (canEditMaster) {
                    Button(onClick = {
                        editingKelas = null
                        showDialog = true
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tambah")
                    }
                }
            }
        }

        kelasList.forEach { k ->
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
                        Text(k.namaKelas, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = RoyalBlue)
                        Text("${k.id} • Tingkat ${k.tingkat} • Jurusan ${k.jurusan}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(
                            text = k.status,
                            containerColor = if (k.status == "Aktif") SuccessGreen else Color(0xFF64748B),
                            contentColor = Color.White
                        )
                        if (canEditMaster) {
                            IconButton(onClick = {
                                editingKelas = k
                                showDialog = true
                            }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit kelas ${k.namaKelas}")
                            }
                            IconButton(onClick = { viewModel.deleteKelas(k.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Hapus kelas ${k.namaKelas}", tint = DangerRed)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        var tingkat by remember { mutableStateOf(editingKelas?.tingkat ?: "X") }
        var jurusan by remember { mutableStateOf(editingKelas?.jurusan ?: "") }
        var namaKelas by remember { mutableStateOf(editingKelas?.namaKelas ?: "") }
        var status by remember { mutableStateOf(editingKelas?.status ?: "Aktif") }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (editingKelas == null) "Tambah Kelas" else "Edit Kelas") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("X", "XI", "XII").forEach { t ->
                            FilterChip(
                                selected = tingkat == t,
                                onClick = {
                                    tingkat = t
                                    if (jurusan.isNotBlank()) namaKelas = "$t ${jurusan.uppercase()}"
                                },
                                label = { Text(t) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = jurusan,
                        onValueChange = {
                            jurusan = it.uppercase()
                            namaKelas = if (jurusan.isNotBlank()) "$tingkat $jurusan" else tingkat
                        },
                        label = { Text("Jurusan (Contoh: KK, BDP, TKJ)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = namaKelas,
                        onValueChange = { namaKelas = it },
                        label = { Text("Nama Kelas") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = status == "Aktif", onClick = { status = "Aktif" }, label = { Text("Aktif") })
                        FilterChip(selected = status == "Tidak Aktif", onClick = { status = "Tidak Aktif" }, label = { Text("Tidak Aktif") })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveKelas(editingKelas?.id, tingkat, jurusan, namaKelas, status) {
                        showDialog = false
                    }
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Batal") } }
        )
    }
}

@Composable
fun AdminPetugasTab(
    viewModel: AbsensiViewModel,
    canEditMaster: Boolean,
    petugasStats: List<PetugasPerformanceStat>
) {
    var showDialog by remember { mutableStateOf(false) }
    var editingPetugas by remember { mutableStateOf<PetugasEntity?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Petugas OSIS (${petugasStats.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (canEditMaster) {
                    Button(onClick = {
                        editingPetugas = null
                        showDialog = true
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tambah")
                    }
                }
            }
        }

        petugasStats.forEach { item ->
            val p = item.petugas
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
                        Text(p.nama, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("${p.id} • Kode: ${p.kode}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusPill(text = "Hari Ini: ${item.todayCount}", containerColor = RoyalBlue, contentColor = Color.White)
                            StatusPill(text = "Total: ${item.totalCount}", containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(
                            text = p.status,
                            containerColor = if (p.status == "Aktif") SuccessGreen else Color(0xFF64748B),
                            contentColor = Color.White
                        )
                        if (canEditMaster) {
                            IconButton(onClick = {
                                editingPetugas = p
                                showDialog = true
                            }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit petugas ${p.nama}")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        var nama by remember { mutableStateOf(editingPetugas?.nama ?: "") }
        var kode by remember { mutableStateOf(editingPetugas?.kode ?: "") }
        var status by remember { mutableStateOf(editingPetugas?.status ?: "Aktif") }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (editingPetugas == null) "Tambah Petugas OSIS" else "Edit Petugas OSIS") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nama,
                        onValueChange = { nama = it },
                        label = { Text("Nama Petugas") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = kode,
                        onValueChange = { kode = it },
                        label = { Text("Kode Verifikasi") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = status == "Aktif", onClick = { status = "Aktif" }, label = { Text("Aktif") })
                        FilterChip(selected = status == "Tidak Aktif", onClick = { status = "Tidak Aktif" }, label = { Text("Tidak Aktif") })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.savePetugas(editingPetugas?.id, nama, kode, status) {
                        showDialog = false
                    }
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Batal") } }
        )
    }
}
