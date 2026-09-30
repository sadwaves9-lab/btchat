package com.example.btchat.ui.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AISmartReply — lightweight rule-based reply suggestions.
 *
 * Runs fully offline. Swap in a TFLite model later for ML suggestions.
 */
object AISmartReply {

    suspend fun suggest(lastIncoming: String): List<String> = withContext(Dispatchers.Default) {
        val s = lastIncoming.lowercase()

        val reply = when {
            s.isBlank() -> "👍"
            "?" in s && (listOf("kaise","kaisa","how","what","kya").any { it in s }) -> "Thik hun, tum batao?"
            "hi" in s || "hello" in s || "hey" in s -> "Hello! Kaise ho?"
            "bye" in s || "good night" in s -> "Good night! 🌙"
            "thanks" in s || "thank" in s || "shukriya" in s -> "Welcome 🙂"
            "good morning" in s -> "Good morning ☀️"
            "kaise ho" in s -> "Main theek hun, tum?"
            "call" in s -> "Abhi call nahi kar sakta"
            "where" in s && "you" in s -> "Ghar pe hun"
            "ok" in s -> "👍"
            "haha" in s || "lol" in s -> "😄"
            "sad" in s || "dukhi" in s -> "Kya hua? Batao na."
            else -> null
        }

        listOfNotNull(
            reply,
            "👍", "Sure!", "OK"
        ).distinct().take(3)
    }

    /** Quick summary — first sentence + count. */
    suspend fun summarize(text: String): String = withContext(Dispatchers.Default) {
        val sentences = text.split(Regex("[.!?]")).map { it.trim() }.filter { it.isNotEmpty() }
        val head = sentences.firstOrNull()?.take(120) ?: "No content"
        "Summary: $head… (${sentences.size} sentences, ${text.length} chars)"
    }
}
