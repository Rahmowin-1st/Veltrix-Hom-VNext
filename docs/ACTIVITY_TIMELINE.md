# Activity Timeline

`GET /activity` exposes bounded account activity as event ID, type, time, optional Project/object IDs, meaningful flag and optional deep link.

The timeline is a read model over durable backend events, not a second progression source of truth. Meaningful-event semantics remain in authoritative progression/event logic; Home/Personal can consume recent activity for context.