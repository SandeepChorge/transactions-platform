package com.madtitan94.transactionsparser.core.catalog

import com.google.android.gms.tasks.Task
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.madtitan94.transactionsparser.core.domain.catalog.BundledCategoryCatalog
import com.madtitan94.transactionsparser.core.domain.catalog.CategoryCatalog
import com.madtitan94.transactionsparser.core.domain.catalog.SuggestedCategorySource
import com.madtitan94.transactionsparser.core.domain.catalog.resolveCategoryCatalog
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Remote Config in front of the bundled catalog.
 *
 * The string parameter is [PARAM]. Its in-app default is the bundled JSON, so a fetch that has
 * never succeeded still returns a document that parses. The SDK's own minimum fetch interval is
 * left at the default of 12 hours — this class does not call `setConfigSettingsAsync`.
 *
 * A payload that does not parse never becomes the value this source emits, and never replaces the
 * last document that did. Remote Config activates whatever was published; the decision to trust it
 * is made here, after the parse.
 */
class RemoteConfigSuggestedCategorySource(
    private val remoteConfig: FirebaseRemoteConfig,
    private val store: DataStoreCategoryCatalogStore,
    private val scope: CoroutineScope
) : SuggestedCategorySource {

    private val bundled: CategoryCatalog = BundledCategoryCatalog.load()
    private val state = MutableStateFlow(bundled)

    init {
        scope.launch { refresh() }
    }

    override fun observe(): Flow<CategoryCatalog> = state.asStateFlow()

    private suspend fun refresh() {
        val bundledJson = BundledCategoryCatalog.json()
        try {
            remoteConfig.setDefaultsAsync(mapOf(PARAM to bundledJson)).awaitResult()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Defaults are a convenience. The bundled catalog is already what [state] holds.
        }
        publish(remoteConfig.getString(PARAM))
        try {
            remoteConfig.fetchAndActivate().awaitResult()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
        publish(remoteConfig.getString(PARAM))
    }

    private suspend fun publish(remoteJson: String) {
        val resolution = resolveCategoryCatalog(
            remoteJson = remoteJson,
            cachedJson = store.read(),
            bundled = bundled
        )
        resolution.persistRemoteJson?.let { store.write(it) }
        state.value = resolution.catalog
    }

    private companion object {
        const val PARAM = "default_categories"
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        val error = task.exception
        when {
            task.isSuccessful -> cont.resume(task.result)
            error != null -> cont.resumeWithException(error)
            else -> cont.resumeWithException(IllegalStateException("Remote Config task failed"))
        }
    }
}
