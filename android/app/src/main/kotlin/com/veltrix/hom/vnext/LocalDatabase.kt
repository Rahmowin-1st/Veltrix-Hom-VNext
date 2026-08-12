package com.veltrix.hom.vnext

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Index
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "local_project")
data class LocalProjectEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val title: String,
    val purpose: String?,
    val status: String,
    val priority: Int,
    val updatedAtEpochMs: Long,
    val revision: Long,
    val syncState: String,
)

@Entity(tableName = "local_goal")
data class LocalGoalEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val projectId: String,
    val title: String,
    val status: String,
    val priority: Int,
    val updatedAtEpochMs: Long,
    val revision: Long,
    val syncState: String,
)

@Entity(tableName = "local_note")
data class LocalNoteEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val projectId: String?,
    val title: String,
    val body: String,
    val updatedAtEpochMs: Long,
    val revision: Long,
    val syncState: String,
)

@Entity(tableName = "local_flashcard_schedule")
data class LocalFlashcardScheduleEntity(
    @PrimaryKey val cardId: String,
    val accountId: String,
    val intervalDays: Int,
    val ease: Double,
    val repetitions: Int,
    val lapses: Int,
    val dueAtEpochMs: Long,
    val lastReviewedAtEpochMs: Long?,
    val syncState: String,
)

@Entity(tableName = "local_sync_mutation", indices = [Index(value = ["accountId", "idempotencyKey"], unique = true)])
data class LocalSyncMutationEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val expectedRevision: Long?,
    val idempotencyKey: String,
    val payload: String,
    val createdAtEpochMs: Long,
    val attemptCount: Int,
    val state: String,
)

@Entity(tableName = "cached_snapshot")
data class CachedSnapshotEntity(
    @PrimaryKey val key: String,
    val accountId: String,
    val schemaVersion: Int,
    val payload: String,
    val updatedAtEpochMs: Long,
)


@Entity(tableName = "local_profile")
data class LocalProfileEntity(
    @PrimaryKey val accountId: String,
    val displayName: String,
    val preferredLanguage: String,
    val timezone: String,
    val onboardingComplete: Boolean,
    val memoryEnabled: Boolean,
    val revision: Long,
    val updatedAtEpochMs: Long,
)

@Entity(tableName = "local_conversation")
data class LocalConversationEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val projectId: String?,
    val scope: String,
    val title: String,
    val archived: Boolean,
    val updatedAtEpochMs: Long,
    val revision: Long,
    val syncState: String,
)

@Entity(tableName = "local_message")
data class LocalMessageEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val conversationId: String,
    val parentMessageId: String?,
    val role: String,
    val state: String,
    val content: String,
    val idempotencyKey: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val revision: Long,
    val syncState: String,
)

@Entity(tableName = "local_source")
data class LocalSourceEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val title: String,
    val type: String,
    val mimeType: String,
    val contentHash: String,
    val state: String,
    val processingProgress: Int,
    val updatedAtEpochMs: Long,
    val revision: Long,
    val syncState: String,
)

@Entity(tableName = "local_assessment_attempt")
data class LocalAssessmentAttemptEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val assessmentId: String,
    val projectId: String?,
    val state: String,
    val startedAtEpochMs: Long,
    val lastActiveAtEpochMs: Long,
    val submittedAtEpochMs: Long?,
    val revision: Long,
    val syncState: String,
)

@Entity(tableName = "local_assessment_answer", primaryKeys = ["attemptId", "questionId"])
data class LocalAssessmentAnswerEntity(
    val attemptId: String,
    val questionId: String,
    val accountId: String,
    val answerPayload: String,
    val answeredAtEpochMs: Long,
    val syncState: String,
)

@Entity(tableName = "local_practice_session")
data class LocalPracticeSessionEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val projectId: String?,
    val focusTopic: String?,
    val state: String,
    val updatedAtEpochMs: Long,
    val revision: Long,
    val syncState: String,
)

@Entity(tableName = "local_mistake")
data class LocalMistakeEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val projectId: String?,
    val topic: String,
    val prompt: String,
    val expectedAnswer: String?,
    val occurrenceCount: Int,
    val status: String,
    val updatedAtEpochMs: Long,
    val revision: Long,
    val syncState: String,
)

@Dao
interface LocalProjectDao {
    @Query("SELECT * FROM local_project WHERE accountId=:accountId ORDER BY priority DESC, updatedAtEpochMs DESC")
    fun observe(accountId: String): Flow<List<LocalProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: LocalProjectEntity)

    @Query("SELECT * FROM local_project WHERE id=:id LIMIT 1")
    suspend fun get(id: String): LocalProjectEntity?
}

@Dao
interface LocalGoalDao {
    @Query("SELECT * FROM local_goal WHERE projectId=:projectId ORDER BY status ASC, priority DESC, updatedAtEpochMs DESC")
    fun observeProject(projectId: String): Flow<List<LocalGoalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: LocalGoalEntity)
}

@Dao
interface LocalNoteDao {
    @Query("SELECT * FROM local_note WHERE accountId=:accountId ORDER BY updatedAtEpochMs DESC")
    fun observe(accountId: String): Flow<List<LocalNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: LocalNoteEntity)
}

@Dao
interface LocalFlashcardDao {
    @Query("SELECT * FROM local_flashcard_schedule WHERE accountId=:accountId AND dueAtEpochMs <= :now ORDER BY dueAtEpochMs ASC")
    suspend fun due(accountId: String, now: Long): List<LocalFlashcardScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: LocalFlashcardScheduleEntity)
}

@Dao
interface LocalSyncDao {
    @Query("SELECT * FROM local_sync_mutation WHERE accountId=:accountId AND state='PENDING' ORDER BY createdAtEpochMs ASC LIMIT :limit")
    suspend fun nextBatch(accountId: String, limit: Int): List<LocalSyncMutationEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(mutation: LocalSyncMutationEntity): Long

    @Query("UPDATE local_sync_mutation SET state=:state, attemptCount=:attemptCount WHERE id=:id")
    suspend fun updateState(id: String, state: String, attemptCount: Int)

    @Query("SELECT count(*) FROM local_sync_mutation WHERE accountId=:accountId AND state='PENDING'")
    suspend fun pendingCount(accountId:String):Int
}

@Dao
interface SnapshotDao {
    @Query("SELECT * FROM cached_snapshot WHERE key=:key AND accountId=:accountId LIMIT 1")
    suspend fun get(accountId: String, key: String): CachedSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(snapshot: CachedSnapshotEntity)
}


@Dao
interface LocalProfileDao {
    @Query("SELECT * FROM local_profile WHERE accountId=:accountId LIMIT 1")
    suspend fun get(accountId: String): LocalProfileEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: LocalProfileEntity)
}

@Dao
interface LocalConversationDao {
    @Query("SELECT * FROM local_conversation WHERE accountId=:accountId ORDER BY updatedAtEpochMs DESC LIMIT :limit")
    suspend fun recent(accountId: String, limit: Int): List<LocalConversationEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: LocalConversationEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: LocalMessageEntity)
    @Query("SELECT * FROM local_message WHERE conversationId=:conversationId ORDER BY createdAtEpochMs ASC")
    suspend fun messages(conversationId: String): List<LocalMessageEntity>
}

@Dao
interface LocalSourceDao {
    @Query("SELECT * FROM local_source WHERE accountId=:accountId ORDER BY updatedAtEpochMs DESC LIMIT :limit")
    suspend fun recent(accountId: String, limit: Int): List<LocalSourceEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(source: LocalSourceEntity)
}

@Dao
interface LocalAssessmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttempt(attempt: LocalAssessmentAttemptEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnswer(answer: LocalAssessmentAnswerEntity)
    @Query("SELECT * FROM local_assessment_attempt WHERE id=:attemptId LIMIT 1")
    suspend fun attempt(attemptId: String): LocalAssessmentAttemptEntity?
    @Query("SELECT * FROM local_assessment_answer WHERE attemptId=:attemptId ORDER BY questionId")
    suspend fun answers(attemptId: String): List<LocalAssessmentAnswerEntity>
}

@Dao
interface LocalPracticeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: LocalPracticeSessionEntity)
    @Query("SELECT * FROM local_practice_session WHERE accountId=:accountId AND state='IN_PROGRESS' ORDER BY updatedAtEpochMs DESC")
    suspend fun inProgress(accountId: String): List<LocalPracticeSessionEntity>
}

@Dao
interface LocalMistakeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mistake: LocalMistakeEntity)
    @Query("SELECT * FROM local_mistake WHERE accountId=:accountId AND status!='ARCHIVED' ORDER BY updatedAtEpochMs DESC LIMIT :limit")
    suspend fun active(accountId: String, limit: Int): List<LocalMistakeEntity>
}

@Database(
    entities = [
        LocalProjectEntity::class,
        LocalGoalEntity::class,
        LocalNoteEntity::class,
        LocalFlashcardScheduleEntity::class,
        LocalSyncMutationEntity::class,
        CachedSnapshotEntity::class,
        LocalProfileEntity::class,
        LocalConversationEntity::class,
        LocalMessageEntity::class,
        LocalSourceEntity::class,
        LocalAssessmentAttemptEntity::class,
        LocalAssessmentAnswerEntity::class,
        LocalPracticeSessionEntity::class,
        LocalMistakeEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class VeltrixLocalDatabase : RoomDatabase() {
    abstract fun profiles(): LocalProfileDao
    abstract fun projects(): LocalProjectDao
    abstract fun conversations(): LocalConversationDao
    abstract fun sources(): LocalSourceDao
    abstract fun assessments(): LocalAssessmentDao
    abstract fun practice(): LocalPracticeDao
    abstract fun mistakes(): LocalMistakeDao
    abstract fun goals(): LocalGoalDao
    abstract fun notes(): LocalNoteDao
    abstract fun flashcards(): LocalFlashcardDao
    abstract fun sync(): LocalSyncDao
    abstract fun snapshots(): SnapshotDao

    companion object {
        @Volatile private var instance: VeltrixLocalDatabase? = null
        fun get(context: Context): VeltrixLocalDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, VeltrixLocalDatabase::class.java, "veltrix-vnext.db")
                .build()
                .also { instance = it }
        }
    }
}
