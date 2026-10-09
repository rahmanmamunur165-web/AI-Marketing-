package com.example

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.firestore.ContactFirestoreRepository
import com.example.data.firestore.FirestoreContact
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ContactRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createContact_authenticatedUser_success() = runBlocking {
        signInTestUser("alice@contactflow.test")
        val repo = ContactFirestoreRepository(firestore, auth)

        val contact = FirestoreContact(
            email = "info@stripe.com",
            domain = "stripe.com",
            companyName = "Stripe",
            sourceUrl = "https://stripe.com/contact",
            validationStatus = "ROLE_ACCOUNT"
        )

        val contactId = withTimeout(5000L) {
            repo.addContact(contact)
        }
        assertNotNull(contactId)
        assertTrue(contactId.isNotEmpty())
    }

    @Test
    fun observeContacts_authenticatedUser_receivesUpdates() = runBlocking {
        signInTestUser("alice_obs@contactflow.test")
        val repo = ContactFirestoreRepository(firestore, auth)

        val contact = FirestoreContact(
            email = "sales@datadoghq.com",
            domain = "datadoghq.com",
            companyName = "Datadog",
            sourceUrl = "https://datadoghq.com/contact",
            validationStatus = "ROLE_ACCOUNT"
        )
        val contactId = withTimeout(5000L) { repo.addContact(contact) }

        val emitted = withTimeout(5000L) {
            repo.observeContacts().first { list -> list.any { it.email == "sales@datadoghq.com" } }
        }
        assertTrue(emitted.isNotEmpty())
    }

    @Test
    fun crossUserAccess_failsPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_cross@contactflow.test")
        val aliceRepo = ContactFirestoreRepository(firestore, auth)
        val contact = FirestoreContact(
            email = "press@github.com",
            domain = "github.com",
            sourceUrl = "https://github.com/press",
            validationStatus = "ROLE_ACCOUNT"
        )
        val contactId = withTimeout(5000L) { aliceRepo.addContact(contact) }

        // Sign in as Bob
        signInTestUser("bob_cross@contactflow.test")
        try {
            withTimeout(5000L) {
                // Bob attempts to read Alice's subcollection directly
                firestore.collection("users").document(aliceUid).collection("contacts").document(contactId).get().await()
            }
            fail("Expected PERMISSION_DENIED exception but operation succeeded")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
    }

    @Test
    fun unauthenticatedUser_failsPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_unauth@contactflow.test")

        // Sign out
        auth.signOut()

        try {
            withTimeout(5000L) {
                firestore.collection("users").document(aliceUid).collection("contacts").get().await()
            }
            fail("Expected PERMISSION_DENIED exception but operation succeeded")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
    }
}
