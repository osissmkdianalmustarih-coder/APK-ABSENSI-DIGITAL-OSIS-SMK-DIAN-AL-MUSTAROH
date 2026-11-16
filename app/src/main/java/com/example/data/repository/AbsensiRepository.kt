package com.example.data.repository

import com.example.data.local.AbsensiDao
import com.example.data.local.AbsensiEntity
import com.example.data.local.AdminUserEntity
import com.example.data.local.KelasEntity
import com.example.data.local.PetugasEntity
import com.example.data.local.SiswaEntity
import com.example.data.local.UniformLabels
import com.example.data.network.FonnteService
import com.example.data.preferences.AppSettings
import com.example.data.preferences.SettingsDataStore
import com.example.util.SecurityUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

data class OperationResult(
    val success: Boolean,
    val message: String
)

data class BroadcastResult(
    val success: Boolean,
    val message: String,
    val sentCount: Int = 0,
    val failedCount: Int = 0
)

class AbsensiRepository(
    private val dao: AbsensiDao,
    private val settingsDataStore: SettingsDataStore,
    private val fonnteService: FonnteService = FonnteService()
) {
    val allSiswa: Flow<List<SiswaEntity>> = dao.observeAllSiswa()
    val allKelas: Flow<List<KelasEntity>> = dao.observeAllKelas()
    val allPetugas: Flow<List<PetugasEntity>> = dao.observeAllPetugas()
    val allAdminUsers: Flow<List<AdminUserEntity>> = dao.observeAllAdminUsers()
    val allAbsensi: Flow<List<AbsensiEntity>> = dao.observeAllAbsensi()
    val settingsFlow: Flow<AppSettings> = settingsDataStore.settingsFlow

    suspend fun ensureBootstrapData() {
        val existingClasses = dao.getAllKelasOnce()
        if (existingClasses.isEmpty()) {
            dao.insertKelasList(
                listOf(
                    KelasEntity("KLS-01", "X", "KK", "X KK", "Aktif"),
                    KelasEntity("KLS-02", "X", "BDP", "X BDP", "Aktif"),
                    KelasEntity("KLS-03", "XI", "KK", "XI KK", "Aktif"),
                    KelasEntity("KLS-04", "XI", "BDP", "XI BDP", "Aktif"),
                    KelasEntity("KLS-05", "XII", "KK", "XII KK", "Aktif"),
                    KelasEntity("KLS-06", "XII", "BDP", "XII BDP", "Aktif")
                )
            )
        }
        val existingAdmins = dao.getAllAdminUsersOnce()
        if (existingAdmins.isEmpty()) {
            dao.insertAdminUser(
                AdminUserEntity(
                    id = "ADM-01",
                    username = "admin",
                    passwordHash = SecurityUtils.computeSha256("admin123"),
                    role = "Developer",
                    status = "Aktif"
                )
            )
        }
        val existingPetugas = dao.getAllPetugasOnce()
        if (existingPetugas.isEmpty()) {
            dao.insertPetugas(
                PetugasEntity(
                    id = "PTG-001",
                    nama = "Petugas OSIS 1",
                    kode = "OSIS01",
                    status = "Aktif",
                    tanggalBuat = SecurityUtils.todayDateStr()
                )
            )
        }
    }

    suspend fun verifyPetugas(kode: String): Result<PetugasEntity> {
        val cleanKode = kode.trim()
        if (cleanKode.isEmpty()) {
            return Result.failure(IllegalArgumentException("Kode verifikasi tidak boleh kosong!"))
        }
        val petugasList = dao.getAllPetugasOnce()
        val match = petugasList.firstOrNull { SecurityUtils.verifySecret(cleanKode, it.kode) }
            ?: return Result.failure(IllegalArgumentException("Kode verifikasi petugas OSIS tidak valid!"))
        if (!match.status.equals("Aktif", ignoreCase = true)) {
            return Result.failure(IllegalStateException("Akun petugas OSIS ini berstatus Tidak Aktif!"))
        }
        return Result.success(match)
    }

    suspend fun loginAdmin(username: String, password: String): Result<AdminUserEntity> {
        val cleanUser = username.trim()
        val cleanPass = password.trim()
        if (cleanUser.isEmpty() || cleanPass.isEmpty()) {
            return Result.failure(IllegalArgumentException("Username dan password wajib diisi!"))
        }
        val admins = dao.getAllAdminUsersOnce()
        val match = admins.firstOrNull {
            it.username == cleanUser && SecurityUtils.verifySecret(cleanPass, it.passwordHash)
        } ?: return Result.failure(IllegalArgumentException("Username atau Password salah!"))

        if (!match.status.equals("Aktif", ignoreCase = true)) {
            return Result.failure(IllegalStateException("Akun Administrator ini dinonaktifkan!"))
        }
        return Result.success(match)
    }

    suspend fun submitAbsensi(
        siswa: SiswaEntity,
        petugas: PetugasEntity,
        badgeSekolah: String,
        badgeOsis: String,
        dasi: String,
        ikatPinggang: String,
        kaosKaki: String,
        sepatu: String,
        atributLainnya: String,
        keterangan: String,
        statusHp: String,
        keteranganHp: String,
        fotoPath: String,
        metode: String
    ): OperationResult {
        val settings = settingsDataStore.settingsFlow.first()
        if (!settings.attendanceEnabled) {
            return OperationResult(false, "Pengisian absensi sedang ditutup oleh Pengawas/Developer.")
        }

        val tglStr = SecurityUtils.todayDateStr()
        val jamStr = SecurityUtils.currentTimeStr()
        val allAbsen = dao.getAllAbsensiOnce()

        val alreadyAttended = allAbsen.any { row ->
            row.tanggal == tglStr && (
                row.idSiswa == siswa.id ||
                    (row.namaSiswa.equals(siswa.nama, ignoreCase = true) &&
                        row.kelas.equals(siswa.kelas, ignoreCase = true))
                )
        }
        if (alreadyAttended) {
            return OperationResult(false, "Siswa \"${siswa.nama}\" (${siswa.kelas}) sudah diabsen hari ini!")
        }

        val maxNum = allAbsen.mapNotNull {
            it.id.filter(Char::isDigit).toIntOrNull()
        }.maxOrNull() ?: 0
        val newId = "ABS-${(maxNum + 1).toString().padStart(4, '0')}"

        val attrs = listOf(badgeSekolah, badgeOsis, dasi, ikatPinggang, kaosKaki, sepatu, atributLainnya)
        val statusSeragamGlobal = if (attrs.all { it == "Lengkap" }) "Lengkap" else "Tidak Lengkap"

        val entry = AbsensiEntity(
            id = newId,
            tanggal = tglStr,
            jamDatang = jamStr,
            idSiswa = siswa.id,
            namaSiswa = siswa.nama,
            kelas = siswa.kelas,
            idPetugas = petugas.id,
            namaPetugas = petugas.nama,
            statusSeragam = statusSeragamGlobal,
            badgeSekolah = badgeSekolah,
            badgeOsis = badgeOsis,
            dasi = dasi,
            ikatPinggang = ikatPinggang,
            kaosKaki = kaosKaki,
            sepatu = sepatu,
            atributLainnya = atributLainnya,
            keterangan = keterangan.ifBlank { "-" },
            statusHp = statusHp,
            keteranganHp = keteranganHp.ifBlank { "-" },
            fotoPath = fotoPath,
            metode = metode,
            createdAtMillis = System.currentTimeMillis()
        )
        dao.insertAbsensi(entry)

        val waSuffix = if (siswa.waOrtu.isNotBlank() && settings.fonnteToken.isNotBlank()) {
            val pesan = buildAttendanceWaMessage(
                namaSiswa = siswa.nama,
                kelas = siswa.kelas,
                tanggal = tglStr,
                jam = jamStr,
                namaPetugas = petugas.nama,
                schoolName = settings.schoolName
            )
            val waRes = fonnteService.sendMessage(settings.fonnteToken, siswa.waOrtu, pesan)
            if (waRes.success) {
                " Notifikasi WhatsApp masuk ke antrean Fonnte."
            } else {
                " Notifikasi WA belum terkirim: ${waRes.reason}"
            }
        } else {
            ""
        }

        return OperationResult(
            true,
            "ABSENSI BERHASIL! Data \"${siswa.nama}\" (${siswa.kelas}) telah tersimpan.$waSuffix"
        )
    }

    suspend fun updateAbsensiPilihan(
        idAbsensi: String,
        badgeSekolah: String,
        badgeOsis: String,
        dasi: String,
        ikatPinggang: String,
        kaosKaki: String,
        sepatu: String,
        atributLainnya: String,
        keterangan: String,
        statusHp: String,
        keteranganHp: String
    ): OperationResult {
        val current = dao.getAllAbsensiOnce().firstOrNull { it.id == idAbsensi }
            ?: return OperationResult(false, "Data absensi tidak ditemukan.")
        val attrs = listOf(badgeSekolah, badgeOsis, dasi, ikatPinggang, kaosKaki, sepatu, atributLainnya)
        val statusSeragamGlobal = if (attrs.all { it == "Lengkap" }) "Lengkap" else "Tidak Lengkap"

        dao.updateAbsensi(
            current.copy(
                statusSeragam = statusSeragamGlobal,
                badgeSekolah = badgeSekolah,
                badgeOsis = badgeOsis,
                dasi = dasi,
                ikatPinggang = ikatPinggang,
                kaosKaki = kaosKaki,
                sepatu = sepatu,
                atributLainnya = atributLainnya,
                keterangan = keterangan.ifBlank { "-" },
                statusHp = statusHp,
                keteranganHp = keteranganHp.ifBlank { "-" }
            )
        )
        return OperationResult(true, "Pilihan absensi berhasil diperbarui!")
    }

    suspend fun saveSiswa(
        id: String?,
        nama: String,
        kelas: String,
        status: String,
        waOrtuRaw: String
    ): OperationResult {
        val cleanNama = nama.trim()
        val cleanKelas = kelas.trim()
        if (cleanNama.isEmpty() || cleanKelas.isEmpty()) {
            return OperationResult(false, "Nama dan kelas siswa wajib diisi!")
        }
        val cleanWa = if (waOrtuRaw.isNotBlank()) {
            try {
                SecurityUtils.normalizeFonnteTarget(waOrtuRaw)
            } catch (e: IllegalArgumentException) {
                return OperationResult(false, e.message ?: "Nomor WA tidak valid.")
            }
        } else {
            ""
        }

        val all = dao.getAllSiswaOnce()
        val duplicate = all.any {
            it.id != (id ?: "") &&
                it.nama.equals(cleanNama, ignoreCase = true) &&
                it.kelas.equals(cleanKelas, ignoreCase = true)
        }
        if (duplicate) {
            return OperationResult(false, "Siswa \"$cleanNama\" sudah terdaftar di kelas $cleanKelas!")
        }

        return if (!id.isNullOrBlank()) {
            val existing = all.firstOrNull { it.id == id }
                ?: return OperationResult(false, "Data siswa tidak ditemukan.")
            dao.updateSiswa(
                existing.copy(
                    nama = cleanNama,
                    kelas = cleanKelas,
                    status = status,
                    waOrtu = cleanWa
                )
            )
            OperationResult(true, "Data siswa berhasil diperbarui!")
        } else {
            val maxNum = all.mapNotNull { it.id.filter(Char::isDigit).toIntOrNull() }.maxOrNull() ?: 0
            val newId = "SIS-${(maxNum + 1).toString().padStart(4, '0')}"
            dao.insertSiswa(
                SiswaEntity(
                    id = newId,
                    nama = cleanNama,
                    kelas = cleanKelas,
                    status = status.ifBlank { "Aktif" },
                    qrToken = newId,
                    waOrtu = cleanWa,
                    tanggalBuat = SecurityUtils.todayDateStr()
                )
            )
            OperationResult(true, "Siswa baru berhasil ditambahkan ($newId)!")
        }
    }

    suspend fun deleteSiswa(idSiswa: String): OperationResult {
        val siswa = dao.getAllSiswaOnce().firstOrNull { it.id == idSiswa }
            ?: return OperationResult(false, "Data siswa tidak ditemukan!")
        val historyCount = dao.countAbsensiForSiswa(idSiswa)
        return if (historyCount > 0) {
            dao.updateSiswa(siswa.copy(status = "Tidak Aktif"))
            OperationResult(
                true,
                "Siswa \"${siswa.nama}\" memiliki riwayat absensi. Status diubah menjadi \"Tidak Aktif\"."
            )
        } else {
            dao.deleteSiswaById(idSiswa)
            OperationResult(true, "Siswa \"${siswa.nama}\" berhasil dihapus permanen.")
        }
    }

    suspend fun saveKelas(
        id: String?,
        tingkat: String,
        jurusan: String,
        namaKelasCustom: String,
        status: String
    ): OperationResult {
        val cleanTingkat = tingkat.trim().uppercase()
        val cleanJurusan = jurusan.trim().uppercase()
        val cleanNama = namaKelasCustom.trim().ifBlank { "$cleanTingkat $cleanJurusan" }
        if (cleanTingkat.isEmpty() || cleanJurusan.isEmpty()) {
            return OperationResult(false, "Tingkat dan jurusan wajib diisi!")
        }

        val all = dao.getAllKelasOnce()
        val duplicate = all.any {
            it.id != (id ?: "") && it.namaKelas.equals(cleanNama, ignoreCase = true)
        }
        if (duplicate) {
            return OperationResult(false, "Kelas \"$cleanNama\" sudah ada!")
        }

        return if (!id.isNullOrBlank()) {
            val existing = all.firstOrNull { it.id == id }
                ?: return OperationResult(false, "ID Kelas tidak ditemukan!")
            dao.updateKelas(
                existing.copy(
                    tingkat = cleanTingkat,
                    jurusan = cleanJurusan,
                    namaKelas = cleanNama,
                    status = status
                )
            )
            OperationResult(true, "Data kelas berhasil diperbarui!")
        } else {
            val maxNum = all.mapNotNull { it.id.filter(Char::isDigit).toIntOrNull() }.maxOrNull() ?: 0
            val newId = "KLS-${(maxNum + 1).toString().padStart(2, '0')}"
            dao.insertKelas(
                KelasEntity(
                    id = newId,
                    tingkat = cleanTingkat,
                    jurusan = cleanJurusan,
                    namaKelas = cleanNama,
                    status = status.ifBlank { "Aktif" }
                )
            )
            OperationResult(true, "Kelas \"$cleanNama\" berhasil ditambahkan!")
        }
    }

    suspend fun deleteKelas(idKelas: String): OperationResult {
        dao.deleteKelasById(idKelas)
        return OperationResult(true, "Data kelas berhasil dihapus!")
    }

    suspend fun savePetugas(
        id: String?,
        nama: String,
        kode: String,
        status: String
    ): OperationResult {
        val cleanNama = nama.trim()
        val cleanKode = kode.trim()
        if (cleanNama.isEmpty()) return OperationResult(false, "Nama petugas wajib diisi!")

        val all = dao.getAllPetugasOnce()
        return if (!id.isNullOrBlank()) {
            val existing = all.firstOrNull { it.id == id }
                ?: return OperationResult(false, "Petugas tidak ditemukan!")
            val updatedKode = if (cleanKode.isNotEmpty()) cleanKode else existing.kode
            dao.updatePetugas(
                existing.copy(
                    nama = cleanNama,
                    kode = updatedKode,
                    status = status
                )
            )
            OperationResult(true, "Data petugas OSIS berhasil diperbarui!")
        } else {
            if (cleanKode.isEmpty()) return OperationResult(false, "Kode verifikasi wajib diisi!")
            if (all.any { SecurityUtils.verifySecret(cleanKode, it.kode) }) {
                return OperationResult(false, "Kode verifikasi sudah digunakan oleh petugas lain!")
            }
            val maxNum = all.mapNotNull { it.id.filter(Char::isDigit).toIntOrNull() }.maxOrNull() ?: 0
            val newId = "PTG-${(maxNum + 1).toString().padStart(3, '0')}"
            dao.insertPetugas(
                PetugasEntity(
                    id = newId,
                    nama = cleanNama,
                    kode = cleanKode,
                    status = status.ifBlank { "Aktif" },
                    tanggalBuat = SecurityUtils.todayDateStr()
                )
            )
            OperationResult(true, "Petugas OSIS baru berhasil ditambahkan!")
        }
    }

    suspend fun saveAdminUser(
        actorRole: String,
        id: String?,
        username: String,
        passwordPlain: String,
        role: String,
        status: String
    ): OperationResult {
        if (actorRole == "Pengawas" && role !in listOf("Pengawas", "Anggota OSIS")) {
            return OperationResult(false, "Pengawas hanya boleh membuat akun Pengawas atau Anggota OSIS.")
        }
        if (actorRole !in listOf("Developer", "Pengawas")) {
            return OperationResult(false, "Akses ditolak.")
        }
        val cleanUser = username.trim()
        if (cleanUser.isEmpty()) return OperationResult(false, "Username wajib diisi!")

        val all = dao.getAllAdminUsersOnce()
        val duplicate = all.any {
            it.id != (id ?: "") && it.username.equals(cleanUser, ignoreCase = true)
        }
        if (duplicate) return OperationResult(false, "Username sudah digunakan!")

        return if (!id.isNullOrBlank() && actorRole == "Developer") {
            val existing = all.firstOrNull { it.id == id }
                ?: return OperationResult(false, "ID Admin tidak ditemukan.")
            val newHash = if (passwordPlain.isNotBlank()) {
                SecurityUtils.computeSha256(passwordPlain.trim())
            } else {
                existing.passwordHash
            }
            dao.updateAdminUser(
                existing.copy(
                    username = cleanUser,
                    passwordHash = newHash,
                    role = role,
                    status = status
                )
            )
            OperationResult(true, "Akun admin berhasil diperbarui!")
        } else {
            if (passwordPlain.isBlank()) return OperationResult(false, "Password wajib diisi!")
            val maxNum = all.mapNotNull { it.id.filter(Char::isDigit).toIntOrNull() }.maxOrNull() ?: 0
            val newId = "ADM-${(maxNum + 1).toString().padStart(2, '0')}"
            dao.insertAdminUser(
                AdminUserEntity(
                    id = newId,
                    username = cleanUser,
                    passwordHash = SecurityUtils.computeSha256(passwordPlain.trim()),
                    role = role,
                    status = status.ifBlank { "Aktif" }
                )
            )
            OperationResult(true, "Akun $role \"$cleanUser\" berhasil dibuat!")
        }
    }

    suspend fun deleteAdminUser(actorRole: String, idAdmin: String): OperationResult {
        if (actorRole != "Developer") return OperationResult(false, "Akses khusus Developer.")
        dao.deleteAdminUserById(idAdmin)
        return OperationResult(true, "Akun admin berhasil dihapus!")
    }

    suspend fun sendBroadcastWa(
        recipients: List<SiswaEntity>,
        templateMessage: String
    ): BroadcastResult {
        val settings = settingsDataStore.settingsFlow.first()
        if (settings.fonnteToken.isBlank()) {
            return BroadcastResult(
                false,
                "Token Fonnte belum diatur. Isi Token Fonnte di menu Pengaturan untuk mengirim broadcast otomatis."
            )
        }
        if (recipients.isEmpty()) {
            return BroadcastResult(false, "Tidak ada penerima yang memiliki nomor WhatsApp valid.")
        }
        var sent = 0
        var failed = 0
        val failedDetails = mutableListOf<String>()
        recipients.forEachIndexed { index, student ->
            val personalMsg = templateMessage
                .replace("{nama}", student.nama, ignoreCase = true)
                .replace("{kelas}", student.kelas, ignoreCase = true)
            val res = fonnteService.sendMessage(settings.fonnteToken, student.waOrtu, personalMsg)
            if (res.success) {
                sent++
            } else {
                failed++
                failedDetails.add("${student.nama} (${res.reason})")
            }
            if (index < recipients.lastIndex) delay(250)
        }
        val extra = if (failedDetails.isNotEmpty()) " Gagal: ${failedDetails.take(3).joinToString("; ")}" else ""
        return BroadcastResult(
            success = sent > 0,
            message = "Broadcast selesai: $sent masuk antrean, $failed gagal dari ${recipients.size} penerima.$extra",
            sentCount = sent,
            failedCount = failed
        )
    }

    suspend fun setDarkTheme(enabled: Boolean) = settingsDataStore.setDarkTheme(enabled)
    suspend fun setSoundEnabled(enabled: Boolean) = settingsDataStore.setSoundEnabled(enabled)
    suspend fun setSchoolName(name: String) = settingsDataStore.setSchoolName(name)
    suspend fun setAttendanceEnabled(enabled: Boolean) = settingsDataStore.setAttendanceEnabled(enabled)
    suspend fun setUniformLabels(labels: UniformLabels) = settingsDataStore.setUniformLabels(labels)
    suspend fun setFonnteToken(token: String) = settingsDataStore.setFonnteToken(token)

    private fun buildAttendanceWaMessage(
        namaSiswa: String,
        kelas: String,
        tanggal: String,
        jam: String,
        namaPetugas: String,
        schoolName: String
    ): String {
        return buildString {
            append("📢 *PEMBERITAHUAN KEHADIRAN SISWA*\n")
            append("━━━━━━━━━━━━━━━━━━\n\n")
            append("Assalamu’alaikum warahmatullahi wabarakatuh.\n\n")
            append("Yth. Bapak/Ibu Orang Tua/Wali,\n")
            append("Dengan hormat, kami informasikan bahwa putra/putri Bapak/Ibu telah tercatat hadir di sekolah dengan rincian berikut:\n\n")
            append("👤 *Nama Siswa:* $namaSiswa\n")
            append("🏫 *Kelas:* $kelas\n")
            append("📅 *Tanggal:* $tanggal\n")
            append("🕒 *Jam Kedatangan:* $jam WIB\n")
            append("✅ *Status:* HADIR\n")
            append("🧑‍💼 *Petugas OSIS:* $namaPetugas\n\n")
            append("Informasi ini dikirim secara otomatis melalui sistem absensi digital sekolah.\n\n")
            append("Hormat kami,\n")
            append("*Tim Absensi*\n")
            append("*OSIS $schoolName*")
        }
    }
}
