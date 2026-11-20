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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import app.brokoli5191.quote.data.SubmissionLimits
import app.brokoli5191.quote.data.QuoteLanguage
import app.brokoli5191.quote.data.storedQuoteTextLength
import app.brokoli5191.quote.data.parseQuoteTags
import app.brokoli5191.quote.data.submissionLength
import app.brokoli5191.quote.ui.QuoteDraft
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

/**
 * The longest prefix of [value] (cut between code points) whose [length] stays within [max], or [value]
 * itself when it already fits. Pasted text is shortened to the limit instead of being dropped.
 *
 * [length] need not be strictly monotonic (quote normalization can strip or close wrapping quotes), so the
 * result always fits but is not guaranteed to be the longest fitting prefix.
 */
internal fun truncateToLimit(value: String, max: Int, length: (String) -> Int): String {
    if (length(value) <= max) return value
    fun prefix(codePoints: Int) = value.substring(0, value.offsetByCodePoints(0, codePoints))
    var low = 0
    var high = value.codePointCount(0, value.length)
    while (low < high) {
        val middle = (low + high + 1) / 2
        if (length(prefix(middle)) <= max) low = middle else high = middle - 1
    }
    return prefix(low)
}

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
    var language by rememberSaveable { mutableStateOf(viewModel.appLanguage.value) }
    var textShortened by rememberSaveable { mutableStateOf(false) }
    var authorShortened by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var handledDraft by remember { mutableStateOf<QuoteDraft?>(null) }
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
    // The limits count what the backend counts: code points of the text as it will be stored.
    val textLength = storedQuoteTextLength(text)
    val authorLength = submissionLength(author)
    val textAtLimit = textLength >= SubmissionLimits.MAX_TEXT_LENGTH
    val authorAtLimit = authorLength >= SubmissionLimits.MAX_AUTHOR_LENGTH
    val textBlank = text.isNotEmpty() && text.isBlank()
    val quoteInvalid = text.isNotEmpty() && textLength < SubmissionLimits.MIN_TEXT_LENGTH
    val parsedTags = parseQuoteTags(tags)
    val tagsInvalid = parsedTags.size > SubmissionLimits.MAX_TAGS ||
        parsedTags.any { submissionLength(it) > SubmissionLimits.MAX_TAG_LENGTH }
    val hasDraft = text.isNotBlank() || author.isNotBlank() || tags.isNotBlank()
    val pendingDraft by viewModel.pendingDraft.collectAsStateWithLifecycle()
    fun fillFrom(draft: QuoteDraft) {
        val fittedText = truncateToLimit(draft.text, SubmissionLimits.MAX_TEXT_LENGTH, ::storedQuoteTextLength)
        val fittedAuthor = truncateToLimit(draft.author, SubmissionLimits.MAX_AUTHOR_LENGTH, ::submissionLength)
        text = fittedText
        author = fittedAuthor
        textShortened = fittedText.length < draft.text.length
        authorShortened = fittedAuthor.length < draft.author.length
        handledDraft = draft
        viewModel.consumePendingDraft()
    }
    LaunchedEffect(pendingDraft, hasDraft) {
        val draft = pendingDraft
        if (draft == null) handledDraft = null
        else if (!hasDraft && draft != handledDraft) fillFrom(draft)
    }
    LaunchedEffect(hasDraft) { viewModel.setNewQuoteHasDraft(hasDraft) }
    DisposableEffect(Unit) { onDispose { viewModel.setNewQuoteHasDraft(false) } }
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
                        onValueChange = {
                            val fitted = truncateToLimit(it, SubmissionLimits.MAX_TEXT_LENGTH, ::storedQuoteTextLength)
                            textShortened = fitted.length < it.length
                            text = fitted
                        },
                        label = { Text(uiText("Quote or affirmation")) },
                        placeholder = { Text(uiText("Write a quote…")) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = SerifFontFamily,
                            lineHeight = 27.sp
                        ),
                        minLines = 1,
                        maxLines = 6,
                        isError = quoteInvalid || textShortened,
                        supportingText = {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    text = when {
                                        textShortened -> uiText("The text was shortened to 500 characters.")
                                        textBlank -> uiText("Add some text besides spaces.")
                                        quoteInvalid -> uiText("Enter at least 3 characters.")
                                        textAtLimit -> uiText("At most 500 characters")
                                        else -> ""
                                    },
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                )
                                Text(
                                    text = "$textLength/${SubmissionLimits.MAX_TEXT_LENGTH}",
                                    color = if (textAtLimit) MaterialTheme.colorScheme.error else Color.Unspecified
                                )
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
                        onValueChange = {
                            val fitted = truncateToLimit(it, SubmissionLimits.MAX_AUTHOR_LENGTH, ::submissionLength)
                            authorShortened = fitted.length < it.length
                            author = fitted
                        },
                        label = { Text(uiText("Author")) },
                        placeholder = { Text(uiText("Unknown")) },
                        isError = authorShortened,
                        supportingText = {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    text = when {
                                        authorShortened -> uiText("The author was shortened to 100 characters.")
                                        authorAtLimit -> uiText("At most 100 characters")
                                        else -> ""
                                    },
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                )
                                Text(
                                    text = "$authorLength/${SubmissionLimits.MAX_AUTHOR_LENGTH}",
                                    color = if (authorAtLimit) MaterialTheme.colorScheme.error else Color.Unspecified
                                )
                            }
                        },
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

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = uiText("Language of the quote"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(QuoteLanguage.ENGLISH to "English", QuoteLanguage.GERMAN to "German").forEach { (code, name) ->
                            FilterChip(
                                selected = language == code,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    language = code
                                },
                                label = { Text(uiText(name)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                )
                            )
                        }
                    }
                    Text(
                        text = uiText("Decides who sees the quote if you submit it to the community."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                        saving = true
                        viewModel.addUserQuote(text.trim(), author.trim(), category, tags.trim(), language)
                        viewModel.selectSavedSubTab("My Quotes")
                        onBack()
                    },
                    enabled = !saving && textLength in SubmissionLimits.MIN_TEXT_LENGTH..SubmissionLimits.MAX_TEXT_LENGTH && !tagsInvalid,
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

    val sharedDraft = pendingDraft
    if (sharedDraft != null && sharedDraft != handledDraft && hasDraft) {
        AlertDialog(
            onDismissRequest = {
                handledDraft = sharedDraft
                viewModel.consumePendingDraft()
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            title = { Text(uiText("Replace your draft?")) },
            text = { Text(uiText("You shared text to Quote while a draft was open.")) },
            dismissButton = {
                TextButton(onClick = {
                    handledDraft = sharedDraft
                    viewModel.consumePendingDraft()
                }) {
                    Text(uiText("Keep my draft"))
                }
            },
            confirmButton = {
                TextButton(onClick = { fillFrom(sharedDraft) }) {
                    Text(uiText("Use shared text"))
                }
            }
        )
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
