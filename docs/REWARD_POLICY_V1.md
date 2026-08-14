# Reward Policy V1

> **Part 2 current-state document.** The exact acceptance SHA/run/job are bound in the canonical package `PROVENANCE.txt`. Preserved Android runtime evidence is from product SHA `2e70908f65bb25d23e87a9ddc690b8ea09f3040e`, run `31787591097`, job `94726891516`; Manager-acceptance deltas are reverified on the final handoff SHA.

Version `reward-v1`. Global daily hard caps are 450 XP and 90 Coins; daily qualified bonus is 20 XP / 5 Coins. Supported meaningful event rules and exact base rewards/soft+hard daily limits are exported in `progression-policy-v1.json`.

The classifier explicitly excludes app opens, navigation, refresh/retry/background noise. Semantic evidence and object identity are required where applicable. Repeated eligible events past soft limits receive a 0.5 multiplier; hard category or global caps reject further reward. Server reward decisions are auditable in `reward_decision_log`.

Executed tests cover trivial-event rejection, missing evidence, semantic duplicate rejection, diminishing rewards, category caps and global daily caps.
