package com.yoon.js.appsearch.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.yoon.js.appsearch.data.share.ShareCoordinator
import com.yoon.js.appsearch.ui.history.HistoryScreen
import com.yoon.js.appsearch.ui.inject.InjectScreen
import com.yoon.js.appsearch.ui.search.SearchScreen
import com.yoon.js.appsearch.ui.share.ShareProcessingScreen

object AppRoutes {
    const val SEARCH = "search"
    const val INJECT = "inject"
    const val HISTORY = "history"
    const val SHARE_PROCESSING = "share_processing"
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    shareCoordinator: ShareCoordinator,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        shareCoordinator.pendingShare.collect { payload ->
            if (payload != null) {
                navController.navigate(AppRoutes.SHARE_PROCESSING) {
                    launchSingleTop = true
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = AppRoutes.SEARCH,
        modifier = modifier,
    ) {
        composable(AppRoutes.SEARCH) {
            SearchScreen(
                onNavigateToInject = {
                    navController.navigate(AppRoutes.INJECT)
                },
                onNavigateToHistory = {
                    navController.navigate(AppRoutes.HISTORY)
                },
            )
        }
        composable(AppRoutes.INJECT) {
            InjectScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(AppRoutes.HISTORY) {
            HistoryScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(AppRoutes.SHARE_PROCESSING) {
            ShareProcessingScreen(
                onNavigateToHistory = {
                    navController.navigate(AppRoutes.HISTORY) {
                        popUpTo(AppRoutes.SEARCH) { inclusive = false }
                    }
                },
                onNavigateToSearch = {
                    navController.popBackStack()
                },
            )
        }
    }
}
