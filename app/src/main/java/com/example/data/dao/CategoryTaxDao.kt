package com.example.data.dao

import androidx.room.*
import com.example.data.entity.CategoryTaxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryTaxDao {
    @Query("SELECT * FROM category_tax_rules ORDER BY categoryName ASC, id ASC")
    fun getAllCategoryTaxRules(): Flow<List<CategoryTaxEntity>>

    @Query("SELECT * FROM category_tax_rules ORDER BY categoryName ASC, id ASC")
    suspend fun getAllCategoryTaxRulesList(): List<CategoryTaxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategoryTaxRule(entity: CategoryTaxEntity): Long

    @Update
    suspend fun updateCategoryTaxRule(entity: CategoryTaxEntity)

    @Delete
    suspend fun deleteCategoryTaxRule(entity: CategoryTaxEntity)

    @Query("DELETE FROM category_tax_rules WHERE id = :id")
    suspend fun deleteCategoryTaxRuleById(id: Long)
}
