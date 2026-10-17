package com.sacco.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.sacco.app.ui.theme.ErrorColor
import com.sacco.app.ui.theme.SuccessColor
import com.sacco.app.ui.theme.WarningColor
import com.sacco.app.util.typeLabel

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
fun StatCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun LabeledRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(12.dp))
        Text(value, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1f))
    }
}

/** Status shown as icon + text (never colour alone). */
@Composable
fun StatusChip(status: String) {
    val (icon, color) = when (status) {
        "ACTIVE", "SUCCESS", "FULLY_PAID", "PAID" -> Icons.Filled.CheckCircle to SuccessColor
        "PENDING", "DUE", "PARTIAL" -> Icons.Filled.Schedule to WarningColor
        else -> Icons.Filled.Cancel to ErrorColor   // REJECTED, OVERDUE, FAILED
    }
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.12f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(14.dp), tint = color)
            Spacer(Modifier.width(4.dp))
            Text(typeLabel(status), color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun FormField(
    value: String, onChange: (String) -> Unit, label: String, error: String? = null,
    keyboard: KeyboardType = KeyboardType.Text, password: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth(),
    singleLine: Boolean = true
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, modifier = modifier,
        isError = error != null, singleLine = singleLine,
        supportingText = { if (error != null) Text(error) },
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (password) {
            { IconButton({ visible = !visible }) { Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Toggle password") } }
        } else null
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit, error: String? = null) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, { open = it }, Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selected, onValueChange = {}, readOnly = true, label = { Text(label) }, isError = error != null,
            supportingText = { if (error != null) Text(error) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(open, { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { onSelect(o); open = false }) }
        }
    }
}

/** Dialog that asks for an amount and shows server-side validation errors inline. */
@Composable
fun AmountDialog(
    title: String, label: String, initial: String = "", onDismiss: () -> Unit,
    submit: (Double, (Result<Unit>) -> Unit) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = { FormField(text, { text = it; err = null }, label, err, KeyboardType.Decimal) },
        confirmButton = {
            Button(enabled = !busy, onClick = {
                val v = text.toDoubleOrNull()
                if (v == null) err = "Enter a valid amount" else {
                    busy = true
                    submit(v) { r -> busy = false; r.onSuccess { onDismiss() }.onFailure { err = it.message } }
                }
            }) { Text("Confirm") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun SimpleLineChart(values: List<Double>, labels: List<String>, modifier: Modifier = Modifier.fillMaxWidth().height(160.dp)) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            if (values.isEmpty()) return@Canvas
            val max = (values.max()).coerceAtLeast(1.0)
            val min = minOf(values.min(), 0.0)
            val step = if (values.size > 1) size.width / (values.size - 1) else 0f
            fun y(v: Double) = (size.height - ((v - min) / (max - min) * size.height)).toFloat()
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 2f)
            val pts = values.mapIndexed { i, v -> Offset(if (values.size == 1) size.width / 2 else i * step, y(v)) }
            pts.zipWithNext().forEach { (a, b) -> drawLine(line, a, b, 6f, StrokeCap.Round) }
            pts.forEach { drawCircle(line, 9f, it) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEach { Text(it, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
fun SimpleBarChart(values: List<Double>, labels: List<String>, modifier: Modifier = Modifier.fillMaxWidth().height(160.dp), color: Color = MaterialTheme.colorScheme.primary) {
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            if (values.isEmpty()) return@Canvas
            val max = values.max().coerceAtLeast(1.0)
            val slot = size.width / values.size
            values.forEachIndexed { i, v ->
                val h = (v / max * size.height).toFloat()
                drawRect(color, Offset(i * slot + slot * 0.2f, size.height - h), Size(slot * 0.6f, h))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall) }
        }
    }
}
