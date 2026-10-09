package com.example.service

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.local.entities.ContactEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExportService {

    /**
     * Sanitizes values to prevent CSV spreadsheet formula injection.
     * Dangerous leading characters: =, +, -, @, tab (\t), return (\r).
     */
    fun sanitizeForSpreadsheet(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        val trimmed = value.trim()
        val firstChar = trimmed.firstOrNull()
        val dangerousChars = setOf('=', '+', '-', '@', '\t', '\r', '%')

        val safeValue = if (firstChar != null && dangerousChars.contains(firstChar)) {
            "'$trimmed" // Prefix with apostrophe to force plain text in spreadsheets
        } else {
            trimmed
        }

        // Escape double quotes as per RFC 4180
        val escapedQuotes = safeValue.replace("\"", "\"\"")
        return "\"$escapedQuotes\""
    }

    fun generateCsvContent(contacts: List<ContactEntity>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        val sb = StringBuilder()

        // Write UTF-8 BOM for Microsoft Excel / Sheets compatibility
        sb.append('\uFEFF')

        // Header
        sb.append("email,domain,company_name,source_url,discovered_at,validation_status\n")

        for (contact in contacts) {
            val email = sanitizeForSpreadsheet(contact.email)
            val domain = sanitizeForSpreadsheet(contact.domain)
            val company = sanitizeForSpreadsheet(contact.companyName ?: "")
            val sourceUrl = sanitizeForSpreadsheet(contact.sourceUrl)
            val discoveredAt = sanitizeForSpreadsheet(dateFormat.format(Date(contact.discoveredAt)))
            val status = sanitizeForSpreadsheet(contact.validationStatus)

            sb.append("$email,$domain,$company,$sourceUrl,$discoveredAt,$status\n")
        }

        return sb.toString()
    }

    fun exportAndShareCsv(
        context: Context,
        contacts: List<ContactEntity>,
        fileNamePrefix: String = "contactflow_contacts"
    ): File? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "${fileNamePrefix}_$timeStamp.csv"

            val exportDir = File(context.cacheDir, "exports")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            val file = File(exportDir, fileName)
            val content = generateCsvContent(contacts)

            FileOutputStream(file).use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            }

            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                putExtra(Intent.EXTRA_SUBJECT, "ContactFlow Export: ${contacts.size} Contacts")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Exported ${contacts.size} public business contacts from ContactFlow."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Share or Save CSV")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)

            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
