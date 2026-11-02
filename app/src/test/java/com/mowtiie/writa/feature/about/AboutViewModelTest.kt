package com.mowtiie.writa.feature.about

import com.mowtiie.writa.core.assets.AssetReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AboutViewModelTest {
    private val assets = FakeAssets()

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = AboutViewModel(assets)

    @Test
    fun theLicenseIsNotShownUntilAskedFor() =
        runTest {
            assertEquals(LicenseState.Hidden, viewModel().state.value.license)
        }

    @Test
    fun openingTheLicenseShowsItsText() =
        runTest {
            val viewModel = viewModel()

            viewModel.onLicenseOpen()
            advanceUntilIdle()

            assertEquals(LicenseState.Shown("the license"), viewModel.state.value.license)
            assertEquals(listOf(AssetReader.LICENSE), assets.requested)
        }

    @Test
    fun aLicenseThatCannotBeReadIsReportedInsteadOfCrashing() =
        runTest {
            assets.failure = IOException("gone")
            val viewModel = viewModel()

            viewModel.onLicenseOpen()
            advanceUntilIdle()

            assertEquals(LicenseState.Unreadable, viewModel.state.value.license)
        }

    @Test
    fun closingTheLicenseHidesIt() =
        runTest {
            val viewModel = viewModel()
            viewModel.onLicenseOpen()
            advanceUntilIdle()

            viewModel.onLicenseClose()

            assertEquals(LicenseState.Hidden, viewModel.state.value.license)
        }

    @Test
    fun tappingTheVersionSevenTimesShowsTheEasterEggOnce() =
        runTest {
            val viewModel = viewModel()

            repeat(6) { viewModel.onVersionTap() }
            assertFalse(viewModel.state.value.easterEgg)

            viewModel.onVersionTap()
            assertTrue(viewModel.state.value.easterEgg)

            viewModel.onEasterEggShown()
            assertFalse(viewModel.state.value.easterEgg)

            viewModel.onVersionTap()
            assertFalse(viewModel.state.value.easterEgg)
        }

    private class FakeAssets : AssetReader {
        val requested = mutableListOf<String>()
        var failure: IOException? = null

        override suspend fun read(name: String): String {
            requested += name
            failure?.let { throw it }
            return "the license"
        }
    }
}
