package com.madtitan94.transactionsparser.feature.profile.presentation

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.madtitan94.transactionsparser.core.domain.datasource.SessionStorage
import com.madtitan94.transactionsparser.core.domain.model.UserSession
import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.EmptyResult
import com.madtitan94.transactionsparser.core.domain.util.Result
import com.madtitan94.transactionsparser.feature.profile.domain.ProfileStorage
import com.madtitan94.transactionsparser.feature.profile.domain.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeSessionStorage(
        initial: UserSession? = UserSession("g1", "Sandeep", "sandeep@example.com", null)
    ) : SessionStorage {
        val session = MutableStateFlow(initial)
        var cleared = false

        override fun observeSession(): Flow<UserSession?> = session

        override suspend fun save(session: UserSession): EmptyResult<DataError.Local> {
            this.session.value = session
            return Result.Success(Unit)
        }

        override suspend fun clear(): EmptyResult<DataError.Local> {
            cleared = true
            session.value = null
            return Result.Success(Unit)
        }
    }

    private class FakeProfileStorage : ProfileStorage {
        val profile = MutableStateFlow<UserProfile?>(null)

        override fun observeProfile(): Flow<UserProfile?> = profile

        override suspend fun save(profile: UserProfile): EmptyResult<DataError.Local> {
            this.profile.value = profile
            return Result.Success(Unit)
        }
    }

    @Test
    fun `logout click opens confirm dialog without clearing the session`() = runTest {
        val sessionStorage = FakeSessionStorage()
        val viewModel = ProfileViewModel(SavedStateHandle(), sessionStorage, FakeProfileStorage())

        viewModel.state.test {
            skipItems(1)

            viewModel.onAction(ProfileAction.OnLogoutClick)

            assertThat(awaitItem().showLogoutConfirm).isTrue()
        }
        assertThat(sessionStorage.cleared).isFalse()
    }

    @Test
    fun `confirming logout clears the session and closes the dialog`() = runTest {
        val sessionStorage = FakeSessionStorage()
        val viewModel = ProfileViewModel(SavedStateHandle(), sessionStorage, FakeProfileStorage())

        viewModel.onAction(ProfileAction.OnLogoutClick)

        viewModel.state.test {
            skipItems(1)

            viewModel.onAction(ProfileAction.OnConfirmLogout)

            // Confirming also clears the session, which triggers a second, separate
            // state emission from observeStoredData() reacting to the cleared session.
            assertThat(expectMostRecentItem().showLogoutConfirm).isFalse()
        }
        assertThat(sessionStorage.cleared).isTrue()
    }

    @Test
    fun `dismissing logout confirm keeps the session`() = runTest {
        val sessionStorage = FakeSessionStorage()
        val viewModel = ProfileViewModel(SavedStateHandle(), sessionStorage, FakeProfileStorage())

        viewModel.onAction(ProfileAction.OnLogoutClick)

        viewModel.state.test {
            skipItems(1)

            viewModel.onAction(ProfileAction.OnDismissLogoutConfirm)

            assertThat(awaitItem().showLogoutConfirm).isFalse()
        }
        assertThat(sessionStorage.cleared).isFalse()
    }
}
