package com.madtitan94.transactionsparser.feature.upload.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.madtitan94.transactionsparser.feature.upload.presentation.UploadRoot
import kotlinx.serialization.Serializable

@Serializable
data object UploadRoute

fun NavGraphBuilder.uploadGraph(
    onOpenSession: (Long) -> Unit
) {
    composable<UploadRoute> {
        UploadRoot(onOpenSession = onOpenSession)
    }
}
