# OpenAPI — Final

`contracts/openapi.yaml` is generated/drift-checked against Ktor route truth. Final CI requires Ktor v1 operation count == OpenAPI operation count and `OPENAPI_DRIFT=PASS`; the verified pre-doc candidate had 148 operations.

Key Part 3 surfaces: `/home`, `/personal`, `/activity`, Student Model/personalization, `/context-carry`, `/commands/resolve`, `/frontend-events`, `/learning-modes`, Project workspace/customization/goal graph, assessment history/retest, search, avatar catalog, account export/delete and `/auth/google`.

Frontend should consume typed contracts rather than guess JSON fields.