package com.example.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Represents an interactive insight for a specific word, idiom, slang, or cultural phrase.
 */
data class KeyTermInsight(
    val translatedTerm: String,
    val originalTerm: String = "",
    val type: String = "Insight",
    val explanation: String
) {
    companion object {
        fun listToJson(list: List<KeyTermInsight>): String {
            if (list.isEmpty()) return ""
            val array = JSONArray()
            for (item in list) {
                val obj = JSONObject().apply {
                    put("translatedTerm", item.translatedTerm)
                    put("originalTerm", item.originalTerm)
                    put("type", item.type)
                    put("explanation", item.explanation)
                }
                array.put(obj)
            }
            return array.toString()
        }

        fun jsonToList(jsonStr: String): List<KeyTermInsight> {
            if (jsonStr.isBlank()) return emptyList()
            return try {
                val array = JSONArray(jsonStr)
                val result = mutableListOf<KeyTermInsight>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    result.add(
                        KeyTermInsight(
                            translatedTerm = obj.optString("translatedTerm", ""),
                            originalTerm = obj.optString("originalTerm", ""),
                            type = obj.optString("type", "Insight"),
                            explanation = obj.optString("explanation", "")
                        )
                    )
                }
                result
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
