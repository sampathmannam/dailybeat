package com.dailybeat.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dailybeat.app.data.model.Place
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {
    @Query("SELECT * FROM places ORDER BY name ASC")
    suspend fun all(): List<Place>

    @Query("SELECT * FROM places ORDER BY name ASC")
    fun observeAll(): Flow<List<Place>>

    @Insert
    suspend fun insert(place: Place): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(places: List<Place>)

    @Query("DELETE FROM places")
    suspend fun deleteAll()

    @Delete
    suspend fun delete(place: Place)

    @Update
    suspend fun update(place: Place)

    @Query("""DELETE FROM places WHERE id = :id AND name = :name AND latitude = :latitude
        AND longitude = :longitude AND radiusM = :radiusM AND isPrivate = :wasPrivate""")
    suspend fun deleteIfCurrent(id: Long, name: String, latitude: Double, longitude: Double,
        radiusM: Int, wasPrivate: Boolean): Int

    @Query("""UPDATE places SET isPrivate = :isPrivate WHERE id = :id AND name = :name
        AND latitude = :latitude AND longitude = :longitude AND radiusM = :radiusM
        AND isPrivate = :wasPrivate""")
    suspend fun setPrivateIfCurrent(id: Long, name: String, latitude: Double, longitude: Double,
        radiusM: Int, wasPrivate: Boolean, isPrivate: Boolean): Int

    @Query("""UPDATE places SET name = :newName WHERE id = :id AND name = :name
        AND latitude = :latitude AND longitude = :longitude AND radiusM = :radiusM
        AND isPrivate = :wasPrivate""")
    suspend fun renameIfCurrent(id: Long, name: String, latitude: Double, longitude: Double,
        radiusM: Int, wasPrivate: Boolean, newName: String): Int
}
