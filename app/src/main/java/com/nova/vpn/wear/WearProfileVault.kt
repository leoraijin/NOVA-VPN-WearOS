package com.nova.vpn.wear

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores the user-approved phone→watch config encrypted with a watch-local Keystore key. */
class WearProfileVault(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "wear-profile.vault"))

    fun save(config: String) = synchronized(LOCK) {
        require(config.isNotBlank())
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(config.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        val bytes = ByteBuffer.allocate(4 + iv.size + encrypted.size).putInt(iv.size).put(iv).put(encrypted).array()
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }

    fun load(): String? = synchronized(LOCK) {
        if (!file.baseFile.isFile && !File(file.baseFile.path + ".bak").isFile) return@synchronized null
        val data = file.readFully()
        val buffer = ByteBuffer.wrap(data)
        val ivSize = buffer.int
        require(ivSize in 12..16 && buffer.remaining() > ivSize + 16) { "Хранилище профиля повреждено" }
        val iv = ByteArray(ivSize).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        Cipher.getInstance(TRANSFORMATION).run {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(doFinal(ciphertext), Charsets.UTF_8)
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build())
        }.generateKey()
    }

    private companion object {
        val LOCK = Any()
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val ALIAS = "nova.wear.profile.v1"
    }
}
