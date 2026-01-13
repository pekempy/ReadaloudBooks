package com.pekempy.ReadAloudbooks.ui.edit

import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.pekempy.ReadAloudbooks.data.UserPreferencesRepository
import com.pekempy.ReadAloudbooks.data.api.AppContainer
import com.pekempy.ReadAloudbooks.data.api.BookResponse
import com.pekempy.ReadAloudbooks.data.Book
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import com.pekempy.ReadAloudbooks.data.api.extractId
import com.pekempy.ReadAloudbooks.data.api.extractValue
import coil.Coil
import coil.memory.MemoryCache
import java.io.File

class EditBookViewModel(private val repository: UserPreferencesRepository) : ViewModel() {
    var book by mutableStateOf<Book?>(null)
    private var originalResponse: com.pekempy.ReadAloudbooks.data.api.BookResponse? = null
    var isLoading by mutableStateOf(false)
    var isSaving by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    var title by mutableStateOf("")
    var subtitle by mutableStateOf("")
    var description by mutableStateOf("")
    var language by mutableStateOf("")
    var rating by mutableStateOf(0f)
    var series by mutableStateOf("")
    var seriesIndex by mutableStateOf("")
    var authors = mutableStateListOf<String>()
    var narrators = mutableStateListOf<String>()
    var tags = mutableStateListOf<String>()
    var collections = mutableStateListOf<String>()

    private var seriesUuid: String? = null
    private val authorUuids = mutableMapOf<String, String>()
    private val narratorUuids = mutableMapOf<String, String>()

    var allCreators by mutableStateOf<List<com.pekempy.ReadAloudbooks.data.api.AuthorResponse>>(emptyList())
    var allSeriesList by mutableStateOf<List<com.pekempy.ReadAloudbooks.data.api.SeriesResponse>>(emptyList())
    var allTagsList by mutableStateOf<List<com.pekempy.ReadAloudbooks.data.api.TagResponse>>(emptyList())
    var allCollectionsList by mutableStateOf<List<com.pekempy.ReadAloudbooks.data.api.SeriesResponse>>(emptyList())

    var textCoverUri by mutableStateOf<Uri?>(null)
    var audioCoverUri by mutableStateOf<Uri?>(null)
    var ebookCoverUrl by mutableStateOf<String?>(null)
    var audiobookCoverUrl by mutableStateOf<String?>(null)

    private val gson = Gson()

    fun loadBook(bookId: String) {
        isLoading = true
        loadMasterLists()
        viewModelScope.launch {
            try {
                val response = AppContainer.apiClientManager.getApi().getBookDetails(bookId)
                originalResponse = response
                
                authors.clear()
                authors.addAll(response.authors.map { it.name })
                
                narrators.clear()
                response.narrators?.let { narrators.addAll(it.map { n -> n.name }) }
                
                val seriesInfo = response.series?.firstOrNull()
                seriesUuid = seriesInfo?.uuid
                val seriesName = seriesInfo?.name ?: ""
                val seriesIdx = seriesInfo?.seriesIndex ?: ""
                
                tags.clear()
                response.tags?.let { tags.addAll(it.map { t -> t.name }) }
                
                collections.clear()
                response.collections?.let { collections.addAll(it.map { c -> c.name }) }
                
                title = response.title
                subtitle = response.subtitle ?: ""
                description = response.description ?: ""
                language = response.language ?: "en"
                rating = response.rating ?: 0f
                series = seriesName
                seriesIndex = seriesIdx
                
                ebookCoverUrl = AppContainer.apiClientManager.getEbookCoverUrl(response.uuid, response.updatedAt)
                audiobookCoverUrl = AppContainer.apiClientManager.getAudiobookCoverUrl(response.uuid, response.updatedAt)
                
                book = com.pekempy.ReadAloudbooks.data.Book(
                    id = response.uuid,
                    title = response.title,
                    author = authors.joinToString(", "),
                    description = response.description,
                    coverUrl = "${AppContainer.apiClientManager.baseUrl}api/v2/books/${response.uuid}/cover",
                    hasAudiobook = response.audiobook != null,
                    hasEbook = response.ebook != null,
                    hasReadAloud = response.readaloud != null
                )
            } catch (e: Exception) {
                error = "Failed to load book: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun save(onSuccess: () -> Unit) {
        val currentBook = book ?: return
        isSaving = true
        viewModelScope.launch {
            try {
                val fieldNames = mutableListOf<String>()
                val dataParts = mutableListOf<MultipartBody.Part>()

                fun addField(name: String, value: Any?) {
                    fieldNames.add(name)
                    dataParts.add(MultipartBody.Part.createFormData(name, gson.toJson(value)))
                }

                fun addMultiField(name: String, values: List<Any>) {
                    values.forEach { valItem ->
                        fieldNames.add(name)
                        dataParts.add(MultipartBody.Part.createFormData(name, gson.toJson(valItem)))
                    }
                }

                originalResponse?.let { original ->
                    addField("uuid", original.uuid)
                    addField("title", title)
                    addField("id", original.id)
                    addField("language", language)
                }

                addField("description", description)
                addField("rating", if (rating > 0) rating else null)
                originalResponse?.suffix?.let { addField("suffix", it) }
                addField("subtitle", if (subtitle.isNotEmpty()) subtitle else null)
                
                val authorsListStr = authors.filter { it.isNotEmpty() }
                addMultiField("authors", authorsListStr)
                
                val narratorsListStr = narrators.filter { it.isNotEmpty() }
                addMultiField("narrators", narratorsListStr)
                
                if (series.isNotEmpty()) {
                    val originalSeries = originalResponse?.series?.firstOrNull()
                    val seriesObject = mutableMapOf<String, Any?>(
                        "name" to series,
                        "uuid" to (findSeriesUuid(series) ?: originalSeries?.uuid),
                        "featured" to (originalSeries?.featured ?: 1),
                        "createdAt" to originalSeries?.createdAt,
                        "updatedAt" to originalSeries?.updatedAt
                    )
                    seriesIndex.replace(",", ".").toDoubleOrNull()?.toInt()?.let { 
                        seriesObject["position"] = it 
                    }
                    addField("series", seriesObject)
                } else {
                    addField("series", null)
                }

                val tagsListStr = tags.filter { it.isNotEmpty() }
                addMultiField("tags", tagsListStr)

                val collectionsListStr = collections.filter { it.isNotEmpty() }
                addMultiField("collections", collectionsListStr)

                originalResponse?.let { original ->
                    addField("status", original.status.extractId())
                    
                    val posValue = if (series.isEmpty()) {
                        original.position.extractValue()
                    } else {
                        seriesIndex.replace(",", ".").toDoubleOrNull()?.toInt() ?: original.position.extractValue()
                    }
                    addField("position", posValue)
                    fun relativize(path: String?): String? {
                        if (path == null) return null

                        val file = File(path)

                        return if (file.extension.isBlank()) {
                            null
                        } else {
                            file.name
                        }
                    }
                }

                android.util.Log.d("EditBookVM", "Submitting ${fieldNames.size} fields and ${dataParts.size} data parts")

                textCoverUri?.let { uri ->
                    prepareFilePart("textCover", uri)?.let { 
                        fieldNames.add("textCover")
                        dataParts.add(it) 
                    }
                }
                
                audioCoverUri?.let { uri ->
                    prepareFilePart("audioCover", uri)?.let { 
                        fieldNames.add("audioCover")
                        dataParts.add(it) 
                    }
                }

                val finalParts = mutableListOf<MultipartBody.Part>()
                fieldNames.forEach { name ->
                    finalParts.add(MultipartBody.Part.createFormData("fields", name))
                }
                finalParts.addAll(dataParts)

                AppContainer.apiClientManager.getApi().updateBook(
                    currentBook.id,
                    finalParts
                )
                
                onSuccess()
            } catch (e: Exception) {
                error = "Failed to save: ${e.message}"
                android.util.Log.e("EditBookVM", "Save failed", e)
            } finally {
                isSaving = false
            }
        }
    }

    private fun loadMasterLists() {
        viewModelScope.launch {
            try {
                val api = AppContainer.apiClientManager.getApi()
                allCreators = api.getCreators()
                allSeriesList = api.getSeries()
                allTagsList = api.getTags()
                allCollectionsList = api.getCollections()
            } catch (e: Exception) {
                android.util.Log.e("EditBookVM", "Failed to load master lists", e)
            }
        }
    }

    private fun findCreatorUuid(name: String): String? {
        return allCreators.find { it.name.equals(name, ignoreCase = true) }?.uuid
    }

    private fun findSeriesUuid(name: String): String? {
        return allSeriesList.find { it.name.equals(name, ignoreCase = true) }?.uuid
    }
    
    private fun findTagUuid(name: String): String? {
        return allTagsList.find { it.name.equals(name, ignoreCase = true) }?.uuid
    }

    private fun findCollectionUuid(name: String): String? {
        return allCollectionsList.find { it.name.equals(name, ignoreCase = true) }?.uuid
    }

    private fun prepareFilePart(partName: String, fileUri: Uri): MultipartBody.Part? {
        val context = AppContainer.context
        val inputStream = context.contentResolver.openInputStream(fileUri) ?: return null
        val fileBytes = inputStream.readBytes()
        val requestFile = fileBytes.toRequestBody("image/*".toMediaTypeOrNull(), 0, fileBytes.size)
        return MultipartBody.Part.createFormData(partName, "cover.jpg", requestFile)
    }
}
