package com.cereal.client.presentation.tasks.script.overview.configuration.model

import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ConfigurationItem
import com.cereal.client.domain.model.script.configuration.ListRow
import com.cereal.client.domain.model.script.configuration.ListRows
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.presentation.view.fields.state.DropDownFieldState
import com.cereal.client.presentation.view.fields.state.ListFieldState
import com.cereal.client.presentation.view.fields.state.ListRowState
import com.cereal.client.presentation.view.fields.state.StringTextFieldState
import com.cereal.client.presentation.view.fields.state.SwitchFieldState
import com.cereal.client.presentation.view.fields.state.TextFieldState
import com.cereal.sdk.statemodifier.ScriptConfig
import com.cereal.sdk.statemodifier.ScriptConfigValue
import com.cereal.sdk.statemodifier.StateModifier
import com.cereal.sdk.statemodifier.Visibility
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

/**
 * Seam: the configuration form, from a definition (plus any stored values) through to the form state,
 * validation results and the configuration values that would be persisted.
 *
 * This deliberately subsumes what would otherwise be separate field-state, item-mapper and validator
 * tests — it asserts what the user sees and what gets saved, not how the form is shaped inside.
 */
class ConfigurationItemsFormSectionListTest {
    enum class Size { SMALL, LARGE }

    /** Caps the list at two rows, reading the rows back through the SDK's own read contract. */
    object AtMostTwoRows : StateModifier {
        override fun getVisibility(scriptConfig: ScriptConfig): Visibility = Visibility.VisibleRequired

        override fun getError(scriptConfig: ScriptConfig): String? {
            val rows =
                (scriptConfig.valueForKey("targets") as? ScriptConfigValue.ListScriptConfigValue)
                    ?.items
                    .orEmpty()
            return if (rows.size > 2) "At most 2 targets are allowed." else null
        }
    }

    private fun fieldDefinition(
        key: String,
        type: ConfigItemType,
        isNullable: Boolean = false,
    ) = ScriptConfigurationItemDefinition(
        name = key,
        description = "desc",
        key = key,
        position = 0,
        type = type,
        isNullable = isNullable,
        stateModifier = null,
        isScriptIdentifier = false,
    )

    private fun recordFields() =
        listOf(
            fieldDefinition("sku", ConfigItemType.StringConfigItem),
            fieldDefinition("qty", ConfigItemType.IntConfigItem),
            fieldDefinition("size", ConfigItemType.EnumConfigItem(@Suppress("UNCHECKED_CAST") (Size::class as KClass<Enum<*>>))),
            fieldDefinition("note", ConfigItemType.StringConfigItem, isNullable = true),
        )

    private fun listDefinition(
        isNullable: Boolean = false,
        stateModifier: StateModifier? = null,
        fields: List<ScriptConfigurationItemDefinition> = recordFields(),
    ) = ScriptConfigurationItemDefinition(
        name = "Targets",
        description = "Products to purchase",
        key = "targets",
        position = 0,
        type = ConfigItemType.ListConfigItem(itemType = Size::class, items = fields),
        isNullable = isNullable,
        stateModifier = stateModifier,
        isScriptIdentifier = false,
    )

    private fun section(
        definition: ScriptConfigurationItemDefinition = listDefinition(),
        value: ConfigValue? = null,
    ) = ConfigurationItemsFormSection(listOf(ConfigurationItem(definition, value))) { _, _, _ -> }

    private fun ConfigurationItemsFormSection.listState(): ListFieldState = getFormFieldStates().single() as ListFieldState

    private fun ListFieldState.fill(
        rowIndex: Int,
        sku: String? = null,
        qty: String? = null,
        size: Size? = null,
        note: String? = null,
    ) {
        val row = rows[rowIndex]
        sku?.let { (row.fieldStates.getValue("sku") as TextFieldState<*>).onValueChange(it) }
        qty?.let { (row.fieldStates.getValue("qty") as TextFieldState<*>).onValueChange(it) }
        note?.let { (row.fieldStates.getValue("note") as TextFieldState<*>).onValueChange(it) }
        size?.let {
            @Suppress("UNCHECKED_CAST")
            (row.fieldStates.getValue("size") as DropDownFieldState<Any>).onValueChange(it)
        }
    }

    private fun listValues(section: ConfigurationItemsFormSection): ListRows? = (section.getScriptConfigurationValues()["targets"] as? ConfigValue.ListValue)?.raw

    @Test
    fun `renders one editable field per record field, with the widget matching each type`() {
        val row = section().listState().rows.single()

        assertEquals(listOf("sku", "qty", "size", "note"), row.fieldDefinitions.map { it.key })
        assertTrue(row.fieldStates.getValue("sku") is TextFieldState<*>)
        assertTrue(row.fieldStates.getValue("qty") is TextFieldState<*>)
        assertTrue(row.fieldStates.getValue("size") is DropDownFieldState<*>)
    }

    @Test
    fun `renders a switch for a boolean record field`() {
        val definition = listDefinition(fields = listOf(fieldDefinition("notify", ConfigItemType.BooleanConfigItem)))

        val row = section(definition).listState().rows.single()

        assertTrue(row.fieldStates.getValue("notify") is SwitchFieldState)
    }

    @Test
    fun `offers the enum constants as options for an enum record field`() {
        val row = section().listState().rows.single()

        val options = (row.fieldStates.getValue("size") as DropDownFieldState<*>).values.value

        assertEquals(listOf(Size.SMALL, Size.LARGE), options)
    }

    @Test
    fun `produces one saved row per filled row`() {
        val sectionUnderTest = section()
        val state = sectionUnderTest.listState()

        state.fill(0, sku = "ABC-123", qty = "2", size = Size.LARGE)
        state.addRow()
        state.fill(1, sku = "XYZ-9", qty = "1", size = Size.SMALL, note = "hurry")

        assertEquals(
            ListRows(
                listOf(
                    ListRow(
                        mapOf(
                            "sku" to ConfigValue.StringValue("ABC-123"),
                            "qty" to ConfigValue.IntValue(2),
                            "size" to ConfigValue.EnumValue(Size.LARGE),
                        ),
                    ),
                    ListRow(
                        mapOf(
                            "sku" to ConfigValue.StringValue("XYZ-9"),
                            "qty" to ConfigValue.IntValue(1),
                            "size" to ConfigValue.EnumValue(Size.SMALL),
                            "note" to ConfigValue.StringValue("hurry"),
                        ),
                    ),
                ),
            ),
            listValues(sectionUnderTest),
        )
    }

    @Test
    fun `removing a row drops it from the saved value`() {
        val sectionUnderTest = section()
        val state = sectionUnderTest.listState()
        state.fill(0, sku = "ABC-123", qty = "2", size = Size.LARGE)
        state.addRow()
        state.fill(1, sku = "XYZ-9", qty = "1", size = Size.SMALL)

        state.removeRow(0)

        assertEquals(listOf("XYZ-9"), listValues(sectionUnderTest)!!.rows.map { (it.fields.getValue("sku")).raw })
    }

    @Test
    fun `editing a field updates the saved value`() {
        val sectionUnderTest = section()
        val state = sectionUnderTest.listState()
        state.fill(0, sku = "ABC-123", qty = "2", size = Size.LARGE)

        state.fill(0, qty = "5")

        assertEquals(
            ConfigValue.IntValue(5),
            listValues(sectionUnderTest)!!
                .rows
                .single()
                .fields
                .getValue("qty"),
        )
    }

    @Test
    fun `reports the error on the specific field that is missing a value`() {
        val state = section().listState()
        state.fill(0, sku = "ABC-123", size = Size.LARGE)

        val isValid = state.validate()

        assertFalse(isValid)
        assertNotNull(
            state.rows
                .single()
                .fieldStates
                .getValue("qty")
                .error,
        )
        assertNull(
            state.rows
                .single()
                .fieldStates
                .getValue("sku")
                .error,
        )
        // A nullable field left blank is not an error.
        assertNull(
            state.rows
                .single()
                .fieldStates
                .getValue("note")
                .error,
        )
    }

    @Test
    fun `keeps a partially filled row rather than silently discarding it`() {
        val sectionUnderTest = section()
        val state = sectionUnderTest.listState()
        state.fill(0, sku = "ABC-123")

        assertEquals(
            ListRows(listOf(ListRow(mapOf("sku" to ConfigValue.StringValue("ABC-123"))))),
            listValues(sectionUnderTest),
        )
    }

    @Test
    fun `keeps a row holding only input that does not parse, and reports it`() {
        val sectionUnderTest = section()
        val state = sectionUnderTest.listState()
        state.fill(0, qty = "not-a-number")

        assertFalse(state.validate())
        // The row survives with its unparsable field reported rather than being silently discarded.
        assertNotNull(
            state.rows
                .single()
                .fieldStates
                .getValue("qty")
                .error,
        )
        assertNotNull(
            state.rows
                .single()
                .fieldStates
                .getValue("sku")
                .error,
        )
    }

    @Test
    fun `a nullable boolean field left untouched saves nothing`() {
        val definition =
            listDefinition(
                fields =
                    listOf(
                        fieldDefinition("sku", ConfigItemType.StringConfigItem),
                        fieldDefinition("notify", ConfigItemType.BooleanConfigItem, isNullable = true),
                    ),
            )
        val sectionUnderTest = section(definition)
        val state = sectionUnderTest.listState()
        state.fill(0, sku = "ABC-123")

        // A switch always holds true or false, so "never touched" must not persist as false — otherwise a
        // nullable boolean could never reach the script as null.
        assertEquals(
            ListRows(listOf(ListRow(mapOf("sku" to ConfigValue.StringValue("ABC-123"))))),
            listValues(sectionUnderTest),
        )
    }

    @Test
    fun `a nullable boolean field the user toggled saves its value`() {
        val definition =
            listDefinition(
                fields =
                    listOf(
                        fieldDefinition("sku", ConfigItemType.StringConfigItem),
                        fieldDefinition("notify", ConfigItemType.BooleanConfigItem, isNullable = true),
                    ),
            )
        val sectionUnderTest = section(definition)
        val state = sectionUnderTest.listState()
        state.fill(0, sku = "ABC-123")
        (
            state.rows
                .single()
                .fieldStates
                .getValue("notify") as SwitchFieldState
        ).onValueChange(false)

        assertEquals(
            ConfigValue.BooleanValue(false),
            listValues(sectionUnderTest)!!
                .rows
                .single()
                .fields
                .getValue("notify"),
        )
    }

    @Test
    fun `a non-nullable boolean field always saves a value`() {
        val definition =
            listDefinition(
                fields =
                    listOf(
                        fieldDefinition("sku", ConfigItemType.StringConfigItem),
                        fieldDefinition("notify", ConfigItemType.BooleanConfigItem),
                    ),
            )
        val sectionUnderTest = section(definition)
        val state = sectionUnderTest.listState()
        state.fill(0, sku = "ABC-123")

        assertTrue(state.validate())
        assertEquals(
            ConfigValue.BooleanValue(false),
            listValues(sectionUnderTest)!!
                .rows
                .single()
                .fields
                .getValue("notify"),
        )
    }

    @Test
    fun `a complete row validates`() {
        val state = section().listState()
        state.fill(0, sku = "ABC-123", qty = "2", size = Size.LARGE)

        assertTrue(state.validate())
    }

    @Test
    fun `a non-nullable list with no rows is invalid`() {
        val state = section().listState()

        assertFalse(state.validate())
        assertNotNull(state.error)
    }

    @Test
    fun `a nullable list with no rows is valid and saves nothing`() {
        val sectionUnderTest = section(listDefinition(isNullable = true))
        val state = sectionUnderTest.listState()

        assertTrue(state.validate())
        assertNull(sectionUnderTest.getScriptConfigurationValues()["targets"])
    }

    @Test
    fun `the list items own state modifier can cap the number of rows`() {
        val sectionUnderTest = section(listDefinition(stateModifier = AtMostTwoRows))
        val state = sectionUnderTest.listState()
        state.fill(0, sku = "A", qty = "1", size = Size.SMALL)
        state.addRow()
        state.fill(1, sku = "B", qty = "1", size = Size.SMALL)

        assertTrue(state.validate())

        state.addRow()
        state.fill(2, sku = "C", qty = "1", size = Size.SMALL)

        assertFalse(state.validate())
        assertEquals("At most 2 targets are allowed.", state.error)
    }

    @Test
    fun `shows the stored rows again when the configuration is reopened`() {
        val stored =
            ListRows(
                listOf(
                    ListRow(
                        mapOf(
                            "sku" to ConfigValue.StringValue("ABC-123"),
                            "qty" to ConfigValue.IntValue(2),
                            "size" to ConfigValue.EnumValue(Size.LARGE),
                        ),
                    ),
                ),
            )
        val sectionUnderTest = section(value = ConfigValue.ListValue(stored))

        assertEquals(stored, listValues(sectionUnderTest))
        assertTrue(sectionUnderTest.listState().validate())
    }

    @Test
    fun `copying an existing configuration brings its rows along`() {
        val sectionUnderTest = section()
        val copied =
            ListRows(
                listOf(
                    ListRow(
                        mapOf(
                            "sku" to ConfigValue.StringValue("XYZ-9"),
                            "qty" to ConfigValue.IntValue(3),
                            "size" to ConfigValue.EnumValue(Size.SMALL),
                        ),
                    ),
                ),
            )

        sectionUnderTest.applyConfigurationValues(mapOf("targets" to ConfigValue.ListValue(copied)))

        assertEquals(copied, listValues(sectionUnderTest))
    }

    @Test
    fun `hides the list when the script says it is irrelevant`() {
        val hidden =
            object : StateModifier {
                override fun getVisibility(scriptConfig: ScriptConfig): Visibility = Visibility.Hidden

                override fun getError(scriptConfig: ScriptConfig): String? = null
            }
        val sectionUnderTest = section(listDefinition(stateModifier = hidden))

        sectionUnderTest.applyStateModifiers()

        assertFalse(sectionUnderTest.listState().isVisible.value)
    }

    @Test
    fun `the untouched blank starter row does not count as entered`() {
        val state = section().listState()

        assertEquals(0, state.enteredRowCount())

        state.fill(0, sku = "ABC-123")

        assertEquals(1, state.enteredRowCount())
    }

    @Test
    fun `values stay correct after editing, adding and removing rows`() {
        val sectionUnderTest = section()
        val state = sectionUnderTest.listState()
        state.fill(0, sku = "A", qty = "1", size = Size.SMALL)
        state.addRow()
        state.fill(1, sku = "B", qty = "2", size = Size.LARGE)
        state.fill(0, qty = "9")
        state.addRow()
        state.fill(2, sku = "C", qty = "3", size = Size.SMALL)

        state.removeRow(1)

        assertEquals(
            listOf("A" to 9, "C" to 3),
            listValues(sectionUnderTest)!!.rows.map { it.fields.getValue("sku").raw to it.fields.getValue("qty").raw },
        )
    }

    /**
     * Every keystroke collects the whole configuration's values twice, and each collection walks every
     * row. Without per-row memoisation, typing one character in a list of two thousand imported rows
     * re-parses every cell of every row — reachable only once a CSV can put that many rows on screen.
     *
     * Counted rather than timed, so the guarantee survives a faster or slower machine.
     */
    @Test
    fun `editing one row re-reads only that row's values`() {
        val reads = mutableMapOf<Int, Int>()
        var rowsCreated = 0
        val fields = listOf(fieldDefinition("sku", ConfigItemType.StringConfigItem))
        val state =
            ListFieldState(
                fieldDefinitions = fields,
                createRow = { _, onRowValueChange ->
                    val index = rowsCreated++
                    val row = ListRowState(fields, onRowValueChange)
                    row.putFieldState(
                        "sku",
                        CountingStringFieldState(
                            onRead = { reads[index] = (reads[index] ?: 0) + 1 },
                            onFieldValueChanged = { row.onFieldValueChanged() },
                        ),
                    )
                    row
                },
            )
        repeat(2) { state.addRow() }
        state.rows.forEachIndexed { index, row ->
            (row.fieldStates.getValue("sku") as TextFieldState<*>).onValueChange("row-$index")
        }
        // Warm every row, then watch what a single keystroke costs.
        state.fieldValue
        reads.clear()

        (state.rows[1].fieldStates.getValue("sku") as TextFieldState<*>).onValueChange("edited")
        val values = state.fieldValue

        assertEquals(setOf(1), reads.keys)
        assertEquals(listOf("row-0", "edited", "row-2"), values.rows.map { it.fields.getValue("sku").raw })
    }

    private class CountingStringFieldState(
        private val onRead: () -> Unit,
        onFieldValueChanged: () -> Unit,
    ) : StringTextFieldState(onValueChange = { onFieldValueChanged() }) {
        override fun getValidatedValue(): String? {
            onRead()
            return super.getValidatedValue()
        }
    }
}
