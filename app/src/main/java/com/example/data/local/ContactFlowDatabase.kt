package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.AuditLogDao
import com.example.data.local.dao.CampaignDao
import com.example.data.local.dao.ContactDao
import com.example.data.local.dao.GoogleConnectionDao
import com.example.data.local.dao.ScanDao
import com.example.data.local.dao.SuppressionDao
import com.example.data.local.entities.AuditLogEntity
import com.example.data.local.entities.CampaignEntity
import com.example.data.local.entities.CampaignRecipientEntity
import com.example.data.local.entities.ContactEntity
import com.example.data.local.entities.GoogleConnectionEntity
import com.example.data.local.entities.ScanEntity
import com.example.data.local.entities.ScannedPageEntity
import com.example.data.local.entities.SuppressionEntity

@Database(
    entities = [
        ScanEntity::class,
        ScannedPageEntity::class,
        ContactEntity::class,
        SuppressionEntity::class,
        CampaignEntity::class,
        CampaignRecipientEntity::class,
        AuditLogEntity::class,
        GoogleConnectionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ContactFlowDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao
    abstract fun contactDao(): ContactDao
    abstract fun suppressionDao(): SuppressionDao
    abstract fun campaignDao(): CampaignDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun googleConnectionDao(): GoogleConnectionDao

    companion object {
        @Volatile
        private var INSTANCE: ContactFlowDatabase? = null

        fun getDatabase(context: Context): ContactFlowDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ContactFlowDatabase::class.java,
                    "contactflow_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
