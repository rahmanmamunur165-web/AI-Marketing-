package com.example.crawler

import com.example.data.local.dao.ContactDao
import com.example.data.local.dao.ScanDao
import com.example.data.local.dao.SuppressionDao
import com.example.data.local.entities.ContactEntity
import com.example.data.local.entities.ScanEntity
import com.example.data.local.entities.ScannedPageEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit

data class CrawlerProgress(
    val statusText: String = "Idle",
    val pagesVisited: Int = 0,
    val emailsFound: Int = 0,
    val errorsCount: Int = 0,
    val currentUrl: String = "",
    val latestEmails: List<ExtractedEmail> = emptyList(),
    val isRunning: Boolean = false
)

class CrawlerEngine(
    private val scanDao: ScanDao,
    private val contactDao: ContactDao,
    private val suppressionDao: SuppressionDao,
    private val coroutineScope: CoroutineScope
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private var activeJob: Job? = null
    private var activeScanId: Long? = null

    private val _progress = MutableStateFlow(CrawlerProgress())
    val progress: StateFlow<CrawlerProgress> = _progress.asStateFlow()

    fun startScan(
        submittedUrl: String,
        maxPages: Int = 25,
        politeDelayMs: Long = 400L,
        onComplete: (Long) -> Unit = {}
    ) {
        if (_progress.value.isRunning) return

        activeJob = coroutineScope.launch(Dispatchers.IO) {
            val validation = UrlSecurityValidator.validateUrl(submittedUrl)
            if (validation is UrlSecurityValidator.ValidationResult.Invalid) {
                _progress.value = CrawlerProgress(
                    statusText = "Error: ${validation.reason}",
                    isRunning = false,
                    errorsCount = 1
                )
                return@launch
            }

            val validResult = validation as UrlSecurityValidator.ValidationResult.Valid
            val normalizedStartUrl = validResult.normalizedUrl
            val targetDomain = validResult.host

            // Create initial scan record in database
            val scanId = scanDao.insertScan(
                ScanEntity(
                    submittedUrl = normalizedStartUrl,
                    normalizedDomain = targetDomain,
                    status = "RUNNING",
                    maxPages = maxPages.coerceIn(1, 100),
                    startedAt = System.currentTimeMillis()
                )
            )
            activeScanId = scanId

            _progress.value = CrawlerProgress(
                statusText = "Initiating scan for $targetDomain...",
                currentUrl = normalizedStartUrl,
                isRunning = true
            )

            val visitedUrls = mutableSetOf<String>()
            val queue = ArrayDeque<Pair<String, Int>>() // Pair<URL, Depth>
            queue.add(Pair(normalizedStartUrl, 0))

            val discoveredEmailsInJob = mutableListOf<ExtractedEmail>()
            var pagesCount = 0
            var errorsCount = 0

            try {
                while (queue.isNotEmpty() && pagesCount < maxPages) {
                    val (currentUrl, depth) = queue.removeFirst()

                    if (visitedUrls.contains(currentUrl)) continue
                    visitedUrls.add(currentUrl)

                    // Revalidate SSRF & host constraints before each fetch
                    val urlCheck = UrlSecurityValidator.validateUrl(currentUrl)
                    if (urlCheck !is UrlSecurityValidator.ValidationResult.Valid) {
                        continue
                    }

                    _progress.value = _progress.value.copy(
                        statusText = "Crawling page ${pagesCount + 1}/$maxPages...",
                        currentUrl = currentUrl,
                        pagesVisited = pagesCount,
                        emailsFound = discoveredEmailsInJob.size,
                        errorsCount = errorsCount
                    )

                    var httpStatus = 200
                    var pageError: String? = null
                    var htmlBody = ""

                    try {
                        val request = Request.Builder()
                            .url(currentUrl)
                            .header(
                                "User-Agent",
                                "Mozilla/5.0 (compatible; ContactFlow-Scanner/1.0; +https://contactflow.app/crawler-info)"
                            )
                            .header("Accept", "text/html,application/xhtml+xml")
                            .build()

                        httpClient.newCall(request).execute().use { response ->
                            httpStatus = response.code
                            if (response.isSuccessful) {
                                val contentType = response.header("Content-Type") ?: ""
                                if (contentType.contains("text/html") || contentType.contains("xhtml")) {
                                    // Limit read size to 2MB per page to avoid memory exhaustion
                                    val source = response.body?.source()
                                    htmlBody = source?.readUtf8(2 * 1024 * 1024) ?: ""
                                }
                            } else {
                                pageError = "HTTP ${response.code} ${response.message}"
                                errorsCount++
                            }
                        }
                    } catch (e: Exception) {
                        pageError = e.message ?: "Network error"
                        errorsCount++
                    }

                    // Save scanned page
                    scanDao.insertScannedPage(
                        ScannedPageEntity(
                            scanId = scanId,
                            canonicalUrl = currentUrl,
                            httpStatus = httpStatus,
                            errorCode = pageError
                        )
                    )

                    pagesCount++

                    if (htmlBody.isNotEmpty()) {
                        // Extract public business emails
                        val foundEmails = PublicEmailExtractor.extractEmailsFromHtml(
                            htmlContent = htmlBody,
                            pageUrl = currentUrl,
                            targetDomain = targetDomain
                        )

                        for (item in foundEmails) {
                            val isSuppressed = suppressionDao.countByEmail(item.email) > 0
                            val contact = ContactEntity(
                                email = item.email,
                                domain = item.domain,
                                companyName = item.companyName,
                                sourceUrl = item.sourceUrl,
                                validationStatus = item.validationStatus,
                                isSuppressed = isSuppressed
                            )
                            val inserted = contactDao.insertContact(contact)
                            if (inserted != -1L) {
                                discoveredEmailsInJob.add(item)
                            }
                        }

                        // Discover subsequent links if depth is less than 3
                        if (depth < 3 && pagesCount < maxPages) {
                            val newLinks = PublicEmailExtractor.extractLinks(
                                html = htmlBody,
                                baseUrl = currentUrl,
                                rootDomain = targetDomain
                            )
                            for (link in newLinks) {
                                if (!visitedUrls.contains(link) && queue.none { it.first == link }) {
                                    queue.add(Pair(link, depth + 1))
                                }
                            }
                        }
                    }

                    _progress.value = _progress.value.copy(
                        pagesVisited = pagesCount,
                        emailsFound = discoveredEmailsInJob.size,
                        errorsCount = errorsCount,
                        latestEmails = discoveredEmailsInJob.takeLast(10)
                    )

                    // Update scan status periodically
                    scanDao.updateScan(
                        ScanEntity(
                            id = scanId,
                            submittedUrl = normalizedStartUrl,
                            normalizedDomain = targetDomain,
                            status = "RUNNING",
                            pagesVisited = pagesCount,
                            emailsFound = discoveredEmailsInJob.size,
                            errorCount = errorsCount,
                            maxPages = maxPages,
                            startedAt = System.currentTimeMillis()
                        )
                    )

                    // Respectful polite crawl delay
                    delay(politeDelayMs)
                }

                // Completed successfully
                scanDao.updateScan(
                    ScanEntity(
                        id = scanId,
                        submittedUrl = normalizedStartUrl,
                        normalizedDomain = targetDomain,
                        status = "COMPLETED",
                        pagesVisited = pagesCount,
                        emailsFound = discoveredEmailsInJob.size,
                        errorCount = errorsCount,
                        maxPages = maxPages,
                        startedAt = System.currentTimeMillis(),
                        completedAt = System.currentTimeMillis()
                    )
                )

                _progress.value = CrawlerProgress(
                    statusText = "Scan finished: $pagesCount pages visited, ${discoveredEmailsInJob.size} emails found",
                    pagesVisited = pagesCount,
                    emailsFound = discoveredEmailsInJob.size,
                    errorsCount = errorsCount,
                    latestEmails = discoveredEmailsInJob,
                    isRunning = false
                )

                withContext(Dispatchers.Main) {
                    onComplete(scanId)
                }

            } catch (e: CancellationException) {
                // User cancelled the scan
                scanDao.updateScan(
                    ScanEntity(
                        id = scanId,
                        submittedUrl = normalizedStartUrl,
                        normalizedDomain = targetDomain,
                        status = "CANCELLED",
                        pagesVisited = pagesCount,
                        emailsFound = discoveredEmailsInJob.size,
                        errorCount = errorsCount,
                        maxPages = maxPages,
                        completedAt = System.currentTimeMillis()
                    )
                )
                _progress.value = CrawlerProgress(
                    statusText = "Scan cancelled by user",
                    pagesVisited = pagesCount,
                    emailsFound = discoveredEmailsInJob.size,
                    errorsCount = errorsCount,
                    isRunning = false
                )
            } catch (e: Exception) {
                scanDao.updateScan(
                    ScanEntity(
                        id = scanId,
                        submittedUrl = normalizedStartUrl,
                        normalizedDomain = targetDomain,
                        status = "FAILED",
                        pagesVisited = pagesCount,
                        emailsFound = discoveredEmailsInJob.size,
                        errorCount = errorsCount + 1,
                        maxPages = maxPages,
                        completedAt = System.currentTimeMillis()
                    )
                )
                _progress.value = CrawlerProgress(
                    statusText = "Scan failed: ${e.message}",
                    pagesVisited = pagesCount,
                    emailsFound = discoveredEmailsInJob.size,
                    errorsCount = errorsCount + 1,
                    isRunning = false
                )
            } finally {
                activeScanId = null
                activeJob = null
            }
        }
    }

    fun cancelScan() {
        activeJob?.cancel()
        activeJob = null
        _progress.value = _progress.value.copy(
            statusText = "Cancelling scan...",
            isRunning = false
        )
    }
}
