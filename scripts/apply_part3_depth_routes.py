#!/usr/bin/env python3
from pathlib import Path
import re

main=Path('server/src/main/kotlin/com/veltrix/hom/vnext/server/Main.kt')
text=main.read_text(encoding='utf-8')

def replace_once(old,new,label):
    global text
    if new in text:
        return
    if old not in text:
        raise SystemExit(f'missing Main anchor: {label}')
    text=text.replace(old,new,1)

replace_once(
    '    val accountData = AccountDataRepository(db)\n    val timeline = ActivityTimelineRepository(db)',
    '    val accountData = AccountDataRepository(db)\n    val accountDeletionWorker = AccountDeletionWorker(db,config.workerEnabled)\n    environment.monitor.subscribe(ApplicationStopped) { accountDeletionWorker.close() }\n    val timeline = ActivityTimelineRepository(db)',
    'account deletion worker',
)

# Replace static legacy mode list with the versioned database-backed Part 3 definitions.
pattern=r'''            get\("/learning-modes"\) \{.*?\n            \}\n            get\("/account/export"\) \{ val p=call\.principal\(auth,limiter\); call\.respond\(blocking\{accountData\.export\(p\.accountId\)\}\) \}'''
replacement='''            get("/learning-modes") { call.principal(auth,limiter); call.respond(blocking { part3.learningModes() }) }\n            get("/account/export") { val p=call.principal(auth,limiter); call.respond(blocking { part3.accountExport(p.accountId) }) }'''
if replacement not in text:
    text2,n=re.subn(pattern,replacement,text,count=1,flags=re.S)
    if n!=1:
        raise SystemExit('missing/ambiguous learning-mode + account-export anchor')
    text=text2

old_goal='''                delete("/{id}/goals/{goalId}") { val p=call.principal(auth,limiter); val rev=call.request.queryParameters["expectedRevision"]?.toLongOrNull() ?: throw validation("expectedRevision required"); blocking{extensions.deleteGoal(p.accountId,call.parameters["id"]!!,call.parameters["goalId"]!!,rev)};call.respond(ApiAck()) }'''
new_goal=old_goal+'''\n                get("/{id}/goals/{goalId}/dependencies") { val p=call.principal(auth,limiter); call.respond(blocking { part3.goalDependencies(p.accountId,call.parameters["id"]!!,call.parameters["goalId"]!!) }) }\n                post("/{id}/goals/{goalId}/dependencies") { val p=call.principal(auth,limiter); call.respond(HttpStatusCode.Created,blocking { part3.addGoalDependency(p.accountId,call.parameters["id"]!!,call.parameters["goalId"]!!,call.receive()) }) }\n                delete("/{id}/goals/{goalId}/dependencies/{dependsOnGoalId}") { val p=call.principal(auth,limiter); blocking { part3.removeGoalDependency(p.accountId,call.parameters["id"]!!,call.parameters["goalId"]!!,call.parameters["dependsOnGoalId"]!!) }; call.respond(ApiAck()) }\n                get("/{id}/goals/{goalId}/links") { val p=call.principal(auth,limiter); call.respond(blocking { part3.goalLinks(p.accountId,call.parameters["id"]!!,call.parameters["goalId"]!!) }) }\n                post("/{id}/goals/{goalId}/links") { val p=call.principal(auth,limiter); call.respond(HttpStatusCode.Created,blocking { part3.addGoalLink(p.accountId,call.parameters["id"]!!,call.parameters["goalId"]!!,call.receive()) }) }\n                post("/{id}/goal-suggestions") { val p=call.principal(auth,limiter); call.respond(HttpStatusCode.Created,blocking { part3.proposeGoalSuggestion(p.accountId,call.parameters["id"]!!,call.receive()) }) }\n                post("/{id}/goal-suggestions/{suggestionId}/decision") { val p=call.principal(auth,limiter); call.respond(blocking { part3.decideGoalSuggestion(p.accountId,call.parameters["id"]!!,call.parameters["suggestionId"]!!,call.receive()) }) }'''
replace_once(old_goal,new_goal,'goal depth routes')

old_assessment='''                post("/{id}/attempts") { val p=call.principal(auth,limiter); call.respond(HttpStatusCode.Created,blocking{assessments.startAttempt(p.accountId,call.id())}) }'''
new_assessment=old_assessment+'''\n                get("/{id}/history") { val p=call.principal(auth,limiter); call.respond(blocking { part3.assessmentHistory(p.accountId,call.id(),call.intQuery("limit",50,1,200)) }) }\n                post("/{id}/retest") { val p=call.principal(auth,limiter); call.respond(HttpStatusCode.Created,blocking { part3.startRetest(p.accountId,call.id(),call.receive()) }) }'''
replace_once(old_assessment,new_assessment,'assessment history routes')
main.write_text(text,encoding='utf-8')

settings=Path('server/src/main/kotlin/com/veltrix/hom/vnext/server/SettingsAccountRepositories.kt')
s=settings.read_text(encoding='utf-8')
old='''            c.prepareStatement("UPDATE device_session SET revoked_at=COALESCE(revoked_at,now()) WHERE account_id=?::uuid").use { ps -> ps.setString(1,accountId);ps.executeUpdate() }'''
new=old+'''\n            c.prepareStatement("INSERT INTO account_deletion_lifecycle(account_id,account_ref_hash,state,purge_after) VALUES (?::uuid,?,'PURGE_PENDING',now())").use { ps -> ps.setString(1,accountId);ps.setString(2,sha256(accountId));ps.executeUpdate() }'''
if new not in s:
    if old not in s:
        raise SystemExit('missing account deletion lifecycle anchor')
    s=s.replace(old,new,1)
settings.write_text(s,encoding='utf-8')
print('PART3_DEPTH_ROUTE_PATCH=PASS')
