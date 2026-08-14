package com.veltrix.hom.vnext

import android.content.Context
import androidx.room.*

/** Read-only local mirror of authoritative Part 2 server state. Economy mutations are never queued here. */
@Entity(tableName="part2_game_snapshot",primaryKeys=["accountId","snapshotType"])
data class Part2GameSnapshotEntity(
    val accountId:String,
    val snapshotType:String,
    val payload:String,
    val serverRevision:Long,
    val updatedAtEpochMs:Long,
)

@Dao
interface Part2GameSnapshotDao {
    @Query("SELECT * FROM part2_game_snapshot WHERE accountId=:accountId AND snapshotType=:snapshotType LIMIT 1")
    suspend fun get(accountId:String,snapshotType:String):Part2GameSnapshotEntity?

    @Insert(onConflict=OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot:Part2GameSnapshotEntity)

    @Query("DELETE FROM part2_game_snapshot WHERE accountId=:accountId")
    suspend fun deleteAccount(accountId:String)
}

@Database(entities=[Part2GameSnapshotEntity::class],version=1,exportSchema=false)
abstract class Part2GameCacheDatabase:RoomDatabase(){
    abstract fun snapshots():Part2GameSnapshotDao
    companion object {
        fun open(context:Context,name:String="veltrix-vnext-part2-game.db"):Part2GameCacheDatabase =
            Room.databaseBuilder(context.applicationContext,Part2GameCacheDatabase::class.java,name).build()
    }
}

class Part2GameLocalStore(private val database:Part2GameCacheDatabase){
    suspend fun save(accountId:String,type:String,payload:String,serverRevision:Long):Part2GameSnapshotEntity {
        require(accountId.isNotBlank() && type.isNotBlank() && serverRevision>=0)
        val dao=database.snapshots()
        val existing=dao.get(accountId,type)
        if(existing!=null && existing.serverRevision>serverRevision) return existing
        val row=Part2GameSnapshotEntity(accountId,type,payload,serverRevision,System.currentTimeMillis())
        dao.upsert(row)
        return row
    }

    suspend fun load(accountId:String,type:String):Part2GameSnapshotEntity?=database.snapshots().get(accountId,type)
    suspend fun clearAccount(accountId:String)=database.snapshots().deleteAccount(accountId)
}
