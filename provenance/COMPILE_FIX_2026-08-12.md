# Part 1 compile repair provenance

Source audit run `31572422651` exposed compile defects that deterministic script-only tests could not detect.

Root-cause repairs applied:
- Kotlin generic return declarations no longer tokenize `>=` accidentally in ChatIntelligenceRepository and GeneratedArtifactService.
- the blocking IO helper now accepts a suspend lambda so Ktor `call.receive()` is not illegally invoked from a non-suspend lambda;
- DeepPractice hint requires a non-null expected answer through the stable practice-state error path;
- DeepPractice completion maps the returned JDBC row through `SessionRow` before `PracticeResponse` mapping.

This commit exists to make the repair provenance explicit and to trigger a fresh remote source audit on the repaired completion branch.
