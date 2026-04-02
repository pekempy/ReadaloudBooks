package com.pekempy.ReadAloudbooks.data.api

object AppContainer {
    val apiClientManager = ApiClientManager()
    lateinit var context: android.content.Context
    val applicationScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    val database by lazy { com.pekempy.ReadAloudbooks.data.database.AppDatabase.getDatabase(context) }
    
    val userPrefs by lazy { com.pekempy.ReadAloudbooks.data.UserPreferencesRepository(context) }
    
    val bookRepository by lazy { 
        com.pekempy.ReadAloudbooks.data.BookRepository(
            database.bookDao(),
            database.progressDao(),
            apiClientManager,
            userPrefs,
            context
        )
    }
}
