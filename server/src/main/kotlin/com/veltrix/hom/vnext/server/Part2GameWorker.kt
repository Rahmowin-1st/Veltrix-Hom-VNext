package com.veltrix.hom.vnext.server

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class Part2GameWorker(private val game:Part2GameRepository, enabled:Boolean):AutoCloseable {
    private val executor=Executors.newSingleThreadScheduledExecutor{r->Thread(r,"veltrix-part2-game-worker").apply{isDaemon=true}}
    init { if(enabled) executor.scheduleWithFixedDelay({runCatching{game.processPending(50);game.reconcileSeasons()}},1,2,TimeUnit.SECONDS) }
    override fun close(){executor.shutdownNow()}
}
