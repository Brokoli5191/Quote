package app.brokoli5191.quote.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
        modifier = modifier.semantics { contentDescription = description }
    )
}
