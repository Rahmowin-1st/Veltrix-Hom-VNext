# Performance — Final

Claims are bounded to executed CI smoke/regression evidence, not production SLO certification. The verified pre-doc final gate recorded profile smoke p50 7.51 ms, p95 11.50 ms and p99 12.56 ms.

Controls include bounded Home/Personal/Workspace aggregates, indexed hot queries, short DB transactions, background source/memory work, bounded pagination/limits and no intended DB/network work on Android main thread.

Production traffic, provider latency, device diversity and capacity require deployment-specific SLO/load testing.