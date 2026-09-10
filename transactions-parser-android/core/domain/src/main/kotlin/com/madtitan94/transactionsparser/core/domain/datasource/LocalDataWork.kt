package com.madtitan94.transactionsparser.core.domain.datasource

import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.EmptyResult
import com.madtitan94.transactionsparser.core.domain.util.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/** Tracks imports that can keep writing after their tab is left. Deletion cancels and joins them. */
class LocalDataWork : LocalDataCleaner {
    private val jobs = mutableSetOf<Job>()

    fun launchIn(scope: CoroutineScope, block: suspend CoroutineScope.() -> Unit): Job {
        val job = scope.launch(start = CoroutineStart.LAZY, block = block)
        synchronized(jobs) { jobs.add(job) }
        job.invokeOnCompletion { synchronized(jobs) { jobs.remove(job) } }
        job.start()
        return job
    }

    override suspend fun clearLocalData(): EmptyResult<DataError.Local> {
        val active = synchronized(jobs) { jobs.toList() }
        active.forEach { it.cancel() }
        active.joinAll()
        return Result.Success(Unit)
    }
}
