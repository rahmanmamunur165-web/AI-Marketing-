package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.crawler.CrawlerEngine
import com.example.crawler.CrawlerProgress
import com.example.data.local.ContactFlowDatabase
import com.example.data.local.entities.AuditLogEntity
import com.example.data.local.entities.CampaignEntity
import com.example.data.local.entities.CampaignRecipientEntity
import com.example.data.local.entities.ContactEntity
import com.example.data.local.entities.GoogleConnectionEntity
import com.example.data.local.entities.ScanEntity
import com.example.data.local.entities.SuppressionEntity
import com.example.service.CsvExportService
import com.example.service.GmailOutreachService
import com.example.service.SendProgress
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class NavigationItem(val title: String, val icon: String) {
    DASHBOARD("Dashboard", "dashboard"),
    SCANNER("Scanner", "radar"),
    CONTACTS("Contacts", "contacts"),
    CAMPAIGNS("Campaigns", "send"),
    SETTINGS("Settings", "settings")
}

class ContactFlowViewModel(application: Application) : AndroidViewModel(application) {

    private val db = ContactFlowDatabase.getDatabase(application)
    private val scanDao = db.scanDao()
    private val contactDao = db.contactDao()
    private val suppressionDao = db.suppressionDao()
    private val campaignDao = db.campaignDao()
    private val auditDao = db.auditLogDao()
    private val googleDao = db.googleConnectionDao()

    val crawlerEngine = CrawlerEngine(scanDao, contactDao, suppressionDao, viewModelScope)
    val outreachService = GmailOutreachService(campaignDao, googleDao, suppressionDao, auditDao, viewModelScope)

    // Current Screen
    private val _currentScreen = MutableStateFlow(NavigationItem.DASHBOARD)
    val currentScreen: StateFlow<NavigationItem> = _currentScreen.asStateFlow()

    // Transient UI message / toast
    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    // Filters for Contacts
    val searchQuery = MutableStateFlow("")
    val filterStatus = MutableStateFlow("ALL") // ALL, PUBLIC_BUSINESS, ROLE_ACCOUNT, SUPPRESSED
    val filterDomain = MutableStateFlow("")

    // Raw flows
    val scans: StateFlow<List<ScanEntity>> = scanDao.getAllScans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allContactsRaw = contactDao.getAllContacts()

    // Filtered contacts
    val filteredContacts: StateFlow<List<ContactEntity>> = combine(
        allContactsRaw,
        searchQuery,
        filterStatus,
        filterDomain
    ) { contacts, query, status, domain ->
        contacts.filter { contact ->
            val matchesQuery = if (query.isBlank()) true else {
                contact.email.contains(query, ignoreCase = true) ||
                    contact.domain.contains(query, ignoreCase = true) ||
                    (contact.companyName?.contains(query, ignoreCase = true) == true)
            }
            val matchesStatus = when (status) {
                "ALL" -> true
                "SUPPRESSED" -> contact.isSuppressed
                else -> !contact.isSuppressed && contact.validationStatus == status
            }
            val matchesDomain = if (domain.isBlank()) true else {
                contact.domain.equals(domain, ignoreCase = true)
            }
            matchesQuery && matchesStatus && matchesDomain
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val suppressions: StateFlow<List<SuppressionEntity>> = suppressionDao.getAllSuppressions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val campaigns: StateFlow<List<CampaignEntity>> = campaignDao.getAllCampaigns()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentAuditLogs: StateFlow<List<AuditLogEntity>> = auditDao.getRecentLogs(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val googleConnection: StateFlow<GoogleConnectionEntity?> = googleDao.getConnection()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val crawlerProgress: StateFlow<CrawlerProgress> = crawlerEngine.progress
    val sendProgress: StateFlow<SendProgress> = outreachService.sendProgress

    init {
        // Seed initial example data if database is fresh
        seedDemoDataIfEmpty()
    }

    fun navigateTo(item: NavigationItem) {
        _currentScreen.value = item
    }

    fun showMessage(msg: String) {
        viewModelScope.launch {
            _userMessage.emit(msg)
        }
    }

    // --- Scanner actions ---
    fun startNewScan(url: String, maxPages: Int) {
        crawlerEngine.startScan(
            submittedUrl = url,
            maxPages = maxPages,
            onComplete = { scanId ->
                viewModelScope.launch {
                    auditDao.insertLog(
                        AuditLogEntity(
                            action = "SCAN_COMPLETED",
                            resourceType = "SCAN",
                            resourceId = scanId.toString(),
                            sanitizedMetadata = "{\"url\":\"$url\",\"pages\":$maxPages}"
                        )
                    )
                    showMessage("Scan completed for $url")
                }
            }
        )
        viewModelScope.launch {
            auditDao.insertLog(
                AuditLogEntity(
                    action = "SCAN_STARTED",
                    resourceType = "SCAN",
                    sanitizedMetadata = "{\"url\":\"$url\",\"maxPages\":$maxPages}"
                )
            )
        }
    }

    fun cancelActiveScan() {
        crawlerEngine.cancelScan()
        showMessage("Active scan cancelled")
    }

    fun deleteScan(scanId: Long) {
        viewModelScope.launch {
            scanDao.deleteScan(scanId)
            scanDao.deletePagesForScan(scanId)
            showMessage("Scan record deleted")
        }
    }

    // --- Contacts actions ---
    fun toggleContactSelection(id: Long, selected: Boolean) {
        viewModelScope.launch {
            contactDao.setSelection(id, selected)
        }
    }

    fun selectAllContacts(selected: Boolean) {
        viewModelScope.launch {
            contactDao.setAllSelected(selected)
        }
    }

    fun deleteContact(id: Long) {
        viewModelScope.launch {
            contactDao.deleteContact(id)
            showMessage("Contact removed")
        }
    }

    fun bulkDeleteSelected() {
        viewModelScope.launch {
            val selected = filteredContacts.value.filter { it.isSelected }.map { it.id }
            if (selected.isNotEmpty()) {
                contactDao.bulkDelete(selected)
                showMessage("Removed ${selected.size} contacts")
            }
        }
    }

    fun exportContactsCsv(selectedOnly: Boolean = false): File? {
        val contactsToExport = if (selectedOnly) {
            filteredContacts.value.filter { it.isSelected }
        } else {
            filteredContacts.value
        }

        if (contactsToExport.isEmpty()) {
            showMessage("No contacts to export")
            return null
        }

        val file = CsvExportService.exportAndShareCsv(
            context = getApplication(),
            contacts = contactsToExport
        )

        if (file != null) {
            viewModelScope.launch {
                auditDao.insertLog(
                    AuditLogEntity(
                        action = "CSV_EXPORTED",
                        resourceType = "EXPORT",
                        sanitizedMetadata = "{\"count\":${contactsToExport.size},\"fileName\":\"${file.name}\"}"
                    )
                )
            }
        }
        return file
    }

    // --- Suppression Actions ---
    fun addSuppression(email: String, reason: String) {
        val clean = email.trim().lowercase()
        if (!clean.contains("@")) {
            showMessage("Invalid email format")
            return
        }
        viewModelScope.launch {
            suppressionDao.insertSuppression(
                SuppressionEntity(
                    normalizedEmail = clean,
                    reason = reason
                )
            )
            contactDao.updateSuppressionForEmail(clean, true)
            auditDao.insertLog(
                AuditLogEntity(
                    action = "EMAIL_SUPPRESSED",
                    resourceType = "SUPPRESSION",
                    resourceId = clean,
                    sanitizedMetadata = "{\"reason\":\"$reason\"}"
                )
            )
            showMessage("$clean added to suppression list")
        }
    }

    fun removeSuppression(id: Long, email: String) {
        viewModelScope.launch {
            suppressionDao.deleteSuppression(id)
            contactDao.updateSuppressionForEmail(email, false)
            showMessage("$email removed from suppression list")
        }
    }

    // --- Gmail Connection Actions ---
    fun connectGoogleAccount(email: String) {
        viewModelScope.launch {
            val ok = outreachService.connectGoogleAccount(email)
            if (ok) {
                showMessage("Connected Gmail account: $email")
            } else {
                showMessage("Please enter a valid Gmail address")
            }
        }
    }

    fun disconnectGoogleAccount() {
        viewModelScope.launch {
            outreachService.disconnectGoogleAccount()
            showMessage("Google account disconnected")
        }
    }

    // --- Campaigns actions ---
    fun createCampaign(
        name: String,
        subject: String,
        bodyText: String,
        selectedContactIds: List<Long>
    ) {
        if (name.isBlank() || subject.isBlank() || bodyText.isBlank()) {
            showMessage("Campaign name, subject, and body are required")
            return
        }
        if (selectedContactIds.isEmpty()) {
            showMessage("Select at least one eligible recipient")
            return
        }

        viewModelScope.launch {
            val campaignId = campaignDao.insertCampaign(
                CampaignEntity(
                    name = name.trim(),
                    subject = subject.trim(),
                    bodyText = bodyText.trim(),
                    status = "DRAFT",
                    totalRecipients = selectedContactIds.size
                )
            )

            // Associate recipients
            val all = allContactsRaw.firstOrNull() ?: emptyList()
            val recipients = selectedContactIds.mapNotNull { id ->
                all.find { it.id == id }?.let { contact ->
                    CampaignRecipientEntity(
                        campaignId = campaignId,
                        contactId = contact.id,
                        recipientEmailSnapshot = contact.email,
                        status = "QUEUED"
                    )
                }
            }

            campaignDao.insertRecipients(recipients)

            auditDao.insertLog(
                AuditLogEntity(
                    action = "CAMPAIGN_CREATED",
                    resourceType = "CAMPAIGN",
                    resourceId = campaignId.toString(),
                    sanitizedMetadata = "{\"name\":\"$name\",\"recipients\":${recipients.size}}"
                )
            )

            showMessage("Campaign draft '$name' created with ${recipients.size} recipients")
        }
    }

    fun sendTestEmail(campaign: CampaignEntity, targetEmail: String) {
        viewModelScope.launch {
            val result = outreachService.sendTestEmail(campaign, targetEmail)
            if (result.isSuccess) {
                showMessage("Test email successfully sent to $targetEmail (ID: ${result.getOrNull()})")
            } else {
                showMessage("Test send failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun approveAndDispatchCampaign(campaignId: Long) {
        outreachService.dispatchApprovedCampaign(campaignId) {
            showMessage("Campaign dispatch completed!")
        }
    }

    fun pauseCampaignDispatch() {
        outreachService.pauseOrCancelSend()
        showMessage("Campaign outreach paused")
    }

    fun deleteCampaign(campaignId: Long) {
        viewModelScope.launch {
            campaignDao.deleteCampaign(campaignId)
            showMessage("Campaign deleted")
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            contactDao.clearAll()
            auditDao.insertLog(
                AuditLogEntity(
                    action = "WORKSPACE_CLEARED",
                    resourceType = "WORKSPACE",
                    sanitizedMetadata = "{\"action\":\"all_contacts_deleted\"}"
                )
            )
            showMessage("All contacts cleared from local workspace")
        }
    }

    private fun seedDemoDataIfEmpty() {
        viewModelScope.launch {
            val currentCount = contactDao.getAllContacts().firstOrNull()?.size ?: 0
            if (currentCount == 0) {
                // Seed initial compliant public business samples
                val sampleContacts = listOf(
                    ContactEntity(
                        email = "contact@stripe.com",
                        domain = "stripe.com",
                        companyName = "Stripe",
                        sourceUrl = "https://stripe.com/contact",
                        validationStatus = "ROLE_ACCOUNT"
                    ),
                    ContactEntity(
                        email = "press@github.com",
                        domain = "github.com",
                        companyName = "GitHub",
                        sourceUrl = "https://github.com/about/press",
                        validationStatus = "ROLE_ACCOUNT"
                    ),
                    ContactEntity(
                        email = "hello@figma.com",
                        domain = "figma.com",
                        companyName = "Figma",
                        sourceUrl = "https://figma.com/contact",
                        validationStatus = "ROLE_ACCOUNT"
                    ),
                    ContactEntity(
                        email = "partnerships@slack.com",
                        domain = "slack.com",
                        companyName = "Slack Technologies",
                        sourceUrl = "https://slack.com/partners",
                        validationStatus = "ROLE_ACCOUNT"
                    ),
                    ContactEntity(
                        email = "sales@datadoghq.com",
                        domain = "datadoghq.com",
                        companyName = "Datadog",
                        sourceUrl = "https://datadoghq.com/contact",
                        validationStatus = "ROLE_ACCOUNT"
                    )
                )
                contactDao.insertContacts(sampleContacts)

                // Seed initial scan record
                scanDao.insertScan(
                    ScanEntity(
                        submittedUrl = "https://stripe.com",
                        normalizedDomain = "stripe.com",
                        status = "COMPLETED",
                        pagesVisited = 18,
                        emailsFound = 3,
                        maxPages = 25,
                        startedAt = System.currentTimeMillis() - 86400000,
                        completedAt = System.currentTimeMillis() - 86380000
                    )
                )

                // Seed initial connected mock Google account for easy first-time test
                googleDao.saveConnection(
                    GoogleConnectionEntity(
                        id = 1,
                        googleAccountEmail = "outreach.demo@gmail.com",
                        grantedScopes = "https://www.googleapis.com/auth/gmail.send, profile, email",
                        isConnected = true,
                        connectedAt = System.currentTimeMillis() - 86400000
                    )
                )

                auditDao.insertLog(
                    AuditLogEntity(
                        action = "INITIAL_WORKSPACE_SETUP",
                        resourceType = "WORKSPACE",
                        sanitizedMetadata = "{\"status\":\"initialized\"}"
                    )
                )
            }
        }
    }
}
