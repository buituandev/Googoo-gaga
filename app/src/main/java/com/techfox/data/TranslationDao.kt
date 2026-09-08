package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TranslationDao {
    @Query("SELECT * FROM translations ORDER BY timestamp DESC")
    fun getAllTranslations(): Flow<List<TranslationEntity>>

    @Query("SELECT * FROM translations ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestTranslation(): TranslationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranslation(item: TranslationEntity): Long

    @Delete
    suspend fun deleteTranslation(item: TranslationEntity)

    @Query("DELETE FROM translations")
    suspend fun clearAll()
}
