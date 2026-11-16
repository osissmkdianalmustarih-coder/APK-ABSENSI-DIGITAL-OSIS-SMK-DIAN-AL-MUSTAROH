/**
 * SISTEM ABSENSI SISWA SEKOLAH - PETUGAS OSIS & ADMINISTRATOR
 */

const SPREADSHEET_ID = "10Pkc9Bzzcs-6huU5YRedxKfkCXHXWlQT"; 
const DEFAULT_DRIVE_FOLDER_ID = "1UkGd1Nu9qihbrlFM2OBNeICQvGx8mvdp";

function getDatabase() {
  if (SPREADSHEET_ID && SPREADSHEET_ID.trim() !== "") {
    return SpreadsheetApp.openById(SPREADSHEET_ID.trim());
  }
  return SpreadsheetApp.getActiveSpreadsheet();
}

function doGet() {
  return HtmlService.createHtmlOutputFromFile('Index')
    .setTitle('Sistem Absensi OSIS - SMK Dian Al Mustarih')
    .addMetaTag('viewport', 'width=device-width, initial-scale=1')
    .setXFrameOptionsMode(HtmlService.XFrameOptionsMode.ALLOWALL);
}

function computeSha256(text) {
  const rawHash = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, text, Utilities.Charset.UTF_8);
  let txtHash = '';
  for (let i = 0; i < rawHash.length; i++) {
    let hashVal = rawHash[i];
    if (hashVal < 0) hashVal += 256;
    if (hashVal.toString(16).length === 1) txtHash += '0';
    txtHash += hashVal.toString(16);
  }
  return txtHash;
}

function verifySecret(inputPlain, storedValue) {
  const cleanInput = inputPlain.toString().trim();
  const cleanStored = storedValue.toString().trim();
  if (!cleanInput || !cleanStored) return false;
  return (cleanInput === cleanStored) || (computeSha256(cleanInput).toLowerCase() === cleanStored.toLowerCase());
}

function getSettingValue(ss, key, defaultValue) {
  const sheet = ss.getSheetByName('PENGATURAN');
  if (!sheet) return defaultValue;
  const data = sheet.getDataRange().getValues();
  for (let i = 1; i < data.length; i++) {
    if (data[i][0] && data[i][0].toString().trim() === key) {
      return data[i][1] ? data[i][1].toString().trim() : defaultValue;
    }
  }
  return defaultValue;
}

function ensureKolomWaOrtu_(sheet) {
  const lastColumn = Math.max(sheet.getLastColumn(), 1);
  const headers = sheet.getRange(1, 1, 1, lastColumn).getDisplayValues()[0];
  const found = headers.findIndex(h => String(h).trim().toLowerCase() === 'no_wa_orang_tua');
  if (found >= 0) return found + 1;
  const newColumn = lastColumn + 1;
  sheet.getRange(1, newColumn).setValue('No_WA_Orang_Tua');
  sheet.getRange(1, newColumn).setFontWeight('bold');
  return newColumn;
}

function normalizeFonnteTarget_(phone) {
  let digits = String(phone || '').replace(/\D/g, '');
  if (!digits) throw new Error('Nomor WhatsApp orang tua belum diisi.');
  if (digits.startsWith('0')) digits = '62' + digits.substring(1);
  else if (digits.startsWith('8')) digits = '62' + digits;
  if (!digits.startsWith('62') || digits.length < 10 || digits.length > 15) {
    throw new Error('Format nomor WhatsApp tidak valid. Gunakan nomor Indonesia, misalnya 081234567890.');
  }
  return digits;
}

function sendFonnteMessage_(target, message) {
  const token = PropertiesService.getScriptProperties().getProperty('FONNTE_TOKEN');
  if (!token) return { success: false, reason: 'Token FONNTE_TOKEN belum ditemukan di Script Properties.' };

  const response = UrlFetchApp.fetch('https://api.fonnte.com/send', {
    method: 'post',
    headers: { Authorization: token },
    payload: {
      target: normalizeFonnteTarget_(target),
      message: String(message || ''),
      countryCode: '0'
    },
    muteHttpExceptions: true,
    followRedirects: true
  });

  const httpCode = response.getResponseCode();
  const body = response.getContentText();
  let result;
  try { result = JSON.parse(body); }
  catch (err) { return { success: false, reason: 'Respons Fonnte tidak dapat dibaca (HTTP ' + httpCode + ').' }; }

  const ok = result.status === true || result.Status === true;
  return {
    success: ok,
    detail: result.detail || '',
    reason: result.reason || (!ok ? 'Fonnte menolak permintaan (HTTP ' + httpCode + ').' : ''),
    requestid: result.requestid || ''
  };
}

function verifikasiPetugas(kodePetugas) {
  try {
    if (!kodePetugas) return { success: false, message: 'Kode verifikasi tidak boleh kosong!' };
    
    const ss = getDatabase();
    const sheetPetugas = ss.getSheetByName('PETUGAS_OSIS');
    if (!sheetPetugas) return { success: false, message: 'Sheet "PETUGAS_OSIS" tidak ditemukan!' };
    
    const dataPetugas = sheetPetugas.getDataRange().getValues();
    const cleanKode = kodePetugas.toString().trim();
    let petugasData = null;

    for (let i = 1; i < dataPetugas.length; i++) {
      if (!dataPetugas[i][0]) continue;
      const kodeRow = dataPetugas[i][2] ? dataPetugas[i][2].toString().trim() : '';
      const statusRow = dataPetugas[i][3] ? dataPetugas[i][3].toString().trim() : 'Aktif';

      if (verifySecret(cleanKode, kodeRow)) {
        if (statusRow.toLowerCase() !== 'aktif') {
          return { success: false, message: 'Akun petugas OSIS ini berstatus Tidak Aktif!' };
        }
        petugasData = {
          id: dataPetugas[i][0].toString().trim(),
          nama: dataPetugas[i][1].toString().trim()
        };
        break;
      }
    }

    if (!petugasData) {
      return { success: false, message: 'Kode verifikasi petugas OSIS tidak valid!' };
    }

    const sheetKelas = ss.getSheetByName('KELAS');
    let listKelas = [];
    if (sheetKelas && sheetKelas.getLastRow() > 1) {
      const dataKelas = sheetKelas.getDataRange().getValues();
      for (let i = 1; i < dataKelas.length; i++) {
        if (!dataKelas[i][0]) continue;
        const namaKelas = dataKelas[i][3] ? dataKelas[i][3].toString().trim() : '';
        const statusKelas = dataKelas[i][4] ? dataKelas[i][4].toString().trim() : 'Aktif';
        if (namaKelas && statusKelas.toLowerCase() === 'aktif' && !listKelas.includes(namaKelas)) {
          listKelas.push(namaKelas);
        }
      }
    }
    if (listKelas.length === 0) {
      listKelas = ['X KK', 'X BDP', 'XI KK', 'XI BDP', 'XII KK', 'XII BDP'];
    }

    const sheetSiswa = ss.getSheetByName('SISWA');
    if (!sheetSiswa) return { success: false, message: 'Sheet "SISWA" tidak ditemukan!' };
    const dataSiswa = sheetSiswa.getDataRange().getValues();
    let listSiswa = [];
    const seenKeys = new Set();

    for (let i = 1; i < dataSiswa.length; i++) {
      if (!dataSiswa[i][0] || !dataSiswa[i][1]) continue;
      const statusSiswa = dataSiswa[i][3] ? dataSiswa[i][3].toString().trim() : 'Aktif';
      if (statusSiswa.toLowerCase() === 'aktif') {
        const idS = dataSiswa[i][0].toString().trim();
        const namaS = dataSiswa[i][1].toString().trim();
        const kelasS = dataSiswa[i][2] ? dataSiswa[i][2].toString().trim() : '';
        const uniqueKey = `${namaS.toLowerCase()}|${kelasS.toLowerCase()}`;

        if (!seenKeys.has(uniqueKey)) {
          seenKeys.add(uniqueKey);
          listSiswa.push({
            id: idS,
            nama: namaS,
            kelas: kelasS,
            qrToken: idS
          });
        }
      }
    }

    listSiswa.sort((a, b) => a.nama.localeCompare(b.nama));

    const timeZone = Session.getScriptTimeZone();
    const tglStr = Utilities.formatDate(new Date(), timeZone, "yyyy-MM-dd");
    const sheetAbsen = ss.getSheetByName('ABSENSI');
    const attendedIdsToday = [];
    if (sheetAbsen && sheetAbsen.getLastRow() > 1) {
      const dataAbsen = sheetAbsen.getDataRange().getValues();
      for (let i = 1; i < dataAbsen.length; i++) {
        const rowTgl = dataAbsen[i][1] instanceof Date ? Utilities.formatDate(dataAbsen[i][1], timeZone, "yyyy-MM-dd") : String(dataAbsen[i][1] || '').trim();
        if (rowTgl === tglStr) {
          const idS = String(dataAbsen[i][3] || '').trim();
          if (idS && idS !== '-') attendedIdsToday.push(idS);
        }
      }
    }

    const namaSekolah = getSettingValue(ss, 'NAMA_SEKOLAH', 'SMK DIAN AL MUSTARIH');

    return {
      success: true,
      petugas: petugasData,
      namaSekolah: namaSekolah,
      listKelas: listKelas,
      listSiswa: listSiswa,
      attendedIdsToday: attendedIdsToday
    };
  } catch (err) {
    return { success: false, message: 'Terjadi kesalahan server: ' + err.toString() };
  }
}

function uploadFotoToDrive(ss, base64Data, fileName) {
  try {
    const folderId = getSettingValue(ss, 'DRIVE_FOLDER_ID', DEFAULT_DRIVE_FOLDER_ID);
    const folder = DriveApp.getFolderById(folderId);
    const splitData = base64Data.split(',');
    const contentType = splitData[0].match(/:(.*?);/)[1];
    const bytes = Utilities.base64Decode(splitData[1]);
    const blob = Utilities.newBlob(bytes, contentType, fileName);
    
    const file = folder.createFile(blob);
    file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
    return file.getUrl();
  } catch (err) {
    throw new Error("Gagal mengunggah foto ke Google Drive: " + err.toString());
  }
}

function submitAbsensi(payload) {
  const lock = LockService.getScriptLock();
  let lockHeld = false;
  try {
    lock.waitLock(15000);
    lockHeld = true;

    const ss = getDatabase();
    const sheetAbsen = ss.getSheetByName('ABSENSI');
    const sheetSiswa = ss.getSheetByName('SISWA');
    if (!sheetAbsen) return { success: false, message: 'Sheet "ABSENSI" tidak ditemukan!' };
    if (!sheetSiswa) return { success: false, message: 'Sheet "SISWA" tidak ditemukan!' };
    const waColumn = ensureKolomWaOrtu_(sheetSiswa);

    const now = new Date();
    const timeZone = Session.getScriptTimeZone();
    const tglStr = Utilities.formatDate(now, timeZone, "yyyy-MM-dd");
    const jamStr = Utilities.formatDate(now, timeZone, "HH:mm:ss");

    const cleanIdSiswa = payload.idSiswa ? payload.idSiswa.toString().trim() : '';
    const cleanNamaSiswa = payload.namaSiswa.toString().trim();
    const cleanKelas = payload.kelas.toString().trim();

    const dataAbsen = sheetAbsen.getDataRange().getValues();
    for (let i = 1; i < dataAbsen.length; i++) {
      if (!dataAbsen[i][0]) continue;
      const rowTgl = dataAbsen[i][1] instanceof Date ?
        Utilities.formatDate(dataAbsen[i][1], timeZone, "yyyy-MM-dd") :
        dataAbsen[i][1].toString().trim();
      const rowIdSiswa = dataAbsen[i][3] ? dataAbsen[i][3].toString().trim() : '';
      const rowNama = dataAbsen[i][4] ? dataAbsen[i][4].toString().trim() : '';
      const rowKelas = dataAbsen[i][5] ? dataAbsen[i][5].toString().trim() : '';
      if (rowTgl === tglStr) {
        const sameById = cleanIdSiswa && rowIdSiswa && (rowIdSiswa === cleanIdSiswa);
        const sameByNameClass = (rowNama.toLowerCase() === cleanNamaSiswa.toLowerCase() && rowKelas.toLowerCase() === cleanKelas.toLowerCase());
        if (sameById || sameByNameClass) {
          return { success: false, message: `Siswa "${cleanNamaSiswa}" (${cleanKelas}) sudah diabsen hari ini!` };
        }
      }
    }

    let fotoUrl = "";
    if (payload.fotoBase64) {
      const namaFile = `Absen_${cleanKelas.replace(/\s+/g, '')}_${cleanNamaSiswa.replace(/\s+/g, '_')}_${tglStr}_${now.getTime()}.${payload.fotoExt || 'jpg'}`;
      fotoUrl = uploadFotoToDrive(ss, payload.fotoBase64, namaFile);
    }

    let maxNum = 0;
    for (let i = 1; i < dataAbsen.length; i++) {
      const val = dataAbsen[i][0] ? dataAbsen[i][0].toString().replace(/\D/g, '') : '';
      const num = parseInt(val, 10);
      if (!isNaN(num) && num > maxNum) maxNum = num;
    }
    const newId = 'ABS-' + String(maxNum + 1).padStart(4, '0');

    const attrValues = [payload.badgeSekolah, payload.badgeOsis, payload.dasi, payload.ikatPinggang, payload.kaosKaki, payload.sepatu, payload.atributLainnya];
    const statusSeragamGlobal = attrValues.every(val => val === 'Lengkap') ? 'Lengkap' : 'Tidak Lengkap';

    sheetAbsen.appendRow([
      newId, tglStr, jamStr, cleanIdSiswa || '-', cleanNamaSiswa, cleanKelas,
      payload.idPetugas || '-', payload.namaPetugas, statusSeragamGlobal,
      payload.badgeSekolah, payload.badgeOsis, payload.dasi, payload.ikatPinggang,
      payload.kaosKaki, payload.sepatu, payload.atributLainnya,
      payload.keterangan || '-', payload.statusHp || 'HP Tidak Dikumpulkan',
      payload.keteranganHp || '-', fotoUrl
    ]);
    SpreadsheetApp.flush();

    let nomorOrtu = '';
    const dataSiswa = sheetSiswa.getDataRange().getValues();
    for (let i = 1; i < dataSiswa.length; i++) {
      if (cleanIdSiswa && String(dataSiswa[i][0] || '').trim() === cleanIdSiswa) {
        nomorOrtu = dataSiswa[i][waColumn - 1] ? String(dataSiswa[i][waColumn - 1]).trim() : '';
        break;
      }
    }

    lock.releaseLock();
    lockHeld = false;

    let notifWa = { success: false, reason: nomorOrtu ? 'Notifikasi belum dikirim.' : 'Nomor WhatsApp orang tua belum diisi.' };
    if (nomorOrtu) {
      const namaPetugasNotif = String(payload.namaPetugas || 'Petugas Absensi').trim();
      const pesan =
        '📢 *PEMBERITAHUAN KEHADIRAN SISWA*\n' +
        '━━━━━━━━━━━━━━━━━━\n\n' +
        'Assalamu’alaikum warahmatullahi wabarakatuh.\n\n' +
        'Yth. Bapak/Ibu Orang Tua/Wali,\n' +
        'Dengan hormat, kami informasikan bahwa putra/putri Bapak/Ibu telah tercatat hadir di sekolah dengan rincian berikut:\n\n' +
        '👤 *Nama Siswa:* ' + cleanNamaSiswa + '\n' +
        '🏫 *Kelas:* ' + cleanKelas + '\n' +
        '📅 *Tanggal:* ' + tglStr + '\n' +
        '🕒 *Jam Kedatangan:* ' + jamStr + ' WIB\n' +
        '✅ *Status:* HADIR\n' +
        '🧑‍💼 *Petugas OSIS:* ' + namaPetugasNotif + '\n\n' +
        'Informasi ini dikirim secara otomatis melalui sistem absensi digital sekolah.\n\n' +
        'Terima kasih atas perhatian dan kerja sama Bapak/Ibu.\n\n' +
        'Hormat kami,\n' +
        '*Tim Absensi*\n' +
        '*OSIS SMK Dian Al Mustarih*\n\n' +
        'Wassalamu’alaikum warahmatullahi wabarakatuh.' +
        'Note: *Jika Bapak/Ibu ingin bertanya tentang Putra/Putri bapak/ibu silahkan menghubungi walas masing" Terimakasih.*';
      try { notifWa = sendFonnteMessage_(nomorOrtu, pesan); }
      catch (waErr) { notifWa = { success: false, reason: waErr.message || String(waErr) }; }
    }

    return {
      success: true,
      idAbsensi: newId,
      jam: jamStr,
      notifWa: notifWa.success,
      message: `ABSENSI BERHASIL! Data "${cleanNamaSiswa}" (${cleanKelas}) telah tersimpan.` +
        (notifWa.success ? ' Notifikasi WhatsApp masuk ke antrean Fonnte.' : ' Notifikasi WhatsApp belum terkirim: ' + notifWa.reason)
    };
  } catch (err) {
    return { success: false, message: 'Gagal menyimpan absensi: ' + err.toString() };
  } finally {
    if (lockHeld) lock.releaseLock();
  }
}

function loginAdmin(username, password) {
  try {
    if (!username || !password) return { success: false, message: 'Username dan password wajib diisi!' };
    const ss = getDatabase();
    const sheet = ss.getSheetByName('ADMIN');
    if (!sheet) return { success: false, message: 'Sheet "ADMIN" tidak ditemukan!' };
    
    const data = sheet.getDataRange().getValues();
    const cleanUser = username.toString().trim();

    for (let i = 1; i < data.length; i++) {
      if (!data[i][0]) continue;
      const u = data[i][1] ? data[i][1].toString().trim() : '';
      const pHash = data[i][2] ? data[i][2].toString().trim() : '';
      const role = data[i][3] ? data[i][3].toString().trim() : 'Administrator';
      const status = data[i][4] ? data[i][4].toString().trim() : 'Aktif';
      
      if (u === cleanUser && verifySecret(password, pHash)) {
        if (status.toLowerCase() !== 'aktif') {
          return { success: false, message: 'Akun Administrator ini dinonaktifkan!' };
        }
        return { success: true, username: u, role: role };
      }
    }
    return { success: false, message: 'Username atau Password salah!' };
  } catch (err) {
    return { success: false, message: 'Gagal login: ' + err.toString() };
  }
}

function getAdminDashboardData() {
  try {
    const ss = getDatabase();
    const sheetSiswa = ss.getSheetByName('SISWA');
    const sheetAbsen = ss.getSheetByName('ABSENSI');
    const sheetPetugas = ss.getSheetByName('PETUGAS_OSIS');
    const sheetKelas = ss.getSheetByName('KELAS');

    if (!sheetSiswa || !sheetAbsen || !sheetPetugas) {
      return { success: false, message: 'Sheet database belum lengkap!' };
    }

    const waColumn = ensureKolomWaOrtu_(sheetSiswa);
    const dataSiswa = sheetSiswa.getDataRange().getValues();
    const dataAbsen = sheetAbsen.getDataRange().getValues();
    const dataPetugas = sheetPetugas.getDataRange().getValues();
    const timeZone = Session.getScriptTimeZone();
    const nowStr = Utilities.formatDate(new Date(), timeZone, "yyyy-MM-dd");
    const namaSekolah = getSettingValue(ss, 'NAMA_SEKOLAH', 'SMK DIAN AL MUSTARIH');

    let listKelasDetail = [];
    let listKelasNama = [];
    if (sheetKelas && sheetKelas.getLastRow() > 1) {
      const dataKelas = sheetKelas.getDataRange().getValues();
      for (let i = 1; i < dataKelas.length; i++) {
        if (!dataKelas[i][0]) continue;
        const namaK = dataKelas[i][3] ? dataKelas[i][3].toString().trim() : '';
        const statusK = dataKelas[i][4] ? dataKelas[i][4].toString().trim() : 'Aktif';
        listKelasDetail.push({
          id: dataKelas[i][0].toString().trim(),
          tingkat: dataKelas[i][1] ? dataKelas[i][1].toString().trim() : '',
          jurusan: dataKelas[i][2] ? dataKelas[i][2].toString().trim() : '',
          namaKelas: namaK,
          status: statusK
        });
        if (namaK && !listKelasNama.includes(namaK)) {
          listKelasNama.push(namaK);
        }
      }
    }

    let totalSiswaAktif = 0;
    let listSiswaMaster = [];
    let statsPerKelas = {};

    listKelasNama.forEach(k => {
      statsPerKelas[k] = { totalAktif: 0, sudahAbsenHariIni: 0 };
    });

    for (let i = 1; i < dataSiswa.length; i++) {
      if (!dataSiswa[i][0]) continue;
      const idSiswa = dataSiswa[i][0].toString().trim();
      const namaSiswa = dataSiswa[i][1] ? dataSiswa[i][1].toString().trim() : '';
      const kelasSiswa = dataSiswa[i][2] ? dataSiswa[i][2].toString().trim() : '';
      const statusSiswa = dataSiswa[i][3] ? dataSiswa[i][3].toString().trim() : 'Aktif';
      const tglBuat = dataSiswa[i][4] || '';

      listSiswaMaster.push({
        id: idSiswa,
        nama: namaSiswa,
        kelas: kelasSiswa,
        status: statusSiswa,
        qrToken: idSiswa,
        waOrtu: dataSiswa[i][waColumn - 1] ? String(dataSiswa[i][waColumn - 1]).trim() : '',
        tanggal: tglBuat instanceof Date ? Utilities.formatDate(tglBuat, timeZone, "yyyy-MM-dd") : tglBuat.toString()
      });

      if (kelasSiswa && !listKelasNama.includes(kelasSiswa)) {
        listKelasNama.push(kelasSiswa);
        statsPerKelas[kelasSiswa] = { totalAktif: 0, sudahAbsenHariIni: 0 };
      }

      if (statusSiswa.toLowerCase() === 'aktif') {
        totalSiswaAktif++;
        if (kelasSiswa) {
          if (!statsPerKelas[kelasSiswa]) statsPerKelas[kelasSiswa] = { totalAktif: 0, sudahAbsenHariIni: 0 };
          statsPerKelas[kelasSiswa].totalAktif++;
        }
      }
    }

    let sudahAbsenHariIni = 0;
    let seragamLengkap = 0;
    let seragamTidakLengkap = 0;
    let hpDikumpulkan = 0;
    let hpTidakDikumpulkan = 0;
    let listRekap = [];
    let setSiswaSudahAbsen = new Set();
    let attendedIdsToday = [];

    for (let i = 1; i < dataAbsen.length; i++) {
      if (!dataAbsen[i][0]) continue;

      const rowTgl = dataAbsen[i][1] instanceof Date ? 
        Utilities.formatDate(dataAbsen[i][1], timeZone, "yyyy-MM-dd") : 
        dataAbsen[i][1].toString().trim();

      const idSiswa = dataAbsen[i][3] ? dataAbsen[i][3].toString().trim() : '-';
      const namaSiswa = dataAbsen[i][4] ? dataAbsen[i][4].toString().trim() : '';
      const kelasSiswa = dataAbsen[i][5] ? dataAbsen[i][5].toString().trim() : '';
      const idPetugas = dataAbsen[i][6] ? dataAbsen[i][6].toString().trim() : '-';
      const namaPetugas = dataAbsen[i][7] ? dataAbsen[i][7].toString().trim() : 'System';
      const statusSeragam = dataAbsen[i][8] ? dataAbsen[i][8].toString().trim() : 'Tidak Lengkap';
      const statusHp = dataAbsen[i][17] ? dataAbsen[i][17].toString().trim() : 'HP Tidak Dikumpulkan';
      const ketHp = dataAbsen[i][18] ? dataAbsen[i][18].toString().trim() : '-';
      const fotoUrl = dataAbsen[i][19] ? dataAbsen[i][19].toString().trim() : '';

      if (rowTgl === nowStr) {
        if (idSiswa && idSiswa !== '-') attendedIdsToday.push(idSiswa);
        const uniqueKey = `${namaSiswa.toLowerCase()}|${kelasSiswa.toLowerCase()}`;
        if (!setSiswaSudahAbsen.has(uniqueKey)) {
          sudahAbsenHariIni++;
          setSiswaSudahAbsen.add(uniqueKey);
          if (kelasSiswa && statsPerKelas[kelasSiswa]) {
            statsPerKelas[kelasSiswa].sudahAbsenHariIni++;
          }
        }
        if (statusSeragam === 'Lengkap') seragamLengkap++;
        else seragamTidakLengkap++;

        if (statusHp === 'HP Dikumpulkan') hpDikumpulkan++;
        else hpTidakDikumpulkan++;
      }

      listRekap.push({
        id: dataAbsen[i][0].toString().trim(),
        tanggal: rowTgl,
        jamDatang: dataAbsen[i][2] instanceof Date ? 
          Utilities.formatDate(dataAbsen[i][2], timeZone, "HH:mm:ss") : 
          dataAbsen[i][2].toString(),
        idSiswa: idSiswa,
        nama: namaSiswa,
        kelas: kelasSiswa,
        idPetugas: idPetugas,
        namaPetugas: namaPetugas,
        statusSeragam: statusSeragam,
        badgeSekolah: dataAbsen[i][9] || '-',
        badgeOsis: dataAbsen[i][10] || '-',
        dasi: dataAbsen[i][11] || '-',
        ikatPinggang: dataAbsen[i][12] || '-',
        kaosKaki: dataAbsen[i][13] || '-',
        sepatu: dataAbsen[i][14] || '-',
        atributLainnya: dataAbsen[i][15] || '-',
        keterangan: dataAbsen[i][16] || '-',
        statusHp: statusHp,
        keteranganHp: ketHp,
        fotoUrl: fotoUrl
      });
    }

    let mapPetugasCountToday = {};
    let mapPetugasCountTotal = {};
    dataAbsen.forEach((row, idx) => {
      if (idx === 0 || !row[0]) return;
      const rTgl = row[1] instanceof Date ? Utilities.formatDate(row[1], timeZone, "yyyy-MM-dd") : String(row[1] || '').trim();
      const pId = String(row[6] || '').trim();
      if (pId && pId !== '-') {
        mapPetugasCountTotal[pId] = (mapPetugasCountTotal[pId] || 0) + 1;
        if (rTgl === nowStr) {
          mapPetugasCountToday[pId] = (mapPetugasCountToday[pId] || 0) + 1;
        }
      }
    });

    let listPetugas = [];
    for (let i = 1; i < dataPetugas.length; i++) {
      if (!dataPetugas[i][0]) continue;
      const pId = dataPetugas[i][0].toString().trim();
      const tglP = dataPetugas[i][4] || '';
      listPetugas.push({
        id: pId,
        nama: dataPetugas[i][1] ? dataPetugas[i][1].toString().trim() : '',
        kode: dataPetugas[i][2] ? dataPetugas[i][2].toString().trim() : '',
        status: dataPetugas[i][3] ? dataPetugas[i][3].toString().trim() : 'Aktif',
        todayCount: mapPetugasCountToday[pId] || 0,
        totalAll: mapPetugasCountTotal[pId] || 0,
        tanggal: tglP instanceof Date ? Utilities.formatDate(tglP, timeZone, "yyyy-MM-dd") : tglP.toString()
      });
    }

    const attEnabledVal = getSettingValue(ss, 'ATTENDANCE_ENABLED', 'true');

    return {
      success: true,
      namaSekolah: namaSekolah,
      attendedIdsToday: attendedIdsToday,
      attendanceEnabled: attEnabledVal.toLowerCase() !== 'false',
      stats: {
        totalSiswa: totalSiswaAktif,
        sudahAbsen: sudahAbsenHariIni,
        belumAbsen: Math.max(0, totalSiswaAktif - sudahAbsenHariIni),
        seragamLengkap: seragamLengkap,
        seragamTidakLengkap: seragamTidakLengkap,
        hpDikumpulkan: hpDikumpulkan,
        hpTidakDikumpulkan: hpTidakDikumpulkan,
        perKelas: statsPerKelas
      },
      listRekap: listRekap.reverse(),
      listKelas: listKelasNama,
      listKelasDetail: listKelasDetail,
      listPetugas: listPetugas,
      listSiswa: listSiswaMaster
    };
  } catch (err) {
    return { success: false, message: 'Gagal memuat data admin: ' + err.toString() };
  }
}

function simpanDataSiswa(payload) {
  try {
    const ss = getDatabase();
    const sheet = ss.getSheetByName('SISWA');
    if (!sheet) return { success: false, message: 'Sheet "SISWA" tidak ditemukan!' };

    const waColumn = ensureKolomWaOrtu_(sheet);
    const data = sheet.getDataRange().getValues();
    const timeZone = Session.getScriptTimeZone();
    const todayStr = Utilities.formatDate(new Date(), timeZone, "yyyy-MM-dd");
    const cleanNama = payload.nama.trim();
    const cleanKelas = payload.kelas.trim();
    let cleanWaOrtu = String(payload.waOrtu || '').trim();
    if (cleanWaOrtu) cleanWaOrtu = normalizeFonnteTarget_(cleanWaOrtu);

    for (let i = 1; i < data.length; i++) {
      if (!data[i][0]) continue;
      const rowId = data[i][0].toString().trim();
      const rowNama = data[i][1].toString().trim();
      const rowKelas = data[i][2].toString().trim();
      if (rowId !== payload.id && rowNama.toLowerCase() === cleanNama.toLowerCase() && rowKelas.toLowerCase() === cleanKelas.toLowerCase()) {
        return { success: false, message: `Siswa "${cleanNama}" sudah terdaftar di kelas ${cleanKelas}!` };
      }
    }

    if (payload.id) {
      for (let i = 1; i < data.length; i++) {
        if (data[i][0].toString().trim() === payload.id.toString().trim()) {
          sheet.getRange(i + 1, 2).setValue(cleanNama);
          sheet.getRange(i + 1, 3).setValue(cleanKelas);
          sheet.getRange(i + 1, 4).setValue(payload.status);
          sheet.getRange(i + 1, waColumn).setValue(cleanWaOrtu);
          SpreadsheetApp.flush();
          return { success: true, message: 'Data siswa berhasil diperbarui!' };
        }
      }
      return { success: false, message: 'Data siswa tidak ditemukan.' };
    } else {
      let maxNum = 0;
      for (let i = 1; i < data.length; i++) {
        const val = data[i][0] ? data[i][0].toString().replace(/\D/g, '') : '';
        const num = parseInt(val, 10);
        if (!isNaN(num) && num > maxNum) maxNum = num;
      }
      const newId = 'SIS-' + String(maxNum + 1).padStart(4, '0');
      const newRow = [newId, cleanNama, cleanKelas, payload.status || 'Aktif', todayStr];
      while (newRow.length < waColumn - 1) newRow.push('');
      newRow.push(cleanWaOrtu);
      sheet.appendRow(newRow);
      SpreadsheetApp.flush();
      return { success: true, message: 'Siswa baru berhasil ditambahkan!' };
    }
  } catch (err) {
    return { success: false, message: 'Gagal menyimpan data siswa: ' + err.toString() };
  }
}

function hapusDataSiswa(idSiswa) {
  try {
    const ss = getDatabase();
    const sheetSiswa = ss.getSheetByName('SISWA');
    const sheetAbsen = ss.getSheetByName('ABSENSI');

    const dataSiswa = sheetSiswa.getDataRange().getValues();
    let namaSiswaTarget = "";
    let rowIndex = -1;

    for (let i = 1; i < dataSiswa.length; i++) {
      if (dataSiswa[i][0].toString().trim() === idSiswa.toString().trim()) {
        rowIndex = i + 1;
        namaSiswaTarget = dataSiswa[i][1].toString().trim();
        break;
      }
    }

    if (rowIndex === -1) return { success: false, message: 'Data siswa tidak ditemukan!' };

    const dataAbsen = sheetAbsen.getDataRange().getValues();
    let adaRiwayat = false;
    for (let j = 1; j < dataAbsen.length; j++) {
      const rowIdS = dataAbsen[j][3] ? dataAbsen[j][3].toString().trim() : '';
      const rowNamaS = dataAbsen[j][4] ? dataAbsen[j][4].toString().trim() : '';
      if (rowIdS === idSiswa.toString().trim() || rowNamaS.toLowerCase() === namaSiswaTarget.toLowerCase()) {
        adaRiwayat = true;
        break;
      }
    }

    if (adaRiwayat) {
      sheetSiswa.getRange(rowIndex, 4).setValue('Tidak Aktif');
      SpreadsheetApp.flush();
      return { 
        success: true, 
        message: `Siswa "${namaSiswaTarget}" memiliki riwayat absensi. Status diubah menjadi "Tidak Aktif" agar riwayat tetap aman.` 
      };
    } else {
      sheetSiswa.deleteRow(rowIndex);
      SpreadsheetApp.flush();
      return { success: true, message: `Siswa "${namaSiswaTarget}" berhasil dihapus permanen.` };
    }
  } catch (err) {
    return { success: false, message: 'Gagal menghapus siswa: ' + err.toString() };
  }
}

function simpanDataKelas(payload) {
  try {
    const ss = getDatabase();
    let sheet = ss.getSheetByName('KELAS');
    if (!sheet) {
      sheet = ss.insertSheet('KELAS');
      sheet.appendRow(['ID_Kelas', 'Tingkat', 'Jurusan', 'Nama_Kelas', 'Status']);
    }

    const data = sheet.getDataRange().getValues();
    const tingkat = payload.tingkat.trim().toUpperCase();
    const jurusan = payload.jurusan.trim().toUpperCase();
    const namaKelas = payload.namaKelas ? payload.namaKelas.trim() : `${tingkat} ${jurusan}`;

    if (payload.id) {
      for (let i = 1; i < data.length; i++) {
        if (data[i][0].toString().trim() === payload.id.toString().trim()) {
          sheet.getRange(i + 1, 2).setValue(tingkat);
          sheet.getRange(i + 1, 3).setValue(jurusan);
          sheet.getRange(i + 1, 4).setValue(namaKelas);
          sheet.getRange(i + 1, 5).setValue(payload.status);
          SpreadsheetApp.flush();
          return { success: true, message: 'Data kelas berhasil diperbarui!' };
        }
      }
      return { success: false, message: 'ID Kelas tidak ditemukan!' };
    } else {
      for (let i = 1; i < data.length; i++) {
        if (data[i][3] && data[i][3].toString().trim().toLowerCase() === namaKelas.toLowerCase()) {
          return { success: false, message: `Kelas "${namaKelas}" sudah ada!` };
        }
      }
      let maxNum = 0;
      for (let i = 1; i < data.length; i++) {
        const val = data[i][0] ? data[i][0].toString().replace(/\D/g, '') : '';
        const num = parseInt(val, 10);
        if (!isNaN(num) && num > maxNum) maxNum = num;
      }
      const newId = 'KLS-' + String(maxNum + 1).padStart(2, '0');
      sheet.appendRow([newId, tingkat, jurusan, namaKelas, payload.status || 'Aktif']);
      SpreadsheetApp.flush();
      return { success: true, message: `Kelas "${namaKelas}" berhasil ditambahkan!` };
    }
  } catch (err) {
    return { success: false, message: 'Gagal menyimpan data kelas: ' + err.toString() };
  }
}

function hapusDataKelas(idKelas) {
  try {
    const ss = getDatabase();
    const sheet = ss.getSheetByName('KELAS');
    if (!sheet) return { success: false, message: 'Sheet "KELAS" tidak ditemukan!' };
    const data = sheet.getDataRange().getValues();
    for (let i = 1; i < data.length; i++) {
      if (data[i][0].toString().trim() === idKelas.toString().trim()) {
        sheet.deleteRow(i + 1);
        SpreadsheetApp.flush();
        return { success: true, message: 'Data kelas berhasil dihapus!' };
      }
    }
    return { success: false, message: 'Kelas tidak ditemukan!' };
  } catch (err) {
    return { success: false, message: 'Gagal menghapus kelas: ' + err.toString() };
  }
}

function simpanPetugasOSIS(payload) {
  try {
    const ss = getDatabase();
    const sheet = ss.getSheetByName('PETUGAS_OSIS');
    if (!sheet) return { success: false, message: 'Sheet "PETUGAS_OSIS" tidak ditemukan!' };

    const data = sheet.getDataRange().getValues();
    const timeZone = Session.getScriptTimeZone();
    const todayStr = Utilities.formatDate(new Date(), timeZone, "yyyy-MM-dd");

    if (payload.id) {
      for (let i = 1; i < data.length; i++) {
        if (data[i][0].toString().trim() === payload.id.toString().trim()) {
          sheet.getRange(i + 1, 2).setValue(payload.nama.trim());
          if (payload.kode && payload.kode.trim() !== '') {
            sheet.getRange(i + 1, 3).setValue(payload.kode.trim());
          }
          sheet.getRange(i + 1, 4).setValue(payload.status);
          SpreadsheetApp.flush();
          return { success: true, message: 'Data petugas OSIS berhasil diperbarui!' };
        }
      }
      return { success: false, message: 'Petugas tidak ditemukan!' };
    } else {
      for (let i = 1; i < data.length; i++) {
        if (data[i][2] && verifySecret(payload.kode, data[i][2].toString())) {
          return { success: false, message: 'Kode verifikasi sudah digunakan oleh petugas lain!' };
        }
      }
      let maxNum = 0;
      for (let i = 1; i < data.length; i++) {
        const val = data[i][0] ? data[i][0].toString().replace(/\D/g, '') : '';
        const num = parseInt(val, 10);
        if (!isNaN(num) && num > maxNum) maxNum = num;
      }
      const newId = 'PTG-' + String(maxNum + 1).padStart(3, '0');
      sheet.appendRow([newId, payload.nama.trim(), payload.kode.trim(), payload.status || 'Aktif', todayStr]);
      SpreadsheetApp.flush();
      return { success: true, message: 'Petugas OSIS baru berhasil ditambahkan!' };
    }
  } catch (err) {
    return { success: false, message: 'Gagal menyimpan petugas OSIS: ' + err.toString() };
  }
}

function getAttendanceControlStatus(auth) {
  try {
    if (!auth || !['Developer', 'Pengawas'].includes(auth.role)) {
      return { success: false, message: 'Akses ditolak.' };
    }
    const ss = getDatabase();
    const val = getSettingValue(ss, 'ATTENDANCE_ENABLED', 'true');
    return { success: true, enabled: val.toLowerCase() !== 'false' };
  } catch (err) {
    return { success: false, message: 'Gagal memuat status absensi: ' + err.toString() };
  }
}

function setAttendanceEnabled(auth, enabled) {
  try {
    if (!auth || !['Developer', 'Pengawas'].includes(auth.role)) {
      return { success: false, message: 'Akses ditolak.' };
    }
    const ss = getDatabase();
    const sheet = ss.getSheetByName('PENGATURAN');
    if (!sheet) return { success: false, message: 'Sheet PENGATURAN tidak ditemukan.' };

    const data = sheet.getDataRange().getValues();
    let found = false;
    for (let i = 1; i < data.length; i++) {
      if (data[i][0] && data[i][0].toString().trim() === 'ATTENDANCE_ENABLED') {
        sheet.getRange(i + 1, 2).setValue(enabled ? 'true' : 'false');
        found = true;
        break;
      }
    }
    if (!found) {
      sheet.appendRow(['ATTENDANCE_ENABLED', enabled ? 'true' : 'false', 'Status aktif/tutup pengisian absensi']);
    }
    SpreadsheetApp.flush();
    return { 
      success: true, 
      enabled: enabled, 
      message: enabled ? 'Pengisian absensi berhasil DIBUKA.' : 'Pengisian absensi berhasil DITUTUP.' 
    };
  } catch (err) {
    return { success: false, message: 'Gagal mengubah pengaturan absensi: ' + err.toString() };
  }
}

function getUniformLabels() {
  try {
    const ss = getDatabase();
    const val = getSettingValue(ss, 'UNIFORM_LABELS_JSON', '');
    if (!val) {
      return { 
        success: true, 
        labels: { badgeSekolah:'Badge Sekolah', badgeOsis:'Badge OSIS', dasi:'Dasi', ikatPinggang:'Ikat Pinggang', kaosKaki:'Kaos Kaki', sepatu:'Sepatu', atributLainnya:'Atribut Lain', catatan:'Catatan Seragam' },
        customAttributes: []
      };
    }
    const parsed = JSON.parse(val);
    return { 
      success: true, 
      labels: parsed.labels || parsed, 
      customAttributes: parsed.customAttributes || [] 
    };
  } catch (err) {
    return { success: false, message: 'Gagal memuat atribut: ' + err.toString() };
  }
}

function saveUniformLabels(auth, labelsObj) {
  try {
    if (!auth || !['Developer', 'Pengawas'].includes(auth.role)) {
      return { success: false, message: 'Akses ditolak.' };
    }
    const ss = getDatabase();
    const sheet = ss.getSheetByName('PENGATURAN');
    if (!sheet) return { success: false, message: 'Sheet PENGATURAN tidak ditemukan.' };

    const jsonStr = JSON.stringify(labelsObj);
    const data = sheet.getDataRange().getValues();
    let found = false;
    for (let i = 1; i < data.length; i++) {
      if (data[i][0] && data[i][0].toString().trim() === 'UNIFORM_LABELS_JSON') {
        sheet.getRange(i + 1, 2).setValue(jsonStr);
        found = true;
        break;
      }
    }
    if (!found) {
      sheet.appendRow(['UNIFORM_LABELS_JSON', jsonStr, 'Konfigurasi nama atribut seragam kustom']);
    }
    SpreadsheetApp.flush();
    return { success: true, labels: labelsObj.labels || labelsObj, customAttributes: labelsObj.customAttributes || [] };
  } catch (err) {
    return { success: false, message: 'Gagal menyimpan atribut: ' + err.toString() };
  }
}

function getViolationPointsData(auth) {
  try {
    if (!auth || !['Developer', 'Pengawas', 'Anggota OSIS'].includes(auth.role)) {
      return { success: false, message: 'Akses ditolak.' };
    }
    const ss = getDatabase();
    const sheetAbsen = ss.getSheetByName('ABSENSI');
    if (!sheetAbsen) return { success: false, message: 'Sheet ABSENSI tidak ditemukan.' };

    const data = sheetAbsen.getDataRange().getValues();
    const mapSiswa = {};
    let totalPoinAll = 0;
    let totalPelanggaranAll = 0;

    for (let i = 1; i < data.length; i++) {
      if (!data[i][0]) continue;
      const idSiswa = data[i][3] ? data[i][3].toString().trim() : '-';
      const namaSiswa = data[i][4] ? data[i][4].toString().trim() : '';
      const kelasSiswa = data[i][5] ? data[i][5].toString().trim() : '';
      const tgl = data[i][1] instanceof Date ? Utilities.formatDate(data[i][1], Session.getScriptTimeZone(), 'yyyy-MM-dd') : String(data[i][1] || '');
      const jam = String(data[i][2] || '');

      const attrCols = [
        { name: 'Badge Sekolah', val: data[i][9] },
        { name: 'Badge OSIS', val: data[i][10] },
        { name: 'Dasi', val: data[i][11] },
        { name: 'Ikat Pinggang', val: data[i][12] },
        { name: 'Kaos Kaki', val: data[i][13] },
        { name: 'Sepatu', val: data[i][14] },
        { name: 'Atribut Lainnya', val: data[i][15] }
      ];

      let pointPelanggaranRow = 0;
      let detailList = [];

      attrCols.forEach(col => {
        if (col.val && col.val.toString().trim() === 'Tidak Lengkap') {
          pointPelanggaranRow += 1;
          totalPelanggaranAll += 1;
          detailList.push(col.name + ' Tidak Lengkap');
        }
      });

      if (pointPelanggaranRow > 0) {
        totalPoinAll += pointPelanggaranRow;
        const key = idSiswa !== '-' ? idSiswa : (namaSiswa + '|' + kelasSiswa);
        if (!mapSiswa[key]) {
          mapSiswa[key] = {
            id: idSiswa,
            nama: namaSiswa,
            kelas: kelasSiswa,
            totalPoin: 0,
            jumlahPelanggaran: 0,
            riwayat: []
          };
        }
        mapSiswa[key].totalPoin += pointPelanggaranRow;
        mapSiswa[key].jumlahPelanggaran += detailList.length;
        mapSiswa[key].riwayat.push({
          tanggal: tgl,
          jam: jam,
          detail: detailList.join(', '),
          poin: pointPelanggaranRow
        });
      }
    }

    const listResult = Object.values(mapSiswa).sort((a, b) => b.totalPoin - a.totalPoin);
    return {
      success: true,
      summary: {
        totalPoin: totalPoinAll,
        siswaTercatat: listResult.length,
        totalPelanggaran: totalPelanggaranAll
      },
      list: listResult
    };
  } catch (err) {
    return { success: false, message: 'Gagal memuat data poin: ' + err.toString() };
  }
}

function kirimBroadcastWaOrtu(payload) {
  try {
    const auth = payload && payload.auth;
    if (!auth || !['Developer', 'Pengawas'].includes(auth.role)) {
      return { success: false, message: 'Akses ditolak: Hanya Pengawas atau Developer yang dapat mengirim broadcast.' };
    }

    const token = PropertiesService.getScriptProperties().getProperty('FONNTE_TOKEN');
    if (!token) {
      return { success: false, message: 'Token FONNTE_TOKEN belum diatur di Script Properties Apps Script.' };
    }

    const targetMode = String(payload.targetMode || 'ALL').trim();
    const targetKelas = String(payload.targetKelas || '').trim();
    const selectedIds = Array.isArray(payload.selectedIds) ? payload.selectedIds.map(String) : [];
    const templatePesan = String(payload.pesan || '').trim();

    if (!templatePesan) {
      return { success: false, message: 'Isi pesan broadcast tidak boleh kosong!' };
    }

    const ss = getDatabase();
    const sheetSiswa = ss.getSheetByName('SISWA');
    const sheetAbsen = ss.getSheetByName('ABSENSI');
    if (!sheetSiswa) return { success: false, message: 'Sheet "SISWA" tidak ditemukan!' };

    const waColumn = ensureKolomWaOrtu_(sheetSiswa);
    const dataSiswa = sheetSiswa.getDataRange().getValues();
    const timeZone = Session.getScriptTimeZone();
    const todayStr = Utilities.formatDate(new Date(), timeZone, 'yyyy-MM-dd');

    const sudahAbsenIds = new Set();
    const seragamKurangIds = new Set();
    if (sheetAbsen && (targetMode === 'BELUM_ABSEN' || targetMode === 'SERAGAM_KURANG')) {
      const dataAbsen = sheetAbsen.getDataRange().getValues();
      for (let i = 1; i < dataAbsen.length; i++) {
        if (!dataAbsen[i][0]) continue;
        const rowTgl = dataAbsen[i][1] instanceof Date
          ? Utilities.formatDate(dataAbsen[i][1], timeZone, 'yyyy-MM-dd')
          : String(dataAbsen[i][1] || '').trim();
        if (rowTgl === todayStr) {
          const idS = String(dataAbsen[i][3] || '').trim();
          const stSeragam = String(dataAbsen[i][8] || '').trim();
          if (idS && idS !== '-') {
            sudahAbsenIds.add(idS);
            if (stSeragam === 'Tidak Lengkap') {
              seragamKurangIds.add(idS);
            }
          }
        }
      }
    }

    const daftarPenerima = [];
    for (let i = 1; i < dataSiswa.length; i++) {
      if (!dataSiswa[i][0]) continue;
      const idS = String(dataSiswa[i][0]).trim();
      const namaS = String(dataSiswa[i][1] || '').trim();
      const kelasS = String(dataSiswa[i][2] || '').trim();
      const statusS = String(dataSiswa[i][3] || 'Aktif').trim();
      const waOrtu = dataSiswa[i][waColumn - 1] ? String(dataSiswa[i][waColumn - 1]).trim() : '';

      if (statusS.toLowerCase() !== 'aktif') continue;
      if (!waOrtu) continue;

      let cocok = false;
      if (targetMode === 'ALL') {
        cocok = true;
      } else if (targetMode === 'KELAS') {
        cocok = kelasS.toLowerCase() === targetKelas.toLowerCase();
      } else if (targetMode === 'BELUM_ABSEN') {
        cocok = (!targetKelas || kelasS.toLowerCase() === targetKelas.toLowerCase()) && !sudahAbsenIds.has(idS);
      } else if (targetMode === 'SERAGAM_KURANG') {
        cocok = (!targetKelas || kelasS.toLowerCase() === targetKelas.toLowerCase()) && seragamKurangIds.has(idS);
      } else if (targetMode === 'MANUAL_SELECT') {
        cocok = selectedIds.includes(idS);
      }

      if (cocok) {
        daftarPenerima.push({
          id: idS,
          nama: namaS,
          kelas: kelasS,
          wa: waOrtu
        });
      }
    }

    if (daftarPenerima.length === 0) {
      return {
        success: false,
        message: 'Tidak ada siswa yang memenuhi kriteria filter atau nomor WhatsApp orang tua belum diisi.'
      };
    }

    let terkirim = 0;
    let gagal = 0;
    const detailGagal = [];

    for (let i = 0; i < daftarPenerima.length; i++) {
      const p = daftarPenerima[i];
      const pesanPersonal = templatePesan
        .replace(/\{nama\}/gi, p.nama)
        .replace(/\{kelas\}/gi, p.kelas);

      try {
        const resWa = sendFonnteMessage_(p.wa, pesanPersonal);
        if (resWa.success) {
          terkirim++;
        } else {
          gagal++;
          detailGagal.push(`${p.nama} (${resWa.reason || 'Gagal'})`);
        }
      } catch (e) {
        gagal++;
        detailGagal.push(`${p.nama} (${e.message})`);
      }

      if (i < daftarPenerima.length - 1) {
        Utilities.sleep(350);
      }
    }

    return {
      success: true,
      terkirim: terkirim,
      gagal: gagal,
      totalTarget: daftarPenerima.length,
      detailGagal: detailGagal,
      message: `Broadcast selesai: ${terkirim} berhasil masuk antrean, ${gagal} gagal dari total ${daftarPenerima.length} penerima.`
    };
  } catch (err) {
    return { success: false, message: 'Gagal menjalankan broadcast: ' + err.toString() };
  }
}

function getRealtimeAttendanceStatus() {
  try {
    const ss = getDatabase();
    const sheetAbsen = ss.getSheetByName('ABSENSI');
    if (!sheetAbsen) return { success: false, message: 'Sheet ABSENSI tidak ditemukan.' };
    const data = sheetAbsen.getDataRange().getValues();
    const tz = Session.getScriptTimeZone();
    const todayStr = Utilities.formatDate(new Date(), tz, 'yyyy-MM-dd');
    const attendedIdsToday = [];
    const recentToday = [];

    for (let i = 1; i < data.length; i++) {
      if (!data[i][0]) continue;
      const rowTgl = data[i][1] instanceof Date ? Utilities.formatDate(data[i][1], tz, 'yyyy-MM-dd') : String(data[i][1] || '').trim();
      if (rowTgl === todayStr) {
        const idS = String(data[i][3] || '').trim();
        if (idS && idS !== '-') attendedIdsToday.push(idS);
        recentToday.push({
          idAbsensi: String(data[i][0]).trim(),
          idSiswa: idS,
          nama: String(data[i][4] || '').trim(),
          kelas: String(data[i][5] || '').trim(),
          jam: data[i][2] instanceof Date ? Utilities.formatDate(data[i][2], tz, 'HH:mm:ss') : String(data[i][2] || ''),
          idPetugas: String(data[i][6] || '').trim(),
          namaPetugas: String(data[i][7] || '').trim(),
          metode: data[i][19] ? 'Manual' : 'QR / Manual'
        });
      }
    }
    return { success: true, attendedIdsToday: attendedIdsToday, recentToday: recentToday.reverse() };
  } catch (err) {
    return { success: false, message: err.toString() };
  }
}

function updateDataAbsensi(payload) {
  try {
    if (!payload || !payload.auth || !['Developer', 'Pengawas', 'Anggota OSIS'].includes(payload.auth.role)) {
      return { success: false, message: 'Akses ditolak.' };
    }
    const ss = getDatabase();
    const sheet = ss.getSheetByName('ABSENSI');
    if (!sheet) return { success: false, message: 'Sheet ABSENSI tidak ditemukan.' };
    const data = sheet.getDataRange().getValues();

    for (let i = 1; i < data.length; i++) {
      if (String(data[i][0] || '').trim() === String(payload.id || '').trim()) {
        const attrs = [
          payload.badgeSekolah, payload.badgeOsis, payload.dasi,
          payload.ikatPinggang, payload.kaosKaki, payload.sepatu, payload.atributLainnya
        ];
        const statusGlobal = attrs.every(v => v === 'Lengkap') ? 'Lengkap' : 'Tidak Lengkap';
        const row = i + 1;
        sheet.getRange(row, 9).setValue(statusGlobal);
        sheet.getRange(row, 10).setValue(payload.badgeSekolah);
        sheet.getRange(row, 11).setValue(payload.badgeOsis);
        sheet.getRange(row, 12).setValue(payload.dasi);
        sheet.getRange(row, 13).setValue(payload.ikatPinggang);
        sheet.getRange(row, 14).setValue(payload.kaosKaki);
        sheet.getRange(row, 15).setValue(payload.sepatu);
        sheet.getRange(row, 16).setValue(payload.atributLainnya);
        sheet.getRange(row, 17).setValue(payload.keterangan || '-');
        sheet.getRange(row, 18).setValue(payload.statusHp);
        sheet.getRange(row, 19).setValue(payload.keteranganHp || '-');
        SpreadsheetApp.flush();
        return { success: true, message: 'Pilihan absensi berhasil diperbarui!' };
      }
    }
    return { success: false, message: 'Data absensi tidak ditemukan.' };
  } catch (err) {
    return { success: false, message: 'Gagal update absensi: ' + err.toString() };
  }
}

function getAdminAccounts(auth) {
  try {
    if (!auth || auth.role !== 'Developer') return { success: false, message: 'Akses khusus Developer.' };
    const ss = getDatabase();
    const sheet = ss.getSheetByName('ADMIN');
    if (!sheet) return { success: false, message: 'Sheet ADMIN tidak ditemukan.' };
    const data = sheet.getDataRange().getValues();
    const listAdmin = [];
    for (let i = 1; i < data.length; i++) {
      if (!data[i][0]) continue;
      listAdmin.push({
        id: String(data[i][0]).trim(),
        username: String(data[i][1] || '').trim(),
        role: String(data[i][3] || 'Anggota OSIS').trim(),
        status: String(data[i][4] || 'Aktif').trim()
      });
    }
    return { success: true, listAdmin: listAdmin };
  } catch (err) {
    return { success: false, message: err.toString() };
  }
}

function simpanAkunAdmin(payload) {
  try {
    if (!payload || !payload.auth || payload.auth.role !== 'Developer') {
      return { success: false, message: 'Akses khusus Developer.' };
    }
    const ss = getDatabase();
    const sheet = ss.getSheetByName('ADMIN');
    if (!sheet) return { success: false, message: 'Sheet ADMIN tidak ditemukan.' };
    const data = sheet.getDataRange().getValues();
    const cleanUser = String(payload.username || '').trim();

    if (payload.id) {
      for (let i = 1; i < data.length; i++) {
        if (String(data[i][0]).trim() === String(payload.id).trim()) {
          sheet.getRange(i + 1, 2).setValue(cleanUser);
          if (payload.password) sheet.getRange(i + 1, 3).setValue(computeSha256(payload.password));
          sheet.getRange(i + 1, 4).setValue(payload.role);
          sheet.getRange(i + 1, 5).setValue(payload.status);
          SpreadsheetApp.flush();
          return { success: true, message: 'Akun admin berhasil diperbarui!' };
        }
      }
      return { success: false, message: 'ID Admin tidak ditemukan.' };
    } else {
      for (let i = 1; i < data.length; i++) {
        if (String(data[i][1] || '').trim().toLowerCase() === cleanUser.toLowerCase()) {
          return { success: false, message: 'Username sudah digunakan!' };
        }
      }
      let maxNum = 0;
      for (let i = 1; i < data.length; i++) {
        const num = parseInt(String(data[i][0] || '').replace(/\D/g, ''), 10);
        if (!isNaN(num) && num > maxNum) maxNum = num;
      }
      const newId = 'ADM-' + String(maxNum + 1).padStart(2, '0');
      sheet.appendRow([newId, cleanUser, computeSha256(payload.password), payload.role, payload.status || 'Aktif']);
      SpreadsheetApp.flush();
      return { success: true, message: 'Akun admin baru berhasil ditambahkan!' };
    }
  } catch (err) {
    return { success: false, message: err.toString() };
  }
}

function tambahAkunAdminPengawas(payload) {
  try {
    if (!payload || !payload.auth || payload.auth.role !== 'Pengawas') {
      return { success: false, message: 'Akses khusus Pengawas.' };
    }
    if (!['Pengawas', 'Anggota OSIS'].includes(payload.role)) {
      return { success: false, message: 'Pengawas hanya boleh membuat akun Pengawas atau Anggota OSIS.' };
    }
    const ss = getDatabase();
    const sheet = ss.getSheetByName('ADMIN');
    if (!sheet) return { success: false, message: 'Sheet ADMIN tidak ditemukan.' };
    const data = sheet.getDataRange().getValues();
    const cleanUser = String(payload.username || '').trim();

    for (let i = 1; i < data.length; i++) {
      if (String(data[i][1] || '').trim().toLowerCase() === cleanUser.toLowerCase()) {
        return { success: false, message: 'Username sudah terdaftar!' };
      }
    }
    let maxNum = 0;
    for (let i = 1; i < data.length; i++) {
      const num = parseInt(String(data[i][0] || '').replace(/\D/g, ''), 10);
      if (!isNaN(num) && num > maxNum) maxNum = num;
    }
    const newId = 'ADM-' + String(maxNum + 1).padStart(2, '0');
    sheet.appendRow([newId, cleanUser, computeSha256(payload.password), payload.role, payload.status || 'Aktif']);
    SpreadsheetApp.flush();
    return { success: true, message: `Akun ${payload.role} "${cleanUser}" berhasil dibuat!` };
  } catch (err) {
    return { success: false, message: err.toString() };
  }
}

function hapusAkunAdmin(idAdmin, auth) {
  try {
    if (!auth || auth.role !== 'Developer') return { success: false, message: 'Akses khusus Developer.' };
    const ss = getDatabase();
    const sheet = ss.getSheetByName('ADMIN');
    const data = sheet.getDataRange().getValues();
    for (let i = 1; i < data.length; i++) {
      if (String(data[i][0]).trim() === String(idAdmin).trim()) {
        sheet.deleteRow(i + 1);
        SpreadsheetApp.flush();
        return { success: true, message: 'Akun admin berhasil dihapus!' };
      }
    }
    return { success: false, message: 'Akun tidak ditemukan.' };
  } catch (err) {
    return { success: false, message: err.toString() };
  }
}