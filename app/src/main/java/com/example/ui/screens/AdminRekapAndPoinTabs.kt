package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.AbsensiEntity
import com.example.data.local.KelasEntity
import com.example.data.preferences.AppSettings
import com.example.ui.components.StatusPill
import com.example.ui.components.UniformAndHpInspectionSection
import com.example.ui.theme.AccentGold
import com.example.ui.theme.DangerRed
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.viewmodel.AbsensiViewModel
import com.example.ui.viewmodel.StudentViolationSummary
import com.example.ui.viewmodel.UniformFormState
import com.example.ui.viewmodel.ViolationDashboardState
import com.example.util.ExportUtils
import com.example.util.SecurityUtils
import java.io.File

@Composable
fun AdminRekapTab(
    viewModel: AbsensiViewModel,
    settings: AppSettings,
    allAbsensi: List<AbsensiEntity>,
    kelasList: List<KelasEntity>
) {
    val context = LocalContext.current
    var searchNama by remember { mutableStateOf("") }
    var filterKelas by remember { mutableStateOf("") }
    var filterSeragam by remember { mutableStateOf("") }
    var filterHp by remember { mutableStateOf("") }
    var filterTanggal by remember { mutableStateOf("") }

    var editingRecord by remember { mutableStateOf<AbsensiEntity?>(null) }
    var editForm by remember { mutableStateOf(UniformFormState()) }
    var viewingPhotoRecord by remember { mutableStateOf<AbsensiEntity?>(null) }

    val filteredRecords = remember(allAbsensi, searchNama, filterKelas, filterSeragam, filterHp, filterTanggal) {
        val q = searchNama.trim().lowercase()
        allAbsensi.filter { r ->
            (q.isEmpty() || r.namaSiswa.lowercase().contains(q) || r.namaPetugas.lowercase().contains(q)) &&
                (filterKelas.isEmpty() || r.kelas == filterKelas) &&
                (filterSeragam.isEmpty() || r.statusSeragam == filterSeragam) &&
                (filterHp.isEmpty() || r.statusHp == filterHp) &&
                (filterTanggal.isEmpty() || r.tanggal == filterTanggal)
        }
    }

    val classOptions = remember(kelasList, allAbsensi) {
        (kelasList.map { it.namaKelas } + allAbsensi.map { it.kelas }).distinct().filter { it.isNotBlank() }
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
                    Text(
                        text = "Rekap & Kehadiran (${filteredRecords.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                val tag = filterTanggal.ifBlank { SecurityUtils.todayDateStr() }
                                ExportUtils.exportAttendancePdfAndShare(context, settings.schoolName, filteredRecords, tag)
                            }
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp), tint = DangerRed)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PDF")
                        }
                        OutlinedButton(
                            onClick = {
                                val tag = filterTanggal.ifBlank { SecurityUtils.todayDateStr() }
                                ExportUtils.exportAttendanceCsvAndShare(context, filteredRecords, tag)
                            }
                        ) {
                            Icon(Icons.Filled.TableView, contentDescription = null, modifier = Modifier.size(16.dp), tint = SuccessGreen)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("CSV")
                        }
                    }
                }

                OutlinedTextField(
                    value = searchNama,
                    onValueChange = { searchNama = it },
                    label = { Text("Cari nama siswa / petugas") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = filterTanggal,
                    onValueChange = { filterTanggal = it },
                    label = { Text("Filter Tanggal (yyyy-MM-dd)") },
                    placeholder = { Text(SecurityUtils.todayDateStr()) },
                    trailingIcon = {
                        if (filterTanggal.isNotEmpty()) {
                            TextButton(onClick = { filterTanggal = "" }) { Text("Reset") }
                        } else {
                            TextButton(onClick = { filterTanggal = SecurityUtils.todayDateStr() }) { Text("Hari Ini") }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.FilterList, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    FilterChip(
                        selected = filterSeragam == "Tidak Lengkap",
                        onClick = { filterSeragam = if (filterSeragam == "Tidak Lengkap") "" else "Tidak Lengkap" },
                        label = { Text("Seragam Kurang") }
                    )
                    FilterChip(
                        selected = filterSeragam == "Lengkap",
                        onClick = { filterSeragam = if (filterSeragam == "Lengkap") "" else "Lengkap" },
                        label = { Text("Seragam Lengkap") }
                    )
                    FilterChip(
                        selected = filterHp == "HP Tidak Dikumpulkan",
                        onClick = { filterHp = if (filterHp == "HP Tidak Dikumpulkan") "" else "HP Tidak Dikumpulkan" },
                        label = { Text("HP Belum Kumpul") }
                    )
                    classOptions.forEach { k ->
                        FilterChip(
                            selected = filterKelas == k,
                            onClick = { filterKelas = if (filterKelas == k) "" else k },
                            label = { Text(k) }
                        )
                    }
                }
            }
        }

        if (filteredRecords.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Tidak ada data rekap yang cocok dengan filter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            filteredRecords.take(150).forEach { item ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.namaSiswa, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${item.kelas} • ${item.tanggal} ${item.jamDatang} • Petugas: ${item.namaPetugas}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusPill(
                                text = item.metode,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            StatusPill(
                                text = item.statusSeragam,
                                containerColor = if (item.statusSeragam == "Lengkap") SuccessGreen else DangerRed,
                                contentColor = Color.White
                            )
                            StatusPill(
                                text = item.statusHp,
                                containerColor = if (item.statusHp == "HP Dikumpulkan") RoyalBlue else Color(0xFF64748B),
                                contentColor = Color.White
                            )
                        }

                        if (item.keterangan != "-" || item.keteranganHp != "-") {
                            Text(
                                text = "Ket: ${item.keterangan} | HP: ${item.keteranganHp}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    editingRecord = item
                                    editForm = UniformFormState(
                                        badgeSekolah = item.badgeSekolah,
                                        badgeOsis = item.badgeOsis,
                                        dasi = item.dasi,
                                        ikatPinggang = item.ikatPinggang,
                                        kaosKaki = item.kaosKaki,
                                        sepatu = item.sepatu,
                                        atributLainnya = item.atributLainnya,
                                        catatan = if (item.keterangan == "-") "" else item.keterangan,
                                        statusHp = item.statusHp,
                                        keteranganHp = if (item.keteranganHp == "-") "" else item.keteranganHp
                                    )
                                }
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ubah Pilihan")
                            }

                            if (item.fotoPath.isNotBlank()) {
                                OutlinedButton(onClick = { viewingPhotoRecord = item }) {
                                    Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Lihat Foto")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingRecord != null) {
        val rec = editingRecord!!
        AlertDialog(
            onDismissRequest = { editingRecord = null },
            title = { Text("Ubah Pilihan: ${rec.namaSiswa}", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("${rec.kelas} • ${rec.tanggal}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    UniformAndHpInspectionSection(
                        labels = settings.uniformLabels,
                        form = editForm,
                        onFormChange = { editForm = it }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateAbsensiPilihan(rec.id, editForm) {
                            editingRecord = null
                        }
                    }
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingRecord = null }) { Text("Batal") }
            }
        )
    }

    if (viewingPhotoRecord != null) {
        val rec = viewingPhotoRecord!!
        AlertDialog(
            onDismissRequest = { viewingPhotoRecord = null },
            title = { Text("Foto Bukti - ${rec.namaSiswa}") },
            text = {
                AsyncImage(
                    model = File(rec.fotoPath),
                    contentDescription = "Foto bukti absensi ${rec.namaSiswa}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            },
            confirmButton = {
                TextButton(onClick = { viewingPhotoRecord = null }) { Text("Tutup") }
            }
        )
    }
}

@Composable
fun AdminPoinTab(
    violationStats: ViolationDashboardState,
    kelasList: List<KelasEntity>
) {
    var search by remember { mutableStateOf("") }
    var filterKelas by remember { mutableStateOf("") }
    var selectedStudentHistory by remember { mutableStateOf<StudentViolationSummary?>(null) }

    val filtered = remember(violationStats, search, filterKelas) {
        val q = search.trim().lowercase()
        violationStats.list.filter { s ->
            (q.isEmpty() || s.namaSiswa.lowercase().contains(q) || s.idSiswa.lowercase().contains(q)) &&
                (filterKelas.isEmpty() || s.kelas == filterKelas)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Sistem Poin Pelanggaran",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Setiap atribut seragam yang tidak lengkap dihitung 1 poin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ViolationMiniStat("Total Poin", violationStats.totalPoinAll.toString(), DangerRed, Modifier.weight(1f))
                    ViolationMiniStat("Siswa", violationStats.totalSiswaTercatat.toString(), RoyalBlue, Modifier.weight(1f))
                    ViolationMiniStat("Pelanggaran", violationStats.totalPelanggaranAll.toString(), Color(0xFFB45309), Modifier.weight(1f))
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
                        selected = filterKelas.isEmpty(),
                        onClick = { filterKelas = "" },
                        label = { Text("Semua") }
                    )
                    kelasList.map { it.namaKelas }.distinct().forEach { k ->
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
                    text = "Belum ada catatan poin pelanggaran.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            filtered.forEach { summary ->
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
                            Text(summary.namaSiswa, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${summary.kelas} • ${summary.idSiswa} • ${summary.jumlahPelanggaran} atribut",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusPill(
                                text = "${summary.totalPoin} Poin",
                                containerColor = DangerRed,
                                contentColor = Color.White
                            )
                            OutlinedButton(onClick = { selectedStudentHistory = summary }) {
                                Text("Detail")
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedStudentHistory != null) {
        val st = selectedStudentHistory!!
        AlertDialog(
            onDismissRequest = { selectedStudentHistory = null },
            title = { Text("Riwayat: ${st.namaSiswa} (${st.kelas})") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    st.riwayat.forEach { h ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("${h.tanggal} ${h.jam}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    StatusPill(text = "+${h.poin} poin", containerColor = AccentGold, contentColor = Color(0xFF0F172A))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(h.detail, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedStudentHistory = null }) { Text("Tutup") }
            }
        )
    }
}

@Composable
private fun ViolationMiniStat(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}
