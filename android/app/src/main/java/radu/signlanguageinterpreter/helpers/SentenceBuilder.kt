package radu.signlanguageinterpreter.helpers

import com.google.gson.reflect.TypeToken
import radu.signlanguageinterpreter.io.HttpClient

object SentenceBuilder {
    private val lexemesList = mutableListOf<List<String>>()
    private val sentenceList = mutableListOf<String>()
    private val sentenceVariantsList = mutableListOf<List<String>>()

    suspend fun buildSentence(lexemes: List<String>): String? {
        if (lexemes.isEmpty()) {
            throw IllegalStateException()
        }

        if (lexemes.count() == 1) {
            lexemesList.add(lexemes)
            val sentence = lexemes[0]
            sentenceList.add(sentence)
            return sentence
        }

        lexemesList.add(lexemes)

        val sentenceResult = HttpClient.post(
            "nlp/lexemes",
            object : TypeToken<String>() {},
            mapOf("lexemesList" to lexemesList, "sentenceList" to sentenceList)
        )

        if (sentenceResult.isFailure) {
            lexemesList.removeLast()
            return null
        }

        val sentence = sentenceResult.getOrThrow()
        sentenceList.add(sentence)
        return sentence
    }

    suspend fun buildSentenceVariants(lexemes: List<String>): List<String>? {
        if (lexemes.isEmpty()) {
            throw IllegalStateException()
        }

        if (lexemes.count() == 1) {
            lexemesList.add(lexemes)
            sentenceVariantsList.add(listOf(lexemes.joinToString(" ")))
            return lexemes
        }

        lexemesList.add(lexemes)

        val sentenceVariantsResult = HttpClient.post(
            "nlp/lexemesVariants",
            object : TypeToken<List<String>>() {},
            mapOf("lexemesList" to lexemesList, "sentenceVariantsList" to sentenceVariantsList, "numVariants" to 3)
        )

        if (sentenceVariantsResult.isFailure) {
            lexemesList.removeLast()
            return null
        }

        val sentenceVariants = sentenceVariantsResult.getOrThrow()
        sentenceVariantsList.add(sentenceVariants)
        return sentenceVariants
    }

    fun clear() {
        lexemesList.clear()
        sentenceList.clear()
        sentenceVariantsList.clear()
    }
}