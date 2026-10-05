package com.techfox.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

data class TranslationWithWarning(
    val entity: TranslationEntity,
    val warning: String? = null
)

class TranslationRepository(context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val dao = db.translationDao()
    private val service = GeminiTranslatorService()
    val preferences = PreferencesManager(context)

    val translationHistory: Flow<List<TranslationEntity>> = dao.getAllTranslations()

    suspend fun translateAndSave(
        text: String,
        targetLanguage: String,
        isStrictRetry: Boolean = false,
        previousWarning: String = "",
        existingEntityId: Long? = null
    ): Result<TranslationWithWarning> {
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
            enableInsight = enableInsight,
            isStrictRetry = isStrictRetry,
            previousReason = previousWarning
        )

        return result.map { output ->
            val entity = TranslationEntity(
                id = existingEntityId ?: 0,
                sourceText = output.sourceText,
                targetLanguage = output.targetLanguage,
                directTranslation = output.directTranslation,
                culturalContext = output.culturalContext,
                keyTermsJson = KeyTermInsight.listToJson(output.keyTerms)
            )
            val id = dao.insertTranslation(entity)
            TranslationWithWarning(
                entity = entity.copy(id = id),
                warning = output.warning
            )
        }
    }

    suspend fun fetchAvailableModels(apiKey: String): Result<List<String>> {
        return service.fetchAvailableModels(apiKey)
    }

    suspend fun clearHistory() {
        dao.clearAll()
    }

    suspend fun deleteItem(item: TranslationEntity) {
        dao.deleteTranslation(item)
    }
}
