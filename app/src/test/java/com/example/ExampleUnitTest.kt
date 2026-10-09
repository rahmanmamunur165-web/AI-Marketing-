package com.example

import com.example.crawler.PublicEmailExtractor
import com.example.crawler.UrlSecurityValidator
import com.example.service.CsvExportService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testUrlSecurityValidator_blocksLocalhostAndMetadata() {
        val localhostResult = UrlSecurityValidator.validateUrl("http://localhost:8080/admin")
        assertTrue(localhostResult is UrlSecurityValidator.ValidationResult.Invalid)

        val metadataResult = UrlSecurityValidator.validateUrl("http://169.254.169.254/latest/meta-data/")
        assertTrue(metadataResult is UrlSecurityValidator.ValidationResult.Invalid)

        val loopbackResult = UrlSecurityValidator.validateUrl("https://127.0.0.1/sensitive")
        assertTrue(loopbackResult is UrlSecurityValidator.ValidationResult.Invalid)

        val invalidPortResult = UrlSecurityValidator.validateUrl("https://example.com:22/")
        assertTrue(invalidPortResult is UrlSecurityValidator.ValidationResult.Invalid)
    }

    @Test
    fun testPublicEmailExtractor_extractsMailtoAndTextEmails() {
        val sampleHtml = """
            <html>
                <head><title>Acme Corporation - Leading Innovations</title></head>
                <body>
                    <h1>Welcome</h1>
                    <p>For sales inquiries, please write to <a href="mailto:sales@acme.com">sales@acme.com</a>.</p>
                    <p>Support is available at support@acme.com or reach out to info@acme.com.</p>
                    <p>Do not contact test@example.com or false-alarm@2x.png.</p>
                </body>
            </html>
        """.trimIndent()

        val extracted = PublicEmailExtractor.extractEmailsFromHtml(
            htmlContent = sampleHtml,
            pageUrl = "https://acme.com/contact",
            targetDomain = "acme.com"
        )

        val emails = extracted.map { it.email }
        assertTrue(emails.contains("sales@acme.com"))
        assertTrue(emails.contains("support@acme.com"))
        assertTrue(emails.contains("info@acme.com"))
        assertFalse("Should filter out image filename assets", emails.contains("false-alarm@2x.png"))
        assertFalse("Should filter out ignored example.com domains", emails.contains("test@example.com"))

        val companyName = PublicEmailExtractor.detectCompanyName(sampleHtml, "acme.com")
        assertEquals("Acme Corporation", companyName)
    }

    @Test
    fun testCsvExport_preventsFormulaInjection() {
        // Leading '=' should be escaped with leading apostrophe "'"
        val formula = "=SUM(1+1)"
        val sanitized = CsvExportService.sanitizeForSpreadsheet(formula)
        assertEquals("\"'=SUM(1+1)\"", sanitized)

        val normal = "info@business.com"
        val sanitizedNormal = CsvExportService.sanitizeForSpreadsheet(normal)
        assertEquals("\"info@business.com\"", sanitizedNormal)

        val leadingPlus = "+123456789"
        val sanitizedPlus = CsvExportService.sanitizeForSpreadsheet(leadingPlus)
        assertEquals("\"'+123456789\"", sanitizedPlus)
    }
}
