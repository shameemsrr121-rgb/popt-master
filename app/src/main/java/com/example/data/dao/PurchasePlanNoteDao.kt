package com.example.data.dao

import androidx.room.*
import com.example.data.entity.PurchasePlanNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchasePlanNoteDao {
    @Query("SELECT * FROM purchase_plan_notes ORDER BY plannedDateMillis ASC")
    fun getAllNotes(): Flow<List<PurchasePlanNoteEntity>>

    @Query("SELECT * FROM purchase_plan_notes ORDER BY plannedDateMillis ASC")
    suspend fun getAllNotesDirect(): List<PurchasePlanNoteEntity>

    @Query("SELECT * FROM purchase_plan_notes WHERE id = :id")
    fun getNoteById(id: Long): Flow<PurchasePlanNoteEntity?>

    @Query("SELECT * FROM purchase_plan_notes WHERE plannedDateMillis BETWEEN :startMillis AND :endMillis ORDER BY plannedDateMillis ASC")
    fun getNotesBetweenDates(startMillis: Long, endMillis: Long): Flow<List<PurchasePlanNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: PurchasePlanNoteEntity): Long

    @Update
    suspend fun updateNote(note: PurchasePlanNoteEntity)

    @Delete
    suspend fun deleteNote(note: PurchasePlanNoteEntity)

    @Query("DELETE FROM purchase_plan_notes WHERE id = :id")
    suspend fun deleteNoteById(id: Long)
}
