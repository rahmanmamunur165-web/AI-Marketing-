package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "scans")
data class ScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val submittedUrl: String,
    val normalizedDomain: String,
    val status: String = "PENDING", // PENDING, RUNNING, COMPLETED, CANCELLED, FAILED
    val pagesVisited: Int = 0,
    val emailsFound: Int = 0,
    val errorCount: Int = 0,
    val maxPages: Int = 25,
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val completedAt: Long? = null
)

@Entity(
    tableName = "scanned_pages",
    indices = [Index(value = ["scanId"]), Index(value = ["canonicalUrl"])]
)
data class ScannedPageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scanId: Long,
    val canonicalUrl: String,
    val httpStatus: Int = 200,
    val scannedAt: Long = System.currentTimeMillis(),
    val errorCode: String? = null
)

@Entity(
    tableName = "contacts",
    indices = [Index(value = ["email"], unique = true), Index(value = ["domain"])]
)
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val email: String,
    val domain: String,
    val companyName: String? = null,
    val sourceUrl: String,
    val discoveredAt: Long = System.currentTimeMillis(),
    val validationStatus: String = "PUBLIC_BUSINESS", // PUBLIC_BUSINESS, ROLE_ACCOUNT, SYNTAX_VALID
    val isSuppressed: Boolean = false,
    val isSelected: Boolean = false
)

@Entity(
    tableName = "suppressions",
    indices = [Index(value = ["normalizedEmail"], unique = true)]
)
data class SuppressionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val normalizedEmail: String,
    val reason: String = "MANUAL_OPT_OUT", // UNSUBSCRIBE, MANUAL_OPT_OUT, BOUNCE_DEFENSE, COMPLIANCE
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "campaigns")
data class CampaignEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val subject: String,
    val bodyText: String,
    val bodyHtml: String? = null,
    val status: String = "DRAFT", // DRAFT, APPROVED, SENDING, COMPLETED, CANCELLED
    val approvedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val totalRecipients: Int = 0,
    val sentCount: Int = 0,
    val failedCount: Int = 0,
    val suppressedCount: Int = 0
)

@Entity(
    tableName = "campaign_recipients",
    indices = [Index(value = ["campaignId"]), Index(value = ["contactId"])]
)
data class CampaignRecipientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val campaignId: Long,
    val contactId: Long,
    val recipientEmailSnapshot: String,
    val status: String = "QUEUED", // QUEUED, SENT_SUBMITTED, FAILED, SUPPRESSED
    val providerMessageId: String? = null,
    val errorMessage: String? = null,
    val attemptedAt: Long? = null,
    val sentAt: Long? = null
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val action: String,
    val resourceType: String,
    val resourceId: String? = null,
    val sanitizedMetadata: String = "{}",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "google_connections")
data class GoogleConnectionEntity(
    @PrimaryKey val id: Int = 1,
    val googleAccountEmail: String = "",
    val grantedScopes: String = "https://www.googleapis.com/auth/gmail.send, profile, email",
    val isConnected: Boolean = false,
    val connectedAt: Long? = null
)
