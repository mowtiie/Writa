package com.mowtiie.writa.feature.notes.list

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
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SchemaViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun assets(
        text: String? = "create table notes ();",
        asked: MutableList<String> = mutableListOf(),
    ) = object : AssetReader {
        override suspend fun read(name: String): String {
            asked += name
            return text ?: throw IOException("missing")
        }
    }

    @Test
    fun theSqlIsNotThereUntilItHasBeenRead() =
        runTest {
            assertEquals(SchemaUiState(), SchemaViewModel(assets()).state.value)
        }

    @Test
    fun theSchemaFileOfTheAppIsRead() =
        runTest {
            val asked = mutableListOf<String>()
            val viewModel = SchemaViewModel(assets(asked = asked))

            advanceUntilIdle()

            assertEquals(SchemaUiState(sql = "create table notes ();"), viewModel.state.value)
            assertEquals(listOf(AssetReader.SCHEMA), asked)
        }

    @Test
    fun aMissingFileIsReportedInsteadOfCrashing() =
        runTest {
            val viewModel = SchemaViewModel(assets(text = null))

            advanceUntilIdle()

            assertEquals(SchemaUiState(unreadable = true), viewModel.state.value)
        }
}
