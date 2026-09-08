package com.techfox.data.subtitle

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "subtitle_jobs")
data class SubtitleJobEntity(
    @PrimaryKey
    val jobId: String,
    val fileName: String,
    val format: String,
    val targetLanguage: String,
    val contextDescription: String,
    val charactersJson: String,
    val originalContent: String,
    val cuesJson: String,
    val totalChunks: Int,
    val completedChunks: Int,
    val totalCues: Int,
    val completedCues: Int,
    val noiseCuesCount: Int,
    val status: String, // "IN_PROGRESS", "PAUSED", "COMPLETED", "FAILED"
    val resultText: String = "",
    val errorMessage: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun serializeCharacters(list: List<SubtitleCharacter>): String {
            val array = JSONArray()
            list.forEach { char ->
                val obj = JSONObject().apply {
                    put("id", char.id)
                    put("name", char.name)
                    put("description", char.description)
                }
                array.put(obj)
            }
            return array.toString()
        }

        fun deserializeCharacters(json: String): List<SubtitleCharacter> {
            if (json.isBlank()) return emptyList()
            return try {
                val array = JSONArray(json)
                val list = mutableListOf<SubtitleCharacter>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        SubtitleCharacter(
                            id = obj.optString("id"),
                            name = obj.optString("name"),
                            description = obj.optString("description")
                        )
                    )
                }
                list
            } catch (e: Exception) {
                emptyList()
            }
        }

        fun serializeCues(cues: List<SubtitleCue>): String {
            val array = JSONArray()
            cues.forEach { cue ->
                val obj = JSONObject().apply {
                    put("id", cue.id)
                    put("startTime", cue.startTime)
                    put("endTime", cue.endTime)
                    put("rawTimingLine", cue.rawTimingLine)
                    put("text", cue.text)
                    put("isNoise", cue.isNoise)
                    if (cue.translatedText != null) {
                        put("translatedText", cue.translatedText)
                    }
                }
                array.put(obj)
            }
            return array.toString()
        }

        fun deserializeCues(json: String): List<SubtitleCue> {
            if (json.isBlank()) return emptyList()
            return try {
                val array = JSONArray(json)
                val list = mutableListOf<SubtitleCue>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        SubtitleCue(
                            id = obj.getInt("id"),
                            startTime = obj.getString("startTime"),
                            endTime = obj.getString("endTime"),
                            rawTimingLine = obj.getString("rawTimingLine"),
                            text = obj.getString("text"),
                            isNoise = obj.optBoolean("isNoise", false),
                            translatedText = if (obj.has("translatedText")) obj.getString("translatedText") else null
                        )
                    )
                }
                list
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}

@Dao
interface SubtitleJobDao {
    @Query("SELECT * FROM subtitle_jobs ORDER BY updatedAt DESC")
    fun getAllJobs(): Flow<List<SubtitleJobEntity>>

    @Query("SELECT * FROM subtitle_jobs WHERE jobId = :jobId LIMIT 1")
    suspend fun getJobById(jobId: String): SubtitleJobEntity?

    @Query("SELECT * FROM subtitle_jobs WHERE status IN ('IN_PROGRESS', 'PAUSED') ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestActiveJob(): SubtitleJobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveJob(job: SubtitleJobEntity)

    @Delete
    suspend fun deleteJob(job: SubtitleJobEntity)

    @Query("DELETE FROM subtitle_jobs WHERE jobId = :jobId")
    suspend fun deleteJobById(jobId: String)
}
