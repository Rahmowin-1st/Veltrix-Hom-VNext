package com.veltrix.hom.vnext

import android.content.Context
import androidx.room.*

@Entity(
    tableName="part3_snapshot",
    primaryKeys=["accountId","kind","scopeId"],
    indices=[Index(value=["accountId","fetchedAtEpochMs"])]
)
data class Part3SnapshotEntity(
    val accountId:String,
    val kind:String,
    val scopeId:String="GLOBAL",
    val payload:String,
    val serverRevision:Long,
    val fetchedAtEpochMs:Long,
    val etag:String?=null,
)

@Entity(tableName="part3_context_carry")
data class Part3ContextCarryEntity(
    @PrimaryKey val accountId:String,
    val projectId:String?,
    val sourceIdsJson:String,
    val conversationId:String?,
    val assessmentId:String?,
    val topic:String?,
    val learningMode:String?,
    val origin:String,
    val returnDestination:String?,
    val contextRevision:Long,
    val syncState:String,
    val updatedAtEpochMs:Long,
)

@Entity(
    tableName="part3_frontend_event",
    primaryKeys=["accountId","eventId"],
    indices=[Index(value=["accountId","occurredAtEpochMs"])]
)
data class Part3FrontendEventEntity(
    val accountId:String,
    val eventId:String,
    val eventType:String,
    val subjectId:String?,
    val projectId:String?,
    val payload:String,
    val occurredAtEpochMs:Long,
    val consumedAtEpochMs:Long?=null,
)

@Dao
interface Part3SnapshotDao {
    @Query("SELECT * FROM part3_snapshot WHERE accountId=:accountId AND kind=:kind AND scopeId=:scopeId LIMIT 1")
    suspend fun get(accountId:String,kind:String,scopeId:String="GLOBAL"):Part3SnapshotEntity?

    @Insert(onConflict=OnConflictStrategy.REPLACE)
    suspend fun put(value:Part3SnapshotEntity)

    @Query("DELETE FROM part3_snapshot WHERE accountId=:accountId")
    suspend fun clearAccount(accountId:String)
}

@Dao
interface Part3ContextCarryDao {
    @Query("SELECT * FROM part3_context_carry WHERE accountId=:accountId LIMIT 1")
    suspend fun get(accountId:String):Part3ContextCarryEntity?

    @Insert(onConflict=OnConflictStrategy.REPLACE)
    suspend fun put(value:Part3ContextCarryEntity)

    @Query("UPDATE part3_context_carry SET syncState='ACKED', updatedAtEpochMs=:now WHERE accountId=:accountId AND contextRevision<=:revision")
    suspend fun markAcked(accountId:String,revision:Long,now:Long)

    @Query("DELETE FROM part3_context_carry WHERE accountId=:accountId")
    suspend fun clear(accountId:String)
}

@Dao
interface Part3FrontendEventDao {
    @Query("SELECT * FROM part3_frontend_event WHERE accountId=:accountId AND consumedAtEpochMs IS NULL ORDER BY occurredAtEpochMs ASC LIMIT :limit")
    suspend fun pending(accountId:String,limit:Int):List<Part3FrontendEventEntity>

    @Insert(onConflict=OnConflictStrategy.IGNORE)
    suspend fun insertAll(values:List<Part3FrontendEventEntity>)

    @Query("UPDATE part3_frontend_event SET consumedAtEpochMs=:now WHERE accountId=:accountId AND eventId=:eventId AND consumedAtEpochMs IS NULL")
    suspend fun consume(accountId:String,eventId:String,now:Long):Int
}

@Database(
    entities=[Part3SnapshotEntity::class,Part3ContextCarryEntity::class,Part3FrontendEventEntity::class],
    version=1,
    exportSchema=true,
)
abstract class Part3LocalDatabase:RoomDatabase() {
    abstract fun snapshots():Part3SnapshotDao
    abstract fun contextCarry():Part3ContextCarryDao
    abstract fun frontendEvents():Part3FrontendEventDao

    companion object {
        @Volatile private var instance:Part3LocalDatabase?=null
        fun get(context:Context):Part3LocalDatabase=instance?:synchronized(this) {
            instance?:Room.databaseBuilder(context.applicationContext,Part3LocalDatabase::class.java,"veltrix-vnext-part3.db").build().also { instance=it }
        }
        fun openForTest(context:Context,name:String):Part3LocalDatabase=Room.databaseBuilder(context.applicationContext,Part3LocalDatabase::class.java,name).build()
    }
}
