package com.lantianhcgp.erbian.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "messages")
data class MsgEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val body: String,
    val ts: Long,
    val status: String,
    val origin: String,
    val metaJson: String, // json.Json 序列化的 meta
)

@Dao
interface MsgDao {
    @Query("SELECT * FROM messages ORDER BY ts ASC")
    fun observeAll(): Flow<List<MsgEntity>>

    @Query("SELECT * FROM messages ORDER BY ts ASC")
    suspend fun all(): List<MsgEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(msg: MsgEntity): Long

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("SELECT COUNT(*) FROM messages WHERE id = :id")
    suspend fun countById(id: String): Int

    @Query("SELECT MAX(ts) FROM messages")
    suspend fun maxTs(): Long?
}

@Database(entities = [MsgEntity::class], version = 1, exportSchema = false)
abstract class ErbianDb : RoomDatabase() {
    abstract fun msgDao(): MsgDao
}
