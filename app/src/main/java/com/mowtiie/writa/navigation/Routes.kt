package com.mowtiie.writa.navigation

import kotlinx.serialization.Serializable

@Serializable
data object SetupRoute

@Serializable
data object LoginRoute

@Serializable
data object NotesRoute

@Serializable
data object ArchiveRoute

@Serializable
data object TrashRoute

@Serializable
data class EditorRoute(
    val noteId: Long = NEW_NOTE,
) {
    companion object {
        /** The editor's "no note yet": a new note. Real ids are never -1. */
        const val NEW_NOTE = -1L

        /** The name the id has in the saved state, so a ViewModel can read it without Android types. */
        val NOTE_ID_KEY: String = serializer().descriptor.getElementName(0)
    }
}
