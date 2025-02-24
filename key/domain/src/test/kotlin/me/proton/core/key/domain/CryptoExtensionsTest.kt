/*
 * Copyright (c) 2020 Proton Technologies AG
 * This file is part of Proton Technologies AG and ProtonCore.
 *
 * ProtonCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonCore.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.proton.core.key.domain

import me.proton.core.crypto.common.pgp.VerificationStatus
import me.proton.core.crypto.common.pgp.exception.CryptoException
import me.proton.core.key.domain.entity.keyholder.KeyHolder
import me.proton.core.key.domain.entity.keyholder.KeyHolderContext
import me.proton.core.key.domain.extension.primary
import me.proton.core.key.domain.extension.publicKeyRing
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CryptoExtensionsTest {

    private val context = TestCryptoContext()
    private val keyHolder1: KeyHolder = TestKeyHolder("user1", context, keyCount = 4, passphraseCount = 1)
    private val keyHolder2: KeyHolder = TestKeyHolder("user2", context, keyCount = 2, passphraseCount = 1)

    private val message =
        """
        Lorèm ipsum dolor sit ämet, conséctétür adipiscing elit. Vivamus eget enim a sem volutpat posuere eget eu leo.
        Sed sollicitudin felis massa, sit amet iaculis justo semper eu.
        Vivamus suscipit nulla eu orci euismod, ut mattis lorem luctus.
        Etiam tincidunt non lorem quis sollicitudin. Praesent auctor lacus sed dictum consectetur.
        Ut sagittis, tortor at maximus efficitur, enim odio rhoncus nisi, eget semper odio odio et purus.
        Nulla nec cursus libero, eu mollis nibh.
        Morbi arcu arcu, mattis vitae tristique porttitor, rhoncus nec tortor. Quisque nec sodales enim, volutpat mollis dui.
        Mauris sit amet interdum mi, in faucibus ex. Quisque volutpat risus mi, eu lacinia odio tempus ac. Nulla facilisi.
        Fusce fermentum ut turpis at vehicula. Pellentesque ultricies est quis hendrerit convallis. Morbi quis nisi lorem.
        """.trimIndent()

    @Test
    fun canUnlock() {
        // canUnlock = with valid passphrase.

        assertTrue(keyHolder1.keys.primary()?.privateKey?.canUnlock(context)!!)
        assertTrue(keyHolder1.keys.first().privateKey.canUnlock(context))
        assertEquals(1, keyHolder1.keys.count { it.privateKey.canUnlock(context) })

        assertTrue(keyHolder2.keys.primary()?.privateKey?.canUnlock(context)!!)
        assertTrue(keyHolder2.keys.first().privateKey.canUnlock(context))
        assertEquals(1, keyHolder2.keys.count { it.privateKey.canUnlock(context) })
    }

    @Test
    fun useKeys_encrypt_sign_decrypt_verify_String() {
        keyHolder1.useKeys(context) {
            val encryptedMessage = encryptText(message)
            val signature = signText(message)

            val decryptedText = decryptText(encryptedMessage)
            assertNotNull(decryptTextOrNull(encryptedMessage))

            assertTrue(verifyText(decryptedText, signature))
            assertEquals(message, decryptedText)
        }
    }

    @Test
    fun useKeys_encrypt_sign_decrypt_verify_ByteArray() {
        val data = message.toByteArray()

        keyHolder1.useKeys(context) {
            val encryptedData = encryptData(data)
            val signatureData = signData(data)

            val decryptedData = decryptData(encryptedData)
            assertNotNull(decryptDataOrNull(encryptedData))

            assertTrue(verifyData(decryptedData, signatureData))
            assertTrue(data.contentEquals(decryptedData))
        }
    }

    @Test
    fun useKeys_encrypt_sign_decrypt_verify_File() {
        val data = message.toByteArray()
        val file = data.getFile("file")

        keyHolder1.useKeys(context) {
            val keyPacket = generateNewKeyPacket()
            val encryptedFile = encryptFile(file, getTempFile("encrypted"), keyPacket)
            val signatureFile = signFile(file)

            val decryptedFile = decryptFile(encryptedFile, getTempFile("decrypted"), keyPacket)
            val decryptedOrNullFile = decryptFileOrNull(encryptedFile, getTempFile("decryptedOrNull"), keyPacket)
            assertNotNull(decryptedOrNullFile)

            assertTrue(verifyFile(decryptedFile, signatureFile))
            assertTrue(file.readBytes().contentEquals(decryptedFile.file.readBytes()))

            encryptedFile.delete()
            decryptedFile.file.delete()
            decryptedOrNullFile.file.delete()
        }
        file.delete()
    }

    @Test
    fun useKeys_encrypt_decrypt_SessionKey() {
        keyHolder1.useKeys(context) {
            val sessionKey = generateNewSessionKey()
            val keyPacket = encryptSessionKey(sessionKey)
            val decryptedKey = decryptSessionKey(keyPacket)

            assertEquals(sessionKey, decryptedKey)
        }
    }

    @Test
    fun useKeys_encryptAndSign_decryptAndVerify_String() {
        keyHolder1.useKeys(context) {
            val encryptedAndSignedMessage = encryptAndSignText(message)
            val decryptedSignedText = decryptAndVerifyText(encryptedAndSignedMessage)

            assertNotNull(decryptAndVerifyTextOrNull(encryptedAndSignedMessage))
            assertEquals(VerificationStatus.Success, decryptedSignedText.status)
            assertEquals(message, decryptedSignedText.text)
        }
    }

    @Test
    fun useKeys_encryptAndSign_decryptAndVerify_ByteArray() {
        val data = message.toByteArray()

        keyHolder1.useKeys(context) {
            val encryptedAndSignedData = encryptAndSignData(data)
            val decryptedSignedData = decryptAndVerifyData(encryptedAndSignedData)

            assertNotNull(decryptAndVerifyDataOrNull(encryptedAndSignedData))
            assertEquals(VerificationStatus.Success, decryptedSignedData.status)
            assertTrue(data.contentEquals(decryptedSignedData.data))
        }
    }

    @Test
    fun useKeys_encrypt_sign__close__decrypt_verify() {
        keyHolder1.useKeys(context) {
            val encryptedMessage = encryptText(message)
            val signature = signText(message)

            encryptedMessage to signature
        }.also { (encryptedMessage, signature) ->

            keyHolder1.useKeys(context) {
                val decryptedText = decryptText(encryptedMessage)

                assertNotNull(decryptTextOrNull(encryptedMessage))
                assertTrue(verifyText(decryptedText, signature))
                assertEquals(message, decryptedText)
            }
        }
    }

    @Test
    fun useKeys_useWrongKeyHolder() {
        keyHolder1.useKeys(context) {
            encryptText(message)
        }.also {
            keyHolder2.useKeys(context) {
                assertNull(decryptTextOrNull(it))
                assertFailsWith(CryptoException::class) { decryptText(it) }
            }
        }
    }

    @Test
    fun useKeys_mustClearUnlockedKeys() {
        lateinit var keyHolderContext: KeyHolderContext

        keyHolder1.useKeys(context) {
            keyHolderContext = this

            assertTrue(privateKeyRing.keys.any())
            assertTrue(privateKeyRing.unlockedKeys.any())
            assertNotNull(privateKeyRing.unlockedPrimaryKey)

            assertTrue(publicKeyRing.keys.any())
            assertNotNull(publicKeyRing.primaryKey)
        }

        // Verify no more unlocked keys bits.
        assertTrue(keyHolderContext.privateKeyRing.unlockedPrimaryKey.unlockedKey.value.allEqual(0))
        assertTrue(keyHolderContext.privateKeyRing.unlockedKeys.all { key -> key.unlockedKey.value.allEqual(0) })
    }

    @Test
    fun without_useKeys_encrypt__decrypt_with_useKeys() {
        val encrypted = keyHolder1.keys.primary()?.privateKey?.publicKey(context)?.encryptText(context, message)
        assertNotNull(encrypted)

        keyHolder1.useKeys(context) {
            val decryptedText = decryptText(encrypted)

            assertNotNull(decryptTextOrNull(encrypted))
            assertEquals(message, decryptedText)
        }
    }

    @Test
    fun without_useKeys_encrypt_decrypt() {
        val publicKey = keyHolder1.keys.primary()?.privateKey?.publicKey(context)
        assertNotNull(publicKey)

        val encrypted = publicKey.encryptText(context, message)

        val unlockedPrivateKey = keyHolder1.keys.primary()?.privateKey?.unlock(context)
        assertNotNull(unlockedPrivateKey)

        unlockedPrivateKey.use {
            val decryptedText = it.decryptText(context, encrypted)

            assertNotNull(it.decryptTextOrNull(context, encrypted))
            assertEquals(message, decryptedText)

            val signature = it.signText(context, message)
            assertTrue(publicKey.verifyText(context, decryptedText, signature))
        }

        // Verify no more unlocked keys bits.
        assertTrue(unlockedPrivateKey.unlockedKey.value.allEqual(0))
    }

    @Test
    fun useKeys_generate_and_verify_encrypted_signature_for_String() {
        // Key holder 1 signs the message and encrypts the signature for Key Holder 2
        val encryptedSignature = keyHolder1.useKeys(context) {
            signTextEncrypted(message, keyHolder2.publicKeyRing(context))
        }
        // Key holder 2 decrypts the signature and verifies it with Key Holder 1's public keys.
        val verified = keyHolder2.useKeys(context) {
            verifyTextEncrypted(message, encryptedSignature, keyHolder1.publicKeyRing(context))
        }
        assertTrue(verified)
    }

    @Test
    fun useKeys_generate_and_verify_encrypted_signature_for_String_corrupted() {
        // Key holder 1 signs the message and encrypts the signature for Key Holder 2
        val encryptedSignature = keyHolder1.useKeys(context) {
            signTextEncrypted(message, keyHolder2.publicKeyRing(context))
        }
        // Key holder 2 decrypts the signature and verifies it with Key Holder 1's public keys.
        val verified = keyHolder2.useKeys(context) {
            verifyTextEncrypted(message + "corrupted", encryptedSignature, keyHolder1.publicKeyRing(context))
        }
        assertFalse(verified)
    }

    @Test
    fun useKeys_generate_and_verify_encrypted_signature_for_String_wrong_key() {
        // Key holder 1 signs the message and encrypts the signature for Key Holder 2
        val encryptedSignature = keyHolder1.useKeys(context) {
            signTextEncrypted(message, keyHolder2.publicKeyRing(context))
        }
        // Key holder 2 decrypts the signature and wrongly verifies it with its own public keys.
        val verified = keyHolder2.useKeys(context) {
            verifyTextEncrypted(message + "corrupted", encryptedSignature, keyHolder2.publicKeyRing(context))
        }
        assertFalse(verified)
    }

    @Test
    fun useKeys_generate_and_verify_encrypted_signature_for_ByteArray() {
        val data = message.toByteArray()
        // Key holder 1 signs the message and encrypts the signature for Key Holder 2
        val encryptedSignature = keyHolder1.useKeys(context) {
            signDataEncrypted(data, keyHolder2.publicKeyRing(context))
        }
        // Key holder 2 decrypts the signature and verifies it with Key Holder 1's public keys.
        val verified = keyHolder2.useKeys(context) {
            verifyDataEncrypted(data, encryptedSignature, keyHolder1.publicKeyRing(context))
        }
        assertTrue(verified)
    }

    @Test
    fun useKeys_generate_and_verify_encrypted_signature_for_ByteArray_corrupted () {
        val data = message.toByteArray()
        // Key holder 1 signs the message and encrypts the signature for Key Holder 2
        val encryptedSignature = keyHolder1.useKeys(context) {
            signDataEncrypted(data, keyHolder2.publicKeyRing(context))
        }
        // Key holder 2 decrypts the signature and verifies it with Key Holder 1's public keys.
        val verified = keyHolder2.useKeys(context) {
            verifyDataEncrypted(data+"corrupted".toByteArray(), encryptedSignature, keyHolder1.publicKeyRing(context))
        }
        assertFalse(verified)
    }
    @Test
    fun useKeys_generate_and_verify_encrypted_signature_for_File() {
        val data = message.toByteArray()
        val file = data.getFile("file")
        // Key holder 1 signs the message and encrypts the signature for Key Holder 2
        val encryptedSignature = keyHolder1.useKeys(context) {
            signFileEncrypted(file, keyHolder2.publicKeyRing(context))
        }
        // Key holder 2 decrypts the signature and verifies it with Key Holder 1's public keys.
        val verified = keyHolder2.useKeys(context) {
            verifyFileEncrypted(file, encryptedSignature, keyHolder1.publicKeyRing(context))
        }
        assertTrue(verified)
        file.delete()
    }
    @Test
    fun useKeys_generate_and_verify_encrypted_signature_for_File_corrupted() {
        val data = message.toByteArray() + "corrupted".toByteArray()
        val file = data.getFile("file")
        // Key holder 1 signs the message and encrypts the signature for Key Holder 2
        val encryptedSignature = keyHolder1.useKeys(context) {
            signFileEncrypted(file, keyHolder2.publicKeyRing(context))
        }
        // Key holder 2 decrypts the signature and verifies it with Key Holder 1's public keys.
        val verified = keyHolder2.useKeys(context) {
            verifyFileEncrypted(file, encryptedSignature, keyHolder1.publicKeyRing(context))
        }
        assertTrue(verified)
        file.delete()
    }
}
