package com.example.btchat.ui.ai

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Offline translation — ML Kit on-device models.
 *
 * Usage:
 *   val text = AITranslate.translate("Hello", "hi")
 */
object AITranslate {

    private val cache = mutableMapOf<Pair<String, String>, Translator>()

    private fun translator(source: String, target: String): Translator =
        cache.getOrPut(source to target) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(target)
                .build()
            Translation.getClient(options)
        }

    /** Detect device language (BCP-47 like "en"). */
    suspend fun detect(text: String): String = suspendCancellableCoroutine { cont ->
        val id = LanguageIdentification.getClient()
        id.identifyLanguage(text)
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resume("en") }
    }

    /** Translate text. lang codes: "en", "hi", "ur", "ar", "es", "fr", "de", "zh", ... */
    suspend fun translate(text: String, targetLang: String): String {
        val src = detect(text)
        if (src == targetLang) return text
        val t = translator(src, targetLang)
        // Ensure model is downloaded (offline after first time)
        suspendCancellableCoroutine<Unit> { cont ->
            val conditions = DownloadConditions.Builder().build()
            t.downloadModelIfNeeded(conditions)
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        return suspendCancellableCoroutine { cont ->
            t.translate(text)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }

    fun mlLang(code: String): String = when (code.lowercase()) {
        "hi" -> TranslateLanguage.HINDI
        "ur" -> TranslateLanguage.URDU
        "ar" -> TranslateLanguage.ARABIC
        "es" -> TranslateLanguage.SPANISH
        "fr" -> TranslateLanguage.FRENCH
        "de" -> TranslateLanguage.GERMAN
        "zh" -> TranslateLanguage.CHINESE
        "ja" -> TranslateLanguage.JAPANESE
        else -> TranslateLanguage.ENGLISH
    }
}
