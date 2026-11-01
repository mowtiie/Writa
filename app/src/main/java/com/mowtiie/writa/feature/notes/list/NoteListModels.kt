package com.mowtiie.writa.feature.notes.list

import com.mowtiie.writa.feature.notes.Note
import com.mowtiie.writa.feature.notes.RefreshFailure
import com.mowtiie.writa.feature.notes.RefreshState
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

/** A note in the list with the key the list tracks it by, which does not change when an upload renames the note. */
data class NoteListItem(
    val key: Long,
    val note: Note,
)

/** A tag in the filter bar with how many notes carry it. */
data class TagCount(
    val tag: String,
    val key: String,
    val count: Int,
    val selected: Boolean,
)

/** The notice above the list when what is shown may be out of date. */
sealed interface NotesBanner {
    /** Offline, and changes made here are waiting to upload. */
    data class UnsentChanges(
        val count: Int,
    ) : NotesBanner

    data class RefreshFailed(
        val reason: RefreshFailure,
    ) : NotesBanner

    companion object {
        /** Only a failed refresh shows a banner; while loading, the refresh indicator says it. */
        fun of(
            refresh: RefreshState,
            pendingChanges: Int,
        ): NotesBanner? {
            val reason = (refresh as? RefreshState.Failed)?.reason ?: return null
            return if (reason == RefreshFailure.Offline && pendingChanges > 0) {
                UnsentChanges(pendingChanges)
            } else {
                RefreshFailed(reason)
            }
        }
    }
}

/** Why the list has nothing to show. */
sealed interface EmptyReason {
    data object NoNotes : EmptyReason

    data class NoMatchForSearch(
        val search: String,
    ) : EmptyReason

    data object NoMatchForTags : EmptyReason

    companion object {
        /** Null while the notes are still loading, and whenever there is something to show. */
        fun of(
            isLoaded: Boolean,
            totalNotes: Int,
            shown: Int,
            search: String,
        ): EmptyReason? =
            when {
                !isLoaded || shown > 0 -> null
                totalNotes == 0 -> NoNotes
                search.isNotBlank() -> NoMatchForSearch(search.trim())
                else -> NoMatchForTags
            }
    }
}

/** Dates in the list, in the user's own zone and language. */
class NoteDates(
    private val zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
) {
    private val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)

    /** The calendar date of a server timestamp, or null if it cannot be read. */
    fun format(timestamp: String?): String? =
        try {
            timestamp?.let { formatter.format(OffsetDateTime.parse(it).atZoneSameInstant(zone)) }
        } catch (_: DateTimeParseException) {
            null
        }
}
