package com.madtitan94.transactionsparser.feature.settings.data

import assertk.assertThat
import assertk.assertions.*
import com.madtitan94.transactionsparser.core.domain.datasource.LocalDataCleaner
import com.madtitan94.transactionsparser.core.domain.datasource.LocalDataWork
import com.madtitan94.transactionsparser.core.domain.datasource.SessionStorage
import com.madtitan94.transactionsparser.core.domain.model.UserSession
import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.EmptyResult
import com.madtitan94.transactionsparser.core.domain.util.Result
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LocalDeleteAccountServiceTest {
    private class Session : SessionStorage {
        val current = MutableStateFlow<UserSession?>(UserSession("test", "Test", "test@example.com", null))
        var failClear = false
        override fun observeSession() = current
        override suspend fun save(session: UserSession): EmptyResult<DataError.Local> {
            current.value = session
            return Result.Success(Unit)
        }
        override suspend fun clear(): EmptyResult<DataError.Local> {
            if (failClear) return Result.Error(DataError.Local.UNKNOWN)
            current.value = null
            return Result.Success(Unit)
        }
    }

    @Test
    fun `login becomes available only after every cleaner succeeds`() = runTest {
        val session = Session()
        val calls = mutableListOf<Int>()
        val service = LocalDeleteAccountService((1..3).map { index ->
            LocalDataCleaner {
                assertThat(session.current.value).isNotNull()
                calls += index
                Result.Success(Unit)
            }
        }, session)
        assertThat(service.deleteAccount()).isInstanceOf(Result.Success::class)
        assertThat(calls).containsExactly(1, 2, 3)
        assertThat(session.current.value).isNull()
    }

    @Test
    fun `failed cleanup retains the session and can be retried`() = runTest {
        val session = Session()
        var fail = true
        var laterCalled = false
        val service = LocalDeleteAccountService(listOf(
            LocalDataCleaner { if (fail) Result.Error(DataError.Local.UNKNOWN) else Result.Success(Unit) },
            LocalDataCleaner { laterCalled = true; Result.Success(Unit) }
        ), session)
        assertThat(service.deleteAccount()).isInstanceOf(Result.Error::class)
        assertThat(session.current.value).isNotNull()
        assertThat(laterCalled).isFalse()
        fail = false
        assertThat(service.deleteAccount()).isInstanceOf(Result.Success::class)
        assertThat(laterCalled).isTrue()
        assertThat(session.current.value).isNull()
    }

    @Test
    fun `session write failure is not reported as successful deletion`() = runTest {
        val session = Session().apply { failClear = true }
        val service = LocalDeleteAccountService(listOf(LocalDataCleaner { Result.Success(Unit) }), session)
        assertThat(service.deleteAccount()).isInstanceOf(Result.Error::class)
        assertThat(session.current.value).isNotNull()
        session.failClear = false
        assertThat(service.deleteAccount()).isInstanceOf(Result.Success::class)
        assertThat(session.current.value).isNull()
    }

    @Test
    fun `leaving the screen cannot cancel a confirmed partial deletion`() = runTest {
        val session = Session()
        val started = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val service = LocalDeleteAccountService(listOf(LocalDataCleaner {
            started.complete(Unit)
            resume.await()
            Result.Success(Unit)
        }), session)
        val job = launch { service.deleteAccount() }
        started.await()
        job.cancel()
        assertThat(session.current.value).isNotNull()
        resume.complete(Unit)
        job.join()
        assertThat(session.current.value).isNull()
    }

    @Test
    fun `concurrent deletion attempts do not interleave stores`() = runTest {
        val session = Session()
        val started = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        var calls = 0
        val service = LocalDeleteAccountService(listOf(LocalDataCleaner {
            calls++
            started.complete(Unit)
            resume.await()
            Result.Success(Unit)
        }), session)
        val first = launch { service.deleteAccount() }
        started.await()
        val second = launch { service.deleteAccount() }
        runCurrent()
        assertThat(calls).isEqualTo(1)
        resume.complete(Unit)
        first.join()
        second.join()
        assertThat(calls).isEqualTo(2)
    }
    @Test
    fun `deletion waits for cancelled imports to finish cleanup before clearing stores`() = runTest {
        val work = LocalDataWork()
        val session = Session()
        val started = CompletableDeferred<Unit>()
        val finishCleanup = CompletableDeferred<Unit>()
        var importFinished = false
        var storeCleared = false
        work.launchIn(backgroundScope) {
            try {
                started.complete(Unit)
                awaitCancellation()
            } finally {
                withContext(NonCancellable) { finishCleanup.await() }
                importFinished = true
            }
        }
        started.await()
        val service = LocalDeleteAccountService(listOf(work, LocalDataCleaner {
            assertThat(importFinished).isTrue()
            storeCleared = true
            Result.Success(Unit)
        }), session)
        val deletion = launch { service.deleteAccount() }
        runCurrent()
        assertThat(storeCleared).isFalse()
        assertThat(session.current.value).isNotNull()
        finishCleanup.complete(Unit)
        deletion.join()
        assertThat(storeCleared).isTrue()
        assertThat(session.current.value).isNull()
    }

}
