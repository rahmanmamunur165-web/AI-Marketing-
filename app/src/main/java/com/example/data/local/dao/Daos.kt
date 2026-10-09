package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entities.AuditLogEntity
import com.example.data.local.entities.CampaignEntity
import com.example.data.local.entities.CampaignRecipientEntity
import com.example.data.local.entities.ContactEntity
import com.example.data.local.entities.GoogleConnectionEntity
import com.example.data.local.entities.ScanEntity
import com.example.data.local.entities.ScannedPageEntity
import com.example.data.local.entities.SuppressionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {
    @Query("SELECT * FROM scans ORDER BY createdAt DESC")
    fun getAllScans(): Flow<List<ScanEntity>>

    @Query("SELECT * FROM scans WHERE id = :id LIMIT 1")
    fun getScanById(id: Long): Flow<ScanEntity?>

    @Query("SELECT * FROM scans WHERE status = 'RUNNING' LIMIT 1")
    suspend fun getRunningScan(): ScanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: ScanEntity): Long

    @Update
    suspend fun updateScan(scan: ScanEntity)

    @Query("DELETE FROM scans WHERE id = :id")
    suspend fun deleteScan(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScannedPage(page: ScannedPageEntity): Long

    @Query("SELECT * FROM scanned_pages WHERE scanId = :scanId ORDER BY scannedAt DESC")
    fun getPagesForScan(scanId: Long): Flow<List<ScannedPageEntity>>

    @Query("DELETE FROM scanned_pages WHERE scanId = :scanId")
    suspend fun deletePagesForScan(scanId: Long)
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY discoveredAt DESC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE isSuppressed = 0 ORDER BY discoveredAt DESC")
    fun getEligibleContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE email = :email LIMIT 1")
    suspend fun getContactByEmail(email: String): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertContact(contact: ContactEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertContacts(contacts: List<ContactEntity>): List<Long>

    @Update
    suspend fun updateContact(contact: ContactEntity)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteContact(id: Long)

    @Query("DELETE FROM contacts WHERE id IN (:ids)")
    suspend fun bulkDelete(ids: List<Long>)

    @Query("UPDATE contacts SET isSelected = :selected WHERE id = :id")
    suspend fun setSelection(id: Long, selected: Boolean)

    @Query("UPDATE contacts SET isSelected = :selected")
    suspend fun setAllSelected(selected: Boolean)

    @Query("UPDATE contacts SET isSuppressed = :isSuppressed WHERE LOWER(email) = LOWER(:email)")
    suspend fun updateSuppressionForEmail(email: String, isSuppressed: Boolean)

    @Query("DELETE FROM contacts")
    suspend fun clearAll()
}

@Dao
interface SuppressionDao {
    @Query("SELECT * FROM suppressions ORDER BY createdAt DESC")
    fun getAllSuppressions(): Flow<List<SuppressionEntity>>

    @Query("SELECT COUNT(*) FROM suppressions WHERE LOWER(normalizedEmail) = LOWER(:email)")
    suspend fun countByEmail(email: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuppression(suppression: SuppressionEntity): Long

    @Query("DELETE FROM suppressions WHERE id = :id")
    suspend fun deleteSuppression(id: Long)

    @Query("DELETE FROM suppressions WHERE LOWER(normalizedEmail) = LOWER(:email)")
    suspend fun deleteByEmail(email: String)
}

@Dao
interface CampaignDao {
    @Query("SELECT * FROM campaigns ORDER BY createdAt DESC")
    fun getAllCampaigns(): Flow<List<CampaignEntity>>

    @Query("SELECT * FROM campaigns WHERE id = :id LIMIT 1")
    fun getCampaignById(id: Long): Flow<CampaignEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCampaign(campaign: CampaignEntity): Long

    @Update
    suspend fun updateCampaign(campaign: CampaignEntity)

    @Query("DELETE FROM campaigns WHERE id = :id")
    suspend fun deleteCampaign(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipients(recipients: List<CampaignRecipientEntity>): List<Long>

    @Query("SELECT * FROM campaign_recipients WHERE campaignId = :campaignId")
    fun getRecipientsForCampaign(campaignId: Long): Flow<List<CampaignRecipientEntity>>

    @Update
    suspend fun updateRecipient(recipient: CampaignRecipientEntity)
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 100): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AuditLogEntity): Long
}

@Dao
interface GoogleConnectionDao {
    @Query("SELECT * FROM google_connections WHERE id = 1 LIMIT 1")
    fun getConnection(): Flow<GoogleConnectionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConnection(connection: GoogleConnectionEntity)

    @Query("UPDATE google_connections SET isConnected = 0 WHERE id = 1")
    suspend fun disconnect()
}
