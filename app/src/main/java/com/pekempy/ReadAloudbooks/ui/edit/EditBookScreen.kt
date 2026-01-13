package com.pekempy.ReadAloudbooks.ui.edit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import com.pekempy.ReadAloudbooks.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBookScreen(
    viewModel: EditBookViewModel,
    bookId: String,
    onBack: () -> Unit
) {
    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId)
    }

    val textCoverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        viewModel.textCoverUri = uri
    }

    val audioCoverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        viewModel.audioCoverUri = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Metadata") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                actions = {
                    if (viewModel.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = { viewModel.save(onBack) }) {
                            Icon(painterResource(R.drawable.ic_check_circle), contentDescription = "Save")
                        }
                    }
                },
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
            )
        }
    ) { padding ->
        if (viewModel.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .imePadding()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CoverPicker(
                        label = "eBook Cover",
                        selectedUri = viewModel.textCoverUri,
                        currentUrl = viewModel.ebookCoverUrl,
                        onClick = { textCoverLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    )
                    CoverPicker(
                        label = "Audio Cover",
                        selectedUri = viewModel.audioCoverUri,
                        currentUrl = viewModel.audiobookCoverUrl,
                        onClick = { audioCoverLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = viewModel.title,
                    onValueChange = { viewModel.title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = viewModel.subtitle,
                    onValueChange = { viewModel.subtitle = it },
                    label = { Text("Subtitle") },
                    modifier = Modifier.fillMaxWidth()
                )

                /* 
                *    TODO:
                *    - Fix authors and narrators
                */

                // TagInputField(
                //     values = viewModel.authors,
                //     label = "Authors",
                //     suggestions = viewModel.allCreators.map { it.name }
                // )

                // TagInputField(
                //     values = viewModel.narrators,
                //     label = "Narrators",
                //     suggestions = viewModel.allCreators.map { it.name }
                // )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = viewModel.language,
                        onValueChange = { viewModel.language = it },
                        label = { Text("Language (e.g. en)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = viewModel.rating.toString().let { if (it == "0.0") "" else it },
                        onValueChange = { viewModel.rating = it.toFloatOrNull() ?: 0f },
                        label = { Text("Rating (0-5)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        )
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SuggestionTextField(
                        value = viewModel.series,
                        onValueChange = { viewModel.series = it },
                        label = "Series",
                        suggestions = viewModel.allSeriesList.map { it.name },
                        modifier = Modifier.weight(2f)
                    )
                    OutlinedTextField(
                        value = viewModel.seriesIndex,
                        onValueChange = { viewModel.seriesIndex = it },
                        label = { Text("Index") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = viewModel.description,
                    onValueChange = { viewModel.description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                TagInputField(
                    values = viewModel.tags,
                    label = "Tags",
                    suggestions = viewModel.allTagsList.map { it.name }
                )

                TagInputField(
                    values = viewModel.collections,
                    label = "Collections",
                    suggestions = viewModel.allCollectionsList.map { it.name }
                )

                if (viewModel.error != null) {
                    Text(
                        text = viewModel.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagInputField(
    values: MutableList<String>,
    label: String,
    suggestions: List<String>,
    modifier: Modifier = Modifier
) {
    var textValue by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    val filteredSuggestions = remember(textValue, suggestions, values) {
        if (textValue.isEmpty()) emptyList()
        else suggestions.filter { 
            it.contains(textValue, ignoreCase = true) && !values.contains(it) 
        }.take(5)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (values.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                values.forEach { value ->
                    InputChip(
                        selected = false,
                        onClick = { },
                        label = { Text(value) },
                        trailingIcon = {
                            Icon(
                                painterResource(R.drawable.ic_close),
                                contentDescription = "Remove",
                                modifier = Modifier.size(16.dp).clickable { values.remove(value) }
                            )
                        }
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = textValue,
                onValueChange = { 
                    textValue = it
                    expanded = it.isNotEmpty() && filteredSuggestions.isNotEmpty()
                },
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = { 
                    IconButton(onClick = {
                        if (textValue.isNotEmpty() && !values.contains(textValue)) {
                            values.add(textValue)
                            textValue = ""
                            expanded = false
                        }
                    }) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = "Add")
                    }
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = {
                        if (textValue.isNotEmpty() && !values.contains(textValue)) {
                            values.add(textValue)
                            textValue = ""
                            expanded = false
                        }
                    }
                ),
                singleLine = true
            )

            DropdownMenu(
                expanded = expanded && filteredSuggestions.isNotEmpty(),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = false),
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                filteredSuggestions.forEach { suggestion ->
                    DropdownMenuItem(
                        text = { Text(suggestion) },
                        onClick = {
                            if (!values.contains(suggestion)) {
                                values.add(suggestion)
                                textValue = ""
                                expanded = false
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suggestions: List<String>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    val filteredSuggestions = remember(value, suggestions) {
        if (value.isEmpty()) emptyList()
        else suggestions.filter { it.contains(value, ignoreCase = true) && it != value }.take(5)
    }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { 
                onValueChange(it)
                expanded = it.isNotEmpty() && filteredSuggestions.isNotEmpty()
            },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        DropdownMenu(
            expanded = expanded && filteredSuggestions.isNotEmpty(),
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = false),
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            filteredSuggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        onValueChange(suggestion)
                        expanded = false
                    }
                )
            }
        }
    }
}
@Composable
fun CoverPicker(
    label: String,
    selectedUri: android.net.Uri?,
    currentUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 8.dp))
        Box(
            modifier = Modifier
                .aspectRatio(0.7f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (selectedUri != null) {
                AsyncImage(
                    model = selectedUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (currentUrl != null) {
                AsyncImage(
                    model = currentUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    painterResource(R.drawable.ic_add),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            ) {
                Text(
                    text = "Change",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

