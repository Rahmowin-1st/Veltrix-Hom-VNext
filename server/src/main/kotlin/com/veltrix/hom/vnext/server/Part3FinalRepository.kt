package com.veltrix.hom.vnext.server

class Part3FinalRepository(
    db:Database,
    projects:ProjectRepository,
    chats:ChatRepository,
    memory:MemoryRepository,
    projectInstructions:ProjectInstructionRepository,
){
    private val student=Part3StudentRepository(db,memory)
    private val experience=Part3ExperienceRepository(db,projects)
    private val snapshots=Part3SnapshotRepository(db,projects,chats,memory,projectInstructions,student)

    fun studentModel(accountId:String,projectId:String?=null,limit:Int=200)=student.snapshot(accountId,projectId,limit)
    fun createSignal(accountId:String,req:StudentSignalCreateRequest)=student.createExplicit(accountId,req)
    fun createInferredSignal(accountId:String,projectId:String?,type:String,valueJson:String,confidence:Double,evidence:List<StudentSignalEvidenceDto>,source:String)=student.createInferred(accountId,projectId,type,valueJson,confidence,evidence,source)
    fun correctSignal(accountId:String,id:String,req:StudentSignalCorrectionRequest)=student.correct(accountId,id,req)
    fun setSignalState(accountId:String,id:String,req:StudentSignalStateRequest)=student.state(accountId,id,req)
    fun deleteSignal(accountId:String,id:String,expectedRevision:Long)=student.delete(accountId,id,expectedRevision)
    fun recommendations(accountId:String,projectId:String?=null,limit:Int=5)=student.recommendations(accountId,projectId,limit)
    fun getContextCarry(accountId:String)=student.getContext(accountId)
    fun putContextCarry(accountId:String,req:ContextCarryPutRequest)=student.putContext(accountId,req)
    fun sourceRelationships(accountId:String,sourceId:String)=student.relationships(accountId,sourceId)
    fun createSourceRelationship(accountId:String,sourceId:String,req:SourceRelationshipCreateRequest)=student.createRelationship(accountId,sourceId,req)

    fun resolveCommand(accountId:String,req:UniversalCommandRequest)=experience.resolveCommand(accountId,req)
    fun templates()=experience.templates()
    fun customizeProject(accountId:String,id:String,req:ProjectCustomizationPutRequest)=experience.customizeProject(accountId,id,req)
    fun mapStages(accountId:String,unitId:String)=experience.mapStages(accountId,unitId)
    fun seasonHistory(accountId:String,limit:Int=30)=experience.seasonHistory(accountId,limit)
    fun avatarCatalog(accountId:String)=experience.avatarCatalog(accountId)
    fun frontendEvents(accountId:String,limit:Int=100,offset:Int=0)=experience.frontendEvents(accountId,limit,offset)
    fun timeline(accountId:String,projectId:String?,type:String?,from:String?,to:String?,query:String?,limit:Int,offset:Int)=experience.timeline(accountId,projectId,type,from,to,query,limit,offset)

    fun homeSnapshot(accountId:String)=snapshots.home(accountId)
    fun personalSnapshot(accountId:String)=snapshots.personal(accountId)
    fun workspace(accountId:String,projectId:String)=snapshots.workspace(accountId,projectId)
}
