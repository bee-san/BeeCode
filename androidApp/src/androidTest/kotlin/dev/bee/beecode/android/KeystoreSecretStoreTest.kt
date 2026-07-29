package dev.bee.beecode.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Credential encryption, on a real device.
 *
 * Instrumented rather than Robolectric deliberately: Robolectric has no `AndroidKeyStore`,
 * so a unit test here would either exercise a shadow that proves nothing about encryption or
 * fall through to the plaintext path and pass while encrypting nothing. The whole claim is
 * that a real platform keystore holds the key, so a real platform is the only place to check
 * it.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreSecretStoreTest {

    @Test
    fun aSecretRoundTripsThroughTheKeystore() {
        val secret = "hunter2-correct-horse"
        val stored = requireNotNull(KeystoreSecretStore.encrypt(secret)) {
            "this device must provide a keystore for the rest of these assertions to mean anything"
        }
        assertEquals(secret, KeystoreSecretStore.decrypt(stored))
    }

    @Test
    fun theStoredFormIsNotThePlaintext() {
        // The point of the whole exercise: what lands in the database must not be readable.
        val secret = "hunter2-correct-horse"
        val stored = requireNotNull(KeystoreSecretStore.encrypt(secret))
        assertNotEquals(secret, stored)
        assertTrue("the ciphertext must not contain the secret: $stored", !stored.contains(secret))
        assertTrue("the stored form must be tagged", KeystoreSecretStore.isEncrypted(stored))
    }

    @Test
    fun encryptingTwiceProducesDifferentCiphertext() {
        // A fresh IV per encryption. Identical output would leak that two credentials are the
        // same, and reusing a GCM IV under one key is a real break rather than a nicety.
        val first = requireNotNull(KeystoreSecretStore.encrypt("same-secret"))
        val second = requireNotNull(KeystoreSecretStore.encrypt("same-secret"))
        assertNotEquals(first, second)
        // Both still decrypt, so the difference is the IV and not corruption.
        assertEquals("same-secret", KeystoreSecretStore.decrypt(first))
        assertEquals("same-secret", KeystoreSecretStore.decrypt(second))
    }

    @Test
    fun aTamperedCiphertextFailsRatherThanReturningGarbage() {
        // GCM is authenticated, which is why it was chosen: a modified ciphertext must not
        // decrypt to plausible nonsense that would then be sent to a server as a password.
        val stored = requireNotNull(KeystoreSecretStore.encrypt("hunter2"))
        val tampered = stored.dropLast(4) + "AAAA"
        assertNull(KeystoreSecretStore.decrypt(tampered))
    }

    @Test
    fun aPlaintextValueFromBeforeThisExistedIsReturnedUnchanged() {
        // Existing installs have an unencrypted credential in the database. Reading it must
        // keep working, or upgrading would silently break a learner's configured sync.
        assertEquals("legacy-plaintext", KeystoreSecretStore.decrypt("legacy-plaintext"))
        assertTrue(!KeystoreSecretStore.isEncrypted("legacy-plaintext"))
    }

    @Test
    fun aTruncatedStoredValueFailsCleanly() {
        // Corruption, or a partial write. Null asks the learner again; a throw would make the
        // settings screen unopenable.
        assertNull(KeystoreSecretStore.decrypt(KeystoreSecretStore.PREFIX))
        assertNull(KeystoreSecretStore.decrypt(KeystoreSecretStore.PREFIX + "no-separator"))
    }

    @Test
    fun anEmptySecretRoundTripsRatherThanBeingSpecialCased() {
        // Not reachable through the UI, which clears blanks to null, but a store that broke
        // on empty input would be a trap for the next caller.
        val stored = requireNotNull(KeystoreSecretStore.encrypt(""))
        assertEquals("", KeystoreSecretStore.decrypt(stored))
    }
}
