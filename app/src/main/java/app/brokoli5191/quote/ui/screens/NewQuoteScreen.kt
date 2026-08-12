package app.brokoli5191.quote.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.brokoli5191.quote.ui.QuoteViewModel
import app.brokoli5191.quote.ui.components.ExpressiveButton
import app.brokoli5191.quote.ui.components.ExpressiveIconButton
import app.brokoli5191.quote.ui.components.rememberExpressiveShape
import app.brokoli5191.quote.ui.theme.SerifFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val customQuoteCategories = listOf(
    "Inspirational", "Life", "Humor", "Love", "Books", "Truth", "Reading",
    "Wisdom", "Happiness", "Writing", "Inspiration", "Philosophy", "Death",
    "Poetry", "Optimism"
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun NewQuoteScreen(
    viewModel: QuoteViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var text by rememberSaveable { mutableStateOf("") }
    var author by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Inspirational") }
    var tags by rememberSaveable { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    val tagsBringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    val formScrollState = rememberScrollState()
    val density = LocalDensity.current
    val saveButtonScrollOffsetPx = with(density) { 72.dp.roundToPx() }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExpressiveIconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "New quote",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Saved privately on this device",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(formScrollState)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Box {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Quote or affirmation") },
                        placeholder = { Text("Write a quote…") },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = SerifFontFamily,
                            lineHeight = 27.sp
                        ),
                        minLines = 1,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it },
                        label = { Text("Author") },
                        placeholder = { Text("Unknown") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 64.dp),
                        shape = RoundedCornerShape(16.dp)
                    )
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = author.trim().firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.titleMedium.copy(
                                lineHeight = 20.sp,
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Shelf",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        customQuoteCategories.forEach { categoryName ->
                            val selected = category == categoryName
                            val interactionSource = remember(categoryName) { MutableInteractionSource() }
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    category = categoryName
                                },
                                label = { Text(categoryName) },
                                shape = rememberExpressiveShape(interactionSource, 18.dp, 8.dp),
                                interactionSource = interactionSource,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                )
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags") },
                    supportingText = { Text("Separate tags with commas") },
                    placeholder = { Text("wisdom, courage, morning") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(tagsBringIntoViewRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                coroutineScope.launch {
                                    delay(400L)
                                    tagsBringIntoViewRequester.bringIntoView()
                                    formScrollState.animateScrollTo(
                                        (formScrollState.maxValue - saveButtonScrollOffsetPx).coerceAtLeast(0)
                                    )
                                }
                            }
                        },
                    shape = RoundedCornerShape(16.dp)
                )
                ExpressiveButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.addUserQuote(text.trim(), author.trim(), category, tags.trim())
                        viewModel.selectSavedSubTab("My Quotes")
                        onBack()
                    },
                    enabled = text.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    restingCorner = 18.dp,
                    contentPadding = PaddingValues(horizontal = 20.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add to My Quotes", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.ime))
            }
        }
    }
}
