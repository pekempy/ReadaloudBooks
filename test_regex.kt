fun main() {
    val t = "Book_6_Harry-f-Blood_Prince_split_002"
    val isMatch = t.matches(Regex(".*split_[0-9]+", RegexOption.IGNORE_CASE))
    println("Match: $isMatch")
    if (isMatch) {
        val numStr = Regex("split_0*([0-9]+)", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)
        println("Num: $numStr")
    }
}
