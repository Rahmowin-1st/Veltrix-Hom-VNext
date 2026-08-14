package com.veltrix.hom.vnext.server

import com.veltrix.hom.vnext.core.DomainException
import kotlin.test.*
import java.util.UUID

class Part3FinalDepthIntegrationTest {
    private fun config():ServerConfig? {
        val url=System.getenv("VELTRIX_TEST_DATABASE_URL") ?: return null
        return ServerConfig("test",url,System.getenv("VELTRIX_TEST_DATABASE_USER")?:"postgres",System.getenv("VELTRIX_TEST_DATABASE_PASSWORD")?:"postgres",8080,"disabled",null)
    }

    @Test fun versionedModesGoalGraphRetestExportAndDeletionPurgeAreReal() {
        val cfg=config() ?: return
        Database(cfg).use { db ->
            val suffix=UUID.randomUUID().toString().take(8)
            val password="testing-password-12345"
            val session=AuthRepository(db).register(RegisterRequest("part3-depth-$suffix@example.test",password,"Part3 Depth"))
            val account=session.accountId
            val projects=ProjectRepository(db)
            val notes=NoteRepository(db)
            val assessments=AssessmentRepository(db)
            val completion=Part3CompletionRepository(db)

            val modes=completion.learningModes()
            assertTrue(setOf("TUTOR","SOCRATIC","EXAM","REVIEW","CONCISE","DEEP_DIVE").all { id -> modes.any { it.id==id && it.version==1 } })
            assertTrue(modes.all { it.toolPolicyJson.isNotBlank() && it.promptPolicyJson.isNotBlank() })

            val project=projects.create(account,CreateProjectRequest("Depth project $suffix","Integrated project depth"))
            val goalA=projects.createGoal(account,project.id,CreateGoalRequest("Learn mechanics"))
            val goalB=projects.createGoal(account,project.id,CreateGoalRequest("Pass mechanics test"))
            val dependency=completion.addGoalDependency(account,project.id,goalB.id,GoalDependencyRequest(goalA.id))
            assertEquals(goalA.id,dependency.dependsOnGoalId)
            assertFailsWith<DomainException> { completion.addGoalDependency(account,project.id,goalA.id,GoalDependencyRequest(goalB.id)) }

            val note=notes.create(account,CreateNoteRequest("Mechanics note","Momentum and energy",projectId=project.id))
            val link=completion.addGoalLink(account,project.id,goalA.id,GoalLinkRequest("NOTE",note.id))
            assertEquals(note.id,link.objectId)
            assertEquals(1,completion.goalLinks(account,project.id,goalA.id).size)

            val suggestion=completion.proposeGoalSuggestion(account,project.id,GoalSuggestionCreateRequest(goalA.id,"Solve 10 mixed problems","Suggested subtask","{\"source\":\"AI_DRAFT\",\"approvedByUser\":false}"))
            assertEquals("PROPOSED",suggestion.state)
            val accepted=completion.decideGoalSuggestion(account,project.id,suggestion.id,GoalSuggestionDecisionRequest("ACCEPT",suggestion.revision))
            assertEquals("ACCEPTED",accepted.state)
            assertNotNull(accepted.acceptedGoalId)
            assertTrue(projects.listGoals(account,project.id).any { it.id==accepted.acceptedGoalId })

            val assessment=assessments.create(account,CreateAssessmentRequest("TEST","Mechanics retest",project.id,listOf(QuestionRequest("2+2?","SHORT_ANSWER",expectedAnswers=listOf("4")))))
            db.tx { c -> c.prepareStatement("UPDATE assessment SET config='{\"durationSeconds\":600}'::jsonb WHERE id=?::uuid AND account_id=?::uuid").use { ps -> ps.setString(1,assessment.id);ps.setString(2,account);ps.executeUpdate() } }
            val question=assessments.get(account,assessment.id).questions.single()
            val first=assessments.startAttempt(account,assessment.id)
            assessments.saveAnswer(account,first.id,SaveAnswerRequest(question.id,listOf("3")))
            val firstResult=assessments.submit(account,first.id)
            val retest=completion.startRetest(account,assessment.id,RetestRequest(first.id))
            assertEquals(first.id,retest.retestOfAttemptId)
            assertNotNull(retest.deadlineAt)
            assessments.saveAnswer(account,retest.attempt.id,SaveAnswerRequest(question.id,listOf("4")))
            val secondResult=assessments.submit(account,retest.attempt.id)
            assertTrue(secondResult.score > firstResult.score)
            val history=completion.assessmentHistory(account,assessment.id)
            assertEquals(2,history.attempts.count { it.state=="GRADED" })
            assertTrue((history.improvementFromFirst ?: 0.0) > 0.0)
            val comparisons=db.tx { c -> c.prepareStatement("SELECT count(*) FROM assessment_comparison WHERE account_id=?::uuid AND assessment_id=?::uuid").use { ps -> ps.setString(1,account);ps.setString(2,assessment.id);ps.executeQuery().use { rs -> rs.next();rs.getInt(1) } } }
            assertEquals(1,comparisons)

            notes.update(account,note.id,UpdateNoteRequest(body="Momentum, energy, impulse",expectedRevision=note.revision))
            val noteVersions=db.tx { c -> c.prepareStatement("SELECT count(*) FROM note_version WHERE account_id=?::uuid AND note_id=?::uuid").use { ps -> ps.setString(1,account);ps.setString(2,note.id);ps.executeQuery().use { rs -> rs.next();rs.getInt(1) } } }
            assertEquals(1,noteVersions)

            val export=completion.export(account)
            assertEquals(3,export.schemaVersion)
            assertTrue((export.entityCounts["project"] ?: 0)>0)
            assertTrue((export.entityCounts["goal_dependency"] ?: 0)>0)
            assertTrue((export.entityCounts["assessment_comparison"] ?: 0)>0)
            assertTrue(export.entityPayloads.getValue("project").contains(project.id))
            assertTrue(export.entityPayloads.getValue("note").contains("Momentum, energy, impulse"))
            assertEquals(64,export.payloadSha256.length)

            AccountDataRepository(db).requestDeletion(account,AccountDeletionRequest(password,"DELETE"))
            val lifecycleBefore=db.tx { c -> c.prepareStatement("SELECT state FROM account_deletion_lifecycle WHERE account_id=?::uuid").use { ps -> ps.setString(1,account);ps.executeQuery().use { rs -> rs.next();rs.getString(1) } } }
            assertEquals("PURGE_PENDING",lifecycleBefore)
            AccountDeletionWorker(db,false).use { worker -> assertTrue(worker.purgeDue(10) >= 1) }
            val accountCount=db.tx { c -> c.prepareStatement("SELECT count(*) FROM account WHERE id=?::uuid").use { ps -> ps.setString(1,account);ps.executeQuery().use { rs -> rs.next();rs.getInt(1) } } }
            assertEquals(0,accountCount)
            val lifecycleAfter=db.tx { c -> c.prepareStatement("SELECT state,account_id FROM account_deletion_lifecycle WHERE account_ref_hash=? ORDER BY requested_at DESC LIMIT 1").use { ps -> ps.setString(1,sha256(account));ps.executeQuery().use { rs -> rs.next();rs.getString(1) to rs.getString(2) } } }
            assertEquals("PURGED",lifecycleAfter.first)
            assertNull(lifecycleAfter.second)
        }
    }
}
