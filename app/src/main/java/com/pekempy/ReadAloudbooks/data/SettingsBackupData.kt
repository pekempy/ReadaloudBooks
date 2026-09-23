package com.pekempy.ReadAloudbooks.data

data class SettingsBackup(
    val version: Int = 1,
    val exportedAt: Long,
    val settings: Map<String, Any>,
    val tabOrder: List<String> = emptyList(),
    val description: String = "ReadAloudBooks Settings Backup"
)
