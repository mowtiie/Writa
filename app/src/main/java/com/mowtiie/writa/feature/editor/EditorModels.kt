package com.mowtiie.writa.feature.editor

import com.mowtiie.writa.feature.notes.Tags

/** The text, tags and pin of a note as the user has them in the editor. */
data class NoteDraft(
    val title: String = "",
    val content: String = "",
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
) {
    /** The same note, ignoring spaces around the text and the case and order of the tags. */
    fun sameAs(other: NoteDraft): Boolean =
        title.trim() == other.title.trim() &&
            content.trim() == other.content.trim() &&
            pinned == other.pinned &&
            Tags.sameTags(tags, other.tags)
}

/** The outcome of turning the text typed into the tag field into tags. */
data class TagCommit(
    val tags: List<String>,
    /** The first tag that did not fit, kept in the field so what the user typed is not lost. */
    val rejected: String?,
)

object TagEntry {
    private const val MAX_SUGGESTIONS = 8

    /** A comma ends a tag, so several can be pasted at once. Blanks and tags the note already has are dropped. */
    fun commit(
        tags: List<String>,
        typed: String,
    ): TagCommit {
        var result = tags
        var rejected: String? = null
        for (part in typed.split(',')) {
            val tag = Tags.clean(part)
            if (tag.isEmpty() || Tags.contains(result, tag)) continue
            if (result.size >= Tags.MAX_TAGS_PER_NOTE) {
                rejected = rejected ?: tag
            } else {
                result = result + tag
            }
        }
        return TagCommit(result, rejected)
    }

    fun suggestions(
        known: List<String>,
        tags: List<String>,
        typed: String,
    ): List<String> {
        val wanted = Tags.key(Tags.clean(typed))
        return known
            .filter { !Tags.contains(tags, it) && Tags.key(it).contains(wanted) }
            .take(MAX_SUGGESTIONS)
    }
}

enum class EditorLoad { Loading, Ready, Unavailable }

enum class EditorMessage { SaveFailed, TrashFailed }

enum class EditorEvent { Close }

data class EditorUiState(
    val load: EditorLoad = EditorLoad.Ready,
    val isNew: Boolean = true,
    val draft: NoteDraft = NoteDraft(),
    val original: NoteDraft = NoteDraft(),
    val tagInput: String = "",
    val archived: Boolean = false,
    val knownTags: List<String> = emptyList(),
    val titleMissing: Boolean = false,
    val tagLimitReached: Boolean = false,
    val isSaving: Boolean = false,
    val message: EditorMessage? = null,
) {
    /** The tags that would be saved: the chips plus whatever was typed but not yet added. */
    val tagsToSave: List<String> get() = TagEntry.commit(draft.tags, tagInput).tags

    val hasUnsavedChanges: Boolean get() = !draft.copy(tags = tagsToSave).sameAs(original)

    val suggestions: List<String> get() = TagEntry.suggestions(knownTags, draft.tags, tagInput)
}
