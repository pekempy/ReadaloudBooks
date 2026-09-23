package com.pekempy.ReadAloudbooks.ui.analytics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pekempy.ReadAloudbooks.data.ReadingStatsRepository
import com.pekempy.ReadAloudbooks.data.ReadingStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ReadingAnalyticsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val statsRepository = ReadingStatsRepository(application)
    
    private val _stats = MutableStateFlow<ReadingStats?>(null)
    val stats: StateFlow<ReadingStats?> = _stats
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    init {
        loadStats()
    }
    
    fun loadStats() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val updatedStats = statsRepository.getStats()
                _stats.value = updatedStats
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
