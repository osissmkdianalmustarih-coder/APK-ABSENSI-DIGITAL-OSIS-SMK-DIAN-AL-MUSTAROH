package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "siswa")
data class SiswaEntity(
    @PrimaryKey val id: String,
    val nama: String,
    val kelas: String,
    val status: String = "Aktif",
    val qrToken: String = id,
    val waOrtu: String = "",
    val tanggalBuat: String = ""
)

@Entity(tableName = "kelas")
data class KelasEntity(
    @PrimaryKey val id: String,
    val tingkat: String,
    val jurusan: String,
    val namaKelas: String,
    val status: String = "Aktif"
)

@Entity(tableName = "petugas_osis")
data class PetugasEntity(
    @PrimaryKey val id: String,
    val nama: String,
    val kode: String,
    val status: String = "Aktif",
    val tanggalBuat: String = ""
)

@Entity(tableName = "admin_users")
data class AdminUserEntity(
    @PrimaryKey val id: String,
    val username: String,
    val passwordHash: String,
    val role: String = "Anggota OSIS",
    val status: String = "Aktif"
)

@Entity(tableName = "absensi")
data class AbsensiEntity(
    @PrimaryKey val id: String,
    val tanggal: String,
    val jamDatang: String,
    val idSiswa: String,
    val namaSiswa: String,
    val kelas: String,
    val idPetugas: String,
    val namaPetugas: String,
    val statusSeragam: String,
    val badgeSekolah: String = "Lengkap",
    val badgeOsis: String = "Lengkap",
    val dasi: String = "Lengkap",
    val ikatPinggang: String = "Lengkap",
    val kaosKaki: String = "Lengkap",
    val sepatu: String = "Lengkap",
    val atributLainnya: String = "Lengkap",
    val keterangan: String = "-",
    val statusHp: String = "HP Dikumpulkan",
    val keteranganHp: String = "-",
    val fotoPath: String = "",
    val metode: String = "QR Code",
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Serializable
data class UniformLabels(
    val badgeSekolah: String = "Badge Sekolah",
    val badgeOsis: String = "Badge OSIS",
    val dasi: String = "Dasi",
    val ikatPinggang: String = "Ikat Pinggang",
    val kaosKaki: String = "Kaos Kaki",
    val sepatu: String = "Sepatu",
    val atributLainnya: String = "Atribut Lain",
    val catatan: String = "Catatan Seragam"
)
