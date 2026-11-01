package dev.origin.ote.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "themes")
data class ThemeEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val kind: String,
    val manifestJson: String,
    val favorite: Boolean = false,
    val createdAt: Long,
    val packSha256: String,
)

@Entity(tableName = "operations")
data class OperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val action: String,
    val status: String,
    val detail: String,
)

@Dao
interface OteDao {
    @Query("SELECT * FROM themes ORDER BY createdAt DESC")
    fun observeThemes(): Flow<List<ThemeEntity>>

    @Query("SELECT * FROM themes WHERE id = :id LIMIT 1")
    suspend fun get(id: String): ThemeEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(theme: ThemeEntity)

    @Query("DELETE FROM themes WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE themes SET favorite = NOT favorite WHERE id = :id")
    suspend fun toggleFavorite(id: String)

    @Query("SELECT * FROM operations ORDER BY timestamp DESC, id DESC LIMIT 200")
    fun observeHistory(): Flow<List<OperationEntity>>

    @Insert
    suspend fun insertOperation(entry: OperationEntity)

    @Query("DELETE FROM operations WHERE id NOT IN (SELECT id FROM operations ORDER BY timestamp DESC, id DESC LIMIT 200)")
    suspend fun pruneHistory()
}

@Database(entities = [ThemeEntity::class, OperationEntity::class], version = 1, exportSchema = true)
abstract class OteDatabase : RoomDatabase() {
    abstract fun dao(): OteDao
}
