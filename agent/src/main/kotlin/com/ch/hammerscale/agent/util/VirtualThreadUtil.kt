package com.ch.hammerscale.agent.util

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors

object VirtualThreadUtil {

    private val defaultExecutor: Executor = Executors.newVirtualThreadPerTaskExecutor()

    fun <T> runBlocking(
        block: () -> T
    ): CompletableFuture<T> {
        return CompletableFuture.supplyAsync(block, defaultExecutor)
    }

    fun <T> runBlocking(
        executor: Executor,
        block: () -> T
    ): CompletableFuture<T> {
        return CompletableFuture.supplyAsync(block, executor)
    }

    fun createVirtualThread(
        name: String,
        task: Runnable
    ): Thread {
        return Thread.ofVirtual()
            .name(name)
            .start(task)
    }

    fun <T> runAll(
        vararg tasks: () -> T
    ): CompletableFuture<List<T>> {
        val futures = tasks.map { runBlocking(it) }

        return CompletableFuture.allOf(*futures.toTypedArray())
            .thenApply { futures.map { it.get() } }
    }
}

