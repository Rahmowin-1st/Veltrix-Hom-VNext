# Personalization Engine

`Part3StudentRepository.recommendations` filters by account, optional Project scope, signal state and confidence. Reason categories include GOAL, MISTAKE, PERFORMANCE, PROJECT_FOCUS, RECENT_CONTEXT and DUE_REVIEW. Responses carry action/target/reason/evidence/confidence/expiry/state.

Home priority and insight policy are deterministic core engines. AI may improve wording or propose content; it cannot decide permissions, rewards, ownership or irreversible effects.

Project recommendations may use global + same-project evidence. Global recommendations do not pull unrelated Project signals.