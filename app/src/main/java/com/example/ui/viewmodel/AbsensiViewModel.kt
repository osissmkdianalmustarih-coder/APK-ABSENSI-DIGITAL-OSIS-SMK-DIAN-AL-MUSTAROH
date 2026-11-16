package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AbsensiEntity
import com.example.data.local.AdminUserEntity
import com.example.data.local.AppDatabase
import com.example.data.local.KelasEntity
import com.example.data.local.PetugasEntity
import com.example.data.local.SiswaEntity
import com.example.data.local.UniformLabels
import com.example.data.preferences.AppSettings
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.AbsensiRepository
import com.example.util.SecurityUtils
import com.example.util.SoundFeedback
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ClassProgressStat(
    val className: String,
    val totalActive: Int,
    val attendedToday: Int
) {
    val percentage: Int
        get() = if (totalActive > 0) ((attendedToday * 100f) / totalActive).toInt().coerceIn(0, 100) else 0
}

data class DashboardStats(
    val totalActiveStudents: Int = 0,
    val attendedTodayCount: Int = 0,
    val notAttendedTodayCount: Int = 0,
    val incompleteUniformTodayCount: Int = 0,
    val hpCollectedTodayCount: Int = 0,
    val perClassStats: List<ClassProgressStat> = emptyList()
) {
    val percentage: Int
        get() = if (totalActiveStudents > 0) ((attendedTodayCount * 100f) / totalActiveStudents).toInt().coerceIn(0, 100) else 0
}

data class ViolationHistoryItem(
    val tanggal: String,
    val jam: String,
    val detail: String,
    val poin: Int
)

data class StudentViolationSummary(
    val idSiswa: String,
    val namaSiswa: String,
    val kelas: String,
    val totalPoin: Int,
    val jumlahPelanggaran: Int,
    val riwayat: List<ViolationHistoryItem>
)

data class ViolationDashboardState(
    val totalPoinAll: Int = 0,
    val totalSiswaTercatat: Int = 0,
    val totalPelanggaranAll: Int = 0,
    val list: List<StudentViolationSummary> = emptyList()
)

data class PetugasPerformanceStat(
    val petugas: PetugasEntity,
    val todayCount: Int,
    val totalCount: Int
)

data class UiBannerMessage(
    val text: String,
    val isError: Boolean = false
)

data class UniformFormState(
    val badgeSekolah: String = "Lengkap",
    val badgeOsis: String = "Lengkap",
    val dasi: String = "Lengkap",
    val ikatPinggang: String = "Lengkap",
    val kaosKaki: String = "Lengkap",
    val sepatu: String = "Lengkap",
    val atributLainnya: String = "Lengkap",
    val catatan: String = "",
    val statusHp: String = "HP Dikumpulkan",
    val keteranganHp: String = ""
)

class AbsensiViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: AbsensiRepository = AbsensiRepository(
        dao = AppDatabase.getInstance(application).absensiDao(),
        settingsDataStore = SettingsDataStore(application)
    )

    val settings: StateFlow<AppSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val allSiswa: StateFlow<List<SiswaEntity>> = repository.allSiswa
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allKelas: StateFlow<List<KelasEntity>> = repository.allKelas
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPetugas: StateFlow<List<PetugasEntity>> = repository.allPetugas
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAdminUsers: StateFlow<List<AdminUserEntity>> = repository.allAdminUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAbsensi: StateFlow<List<AbsensiEntity>> = repository.allAbsensi
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activePetugas = MutableStateFlow<PetugasEntity?>(null)
    val activePetugas: StateFlow<PetugasEntity?> = _activePetugas.asStateFlow()

    private val _adminSession = MutableStateFlow<AdminUserEntity?>(null)
    val adminSession: StateFlow<AdminUserEntity?> = _adminSession.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _events = MutableSharedFlow<UiBannerMessage>()
    val events: SharedFlow<UiBannerMessage> = _events.asSharedFlow()

    val todayAbsensi: StateFlow<List<AbsensiEntity>> = allAbsensi.combine(settings) { records, _ ->
        val today = SecurityUtils.todayDateStr()
        records.filter { it.tanggal == today }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val attendedIdsToday: StateFlow<Set<String>> = todayAbsensi.combine(allSiswa) { todayList, _ ->
        todayList.map { it.idSiswa }.toSet()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val dashboardStats: StateFlow<DashboardStats> = combine(
        allSiswa,
        allKelas,
        todayAbsensi
    ) { siswaList, kelasList, todayRecords ->
        val activeStudents = siswaList.filter { it.status.equals("Aktif", ignoreCase = true) }
        val activeClassNames = kelasList
            .filter { it.status.equals("Aktif", ignoreCase = true) }
            .map { it.namaKelas }
            .ifEmpty { activeStudents.map { it.kelas }.distinct() }
            .distinct()

        val attendedKeys = todayRecords.map { "${it.namaSiswa.lowercase()}|${it.kelas.lowercase()}" }.toSet()
        val attendedByClass = mutableMapOf<String, Int>()
        val totalByClass = mutableMapOf<String, Int>()

        activeClassNames.forEach { k ->
            totalByClass[k] = 0
            attendedByClass[k] = 0
        }

        activeStudents.forEach { s ->
            totalByClass[s.kelas] = (totalByClass[s.kelas] ?: 0) + 1
            val key = "${s.nama.lowercase()}|${s.kelas.lowercase()}"
            if (attendedKeys.contains(key) || todayRecords.any { it.idSiswa == s.id }) {
                attendedByClass[s.kelas] = (attendedByClass[s.kelas] ?: 0) + 1
            }
        }

        val perClassList = totalByClass.keys.map { k ->
            ClassProgressStat(
                className = k,
                totalActive = totalByClass[k] ?: 0,
                attendedToday = attendedByClass[k] ?: 0
            )
        }

        val attendedUniqueToday = attendedKeys.size
        val incompleteToday = todayRecords.count { it.statusSeragam != "Lengkap" }
        val hpCollectedToday = todayRecords.count { it.statusHp == "HP Dikumpulkan" }

        DashboardStats(
            totalActiveStudents = activeStudents.size,
            attendedTodayCount = attendedUniqueToday,
            notAttendedTodayCount = (activeStudents.size - attendedUniqueToday).coerceAtLeast(0),
            incompleteUniformTodayCount = incompleteToday,
            hpCollectedTodayCount = hpCollectedToday,
            perClassStats = perClassList
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardStats())

    val violationStats: StateFlow<ViolationDashboardState> = combine(
        allAbsensi,
        settings
    ) { records, currentSettings ->
        val labels = currentSettings.uniformLabels
        val grouped = mutableMapOf<String, MutableList<Pair<AbsensiEntity, List<String>>>>()
        var totalPoin = 0
        var totalPelanggaran = 0

        records.forEach { row ->
            val missing = mutableListOf<String>()
            if (row.badgeSekolah == "Tidak Lengkap") missing.add(labels.badgeSekolah)
            if (row.badgeOsis == "Tidak Lengkap") missing.add(labels.badgeOsis)
            if (row.dasi == "Tidak Lengkap") missing.add(labels.dasi)
            if (row.ikatPinggang == "Tidak Lengkap") missing.add(labels.ikatPinggang)
            if (row.kaosKaki == "Tidak Lengkap") missing.add(labels.kaosKaki)
            if (row.sepatu == "Tidak Lengkap") missing.add(labels.sepatu)
            if (row.atributLainnya == "Tidak Lengkap") missing.add(labels.atributLainnya)

            if (missing.isNotEmpty()) {
                totalPoin += missing.size
                totalPelanggaran += missing.size
                val key = if (row.idSiswa != "-") row.idSiswa else "${row.namaSiswa}|${row.kelas}"
                grouped.getOrPut(key) { mutableListOf() }.add(row to missing)
            }
        }

        val summaryList = grouped.map { (key, entries) ->
            val sample = entries.first().first
            val history = entries.map { (r, missing) ->
                ViolationHistoryItem(
                    tanggal = r.tanggal,
                    jam = r.jamDatang,
                    detail = missing.joinToString(", ") { "$it Tidak Lengkap" },
                    poin = missing.size
                )
            }
            StudentViolationSummary(
                idSiswa = if (sample.idSiswa != "-") sample.idSiswa else key,
                namaSiswa = sample.namaSiswa,
                kelas = sample.kelas,
                totalPoin = history.sumOf { it.poin },
                jumlahPelanggaran = history.sumOf { it.poin },
                riwayat = history
            )
        }.sortedByDescending { it.totalPoin }

        ViolationDashboardState(
            totalPoinAll = totalPoin,
            totalSiswaTercatat = summaryList.size,
            totalPelanggaranAll = totalPelanggaran,
            list = summaryList
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ViolationDashboardState())

    val petugasPerformance: StateFlow<List<PetugasPerformanceStat>> = combine(
        allPetugas,
        allAbsensi
    ) { petugasList, absensiList ->
        val today = SecurityUtils.todayDateStr()
        petugasList.map { p ->
            PetugasPerformanceStat(
                petugas = p,
                todayCount = absensiList.count { it.idPetugas == p.id && it.tanggal == today },
                totalCount = absensiList.count { it.idPetugas == p.id }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.ensureBootstrapData()
        }
    }

    private fun emitMessage(text: String, isError: Boolean = false, playSound: Boolean = true) {
        if (playSound) {
            SoundFeedback.playTone(success = !isError, enabled = settings.value.soundEnabled)
        }
        viewModelScope.launch {
            _events.emit(UiBannerMessage(text, isError))
        }
    }

    fun toggleDarkTheme() {
        viewModelScope.launch {
            repository.setDarkTheme(!settings.value.isDarkTheme)
        }
    }

    fun toggleSoundEnabled() {
        viewModelScope.launch {
            val next = !settings.value.soundEnabled
            repository.setSoundEnabled(next)
            _events.emit(UiBannerMessage(if (next) "Suara umpan balik diaktifkan" else "Suara umpan balik dinonaktifkan"))
        }
    }

    fun loginPetugas(kode: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.verifyPetugas(kode)
            _isBusy.value = false
            res.onSuccess { p ->
                _activePetugas.value = p
                emitMessage("Halo, ${p.nama}! Siap bertugas.", isError = false)
            }.onFailure { err ->
                emitMessage(err.message ?: "Kode tidak valid!", isError = true)
            }
        }
    }

    fun logoutPetugas() {
        _activePetugas.value = null
        emitMessage("Petugas keluar dari sesi.", playSound = false)
    }

    fun submitAttendance(
        siswa: SiswaEntity,
        form: UniformFormState,
        fotoPath: String,
        metode: String,
        onSuccess: () -> Unit
    ) {
        val petugas = _activePetugas.value
        if (petugas == null) {
            emitMessage("Silakan verifikasi petugas OSIS terlebih dahulu!", isError = true)
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.submitAbsensi(
                siswa = siswa,
                petugas = petugas,
                badgeSekolah = form.badgeSekolah,
                badgeOsis = form.badgeOsis,
                dasi = form.dasi,
                ikatPinggang = form.ikatPinggang,
                kaosKaki = form.kaosKaki,
                sepatu = form.sepatu,
                atributLainnya = form.atributLainnya,
                keterangan = form.catatan,
                statusHp = form.statusHp,
                keteranganHp = form.keteranganHp,
                fotoPath = fotoPath,
                metode = metode
            )
            _isBusy.value = false
            emitMessage(res.message, isError = !res.success)
            if (res.success) onSuccess()
        }
    }

    fun findSiswaByQrToken(token: String): SiswaEntity? {
        val clean = token.trim()
        return allSiswa.value.firstOrNull {
            it.status.equals("Aktif", ignoreCase = true) &&
                (it.qrToken.equals(clean, ignoreCase = true) || it.id.equals(clean, ignoreCase = true))
        }
    }

    fun notifyInvalidQr(message: String) {
        emitMessage(message, isError = true)
    }

    fun loginAdmin(username: String, password: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.loginAdmin(username, password)
            _isBusy.value = false
            res.onSuccess { admin ->
                _adminSession.value = admin
                emitMessage("Login berhasil sebagai ${admin.role} (${admin.username})")
            }.onFailure { err ->
                emitMessage(err.message ?: "Login gagal", isError = true)
            }
        }
    }

    fun logoutAdmin() {
        _adminSession.value = null
        emitMessage("Logout admin berhasil.", playSound = false)
    }

    fun updateAbsensiPilihan(
        idAbsensi: String,
        form: UniformFormState,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.updateAbsensiPilihan(
                idAbsensi = idAbsensi,
                badgeSekolah = form.badgeSekolah,
                badgeOsis = form.badgeOsis,
                dasi = form.dasi,
                ikatPinggang = form.ikatPinggang,
                kaosKaki = form.kaosKaki,
                sepatu = form.sepatu,
                atributLainnya = form.atributLainnya,
                keterangan = form.catatan,
                statusHp = form.statusHp,
                keteranganHp = form.keteranganHp
            )
            _isBusy.value = false
            emitMessage(res.message, isError = !res.success)
            if (res.success) onDone()
        }
    }

    fun saveSiswa(id: String?, nama: String, kelas: String, status: String, waOrtu: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.saveSiswa(id, nama, kelas, status, waOrtu)
            _isBusy.value = false
            emitMessage(res.message, isError = !res.success)
            if (res.success) onDone()
        }
    }

    fun deleteSiswa(idSiswa: String) {
        viewModelScope.launch {
            val res = repository.deleteSiswa(idSiswa)
            emitMessage(res.message, isError = !res.success)
        }
    }

    fun saveKelas(id: String?, tingkat: String, jurusan: String, namaKelas: String, status: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.saveKelas(id, tingkat, jurusan, namaKelas, status)
            _isBusy.value = false
            emitMessage(res.message, isError = !res.success)
            if (res.success) onDone()
        }
    }

    fun deleteKelas(idKelas: String) {
        viewModelScope.launch {
            val res = repository.deleteKelas(idKelas)
            emitMessage(res.message, isError = !res.success)
        }
    }

    fun savePetugas(id: String?, nama: String, kode: String, status: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.savePetugas(id, nama, kode, status)
            _isBusy.value = false
            emitMessage(res.message, isError = !res.success)
            if (res.success) onDone()
        }
    }

    fun saveAdminUser(
        id: String?,
        username: String,
        password: String,
        role: String,
        status: String,
        onDone: () -> Unit
    ) {
        val actor = _adminSession.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.saveAdminUser(actor.role, id, username, password, role, status)
            _isBusy.value = false
            emitMessage(res.message, isError = !res.success)
            if (res.success) onDone()
        }
    }

    fun deleteAdminUser(idAdmin: String) {
        val actor = _adminSession.value ?: return
        viewModelScope.launch {
            val res = repository.deleteAdminUser(actor.role, idAdmin)
            emitMessage(res.message, isError = !res.success)
        }
    }

    fun saveUniformLabels(labels: UniformLabels) {
        viewModelScope.launch {
            repository.setUniformLabels(labels)
            emitMessage("Nama atribut seragam berhasil disimpan!")
        }
    }

    fun setAttendanceEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAttendanceEnabled(enabled)
            emitMessage(
                if (enabled) "Pengisian absensi berhasil DIBUKA." else "Pengisian absensi berhasil DITUTUP."
            )
        }
    }

    fun saveSchoolAndGatewaySettings(schoolName: String, fonnteToken: String) {
        viewModelScope.launch {
            repository.setSchoolName(schoolName)
            repository.setFonnteToken(fonnteToken)
            emitMessage("Pengaturan sekolah & gateway WhatsApp disimpan!")
        }
    }

    fun sendBroadcastWa(recipients: List<SiswaEntity>, templateMessage: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.sendBroadcastWa(recipients, templateMessage)
            _isBusy.value = false
            emitMessage(res.message, isError = !res.success)
        }
    }
}
