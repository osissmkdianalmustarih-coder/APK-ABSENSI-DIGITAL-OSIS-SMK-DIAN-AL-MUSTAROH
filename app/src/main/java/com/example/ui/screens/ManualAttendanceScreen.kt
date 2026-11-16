package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.local.AbsensiEntity
import com.example.data.local.KelasEntity
import com.example.data.local.PetugasEntity
import com.example.data.local.SiswaEntity
import com.example.data.preferences.AppSettings
import com.example.ui.components.PetugasActiveBanner
import com.example.ui.components.PetugasGateCard
import com.example.ui.components.StatusPill
import com.example.ui.components.UniformAndHpInspectionSection
import com.example.ui.theme.AccentGold
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SuccessContainerLight
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessTextLight
import com.example.ui.viewmodel.AbsensiViewModel
import com.example.ui.viewmodel.UniformFormState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualAttendanceScreen(
    viewModel: AbsensiViewModel,
    settings: AppSettings,
    activePetugas: PetugasEntity?,
    kelasList: List<KelasEntity>,
    siswaList: List<SiswaEntity>,
    todayRecords: List<AbsensiEntity>,
    attendedIdsToday: Set<String>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedKelas by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedSiswa by remember { mutableStateOf<SiswaEntity?>(null) }
    var formState by remember { mutableStateOf(UniformFormState()) }
    var savedFotoPath by remember { mutableStateOf("") }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }

    suspend fun compressAndSaveUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
            val maxW = 800
            val scaled = if (bmp.width > maxW) {
                val ratio = maxW.toFloat() / bmp.width
                Bitmap.createScaledBitmap(bmp, maxW, (bmp.height * ratio).toInt().coerceAtLeast(1), true)
            } else {
                bmp
            }
            val dir = File(context.filesDir, "attendance_photos").apply { mkdirs() }
            val outFile = File(dir, "bukti_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 75, out)
            }
            outFile.absolutePath
        }.getOrNull()
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCameraFile
        if (success && file != null && file.exists()) {
            scope.launch {
                val uri = Uri.fromFile(file)
                val compressed = compressAndSaveUri(uri)
                if (compressed != null) {
                    savedFotoPath = compressed
                }
            }
        }
    }

    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val compressed = compressAndSaveUri(uri)
                if (compressed != null) {
                    savedFotoPath = compressed
                }
            }
        }
    }

    val activeClasses = remember(kelasList, siswaList) {
        val fromMaster = kelasList.filter { it.status.equals("Aktif", ignoreCase = true) }.map { it.namaKelas }
        val fromStudents = siswaList.filter { it.status.equals("Aktif", ignoreCase = true) }.map { it.kelas }
        (fromMaster + fromStudents).distinct()
    }

    val activeStudentsInClass = remember(siswaList, selectedKelas, searchQuery) {
        val k = selectedKelas ?: return@remember emptyList()
        val q = searchQuery.trim().lowercase()
        siswaList.filter { s ->
            s.status.equals("Aktif", ignoreCase = true) &&
                s.kelas.equals(k, ignoreCase = true) &&
                (q.isEmpty() || s.nama.lowercase().contains(q) || s.id.lowercase().contains(q))
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (activePetugas == null) {
            item {
                PetugasGateCard(
                    schoolName = settings.schoolName,
                    onLogin = { viewModel.loginPetugas(it) }
                )
            }
        } else {
            item {
                PetugasActiveBanner(
                    petugas = activePetugas,
                    todayCount = todayRecords.count { it.idPetugas == activePetugas.id },
                    onLogout = { viewModel.logoutPetugas() }
                )
            }

            // STEP 1: PILIH KELAS
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusPill(text = "1", containerColor = AccentGold, contentColor = Color(0xFF0F172A))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Pilih Kelas",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = selectedKelas ?: "Belum dipilih",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            activeClasses.forEach { kelasName ->
                                val count = siswaList.count {
                                    it.status.equals("Aktif", ignoreCase = true) &&
                                        it.kelas.equals(kelasName, ignoreCase = true)
                                }
                                val isSelected = selectedKelas == kelasName
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) RoyalBlue else MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(
                                        1.5.dp,
                                        if (isSelected) RoyalBlue else MaterialTheme.colorScheme.outline
                                    ),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            selectedKelas = kelasName
                                            selectedSiswa = null
                                            searchQuery = ""
                                        }
                                        .testTag("class_chip_$kelasName")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = kelasName,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "$count Siswa",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isSelected) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // STEP 2: PILIH SISWA
            if (selectedKelas != null) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusPill(text = "2", containerColor = AccentGold, contentColor = Color(0xFF0F172A))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Pilih Siswa ($selectedKelas)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = { Text("Cari nama atau ID siswa") },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("manual_student_search")
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            if (activeStudentsInClass.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.PersonSearch,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Belum ada data siswa di kelas $selectedKelas.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Tambahkan siswa terlebih dahulu melalui Panel Admin → Siswa & QR.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    activeStudentsInClass.take(30).forEach { student ->
                                        val isAttended = attendedIdsToday.contains(student.id)
                                        val isPicked = selectedSiswa?.id == student.id
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = when {
                                                isPicked -> MaterialTheme.colorScheme.primaryContainer
                                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                            },
                                            border = BorderStroke(
                                                1.5.dp,
                                                if (isPicked) RoyalBlue else MaterialTheme.colorScheme.outline
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable { selectedSiswa = student }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = student.nama,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = student.id,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    StatusPill(
                                                        text = if (isAttended) "SUDAH" else "Belum",
                                                        containerColor = if (isAttended) SuccessContainerLight else AccentGold.copy(alpha = 0.25f),
                                                        contentColor = if (isAttended) SuccessTextLight else Color(0xFF78350F)
                                                    )
                                                    if (isPicked) {
                                                        Icon(
                                                            imageVector = Icons.Filled.CheckCircle,
                                                            contentDescription = "Terpilih",
                                                            tint = RoyalBlue
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // STEP 3, 4, 5: SERAGAM, HP, FOTO BUKTI & SUBMIT
            if (selectedSiswa != null) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            UniformAndHpInspectionSection(
                                labels = settings.uniformLabels,
                                form = formState,
                                onFormChange = { formState = it }
                            )

                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Foto Bukti (Opsional)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            if (savedFotoPath.isNotBlank()) {
                                AsyncImage(
                                    model = File(savedFotoPath),
                                    contentDescription = "Preview foto bukti absensi",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { savedFotoPath = "" },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Hapus Foto")
                                }
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val tmpFile = File(context.cacheDir, "cam_${System.currentTimeMillis()}.jpg")
                                            val uri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                tmpFile
                                            )
                                            pendingCameraFile = tmpFile
                                            pendingCameraUri = uri
                                            takePictureLauncher.launch(uri)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                    ) {
                                        Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Kamera")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            pickPhotoLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                    ) {
                                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Galeri")
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    val target = selectedSiswa!!
                                    viewModel.submitAttendance(
                                        siswa = target,
                                        form = formState,
                                        fotoPath = savedFotoPath,
                                        metode = "Manual",
                                        onSuccess = {
                                            selectedSiswa = null
                                            formState = UniformFormState()
                                            savedFotoPath = ""
                                        }
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("submit_manual_attendance_button")
                            ) {
                                Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("SIMPAN ABSENSI MANUAL", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
