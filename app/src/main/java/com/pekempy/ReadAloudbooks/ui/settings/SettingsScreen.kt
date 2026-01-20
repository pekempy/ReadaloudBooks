package com.pekempy.ReadAloudbooks.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import com.pekempy.ReadAloudbooks.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.graphics.Brush
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHome(
    onBack: () -> Unit,
    onNavigateTo: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SettingsNavItem(
                title = "Connection",
                subtitle = "Manage server and account",
                iconRes = R.drawable.ic_link
            ) { onNavigateTo("settings/connections") }
            
            SettingsNavItem(
                title = "Theming",
                subtitle = "Customize appearance",
                iconRes = R.drawable.ic_palette
            ) { onNavigateTo("settings/theming") }
            
            SettingsNavItem(
                title = "Audio Playback",
                subtitle = "Player settings",
                iconRes = R.drawable.ic_headphones
            ) { onNavigateTo("settings/audio") }
            
            SettingsNavItem(
                title = "eBook",
                subtitle = "Reader settings",
                iconRes = R.drawable.ic_book
            ) { onNavigateTo("settings/ebook") }
            
            SettingsNavItem(
                title = "Storage",
                subtitle = "Manage downloaded files",
                iconRes = R.drawable.ic_storage
            ) { onNavigateTo("storage") }

            SettingsNavItem(
                title = "Reading Statistics",
                subtitle = "View your reading progress",
                iconRes = R.drawable.ic_timeline
            ) { onNavigateTo("statistics") }

            SettingsNavItem(
                title = "Collections",
                subtitle = "Organize your books",
                iconRes = R.drawable.ic_folder
            ) { onNavigateTo("collections") }

            SettingsNavItem(
                title = "Support",
                subtitle = "Support the projects and developer",
                iconRes = R.drawable.ic_card_giftcard
            ) { onNavigateTo("settings/support") }
        }
    }
}

@Composable
fun SettingsNavItem(
    title: String,
    subtitle: String,
    iconRes: Int,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(painterResource(iconRes), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Icon(painterResource(R.drawable.ic_keyboard_arrow_right), contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsConnections(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    var remoteUrl by remember(viewModel.serverUrl) { mutableStateOf(viewModel.serverUrl) }
    var localUrl by remember(viewModel.localServerUrl) { mutableStateOf(viewModel.localServerUrl) }
    var useLocalOnWifi by remember(viewModel.useLocalOnWifi) { mutableStateOf(viewModel.useLocalOnWifi) }
    var wifiSsid by remember(viewModel.wifiSsid) { mutableStateOf(viewModel.wifiSsid) }
    var hasChanges by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connection") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SettingsSection("Server Checklist") {
                 OutlinedTextField(
                    value = remoteUrl,
                    onValueChange = { remoteUrl = it; hasChanges = true },
                    label = { Text("Remote Address") },
                    placeholder = { Text("https://storyteller.example.com") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = localUrl,
                    onValueChange = { localUrl = it; hasChanges = true },
                    label = { Text("Local Address") },
                    placeholder = { Text("http://192.168.1.x:8001") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                   modifier = Modifier.fillMaxWidth(),
                   verticalAlignment = Alignment.CenterVertically,
                   horizontalArrangement = Arrangement.SpaceBetween
                ) {
                   Text("Auto-switch on Home WiFi", modifier = Modifier.weight(1f))
                   Switch(
                       checked = useLocalOnWifi,
                       onCheckedChange = { useLocalOnWifi = it; hasChanges = true }
                   )
                }
                
                if (useLocalOnWifi) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = wifiSsid,
                        onValueChange = { wifiSsid = it; hasChanges = true },
                        label = { Text("Home WiFi Name (SSID)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { 
                        viewModel.updateConnectionSettings(remoteUrl, localUrl, useLocalOnWifi, wifiSsid)
                        hasChanges = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = hasChanges
                ) {
                    Text("Save Changes")
                }
            }
            
            SettingsSection("Account") {
                Button(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Logout")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTheming(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Theming") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Column(
             modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
             SettingsSection("Theme Mode") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeOptionButton("System", viewModel.themeMode == 0, { viewModel.setTheme(0) }, Modifier.weight(1f))
                        ThemeOptionButton("Light", viewModel.themeMode == 1, { viewModel.setTheme(1) }, Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeOptionButton("Dark", viewModel.themeMode == 2, { viewModel.setTheme(2) }, Modifier.weight(1f))
                        ThemeOptionButton("Amoled", viewModel.themeMode == 3, { viewModel.setTheme(3) }, Modifier.weight(1f))
                    }
                }
             }

             SettingsSection("Dynamic Colour") {
                 Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Enable Dynamic Colour")
                    Switch(
                        checked = viewModel.useDynamicColors,
                        onCheckedChange = { viewModel.setDynamicColor(it) }
                    )
                }
             }

             if (!viewModel.useDynamicColors) {
                 SettingsSection("Theme Colour") {
                     val currentColor = remember(viewModel.themeSource) {
                        when (viewModel.themeSource) {
                            0 -> Color(0xFF6750A4)
                            1 -> Color(0xFF2196F3) 
                            2 -> Color(0xFFF44336) 
                            3 -> Color(0xFF4CAF50) 
                            4 -> Color(0xFFFF9800) 
                            else -> Color(viewModel.themeSource)
                        }
                    }
                    ColorPicker(
                        color = currentColor,
                        onColorChanged = { newColor ->
                             viewModel.updateThemeSource(newColor.toArgb())
                        }
                    )
                 }
             }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAudio(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audio Playback") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Column(
             modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SettingsSection("Sleep Timer") {
                Row(
                    modifier = Modifier.fillMaxWidth(), 
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0, 15, 30, 45, 60).forEach { mins ->
                        FilterChip(
                            selected = viewModel.sleepTimerMinutes == mins,
                            onClick = { viewModel.setSleepTimer(mins) },
                            label = { Text(if (mins == 0) "Off" else "$mins m") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Play until end of chapter")
                    Switch(
                        checked = viewModel.sleepTimerFinishChapter,
                        onCheckedChange = { viewModel.updateSleepTimerFinishChapter(it) }
                    )
                }
            }

            SettingsSection("Playback Speed") {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Default Speed: ${"%.2f".format(viewModel.playbackSpeed)}x")
                    }
                    Slider(
                        value = viewModel.playbackSpeed,
                        onValueChange = { 
                            val rounded = (it * 20).roundToInt() / 20f
                            viewModel.updatePlaybackSpeed(rounded) 
                        },
                        valueRange = 0.5f..2.0f,
                        steps = 29
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsEbook(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("eBook") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Column(
             modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SettingsSection("Font Size") {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Size: ${viewModel.readerFontSize.toInt()}px")
                    }
                    Slider(
                        value = viewModel.readerFontSize,
                        onValueChange = { viewModel.updateReaderFontSize(it) },
                        valueRange = 12f..36f,
                        steps = 24
                    )
                }
            }

            SettingsSection("Reader Theme") {
                val themes = listOf("White", "Sepia", "Dark", "OLED")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    themes.forEachIndexed { index, name ->
                        FilterChip(
                            selected = viewModel.readerTheme == index,
                            onClick = { viewModel.updateReaderTheme(index) },
                            label = { Text(name) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            SettingsSection("Typeface") {
                val fonts = listOf("serif" to "Serif", "sans-serif" to "Sans", "monospace" to "Mono")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    fonts.forEach { (id, name) ->
                        FilterChip(
                            selected = viewModel.readerFontFamily == id,
                            onClick = { viewModel.updateReaderFontFamily(id) },
                            label = { Text(name) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ColorPicker(
    color: Color,
    onColorChanged: (Color) -> Unit
) {
    var hsv by remember(color) {
        val floatArray = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), floatArray)
        mutableStateOf(Triple(floatArray[0], floatArray[1], floatArray[2]))
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
            )
            Text(
                "Selected Colour",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text("Hue", style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(8.dp))
        
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
        ) {
             val width = constraints.maxWidth.toFloat()
             
             Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Red,
                                Color.Yellow,
                                Color.Green,
                                Color.Cyan,
                                Color.Blue,
                                Color.Magenta,
                                Color.Red
                            )
                        )
                    )
                    .pointerInput(Unit) {
                         detectTapGestures { offset ->
                            val newHue = (offset.x / size.width) * 360f
                            val hue = newHue.coerceIn(0f, 360f)
                            
                            var s = hsv.second
                            var v = hsv.third
                            if (s < 0.1f) s = 0.8f
                            if (v < 0.1f) v = 0.9f
                            
                            hsv = Triple(hue, s, v)
                            val newColorInt = android.graphics.Color.HSVToColor(floatArrayOf(hue, s, v))
                            onColorChanged(Color(newColorInt))
                        }
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            val newHue = (change.position.x / size.width) * 360f
                            val hue = newHue.coerceIn(0f, 360f)
                            
                            var s = hsv.second
                            var v = hsv.third
                            if (s < 0.1f) s = 0.8f
                            if (v < 0.1f) v = 0.9f

                            hsv = Triple(hue, s, v)
                            val newColorInt = android.graphics.Color.HSVToColor(floatArrayOf(hue, s, v))
                            onColorChanged(Color(newColorInt))
                        }
                    }
            )

            val thumbPositionRaw = (hsv.first / 360f) * maxWidth.value
            val thumbPosition = thumbPositionRaw.dp - 10.dp

            Box(
                modifier = Modifier
                    .offset(x = thumbPosition)
                    .size(20.dp)
                    .background(Color.White, CircleShape)
                    .border(2.dp, Color.Black, CircleShape)
                    .align(Alignment.CenterStart)
            )
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        content()
    }
}

@Composable
fun ThemeOptionButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val padding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    if (selected) {
        Button(onClick = onClick, modifier = modifier, contentPadding = padding) { 
            Text(text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) 
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, contentPadding = padding) { 
            Text(text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) 
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSupport(
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Support") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .paddingFromBaseline(bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SupportItem(
                    title = "Donate to the developer",
                    subtitle = "paypal.me/TroubledMindTrade",
                    iconRes = R.drawable.ic_payments,
                    onClick = { onOpenUrl("https://paypal.me/TroubledMindTrade") }
                )
                SupportItem(
                    title = "Sponsor the developer",
                    subtitle = "github.com/pekempy",
                    iconRes = R.drawable.ic_code,
                    onClick = { onOpenUrl("https://github.com/pekempy") }
                )
                SupportItem(
                    title = "Support Storyteller",
                    subtitle = "opencollective.com/storyteller",
                    iconRes = R.drawable.ic_mode_heat,
                    onClick = { onOpenUrl("https://opencollective.com/storyteller") }
                )
                SupportItem(
                    title = "Support Hardcover",
                    subtitle = "hardcover.app/supporter",
                    iconRes = R.drawable.ic_book,
                    onClick = { onOpenUrl("https://hardcover.app/supporter") }
                )
            }
            
            Text(
                text = "This app was coded with the assistance of AI technology. This message is included as part of the developer's commitment to transparency about the use of AI in software development.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )
        }
    }
}

@Composable
fun SupportItem(
    title: String,
    subtitle: String,
    iconRes: Int,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
