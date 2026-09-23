# v0.11.1 Complete Implementation Guides

## Feature 1: Batch Operations (IN PROGRESS)

### Current State:
- ✅ ViewModel state added (selectionMode, selectedBooks, toggle methods)
- ✅ BookItem signature updated (isSelectionMode, isSelected, onSelectionToggle)
- ✅ Selection toggle button in toolbar
- ❌ Checkbox overlay UI
- ❌ BatchOperationBar integration
- ❌ Action handlers

### Implementation Steps:
1. Add checkbox overlay to BookItem (lines 54-214 in BookComponents.kt)
   - Add Box wrapper around entire Card content
   - Add Checkbox in top-right corner when isSelectionMode=true
   - Style: Surface with primary color background

2. Wire up selection in LibraryScreen
   - Pass viewModel.selectionMode to BookItem
   - Pass book.id in viewModel.selectedBooks for isSelected
   - Pass { viewModel.toggleBookSelection(book.id) } for onSelectionToggle

3. Add BatchOperationBar at bottom of Scaffold
   - Import from ui.library.BatchOperations
   - Show when viewModel.selectedBooks.isNotEmpty()
   - Wire onCancel to viewModel.clearSelection()
   - Implement batch action handlers

4. Batch action handlers in LibraryViewModel:
   ```kotlin
   fun batchDownload(fileDir: File) {
       selectedBooks.forEach { bookId ->
           books.find { it.id == bookId }?.let { downloadBook(it) }
       }
       clearSelection()
   }
   
   fun batchDelete() {
       // Implementation
   }
   ```

---

## Feature 2: Tab Ordering

### Goal: Drag-and-drop tab reordering in settings

### Files:
- `ui/settings/TabOrdering.kt` (EXISTS - needs integration)
- `ui/settings/SettingsScreen.kt` (add route)
- `MainActivity.kt` (add composable route)
- `data/UserPreferencesRepository.kt` (add TAB_ORDER preference)

### Implementation:
1. Add tab order preference:
   ```kotlin
   // UserPreferencesRepository.kt
   val TAB_ORDER = stringPreferencesKey("tab_order")
   val SHOW_BOOKS_TAB = booleanPreferencesKey("show_books_tab")
   // etc for each tab
   
   suspend fun updateTabOrder(order: List<String>) {
       context.dataStore.edit { it[TAB_ORDER] = order.joinToString(",") }
   }
   ```

2. Tab ordering screen needs:
   ```kotlin
   @Composable
   fun TabOrderingScreen(
       viewModel: SettingsViewModel,
       onBack: () -> Unit
   ) {
       val tabs = remember {
           listOf(
               TabItem("books", "Books", R.drawable.ic_book, viewModel.showBooksTab),
               TabItem("authors", "Authors", R.drawable.ic_person, viewModel.showAuthorsTab),
               // etc
           )
       }
       
       var tabList by remember { mutableStateOf(tabs) }
       
       LazyColumn {
           items(tabList, key = { it.id }) { tab ->
               TabOrderItem(
                   tab = tab,
                   onToggle = { viewModel.toggleTab(tab.id) },
                   modifier = Modifier.animateItemPlacement()
               )
           }
       }
   }
   ```

3. Apply ordering in AppNavigationBar:
   - Read TAB_ORDER from settings
   - Render tabs in custom order
   - Show/hide based on preferences

---

## Feature 3: Theme from Book Covers

### Goal: Extract dominant color from book cover and apply as theme

### Current State:
- ✅ ColorExtractor.kt utility created
- ✅ Palette library added to dependencies
- ❌ Integration with ViewModels
- ❌ Apply color when book opens

### Implementation:
1. Add theme source tracking in UserPreferencesRepository:
   ```kotlin
   val BOOK_THEME_COLOR = intPreferencesKey("book_theme_color")
   val USE_BOOK_COLORS = booleanPreferencesKey("use_book_colors")
   
   suspend fun updateBookThemeColor(color: Int) {
       context.dataStore.edit { 
           it[BOOK_THEME_COLOR] = color
           if (get(USE_BOOK_COLORS) == true) {
               it[THEME_SOURCE] = color
           }
       }
   }
   ```

2. Extract color when book loads:
   ```kotlin
   // In ReadAloudAudioViewModel.initializePlayer() or AudiobookViewModel
   LaunchedEffect(currentBook) {
       currentBook?.coverUrl?.let { coverUrl ->
           val color = ColorExtractor.extractDominantColor(coverUrl, context)
           if (color != null && ColorExtractor.isColorUsable(color)) {
               repository.updateBookThemeColor(color)
           }
       }
   }
   ```

3. Add toggle in theming settings:
   ```kotlin
   SettingsSection("Book Cover Theming") {
       Row {
           Text("Use colors from book covers")
           Switch(
               checked = viewModel.useBookColors,
               onCheckedChange = { viewModel.setUseBookColors(it) }
           )
       }
   }
   ```

4. Restore default theme when navigating away:
   ```kotlin
   DisposableEffect(Unit) {
       onDispose {
           if (useBookColors) {
               repository.updateThemeSource(savedThemeSource)
           }
       }
   }
   ```

---

## Feature 4: Reading Analytics

### Goal: Track reading time, calculate stats, display graphs

### Files to Create:
- `data/ReadingStatsRepository.kt` - Data persistence
- `data/ReadingSession.kt` - Data models
- `ui/analytics/ReadingAnalyticsScreen.kt` (replace placeholder)
- `ui/analytics/ReadingAnalyticsViewModel.kt`

### Data Models:
```kotlin
data class ReadingSession(
    val bookId: String,
    val startTime: Long,
    val endTime: Long,
    val durationMs: Long,
    val bookType: String // "readaloud", "audiobook", "ebook"
)

data class ReadingStats(
    val totalReadingTimeMs: Long,
    val booksFinished: Int,
    val currentStreak: Int,
    val averageSessionMs: Long,
    val readingByDay: Map<String, Long>, // Date -> duration
    val readingByBook: List<BookStats>
)

data class BookStats(
    val bookId: String,
    val title: String,
    val totalTimeMs: Long,
    val progress: Float,
    val lastRead: Long
)
```

### Implementation:
1. Create Room database for sessions:
   ```kotlin
   @Entity(tableName = "reading_sessions")
   data class ReadingSessionEntity(
       @PrimaryKey(autoGenerate = true) val id: Long = 0,
       val bookId: String,
       val startTime: Long,
       val endTime: Long,
       val durationMs: Long,
       val bookType: String
   )
   
   @Dao
   interface ReadingSessionDao {
       @Query("SELECT * FROM reading_sessions WHERE bookId = :bookId")
       fun getSessionsForBook(bookId: String): Flow<List<ReadingSessionEntity>>
       
       @Query("SELECT * FROM reading_sessions WHERE startTime >= :since")
       fun getSessionsSince(since: Long): Flow<List<ReadingSessionEntity>>
       
       @Insert
       suspend fun insertSession(session: ReadingSessionEntity)
   }
   ```

2. Track reading time in players:
   ```kotlin
   // In ReadAloudAudioViewModel / AudiobookViewModel / ReaderViewModel
   private var sessionStartTime: Long? = null
   
   fun startSession(bookId: String) {
       sessionStartTime = System.currentTimeMillis()
   }
   
   fun endSession() {
       sessionStartTime?.let { startTime ->
           val endTime = System.currentTimeMillis()
           val duration = endTime - startTime
           if (duration > 10000) { // Only log sessions > 10 seconds
               viewModelScope.launch {
                   readingStatsRepository.insertSession(
                       ReadingSession(
                           bookId = currentBook!!.id,
                           startTime = startTime,
                           endTime = endTime,
                           durationMs = duration,
                           bookType = "readaloud" // or audiobook/ebook
                       )
                   )
               }
           }
       }
       sessionStartTime = null
   }
   
   // Call endSession in onPause / onStop / DisposableEffect
   ```

3. Analytics UI with graphs:
   ```kotlin
   @Composable
   fun ReadingAnalyticsScreen(
       viewModel: ReadingAnalyticsViewModel,
       onBack: () -> Unit
   ) {
       val stats by viewModel.stats.collectAsState()
       
       Column {
           // Summary cards
           Row {
               StatCard("Total Time", formatTime(stats.totalReadingTimeMs))
               StatCard("Books Finished", stats.booksFinished.toString())
               StatCard("Current Streak", "${stats.currentStreak} days")
           }
           
           // Weekly reading graph
           WeeklyReadingGraph(stats.readingByDay)
           
           // Books list
           LazyColumn {
               items(stats.readingByBook) { bookStats ->
                   BookStatItem(bookStats)
               }
           }
       }
   }
   ```

---

## Feature 5: Advanced Settings

### Goal: Expose all advanced configuration options

### Settings to Add:
1. **Sync Settings**
   - Background sync frequency
   - Sync on WiFi only
   - Auto-sync progress

2. **Download Settings**
   - Download quality
   - Auto-download new series books
   - Cache size limit

3. **Playback Settings**
   - Auto-play next chapter
   - Remember position threshold
   - Skip silence

4. **Reader Settings**
   - Auto-scroll speed
   - Page turn animation
   - Brightness override

5. **Advanced Options**
   - Clear all caches
   - Reset to defaults
   - Export logs
   - Developer mode

### Implementation:
```kotlin
@Composable
fun AdvancedSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    LazyColumn {
        item {
            SettingsSection("Sync") {
                DropdownSetting(
                    title = "Background sync",
                    options = listOf("15min", "30min", "1h", "Manual"),
                    selected = viewModel.syncFrequency,
                    onSelect = { viewModel.setSyncFrequency(it) }
                )
                SwitchSetting(
                    title = "WiFi only",
                    checked = viewModel.syncWifiOnly,
                    onCheckedChange = { viewModel.setSyncWifiOnly(it) }
                )
            }
        }
        
        item {
            SettingsSection("Downloads") {
                SliderSetting(
                    title = "Cache size limit",
                    value = viewModel.cacheLimit,
                    range = 100f..5000f,
                    unit = "MB",
                    onValueChange = { viewModel.setCacheLimit(it) }
                )
            }
        }
        
        item {
            SettingsSection("Dangerous") {
                ButtonSetting(
                    title = "Clear all caches",
                    subtitle = "Free up storage space",
                    onClick = { viewModel.clearAllCaches() },
                    destructive = true
                )
            }
        }
    }
}
```

---

## Feature 6: Backup & Restore

### Goal: Export/import all settings as JSON

### Implementation:
```kotlin
// data/SettingsBackup.kt
data class SettingsBackup(
    val version: Int = 1,
    val exportedAt: Long,
    val settings: Map<String, Any>,
    val tabOrder: List<String>,
    val themePreferences: ThemePreferences
)

object BackupManager {
    suspend fun exportSettings(repository: UserPreferencesRepository): String {
        val settings = repository.userSettings.first()
        val backup = SettingsBackup(
            exportedAt = System.currentTimeMillis(),
            settings = mapOf(
                "themeMode" to settings.themeMode,
                "dynamicColor" to settings.useDynamicColors,
                "themeSource" to settings.themeSource,
                // ... all settings
            ),
            tabOrder = listOf("books", "authors", "series", "collections"),
            themePreferences = ThemePreferences(...)
        )
        return Json.encodeToString(backup)
    }
    
    suspend fun importSettings(json: String, repository: UserPreferencesRepository) {
        val backup = Json.decodeFromString<SettingsBackup>(json)
        // Apply each setting
        repository.setThemeMode(backup.settings["themeMode"] as Int)
        // etc
    }
}
```

### UI:
```kotlin
@Composable
fun SettingsBackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { UserPreferencesRepository(context) }
    
    var exportedJson by remember { mutableStateOf<String?>(null) }
    
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { stream ->
                stream.write(exportedJson!!.toByteArray())
            }
        }
    }
    
    Column {
        Button(onClick = {
            viewModelScope.launch {
                exportedJson = BackupManager.exportSettings(repository)
                exportLauncher.launch("readaloud-settings-backup.json")
            }
        }) {
            Text("Export Settings")
        }
        
        Button(onClick = { /* Import */ }) {
            Text("Import Settings")
        }
    }
}
```

---

## Feature 7: Per-Page Layouts

### Goal: Different layout for each screen (Library: grid, Authors: list, etc)

### Implementation:
```kotlin
// In UserPreferencesRepository
val LIBRARY_LAYOUT = stringPreferencesKey("library_layout")
val AUTHORS_LAYOUT = stringPreferencesKey("authors_layout")
// etc

enum class LayoutType {
    GRID_2, GRID_3, GRID_4, LIST, COMPACT
}

// In LibraryScreen
val currentLayout = when(viewModel.currentViewMode) {
    ViewMode.Library -> settings.libraryLayout
    ViewMode.Authors -> settings.authorsLayout
    else -> LayoutType.GRID_3
}

when(currentLayout) {
    LayoutType.GRID_3 -> LazyVerticalGrid(columns = 3) { ... }
    LayoutType.LIST -> LazyColumn { ... }
    LayoutType.COMPACT -> LazyColumn { /* Compact items */ }
}
```

---

## Feature 8: Enhanced Animations

### Improvements:
1. Page transitions (already using slideIn/slideOut - enhance with spring)
2. Loading states with shimmer
3. Book card hover effects
4. Tab switching animations
5. Progress bar animations

### Implementation:
```kotlin
// Spring-based transitions
val spring = spring<IntOffset>(dampingRatio = 0.8f, stiffness = 380f)

enterTransition = slideInHorizontally(animationSpec = spring) + fadeIn()

// Shimmer loading
@Composable
fun ShimmerLoadingCard() {
    val infiniteTransition = rememberInfiniteTransition()
    val shimmer = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(1000))
    )
    
    Box(Modifier.shimmerBackground(shimmer.value))
}

// Card hover (combinedClickable with scale)
var pressed by remember { mutableStateOf(false) }
val scale by animateFloatAsState(if (pressed) 0.95f else 1f)

Card(
    modifier = Modifier
        .scale(scale)
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                onTap = { onClick() }
            )
        }
)
```

---

## Integration Checklist

For each feature:
1. ✅ Create/update data models
2. ✅ Add UserPreferences keys
3. ✅ Create UI composables
4. ✅ Add to SettingsViewModel
5. ✅ Add route to MainActivity
6. ✅ Add menu item to SettingsHome
7. ✅ Test compilation
8. ✅ Test functionality
9. ✅ Commit with clear message

## Testing Plan

1. Download dialog: Click download, verify dialog shows, select format, verify download starts
2. Batch operations: Toggle selection, select books, verify actions work
3. Tab ordering: Drag tabs, verify order persists, toggle visibility
4. Theme from covers: Open book, verify theme changes, close book, verify restores
5. Analytics: Read for 30s, verify session logged, check stats screen
6. Advanced settings: Change each setting, verify persistence
7. Backup: Export, import, verify all settings restored
8. Per-page layouts: Switch layouts, verify each screen type
9. Animations: Navigate around, verify smooth transitions

## Build & Release

```bash
./gradlew clean
./gradlew assembleDebug
# Test on device
./gradlew assembleRelease
# Tag v0.11.1
git tag -a v0.11.1 -m "Complete feature implementation"
git push origin v0.11.1
```
