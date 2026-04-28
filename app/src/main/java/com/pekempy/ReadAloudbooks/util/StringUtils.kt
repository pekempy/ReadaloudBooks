package com.pekempy.ReadAloudbooks.util

object StringUtils {
    fun normalizeTitle(title: String?): String {
        if (title.isNullOrBlank()) return ""
        val lowercase = title.trim().lowercase()
        return when {
            lowercase.startsWith("the ") -> title.substring(4)
            lowercase.startsWith("a ") -> title.substring(2)
            lowercase.startsWith("an ") -> title.substring(3)
            else -> title
        }
    }
    fun decodeHtml(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
        } else {
            @Suppress("DEPRECATION")
            android.text.Html.fromHtml(text).toString()
        }
    }
    fun formatChapterTitle(title: String?, index: Int): String {
        if (title.isNullOrBlank()) return "Chapter ${index + 1}"
        val t = title.trim()
        if (t.matches(Regex(".*split_[0-9]+", RegexOption.IGNORE_CASE))) {
            val numStr = Regex("split_0*([0-9]+)", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)
            val num = numStr?.toIntOrNull() ?: index
            return "Part ${num + 1}"
        }
        val clean = t.replace("_", " ").replace("-", " ")
        return clean.replace(Regex("\\s+"), " ").trim()
    }
}
