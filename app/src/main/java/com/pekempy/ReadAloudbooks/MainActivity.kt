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
import com.pekempy.ReadAloudbooks.ui.settings.AdvancedSettingsScreen
import com.pekempy.ReadAloudbooks.ui.settings.SettingsViewModel
import com.pekempy.ReadAloudbooks.ui.theme.ReadAloudBooksTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.pekempy.ReadAloudbooks.ui.player.MiniPlayerBar
import com.pekempy.ReadAloudbooks.ui.components.AppNavigationBar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.navigation.compose.currentBackStackEntryAsState
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class MainActivity : ComponentActivity() {
    private lateinit var repository: UserPreferencesRepository
    private lateinit var sharedAudiobookViewModel: AudiobookViewModel
    private lateinit var readAloudAudioViewModel: com.pekempy.ReadAloudbooks.ui.player.ReadAloudAudioViewModel

// Type-safe ViewModel factory helper
class ViewModelFactory<T : ViewModel>(
    private val creator: () -> T
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return creator() as T
    }
}
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
        lifecycleScope.launch {
            val initialSettings = repository.userSettings.first()
            com.pekempy.ReadAloudbooks.data.SyncWorker.schedule(applicationContext, initialSettings.syncFrequencyBackground)
        }
        
        // Set up auto re-login callback for 401 errors
        com.pekempy.ReadAloudbooks.data.api.AppContainer.apiClientManager.onAuthFailure = {
            try {
                val credentials = repository.userCredentials.first()
                if (credentials != null && !credentials.password.isNullOrBlank()) {
                    val apiManager = com.pekempy.ReadAloudbooks.data.api.AppContainer.apiClientManager
                    val usernamePart = credentials.username.toRequestBody("text/plain".toMediaTypeOrNull())
                    val passwordPart = credentials.password.toRequestBody("text/plain".toMediaTypeOrNull())
                    
                    val response = apiManager.getApi().login(usernamePart, passwordPart)
                    
                    repository.saveCredentials(
                        url = credentials.url,
                        localUrl = credentials.localUrl,
                        username = credentials.username,
                        password = credentials.password,
                        token = response.accessToken,
                        useLocalOnWifi = credentials.useLocalOnWifi,
                        wifiSsid = credentials.wifiSsid
                    )
                    
                    apiManager.updateConfig(credentials.url, response.accessToken)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Auto re-login failed: ${e.message}")
                false
            }
        }
        handleIntent(intent)
        val initialIsLoggedIn = runBlocking { repository.isLoggedIn.first() }

        sharedAudiobookViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(AudiobookViewModel::class.java)) {
                    return AudiobookViewModel(repository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        })[AudiobookViewModel::class.java]

        readAloudAudioViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(com.pekempy.ReadAloudbooks.ui.player.ReadAloudAudioViewModel::class.java)) {
                    return com.pekempy.ReadAloudbooks.ui.player.ReadAloudAudioViewModel(repository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        })[com.pekempy.ReadAloudbooks.ui.player.ReadAloudAudioViewModel::class.java]

        readerViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(ReaderViewModel::class.java)) {
                    return ReaderViewModel(repository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        })[ReaderViewModel::class.java]

        libraryViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
                    return LibraryViewModel(repository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        })[LibraryViewModel::class.java]

        setContent {
            val settings by repository.userSettings.collectAsState(initial = com.pekempy.ReadAloudbooks.data.UserSettings(0, true, 0, 0, 0, false, 18f, 0, "serif", 1.0f, false, true, true, true, true, 0, 0, 0L, false, "shelf,books,authors,series,collections", emptySet()))
            
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
                                    hasProcessing = libraryViewModel.hasProcessing && !libraryViewModel.isOfflineMode,
                                    processingCount = libraryViewModel.totalProcessingCount,
                                    showBooks = settings.showBooksTab,
                                    showAuthors = settings.showAuthorsTab,
                                    showSeries = settings.showSeriesTab,
                                    showCollections = settings.showCollectionsTab,
                                    tabOrder = settings.tabOrder.split(",").map { it.trim() }
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
                                if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
                                    return LoginViewModel(repository) as T
                                }
                                throw IllegalArgumentException("Unknown ViewModel class")
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
                                    if (modelClass.isAssignableFrom(com.pekempy.ReadAloudbooks.ui.settings.SettingsViewModel::class.java)) {
                                        return com.pekempy.ReadAloudbooks.ui.settings.SettingsViewModel(repository) as T
                                    }
                                    throw IllegalArgumentException("Unknown ViewModel class")
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
                                    if (modelClass.isAssignableFrom(com.pekempy.ReadAloudbooks.ui.settings.SettingsViewModel::class.java)) {
                                        return com.pekempy.ReadAloudbooks.ui.settings.SettingsViewModel(repository) as T
                                    }
                                    throw IllegalArgumentException("Unknown ViewModel class")
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
                                    if (modelClass.isAssignableFrom(com.pekempy.ReadAloudbooks.ui.settings.SettingsViewModel::class.java)) {
                                        return com.pekempy.ReadAloudbooks.ui.settings.SettingsViewModel(repository) as T
                                    }
                                    throw IllegalArgumentException("Unknown ViewModel class")
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
                                    if (modelClass.isAssignableFrom(com.pekempy.ReadAloudbooks.ui.settings.SettingsViewModel::class.java)) {
                                        return com.pekempy.ReadAloudbooks.ui.settings.SettingsViewModel(repository) as T
                                    }
                                    throw IllegalArgumentException("Unknown ViewModel class")
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
                                    if (modelClass.isAssignableFrom(com.pekempy.ReadAloudbooks.ui.settings.StorageManagementViewModel::class.java)) {
                                        return com.pekempy.ReadAloudbooks.ui.settings.StorageManagementViewModel(repository) as T
                                    }
                                    throw IllegalArgumentException("Unknown ViewModel class")
                                }
                            }
                        )
                        com.pekempy.ReadAloudbooks.ui.settings.StorageManagementScreen(
                            viewModel = storageViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    
                    // Backup & Restore
                    composable(
                        route = "settings/backup",
                        enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
                    ) {
                        com.pekempy.ReadAloudbooks.ui.settings.SettingsBackupScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                    
                    // Advanced Settings  
                    composable(
                        route = "settings/advanced",
                        enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
                    ) {
                        com.pekempy.ReadAloudbooks.ui.settings.AdvancedSettingsScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                    
                    // Tab Ordering
                    composable(
                        route = "settings/tabs",
                        enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { it }) },
                        popEnterTransition = { slideInHorizontally(initialOffsetX = { it }) },
                        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
                    ) {
                        val settingsViewModel = viewModel<com.pekempy.ReadAloudbooks.ui.settings.SettingsViewModel>(
                            factory = object : ViewModelProvider.Factory {
                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                    if (modelClass.isAssignableFrom(com.pekempy.ReadAloudbooks.ui.settings.SettingsViewModel::class.java)) {
                                        return com.pekempy.ReadAloudbooks.ui.settings.SettingsViewModel(repository) as T
                                    }
                                    throw IllegalArgumentException("Unknown ViewModel class")
                                }
                            }
                        )
                        com.pekempy.ReadAloudbooks.ui.settings.TabOrderingScreen(
                            viewModel = settingsViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    
                    // Reading Analytics
                    composable(
                        route = "analytics",
                        enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
                    ) {
                        com.pekempy.ReadAloudbooks.ui.settings.ReadingAnalyticsScreen(
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
                                    if (modelClass.isAssignableFrom(com.pekempy.ReadAloudbooks.ui.detail.BookDetailViewModel::class.java)) {
                                        return com.pekempy.ReadAloudbooks.ui.detail.BookDetailViewModel(repository) as T
                                    }
                                    throw IllegalArgumentException("Unknown ViewModel class")
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
                                    if (modelClass.isAssignableFrom(com.pekempy.ReadAloudbooks.ui.edit.EditBookViewModel::class.java)) {
                                        return com.pekempy.ReadAloudbooks.ui.edit.EditBookViewModel(repository) as T
                                    }
                                    throw IllegalArgumentException("Unknown ViewModel class")
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
                    composable(route = "settings/advanced") {
                        val settingsViewModel = viewModel<SettingsViewModel>(
                            factory = object : ViewModelProvider.Factory {
                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                    if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                                        return SettingsViewModel(repository) as T
                                    }
                                    throw IllegalArgumentException("Unknown ViewModel class")
                                }
                            }
                        )
                        AdvancedSettingsScreen(
                            viewModel = settingsViewModel,
                            onBackClick = { navController.popBackStack() }
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
