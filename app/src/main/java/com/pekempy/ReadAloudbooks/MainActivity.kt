package com.pekempy.ReadAloudbooks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pekempy.ReadAloudbooks.ui.theme.ReadAloudBooksTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pekempy.ReadAloudbooks.data.UserPreferencesRepository
import com.pekempy.ReadAloudbooks.ui.library.LibraryScreen
import com.pekempy.ReadAloudbooks.ui.library.LibraryViewModel
import com.pekempy.ReadAloudbooks.ui.login.LoginScreen
import com.pekempy.ReadAloudbooks.ui.login.LoginViewModel
import com.pekempy.ReadAloudbooks.ui.reader.ReaderScreen
import com.pekempy.ReadAloudbooks.ui.reader.ReaderViewModel
import com.pekempy.ReadAloudbooks.ui.player.AudiobookPlayerScreen
import com.pekempy.ReadAloudbooks.ui.player.AudiobookViewModel
import com.pekempy.ReadAloudbooks.ui.theme.ReadAloudBooksTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.pekempy.ReadAloudbooks.ui.player.MiniPlayerBar
import com.pekempy.ReadAloudbooks.ui.components.AppNavigationBar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.currentBackStackEntryAsState
import com.pekempy.ReadAloudbooks.data.RepositoryProvider

class MainActivity : ComponentActivity() {
    private lateinit var repository: UserPreferencesRepository
    private lateinit var sharedAudiobookViewModel: AudiobookViewModel
    private lateinit var readAloudAudioViewModel: com.pekempy.ReadAloudbooks.ui.player.ReadAloudAudioViewModel
    private lateinit var readerViewModel: ReaderViewModel
    private lateinit var libraryViewModel: LibraryViewModel
    private var navigateToBookOnStart: Pair<String, String>? = null

    private fun handleIntent(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra("notification_click", false) == true) {
            intent.removeExtra("notification_click")
            runBlocking {
                val lastBook = repository.lastActiveBook.first()
                if (lastBook.first != null) {
                    navigateToBookOnStart = lastBook.first!! to lastBook.second!!
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        repository = UserPreferencesRepository(applicationContext)
        handleIntent(intent)
        val initialIsLoggedIn = runBlocking { repository.isLoggedIn.first() }

        sharedAudiobookViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AudiobookViewModel(
                    repository,
                    RepositoryProvider.audioBookmarkRepository
                ) as T
            }
        })[AudiobookViewModel::class.java]

        readAloudAudioViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return com.pekempy.ReadAloudbooks.ui.player.ReadAloudAudioViewModel(repository) as T
            }
        })[com.pekempy.ReadAloudbooks.ui.player.ReadAloudAudioViewModel::class.java]

        readerViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ReaderViewModel(
                    repository,
                    RepositoryProvider.highlightRepository,
                    RepositoryProvider.bookmarkRepository,
                    RepositoryProvider.readingStatisticsRepository
                ) as T
            }
        })[ReaderViewModel::class.java]

        libraryViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LibraryViewModel(repository) as T
            }
        })[LibraryViewModel::class.java]

        setContent {
            val settings by repository.userSettings.collectAsState(initial = com.pekempy.ReadAloudbooks.data.UserSettings(0, true, 0, 0, 18f, 0, "serif", 1.0f, false))
            
            val isDarkTheme = when (settings.themeMode) {
                1 -> false
                2, 3 -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            ReadAloudBooksTheme(
                darkTheme = isDarkTheme,
                dynamicColour = settings.useDynamicColors,
                themeSource = settings.themeSource,
                amoled = settings.themeMode == 3
            ) {
                val navController = rememberNavController()

                LaunchedEffect(navController) {
                    navigateToBookOnStart?.let { (bookId, type) ->
                        if (type == "readaloud") {
                            navController.navigate("reader/$bookId?isReadAloud=true&expandPlayer=true")
                        } else {
                            navController.navigate("player/$bookId")
                        }
                        navigateToBookOnStart = null
                    }
                }

                var updateRelease by remember { mutableStateOf<com.pekempy.ReadAloudbooks.util.UpdateChecker.GitHubRelease?>(null) }
                val context = androidx.compose.ui.platform.LocalContext.current
                
                LaunchedEffect(Unit) {
                    val isPlayStore = com.pekempy.ReadAloudbooks.util.UpdateChecker.isInstalledFromPlayStore(context)
                    android.util.Log.d("MainActivity", "Is installed from Play Store: $isPlayStore")
                    if (!isPlayStore) {
                        val currentVersion = BuildConfig.VERSION_NAME
                        android.util.Log.d("MainActivity", "Current version: $currentVersion. Checking GitHub for updates...")
                        val newRelease = com.pekempy.ReadAloudbooks.util.UpdateChecker.checkForUpdate(currentVersion)
                        if (newRelease != null) {
                            android.util.Log.i("MainActivity", "Found new release: ${newRelease.tag_name}")
                            updateRelease = newRelease
                        } else {
                            android.util.Log.d("MainActivity", "No new release found or update check failed")
                        }
                    }
                }

                if (updateRelease != null) {
                    AlertDialog(
                        onDismissRequest = { updateRelease = null },
                        title = { Text("Update Available") },
                        text = { Text("A new version (${updateRelease?.tag_name}) is available. would you like to update?") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(updateRelease?.html_url))
                                    context.startActivity(intent)
                                    updateRelease = null
                                }
                            ) {
                                Text("Update")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { updateRelease = null }) {
                                Text("Later")
                            }
                        }
                    )
                }

                LaunchedEffect(Unit) {
                    val lastBook = repository.lastActiveBook.first()
                    val bookId = lastBook.first
                    val type = lastBook.second
                    if (bookId != null) {
                        if (type == "readaloud") {
                            readAloudAudioViewModel.restoreBook(bookId)
                        } else {
                            sharedAudiobookViewModel.restoreBook(bookId)
                        }
                    }
                }
                
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                
                val shouldShowNavBar = currentRoute != null && 
                    !currentRoute.startsWith("reader") && 
                    !currentRoute.startsWith("player") &&
                    currentRoute != "login"



                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        Column(modifier = Modifier.navigationBarsPadding()) {
                            MiniPlayerBar(
                                audiobookViewModel = sharedAudiobookViewModel,
                                readAloudViewModel = readAloudAudioViewModel,
                                navController = navController
                            )
                            
                            if (shouldShowNavBar) {
                                AppNavigationBar(
                                    currentRoute = currentRoute,
                                    onNavigateToLibrary = {
                                        navController.navigate("library") {
                                            popUpTo("library") { inclusive = true }
                                        }
                                    },
                                    currentViewMode = libraryViewModel.currentViewMode,
                                    onViewModeChange = { libraryViewModel.setViewMode(it) },
                                    hasDownloads = libraryViewModel.downloadingBooks.isNotEmpty(),
                                    downloadCount = libraryViewModel.downloadingBooks.size,
                                    hasProcessing = libraryViewModel.hasProcessing,
                                    processingCount = libraryViewModel.totalProcessingCount
                                )
                            }
                        }
                    }
                ) { paddingValues ->
                    NavHost(
                        navController = navController,
                        startDestination = if (initialIsLoggedIn) "library" else "login",
                        modifier = Modifier.padding(paddingValues)
                    ) {
                    composable("login") {
                        val loginViewModel = viewModel<LoginViewModel>(
                            factory = object : ViewModelProvider.Factory {
                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                    return LoginViewModel(repository) as T
                                }
                            }
                        )
                        LoginScreen(
                            viewModel = loginViewModel,
                            onLoginSuccess = {
                                navController.navigate("library") {
                                    popUpTo("login") { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(
                        route = "library?viewMode={viewMode}&filter={filter}",
                        arguments = listOf(
                            androidx.navigation.navArgument("viewMode") { nullable = true },
                            androidx.navigation.navArgument("filter") { nullable = true }
                        ),
                        exitTransition = {
                            if (targetState.destination.route?.startsWith("settings") == true) {
                                slideOutHorizontally(targetOffsetX = { -it })
                            } else {
                                slideOutHorizontally(targetOffsetX = { it })
                            }
                        },
                        popEnterTransition = {
                            if (initialState.destination.route?.startsWith("settings") == true) {
                                slideInHorizontally(initialOffsetX = { -it })
                            } else {
                                slideInHorizontally(initialOffsetX = { it })
                            }
                        }
                    ) { backStackEntry ->
                        val viewModeStr = backStackEntry.arguments?.getString("viewMode")
                        val filter = backStackEntry.arguments?.getString("filter")
                        
                        LaunchedEffect(viewModeStr, filter) {
                            if (viewModeStr != null) {
                                try {
                                    libraryViewModel.setViewMode(LibraryViewModel.ViewMode.valueOf(viewModeStr))
                                } catch (e: Exception) { libraryViewModel.setViewMode(LibraryViewModel.ViewMode.Home) }
                            } else {
                                if (libraryViewModel.currentViewMode == LibraryViewModel.ViewMode.Home) { 
                                     libraryViewModel.setViewMode(LibraryViewModel.ViewMode.Home)
                                }
                            }
                            if (filter != null) {
                                libraryViewModel.selectFilter(filter)
                            }
                        }

                        LibraryScreen(
                            viewModel = libraryViewModel,
                            onBookClick = { book ->
                                navController.navigate("detail/${book.id}")
                            },
                            onReadEbook = { book ->
                                navController.navigate("reader/${book.id}?isReadAloud=false")
                            },
                            onPlayReadAloud = { book ->
                                navController.navigate("reader/${book.id}?isReadAloud=true")
                            },
                            onPlayAudiobook = { book ->
                                if (book.hasReadAloud) {
                                    navController.navigate("reader/${book.id}?isReadAloud=true")
                                } else {
                                    navController.navigate("player/${book.id}")
                                }
                            },
                            onSettingsClick = {
                                navController.navigate("settings")
                            },
                            onLogout = {
                                runBlocking { repository.clearCredentials() }
                                navController.navigate("login") {
                                    popUpTo("library") { inclusive = true }
                                }
                            },
                            onEditBook = { book ->
                                navController.navigate("edit/${book.id}")
                            }
                        )
                    }
                    composable(
                        route = "settings",
                        enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { it }) },
                        popEnterTransition = { slideInHorizontally(initialOffsetX = { it }) },
                        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
                    ) {
                        com.pekempy.ReadAloudbooks.ui.settings.SettingsHome(
                            onBack = { navController.popBackStack() },
                            onNavigateTo = { route -> navController.navigate(route) }
                        )
                    }
                    composable(
                        route = "settings/connections",
                        enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { it }) },
                        popEnterTransition = { slideInHorizontally(initialOffsetX = { it }) },
                        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
                    ) {
                        val settingsViewModel = viewModel<com.pekempy.ReadAloudbooks.ui.settings.SettingsViewModel>(
                            factory = object : ViewModelProvider.Factory {
                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                    return com.pekempy.ReadAloudbooks.ui.settings.SettingsViewModel(repository) as T
                                }
                            }
                        )
                        com.pekempy.ReadAloudbooks.ui.settings.SettingsConnections(
                            viewModel = settingsViewModel,
                            onBack = { navController.popBackStack() },
                            onLogout = {
                                runBlocking { repository.clearCredentials() }
                                navController.navigate("login") {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(
                        route = "settings/theming",
                        enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { it }) },
                        popEnterTransition = { slideInHorizontally(initialOffsetX = { it }) },
                        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
                    ) {
                        val settingsViewModel = viewModel<com.pekempy.ReadAloudbooks.ui.settings.SettingsViewModel>(
                            factory = object : ViewModelProvider.Factory {
                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                    return com.pekempy.ReadAloudbooks.ui.settings.SettingsViewModel(repository) as T
                                }
                            }
                        )
                        com.pekempy.ReadAloudbooks.ui.settings.SettingsTheming(
                            viewModel = settingsViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = "settings/audio",
                        enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { it }) },
                        popEnterTransition = { slideInHorizontally(initialOffsetX = { it }) },
                        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
                    ) {
                        val settingsViewModel = viewModel<com.pekempy.ReadAloudbooks.ui.settings.SettingsViewModel>(
                            factory = object : ViewModelProvider.Factory {
                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                    return com.pekempy.ReadAloudbooks.ui.settings.SettingsViewModel(repository) as T
                                }
                            }
                        )
                        com.pekempy.ReadAloudbooks.ui.settings.SettingsAudio(
                            viewModel = settingsViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = "settings/ebook",
                        enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { it }) },
                        popEnterTransition = { slideInHorizontally(initialOffsetX = { it }) },
                        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
                    ) {
                        val settingsViewModel = viewModel<com.pekempy.ReadAloudbooks.ui.settings.SettingsViewModel>(
                            factory = object : ViewModelProvider.Factory {
                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                    return com.pekempy.ReadAloudbooks.ui.settings.SettingsViewModel(repository) as T
                                }
                            }
                        )
                        com.pekempy.ReadAloudbooks.ui.settings.SettingsEbook(
                            viewModel = settingsViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = "settings/support",
                        enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { it }) },
                        popEnterTransition = { slideInHorizontally(initialOffsetX = { it }) },
                        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
                    ) {
                        com.pekempy.ReadAloudbooks.ui.settings.SettingsSupport(
                            onBack = { navController.popBackStack() },
                            onOpenUrl = { url ->
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                    navController.context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.util.Log.e("MainActivity", "Failed to open URL: $url", e)
                                }
                            }
                        )
                    }
                    composable(
                        route = "storage",
                        enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { it }) },
                        popEnterTransition = { slideInHorizontally(initialOffsetX = { it }) },
                        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
                    ) {
                        val storageViewModel = viewModel<com.pekempy.ReadAloudbooks.ui.settings.StorageManagementViewModel>(
                            factory = object : ViewModelProvider.Factory {
                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                    return com.pekempy.ReadAloudbooks.ui.settings.StorageManagementViewModel(repository) as T
                                }
                            }
                        )
                        com.pekempy.ReadAloudbooks.ui.settings.StorageManagementScreen(
                            viewModel = storageViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("detail/{bookId}",
                        enterTransition = { slideInHorizontally(initialOffsetX = { -it }) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { it }) },
                        popEnterTransition = { slideInHorizontally(initialOffsetX = { it }) },
                        popExitTransition = { slideOutHorizontally(targetOffsetX = { -it }) }
                    ) { backStackEntry ->
                        val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
                        val detailViewModel = viewModel<com.pekempy.ReadAloudbooks.ui.detail.BookDetailViewModel>(
                            factory = object : ViewModelProvider.Factory {
                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                    return com.pekempy.ReadAloudbooks.ui.detail.BookDetailViewModel(
                                        repository
                                    ) as T
                                }
                            }
                        )
                        
                        com.pekempy.ReadAloudbooks.ui.detail.BookDetailScreen(
                            viewModel = detailViewModel,
                            audiobookViewModel = sharedAudiobookViewModel,
                            readAloudViewModel = readAloudAudioViewModel,
                            bookId = bookId,
                            onBack = { navController.popBackStack() },
                            onPlay = { book ->
                                navController.navigate("player/${book.id}")
                            },
                            onAuthorClick = { author ->
                                navController.navigate("library?viewMode=${LibraryViewModel.ViewMode.Authors.name}&filter=$author") {
                                    popUpTo("library") { inclusive = true }
                                }
                            },
                            onSeriesClick = { series ->
                                navController.navigate("library?viewMode=${LibraryViewModel.ViewMode.Series.name}&filter=$series") {
                                    popUpTo("library") { inclusive = true }
                                }
                            },
                            onRead = { id, isReadAloud ->
                                navController.navigate("reader/$id?isReadAloud=$isReadAloud")
                            },
                            onNavigateToDownloads = {
                                navController.navigate("storage")
                            },
                            onEdit = { id ->
                                navController.navigate("edit/$id")
                            }
                        )
                    }
                    composable("edit/{bookId}",
                        enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { it }) },
                        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) },
                        popExitTransition = { slideOutHorizontally(targetOffsetX = { -it }) }
                    ) { backStackEntry ->
                        val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
                        val editViewModel = viewModel<com.pekempy.ReadAloudbooks.ui.edit.EditBookViewModel>(
                            factory = object : ViewModelProvider.Factory {
                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                    return com.pekempy.ReadAloudbooks.ui.edit.EditBookViewModel(repository) as T
                                }
                            }
                        )
                        com.pekempy.ReadAloudbooks.ui.edit.EditBookScreen(
                            viewModel = editViewModel,
                            bookId = bookId,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = "reader/{bookId}?isReadAloud={isReadAloud}&expandPlayer={expandPlayer}",
                        arguments = listOf(
                            androidx.navigation.navArgument("bookId") { type = androidx.navigation.NavType.StringType },
                            androidx.navigation.navArgument("isReadAloud") { 
                                type = androidx.navigation.NavType.BoolType
                                defaultValue = false 
                            },
                            androidx.navigation.navArgument("expandPlayer") { 
                                type = androidx.navigation.NavType.BoolType
                                defaultValue = false 
                            }
                        ),
                        enterTransition = { slideInHorizontally(initialOffsetX = { -it }) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { it }) },
                        popEnterTransition = { slideInHorizontally(initialOffsetX = { it }) },
                        popExitTransition = { slideOutHorizontally(targetOffsetX = { -it }) }
                    ) { backStackEntry ->
                        val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
                        val isReadAloud = backStackEntry.arguments?.getBoolean("isReadAloud") ?: false
                        val expandPlayer = backStackEntry.arguments?.getBoolean("expandPlayer") ?: false
                        
                        if (isReadAloud) {
                            com.pekempy.ReadAloudbooks.ui.player.ReadAloudPlayerScreen(
                                readerViewModel = readerViewModel,
                                readAloudAudioViewModel = readAloudAudioViewModel,
                                bookId = bookId,
                                initiallyExpanded = expandPlayer,
                                onBack = { navController.popBackStack() }
                            )
                        } else {
                            ReaderScreen(
                                viewModel = readerViewModel,
                                bookId = bookId,
                                isReadAloud = false,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                    composable("player/{bookId}",
                        enterTransition = { slideInVertically(initialOffsetY = { it }) },
                        exitTransition = { slideOutVertically(targetOffsetY = { it }) },
                        popEnterTransition = { slideInVertically(initialOffsetY = { it }) },
                        popExitTransition = { slideOutVertically(targetOffsetY = { it }) }
                    ) { backStackEntry ->
                        val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
                        AudiobookPlayerScreen(
                            viewModel = sharedAudiobookViewModel,
                            bookId = bookId,
                            onBack = { navController.popBackStack() },
                            onSwitchToReadAloud = { id ->
                                navController.navigate("reader/$id?isReadAloud=true")
                            }
                        )
                    }
                    composable(
                        route = "statistics",
                        enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { it }) },
                        popEnterTransition = { slideInHorizontally(initialOffsetX = { it }) },
                        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
                    ) {
                        val statisticsViewModel = viewModel<com.pekempy.ReadAloudbooks.ui.statistics.ReadingStatisticsViewModel>(
                            factory = object : ViewModelProvider.Factory {
                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                    return com.pekempy.ReadAloudbooks.ui.statistics.ReadingStatisticsViewModel(
                                        RepositoryProvider.readingStatisticsRepository
                                    ) as T
                                }
                            }
                        )
                        com.pekempy.ReadAloudbooks.ui.statistics.ReadingStatisticsScreen(
                            viewModel = statisticsViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (::sharedAudiobookViewModel.isInitialized) sharedAudiobookViewModel.saveBookProgress()
        if (::readAloudAudioViewModel.isInitialized) readAloudAudioViewModel.saveBookProgress()
        if (::readerViewModel.isInitialized) readerViewModel.saveProgress()
    }
}
