package com.cereal.client.domain.model.datasets

import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.model.script.configuration.Secret
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CustomDatasetFileMapperTest {
    private enum class Size { SMALL, LARGE }

    private fun definition(
        key: String,
        type: ConfigItemType,
        isNullable: Boolean,
    ) = ScriptConfigurationItemDefinition(
        name = key,
        description = "",
        key = key,
        position = 0,
        type = type,
        isNullable = isNullable,
        stateModifier = null,
        isScriptIdentifier = false,
    )

    @Test
    fun `maps rows to dataset items converting each field to its configured type`() {
        val definitions =
            listOf(
                definition("name", ConfigItemType.StringConfigItem, isNullable = false),
                definition("age", ConfigItemType.IntConfigItem, isNullable = false),
                definition("active", ConfigItemType.BooleanConfigItem, isNullable = false),
            )
        val rows =
            listOf(
                mapOf("name" to "Alice", "age" to "30", "active" to "true"),
                mapOf("name" to "Bob", "age" to "25", "active" to "false"),
            )

        val items = definitions.toDatasetItems(rows)

        assertEquals(2, items.size)
        assertEquals(ConfigValue.StringValue("Alice"), items[0].fields["name"])
        assertEquals(ConfigValue.IntValue(30), items[0].fields["age"])
        assertEquals(ConfigValue.BooleanValue(true), items[0].fields["active"])
        assertEquals(ConfigValue.BooleanValue(false), items[1].fields["active"])
    }

    @Test
    fun `treats empty cells as null for nullable fields`() {
        val definitions =
            listOf(
                definition("name", ConfigItemType.StringConfigItem, isNullable = false),
                definition("nickname", ConfigItemType.StringConfigItem, isNullable = true),
            )
        val rows = listOf(mapOf("name" to "Alice", "nickname" to ""))

        val items = definitions.toDatasetItems(rows)

        assertEquals(ConfigValue.StringValue("Alice"), items[0].fields["name"])
        assertNull(items[0].fields["nickname"])
    }

    @Test
    fun `throws when a required column is missing from the CSV`() {
        val definitions =
            listOf(
                definition("name", ConfigItemType.StringConfigItem, isNullable = false),
                definition("age", ConfigItemType.IntConfigItem, isNullable = false),
            )
        val rows = listOf(mapOf("name" to "Alice"))

        val exception =
            assertFailsWith<InvalidDatasetFileException> {
                definitions.toDatasetItems(rows)
            }
        assertEquals("Missing required column(s): 'age'. The file has: 'name'.", exception.message)
    }

    @Test
    fun `throws when a cell cannot be converted to its type`() {
        val definitions = listOf(definition("age", ConfigItemType.IntConfigItem, isNullable = false))
        val rows = listOf(mapOf("age" to "not-a-number"))

        val exception =
            assertFailsWith<InvalidDatasetFileException> {
                definitions.toDatasetItems(rows)
            }
        assertEquals(
            "One problem was found:\nrow 1, column 'age': 'not-a-number' is not a whole number.",
            exception.message,
        )
    }

    @Test
    fun `throws when a non-nullable field is empty`() {
        val definitions = listOf(definition("name", ConfigItemType.StringConfigItem, isNullable = false))
        val rows = listOf(mapOf("name" to ""))

        val exception =
            assertFailsWith<InvalidDatasetFileException> {
                definitions.toDatasetItems(rows)
            }
        assertEquals(
            "One problem was found:\nrow 1, column 'name': a value is required but the cell is empty.",
            exception.message,
        )
    }

    @Test
    fun `rejects an unrecognised boolean rather than importing it as false`() {
        // Behaviour change: a dataset CSV holding "Y" used to import as false. A loud rejection beats
        // a dataset that silently means something other than what the file says.
        val definitions = listOf(definition("active", ConfigItemType.BooleanConfigItem, isNullable = false))

        val exception =
            assertFailsWith<InvalidDatasetFileException> {
                definitions.toDatasetItems(listOf(mapOf("active" to "Y")))
            }

        assertEquals(
            "One problem was found:\nrow 1, column 'active': " +
                "'Y' is not a yes/no value. Accepted: true, yes, 1, false, no, 0.",
            exception.message,
        )
    }

    @Test
    fun `accepts the unambiguous boolean spellings`() {
        val definitions = listOf(definition("active", ConfigItemType.BooleanConfigItem, isNullable = false))

        val items = definitions.toDatasetItems(listOf(mapOf("active" to "yes"), mapOf("active" to "0")))

        assertEquals(ConfigValue.BooleanValue(true), items[0].fields["active"])
        assertEquals(ConfigValue.BooleanValue(false), items[1].fields["active"])
    }

    @Test
    fun `rejects an enum value matching no constant rather than discarding it`() {
        @Suppress("UNCHECKED_CAST")
        val definitions =
            listOf(
                definition("size", ConfigItemType.EnumConfigItem(Size::class as kotlin.reflect.KClass<Enum<*>>), isNullable = true),
            )

        val exception =
            assertFailsWith<InvalidDatasetFileException> {
                definitions.toDatasetItems(listOf(mapOf("size" to "HUGE")))
            }

        assertEquals(
            "One problem was found:\nrow 1, column 'size': 'HUGE' is not one of: SMALL, LARGE.",
            exception.message,
        )
    }

    // region Resilience

    @Test
    fun `matches a column to its field regardless of capitalisation and surrounding space`() {
        val definitions =
            listOf(
                definition("name", ConfigItemType.StringConfigItem, isNullable = false),
                definition("age", ConfigItemType.IntConfigItem, isNullable = false),
            )

        val items = definitions.toDatasetItems(listOf(mapOf("Name" to "Alice", " AGE " to "30")))

        assertEquals(ConfigValue.StringValue("Alice"), items[0].fields["name"])
        assertEquals(ConfigValue.IntValue(30), items[0].fields["age"])
    }

    @Test
    fun `ignores a column the definitions do not declare`() {
        val definitions = listOf(definition("name", ConfigItemType.StringConfigItem, isNullable = false))

        val items = definitions.toDatasetItems(listOf(mapOf("name" to "Alice", "notes" to "ignore me")))

        assertEquals(mapOf<String, ConfigValue?>("name" to ConfigValue.StringValue("Alice")), items[0].fields)
    }

    @Test
    fun `accepts an absent column for a nullable field`() {
        val definitions =
            listOf(
                definition("name", ConfigItemType.StringConfigItem, isNullable = false),
                definition("nickname", ConfigItemType.StringConfigItem, isNullable = true),
            )

        val items = definitions.toDatasetItems(listOf(mapOf("name" to "Alice")))

        assertEquals(ConfigValue.StringValue("Alice"), items[0].fields["name"])
        assertTrue(items[0].fields.containsKey("nickname"))
        assertNull(items[0].fields["nickname"])
    }

    @Test
    fun `treats a whitespace-only cell as no value rather than as text`() {
        val definitions = listOf(definition("nickname", ConfigItemType.StringConfigItem, isNullable = true))

        val items = definitions.toDatasetItems(listOf(mapOf("nickname" to "   ")))

        assertNull(items[0].fields["nickname"])
    }

    @Test
    fun `reports every problem in the file at once instead of only the first`() {
        // Fixing a spreadsheet one rejected cell per attempt is the failure this replaces.
        val definitions =
            listOf(
                definition("name", ConfigItemType.StringConfigItem, isNullable = false),
                definition("age", ConfigItemType.IntConfigItem, isNullable = false),
            )
        val rows =
            listOf(
                mapOf("name" to "Alice", "age" to "thirty"),
                mapOf("name" to "", "age" to "25"),
            )

        val exception = assertFailsWith<InvalidDatasetFileException> { definitions.toDatasetItems(rows) }

        assertEquals(
            "2 problems were found:\n" +
                "row 1, column 'age': 'thirty' is not a whole number.\n" +
                "row 2, column 'name': a value is required but the cell is empty.",
            exception.message,
        )
    }

    @Test
    fun `rejects a file whose header names the same field twice`() {
        val definitions = listOf(definition("name", ConfigItemType.StringConfigItem, isNullable = false))

        val exception =
            assertFailsWith<InvalidDatasetFileException> {
                definitions.toDatasetItems(listOf(mapOf("name" to "Alice", "NAME" to "Bob")))
            }

        assertEquals("The file has more than one column for: 'name', 'NAME'.", exception.message)
    }

    @Test
    fun `rejects a file holding no data rows`() {
        val definitions = listOf(definition("name", ConfigItemType.StringConfigItem, isNullable = false))

        val exception = assertFailsWith<InvalidDatasetFileException> { definitions.toDatasetItems(emptyList()) }

        assertEquals("No rows found — the file contains only a header row.", exception.message)
    }

    // endregion

    // region Secret

    @Test
    fun `imports a credential column into secret values`() {
        // The point of ticket 07: hundreds of tasks each with their own credential, set up by
        // importing a CSV rather than typing every key by hand.
        val definitions =
            listOf(
                definition("account", ConfigItemType.StringConfigItem, isNullable = false),
                definition("apiKey", ConfigItemType.SecretConfigItem, isNullable = false),
            )
        val rows =
            listOf(
                mapOf("account" to "alice", "apiKey" to "sk-live-alice"),
                mapOf("account" to "bob", "apiKey" to "sk-live-bob"),
            )

        val items = definitions.toDatasetItems(rows)

        assertEquals(ConfigValue.SecretValue(Secret("sk-live-alice")), items[0].fields["apiKey"])
        assertEquals(ConfigValue.SecretValue(Secret("sk-live-bob")), items[1].fields["apiKey"])
    }

    @Test
    fun `an imported credential is masked on render but revealable`() {
        val definitions = listOf(definition("apiKey", ConfigItemType.SecretConfigItem, isNullable = false))

        val item = definitions.toDatasetItems(listOf(mapOf("apiKey" to "sk-live-alice"))).single()

        val value = item.fields["apiKey"] as ConfigValue.SecretValue
        assertEquals(Secret.MASK, value.raw.toString())
        assertEquals("sk-live-alice", value.raw.reveal())
    }

    @Test
    fun `an empty required credential cell is rejected like any other required field`() {
        val definitions = listOf(definition("apiKey", ConfigItemType.SecretConfigItem, isNullable = false))

        val exception =
            assertFailsWith<InvalidDatasetFileException> {
                definitions.toDatasetItems(listOf(mapOf("apiKey" to "")))
            }
        assertEquals(
            "One problem was found:\nrow 1, column 'apiKey': a value is required but the cell is empty.",
            exception.message,
        )
    }

    // endregion
}
