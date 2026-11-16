package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AbsensiDao {

    // SISWA
    @Query("SELECT * FROM siswa ORDER BY nama ASC")
    fun observeAllSiswa(): Flow<List<SiswaEntity>>

    @Query("SELECT * FROM siswa ORDER BY nama ASC")
    suspend fun getAllSiswaOnce(): List<SiswaEntity>

    @Query("SELECT * FROM siswa WHERE qrToken = :token OR id = :token LIMIT 1")
    suspend fun findSiswaByToken(token: String): SiswaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSiswa(siswa: SiswaEntity)

    @Update
    suspend fun updateSiswa(siswa: SiswaEntity)

    @Query("DELETE FROM siswa WHERE id = :id")
    suspend fun deleteSiswaById(id: String)

    // KELAS
    @Query("SELECT * FROM kelas ORDER BY id ASC")
    fun observeAllKelas(): Flow<List<KelasEntity>>

    @Query("SELECT * FROM kelas ORDER BY id ASC")
    suspend fun getAllKelasOnce(): List<KelasEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKelas(kelas: KelasEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertKelasList(kelasList: List<KelasEntity>)

    @Update
    suspend fun updateKelas(kelas: KelasEntity)

    @Query("DELETE FROM kelas WHERE id = :id")
    suspend fun deleteKelasById(id: String)

    // PETUGAS OSIS
    @Query("SELECT * FROM petugas_osis ORDER BY id ASC")
    fun observeAllPetugas(): Flow<List<PetugasEntity>>

    @Query("SELECT * FROM petugas_osis ORDER BY id ASC")
    suspend fun getAllPetugasOnce(): List<PetugasEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPetugas(petugas: PetugasEntity)

    @Update
    suspend fun updatePetugas(petugas: PetugasEntity)

    @Query("DELETE FROM petugas_osis WHERE id = :id")
    suspend fun deletePetugasById(id: String)

    // ADMIN USERS
    @Query("SELECT * FROM admin_users ORDER BY id ASC")
    fun observeAllAdminUsers(): Flow<List<AdminUserEntity>>

    @Query("SELECT * FROM admin_users ORDER BY id ASC")
    suspend fun getAllAdminUsersOnce(): List<AdminUserEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdminUser(admin: AdminUserEntity)

    @Update
    suspend fun updateAdminUser(admin: AdminUserEntity)

    @Query("DELETE FROM admin_users WHERE id = :id")
    suspend fun deleteAdminUserById(id: String)

    // ABSENSI
    @Query("SELECT * FROM absensi ORDER BY createdAtMillis DESC")
    fun observeAllAbsensi(): Flow<List<AbsensiEntity>>

    @Query("SELECT * FROM absensi ORDER BY createdAtMillis DESC")
    suspend fun getAllAbsensiOnce(): List<AbsensiEntity>

    @Query("SELECT * FROM absensi WHERE tanggal = :tanggal ORDER BY createdAtMillis DESC")
    fun observeAbsensiByDate(tanggal: String): Flow<List<AbsensiEntity>>

    @Query("SELECT COUNT(*) FROM absensi WHERE idSiswa = :idSiswa")
    suspend fun countAbsensiForSiswa(idSiswa: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAbsensi(absensi: AbsensiEntity)

    @Update
    suspend fun updateAbsensi(absensi: AbsensiEntity)

    @Query("DELETE FROM absensi WHERE id = :id")
    suspend fun deleteAbsensiById(id: String)
}
