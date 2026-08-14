package com.veltrix.hom.vnext.server

import com.veltrix.hom.vnext.core.*
import com.veltrix.hom.vnext.server.ai.AiContextOrchestrator
import com.veltrix.hom.vnext.server.ai.MemoryAutomationService
import com.veltrix.hom.vnext.server.rag.EmbeddingFactory
import com.veltrix.hom.vnext.server.rag.HybridRetrievalRepository
import com.veltrix.hom.vnext.server.storage.StorageFactory
import com.veltrix.hom.vnext.server.learning.DeepPracticeRepository
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import io.ktor.util.cio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

private const val API_VERSION = "v1"
private const val SERVICE_VERSION = "0.2.0-part2"

fun main() {
    val config = ServerConfig.fromEnv()
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") { veltrixModule(config) }.start(wait = true)
}

fun Application.veltrixModule(config: ServerConfig) {
    val db = Database(config)
    environment.monitor.subscribe(ApplicationStopped) { db.close() }
    val auth = AuthRepository(db)
    val profile = ProfileRepository(db)
    val projects = ProjectRepository(db)
    val memory = MemoryRepository(db)
    val game = Part2GameRepository(db,memory)
    val gameWorker = Part2GameWorker(game,config.workerEnabled)
    environment.monitor.subscribe(ApplicationStopped) { gameWorker.close() }
    val sources = SourceRepository(db)
    val sourceProcessing = SourceProcessingService(config, db, sources)
    environment.monitor.subscribe(ApplicationStopped) { sourceProcessing.close() }
    val chats = ChatRepository(db)
    val chatIntelligence = ChatIntelligenceRepository(db)
    val notes = NoteRepository(db)
    val assessments = AssessmentRepository(db)
    val practice = PracticeRepository(db)
    val deepPractice = DeepPracticeRepository(db)
    val flashcards = FlashcardRepository(db)
    val mistakes = MistakeRepository(db)
    val search = GlobalSearchRepository(db)
    val home = HomeAggregatorRepository(db, projects, memory, game)
    val workspace = ProjectWorkspaceRepository(db, projects, chats, memory)
    val tools = ToolRepository(db)
    val settings = SettingsRepository(db)
    val notifications = NotificationRepository(db)
    val accountData = AccountDataRepository(db)
    val timeline = ActivityTimelineRepository(db)
    val personal = PersonalAggregatorRepository(db,memory,timeline,game)
    val projectInstructions = ProjectInstructionRepository(db)
    val ai = AiExecutionService(config)
    val aiContext = AiContextOrchestrator(projects, chats, memory, projectInstructions, chatIntelligence, sourceProcessing.rag)
    val memoryAutomation = MemoryAutomationService(db, config.workerEnabled)
    environment.monitor.subscribe(ApplicationStopped) { memoryAutomation.close() }
    val artifactDrafts = GeneratedArtifactService(db, ai, config.environment)
    val extensions = WorkspaceExtensionRepository(db)
    val translation = TranslationService(config,db)
    val sync = SyncRepository(db)
    val limiter = RequestRateLimiter()

    install(CallId) {
        retrieveFromHeader(HttpHeaders.XRequestId)
        generate { UUID.randomUUID().toString() }
        verify { it.length in 8..128 }
        replyToHeader(HttpHeaders.XRequestId)
    }
    install(CallLogging) { disableDefaultColors() }
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = false; explicitNulls = false; encodeDefaults = true })
    }
    install(StatusPages) {
        exception<DomainException> { call, cause -> call.respondDomainError(cause.error) }
        exception<IllegalArgumentException> { call, cause -> call.respondDomainError(DomainError("VALIDATION",ErrorCategory.VALIDATION,cause.message ?: "Invalid request")) }
        exception<Throwable> { call, _ -> call.respondDomainError(DomainError("INTERNAL",ErrorCategory.INTERNAL,"Internal server error",retryable=false)) }
    }

    routing {
        get("/health") { call.respond(HealthResponse("ok","veltrix-hom-vnext",config.environment,SERVICE_VERSION)) }
        get("/ready") { val dbReady=db.ping(); val storageReady=sourceProcessing.storageConfigured; val embeddingReady=sourceProcessing.embeddingConfigured; val ready=dbReady&&storageReady&&embeddingReady; call.respond(ReadinessResponse(ready,if(dbReady)"ok" else "unavailable",ai.liveProviderConfigured,ai.testProviderConfigured,storageReady,embeddingReady)) }

        route("/$API_VERSION") {
            post("/auth/register") { limited(call,limiter,"public:register"); call.respond(HttpStatusCode.Created, blocking { auth.register(call.receive()) }) }
            post("/auth/login") { limited(call,limiter,"public:login"); call.respond(blocking { auth.login(call.receive()) }) }
            post("/auth/refresh") { val token=call.bearerToken(); limited(call,limiter,"session:${hashKey(token)}"); call.respond(blocking { auth.rotate(token) }) }
            post("/auth/logout") { val token=call.bearerToken(); blocking { auth.signOut(token) }; call.respond(ApiAck()) }

            get("/profile") { val p=call.principal(auth,limiter); call.respond(blocking { profile.get(p.accountId) }) }
            patch("/profile") { val p=call.principal(auth,limiter); call.respond(blocking { profile.update(p.accountId,call.receive()) }) }
            get("/home") { val p=call.principal(auth,limiter); call.respond(blocking { home.snapshot(p.accountId) }) }
            get("/personal") { val p=call.principal(auth,limiter); call.respond(blocking{personal.snapshot(p.accountId)}) }
            get("/activity") { val p=call.principal(auth,limiter); call.respond(blocking{timeline.list(p.accountId,call.request.queryParameters["projectId"],call.intQuery("limit",50,1,200),call.intQuery("offset",0,0,1_000_000))}) }

            route("/game") {
                get("/profile") { val p=call.principal(auth,limiter); call.respond(blocking{game.profile(p.accountId)}) }
                get("/xp") { val p=call.principal(auth,limiter); call.respond(blocking{game.xpHistory(p.accountId,call.intQuery("limit",50,1,200),call.intQuery("offset",0,0,1_000_000))}) }
                get("/coins") { val p=call.principal(auth,limiter); call.respond(blocking{game.coinHistory(p.accountId,call.intQuery("limit",50,1,200),call.intQuery("offset",0,0,1_000_000))}) }
                get("/coins/reconciliation") { val p=call.principal(auth,limiter); call.respond(blocking{game.reconcileCoins(p.accountId)}) }
                get("/stats") { val p=call.principal(auth,limiter); call.respond(blocking{game.gamingStats(p.accountId)}) }
                get("/events") { val p=call.principal(auth,limiter); call.respond(blocking{game.stateEvents(p.accountId,call.intQuery("limit",50,1,200),call.intQuery("offset",0,0,1_000_000))}) }
            }
            get("/achievements") { val p=call.principal(auth,limiter); call.respond(blocking{game.achievements(p.accountId)}) }
            get("/inventory") { val p=call.principal(auth,limiter); call.respond(blocking{game.inventory(p.accountId,call.intQuery("limit",100,1,200),call.intQuery("offset",0,0,1_000_000))}) }
            route("/avatars") {
                get { val p=call.principal(auth,limiter); call.respond(blocking{game.avatars(p.accountId)}) }
                post("/equip") { val p=call.principal(auth,limiter); call.respond(blocking{game.equipAvatar(p.accountId,call.receive())}) }
            }
            route("/personal/map") {
                get { val p=call.principal(auth,limiter); call.respond(blocking{game.map(p.accountId,false)}) }
                post("/unlock") { val p=call.principal(auth,limiter); call.respond(blocking{game.map(p.accountId,true)}) }
                post("/units/{unitId}/start") { val p=call.principal(auth,limiter); val id=call.parameters["unitId"]?:throw validation("unitId required"); call.respond(blocking{game.startUnit(p.accountId,id,call.receive())}) }
            }
            get("/seasons/current") { val p=call.principal(auth,limiter); val s=blocking{game.currentSeason(p.accountId)}; call.respond(CurrentSeasonResponse(s.first,s.second)) }

            route("/settings") {
                get { val p=call.principal(auth,limiter); call.respond(blocking{settings.list(p.accountId,call.request.queryParameters["category"])}) }
                put { val p=call.principal(auth,limiter); call.respond(blocking{settings.put(p.accountId,call.receive())}) }
            }
            route("/notifications") {
                get("/preferences") { val p=call.principal(auth,limiter); call.respond(blocking{notifications.preferences(p.accountId)}) }
                put("/preferences") { val p=call.principal(auth,limiter); call.respond(blocking{notifications.putPreference(p.accountId,call.receive())}) }
                get("/intents") { val p=call.principal(auth,limiter); call.respond(blocking{notifications.listIntents(p.accountId,call.intQuery("limit",100,1,200))}) }
            }
            get("/learning-modes") {
                call.principal(auth,limiter)
                call.respond(listOf(
                    LearningModeResponse("DEFAULT","balanced",false,true,"when-useful","direct"),
                    LearningModeResponse("TUTOR","adaptive",true,false,"when-useful","teaching"),
                    LearningModeResponse("SOCRATIC","guided",true,false,"when-useful","questions-first"),
                    LearningModeResponse("EXPLAIN_SIMPLE","simple",false,true,"low","simple"),
                    LearningModeResponse("DEEP_DIVE","deep",false,true,"high","detailed"),
                    LearningModeResponse("PRACTICE_COACH","concise",true,false,"low","coach"),
                    LearningModeResponse("EXAM_PREP","exam",true,false,"medium","exam"),
                    LearningModeResponse("RESEARCH","deep",false,true,"high","research"),
                    LearningModeResponse("WRITING_HELP","adaptive",true,true,"medium","editorial")
                ))
            }
            get("/account/export") { val p=call.principal(auth,limiter); call.respond(blocking{accountData.export(p.accountId)}) }
            post("/account/delete") { val p=call.principal(auth,limiter); blocking{accountData.requestDeletion(p.accountId,call.receive())}; call.respond(ApiAck()) }

            get("/project-templates") { call.principal(auth,limiter); call.respond(extensions.templates()) }

            route("/projects") {
                get { val p=call.principal(auth,limiter); call.respond(blocking { projects.list(p.accountId,call.intQuery("limit",50,1,100),call.intQuery("offset",0,0,1_000_000)) }) }
                post { val p=call.principal(auth,limiter); call.respond(HttpStatusCode.Created,blocking{projects.create(p.accountId,call.receive())}) }
                get("/{id}") { val p=call.principal(auth,limiter); call.respond(blocking{projects.get(p.accountId,call.id())}) }
                patch("/{id}") { val p=call.principal(auth,limiter); call.respond(blocking{projects.update(p.accountId,call.id(),call.receive())}) }
                get("/{id}/workspace") { val p=call.principal(auth,limiter); call.respond(blocking{workspace.snapshot(p.accountId,call.id())}) }
                get("/{id}/instructions") { val p=call.principal(auth,limiter); val value=blocking{projectInstructions.active(p.accountId,call.id())}; if(value==null) call.respond(HttpStatusCode.NoContent) else call.respond(value) }
                put("/{id}/instructions") { val p=call.principal(auth,limiter); call.respond(blocking{projectInstructions.put(p.accountId,call.id(),call.receive())}) }
                delete("/{id}/instructions") { val p=call.principal(auth,limiter); blocking{projectInstructions.reset(p.accountId,call.id())}; call.respond(ApiAck()) }
                get("/{id}/goals") { val p=call.principal(auth,limiter); call.respond(blocking{projects.listGoals(p.accountId,call.id())}) }
                post("/{id}/goals") { val p=call.principal(auth,limiter); call.respond(HttpStatusCode.Created,blocking{projects.createGoal(p.accountId,call.id(),call.receive())}) }
                post("/{id}/goals/{goalId}/transition") { val p=call.principal(auth,limiter); val req=call.receive<GoalTransitionRequest>(); call.respond(blocking{projects.transitionGoal(p.accountId,call.parameters["id"]!!,call.parameters["goalId"]!!,req.target,req.expectedRevision)}) }
                patch("/{id}/goals/{goalId}") { val p=call.principal(auth,limiter); call.respond(blocking{extensions.updateGoal(p.accountId,call.parameters["id"]!!,call.parameters["goalId"]!!,call.receive())}) }
                delete("/{id}/goals/{goalId}") { val p=call.principal(auth,limiter); val rev=call.request.queryParameters["expectedRevision"]?.toLongOrNull() ?: throw validation("expectedRevision required"); blocking{extensions.deleteGoal(p.accountId,call.parameters["id"]!!,call.parameters["goalId"]!!,rev)};call.respond(ApiAck()) }
            }

            route("/memory") {
                get { val p=call.principal(auth,limiter); call.respond(blocking{memory.list(p.accountId,call.request.queryParameters["projectId"],call.intQuery("limit",100,1,500))}) }
                post { val p=call.principal(auth,limiter); call.respond(HttpStatusCode.Created,blocking{memory.create(p.accountId,call.receive())}) }
                get("/maturity") { val p=call.principal(auth,limiter); call.respond(blocking{memory.maturity(p.accountId)}) }
                post("/{id}/correct") { val p=call.principal(auth,limiter); val req=call.receive<MemoryCorrectionRequest>(); val old=blocking{memory.list(p.accountId,null,500).firstOrNull{it.id==call.id()}} ?: throw DomainException(DomainError("MEMORY_NOT_FOUND",ErrorCategory.NOT_FOUND,"Memory not found")); call.respond(blocking{memory.correct(p.accountId,call.id(),MemoryCreateRequest(old.scope,old.scopeId,old.category,req.statement,"EXPLICIT_USER",1.0,"USER_CORRECTION",req.evidenceObjectId))}) }
            }

            route("/source-collections") {
                get { val p=call.principal(auth,limiter); call.respond(blocking{extensions.listCollections(p.accountId)}) }
                post { val p=call.principal(auth,limiter); call.respond(HttpStatusCode.Created,blocking{extensions.createCollection(p.accountId,call.receive())}) }
            }

            route("/sources") {
                post("/upload") {
                    val p=call.principal(auth,limiter)
                    val declared=call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull(); if(declared!=null && declared>SourceProcessingService.MAX_FILE_BYTES+1024*1024) throw DomainException(DomainError("SOURCE_TOO_LARGE",ErrorCategory.VALIDATION,"Upload exceeds source size limit"))
                    var title:String?=null;var type="FILE";var mime:String?=null;var fileName:String?=null;var temp:File?=null
                    val multipart=call.receiveMultipart(formFieldLimit=2L*1024*1024)
                    multipart.forEachPart { part ->
                        try { when(part) {
                            is PartData.FormItem -> when(part.name){"title"->title=part.value.take(240);"type"->type=part.value.take(40);"mimeType"->mime=part.value.take(160)}
                            is PartData.FileItem -> { if(temp!=null)throw validation("Only one file is allowed");fileName=part.originalFileName;mime=mime?:part.contentType?.toString();temp=File.createTempFile("veltrix-source-",".upload");part.provider().copyAndClose(temp!!.writeChannel()) }
                            else -> Unit
                        }} finally { part.dispose() }
                    }
                    val file=temp ?: throw validation("File part is required"); val mt=mime ?: throw validation("mimeType is required"); val t=title?.trim()?.takeIf{it.isNotEmpty()} ?: fileName?.take(240) ?: "Source"
                    call.respond(HttpStatusCode.Created,blocking{sourceProcessing.enqueueUpload(p.accountId,t,type,mt,fileName,file)})
                }
                get { val p=call.principal(auth,limiter); call.respond(blocking{sources.list(p.accountId,call.intQuery("limit",100,1,200),call.intQuery("offset",0,0,1_000_000))}) }
                post { val p=call.principal(auth,limiter); call.respond(HttpStatusCode.Created,blocking{sources.createMetadata(p.accountId,call.receive())}) }
                get("/{id}") { val p=call.principal(auth,limiter); call.respond(blocking{sources.get(p.accountId,call.id())}) }
                patch("/{id}") { val p=call.principal(auth,limiter); call.respond(blocking{sources.update(p.accountId,call.id(),call.receive())}) }
                delete("/{id}") { val p=call.principal(auth,limiter); val rev=call.request.queryParameters["expectedRevision"]?.toLongOrNull() ?: throw validation("expectedRevision required"); blocking{sources.delete(p.accountId,call.id(),rev)}; call.respond(ApiAck()) }
                get("/{id}/annotations") { val p=call.principal(auth,limiter); call.respond(blocking{extensions.annotations(p.accountId,call.id())}) }
                post("/{id}/annotations") { val p=call.principal(auth,limiter); call.respond(HttpStatusCode.Created,blocking{extensions.annotate(p.accountId,call.id(),call.receive())}) }
                post("/{id}/retry") { val p=call.principal(auth,limiter); call.respond(blocking{sourceProcessing.retry(p.accountId,call.id())}) }
                post("/{id}/text") { val p=call.principal(auth,limiter); val req=call.receive<SourceTextIngestRequest>(); call.respond(blocking{sourceProcessing.ingestDirectText(p.accountId,call.id(),req.text)}) }
                post("/search") { val p=call.principal(auth,limiter); val req=call.receive<HybridSearchRequest>(); call.respond(blocking{sourceProcessing.hybridSearch(p.accountId,req)}) }
                get("/{id}/storage") { val p=call.principal(auth,limiter); call.respond(blocking{sourceProcessing.storageHead(p.accountId,call.id())}) }
                post("/{id}/link-project") { val p=call.principal(auth,limiter); val req=call.receive<ProjectLinkRequest>(); blocking{sources.linkProject(p.accountId,call.id(),req.projectId)}; call.respond(ApiAck()) }
                post("/{id}/unlink-project") { val p=call.principal(auth,limiter); val req=call.receive<ProjectLinkRequest>(); blocking{sources.unlinkProject(p.accountId,call.id(),req.projectId)}; call.respond(ApiAck()) }
            }

            route("/chats") {
                get { val p=call.principal(auth,limiter); call.respond(blocking{chats.list(p.accountId,call.request.queryParameters["projectId"],call.intQuery("limit",50,1,100),call.intQuery("offset",0,0,1_000_000))}) }
                post { val p=call.principal(auth,limiter); call.respond(HttpStatusCode.Created,blocking{chats.create(p.accountId,call.receive())}) }
                get("/{id}") { val p=call.principal(auth,limiter); call.respond(blocking{chats.get(p.accountId,call.id())}) }
                patch("/{id}") { val p=call.principal(auth,limiter); call.respond(blocking{chats.update(p.accountId,call.id(),call.receive())}) }
                delete("/{id}") { val p=call.principal(auth,limiter); val rev=call.request.queryParameters["expectedRevision"]?.toLongOrNull() ?: throw validation("expectedRevision required"); blocking{chats.delete(p.accountId,call.id(),rev)}; call.respond(ApiAck()) }
                get("/{id}/messages") { val p=call.principal(auth,limiter); call.respond(blocking{chats.messages(p.accountId,call.id(),call.intQuery("limit",100,1,200),call.request.queryParameters["before"])}) }
                post("/{id}/messages") { val p=call.principal(auth,limiter); call.respond(HttpStatusCode.Accepted,blocking{chats.enqueueUserMessage(p.accountId,call.id(),call.receive())}) }
                post("/{id}/messages/{messageId}/edit-branch") { val p=call.principal(auth,limiter); call.respond(HttpStatusCode.Accepted,blocking{chats.editUserMessageBranch(p.accountId,call.parameters["id"]!!,call.parameters["messageId"]!!,call.receive())}) }
                post("/{id}/attachments") { val p=call.principal(auth,limiter); call.respond(HttpStatusCode.Created,blocking{chatIntelligence.attach(p.accountId,call.parameters["id"]!!,null,call.receive())}) }
                get("/{id}/attachments") { val p=call.principal(auth,limiter); call.respond(blocking{chatIntelligence.attachments(p.accountId,call.parameters["id"]!!)}) }
                post("/{id}/messages/{messageId}/attachments") { val p=call.principal(auth,limiter); call.respond(HttpStatusCode.Created,blocking{chatIntelligence.attach(p.accountId,call.parameters["id"]!!,call.parameters["messageId"]!!,call.receive())}) }
                get("/{id}/messages/{messageId}/citations") { val p=call.principal(auth,limiter); call.respond(blocking{chatIntelligence.citations(p.accountId,call.parameters["messageId"]!!)}) }
                post("/{id}/messages/{messageId}/regenerate") { val p=call.principal(auth,limiter); val req=call.receive<RegenerateRequest>(); call.respond(HttpStatusCode.Accepted,blocking{chatIntelligence.createRegeneration(p.accountId,call.parameters["id"]!!,call.parameters["messageId"]!!,req.idempotencyKey)}) }
                post("/{id}/messages/{messageId}/retry") { val p=call.principal(auth,limiter); val req=call.receive<RetryMessageRequest>(); call.respond(HttpStatusCode.Accepted,blocking{chatIntelligence.retryUser(p.accountId,call.parameters["id"]!!,call.parameters["messageId"]!!,req.idempotencyKey)}) }
                post("/{id}/link-project") { val p=call.principal(auth,limiter); val req=call.receive<ConversationLinkProjectRequest>(); call.respond(blocking{chatIntelligence.linkProject(p.accountId,call.parameters["id"]!!,req.projectId,req.expectedRevision)}) }
                post("/{id}/messages/{messageId}/save-note") { val p=call.principal(auth,limiter); call.respond(HttpStatusCode.Created,blocking{extensions.noteFromMessage(p.accountId,call.parameters["id"]!!,call.parameters["messageId"]!!,call.receive())}) }
            }

            post("/ai/stream") {
                val p=call.principal(auth,limiter)
                val req=call.receive<AiStreamRequest>()
                val preparedBase=blocking{aiContext.prepare(p.accountId,req)}
                val toolResults=blocking {
                    req.toolIds.distinct().take(8).mapNotNull { toolId ->
                        val input=req.toolInputs[toolId] ?: return@mapNotNull null
                        val result=tools.invoke(p.accountId,ToolRequest(toolId,input,req.conversationId,req.projectId))
                        toolId to result.output
                    }
                }
                val prepared=if(toolResults.isEmpty()) preparedBase else preparedBase.copy(
                    request=preparedBase.request.copy(input=preparedBase.request.input+"\n\nDETERMINISTIC TOOL RESULTS (authoritative; do not recalculate):\n"+toolResults.joinToString("\n"){(id,out)->"$id="+out.entries.sortedBy{it.key}.joinToString(","){"${it.key}:${it.value}"}})
                )
                val user=blocking{chats.enqueueUserMessage(p.accountId,req.conversationId,SendMessageRequest(req.text,null,null,req.idempotencyKey))}
                blocking{chats.markUserSending(p.accountId,user.id)}
                val assistant=blocking{chats.createAssistantStreaming(p.accountId,req.conversationId,user.id,"${req.idempotencyKey}:assistant")}
                call.response.header(HttpHeaders.CacheControl,"no-cache")
                call.respondTextWriter(ContentType.Text.EventStream) {
                    try {
                        toolResults.forEach { (toolId,output) ->
                            val payload=output.entries.sortedBy{it.key}.joinToString(","){"${it.key}=${it.value}"}
                            write("event: tool\ndata: {\"toolId\":\"${escapeJson(toolId)}\",\"result\":\"${escapeJson(payload)}\"}\n\n")
                            flush()
                        }
                        val execution=ai.stream(call.callId ?: req.idempotencyKey,prepared.request)
                        for(chunk in execution) {
                            blocking{chats.appendAssistantSegment(p.accountId,assistant.id,chunk.text)}
                            write("event: segment\ndata: {\"messageId\":\"${assistant.id}\",\"segment\":\"${escapeJson(chunk.text)}\",\"final\":${chunk.final}}\n\n")
                            flush()
                        }
                        blocking{chatIntelligence.persistCitations(p.accountId,assistant.id,prepared.citations)}
                        blocking{chats.finishAssistant(p.accountId,assistant.id)}
                        blocking{chats.markUserCompleted(p.accountId,user.id)}
                        blocking{memoryAutomation.enqueuePostChat(p.accountId,req.conversationId,user.id,assistant.id)}
                    } catch(e:DomainException) {
                        runCatching{blocking{chats.failAssistant(p.accountId,assistant.id)}}
                        runCatching{blocking{chats.markUserFailed(p.accountId,user.id)}}
                        val error=ErrorBody(e.error.code,e.error.category.name,e.error.message,e.error.retryable,call.callId)
                        write("event: error\ndata: ${errorJson(error)}\n\n");flush()
                    }
                }
            }
            post("/ai/cancel") { call.principal(auth,limiter); val req=call.receive<AiCancelRequest>(); call.respond(AiCancelResponse(ai.cancel(req.requestId))) }

            route("/notes") {
                get { val p=call.principal(auth,limiter); val q=call.request.queryParameters["q"]; call.respond(blocking{if(q.isNullOrBlank())notes.list(p.accountId,call.request.queryParameters["projectId"],call.intQuery("limit",100,1,200)) else extensions.searchNotes(p.accountId,q,call.request.queryParameters["projectId"],call.intQuery("limit",100,1,200))}) }
                post { val p=call.principal(auth,limiter); call.respond(HttpStatusCode.Created,blocking{notes.create(p.accountId,call.receive())}) }
                patch("/{id}") { val p=call.principal(auth,limiter); call.respond(blocking{notes.update(p.accountId,call.id(),call.receive())}) }
                delete("/{id}") { val p=call.principal(auth,limiter); val rev=call.request.queryParameters["expectedRevision"]?.toLongOrNull() ?: throw validation("expectedRevision required"); blocking{extensions.deleteNote(p.accountId,call.id(),rev)};call.respond(ApiAck()) }
            }

            route("/assessments") {
                post { val p=call.principal(auth,limiter); call.respond(HttpStatusCode.Created,blocking{assessments.create(p.accountId,call.receive())}) }
                get("/{id}") { val p=call.principal(auth,limiter); call.respond(blocking{assessments.get(p.accountId,call.id())}) }
                post("/{id}/attempts") { val p=call.principal(auth,limiter); call.respond(HttpStatusCode.Created,blocking{assessments.startAttempt(p.accountId,call.id())}) }
                put("/attempts/{attemptId}/answer") { val p=call.principal(auth,limiter); call.respond(blocking{assessments.saveAnswer(p.accountId,call.parameters["attemptId"]!!,call.receive())}) }
                post("/attempts/{attemptId}/submit") { val p=call.principal(auth,limiter); call.respond(blocking{assessments.submit(p.accountId,call.parameters["attemptId"]!!)}) }
            }

            route("/practice") {
                post { val p=call.principal(auth,limiter); call.respond(HttpStatusCode.Created,blocking{deepPractice.create(p.accountId,call.receive())}) }
                get("/{id}") { val p=call.principal(auth,limiter); call.respond(blocking{deepPractice.detail(p.accountId,call.id())}) }
                post("/{id}/items") { val p=call.principal(auth,limiter); call.respond(HttpStatusCode.Created,blocking{deepPractice.addItem(p.accountId,call.id(),call.receive())}) }
                post("/{id}/items/{itemId}/attempt") { val p=call.principal(auth,limiter); call.respond(blocking{deepPractice.attempt(p.accountId,call.parameters["id"]!!,call.parameters["itemId"]!!,call.receive())}) }
                post("/{id}/items/{itemId}/hint") { val p=call.principal(auth,limiter); call.respond(blocking{deepPractice.hint(p.accountId,call.parameters["id"]!!,call.parameters["itemId"]!!)}) }
                post("/{id}/items/{itemId}/check") { val p=call.principal(auth,limiter); call.respond(blocking{deepPractice.check(p.accountId,call.parameters["id"]!!,call.parameters["itemId"]!!)}) }
                post("/{id}/items/{itemId}/skip") { val p=call.principal(auth,limiter); call.respond(blocking{deepPractice.skip(p.accountId,call.parameters["id"]!!,call.parameters["itemId"]!!)}) }
                post("/{id}/complete") { val p=call.principal(auth,limiter); val req=call.receive<CompletePracticeRequest>(); call.respond(blocking{deepPractice.complete(p.accountId,call.id(),req.expectedRevision)}) }
            }
            route("/artifacts") {
                get { val p=call.principal(auth,limiter); call.respond(blocking{artifactDrafts.list(p.accountId,call.request.queryParameters["projectId"],call.intQuery("limit",100,1,200))}) }
                post { val p=call.principal(auth,limiter); call.respond(HttpStatusCode.Created,blocking{artifactDrafts.create(p.accountId,call.receive())}) }
                get("/{id}") { val p=call.principal(auth,limiter); call.respond(blocking{artifactDrafts.get(p.accountId,call.id())}) }
                post("/{id}/ready") { val p=call.principal(auth,limiter); call.respond(blocking{artifactDrafts.validateReady(p.accountId,call.id(),call.receive())}) }
            }

            route("/flashcards") {
                post("/decks") { val p=call.principal(auth,limiter); call.respond(HttpStatusCode.Created,blocking{flashcards.createDeck(p.accountId,call.receive())}) }
                post("/decks/{deckId}/cards") { val p=call.principal(auth,limiter); call.respond(HttpStatusCode.Created,blocking{flashcards.createCard(p.accountId,call.parameters["deckId"]!!,call.receive())}) }
                get("/due") { val p=call.principal(auth,limiter); call.respond(blocking{flashcards.due(p.accountId,call.intQuery("limit",100,1,200))}) }
                post("/cards/{cardId}/review") { val p=call.principal(auth,limiter); call.respond(blocking{flashcards.review(p.accountId,call.parameters["cardId"]!!,call.receive())}) }
                patch("/cards/{cardId}") { val p=call.principal(auth,limiter); call.respond(blocking{extensions.updateCard(p.accountId,call.parameters["cardId"]!!,call.receive())}) }
                post("/cards/{cardId}/reset-schedule") { val p=call.principal(auth,limiter); call.respond(blocking{extensions.resetCardSchedule(p.accountId,call.parameters["cardId"]!!)}) }
                get("/decks/{deckId}/stats") { val p=call.principal(auth,limiter); call.respond(blocking{extensions.deckStats(p.accountId,call.parameters["deckId"]!!)}) }
            }

            route("/mistakes") {
                get { val p=call.principal(auth,limiter); call.respond(blocking{mistakes.list(p.accountId,call.request.queryParameters["projectId"],call.intQuery("limit",100,1,200))}) }
                post("/{id}/resolve") { val p=call.principal(auth,limiter); val req=call.receive<ResolveMistakeRequest>(); call.respond(blocking{mistakes.resolve(p.accountId,call.id(),req.expectedRevision)}) }
                post("/{id}/practice") { val p=call.principal(auth,limiter); call.respond(HttpStatusCode.Created,blocking{extensions.practiceFromMistake(p.accountId,call.id(),call.receive())}) }
                post("/{id}/flashcard") { val p=call.principal(auth,limiter); call.respond(HttpStatusCode.Created,blocking{extensions.flashcardFromMistake(p.accountId,call.id(),call.receive())}) }
            }

            post("/sync/mutations") { val p=call.principal(auth,limiter); call.respond(blocking{sync.applyBatch(p.accountId,call.receive())}) }
            post("/search") { val p=call.principal(auth,limiter); call.respond(blocking{search.search(p.accountId,call.receive())}) }
            post("/tools/invoke") { val p=call.principal(auth,limiter); call.respond(blocking{tools.invoke(p.accountId,call.receive())}) }
            post("/translate") { val p=call.principal(auth,limiter); call.respond(blocking{translation.translate(p.accountId,call.receive())}) }
            route("/store") {
                get { val p=call.principal(auth,limiter); call.respond(blocking{game.store(p.accountId)}) }
                post("/purchase") { val p=call.principal(auth,limiter); call.respond(HttpStatusCode.Created,blocking{game.purchase(p.accountId,call.receive())}) }
            }
        }
    }
}

private suspend fun ApplicationCall.principal(auth:AuthRepository,limiter:RequestRateLimiter):AuthRepository.SessionPrincipal {
    val token=bearerToken(); limited(this,limiter,"auth:${hashKey(token)}")
    return blocking { auth.resolve(token) } ?: throw DomainException(DomainError("AUTH_EXPIRED",ErrorCategory.AUTH,"Session is invalid or expired"))
}
private fun ApplicationCall.bearerToken():String {
    val header=request.headers[HttpHeaders.Authorization] ?: throw DomainException(DomainError("AUTH_EXPIRED",ErrorCategory.AUTH,"Missing authorization"))
    if(!header.startsWith("Bearer ",ignoreCase=true)) throw DomainException(DomainError("AUTH_EXPIRED",ErrorCategory.AUTH,"Bearer token required"))
    return header.substringAfter(' ').trim().takeIf{it.length in 32..256} ?: throw DomainException(DomainError("AUTH_EXPIRED",ErrorCategory.AUTH,"Invalid bearer token"))
}
private fun ApplicationCall.id():String=parameters["id"]?.let(::validatedUuid) ?: throw validation("Missing id")
private fun validatedUuid(value:String):String=runCatching{UUID.fromString(value).toString()}.getOrElse{throw validation("Invalid UUID")}
private fun ApplicationCall.intQuery(name:String,default:Int,min:Int,max:Int):Int=request.queryParameters[name]?.toIntOrNull()?.coerceIn(min,max)?:default
private suspend fun <T> blocking(block:suspend ()->T):T=withContext(Dispatchers.IO){block()}
private fun hashKey(value:String)=sha256(value).take(24)
private fun limited(call:ApplicationCall,limiter:RequestRateLimiter,key:String){if(!limiter.allow(key))throw DomainException(DomainError("RATE_LIMIT",ErrorCategory.RATE_LIMIT,"Too many requests",true,call.callId))}
private suspend fun ApplicationCall.respondDomainError(error:DomainError){
    val e=error.copy(requestId=error.requestId?:callId)
    val status=when(e.category){ErrorCategory.AUTH->HttpStatusCode.Unauthorized;ErrorCategory.PERMISSION->HttpStatusCode.Forbidden;ErrorCategory.VALIDATION->HttpStatusCode.BadRequest;ErrorCategory.NOT_FOUND->HttpStatusCode.NotFound;ErrorCategory.CONFLICT->HttpStatusCode.Conflict;ErrorCategory.RATE_LIMIT->HttpStatusCode.TooManyRequests;ErrorCategory.TEMPORARY_UNAVAILABLE,ErrorCategory.AI_PROVIDER,ErrorCategory.NETWORK_UPSTREAM->HttpStatusCode.ServiceUnavailable;else->HttpStatusCode.InternalServerError}
    respond(status,ErrorEnvelope(ErrorBody(e.code,e.category.name,e.message,e.retryable,e.requestId)))
}
private fun errorJson(e:ErrorBody)="{\"code\":\"${escapeJson(e.code)}\",\"category\":\"${escapeJson(e.category)}\",\"message\":\"${escapeJson(e.message)}\",\"retryable\":${e.retryable},\"requestId\":${e.requestId?.let{"\"${escapeJson(it)}\""}?:"null"}}"
