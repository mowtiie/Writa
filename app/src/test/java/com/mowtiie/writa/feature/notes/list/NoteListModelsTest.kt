package com.mowtiie.writa.feature.notes.list

import com.mowtiie.writa.feature.notes.RefreshFailure
import com.mowtiie.writa.feature.notes.RefreshState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

class NoteListModelsTest {
    // ---------------------------- the banner ----------------------------

    @Test
    fun noBannerWhenNothingFailed() {
        assertNull(NotesBanner.of(RefreshState.Idle, pendingChanges = 0))
        assertNull(NotesBanner.of(RefreshState.Idle, pendingChanges = 5))
    }

    @Test
    fun noBannerWhileLoadingBecauseTheRefreshIndicatorSaysSo() {
        assertNull(NotesBanner.of(RefreshState.Loading, pendingChanges = 0))
        assertNull(NotesBanner.of(RefreshState.Loading, pendingChanges = 3))
    }

    @Test
    fun aFailedRefreshShowsItsReason() {
        for (reason in RefreshFailure.entries) {
            assertEquals(
                NotesBanner.RefreshFailed(reason),
                NotesBanner.of(RefreshState.Failed(reason), pendingChanges = 0),
            )
        }
    }

    @Test
    fun beingOfflineWithUnsentChangesSaysHowManyWillUpload() {
        val banner = NotesBanner.of(RefreshState.Failed(RefreshFailure.Offline), pendingChanges = 3)

        assertEquals(NotesBanner.UnsentChanges(3), banner)
    }

    @Test
    fun unsentChangesOnlyMatterWhenTheProblemIsBeingOffline() {
        val banner = NotesBanner.of(RefreshState.Failed(RefreshFailure.Server), pendingChanges = 3)

        assertEquals(NotesBanner.RefreshFailed(RefreshFailure.Server), banner)
    }

    // ---------------------------- the empty list ----------------------------

    @Test
    fun nothingIsSaidWhileTheNotesAreStillLoading() {
        assertNull(EmptyReason.of(isLoaded = false, totalNotes = 0, shown = 0, search = ""))
    }

    @Test
    fun nothingIsSaidWhenThereIsSomethingToShow() {
        assertNull(EmptyReason.of(isLoaded = true, totalNotes = 5, shown = 2, search = "x"))
    }

    @Test
    fun noNotesAtAllIsToldApartFromNoMatches() {
        assertEquals(EmptyReason.NoNotes, EmptyReason.of(true, totalNotes = 0, shown = 0, search = ""))
        assertEquals(EmptyReason.NoNotes, EmptyReason.of(true, totalNotes = 0, shown = 0, search = "milk"))
    }

    @Test
    fun aSearchWithoutMatchesNamesTheSearch() {
        assertEquals(
            EmptyReason.NoMatchForSearch("milk"),
            EmptyReason.of(true, totalNotes = 4, shown = 0, search = "  milk "),
        )
    }

    @Test
    fun filteringByTagsWithoutMatchesIsToldApartFromSearching() {
        assertEquals(EmptyReason.NoMatchForTags, EmptyReason.of(true, totalNotes = 4, shown = 0, search = ""))
        assertEquals(EmptyReason.NoMatchForTags, EmptyReason.of(true, totalNotes = 4, shown = 0, search = "   "))
    }

    @Test
    fun aFailedLoadWithNothingSavedIsToldApartFromNoNotes() {
        assertEquals(
            EmptyReason.LoadFailed,
            EmptyReason.of(true, totalNotes = 0, shown = 0, search = "", loadFailed = true),
        )
    }

    @Test
    fun aFailedLoadDoesNotHideSavedNotesThatDoNotMatch() {
        assertEquals(
            EmptyReason.NoMatchForTags,
            EmptyReason.of(true, totalNotes = 4, shown = 0, search = "", loadFailed = true),
        )
    }

    // ---------------------------- dates ----------------------------

    private val dates = NoteDates(ZoneId.of("UTC"), Locale.US)

    @Test
    fun aTimestampBecomesAnEasyToReadDate() {
        assertEquals("Nov 1, 2026", dates.format("2026-11-01T10:00:00.123456+00:00"))
        assertEquals("Nov 1, 2026", dates.format("2026-11-01T10:00:00Z"))
    }

    @Test
    fun theDateIsTheUsersOwnDayNotTheServers() {
        val auckland = NoteDates(ZoneId.of("Pacific/Auckland"), Locale.US)

        assertEquals("Nov 2, 2026", auckland.format("2026-11-01T20:00:00+00:00"))
        assertEquals("Nov 1, 2026", dates.format("2026-11-01T20:00:00+00:00"))
    }

    @Test
    fun theLanguageFollowsTheLocale() {
        val german = NoteDates(ZoneId.of("UTC"), Locale.GERMANY).format("2026-11-01T10:00:00Z")

        assertNotEquals(dates.format("2026-11-01T10:00:00Z"), german)
        assertTrue(german.orEmpty().contains("2026"))
    }

    @Test
    fun missingOrUnreadableTimestampsHaveNoDate() {
        assertNull(dates.format(null))
        assertNull(dates.format(""))
        assertNull(dates.format("yesterday"))
    }
}
