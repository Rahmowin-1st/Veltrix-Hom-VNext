# Assessment Engine

Assessments are account/Project-owned definitions with attempts, answers, submit/grade lifecycle, history and retest support. Part 3 adds deadline, duration and `retest_of_attempt_id`, plus `assessment_comparison` for graded attempts.

`/assessments/{id}/history` returns bounded history/best/latest/improvement; `/retest` starts a linked timed retest. Results may feed performance/mistake signals and meaningful activity. AI may draft questions/explanations, but attempt state/scoring/history remain backend-owned.