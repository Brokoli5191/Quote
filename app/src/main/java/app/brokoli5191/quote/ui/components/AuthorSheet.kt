package app.brokoli5191.quote.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.brokoli5191.quote.data.AuthorInfo
import app.brokoli5191.quote.data.QuoteLanguage
import app.brokoli5191.quote.ui.uiText
import kotlinx.coroutines.launch

/**
 * Bottom sheet about an author: portrait, short description and a Wikipedia link. With
 * [onShowMoreQuotes] and a [quoteCount] above 1 it also offers "More quotes by …", which hides the
 * sheet first and then calls [onShowMoreQuotes] (e.g. a Library search for the author).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorSheet(
    name: String,
    info: AuthorInfo?,
    imageUrl: String?,
    language: String,
    onDismiss: () -> Unit,
    quoteCount: Int = 0,
    onShowMoreQuotes: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val description = info?.description?.takeIf { it.isNotBlank() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AuthorAvatar(name = name, imageUrl = imageUrl, unknown = false, size = 112.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            if (description != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(24.dp))
            if (onShowMoreQuotes != null && quoteCount > 1) {
                ExpressiveTonalButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        // The sheet leaves before the screen changes underneath it.
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            onDismiss()
                            onShowMoreQuotes()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    restingCorner = 18.dp
                ) {
                    Icon(Icons.Default.FormatQuote, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        uiText("More quotes by %1\$s (%2\$d)").format(name, quoteCount),
                        maxLines = 2,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            ExpressiveButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val url = info?.wikiUrl ?: wikipediaSearchUrl(name, language)
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                restingCorner = 18.dp
            ) {
                Icon(Icons.Default.Language, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(uiText("Read on Wikipedia"), maxLines = 2, textAlign = TextAlign.Center)
            }
        }
    }
}

private fun wikipediaSearchUrl(author: String, language: String): String {
    val wikipediaLanguage = if (language == QuoteLanguage.GERMAN) "de" else "en"
    return "https://$wikipediaLanguage.wikipedia.org/w/index.php?search=" +
        java.net.URLEncoder.encode(author, "UTF-8")
}
