package com.watube.yard.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.watube.yard.db.obj.CustomInstance
import kotlinx.coroutines.flow.Flow

// region Piped Instances
@Dao
interface CustomInstanceDao {
    @Query("SELECT * FROM customInstance ORDER BY name")
    suspend fun getAll(): List<CustomInstance>

    @Query("SELECT * FROM customInstance ORDER BY name")
    fun getAllFlow(): Flow<List<CustomInstance>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(customInstance: CustomInstance)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(customInstances: List<CustomInstance>)

    @Delete
    suspend fun deleteCustomInstance(customInstance: CustomInstance)
}
// endregion
