package com.example.btchat.bluetooth

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * EncryptionManager — AES-256-GCM with ECDH key exchange.
 *
 * Flow:
 *  1. Each side generates an EC P-256 key pair.
 *  2. Public keys are swapped via handshake.
 *  3. ECDH agreement → shared secret → HKDF → AES key.
 *  4. Messages encrypted with AES/GCM/NoPadding, 12-byte IV, 128-bit tag.
 */
object EncryptionManager {

    private const val CURVE = "secp256r1"
    private const val KEY_ALGO = "EC"
    private const val AES = "AES"
    private const val GCM_TAG_BITS = 128
    private const val IV_LEN = 12

    /** Generate ephemeral ECDH keypair. */
    fun generateKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance(KEY_ALGO)
        kpg.initialize(java.security.spec.ECGenParameterSpec(CURVE))
        return kpg.generateKeyPair()
    }

    /** Encode public key to Base64 for handshake. */
    fun encodePublicKey(pub: PublicKey): String =
        Base64.encodeToString(pub.encoded, Base64.NO_WRAP)

    fun decodePublicKey(b64: String): PublicKey {
        val bytes = Base64.decode(b64, Base64.NO_WRAP)
        return KeyFactory.getInstance(KEY_ALGO).generatePublic(X509EncodedKeySpec(bytes))
    }

    /** Derive 256-bit AES key from ECDH shared secret. */
    fun deriveAesKey(ownPrivate: KeyPair, theirPub: PublicKey): SecretKey {
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(ownPrivate.private)
        ka.doPhase(theirPub, true)
        val shared = ka.generateSecret()
        // Simple HKDF-ish: SHA-256 of shared secret → first 32 bytes
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val keyBytes = md.digest(shared)
        return SecretKeySpec(keyBytes, AES)
    }

    /** Encrypt plaintext with AES-256-GCM. Output = IV(12) || CIPHERTEXT+TAG. */
    fun encrypt(plain: ByteArray, key: SecretKey): ByteArray {
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ct = cipher.doFinal(plain)
        return iv + ct
    }

    /** Decrypt. Input = IV(12) || CIPHERTEXT+TAG. */
    fun decrypt(cipherText: ByteArray, key: SecretKey): ByteArray {
        require(cipherText.size > IV_LEN) { "Cipher text too short" }
        val iv = cipherText.copyOfRange(0, IV_LEN)
        val ct = cipherText.copyOfRange(IV_LEN, cipherText.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ct)
    }

    /** Convenience string helpers. */
    fun encryptString(text: String, key: SecretKey): String =
        Base64.encodeToString(encrypt(text.toByteArray(Charsets.UTF_8), key), Base64.NO_WRAP)

    fun decryptString(b64: String, key: SecretKey): String =
        String(decrypt(Base64.decode(b64, Base64.NO_WRAP), key), Charsets.UTF_8)
}
