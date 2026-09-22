package com.cereal.client.presentation.tasks.script.overview.configuration

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.files.ReadCustomDatasetFileInteractor
import com.cereal.client.application.interactor.files.ReadListFileInteractor
import com.cereal.client.application.interactor.files.ReadProxyFileInteractor
import com.cereal.client.application.interactor.script.GetScriptConfigDefinitionInteractor
import com.cereal.client.application.interactor.script.GetScriptPackageInstancesByPackageNameInteractor
import com.cereal.client.domain.model.datasets.DatasetType
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigurationItem
import com.cereal.client.domain.model.script.configuration.MAX_LIST_IMPORT_ROWS
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.infrastructure.data.datasource.csv.CsvReader
import com.cereal.client.infrastructure.data.datasource.csv.CsvWriter
import com.cereal.client.infrastructure.data.datasource.filesystem.FileSystemProxyTemplateDataSource
import com.cereal.client.infrastructure.provider.DatasetFileProviderImpl
import com.cereal.client.presentation.error.ErrorAction
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.view.fields.state.ListFieldState
import com.cereal.client.presentation.view.fields.state.TextFieldState
import com.github.kittinunf.result.coroutines.SuspendableResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.reflect.KClass

/**
 * Seam: the script configuration view model, driving a real temporary CSV file through the real
 * interactor, dataset-file provider and CSV reader — a file on disk is a genuine external edge, so it
 * is used for real rather than faked.
 *
 * This owns the whole CSV contract for lists. Assertions are on what the user ends up seeing:
 * the rows in the form, or the message the error surfaces.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScriptConfigurationCsvImportTest {
    enum class Size { SMALL, LARGE }

    @TempDir
    lateinit var tempDir: Path

    private val dispatcher = StandardTestDispatcher()
    private val dispatcherProvider = CoroutinesDispatcherProvider(dispatcher, dispatcher, dispatcher)
    private val errorResolver = ErrorResolver()
    private val scriptPackage: ScriptPackage = mockk(relaxed = true)
    private val getScriptConfigDefinitionInteractor: GetScriptConfigDefinitionInteractor = mockk(relaxed = true)
    private val getScriptPackageInstancesByPackageNameInteractor: GetScriptPackageInstancesByPackageNameInteractor = mockk(relaxed = true)
    private val readListFileInteractor =
        ReadListFileInteractor(
            DatasetFileProviderImpl(CsvReader(), CsvWriter(), FileSystemProxyTemplateDataSource()),
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { getScriptPackageInstancesByPackageNameInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<List<ScriptPackageInstance>, Exception>) -> Unit>()(
                SuspendableResult.Success(emptyList()),
            )
        }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ------------------------------------------------------------------
    // A happy import
    // ------------------------------------------------------------------

    @Test
    fun `imports a well-formed file with a correctly typed value for every field type`() {
        val state =
            importing(
                """
                sku,qty,price,size,notify,note
                ABC-123,2,9.99,LARGE,yes,gift wrap
                XYZ-9,1,0.5,SMALL,no,
                """.trimIndent(),
            )

        assertEquals(
            listOf(
                mapOf(
                    "sku" to "ABC-123",
                    "qty" to 2,
                    "price" to 9.99,
                    "size" to Size.LARGE,
                    "notify" to true,
                    "note" to "gift wrap",
                ),
                mapOf("sku" to "XYZ-9", "qty" to 1, "price" to 0.5, "size" to Size.SMALL, "notify" to false),
            ),
            state.importedRows(),
        )
        assertNoError()
    }

    @Test
    fun `matches columns regardless of case, surrounding spaces or order`() {
        val state =
            importing(
                """
                ` QTY `,Size,SKU
                2,LARGE,ABC-123
                """.trimIndent().replace("`", ""),
            )

        assertEquals(listOf(mapOf("sku" to "ABC-123", "qty" to 2, "size" to Size.LARGE)), state.importedRows())
    }

    @Test
    fun `ignores columns the record does not declare`() {
        val state =
            importing(
                """
                sku,qty,size,warehouse
                ABC-123,2,LARGE,Rotterdam
                """.trimIndent(),
            )

        assertEquals(listOf(mapOf("sku" to "ABC-123", "qty" to 2, "size" to Size.LARGE)), state.importedRows())
    }

    @Test
    fun `leaves an optional field unset on every row when its column is absent`() {
        val state =
            importing(
                """
                sku,qty,size
                ABC-123,2,LARGE
                XYZ-9,1,SMALL
                """.trimIndent(),
            )

        assertTrue(state.importedRows().none { it.containsKey("note") })
        assertTrue(state.importedRows().none { it.containsKey("price") })
    }

    @Test
    fun `treats an empty cell in an optional column as no value`() {
        val state =
            importing(
                """
                sku,qty,size,note
                ABC-123,2,LARGE,
                """.trimIndent(),
            )

        assertEquals(listOf(mapOf("sku" to "ABC-123", "qty" to 2, "size" to Size.LARGE)), state.importedRows())
    }

    @Test
    fun `replaces rows the user already entered`() {
        val state = importing("sku,qty,size\nABC-123,2,LARGE")
        state.fillFirstRow(sku = "TYPED", qty = "7")

        importInto(state, "sku,qty,size\nNEW-1,3,SMALL\nNEW-2,4,LARGE", confirmReplace = true)

        assertEquals(
            listOf(
                mapOf("sku" to "NEW-1", "qty" to 3, "size" to Size.SMALL),
                mapOf("sku" to "NEW-2", "qty" to 4, "size" to Size.LARGE),
            ),
            state.importedRows(),
        )
    }

    @Test
    fun `keeps the imported rows after the file is deleted`() {
        val file = csvFile("sku,qty,size\nABC-123,2,LARGE")
        val state = importFile(file)

        assertTrue(file.delete())

        assertEquals(listOf(mapOf("sku" to "ABC-123", "qty" to 2, "size" to Size.LARGE)), state.importedRows())
    }

    // ------------------------------------------------------------------
    // Delimiters and structure
    // ------------------------------------------------------------------

    @Test
    fun `reads a semicolon-separated file exported by a European spreadsheet`() {
        val state = importing("sku;qty;size\nABC-123;2;LARGE")

        assertEquals(listOf(mapOf("sku" to "ABC-123", "qty" to 2, "size" to Size.LARGE)), state.importedRows())
    }

    @Test
    fun `reads a tab-separated file`() {
        val state = importing("sku\tqty\tsize\nABC-123\t2\tLARGE")

        assertEquals(listOf(mapOf("sku" to "ABC-123", "qty" to 2, "size" to Size.LARGE)), state.importedRows())
    }

    @Test
    fun `reports a file holding nothing but a header row`() {
        importing("sku,qty,size")

        assertTrue(errorMessage().contains("No rows found"), errorMessage())
    }

    @Test
    fun `reports a malformed file readably`() {
        importing("sku,qty,size\n\"unterminated,2,LARGE\nABC,1,SMALL")

        assertTrue(errorMessage().startsWith("The provided file is invalid:"), errorMessage())
    }

    @Test
    fun `rejects a file over the row limit, naming the count and the limit`() {
        val rows = (1..MAX_LIST_IMPORT_ROWS + 1).joinToString("\n") { "SKU-$it,1,SMALL" }
        importing("sku,qty,size\n$rows")

        assertTrue(errorMessage().contains("${MAX_LIST_IMPORT_ROWS + 1} rows"), errorMessage())
        assertTrue(errorMessage().contains("maximum of $MAX_LIST_IMPORT_ROWS rows"), errorMessage())
    }

    // ------------------------------------------------------------------
    // Rejected files
    // ------------------------------------------------------------------

    @Test
    fun `rejects a file missing a required column, naming what is missing`() {
        importing("sku,note\nABC-123,gift wrap")

        assertTrue(errorMessage().contains("'qty'"), errorMessage())
        assertTrue(errorMessage().contains("'size'"), errorMessage())
    }

    @Test
    fun `rejects a file whose headers match the same field twice`() {
        importing("sku,SKU,qty,size\nA,B,1,SMALL")

        assertTrue(errorMessage().contains("more than one column for"), errorMessage())
        assertTrue(errorMessage().contains("'sku', 'SKU'"), errorMessage())
    }

    @Test
    fun `reports an empty required cell with its row and column`() {
        importing("sku,qty,size\nABC-123,,LARGE")

        assertEquals(
            "The provided file is invalid: One problem was found:\nrow 1, column 'qty': a value is required but the cell is empty.",
            errorMessage(),
        )
    }

    @Test
    fun `reports a value that is not a number with its row, column and text`() {
        importing("sku,qty,size\nA,1,SMALL\nB,12x,LARGE")

        assertTrue(errorMessage().contains("row 2, column 'qty': '12x' is not a whole number."), errorMessage())
    }

    @Test
    fun `reports an unrecognised boolean rather than silently importing false`() {
        importing("sku,qty,size,notify\nABC-123,2,LARGE,maybe")

        assertTrue(errorMessage().contains("row 1, column 'notify': 'maybe' is not a yes/no value."), errorMessage())
        assertTrue(errorMessage().contains("true, yes, 1, false, no, 0"), errorMessage())
    }

    @Test
    fun `reports an unmatched enum value alongside the valid constants`() {
        importing("sku,qty,size\nABC-123,2,HUGE")

        assertTrue(errorMessage().contains("row 1, column 'size': 'HUGE' is not one of: SMALL, LARGE."), errorMessage())
    }

    @Test
    fun `reports several problems at once and imports nothing`() {
        val state =
            importing(
                """
                sku,qty,size
                A,12x,LARGE
                B,2,HUGE
                ,3,SMALL
                """.trimIndent(),
            )

        assertTrue(errorMessage().contains("3 problems were found:"), errorMessage())
        assertTrue(errorMessage().contains("row 1, column 'qty'"), errorMessage())
        assertTrue(errorMessage().contains("row 2, column 'size'"), errorMessage())
        assertTrue(errorMessage().contains("row 3, column 'sku'"), errorMessage())
        assertTrue(state.importedRows().isEmpty(), "nothing should have been imported")
    }

    // ------------------------------------------------------------------
    // Replace confirmation
    // ------------------------------------------------------------------

    @Test
    fun `asks before replacing rows the user typed, and imports nothing when cancelled`() {
        val viewModel = createViewModel()
        val state = viewModel.listState()
        state.fillFirstRow(sku = "TYPED", qty = "7")

        selectFile(viewModel, state, csvFile("sku,qty,size\nNEW-1,3,SMALL"))

        val confirmation = viewModel.listReplaceConfirmation.value
        assertEquals(1, confirmation?.existingRowCount)
        assertEquals(1, confirmation?.importedRowCount)

        viewModel.cancelListReplace()

        assertNull(viewModel.listReplaceConfirmation.value)
        assertEquals("TYPED", state.rows.single().stringValue("sku"))
    }

    @Test
    fun `does not ask when the list only holds the untouched blank starter row`() {
        val viewModel = createViewModel()
        val state = viewModel.listState()

        selectFile(viewModel, state, csvFile("sku,qty,size\nNEW-1,3,SMALL"))

        assertNull(viewModel.listReplaceConfirmation.value)
        assertEquals(listOf(mapOf("sku" to "NEW-1", "qty" to 3, "size" to Size.SMALL)), state.importedRows())
    }

    // ------------------------------------------------------------------
    // Availability
    // ------------------------------------------------------------------

    @Test
    fun `offers the import on a list, described by the record's fields`() {
        val viewModel = createViewModel()
        val state = viewModel.listState()

        assertTrue(state.showImportButton())
        state.onImport()

        assertEquals(
            DatasetType.ConfigList(recordFields()),
            viewModel.fileImportConfig.value?.datasetType,
        )
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    private fun importing(contents: String): ListFieldState = importFile(csvFile(contents))

    private fun importFile(file: File): ListFieldState {
        val viewModel = createViewModel()
        val state = viewModel.listState()
        selectFile(viewModel, state, file)
        return state
    }

    private fun importInto(
        state: ListFieldState,
        contents: String,
        confirmReplace: Boolean,
    ) {
        val viewModel = currentViewModel!!
        selectFile(viewModel, state, csvFile(contents))
        if (confirmReplace) viewModel.confirmListReplace()
    }

    private fun selectFile(
        viewModel: ScriptConfigurationViewModel,
        state: ListFieldState,
        file: File,
    ) {
        state.onImport()
        viewModel.onDatasetFileSelected(file)
        dispatcher.scheduler.advanceUntilIdle()
    }

    private var currentViewModel: ScriptConfigurationViewModel? = null

    private fun createViewModel(): ScriptConfigurationViewModel {
        coEvery { getScriptConfigDefinitionInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<GetScriptConfigDefinitionInteractor.Result, Exception>) -> Unit>()(
                SuspendableResult.Success(
                    GetScriptConfigDefinitionInteractor.Result(listOf(ConfigurationItem(listDefinition(), null)), emptyMap()),
                ),
            )
        }
        val viewModel =
            ScriptConfigurationViewModel(
                scriptPackage = scriptPackage,
                initialScriptPackageInstance = null,
                scope = CoroutineScope(dispatcher),
                errorResolver = errorResolver,
                dispatcherProvider = dispatcherProvider,
                getScriptConfigDefinitionInteractor = getScriptConfigDefinitionInteractor,
                getScriptPackageInstancesByPackageNameInteractor = getScriptPackageInstancesByPackageNameInteractor,
                readCustomDatasetFileInteractor = mockk<ReadCustomDatasetFileInteractor>(relaxed = true),
                readProxyFileInteractor = mockk<ReadProxyFileInteractor>(relaxed = true),
                readListFileInteractor = readListFileInteractor,
            )
        dispatcher.scheduler.advanceUntilIdle()
        currentViewModel = viewModel
        return viewModel
    }

    private fun ScriptConfigurationViewModel.listState(): ListFieldState =
        scriptConfigurationForm.value!!
            .mainConfigurationFormSection
            .getFormFieldStates()
            .filterIsInstance<ListFieldState>()
            .single()

    private var fileCounter = 0

    private fun csvFile(contents: String): File = tempDir.resolve("import-${fileCounter++}.csv").toFile().apply { writeText(contents) }

    private fun ListFieldState.importedRows(): List<Map<String, Any>> = fieldValue.rows.map { row -> row.fields.mapValues { it.value.raw } }

    private fun ListFieldState.fillFirstRow(
        sku: String,
        qty: String,
    ) {
        (rows.first().fieldStates.getValue("sku") as TextFieldState<*>).onValueChange(sku)
        (rows.first().fieldStates.getValue("qty") as TextFieldState<*>).onValueChange(qty)
    }

    private fun com.cereal.client.presentation.view.fields.state.ListRowState.stringValue(key: String): String? = (fieldStates.getValue(key) as TextFieldState<*>).text

    private fun errorMessage(): String = (errorResolver.errorAction.value as ErrorAction.Message).message

    private fun assertNoError() = assertEquals(ErrorAction.None, errorResolver.errorAction.value)

    private fun fieldDefinition(
        key: String,
        type: ConfigItemType,
        position: Int,
        isNullable: Boolean = false,
    ) = ScriptConfigurationItemDefinition(
        name = key,
        description = "desc",
        key = key,
        position = position,
        type = type,
        isNullable = isNullable,
        stateModifier = null,
        isScriptIdentifier = false,
    )

    private fun recordFields() =
        listOf(
            fieldDefinition("sku", ConfigItemType.StringConfigItem, position = 0),
            fieldDefinition("qty", ConfigItemType.IntConfigItem, position = 1),
            fieldDefinition("price", ConfigItemType.DoubleConfigItem, position = 2, isNullable = true),
            fieldDefinition(
                "size",
                ConfigItemType.EnumConfigItem(@Suppress("UNCHECKED_CAST") (Size::class as KClass<Enum<*>>)),
                position = 3,
            ),
            fieldDefinition("notify", ConfigItemType.BooleanConfigItem, position = 4, isNullable = true),
            fieldDefinition("note", ConfigItemType.StringConfigItem, position = 5, isNullable = true),
        )

    private fun listDefinition() =
        ScriptConfigurationItemDefinition(
            name = "Targets",
            description = "Products to purchase",
            key = "targets",
            position = 0,
            type = ConfigItemType.ListConfigItem(itemType = Size::class, items = recordFields()),
            isNullable = false,
            stateModifier = null,
            isScriptIdentifier = false,
        )
}
