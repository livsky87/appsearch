package com.yoon.js.appsearch.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.yoon.js.appsearch.ui.history.HistoryScreen
import com.yoon.js.appsearch.ui.history.SourceDetailScreen
import com.yoon.js.appsearch.ui.inject.InjectScreen
import com.yoon.js.appsearch.ui.search.SearchScreen

object AppRoutes {
    const val SEARCH = "search"
    const val INJECT = "inject"
    const val HISTORY = "history"
    const val HISTORY_DETAIL = "history/{sourceId}"

    fun historyDetail(sourceId: Long) = "history/$sourceId"
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
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
                onSourceClick = { sourceId ->
                    navController.navigate(AppRoutes.historyDetail(sourceId))
                },
            )
        }
        composable(
            route = AppRoutes.HISTORY_DETAIL,
            arguments = listOf(
                navArgument("sourceId") { type = NavType.LongType },
            ),
        ) {
            SourceDetailScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
