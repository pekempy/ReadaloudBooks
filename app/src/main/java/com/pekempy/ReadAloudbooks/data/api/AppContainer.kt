package com.pekempy.ReadAloudbooks.data.api

object AppContainer {
    val apiClientManager = ApiClientManager()
    lateinit var context: android.content.Context
    
    val readiumEngine by lazy { 
        com.pekempy.ReadAloudbooks.util.ReadiumEngine.getInstance(context) 
    }
}
