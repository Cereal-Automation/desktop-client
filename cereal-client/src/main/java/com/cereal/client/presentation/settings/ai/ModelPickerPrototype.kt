package com.cereal.client.presentation.settings.ai

// PROTOTYPE (wayfinder #249), throwaway. Three model picker variants, mounted in place of the model field in
// both Settings → AI (LlmPresetForm) and the Home connect card (HomeConnectForm). The black strip under the
// picker switches variant (A/B/C) and list state; both surfaces share it. Fake data, no network, no saving.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealButton
import com.cereal.client.presentation.view.CerealCircularProgressIndicator
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.CerealTextField
import com.cereal.client.presentation.view.CerealToggle
import com.cereal.client.presentation.view.Dialog

private data class FakeModel(
    val id: String,
    val name: String?,
    val created: String,
    val free: Boolean,
)

// Newest first, as the picker sorts. Mix of named/unnamed and free/paid, like OpenRouter.
private val fakeModels =
    listOf(
        FakeModel("anthropic/claude-opus-5.5", "Claude Opus 5.5", "2026-10-14", false),
        FakeModel("google/gemini-3.5-flash:free", "Gemini 3.5 Flash (free)", "2026-10-02", true),
        FakeModel("openai/gpt-6-mini", null, "2026-09-21", false),
        FakeModel("meta-llama/llama-5-70b-instruct:free", "Llama 5 70B Instruct (free)", "2026-08-30", true),
        FakeModel("anthropic/claude-sonnet-5.5", "Claude Sonnet 5.5", "2026-08-11", false),
        FakeModel("mistralai/mistral-medium-4", "Mistral Medium 4", "2026-07-19", false),
        FakeModel("qwen/qwen-4-32b:free", "Qwen 4 32B (free)", "2026-06-03", true),
        FakeModel("deepseek/deepseek-v5", null, "2026-05-12", false),
        FakeModel("openai/gpt-5.1", "GPT-5.1", "2026-03-27", false),
        FakeModel("anthropic/claude-haiku-5.5", "Claude Haiku 5.5", "2026-02-09", false),
    )

private enum class ListState(
    val label: String,
) {
    NO_KEY("No key"),
    LOADING("Loading"),
    LOADED("Loaded"),
    FAILED("Failed"),
    MISSING("Saved id missing"),
}

private object Proto {
    var variant by mutableStateOf('A')
    var state by mutableStateOf(ListState.LOADED)

    // Default: newest $0 model, else newest.
    var selectedId by mutableStateOf((fakeModels.firstOrNull { it.free } ?: fakeModels.first()).id)
    var freeText by mutableStateOf("")
}

private const val MISSING_ID = "anthropic/claude-2.1"

/** Drop-in for the model field. [compact] is the Home card's denser layout. */
@Composable
fun ModelPickerPrototype(compact: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val selectedId = if (Proto.state == ListState.MISSING) MISSING_ID else Proto.selectedId
        when (Proto.state) {
            ListState.NO_KEY -> DisabledField("Chosen after you add your key")

            ListState.LOADING ->
                DisabledField("Loading models…") {
                    CerealCircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp)
                }

            ListState.FAILED -> FreeTextFallback()

            ListState.LOADED, ListState.MISSING ->
                when (Proto.variant) {
                    'A' -> VariantA(selectedId)
                    'B' -> VariantB(selectedId)
                    else -> VariantC(selectedId, compact)
                }
        }
        PrototypeStrip()
    }
}

// ---- Variant A: combobox. The field shows the pick; opening it gives a popup with search on top. ----

@Composable
private fun VariantA(selectedId: String) {
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    Box {
        SelectedBox(selectedId, modifier = Modifier.clickable { open = true }) {
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = CerealTheme.colorScheme.contentTertiary)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            properties = PopupProperties(focusable = true),
            modifier = Modifier.width(460.dp),
        ) {
            Box(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                SearchField(query) { query = it }
            }
            Box(Modifier.heightIn(max = 320.dp)) {
                LazyColumn {
                    items(filter(query)) { model ->
                        ModelRow(model, selected = model.id == selectedId, showDate = true) {
                            Proto.selectedId = model.id
                            open = false
                        }
                    }
                }
            }
        }
    }
}

// ---- Variant B: summary + Change, which opens a full dialog with search and a Free-only filter. ----

@Composable
private fun VariantB(selectedId: String) {
    var open by remember { mutableStateOf(false) }
    SelectedBox(selectedId) {
        CerealText(
            "Change",
            modifier = Modifier.clickable { open = true },
            color = CerealTheme.colorScheme.link,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (open) {
        var query by remember { mutableStateOf("") }
        var freeOnly by remember { mutableStateOf(false) }
        Dialog(title = "Choose a model", onDismissRequest = { open = false }) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) { SearchField(query) { query = it } }
                    CerealText("Free only", style = MaterialTheme.typography.bodyMedium)
                    CerealToggle(checked = freeOnly, onCheckedChange = { freeOnly = it })
                }
                CerealText(
                    "${fakeModels.size} models from OpenRouter, newest first",
                    style = MaterialTheme.typography.bodySmall,
                    color = CerealTheme.colorScheme.contentTertiary,
                )
                LazyColumn {
                    items(filter(query).filter { !freeOnly || it.free }) { model ->
                        ModelRow(model, selected = model.id == selectedId, showDate = true) {
                            Proto.selectedId = model.id
                            open = false
                        }
                    }
                }
            }
        }
    }
}

// ---- Variant C: no popup. Search field above an always-visible scrolling radio list. ----

@Composable
private fun VariantC(
    selectedId: String,
    compact: Boolean,
) {
    var query by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SearchField(query) { query = it }
        val shape = RoundedCornerShape(8.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (compact) 180.dp else 240.dp)
                .clip(shape)
                .border(1.dp, CerealTheme.colorScheme.border, shape),
        ) {
            LazyColumn {
                if (selectedId == MISSING_ID) {
                    item { MissingRow() }
                }
                items(filter(query)) { model ->
                    ModelRow(model, selected = model.id == selectedId, showDate = !compact, radio = true) {
                        Proto.selectedId = model.id
                    }
                }
            }
        }
    }
}

// ---- Shared bits (prototype-grade) ----

@Composable
private fun SelectedBox(
    selectedId: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    val model = fakeModels.firstOrNull { it.id == selectedId }
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(CerealTheme.colorScheme.inputBackground)
                .border(1.dp, CerealTheme.colorScheme.border, shape)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (model == null) {
                CerealText(selectedId, style = MaterialTheme.typography.bodyMedium)
                CerealText("Not offered by provider", style = MaterialTheme.typography.bodySmall, color = CerealTheme.colorScheme.warning)
            } else {
                NameAndId(model)
            }
        }
        if (model?.free == true) FreeBadge()
        Box(Modifier.padding(start = 10.dp)) { trailing() }
    }
}

@Composable
private fun ModelRow(
    model: FakeModel,
    selected: Boolean,
    showDate: Boolean,
    radio: Boolean = false,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(if (selected) primary.copy(alpha = 0.08f) else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (radio) {
            Box(Modifier.size(14.dp).border(if (selected) 4.dp else 1.5.dp, if (selected) primary else CerealTheme.colorScheme.borderDark, CircleShape))
        }
        Column(Modifier.weight(1f)) { NameAndId(model) }
        if (model.free) FreeBadge()
        if (showDate) {
            CerealText(model.created, style = MaterialTheme.typography.bodySmall, color = CerealTheme.colorScheme.contentTertiary)
        }
    }
}

@Composable
private fun MissingRow() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(14.dp).border(4.dp, MaterialTheme.colorScheme.primary, CircleShape))
        Column(Modifier.weight(1f)) {
            CerealText(MISSING_ID, style = MaterialTheme.typography.bodyMedium)
            CerealText("Not offered by provider", style = MaterialTheme.typography.bodySmall, color = CerealTheme.colorScheme.warning)
        }
    }
}

@Composable
private fun NameAndId(model: FakeModel) {
    if (model.name != null) {
        CerealText(model.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
        CerealText(model.id, style = MaterialTheme.typography.bodySmall, color = CerealTheme.colorScheme.contentTertiary)
    } else {
        CerealText(model.id, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun FreeBadge() {
    val green = CerealTheme.colorScheme.success
    CerealText(
        "Free",
        modifier =
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(green.copy(alpha = 0.15f))
                .border(1.dp, green.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp),
        color = green,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
    )
}

@Composable
private fun SearchField(
    query: String,
    onChange: (String) -> Unit,
) = CerealTextField(
    value = query,
    onValueChange = onChange,
    placeholder = "Search models",
    modifier = Modifier.fillMaxWidth(),
    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = CerealTheme.colorScheme.contentTertiary) },
)

@Composable
private fun DisabledField(
    text: String,
    leading: (@Composable () -> Unit)? = null,
) = CerealTextField(
    value = "",
    onValueChange = {},
    placeholder = text,
    enabled = false,
    modifier = Modifier.fillMaxWidth(),
    leadingIcon = leading,
)

@Composable
private fun FreeTextFallback() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CerealTextField(
            value = Proto.freeText,
            onValueChange = { Proto.freeText = it },
            placeholder = "Type a model id, e.g. gpt-5.1",
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CerealText(
                "Couldn't load the model list (401 Unauthorized). Type the model id instead.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            CerealText("Retry", modifier = Modifier.clickable { Proto.state = ListState.LOADED }, color = CerealTheme.colorScheme.link, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun filter(query: String) =
    fakeModels.filter { query.isBlank() || it.id.contains(query, true) || it.name?.contains(query, true) == true }

/** The prototype's switcher: obviously not part of the design. */
@Composable
private fun PrototypeStrip() {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.Black)
                .border(1.dp, Color(0xFFFF00AA), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val variants = listOf('A' to "Combobox", 'B' to "Dialog", 'C' to "Inline list")
        val label = variants.first { it.first == Proto.variant }
        StripButton("◀") { Proto.variant = variants[(variants.indexOf(label) + variants.size - 1) % variants.size].first }
        CerealText("PROTOTYPE ${label.first} (${label.second})", color = Color(0xFFFF00AA), style = MaterialTheme.typography.labelMedium)
        StripButton("▶") { Proto.variant = variants[(variants.indexOf(label) + 1) % variants.size].first }
        CerealText("|", color = Color.Gray)
        ListState.entries.forEach { s ->
            StripButton(s.label, active = s == Proto.state) { Proto.state = s }
        }
    }
}

@Composable
private fun StripButton(
    text: String,
    active: Boolean = false,
    onClick: () -> Unit,
) = CerealText(
    text,
    modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 2.dp),
    color = if (active) Color.White else Color.Gray,
    style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.Normal),
)
