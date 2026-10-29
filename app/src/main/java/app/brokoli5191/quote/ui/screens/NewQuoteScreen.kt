package app.brokoli5191.quote.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.brokoli5191.quote.ui.QuoteViewModel
import app.brokoli5191.quote.ui.categoryText
import app.brokoli5191.quote.ui.uiText
import app.brokoli5191.quote.ui.components.AuthorAvatar
import app.brokoli5191.quote.ui.components.ExpressiveButton
import app.brokoli5191.quote.ui.components.ExpressiveIconButton
import app.brokoli5191.quote.ui.components.rememberExpressiveShape
import app.brokoli5191.quote.ui.components.FloatingChromeBackdrop
import app.brokoli5191.quote.ui.components.bleedHorizontally
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import app.brokoli5191.quote.ui.theme.SerifFontFamily
import app.brokoli5191.quote.data.CategoryMapper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val customQuoteCategories = CategoryMapper.categories
private val FormHorizontalPadding = 20.dp
private val HeaderContentGap = 8.dp
private val HeaderFallbackHeight = 68.dp

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun NewQuoteScreen(
    viewModel: QuoteViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    // Bottom space the form must keep clear: the keyboard, or whatever sits below it (the app's
    // bottom bar plus the system navigation bar). The form ends at the keyboard; anything else
    // floats over it, so the form scrolls underneath and only gets matching end padding.
    bottomInsets: WindowInsets = WindowInsets.ime.union(WindowInsets.navigationBars)
) {
    var text by rememberSaveable { mutableStateOf("") }
    var author by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Inspirational") }
    var tags by rememberSaveable { mutableStateOf("") }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val tagsBringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    val formScrollState = rememberScrollState()
    val density = LocalDensity.current
    val saveButtonScrollOffsetPx = with(density) { 72.dp.roundToPx() }
    val lowPerformanceMode by viewModel.lowPerformanceMode.collectAsStateWithLifecycle()
    val blurNavigationSurfaces by viewModel.blurNavigationSurfaces.collectAsStateWithLifecycle()
    val formHazeState = rememberHazeState(blurEnabled = blurNavigationSurfaces && !lowPerformanceMode)
    // The header floats over the form, which starts below its measured height and scrolls under it.
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerHeight = if (headerHeightPx > 0) with(density) { headerHeightPx.toDp() } else statusBarTop + HeaderFallbackHeight
    val trimmedTextLength = text.trim().length
    val quoteInvalid = text.isNotEmpty() && trimmedTextLength < 3
    val parsedTags = tags.split(',').map(String::trim).filter(String::isNotEmpty)
    val tagsInvalid = parsedTags.size > 8 || parsedTags.any { it.length > 30 }
    val hasDraft = text.isNotBlank() || author.isNotBlank() || tags.isNotBlank() || category != "Inspirational"
    val requestBack = {
        if (hasDraft) showDiscardDialog = true else onBack()
    }

    BackHandler(enabled = hasDraft) { showDiscardDialog = true }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(formHazeState)
                    .windowInsetsPadding(WindowInsets.ime.only(WindowInsetsSides.Bottom))
                    .verticalScroll(formScrollState)
                    .padding(
                        start = FormHorizontalPadding,
                        top = headerHeight + HeaderContentGap,
                        end = FormHorizontalPadding
                    ),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Box {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { if (it.length <= 500) text = it },
                        label = { Text(uiText("Quote or affirmation")) },
                        placeholder = { Text(uiText("Write a quote…")) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = SerifFontFamily,
                            lineHeight = 27.sp
                        ),
                        minLines = 1,
                        maxLines = 6,
                        isError = quoteInvalid,
                        supportingText = {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    text = if (quoteInvalid) uiText("Enter at least 3 characters.") else "",
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                )
                                Text("${text.length}/500")
                            }
                        },
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
                        onValueChange = { if (it.length <= 100) author = it },
                        label = { Text(uiText("Author")) },
                        placeholder = { Text(uiText("Unknown")) },
                        supportingText = { Text("${author.length}/100") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 64.dp),
                        shape = RoundedCornerShape(16.dp)
                    )
                    val trimmedAuthor = author.trim()
                    AuthorAvatar(
                        name = trimmedAuthor,
                        imageUrl = null,
                        unknown = trimmedAuthor.isEmpty() || trimmedAuthor == "Unknown" || trimmedAuthor == "Unbekannt",
                        size = 52.dp
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = uiText("Shelf"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    // The chips scroll out to the screen edges instead of being cut off at the
                    // form padding; the inner padding keeps the first and last chip aligned with it.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bleedHorizontally(FormHorizontalPadding)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = FormHorizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        customQuoteCategories.forEach { categoryName ->
                            val selected = category == categoryName
                            val interactionSource = remember(categoryName) { MutableInteractionSource() }
                            FilterChip(
                                selected = selected,
                                onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    category = categoryName
                                },
                                label = { Text(categoryText(categoryName)) },
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
                    label = { Text(uiText("Tags")) },
                    supportingText = {
                        Text(
                            if (tagsInvalid) uiText("Maximum 8 tags, 30 characters each.")
                            else uiText("Separate tags with commas")
                        )
                    },
                    isError = tagsInvalid,
                    placeholder = { Text(uiText("wisdom, courage, morning")) },
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
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.addUserQuote(text.trim(), author.trim(), category, tags.trim())
                        viewModel.selectSavedSubTab("My Quotes")
                        onBack()
                    },
                    enabled = trimmedTextLength in 3..500 && !tagsInvalid,
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
                    Text(
                        uiText("Add to My Quotes"),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
                Spacer(Modifier.height(8.dp))
                // Lets the button scroll out from under the floating bottom bar.
                Spacer(Modifier.windowInsetsBottomHeight(bottomInsets.exclude(WindowInsets.ime)))
            }

            FloatingChromeBackdrop(
                hazeState = formHazeState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerHeight + HeaderContentGap)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { headerHeightPx = it.height }
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExpressiveIconButton(onClick = requestBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = uiText("Back")
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = uiText("New quote"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = uiText("Stored locally; only sent when you submit it"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            title = { Text(uiText("Discard draft?")) },
            text = { Text(uiText("Your unsaved quote will be lost.")) },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(uiText("Keep editing"))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onBack()
                }) {
                    Text(uiText("Discard"), color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }
}
