package com.pekempy.ReadAloudbooks.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

/**
 * Dictionary lookup using Free Dictionary API
 */
class DictionaryService {

    data class Definition(
        val word: String,
        val phonetic: String?,
        val partOfSpeech: String,
        val definition: String,
        val example: String?
    )

    suspend fun lookupWord(word: String): List<Definition> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.dictionaryapi.dev/api/v2/entries/en/${word.lowercase()}")
            val response = url.readText()
            parseDefinitions(response)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseDefinitions(jsonResponse: String): List<Definition> {
        val definitions = mutableListOf<Definition>()

        try {
            val jsonArray = JSONArray(jsonResponse)
            for (i in 0 until jsonArray.length()) {
                val entry = jsonArray.getJSONObject(i)
                val word = entry.getString("word")
                val phonetic = entry.optString("phonetic", null)

                val meanings = entry.getJSONArray("meanings")
                for (j in 0 until meanings.length()) {
                    val meaning = meanings.getJSONObject(j)
                    val partOfSpeech = meaning.getString("partOfSpeech")

                    val defs = meaning.getJSONArray("definitions")
                    for (k in 0 until minOf(defs.length(), 3)) { // Limit to 3 definitions per part of speech
                        val def = defs.getJSONObject(k)
                        definitions.add(
                            Definition(
                                word = word,
                                phonetic = phonetic,
                                partOfSpeech = partOfSpeech,
                                definition = def.getString("definition"),
                                example = def.optString("example", null)
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Return empty list on parse error
        }

        return definitions
    }
}

/**
 * Dictionary Dialog Component
 */
@Composable
fun DictionaryDialog(
    word: String,
    onDismiss: () -> Unit,
    onSearchInBook: () -> Unit
) {
    val dictionaryService = remember { DictionaryService() }
    var definitions by remember { mutableStateOf<List<DictionaryService.Definition>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(word) {
        isLoading = true
        error = false
        try {
            val result = dictionaryService.lookupWord(word)
            definitions = result
            if (result.isEmpty()) {
                error = true
            }
        } catch (e: Exception) {
            error = true
        } finally {
            isLoading = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = word,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Text("✕")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Content
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp)
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    error || definitions.isNullOrEmpty() -> {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Definition not found",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "Try searching for \"$word\" in the book instead.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            definitions?.firstOrNull()?.phonetic?.let { phonetic ->
                                Text(
                                    text = phonetic,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                            }

                            definitions?.groupBy { it.partOfSpeech }?.forEach { (pos, defs) ->
                                Text(
                                    text = pos,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )

                                defs.forEach { def ->
                                    Column(
                                        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                                    ) {
                                        Text(
                                            text = "• ${def.definition}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        def.example?.let { example ->
                                            Text(
                                                text = "\"$example\"",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Actions
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onSearchInBook) {
                        Text("Search in Book")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

/**
 * Context menu for word selection with dictionary option
 */
@Composable
fun WordContextMenu(
    word: String,
    onDefine: () -> Unit,
    onHighlight: () -> Unit,
    onCopy: () -> Unit,
    onSearchInBook: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(word) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ContextMenuItem(icon = "📖", text = "Define", onClick = onDefine)
                ContextMenuItem(icon = "🖍️", text = "Highlight", onClick = onHighlight)
                ContextMenuItem(icon = "📋", text = "Copy", onClick = onCopy)
                ContextMenuItem(icon = "🔍", text = "Search in Book", onClick = onSearchInBook)
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ContextMenuItem(
    icon: String,
    text: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Text(icon, modifier = Modifier.width(32.dp))
            Text(text)
        }
    }
}
