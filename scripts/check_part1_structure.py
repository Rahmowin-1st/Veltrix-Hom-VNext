#!/usr/bin/env python3
from pathlib import Path
import subprocess

ROOT=Path(__file__).resolve().parents[1]
FROZEN="fe4b2528a0a7da550351d926d44cc025e49dcafc"
required=[
    "settings.gradle.kts",
    "core/src/main/kotlin/com/veltrix/hom/vnext/core/Part2GameSystems.kt",
    "server/src/main/kotlin/com/veltrix/hom/vnext/server/Main.kt",
    "server/src/main/kotlin/com/veltrix/hom/vnext/server/Part2GameRepository.kt",
    "database/migrations/V001__core_schema.sql",
    "database/migrations/V004__part1_intelligence_completion.sql",
    "android/app/src/main/kotlin/com/veltrix/hom/vnext/VeltrixApiClient.kt",
]
missing=[p for p in required if not (ROOT/p).is_file()]
if missing:
    raise SystemExit("missing required Part1/continuity files: "+", ".join(missing))
subprocess.run(["git","cat-file","-e",f"{FROZEN}^{{commit}}"],cwd=ROOT,check=True)
subprocess.run(["git","merge-base","--is-ancestor",FROZEN,"HEAD"],cwd=ROOT,check=True)
print("PART1_STRUCTURE_AND_ANCESTRY=PASS")
