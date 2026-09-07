package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class TranslationRepository(context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val dao = db.translationDao()
    private val service = GeminiTranslatorService()
    val preferences = PreferencesManager(context)

    val translationHistory: Flow<List<TranslationEntity>> = dao.getAllTranslations()

    suspend fun translateAndSave(text: String, targetLanguage: String): Result<TranslationEntity> {
        val apiKey = preferences.getApiKey()
        val model = preferences.getModel()
        val customInstruction = preferences.getCustomInstruction()
        val enableInsight = preferences.isInsightEnabled()

        val result = service.translateWithNuance(
            text = text,
            targetLanguage = targetLanguage,
            modelName = model,
            apiKey = apiKey,
            customInstruction = customInstruction,
            enableInsight = enableInsight
        )

        return result.map { output ->
            val entity = TranslationEntity(
                sourceText = output.sourceText,
                targetLanguage = output.targetLanguage,
                directTranslation = output.directTranslation,
                culturalContext = output.culturalContext,
                keyTermsJson = KeyTermInsight.listToJson(output.keyTerms)
            )
            val id = dao.insertTranslation(entity)
            entity.copy(id = id)
        }
    }

    suspend fun clearHistory() {
        dao.clearAll()
    }

    suspend fun deleteItem(item: TranslationEntity) {
        dao.deleteTranslation(item)
    }
}
