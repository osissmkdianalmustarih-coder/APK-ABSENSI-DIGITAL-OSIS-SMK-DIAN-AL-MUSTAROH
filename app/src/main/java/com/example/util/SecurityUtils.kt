package com.example.util

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SecurityUtils {

    fun computeSha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun verifySecret(inputPlain: String, storedValue: String): Boolean {
        val cleanInput = inputPlain.trim()
        val cleanStored = storedValue.trim()
        if (cleanInput.isEmpty() || cleanStored.isEmpty()) return false
        return cleanInput == cleanStored ||
            computeSha256(cleanInput).equals(cleanStored, ignoreCase = true)
    }

    fun normalizeFonnteTarget(phone: String): String {
        var digits = phone.filter { it.isDigit() }
        if (digits.isEmpty()) throw IllegalArgumentException("Nomor WhatsApp orang tua belum diisi.")
        if (digits.startsWith("0")) {
            digits = "62" + digits.substring(1)
        } else if (digits.startsWith("8")) {
            digits = "62$digits"
        }
        if (!digits.startsWith("62") || digits.length !in 10..15) {
            throw IllegalArgumentException("Format nomor WhatsApp tidak valid. Gunakan nomor Indonesia, misalnya 081234567890.")
        }
        return digits
    }

    fun todayDateStr(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun currentTimeStr(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
}
