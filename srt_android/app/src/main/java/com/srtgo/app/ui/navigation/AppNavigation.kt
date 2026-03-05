package com.srtgo.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Train
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.srtgo.app.ui.screen.history.HistoryScreen
import com.srtgo.app.ui.screen.macro.MacroScreen
import com.srtgo.app.ui.screen.reservations.ReservationsScreen
import com.srtgo.app.ui.screen.result.ResultScreen
import com.srtgo.app.ui.screen.search.SearchScreen
import com.srtgo.app.ui.screen.settings.SettingsScreen

sealed class Screen(val route: String) {
    data object Search : Screen("search")
    data object Result : Screen("result/{searchParamsJson}") {
        fun createRoute(searchParamsJson: String): String =
            "result/${java.net.URLEncoder.encode(searchParamsJson, "UTF-8")}"
    }
    data object Macro : Screen("macro/{macroConfigJson}") {
        fun createRoute(macroConfigJson: String): String =
            "macro/${java.net.URLEncoder.encode(macroConfigJson, "UTF-8")}"
    }
    data object Reservations : Screen("reservations")
    data object History : Screen("history")
    data object Settings : Screen("settings")
}

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val screen: Screen
)

val bottomNavItems = listOf(
    BottomNavItem("검색", Icons.Default.Search, Screen.Search),
    BottomNavItem("예약", Icons.Default.Train, Screen.Reservations),
    BottomNavItem("이력", Icons.Default.History, Screen.History),
    BottomNavItem("설정", Icons.Default.Settings, Screen.Settings)
)

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Search.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Search.route) {
            SearchScreen(
                onNavigateToResult = { searchParamsJson ->
                    navController.navigate(Screen.Result.createRoute(searchParamsJson))
                }
            )
        }

        composable(
            route = Screen.Result.route,
            arguments = listOf(
                navArgument("searchParamsJson") { type = NavType.StringType }
            )
        ) {
            ResultScreen(
                onNavigateToMacro = { macroConfigJson ->
                    navController.navigate(Screen.Macro.createRoute(macroConfigJson))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Macro.route,
            arguments = listOf(
                navArgument("macroConfigJson") { type = NavType.StringType }
            )
        ) {
            MacroScreen(
                onNavigateBack = {
                    navController.popBackStack(Screen.Search.route, inclusive = false)
                }
            )
        }

        composable(Screen.Reservations.route) {
            ReservationsScreen()
        }

        composable(Screen.History.route) {
            HistoryScreen()
        }

        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}
