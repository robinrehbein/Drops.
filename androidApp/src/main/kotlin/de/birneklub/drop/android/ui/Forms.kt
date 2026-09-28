package de.birneklub.drop.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.birneklub.drop.core.format.Format
import kotlinx.datetime.LocalDate

/** Grouped fields with hairline dividers, like a settings table. */
@Composable
fun FormCard(content: @Composable ColumnScope.() -> Unit) {
    val c = Drops.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.dp, c.line, RoundedCornerShape(18.dp)).background(c.line),
        verticalArrangement = Arrangement.spacedBy(1.dp), content = content,
    )
}

/** Side-by-side fields share one height, so the divider background never shows below the shorter cell. */
@Composable
fun FieldRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(1.dp), content = content)
}

/**
 * A labelled text field. [error] is shown right under the field in plain words
 * that say how to fix it, and is announced by TalkBack.
 */
@Composable
fun FormField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    type: KeyboardType = KeyboardType.Text,
    placeholder: String? = null,
    error: String? = null,
    singleLine: Boolean = true,
) {
    val c = Drops.colors
    Column(modifier.background(c.surface).heightIn(min = Space.touch).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(label, style = DropsType.caption, color = if (error != null) c.bad else c.muted)
        BasicTextField(
            value, onChange, singleLine = singleLine, textStyle = DropsType.body.copy(color = c.ink), cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(keyboardType = type),
            modifier = Modifier.fillMaxWidth().padding(top = Space.xxs).a11y(label).then(if (error != null) Modifier.semantics { error(error) } else Modifier),
            decorationBox = { field ->
                Box {
                    if (value.isEmpty() && placeholder != null) Text(placeholder, style = DropsType.body, color = c.muted, maxLines = 1)
                    field()
                }
            },
        )
        if (error != null) Text(error, style = DropsType.caption, color = c.bad, modifier = Modifier.padding(top = Space.xs))
    }
}

/** Date chosen with the system date picker instead of typed; can be cleared again. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, value: LocalDate?, onChange: (LocalDate?) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    val c = Drops.colors
    var open by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier.background(c.surface).heightIn(min = Space.touch)
            .clickable(role = Role.Button, onClickLabel = "Datum wählen") { open = true }
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = DropsType.caption, color = c.muted)
            Text(if (value != null) Format.date(value) else "Datum wählen", style = DropsType.body, color = if (value != null) c.ink else c.muted, modifier = Modifier.padding(top = Space.xxs))
        }
        if (value != null) {
            Box(Modifier.size(Space.touch).clickable(role = Role.Button) { onChange(null) }.a11y("$label entfernen"), contentAlignment = Alignment.Center) {
                Text("×", style = DropsType.headline, color = c.muted)
            }
        }
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = value?.let { it.toEpochDays() * MILLIS_PER_DAY })
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextAction("Übernehmen", {
                    state.selectedDateMillis?.let { onChange(LocalDate.fromEpochDays((it / MILLIS_PER_DAY).toInt())) }
                    open = false
                })
            },
            dismissButton = { TextAction("Abbrechen", { open = false }, c.muted) },
        ) { DatePicker(state, showModeToggle = true) }
    }
}

private const val MILLIS_PER_DAY = 86_400_000L

/** One choice from a horizontally scrolling chip row. */
@Composable
fun ChoiceRow(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Text(label, style = DropsType.small, color = Drops.colors.muted)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { Chip(it, it == selected) { onSelect(it) } }
        }
    }
}

@Composable
fun FormSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Eyebrow(title)
        content()
    }
}
