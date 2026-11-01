package com.mowtiie.writa.feature.notes

object Tags {
    const val MAX_TAG_LENGTH = 30
    const val MAX_TAGS_PER_NOTE = 10

    private val whitespaceRun = Regex("\\s+")

    fun clean(raw: String?): String {
        if (raw == null) return ""
        return raw
            .trim()
            .replace(whitespaceRun, " ")
            .trimStart { it == '#' || it.isWhitespace() }
            .take(MAX_TAG_LENGTH)
            .trim()
    }

    fun key(tag: String): String = tag.lowercase()

    fun contains(
        tags: Collection<String>,
        tag: String,
    ): Boolean {
        val wanted = key(tag)
        return tags.any { key(it) == wanted }
    }

    fun add(
        tags: List<String>,
        raw: String?,
    ): List<String> {
        val tag = clean(raw)
        val rejected = tag.isEmpty() || tags.size >= MAX_TAGS_PER_NOTE || contains(tags, tag)
        return if (rejected) tags else tags + tag
    }

    fun normalize(raw: Collection<String>?): List<String> = raw.orEmpty().fold(emptyList(), ::add)

    fun sameTags(
        a: List<String>,
        b: List<String>,
    ): Boolean = a.size == b.size && a.map(::key).toSet() == b.map(::key).toSet()

    fun matchesAny(
        noteTags: List<String>,
        selectedKeys: Set<String>,
    ): Boolean = selectedKeys.isEmpty() || noteTags.any { key(it) in selectedKeys }

    fun usage(tagsPerNote: Iterable<List<String>>): Map<String, Int> {
        val spelling = LinkedHashMap<String, String>()
        val counts = LinkedHashMap<String, Int>()
        for (tag in tagsPerNote.flatten()) {
            val key = key(tag)
            spelling.putIfAbsent(key, tag)
            counts[key] = (counts[key] ?: 0) + 1
        }
        return counts.keys
            .sortedWith(
                compareByDescending<String> { counts.getValue(it) }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { spelling.getValue(it) },
            ).associate { spelling.getValue(it) to counts.getValue(it) }
    }
}
