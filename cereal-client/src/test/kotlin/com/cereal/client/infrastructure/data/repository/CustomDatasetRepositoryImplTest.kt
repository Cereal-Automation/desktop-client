package com.cereal.client.infrastructure.data.repository

import com.cereal.client.application.datasets.InvalidFileException
import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.datasets.CustomDatasetItem
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.model.user.User
import com.cereal.client.fixtures.InMemoryDatasetDataSource
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.csv.CsvReader
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class CustomDatasetRepositoryImplTest {
    private lateinit var repository: CustomDatasetRepositoryImpl

    private val datasetDataSource = InMemoryDatasetDataSource()
    private val userSession = mockk<UserSession>(relaxed = true)
    private val csvReader = CsvReader()

    @TempDir
    lateinit var tempDir: File

    private val user =
        User(
            id = "user-1",
            name = "Test User",
            email = "test@example.com",
            encryptionKey = "key",
            accessToken = "token",
        )

    @BeforeEach
    fun setUp() {
        // MockK: UserSession is a precondition (auth state), not the seam under test.
        coEvery { userSession.requireUser() } returns user
        repository =
            CustomDatasetRepositoryImpl(
                datasetDataSource = datasetDataSource,
                userSession = userSession,
                csvReader = csvReader,
            )
    }

    private fun createDefinition(
        key: String,
        position: Int,
        type: ConfigItemType = ConfigItemType.StringConfigItem,
        isNullable: Boolean = false,
    ): ScriptConfigurationItemDefinition =
        ScriptConfigurationItemDefinition(
            name = key,
            description = "desc",
            key = key,
            position = position,
            type = type,
            isNullable = isNullable,
            stateModifier = null,
            isScriptIdentifier = false,
        )

    private fun createGroup(id: String = "group-1"): CustomDatasetGroup =
        CustomDatasetGroup(
            id = id,
            name = "Group $id",
            numberOfItems = 0,
            itemDefinitions = listOf(createDefinition("name", 0)),
            items = emptySequence(),
            createdAt = Instant.fromEpochMilliseconds(0),
        )

    private fun createItem(): CustomDatasetItem =
        CustomDatasetItem(
            id = UUID.randomUUID(),
            fields = mapOf("name" to ConfigValue.StringValue("value")),
        )

    private fun writeCsv(
        name: String,
        content: String,
    ): File =
        File(tempDir, name).apply {
            writeText(content)
        }

    @Test
    fun `createDatasetGroup makes the group observable via getDatasetGroups`() =
        runTest {
            val group = createGroup()

            repository.createDatasetGroup(group)

            val groups = repository.getDatasetGroups().first()
            assertEquals(listOf(group.id), groups.map { it.id })
            assertEquals("Group group-1", groups.single().name)
        }

    @Test
    fun `updateDatasetGroup changes the stored group name`() =
        runTest {
            val group = createGroup()
            repository.createDatasetGroup(group)

            repository.updateDatasetGroup(group.copy(name = "Renamed"))

            assertEquals(
                "Renamed",
                repository
                    .getDatasetGroups()
                    .first()
                    .single()
                    .name,
            )
        }

    @Test
    fun `deleteDatasetGroup removes the group`() =
        runTest {
            val group = createGroup("group-9")
            repository.createDatasetGroup(group)

            repository.deleteDatasetGroup(group)

            assertTrue(repository.getDatasetGroups().first().isEmpty())
        }

    @Test
    fun `createOrUpdateDataset makes the item observable via getDatasets and getDatasetsFromGroup`() =
        runTest {
            val group = createGroup("group-3")
            repository.createDatasetGroup(group)
            val item = createItem()

            repository.createOrUpdateDataset(item, group)

            assertEquals(listOf(item), repository.getDatasets(group).first())
            assertEquals(listOf(item), repository.getDatasetsFromGroup(group.id))
        }

    @Test
    fun `createOrUpdateDataset updates an existing item in place`() =
        runTest {
            val group = createGroup("group-3")
            repository.createDatasetGroup(group)
            val item = createItem()
            repository.createOrUpdateDataset(item, group)

            val updated = item.copy(fields = mapOf("name" to ConfigValue.StringValue("changed")))
            repository.createOrUpdateDataset(updated, group)

            val stored = repository.getDatasetsFromGroup(group.id)
            assertEquals(1, stored.size)
            assertEquals(ConfigValue.StringValue("changed"), stored.single().fields["name"])
        }

    @Test
    fun `deleteDataset removes the item from the group`() =
        runTest {
            val group = createGroup("group-4")
            repository.createDatasetGroup(group)
            val item = createItem()
            repository.createOrUpdateDataset(item, group)

            repository.deleteDataset(item)

            assertTrue(repository.getDatasetsFromGroup(group.id).isEmpty())
        }

    @Test
    fun `getDatasetsInGroupCount reflects the number of stored items`() =
        runTest {
            val group = createGroup("group-1")
            repository.createDatasetGroup(group)
            repository.createOrUpdateDataset(createItem(), group)
            repository.createOrUpdateDataset(createItem(), group)

            assertEquals(2L, repository.getDatasetsInGroupCount(group.id))
        }

    @Test
    fun `getDatasetGroups starts empty for a fresh user`() =
        runTest {
            assertTrue(repository.getDatasetGroups().first().isEmpty())
        }

    @Test
    fun `readFromFile parses a CSV into dataset items via the definitions`() =
        runTest {
            val definitions =
                listOf(
                    createDefinition("name", 0),
                    createDefinition("age", 1, type = ConfigItemType.IntConfigItem),
                )
            val file =
                writeCsv(
                    "data.csv",
                    """
                    name,age
                    Alice,30
                    Bob,25
                    """.trimIndent(),
                )

            val result = repository.readFromFile(file, definitions)

            assertEquals(2, result.size)
            assertEquals("Alice", result[0].fields["name"]?.raw)
            assertEquals(30, result[0].fields["age"]?.raw)
            assertEquals("Bob", result[1].fields["name"]?.raw)
            assertEquals(25, result[1].fields["age"]?.raw)
        }

    @Test
    fun `readFromFile throws InvalidFileException when a required column is missing`() =
        runTest {
            // The CSV omits the required "name" column, so toDatasetItems raises
            // InvalidDatasetFileException, which the repository wraps as InvalidFileException.
            val definitions = listOf(createDefinition("name", 0))
            val file =
                writeCsv(
                    "bad.csv",
                    """
                    other
                    value
                    """.trimIndent(),
                )

            assertFailsWith<InvalidFileException> {
                repository.readFromFile(file, definitions)
            }
        }

    @Test
    fun `readFromFile throws InvalidFileException when a cell has the wrong type`() =
        runTest {
            // "age" is an Int column but the cell holds text, so parseValue throws
            // NumberFormatException -> InvalidDatasetFileException -> InvalidFileException.
            val definitions =
                listOf(
                    createDefinition("name", 0),
                    createDefinition("age", 1, type = ConfigItemType.IntConfigItem),
                )
            val file =
                writeCsv(
                    "bad-type.csv",
                    """
                    name,age
                    Alice,notanumber
                    """.trimIndent(),
                )

            assertFailsWith<InvalidFileException> {
                repository.readFromFile(file, definitions)
            }
        }
}
