package com.example.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.example.R
import com.example.data.local.ThemeMode
import com.example.ui.screens.ArticleDetailScreen
import com.example.ui.screens.FeedScreen
import com.example.ui.screens.SavedScreen
import com.example.ui.screens.SourcesScreen
import com.example.ui.theme.FolksWireTheme
import com.example.ui.viewmodel.NewsViewModel

object FolksWireRoutes {
    const val FEED = "feed"
    const val SAVED = "saved"
    const val SOURCES = "sources"
    const val ARTICLE = "article/{articleId}"

    fun articleRoute(articleId: String): String = "article/$articleId"
}

private data class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topLevelDestinations = listOf(
    TopLevelDestination(
        route = FolksWireRoutes.FEED,
        labelRes = R.string.nav_feed,
        selectedIcon = Icons.Filled.DynamicFeed,
        unselectedIcon = Icons.Outlined.DynamicFeed,
        testTag = "nav_feed"
    ),
    TopLevelDestination(
        route = FolksWireRoutes.SAVED,
        labelRes = R.string.nav_saved,
        selectedIcon = Icons.Filled.Bookmark,
        unselectedIcon = Icons.Outlined.BookmarkBorder,
        testTag = "nav_saved"
    ),
    TopLevelDestination(
        route = FolksWireRoutes.SOURCES,
        labelRes = R.string.nav_sources,
        selectedIcon = Icons.Filled.RssFeed,
        unselectedIcon = Icons.Outlined.RssFeed,
        testTag = "nav_sources"
    )
)

@Composable
fun FolksWireApp(
    newsViewModel: NewsViewModel = viewModel()
) {
    val uiState by newsViewModel.uiState.collectAsStateWithLifecycle()
    val articlesState by newsViewModel.articlesState.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = when (uiState.preferences.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    FolksWireTheme(darkTheme = useDarkTheme) {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        val isTopLevel = topLevelDestinations.any { dest ->
            currentDestination?.hierarchy?.any { it.route == dest.route } == true
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 640.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    if (isTopLevel && !useNavRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp
                        ) {
                            topLevelDestinations.forEach { dest ->
                                val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(dest.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        DestinationIcon(
                                            dest = dest,
                                            selected = selected,
                                            bookmarkCount = uiState.bookmarks.size
                                        )
                                    },
                                    label = { Text(stringResource(dest.labelRes)) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isTopLevel && useNavRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            topLevelDestinations.forEach { dest ->
                                val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(dest.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        DestinationIcon(
                                            dest = dest,
                                            selected = selected,
                                            bookmarkCount = uiState.bookmarks.size
                                        )
                                    },
                                    label = { Text(stringResource(dest.labelRes)) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = FolksWireRoutes.FEED,
                        modifier = Modifier.weight(1f)
                    ) {
                        composable(FolksWireRoutes.FEED) {
                            FeedScreen(
                                uiState = uiState,
                                articlesState = articlesState,
                                onRefreshAll = { newsViewModel.refreshAll() },
                                onRefreshSource = { newsViewModel.refreshSource(it) },
                                onUpdateSourceUrl = { id, url -> newsViewModel.updateSourceUrl(id, url) },
                                onSelectProvider = { newsViewModel.setProviderFilter(it) },
                                onSelectCategory = { newsViewModel.setCategoryFilter(it) },
                                onSelectHashtag = { newsViewModel.setHashtagFilter(it) },
                                onSearchQueryChange = { newsViewModel.setSearchQuery(it) },
                                onToggleSearch = { newsViewModel.setSearchActive(it) },
                                onClearFilters = { newsViewModel.clearAllFilters() },
                                onArticleClick = { article ->
                                    navController.navigate(FolksWireRoutes.articleRoute(article.id))
                                },
                                onToggleBookmark = { newsViewModel.toggleBookmark(it) }
                            )
                        }

                        composable(FolksWireRoutes.SAVED) {
                            SavedScreen(
                                bookmarks = uiState.bookmarks,
                                onArticleClick = { article ->
                                    navController.navigate(FolksWireRoutes.articleRoute(article.id))
                                },
                                onToggleBookmark = { newsViewModel.toggleBookmark(it) },
                                onHashtagClick = { tag ->
                                    newsViewModel.applyHashtagFromReader(tag)
                                    navController.navigate(FolksWireRoutes.FEED) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }

                        composable(FolksWireRoutes.SOURCES) {
                            SourcesScreen(
                                sources = uiState.sources,
                                preferences = uiState.preferences,
                                onToggleSource = { id, enabled -> newsViewModel.setSourceEnabled(id, enabled) },
                                onRefreshSource = { newsViewModel.refreshSource(it) },
                                onUpdateSourceUrl = { id, url -> newsViewModel.updateSourceUrl(id, url) },
                                onAddCustomSource = { name, url, cat -> newsViewModel.addCustomSource(name, url, cat) },
                                onDeleteCustomSource = { newsViewModel.deleteCustomSource(it) },
                                onSetThemeMode = { newsViewModel.setThemeMode(it) },
                                onSetFontScale = { newsViewModel.setFontScale(it) },
                                onClearCache = { newsViewModel.clearCachedArticles() }
                            )
                        }

                        composable(
                            route = FolksWireRoutes.ARTICLE,
                            arguments = listOf(navArgument("articleId") { type = NavType.StringType }),
                            deepLinks = listOf(
                                navDeepLink { uriPattern = "folkswire://#/article/{articleId}" }
                            )
                        ) { backStackEntry ->
                            val articleId = backStackEntry.arguments?.getString("articleId").orEmpty()
                            val article by newsViewModel.observeArticle(articleId)
                                .collectAsStateWithLifecycle(initialValue = null)

                            ArticleDetailScreen(
                                article = article,
                                fontScale = uiState.preferences.fontScale,
                                onBack = { navController.popBackStack() },
                                onMarkAsRead = { newsViewModel.markAsRead(it) },
                                onToggleBookmark = { newsViewModel.toggleBookmark(it) },
                                onCycleFontScale = { newsViewModel.setFontScale(it) },
                                onHashtagClick = { tag ->
                                    newsViewModel.applyHashtagFromReader(tag)
                                    navController.navigate(FolksWireRoutes.FEED) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationIcon(
    dest: TopLevelDestination,
    selected: Boolean,
    bookmarkCount: Int
) {
    val icon = if (selected) dest.selectedIcon else dest.unselectedIcon
    if (dest.route == FolksWireRoutes.SAVED && bookmarkCount > 0) {
        BadgedBox(
            badge = {
                Badge {
                    Text(bookmarkCount.coerceAtMost(99).toString())
                }
            }
        ) {
            Icon(imageVector = icon, contentDescription = stringResource(dest.labelRes))
        }
    } else {
        Icon(imageVector = icon, contentDescription = stringResource(dest.labelRes))
    }
}
