package com.example.data.firestore

import android.content.Context
import com.example.R
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

@IgnoreExtraProperties
data class FirestoreContact(
    @DocumentId val id: String = "",
    val email: String = "",
    val domain: String = "",
    val companyName: String? = null,
    val sourceUrl: String = "",
    val validationStatus: String = "PUBLIC_BUSINESS",
    val isSuppressed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

class ContactFirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeContacts(): Flow<List<FirestoreContact>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("users").document(uid).collection("contacts")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(FirestoreContact::class.java) }
        )
    }

    suspend fun addContact(contact: FirestoreContact): String {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("contacts").document()
        val payload = hashMapOf<String, Any?>(
            "email" to contact.email,
            "domain" to contact.domain,
            "companyName" to contact.companyName,
            "sourceUrl" to contact.sourceUrl,
            "validationStatus" to contact.validationStatus,
            "isSuppressed" to contact.isSuppressed,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }

        docRef.set(payload).await()
        return docRef.id
    }

    suspend fun deleteContact(contactId: String) {
        val uid = requireUserId()
        db.collection("users").document(uid).collection("contacts").document(contactId).delete().await()
    }
}
