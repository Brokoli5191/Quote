package app.brokoli5191.quote.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.brokoli5191.quote.ui.uiText

/**
 * Material 3 time picker dialog: clock dial by default, switchable to keyboard input.
 * Uses the 12/24-hour format of the system setting; [onConfirm] always receives a 24-hour value.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuoteTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val is24Hour = remember(context) { DateFormat.is24HourFormat(context) }
    val state = rememberTimePickerState(
        initialHour = initialHour.coerceIn(0, 23),
        initialMinute = initialMinute.coerceIn(0, 59),
        is24Hour = is24Hour
    )
    var showInput by rememberSaveable { mutableStateOf(false) }
    // AM/PM follows the accent like the hour/minute fields; the default tertiary tone clashes with it.
    val colors = TimePickerDefaults.colors(
        periodSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        periodSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
    )

    TimePickerDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = uiText(if (showInput) "Enter time" else "Select time"),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        },
        confirmButton = {
            ExpressiveTextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text(uiText("OK"))
            }
        },
        dismissButton = {
            ExpressiveTextButton(onClick = onDismiss) {
                Text(uiText("Cancel"))
            }
        },
        modeToggleButton = {
            IconButton(onClick = { showInput = !showInput }) {
                Icon(
                    imageVector = if (showInput) Icons.Outlined.Schedule else Icons.Outlined.Keyboard,
                    contentDescription = uiText(if (showInput) "Switch to clock" else "Switch to text input")
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        if (showInput) {
            TimeInput(state = state, colors = colors)
        } else {
            TimePicker(state = state, colors = colors)
        }
    }
}
