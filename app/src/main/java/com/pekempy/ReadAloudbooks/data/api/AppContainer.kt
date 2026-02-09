package com.pekempy.ReadAloudbooks.data.api

object AppContainer {
    val apiClientManager = ApiClientManager()
    lateinit var context: android.content.Context
    val applicationScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
}
