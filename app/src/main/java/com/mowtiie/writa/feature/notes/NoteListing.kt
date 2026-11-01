package com.mowtiie.writa.feature.notes

/** What the user asked of the notes list. Filtering and sorting happen on the device, never on the server. */
data class NoteListQuery(
    val search: String = "",
    /** [Tags.key] forms; a note matches if it has any of them. Empty means no tag filter. */
    val tagKeys: Set<String> = emptySet(),
    val sort: Sort = Sort.NEWEST,
)

/**
 * Notes with their searchable text lowercased once, so typing in the search box does not lowercase every note
 * again. The text is encrypted in the database, so search cannot run in SQL; build this off the main thread.
 */
class SearchableNotes(
    private val notes: List<Note>,
) {
    private val haystacks: Map<Long, String> = notes.associate { it.id to haystackOf(it) }

    /** Pinned notes first, then the chosen order; ties keep the order the notes came in (newest first). */
    fun apply(query: NoteListQuery): List<Note> {
        val needle = query.search.trim().lowercase()
        val matching =
            notes.filter { note ->
                Tags.matchesAny(note.tags, query.tagKeys) && (needle.isEmpty() || needle in haystacks.getValue(note.id))
            }
        return sorted(matching, query.sort).sortedByDescending { it.pinned }
    }

    private fun sorted(
        list: List<Note>,
        sort: Sort,
    ): List<Note> =
        when (sort) {
            Sort.NEWEST -> list
            Sort.OLDEST -> list.asReversed()
            Sort.TITLE_AZ -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            Sort.TITLE_ZA -> list.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title })
        }

    private fun haystackOf(note: Note): String =
        listOfNotNull(note.title, note.content, note.tags.joinToString(" ")).joinToString("\n").lowercase()
}
