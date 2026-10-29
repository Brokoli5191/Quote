package app.brokoli5191.quote.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import app.brokoli5191.quote.data.QuoteLanguage
import app.brokoli5191.quote.ui.uiText

/**
 * Small chip that flips a quote between its two languages.
 * [targetLanguage] is the language the quote is shown in after tapping ("en"/"de");
 * [showingTranslation] marks the chip selected while the non-default version is visible.
 */
@Composable
fun TranslationToggle(
    showingTranslation: Boolean,
    targetLanguage: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val toGerman = targetLanguage == QuoteLanguage.GERMAN
    val label = uiText(if (toGerman) "German" else "English")
    val description = uiText(if (toGerman) "Show in German" else "Show in English")
    FilterChip(
        selected = showingTranslation,
        onClick = onToggle,
        label = {
            Text(text = label, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible)
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Translate,
                contentDescription = null,
                modifier = Modifier.size(FilterChipDefaults.IconSize)
            )
        },
        // Selected state follows the accent like the app's other selected chips/tabs
        // (the default secondaryContainer is amber in the dark themes).
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = modifier.semantics { contentDescription = description }
    )
}

/**
 * Quote text that crossfades between original and translation while the card's height springs to
 * the new size. Keyed on [showingTranslation] rather than the text, so only a card whose toggle was
 * tapped animates (an app language change swaps texts instantly). [lowPerformanceMode] switches
 * instantly without any animation. Takes the full width, so only the height changes.
 */
@Composable
fun AnimatedTranslation(
    showingTranslation: Boolean,
    lowPerformanceMode: Boolean,
    content: @Composable (showingTranslation: Boolean) -> Unit,
) {
    if (lowPerformanceMode) {
        content(showingTranslation)
        return
    }
    AnimatedContent(
        targetState = showingTranslation,
        transitionSpec = {
            // Clipped only while the size animates, so a longer text fading in does not draw over
            // the toggle and author row below before the card has grown to fit it.
            fadeIn(tween(durationMillis = 220, delayMillis = 60)) togetherWith
                fadeOut(tween(durationMillis = 90)) using
                SizeTransform(clip = true) { _, _ ->
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                        visibilityThreshold = IntSize.VisibilityThreshold
                    )
                }
        },
        modifier = Modifier.fillMaxWidth(),
        label = "TranslationTransition"
    ) { showing ->
        content(showing)
    }
}
