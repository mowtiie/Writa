package com.mowtiie.writa.core.ui

import androidx.compose.runtime.Composable
import com.mowtiie.writa.core.datastore.AppSettings
import com.mowtiie.writa.core.datastore.Theme
import com.mowtiie.writa.feature.about.AboutActions
import com.mowtiie.writa.feature.about.AboutScreen
import com.mowtiie.writa.feature.about.AboutUiState
import com.mowtiie.writa.feature.archive.ArchiveActions
import com.mowtiie.writa.feature.archive.ArchiveScreen
import com.mowtiie.writa.feature.archive.ArchiveUiState
import com.mowtiie.writa.feature.auth.LoginScreen
import com.mowtiie.writa.feature.auth.LoginUiState
import com.mowtiie.writa.feature.editor.EditorActions
import com.mowtiie.writa.feature.editor.EditorScreen
import com.mowtiie.writa.feature.editor.EditorUiState
import com.mowtiie.writa.feature.editor.NoteDraft
import com.mowtiie.writa.feature.notes.Note
import com.mowtiie.writa.feature.notes.RefreshFailure
import com.mowtiie.writa.feature.notes.SyncState
import com.mowtiie.writa.feature.notes.list.EmptyReason
import com.mowtiie.writa.feature.notes.list.NoteListItem
import com.mowtiie.writa.feature.notes.list.NoteListUiState
import com.mowtiie.writa.feature.notes.list.NotesActions
import com.mowtiie.writa.feature.notes.list.NotesBanner
import com.mowtiie.writa.feature.notes.list.NotesNavigation
import com.mowtiie.writa.feature.notes.list.NotesScreen
import com.mowtiie.writa.feature.notes.list.SchemaOutdatedScreen
import com.mowtiie.writa.feature.notes.list.SchemaUiState
import com.mowtiie.writa.feature.notes.list.TagCount
import com.mowtiie.writa.feature.settings.SettingsActions
import com.mowtiie.writa.feature.settings.SettingsScreen
import com.mowtiie.writa.feature.settings.SettingsUiState
import com.mowtiie.writa.feature.setup.SetupScreen
import com.mowtiie.writa.feature.setup.SetupUiState
import com.mowtiie.writa.feature.setup.UrlProblem
import com.mowtiie.writa.feature.trash.TrashActions
import com.mowtiie.writa.feature.trash.TrashScreen
import com.mowtiie.writa.feature.trash.TrashUiState

/** Every screen of the app filled with realistic, long-ish content, for checks that look at all of them. */
object SampleScreens {
    private fun note(
        id: Long,
        pinned: Boolean = false,
        tags: List<String> = listOf("home", "errands", "shopping", "urgent"),
        sync: SyncState = SyncState.Synced,
    ) = Note(
        id,
        "Weekend groceries and the long title of note $id",
        "Milk, eggs and bread.\nCall the plumber about the leaking tap in the kitchen and the bathroom.",
        tags,
        pinned,
        false,
        "2026-11-01T10:00:00+00:00",
        "2026-11-03T10:00:00+00:00",
        null,
        sync,
    )

    private val notes = listOf(note(1, pinned = true), note(2, sync = SyncState.Pending), note(3, tags = emptyList()))

    val all: List<Pair<String, @Composable () -> Unit>> =
        listOf(
            "setup" to {
                SetupScreen(
                    SetupUiState(url = "https://abc.supabase.co", urlProblem = UrlProblem.NotReached),
                    {},
                    {},
                    {},
                )
            },
            "login" to {
                LoginScreen(
                    LoginUiState(email = "me@example.com", serverHost = "abc.supabase.co"),
                    {},
                    {},
                    {},
                    {},
                    {},
                    {},
                )
            },
            "notes" to {
                NotesScreen(
                    NoteListUiState(
                        items = notes.map { NoteListItem(it.id, it) },
                        isLoaded = true,
                        tags = listOf(TagCount("home", "home", 3, true), TagCount("errands", "errands", 2, false)),
                        banner = NotesBanner.RefreshFailed(RefreshFailure.Offline),
                    ),
                    NotesActions(),
                    NotesNavigation(),
                )
            },
            "notes-empty" to {
                NotesScreen(
                    NoteListUiState(isLoaded = true, emptyReason = EmptyReason.LoadFailed),
                    NotesActions(),
                    NotesNavigation(),
                )
            },
            "editor" to {
                EditorScreen(
                    EditorUiState(
                        isNew = false,
                        draft =
                            NoteDraft(
                                "Weekend groceries and the long title",
                                "Milk, eggs and bread.",
                                listOf("home", "errands", "shopping", "urgent"),
                                pinned = true,
                            ),
                    ),
                    EditorActions(),
                )
            },
            "archive" to { ArchiveScreen(ArchiveUiState(notes, true), ArchiveActions(), {}, {}) },
            "trash" to { TrashScreen(TrashUiState(notes, true), TrashActions(), {}) },
            "settings" to {
                SettingsScreen(
                    SettingsUiState(settings = AppSettings(theme = Theme.Battery)),
                    SettingsActions(),
                    true,
                    {},
                )
            },
            "about" to { AboutScreen(AboutUiState(), AboutActions(), "1.0.0", {}) },
            "schema" to {
                SchemaOutdatedScreen(
                    SchemaUiState(sql = "create table if not exists public.notes (\n  id bigint\n);"),
                    {},
                )
            },
        )
}
