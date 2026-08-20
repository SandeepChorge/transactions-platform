package com.madtitan94.transactionsparser.core.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * What came back for one permission.
 *
 * [DENIED_PERMANENTLY] is the case worth separating: a plain denial can be asked about again,
 * but once the system stops showing the prompt the only way forward is app settings, and a
 * feature that keeps re-launching a request that will never appear looks broken.
 */
enum class PermissionOutcome { GRANTED, DENIED, DENIED_PERMANENTLY }

@Immutable
class PermissionRequester internal constructor(
    private val launch: (Array<String>) -> Unit
) {
    fun request(vararg permissions: String) {
        if (permissions.isEmpty()) return
        launch(arrayOf(*permissions))
    }
}

/**
 * One reusable permission launcher, so a feature states which permissions it needs and reads a
 * three-way outcome per permission instead of hand-rolling a launcher and rationale check.
 *
 * Nothing in the app needs a runtime permission today — CSV export goes through the Storage
 * Access Framework, where the system picker is itself the grant. This exists so the next feature
 * that does need one (camera, notifications) has somewhere to plug in.
 */
@Composable
fun rememberPermissionRequester(
    onResult: (Map<String, PermissionOutcome>) -> Unit
): PermissionRequester {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        onResult(
            granted.mapValues { (permission, isGranted) ->
                when {
                    isGranted -> PermissionOutcome.GRANTED
                    // Checked only after a result: before the first ask this is also false, which
                    // would misreport a never-requested permission as permanently denied.
                    activity?.shouldShowRequestPermissionRationale(permission) == true ->
                        PermissionOutcome.DENIED
                    else -> PermissionOutcome.DENIED_PERMANENTLY
                }
            }
        )
    }

    return remember(launcher) { PermissionRequester { launcher.launch(it) } }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
