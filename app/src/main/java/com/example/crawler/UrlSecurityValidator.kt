package com.example.crawler

import java.net.InetAddress
import java.net.URI
import java.net.URL

object UrlSecurityValidator {

    sealed class ValidationResult {
        data class Valid(val normalizedUrl: String, val host: String) : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }

    private val BLOCKED_HOSTNAMES = setOf(
        "localhost",
        "127.0.0.1",
        "::1",
        "0.0.0.0",
        "metadata.google.internal",
        "169.254.169.254", // Cloud Metadata IP
        "instance-data",
        "metadata"
    )

    fun validateUrl(inputUrl: String): ValidationResult {
        val trimmed = inputUrl.trim()
        if (trimmed.isEmpty()) {
            return ValidationResult.Invalid("URL cannot be empty")
        }

        val parsedUrl = try {
            val withScheme = if (!trimmed.startsWith("http://", ignoreCase = true) &&
                !trimmed.startsWith("https://", ignoreCase = true)
            ) {
                "https://$trimmed"
            } else {
                trimmed
            }
            URL(withScheme)
        } catch (e: Exception) {
            return ValidationResult.Invalid("Malformed URL format: ${e.message}")
        }

        // Scheme verification: HTTP and HTTPS only
        val protocol = parsedUrl.protocol.lowercase()
        if (protocol != "http" && protocol != "https") {
            return ValidationResult.Invalid("Unsupported protocol '$protocol'. Only HTTP and HTTPS are permitted.")
        }

        // Port check: safe web ports only (80, 443, or unspecified/-1)
        val port = parsedUrl.port
        if (port != -1 && port != 80 && port != 443) {
            return ValidationResult.Invalid("Forbidden port: $port. Only standard web ports (80, 443) are allowed.")
        }

        val host = parsedUrl.host.lowercase()
        if (host.isEmpty()) {
            return ValidationResult.Invalid("URL is missing a valid hostname.")
        }

        // Check blocked hostname strings & cloud metadata
        if (BLOCKED_HOSTNAMES.contains(host) ||
            host.endsWith(".local") ||
            host.endsWith(".internal") ||
            host.endsWith(".localhost") ||
            host.contains("metadata.google")
        ) {
            return ValidationResult.Invalid("Access to private, localhost, or cloud metadata host '$host' is forbidden.")
        }

        // DNS resolution check to prevent SSRF against internal/private IP ranges
        val isSafeIp = isPublicIpAddress(host)
        if (!isSafeIp) {
            return ValidationResult.Invalid("Hostname '$host' resolves to a reserved, loopback, or private internal IP.")
        }

        return ValidationResult.Valid(
            normalizedUrl = parsedUrl.toExternalForm(),
            host = host
        )
    }

    private fun isPublicIpAddress(host: String): Boolean {
        return try {
            val addresses = InetAddress.getAllByName(host)
            for (addr in addresses) {
                if (addr.isLoopbackAddress ||
                    addr.isSiteLocalAddress ||
                    addr.isLinkLocalAddress ||
                    addr.isMulticastAddress ||
                    addr.isAnyLocalAddress
                ) {
                    return false
                }

                val ipBytes = addr.address
                if (ipBytes.size == 4) { // IPv4
                    val b0 = ipBytes[0].toInt() and 0xFF
                    val b1 = ipBytes[1].toInt() and 0xFF

                    // 10.0.0.0/8
                    if (b0 == 10) return false
                    // 172.16.0.0/12
                    if (b0 == 172 && (b1 in 16..31)) return false
                    // 192.168.0.0/16
                    if (b0 == 192 && b1 == 168) return false
                    // 127.0.0.0/8
                    if (b0 == 127) return false
                    // 169.254.0.0/16 (Link Local / Cloud Metadata)
                    if (b0 == 169 && b1 == 254) return false
                    // 100.64.0.0/10 (Carrier-grade NAT)
                    if (b0 == 100 && (b1 in 64..127)) return false
                    // 0.0.0.0/8
                    if (b0 == 0) return false
                }
            }
            true
        } catch (e: Exception) {
            // If DNS lookup fails or is blocked during validation, allow proceeding if it's a valid domain format
            // but reject if it looks like a private IP string
            !host.matches(Regex("^(10\\.|192\\.168\\.|172\\.(1[6-9]|2[0-9]|3[01])\\.|127\\.|169\\.254\\.).*"))
        }
    }
}
