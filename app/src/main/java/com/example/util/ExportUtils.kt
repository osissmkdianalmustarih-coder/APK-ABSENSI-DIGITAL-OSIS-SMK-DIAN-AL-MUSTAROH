package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.local.AbsensiEntity
import com.example.data.local.SiswaEntity
import java.io.File
import java.io.FileOutputStream

object ExportUtils {

    fun exportAttendanceCsvAndShare(context: Context, records: List<AbsensiEntity>, dateTag: String) {
        val sb = StringBuilder("\uFEFFNo,Tanggal,Jam,ID Siswa,Nama Siswa,Kelas,Petugas,Metode,Seragam,HP,Keterangan\n")
        records.forEachIndexed { idx, r ->
            fun esc(v: String) = "\"${v.replace("\"", "\"\"")}\""
            sb.append(
                listOf(
                    esc((idx + 1).toString()),
                    esc(r.tanggal),
                    esc(r.jamDatang),
                    esc(r.idSiswa),
                    esc(r.namaSiswa),
                    esc(r.kelas),
                    esc(r.namaPetugas),
                    esc(r.metode),
                    esc(r.statusSeragam),
                    esc(r.statusHp),
                    esc(r.keterangan)
                ).joinToString(",")
            ).append("\n")
        }
        val file = File(context.cacheDir, "Laporan_Absensi_$dateTag.csv")
        file.writeText(sb.toString(), Charsets.UTF_8)
        shareFile(context, file, "text/csv", "Bagikan Laporan CSV")
    }

    fun exportAttendancePdfAndShare(
        context: Context,
        schoolName: String,
        records: List<AbsensiEntity>,
        dateTag: String
    ) {
        val pdf = PdfDocument()
        val pageWidth = 842 // A4 Landscape in points
        val pageHeight = 595
        val titlePaint = Paint().apply {
            color = Color.parseColor("#0B4DA2")
            textSize = 16f
            isFakeBoldText = true
        }
        val subtitlePaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 11f
        }
        val headerBgPaint = Paint().apply { color = Color.parseColor("#0B4DA2") }
        val headerTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 9.5f
            isFakeBoldText = true
        }
        val rowTextPaint = Paint().apply {
            color = Color.BLACK
            textSize = 9f
        }
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 0.8f
        }

        val colsX = floatArrayOf(28f, 56f, 118f, 174f, 240f, 388f, 456f, 540f, 646f, 742f)
        val headers = listOf("No", "Tanggal", "Jam", "ID", "Nama Siswa", "Kelas", "Seragam", "Status HP", "Petugas", "Metode")

        var pageNumber = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas

        fun drawPageHeader(c: Canvas) {
            c.drawText("LAPORAN REKAPITULASI ABSENSI & KEDISIPLINAN SISWA", 28f, 36f, titlePaint)
            c.drawText("OSIS $schoolName • Tanggal Cetak: $dateTag • Total: ${records.size} data", 28f, 52f, subtitlePaint)
            c.drawRect(24f, 64f, pageWidth - 24f, 84f, headerBgPaint)
            headers.forEachIndexed { idx, label ->
                c.drawText(label, colsX[idx], 78f, headerTextPaint)
            }
        }

        drawPageHeader(canvas)
        var y = 100f

        records.forEachIndexed { idx, r ->
            if (y > pageHeight - 36f) {
                pdf.finishPage(page)
                pageNumber++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                drawPageHeader(canvas)
                y = 100f
            }
            val rowVals = listOf(
                (idx + 1).toString(),
                r.tanggal,
                r.jamDatang,
                r.idSiswa,
                r.namaSiswa.take(24),
                r.kelas.take(10),
                r.statusSeragam.take(14),
                r.statusHp.take(18),
                r.namaPetugas.take(15),
                r.metode.take(10)
            )
            rowVals.forEachIndexed { colIdx, text ->
                canvas.drawText(text, colsX[colIdx], y, rowTextPaint)
            }
            canvas.drawLine(24f, y + 6f, pageWidth - 24f, y + 6f, linePaint)
            y += 20f
        }

        pdf.finishPage(page)
        val file = File(context.cacheDir, "Laporan_Absensi_$dateTag.pdf")
        FileOutputStream(file).use { out -> pdf.writeTo(out) }
        pdf.close()
        shareFile(context, file, "application/pdf", "Bagikan Laporan PDF")
    }

    fun exportQrCardsPdfAndShare(
        context: Context,
        schoolName: String,
        students: List<SiswaEntity>,
        titleTag: String
    ) {
        val pdf = PdfDocument()
        val pageWidth = 595 // A4 Portrait
        val pageHeight = 842
        val titlePaint = Paint().apply {
            color = Color.parseColor("#0B4DA2")
            textSize = 14f
            isFakeBoldText = true
        }
        val cardBorderPaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }
        val schoolPaint = Paint().apply {
            color = Color.parseColor("#0B4DA2")
            textSize = 8f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val namePaint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val classPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 8.5f
            textAlign = Paint.Align.CENTER
        }

        val cols = 3
        val rowsPerPage = 4
        val cardsPerPage = cols * rowsPerPage
        val cardW = 165f
        val cardH = 175f
        val startX = 34f
        val startY = 58f
        val gapX = 16f
        val gapY = 14f

        val chunks = students.chunked(cardsPerPage)
        chunks.forEachIndexed { pageIdx, pageStudents ->
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIdx + 1).create())
            val canvas = page.canvas
            canvas.drawText("KARTU QR ABSENSI - OSIS $schoolName", 34f, 36f, titlePaint)

            pageStudents.forEachIndexed { i, student ->
                val c = i % cols
                val r = i / cols
                val left = startX + c * (cardW + gapX)
                val top = startY + r * (cardH + gapY)
                canvas.drawRoundRect(left, top, left + cardW, top + cardH, 10f, 10f, cardBorderPaint)
                val centerX = left + cardW / 2f
                canvas.drawText("OSIS $schoolName".take(28), centerX, top + 16f, schoolPaint)
                val qrBmp = QrUtils.generateQrBitmap(student.qrToken, 240)
                if (qrBmp != null) {
                    val qrSize = 104f
                    val qrLeft = centerX - qrSize / 2f
                    val qrTop = top + 24f
                    canvas.drawBitmap(
                        qrBmp,
                        null,
                        android.graphics.RectF(qrLeft, qrTop, qrLeft + qrSize, qrTop + qrSize),
                        null
                    )
                }
                canvas.drawText(student.nama.take(22), centerX, top + 146f, namePaint)
                canvas.drawText("${student.kelas} • ${student.id}", centerX, top + 162f, classPaint)
            }
            pdf.finishPage(page)
        }

        val safeTag = titleTag.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(context.cacheDir, "Kartu_QR_$safeTag.pdf")
        FileOutputStream(file).use { out -> pdf.writeTo(out) }
        pdf.close()
        shareFile(context, file, "application/pdf", "Bagikan Kartu QR PDF")
    }

    private fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }
}
