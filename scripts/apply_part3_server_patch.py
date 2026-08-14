#!/usr/bin/env python3
from pathlib import Path


def replace_required(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    if new in text:
        return
    if old not in text:
        raise SystemExit(f"Expected patch anchor missing: {path}: {old[:100]!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")

# Final service identity + Part 3 facade.
replace_required(
    "server/src/main/kotlin/com/veltrix/hom/vnext/server/Main.kt",
    'private const val SERVICE_VERSION = "0.2.0-part2"',
    'private const val SERVICE_VERSION = "0.3.0-backend-part3"',
)
replace_required(
    "server/src/main/kotlin/com/veltrix/hom/vnext/server/Main.kt",
    '    val projectInstructions = ProjectInstructionRepository(db)\n    val ai = AiExecutionService(config)',
    '    val projectInstructions = ProjectInstructionRepository(db)\n    val part3 = Part3FinalRepository(db, projects, chats, memory, projectInstructions)\n    val ai = AiExecutionService(config)',
)
replace_required(
    "server/src/main/kotlin/com/veltrix/hom/vnext/server/Main.kt",
    '''            get("/home") { val p=call.principal(auth,limiter); call.respond(blocking { home.snapshot(p.accountId) }) }\n            get("/personal") { val p=call.principal(auth,limiter); call.respond(blocking{personal.snapshot(p.accountId)}) }\n            get("/activity") { val p=call.principal(auth,limiter); call.respond(blocking{timeline.list(p.accountId,call.request.queryParameters["projectId"],call.intQuery("limit",50,1,200),call.intQuery("offset",0,0,1_000_000))}) }''',
    '''            get("/home") { val p=call.principal(auth,limiter); call.respond(blocking { part3.homeSnapshot(p.accountId) }) }\n            get("/personal") { val p=call.principal(auth,limiter); call.respond(blocking { part3.personalSnapshot(p.accountId) }) }\n            get("/activity") { val p=call.principal(auth,limiter); call.respond(blocking { part3.timeline(p.accountId,call.request.queryParameters["projectId"],call.request.queryParameters["type"],call.request.queryParameters["from"],call.request.queryParameters["to"],call.request.queryParameters["q"],call.intQuery("limit",50,1,200),call.intQuery("offset",0,0,1_000_000)) }) }\n            get("/frontend-events") { val p=call.principal(auth,limiter); call.respond(blocking { part3.frontendEvents(p.accountId,call.intQuery("limit",100,1,200),call.intQuery("offset",0,0,1_000_000)) }) }\n            get("/student-model") { val p=call.principal(auth,limiter); call.respond(blocking { part3.studentModel(p.accountId,call.request.queryParameters["projectId"],call.intQuery("limit",200,1,500)) }) }\n            post("/student-model/signals") { val p=call.principal(auth,limiter); call.respond(HttpStatusCode.Created,blocking { part3.createSignal(p.accountId,call.receive()) }) }\n            post("/student-model/signals/{id}/correct") { val p=call.principal(auth,limiter); call.respond(blocking { part3.correctSignal(p.accountId,call.id(),call.receive()) }) }\n            post("/student-model/signals/{id}/state") { val p=call.principal(auth,limiter); call.respond(blocking { part3.setSignalState(p.accountId,call.id(),call.receive()) }) }\n            delete("/student-model/signals/{id}") { val p=call.principal(auth,limiter); val rev=call.request.queryParameters["expectedRevision"]?.toLongOrNull() ?: throw validation("expectedRevision required"); blocking { part3.deleteSignal(p.accountId,call.id(),rev) }; call.respond(ApiAck()) }\n            get("/personalization/recommendations") { val p=call.principal(auth,limiter); call.respond(blocking { part3.recommendations(p.accountId,call.request.queryParameters["projectId"],call.intQuery("limit",5,1,10)) }) }\n            get("/context-carry") { val p=call.principal(auth,limiter); val value=blocking { part3.getContextCarry(p.accountId) }; if(value==null) call.respond(HttpStatusCode.NoContent) else call.respond(value) }\n            put("/context-carry") { val p=call.principal(auth,limiter); call.respond(blocking { part3.putContextCarry(p.accountId,call.receive()) }) }\n            post("/commands/resolve") { val p=call.principal(auth,limiter); call.respond(blocking { part3.resolveCommand(p.accountId,call.receive()) }) }\n            get("/avatars/catalog") { val p=call.principal(auth,limiter); call.respond(blocking { part3.avatarCatalog(p.accountId) }) }\n            get("/seasons/history") { val p=call.principal(auth,limiter); call.respond(blocking { part3.seasonHistory(p.accountId,call.intQuery("limit",30,1,100)) }) }''',
)
replace_required(
    "server/src/main/kotlin/com/veltrix/hom/vnext/server/Main.kt",
    '                post("/units/{unitId}/start") { val p=call.principal(auth,limiter); val id=call.parameters["unitId"]?:throw validation("unitId required"); call.respond(blocking{game.startUnit(p.accountId,id,call.receive())}) }',
    '                post("/units/{unitId}/start") { val p=call.principal(auth,limiter); val id=call.parameters["unitId"]?:throw validation("unitId required"); call.respond(blocking{game.startUnit(p.accountId,id,call.receive())}) }\n                get("/units/{unitId}/stages") { val p=call.principal(auth,limiter); val id=call.parameters["unitId"]?:throw validation("unitId required"); call.respond(blocking { part3.mapStages(p.accountId,id) }) }',
)
replace_required(
    "server/src/main/kotlin/com/veltrix/hom/vnext/server/Main.kt",
    '            get("/project-templates") { call.principal(auth,limiter); call.respond(extensions.templates()) }',
    '            get("/project-templates") { call.principal(auth,limiter); call.respond(blocking { part3.templates() }) }',
)
replace_required(
    "server/src/main/kotlin/com/veltrix/hom/vnext/server/Main.kt",
    '                get("/{id}/workspace") { val p=call.principal(auth,limiter); call.respond(blocking{workspace.snapshot(p.accountId,call.id())}) }',
    '                get("/{id}/workspace") { val p=call.principal(auth,limiter); call.respond(blocking { part3.workspace(p.accountId,call.id()) }) }\n                put("/{id}/customization") { val p=call.principal(auth,limiter); call.respond(blocking { part3.customizeProject(p.accountId,call.id(),call.receive()) }) }',
)
replace_required(
    "server/src/main/kotlin/com/veltrix/hom/vnext/server/Main.kt",
    '                get("/{id}") { val p=call.principal(auth,limiter); call.respond(blocking{sources.get(p.accountId,call.id())}) }',
    '                get("/{id}") { val p=call.principal(auth,limiter); call.respond(blocking{sources.get(p.accountId,call.id())}) }\n                get("/{id}/relationships") { val p=call.principal(auth,limiter); call.respond(blocking { part3.sourceRelationships(p.accountId,call.id()) }) }\n                post("/{id}/relationships") { val p=call.principal(auth,limiter); call.respond(HttpStatusCode.Created,blocking { part3.createSourceRelationship(p.accountId,call.id(),call.receive()) }) }',
)

# Final long-term gate: preserve normal early levels while hard-gating Level 50 at 90 qualified days.
replace_required(
    "core/src/main/kotlin/com/veltrix/hom/vnext/core/Part3FinalSystems.kt",
    '''    fun maxLevelForQualifiedDays(days: Int): Int {\n        require(days >= 0)\n        if (days >= MIN_QUALIFIED_DAYS_LEVEL_50) return 50\n        return (1 + floor(days.toDouble() * 49.0 / MIN_QUALIFIED_DAYS_LEVEL_50).toInt()).coerceIn(1, 49)\n    }''',
    '''    fun maxLevelForQualifiedDays(days: Int): Int {\n        require(days >= 0)\n        return when {\n            days >= 90 -> 50\n            days >= 75 -> 49\n            days >= 60 -> 48\n            days >= 45 -> 46\n            days >= 30 -> 44\n            else -> 40\n        }\n    }''',
)
replace_required(
    "database/migrations/V007__part3_final_capability_expansion.sql",
    " SELECT CASE WHEN p_days>=90 THEN 50 ELSE GREATEST(1,LEAST(49,1+floor(GREATEST(p_days,0)::numeric*49/90)::int)) END::smallint $$;",
    " SELECT CASE WHEN p_days>=90 THEN 50 WHEN p_days>=75 THEN 49 WHEN p_days>=60 THEN 48 WHEN p_days>=45 THEN 46 WHEN p_days>=30 THEN 44 ELSE 40 END::smallint $$;",
)

# All final game-facing eligibility/requirements consume the effective profile level.
part2 = Path("server/src/main/kotlin/com/veltrix/hom/vnext/server/Part2GameRepository.kt")
text = part2.read_text(encoding="utf-8")
text = text.replace("val level=progression(c,accountId).second", "val level=effectiveLevel(c,accountId)")
text = text.replace("val p=progression(c,a);val eligibility=PersonalMapEligibilityEngine.evaluate(p.second,MemoryMaturityState.valueOf(maturity))", "val p=progression(c,a);val eligibility=PersonalMapEligibilityEngine.evaluate(effectiveLevel(c,a),MemoryMaturityState.valueOf(maturity))")
anchor = '    private fun progression(c:Connection,a:String):Triple<Long,Int,Long> ='
if "private fun effectiveLevel" not in text:
    if anchor not in text:
        raise SystemExit("Part2 effective-level insertion anchor missing")
    text = text.replace(anchor, '    private fun effectiveLevel(c:Connection,a:String):Int=c.prepareStatement("SELECT effective_level FROM progression_profile WHERE account_id=?::uuid").use{ps->ps.setString(1,a);ps.executeQuery().use{rs->rs.next();rs.getInt(1)}}\n' + anchor, 1)

# Stored projection may lag XP mutation paths; derive the authoritative gated level from level + qualified days at read time.
old_effective = '    private fun effectiveLevel(c:Connection,a:String):Int=c.prepareStatement("SELECT effective_level FROM progression_profile WHERE account_id=?::uuid").use{ps->ps.setString(1,a);ps.executeQuery().use{rs->rs.next();rs.getInt(1)}}'
new_effective = '    private fun effectiveLevel(c:Connection,a:String):Int=c.prepareStatement("SELECT LEAST(level,part3_max_level_for_days(qualified_active_days)) FROM progression_profile WHERE account_id=?::uuid").use{ps->ps.setString(1,a);ps.executeQuery().use{rs->rs.next();rs.getInt(1)}}'
if old_effective in text:
    text = text.replace(old_effective, new_effective, 1)
elif new_effective not in text:
    raise SystemExit("Part2 effective-level helper anchor missing")
part2.write_text(text, encoding="utf-8")

print("PART3_SERVER_PATCH=PASS")
