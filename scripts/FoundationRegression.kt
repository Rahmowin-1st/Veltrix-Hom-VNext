import com.veltrix.hom.vnext.core.*
import com.veltrix.hom.vnext.server.foundation.*
import com.veltrix.hom.vnext.server.RequestRateLimiter
import java.time.Instant

private var pass=0
private var fail=0
private fun test(name:String, block:()->Boolean){
    try { if(block()){ println("PASS | $name"); pass++ } else { println("FAIL | $name"); fail++ } }
    catch(t:Throwable){ println("FAIL | $name | ${t::class.simpleName}: ${t.message}"); fail++ }
}
private fun expectDomain(code:String, block:()->Unit)=try{block();false}catch(e:DomainException){e.error.code==code}

private data class Provider(
    override val id:String,
    override val tier:ModelTier,
    override val capabilities:ModelCapability,
    val configured:Boolean=true,
):ModelProviderAdapter{
    override fun isConfigured()=configured
    override fun stream(request:AiProviderRequest,cancellation:RequestCancellation)=sequenceOf(AiProviderChunk("provider:$id",final=true))
}

fun main(){
    val password="Correct Horse Battery Staple!".toCharArray()
    val hash=PasswordHasher.hash(password)
    test("password hash is PBKDF2-SHA256 600k") { hash.startsWith("pbkdf2-sha256$600000$") }
    test("password verification succeeds") { PasswordHasher.verify(password, hash) }
    test("password verification rejects wrong password") { !PasswordHasher.verify("wrong password here".toCharArray(), hash) }
    test("password salts are unique") { PasswordHasher.hash(password) != hash }

    val session=SessionTokens.generate()
    test("session token is opaque and hashed server-side") { session.clientToken.length >= 40 && session.storedHashHex.length==64 }
    test("session token verification succeeds") { SessionTokens.matches(session.clientToken, session.storedHashHex) }
    test("session token verification rejects wrong token") { !SessionTokens.matches(session.clientToken+"x", session.storedHashHex) }

    val fast=Provider("fast", ModelTier.FAST, ModelCapability(true,true,false,32_000))
    val quality=Provider("quality", ModelTier.HIGH_QUALITY, ModelCapability(true,true,true,128_000))
    val router=AIRequestRouter(listOf(fast,quality))
    test("classification routes cheap/fast first") { router.route(AiOperation.CLASSIFICATION).first().id=="fast" }
    test("chat routes high-quality first") { router.route(AiOperation.CHAT).first().id=="quality" }
    test("vision requirement filters non-vision provider") { router.route(AiOperation.SOURCE_REASONING, needsVision=true).all { it.capabilities.vision } }
    test("unconfigured providers produce stable error") { expectDomain("AI_PROVIDER_DOWN") { AIRequestRouter(listOf(fast.copy(configured=false))).route(AiOperation.CHAT) } }

    val testProvider=DeterministicTestModelProvider()
    test("test AI provider is explicitly labeled mock") { testProvider.testOnly && testProvider.id=="MOCK_TEST_ONLY" }
    test("test AI provider is excluded from normal routing") { expectDomain("AI_PROVIDER_DOWN") { AIRequestRouter(listOf(testProvider)).route(AiOperation.CHAT) } }
    val routedTest=AIRequestRouter(listOf(testProvider),allowTestProviders=true).route(AiOperation.CLASSIFICATION).single()
    test("test AI provider can be enabled only for deterministic CI") { routedTest.id=="MOCK_TEST_ONLY" }
    test("test AI provider streams deterministic final marker") { val chunks=routedTest.stream(AiProviderRequest(AiOperation.CLASSIFICATION,"hello test")).toList(); chunks.joinToString(""){it.text}.contains("hello test") && chunks.last().final }
    val cancelDuring=RequestCancellation().also{it.cancel()}
    test("test AI provider obeys cancellation") { expectDomain("AI_CANCELLED") { routedTest.stream(AiProviderRequest(AiOperation.CLASSIFICATION,"cancel"),cancelDuring).toList() } }

    val cancel=RequestCancellation()
    test("request cancellation starts false") { !cancel.isCancelled() }
    cancel.cancel()
    test("request cancellation becomes observable") { cancel.isCancelled() }
    test("cancelled request throws stable domain error") { expectDomain("AI_CANCELLED") { cancel.throwIfCancelled() } }

    val acct="a"
    val proj=Project(id="p", accountId=acct, title="CEFR", aiInstruction="British English only")
    val accountMem=MemoryItem(id="ma",accountId=acct,scope=MemoryScope.ACCOUNT,category=MemoryCategory.PREFERENCE,statement="Prefer concise answers",origin=MemoryOrigin.EXPLICIT_USER,confidence=1.0,evidence=listOf(MemoryEvidence(kind="CHAT",objectId="1")))
    val projectMem=MemoryItem(id="mp",accountId=acct,scope=MemoryScope.PROJECT,scopeId="p",category=MemoryCategory.GOAL,statement="Pass CEFR exam",origin=MemoryOrigin.PROJECT_ACTIVITY,confidence=.9,evidence=listOf(MemoryEvidence(kind="GOAL",objectId="g")))
    val wrongMem=projectMem.copy(id="wrong",scopeId="other",statement="other project secret")
    val chunks=Chunker.chunk(acct,"s",1,"CEFR vocabulary and British English grammar. " .repeat(50), targetChars=300, overlapChars=50)
    val sourceResults=SourceRetrievalEngine.search(chunks,acct,"CEFR British",setOf("s"))
    val plan=ContextPlanner.plan(ContextCarry(acct,projectId="p",sourceIds=setOf("s"),learningMode=LearningMode.EXAM_PREP),proj,listOf(accountMem),listOf(projectMem,wrongMem),sourceResults,setOf("calculator.basic","source.search"),"CEFR exam")
    test("context planner applies scoped project instruction") { plan.projectInstruction=="British English only" }
    test("context planner excludes other project memory") { plan.memories.none { it.id=="wrong" } }
    test("context planner preserves selected source citations") { plan.sourceCitations.isNotEmpty() && plan.sourceCitations.all { it.sourceId=="s" } }
    test("context planner preserves learning mode") { plan.learningMode==LearningMode.EXAM_PREP }

    var process=SourceProcess("s")
    process=SourcePipeline.next(process)
    test("source pipeline validates after upload") { process.stage==SourceStage.SAFETY_VALIDATE }
    process=SourcePipeline.next(process)
    test("source pipeline reaches extract") { process.stage==SourceStage.EXTRACT }
    process=SourcePipeline.next(process, needsOcr=true)
    test("image path inserts OCR stage") { process.stage==SourceStage.OCR }
    process=SourcePipeline.next(process, needsOcr=true)
    test("OCR continues to normalize") { process.stage==SourceStage.NORMALIZE }
    while(process.stage !in setOf(SourceStage.READY,SourceStage.FAILED)) process=SourcePipeline.next(process)
    test("source pipeline reaches ready 100 percent") { process.stage==SourceStage.READY && process.progress==100 }
    test("supported MIME list is explicit") { SourcePipeline.validateMime("application/pdf") && SourcePipeline.validateMime("image/png") && !SourcePipeline.validateMime("application/x-msdownload") }

    test("chunker creates bounded multiple chunks") { chunks.size > 2 && chunks.all { it.text.length <= 310 } }
    test("chunker preserves source/version provenance") { chunks.all { it.sourceId=="s" && it.sourceVersion==1L && it.textHash.length==64 } }
    val validCitation=sourceResults.first().second
    test("citation assembler accepts exact provenance") { CitationAssembler.validate(listOf(validCitation),chunks).size==1 }
    val stale=validCitation.copy(textHash="bad")
    test("citation assembler rejects fabricated/stale citation") { expectDomain("NO_CITATION_SUPPORT") { CitationAssembler.validate(listOf(stale),chunks) } }

    val rate=RequestRateLimiter(maxRequests=2,windowSeconds=60)
    val rt=Instant.ofEpochSecond(1000)
    test("rate limiter allows capacity") { rate.allow("account",rt) && rate.allow("account",rt) }
    test("rate limiter rejects burst over capacity") { !rate.allow("account",rt) }
    test("rate limiter recovers after window") { rate.allow("account",rt.plusSeconds(60)) }


    var onboarding=OnboardingState(accountId="acct",displayName="Ada",memoryAcknowledged=true)
    onboarding=OnboardingEngine.complete(onboarding)
    test("onboarding completes with minimal required data") { onboarding.completed && onboarding.revision==2L }
    test("onboarding rejects missing memory acknowledgement") { expectDomain("VALIDATION") { OnboardingEngine.complete(OnboardingState(accountId="acct",displayName="Ada")) } }

    val plain=SourceIngestionEngine.extract("hello source".toByteArray(),"text/plain")
    test("plain text source extraction deterministic") { plain.text=="hello source" }
    val ocr=OcrAdapter { _,_ -> "recognized text" }
    test("image source uses explicit OCR adapter") { SourceIngestionEngine.extract(byteArrayOf(1,2,3),"image/png",ocr).text=="recognized text" }
    test("image source without OCR fails honestly") { expectDomain("OCR_FAILED") { SourceIngestionEngine.extract(byteArrayOf(1),"image/png") } }
    test("unsupported source type rejected") { expectDomain("SOURCE_UNSUPPORTED") { SourceIngestionEngine.extract(byteArrayOf(1),"application/x-msdownload") } }
    var pr=SourceProcessingRecord("src")
    repeat(7){ pr=SourceIngestionEngine.advance(pr) }
    test("source processing state survives deterministic progression") { pr.stage==SourceProcessStage.READY && pr.progress==100 }

    val mutation=SyncMutation(accountId="a",entityType="NOTE",entityId="n",operation="UPSERT",expectedRevision=3,idempotencyKey="idem")
    test("sync applies matching revision once") { SyncRecoveryEngine.decide(mutation,emptySet(),3,false,Instant.EPOCH).outcome==SyncOutcome.APPLIED }
    test("sync duplicate is idempotent") { SyncRecoveryEngine.decide(mutation,setOf("idem"),3,false,Instant.EPOCH).outcome==SyncOutcome.DUPLICATE }
    test("sync detects revision conflict") { SyncRecoveryEngine.decide(mutation,emptySet(),4,false,Instant.EPOCH).outcome==SyncOutcome.CONFLICT }
    test("sync transient failure schedules bounded retry") { SyncRecoveryEngine.decide(mutation.copy(attemptCount=4),emptySet(),3,true,Instant.EPOCH).let{it.outcome==SyncOutcome.RETRY_LATER && it.nextAttemptAt!!.isAfter(Instant.EPOCH)} }

    val quiet=NotificationPreferenceRule("FLASHCARD",true,java.time.LocalTime.of(22,0),java.time.LocalTime.of(7,0),"UTC")
    test("notification quiet hours cross midnight") { !NotificationPolicy.mayDeliver(quiet,Instant.parse("2026-01-01T23:00:00Z")) && NotificationPolicy.mayDeliver(quiet,Instant.parse("2026-01-01T12:00:00Z")) }
    test("disabled notification category never delivers") { !NotificationPolicy.mayDeliver(quiet.copy(enabled=false),Instant.parse("2026-01-01T12:00:00Z")) }

    val testTranslation=TranslationAdapter { input -> TranslationOutput("[${input.targetLanguage}] ${input.text}","MOCK_TEST_ONLY",false) }
    test("translation test adapter is explicitly non-live") { TranslationRouter(listOf("MOCK_TEST_ONLY" to testTranslation),allowTestOnly=true).translate(TranslationInput("hello",targetLanguage="uz")).let{!it.live && it.providerId=="MOCK_TEST_ONLY"} }
    test("translation mock excluded from normal routing") { expectDomain("TRANSLATION_FAILED") { TranslationRouter(listOf("MOCK_TEST_ONLY" to testTranslation),allowTestOnly=false).translate(TranslationInput("hello",targetLanguage="uz")) } }

    test("account deletion requires reauthentication") { expectDomain("AUTH_EXPIRED") { DataControlPolicy.validateAccountDeletion(false,"DELETE MY ACCOUNT") } }
    test("account deletion requires explicit confirmation") { expectDomain("VALIDATION") { DataControlPolicy.validateAccountDeletion(true,"delete") } }
    test("account deletion explicit path accepted") { DataControlPolicy.validateAccountDeletion(true,"DELETE MY ACCOUNT"); true }
    test("note conflict policy protects concurrent edit") { NoteConflictPolicy.canWrite(4,4) && !NoteConflictPolicy.canWrite(3,4) }
    val linked=SourceLinkPolicy.unlink(Source(accountId="a",title="s",type="TEXT",mimeType="text/plain",contentHash="h"),setOf("p1","p2"),"p1")
    test("source unlink never deletes global source") { linked.first.title=="s" && linked.second==setOf("p2") }
    test("performance page size is bounded") { PerformanceBudgets.boundedPageSize(1000)==200 && PerformanceBudgets.boundedPageSize(0)==1 }

    println("SUMMARY | PASS=$pass FAIL=$fail TOTAL=${pass+fail}")
    if(fail>0) error("Foundation regression failures=$fail")
}
