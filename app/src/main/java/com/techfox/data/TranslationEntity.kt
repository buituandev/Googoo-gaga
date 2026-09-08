package com.techfox.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "translations")
data class TranslationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceText: String,
    val targetLanguage: String,
    val directTranslation: String,
    val culturalContext: String,
    val keyTermsJson: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    val keyTerms: List<KeyTermInsight>
        get() = KeyTermInsight.jsonToList(keyTermsJson)
}

