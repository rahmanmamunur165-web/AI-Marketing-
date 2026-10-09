package com.example.crawler

import java.net.URI
import java.util.Locale
import java.util.regex.Pattern

data class ExtractedEmail(
    val email: String,
    val domain: String,
    val sourceUrl: String,
    val companyName: String?,
    val validationStatus: String // "ROLE_ACCOUNT", "PUBLIC_BUSINESS", "SYNTAX_VALID"
)

object PublicEmailExtractor {

    // RFC 5322 compliant practical regex for web email scanning
    private val EMAIL_REGEX = Pattern.compile(
        "[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,10}",
        Pattern.CASE_INSENSITIVE
    )

    private val MAILTO_REGEX = Pattern.compile(
        "href=[\"']mailto:([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})",
        Pattern.CASE_INSENSITIVE
    )

    // Role-based business prefixes
    private val ROLE_PREFIXES = setOf(
        "info", "contact", "sales", "support", "hello", "team", "office",
        "press", "media", "help", "billing", "careers", "jobs", "inquiries",
        "partnerships", "privacy", "admin", "service", "general"
    )

    // File extensions or asset artifacts to filter out
    private val IGNORED_EXTENSIONS = setOf(
        "png", "jpg", "jpeg", "gif", "svg", "webp", "ico", "bmp", "tiff",
        "js", "css", "woff", "woff2", "ttf", "eot", "mp4", "mp3", "pdf", "zip"
    )

    // Mock or library false positives to ignore
    private val IGNORED_DOMAINS = setOf(
        "example.com", "sample.com", "domain.com", "yourdomain.com",
        "w3.org", "schema.org", "sentry.io", "googleapis.com", "github.com",
        "cloudflare.com", "google.com", "gravatar.com"
    )

    fun extractEmailsFromHtml(
        htmlContent: String,
        pageUrl: String,
        targetDomain: String
    ): List<ExtractedEmail> {
        val results = mutableMapOf<String, ExtractedEmail>()
        val companyName = detectCompanyName(htmlContent, targetDomain)

        // 1. Extract from mailto: links (highest confidence)
        val mailtoMatcher = MAILTO_REGEX.matcher(htmlContent)
        while (mailtoMatcher.find()) {
            val rawEmail = mailtoMatcher.group(1)?.trim()?.lowercase(Locale.ROOT)
            if (rawEmail != null && isValidBusinessCandidate(rawEmail)) {
                val emailDomain = extractDomainFromEmail(rawEmail)
                val status = classifyEmail(rawEmail)
                results[rawEmail] = ExtractedEmail(
                    email = rawEmail,
                    domain = emailDomain,
                    sourceUrl = pageUrl,
                    companyName = companyName,
                    validationStatus = status
                )
            }
        }

        // 2. Extract from visible text content
        val textMatcher = EMAIL_REGEX.matcher(htmlContent)
        while (textMatcher.find()) {
            val rawEmail = textMatcher.group()?.trim()?.lowercase(Locale.ROOT)
            if (rawEmail != null && isValidBusinessCandidate(rawEmail)) {
                if (!results.containsKey(rawEmail)) {
                    val emailDomain = extractDomainFromEmail(rawEmail)
                    val status = classifyEmail(rawEmail)
                    results[rawEmail] = ExtractedEmail(
                        email = rawEmail,
                        domain = emailDomain,
                        sourceUrl = pageUrl,
                        companyName = companyName,
                        validationStatus = status
                    )
                }
            }
        }

        return results.values.toList()
    }

    private fun classifyEmail(email: String): String {
        val prefix = email.substringBefore("@")
        return if (ROLE_PREFIXES.contains(prefix)) {
            "ROLE_ACCOUNT"
        } else {
            "PUBLIC_BUSINESS"
        }
    }

    private fun isValidBusinessCandidate(email: String): Boolean {
        if (!email.contains("@")) return false
        val parts = email.split("@")
        if (parts.size != 2) return false
        val local = parts[0]
        val domain = parts[1]

        if (local.length < 2 || domain.length < 4) return false

        // Check if domain ends with an asset extension (e.g. filename@2x.png)
        val ext = domain.substringAfterLast(".", "")
        if (IGNORED_EXTENSIONS.contains(ext) || IGNORED_EXTENSIONS.contains(local.substringAfterLast(".", ""))) {
            return false
        }

        // Check ignored template/framework domains
        if (IGNORED_DOMAINS.contains(domain)) {
            return false
        }

        return true
    }

    private fun extractDomainFromEmail(email: String): String {
        return email.substringAfter("@", "").lowercase(Locale.ROOT)
    }

    fun detectCompanyName(html: String, fallbackDomain: String): String {
        // Try og:site_name
        val ogMatcher = Pattern.compile(
            "<meta[^>]+property=[\"']og:site_name[\"'][^>]+content=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE
        ).matcher(html)
        if (ogMatcher.find()) {
            val found = ogMatcher.group(1)?.trim()
            if (!found.isNullOrBlank()) return found
        }

        // Try <title> tag
        val titleMatcher = Pattern.compile("<title>([^<]+)</title>", Pattern.CASE_INSENSITIVE).matcher(html)
        if (titleMatcher.find()) {
            val rawTitle = titleMatcher.group(1)?.trim() ?: ""
            val cleanTitle = rawTitle
                .substringBefore(" - ")
                .substringBefore(" | ")
                .substringBefore(" • ")
                .trim()
            if (cleanTitle.isNotBlank() && cleanTitle.length < 50) {
                return cleanTitle
            }
        }

        // Fallback: capitalize domain
        return fallbackDomain
            .removePrefix("www.")
            .substringBefore(".")
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }

    fun extractLinks(html: String, baseUrl: String, rootDomain: String): List<String> {
        val links = mutableSetOf<String>()
        val linkMatcher = Pattern.compile(
            "href=[\"'](https?://[^\"'#\\s]+|/[^\"'#\\s]+)[\"']",
            Pattern.CASE_INSENSITIVE
        ).matcher(html)

        val cleanRoot = rootDomain.removePrefix("www.").lowercase(Locale.ROOT)

        while (linkMatcher.find()) {
            val rawHref = linkMatcher.group(1) ?: continue
            val absoluteUrl = try {
                URI(baseUrl).resolve(rawHref).toString()
            } catch (e: Exception) {
                continue
            }

            // Exclude static media links
            val lower = absoluteUrl.lowercase(Locale.ROOT)
            val extension = lower.substringAfterLast(".", "")
            if (IGNORED_EXTENSIONS.contains(extension)) {
                continue
            }

            // Prioritize contact and about links within the same root domain
            if (lower.contains(cleanRoot)) {
                links.add(absoluteUrl)
            }
        }

        // Prioritize links with keywords: contact, about, team, company, press
        return links.sortedByDescending { url ->
            val u = url.lowercase(Locale.ROOT)
            when {
                u.contains("contact") -> 3
                u.contains("about") -> 2
                u.contains("team") || u.contains("company") -> 1
                else -> 0
            }
        }
    }
}
