package com.materialy.music.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import com.materialy.music.ui.components.bouncy
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Waves
import com.materialy.music.ui.screens.wave.MyWaveScreen
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.materialy.music.ui.components.CustomToastHost
import com.materialy.music.ui.components.MiniPlayer
import com.materialy.music.ui.screens.download.DownloadScreen
import com.materialy.music.ui.screens.home.HomeScreen
import com.materialy.music.ui.screens.library.LibraryScreen
import com.materialy.music.ui.screens.player.PlayerScreen
import com.materialy.music.ui.screens.player.PlayerViewModel
import com.materialy.music.ui.screens.player.QueueScreen
import com.materialy.music.ui.screens.playlist.PlaylistScreen
import com.materialy.music.ui.screens.search.SearchScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.materialy.music.ui.screens.album.AlbumDetailScreen
import com.materialy.music.ui.screens.artist.ArtistDetailScreen
import com.materialy.music.ui.screens.settings.SettingsScreen
import com.materialy.music.ui.screens.settings.DiagnosticsScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Screen("home", "Главная", Icons.Filled.Home)
    data object MyWave : Screen("my_wave", "Моя Волна", Icons.Filled.Waves)
    data object Search : Screen("search", "Поиск", Icons.Filled.Search)
    data object Library : Screen("library", "Медиатека", Icons.Filled.LibraryMusic)
    data object Download : Screen("download", "Загрузки", Icons.Filled.CloudDownload)
    data object Playlists : Screen("playlists", "Плейлисты", Icons.AutoMirrored.Filled.QueueMusic)
    data object AlbumDetail : Screen("album/{albumName}", "Альбом", Icons.Filled.Album) {
        fun createRoute(albumName: String) = "album/${java.net.URLEncoder.encode(albumName, "UTF-8")}"
    }
    data object ArtistDetail : Screen("artist/{artistName}", "Исполнитель", Icons.Filled.Album) {
        fun createRoute(artistName: String) = "artist/${java.net.URLEncoder.encode(artistName, "UTF-8")}"
    }
    data object Player : Screen("player", "Плеер", Icons.Filled.Album)
    data object Queue : Screen("queue", "Очередь", Icons.AutoMirrored.Filled.QueueMusic)
    data object Settings : Screen("settings", "Настройки", Icons.Filled.Settings)
    data object Diagnostics : Screen("diagnostics", "Диагностика", Icons.Filled.Settings)
}

@Composable
fun AppNavHost(
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val nav = rememberNavController()
    val navBackStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // 4 Primary Tabs in Bottom Navigation Bar
    val tabItems = listOf(Screen.Home, Screen.MyWave, Screen.Search, Screen.Library)
    val isPlayerScreen = currentRoute == Screen.Player.route

    fun navigateToTab(targetRoute: String) {
        val startDestId = nav.graph.findStartDestination().id
        nav.navigate(targetRoute) {
            popUpTo(startDestId) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openPlayer() {
        nav.navigate(Screen.Player.route) {
            launchSingleTop = true
        }
    }

    var isMiniPlayerScrolledVisible by remember { mutableStateOf(true) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                val dx = available.x
                if (kotlin.math.abs(dy) > kotlin.math.abs(dx)) {
                    if (dy < -8f) {
                        isMiniPlayerScrolledVisible = false
                    } else if (dy > 8f) {
                        isMiniPlayerScrolledVisible = true
                    }
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(currentRoute) {
        isMiniPlayerScrolledVisible = true
    }

    val currentSongForDock by playerViewModel.current.collectAsStateWithLifecycle()
    LaunchedEffect(currentSongForDock?.songId) {
        if (currentSongForDock != null) {
            isMiniPlayerScrolledVisible = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        NavHost(
            navController = nav,
            startDestination = Screen.Home.route,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                scaleIn(initialScale = 0.95f, animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)) +
                        fadeIn(animationSpec = tween(220))
            },
            exitTransition = {
                scaleOut(targetScale = 0.97f, animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)) +
                        fadeOut(animationSpec = tween(180))
            },
            popEnterTransition = {
                scaleIn(initialScale = 0.95f, animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)) +
                        fadeIn(animationSpec = tween(220))
            },
            popExitTransition = {
                scaleOut(targetScale = 0.97f, animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)) +
                        fadeOut(animationSpec = tween(180))
            }
        ) {
            // TAB 1: Home / Discover
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToSettings = {
                        nav.navigate(Screen.Settings.route) { launchSingleTop = true }
                    },
                    onNavigateToDownloads = {
                        nav.navigate(Screen.Download.route) { launchSingleTop = true }
                    },
                    onNavigateToPlayer = { openPlayer() },
                    onNavigateToMyWave = {
                        navigateToTab(Screen.MyWave.route)
                    },
                    onNavigateToAlbum = { albumName ->
                        nav.navigate(Screen.AlbumDetail.createRoute(albumName))
                    },
                    onNavigateToArtist = { artistName ->
                        nav.navigate(Screen.ArtistDetail.createRoute(artistName))
                    }
                )
            }

            // TAB 2: My Wave (Signature Experience)
            composable(Screen.MyWave.route) {
                MyWaveScreen(
                    onNavigateToPlayer = { openPlayer() }
                )
            }

            // TAB 3: Search & Explore
            composable(Screen.Search.route) {
                SearchScreen(
                    onNavigateToPlayer = { openPlayer() }
                )
            }

            // TAB 4: Unified Library
            composable(Screen.Library.route) {
                LibraryScreen(
                    onPlayer = { openPlayer() },
                    onNavigateToDownload = {
                        nav.navigate(Screen.Download.route) { launchSingleTop = true }
                    },
                    onNavigateToOnline = {
                        navigateToTab(Screen.Search.route)
                    },
                    onNavigateToSettings = {
                        nav.navigate(Screen.Settings.route) { launchSingleTop = true }
                    },
                    onNavigateToPlaylists = {
                        nav.navigate(Screen.Playlists.route) { launchSingleTop = true }
                    }
                )
            }

            // Download Screen
            composable(Screen.Download.route) {
                DownloadScreen(
                    onPlayer = { openPlayer() },
                    onNavigateToLibrary = { navigateToTab(Screen.Library.route) }
                )
            }

            // Playlists Screen
            composable(Screen.Playlists.route) {
                PlaylistScreen(
                    onPlayer = { openPlayer() }
                )
            }

            // Album Detail Screen
            composable(
                route = Screen.AlbumDetail.route,
                arguments = listOf(navArgument("albumName") { type = NavType.StringType })
            ) {
                AlbumDetailScreen(
                    onBack = { nav.popBackStack() },
                    onPlayer = { openPlayer() }
                )
            }

            // Artist Detail Screen
            composable(
                route = Screen.ArtistDetail.route,
                arguments = listOf(navArgument("artistName") { type = NavType.StringType })
            ) {
                ArtistDetailScreen(
                    onBack = { nav.popBackStack() },
                    onPlayer = { openPlayer() }
                )
            }

            // Settings Screen
            composable(
                route = Screen.Settings.route,
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { (it * 0.28f).toInt() },
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(240))
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { -(it * 0.20f).toInt() },
                        animationSpec = tween(240, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(180))
                },
                popEnterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { -(it * 0.20f).toInt() },
                        animationSpec = tween(260, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(220))
                },
                popExitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { (it * 0.28f).toInt() },
                        animationSpec = tween(240, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(180))
                }
            ) {
                SettingsScreen(
                    onDiagnostics = { nav.navigate(Screen.Diagnostics.route) },
                    onBack = {
                        if (!nav.popBackStack()) {
                            navigateToTab(Screen.Home.route)
                        }
                    }
                )
            }

            composable(Screen.Diagnostics.route) {
                DiagnosticsScreen(onBack = { nav.popBackStack() })
            }

            // Full Player Screen
            composable(
                route = Screen.Player.route,
                enterTransition = {
                    slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(340, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(180))
                },
                exitTransition = {
                    slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(260, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(180))
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(180))
                },
                popExitTransition = {
                    slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(260, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(180))
                }
            ) {
                PlayerScreen(
                    onQueue = { nav.navigate(Screen.Queue.route) },
                    onBack = {
                        if (!nav.popBackStack()) {
                            navigateToTab(Screen.Home.route)
                        }
                    }
                )
            }

            composable(Screen.Queue.route) {
                QueueScreen(onBack = { nav.popBackStack() })
            }
        }

        // Floating MiniPlayer + NavigationBar docked at bottom
        PlayerDock(
            visible = !isPlayerScreen,
            currentRoute = currentRoute,
            tabs = tabItems,
            viewModel = playerViewModel,
            isMiniPlayerScrolledVisible = isMiniPlayerScrolledVisible,
            onOpenPlayer = ::openPlayer,
            onNavigateToTab = ::navigateToTab,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        CustomToastHost()
    }
}

@Composable
private fun PlayerDock(
    visible: Boolean,
    currentRoute: String?,
    tabs: List<Screen>,
    viewModel: PlayerViewModel,
    isMiniPlayerScrolledVisible: Boolean,
    onOpenPlayer: () -> Unit,
    onNavigateToTab: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentSong by viewModel.current.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentPos by viewModel.currentPos.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val bassEnergy by viewModel.player.bassEnergy.collectAsStateWithLifecycle()
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(initialOffsetY = { it }, animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(200)),
        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(180))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val showMiniPlayer = currentRoute != Screen.Player.route &&
                    currentRoute != Screen.MyWave.route &&
                    isMiniPlayerScrolledVisible
            MiniPlayer(
                song = currentSong,
                isPlaying = isPlaying,
                progress = if (duration > 0) currentPos.toFloat() / duration.toFloat() else 0f,
                bassEnergy = bassEnergy,
                isVisible = showMiniPlayer,
                onPlayPause = { viewModel.player.togglePlayPause() },
                onNext = { viewModel.player.next() },
                onPrev = { viewModel.player.previous() },
                onClick = onOpenPlayer
            )
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .shadow(elevation = 10.dp, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            ) {
                tabs.forEach { screen ->
                    val isSelected = currentRoute == screen.route
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { if (currentRoute != screen.route) onNavigateToTab(screen.route) },
                        modifier = Modifier.bouncy(scaleDown = 0.92f),
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.label,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = {
                            Text(
                                text = screen.label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}
