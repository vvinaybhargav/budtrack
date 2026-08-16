package com.vinay.fintrack.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        color = Pf.Text,
        fontSize = 16.sp,
        fontWeight = FontWeight.ExtraBold
    )
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, size: Int = 12) {
    Text(text, modifier = modifier, color = Pf.Muted, fontSize = size.sp)
}

@Composable
fun PfCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(Space.s3),
    shape: RoundedCornerShape = Radius.Md,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Pf.Surface, shape)
            .border(1.dp, Pf.Hairline, shape)
            .padding(padding),
        content = content
    )
}

@Composable
fun Tag(text: String, background: Color, contentColor: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .background(background, Radius.Pill)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        color = contentColor,
        fontSize = 11.sp
    )
}

@Composable
fun OutlineTag(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .border(1.dp, Pf.Accent, Radius.Pill)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        color = Pf.Accent,
        fontSize = 10.sp
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = Radius.Pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = Pf.Accent,
            contentColor = Color.White,
            disabledContainerColor = Pf.Accent.copy(alpha = 0.35f),
            disabledContentColor = Color.White.copy(alpha = 0.6f)
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
    ) { Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = Radius.Pill,
        border = BorderStroke(1.dp, Pf.Hairline),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Pf.Text,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = Pf.Muted
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
    ) { Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text, color = Pf.Accent400, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .background(if (selected) Pf.Accent else Pf.Surface2, Radius.Pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        color = if (selected) Color.White else Pf.Text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun ProgressBar(fraction: Float, color: Color, height: Int = 7, modifier: Modifier = Modifier) {
    // A NaN from dividing by a zero budget or tenure survives coerceIn — every
    // NaN comparison is false — and then fillMaxWidth throws. Guard at the one
    // place every caller goes through.
    val safe = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f
    Box(
        modifier
            .fillMaxWidth()
            .height(height.dp)
            .background(Pf.Surface2, Radius.Pill)
    ) {
        Box(
            Modifier
                .fillMaxWidth(safe)
                .height(height.dp)
                .background(color, Radius.Pill)
        )
    }
}

/**
 * A field that keeps its own text and only reports a settled value, so typing
 * "6500" is one save rather than four — each of which was a disk write and a
 * Firestore push.
 *
 * [onSettled] fires after [delayMs] of no typing, and blank input is never
 * reported: clearing a budget used to store zero, which then divided by zero.
 */
@Composable
fun DebouncedField(
    value: String,
    onSettled: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    numeric: Boolean = false,
    singleLine: Boolean = true,
    allowBlank: Boolean = false,
    delayMs: Long = 600
) {
    var text by remember(value) { mutableStateOf(value) }
    var edited by remember { mutableStateOf(false) }

    LaunchedEffect(text, edited) {
        if (!edited || text == value) return@LaunchedEffect
        kotlinx.coroutines.delay(delayMs)
        if (allowBlank || text.isNotBlank()) onSettled(text)
    }

    PfField(
        label = label,
        value = text,
        onValueChange = { text = it; edited = true },
        modifier = modifier,
        placeholder = placeholder,
        numeric = numeric,
        singleLine = singleLine
    )
}

@Composable
fun PfField(
    label: String? = null,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    numeric: Boolean = false,
    singleLine: Boolean = true,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val displayLabel = label ?: if (placeholder.isNotEmpty() &&
        !placeholder.lowercase().contains("search") &&
        !placeholder.lowercase().contains("ask") &&
        !placeholder.lowercase().contains("message") &&
        !placeholder.lowercase().contains("type")) placeholder else null

    Column(modifier) {
        if (displayLabel != null) {
            Muted(displayLabel, Modifier.padding(bottom = 5.dp))
        }
        OutlinedTextField(
            value = value,
            onValueChange = { if (numeric) onValueChange(it.filter { c -> c.isDigit() || c == '.' }) else onValueChange(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = Pf.Muted, fontSize = 14.sp) },
            singleLine = singleLine,
            shape = Radius.Sm,
            textStyle = androidx.compose.ui.text.TextStyle(color = Pf.Text, fontSize = 14.sp),
            keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
            trailingIcon = trailingIcon,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Pf.Surface2,
                unfocusedContainerColor = Pf.Surface2,
                focusedBorderColor = Pf.Accent,
                unfocusedBorderColor = Pf.Hairline,
                cursorColor = Pf.Accent,
                focusedTextColor = Pf.Text,
                unfocusedTextColor = Pf.Text
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PfSelect(
    label: String? = null,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        if (label != null) {
            Muted(label, Modifier.padding(bottom = 5.dp))
        }
        ExposedDropdownMenuBox(
            expanded = expanded && enabled,
            onExpandedChange = { if (enabled) expanded = it }
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                shape = Radius.Sm,
                textStyle = androidx.compose.ui.text.TextStyle(color = Pf.Text, fontSize = 14.sp),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Pf.Surface2,
                    unfocusedContainerColor = Pf.Surface2,
                    disabledContainerColor = Pf.Surface2,
                    focusedBorderColor = Pf.Accent,
                    unfocusedBorderColor = Pf.Hairline,
                    disabledBorderColor = Pf.Hairline,
                    focusedTextColor = Pf.Text,
                    unfocusedTextColor = Pf.Text,
                    disabledTextColor = Pf.Muted,
                    focusedTrailingIconColor = Pf.Muted,
                    unfocusedTrailingIconColor = Pf.Muted
                )
            )
            ExposedDropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Pf.Surface2)
            ) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt, color = Pf.Text, fontSize = 14.sp) },
                        onClick = { onSelect(opt); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Pf.Hairline)
    )
}

@Composable
fun EditorActions(
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.s2, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GhostButton("Delete", onDelete)
        SecondaryButton("Cancel", onCancel)
        PrimaryButton("Save", onSave)
    }
}

/** Ratio that stays sane when the denominator is zero — a cleared budget, a
 *  loan with no tenure, a card with no limit. */
fun safeFraction(part: Double, whole: Double): Float =
    if (whole <= 0.0) 0f else (part / whole).toFloat().coerceIn(0f, 1f)

fun safeFraction(part: Int, whole: Int): Float =
    if (whole <= 0) 0f else (part.toFloat() / whole).coerceIn(0f, 1f)
