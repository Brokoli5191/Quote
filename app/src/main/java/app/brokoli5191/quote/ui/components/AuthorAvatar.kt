package app.brokoli5191.quote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.brokoli5191.quote.BuildConfig
import app.brokoli5191.quote.ui.uiText
import coil.compose.AsyncImage
import coil.request.ImageRequest

// Wikimedia asks clients to identify themselves; image requests use the same agent as the API lookups.
private val AuthorImageUserAgent =
    "QuoteApp/${BuildConfig.VERSION_NAME} (https://github.com/Brokoli5191/Quote)"

/**
 * Circular author portrait. Shows the author's initial until [imageUrl] has loaded (or when it is
 * missing/fails), and a question mark for unknown authors.
 */
@Composable
fun AuthorAvatar(
    name: String,
    imageUrl: String?,
    unknown: Boolean,
    size: Dp = 42.dp,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        val initial = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
        if (unknown || initial == null) {
            Icon(
                imageVector = Icons.Default.QuestionMark,
                contentDescription = uiText("Unknown author"),
                tint = colors.onPrimaryContainer,
                modifier = Modifier.size(size * 0.5f)
            )
        } else {
            val context = LocalContext.current
            val url = imageUrl?.takeIf { it.isNotBlank() }
            var imageLoaded by remember(url) { mutableStateOf(false) }

            if (!imageLoaded) {
                // Sized from the avatar, not the font scale, so the letter always fits the circle.
                val letterSize = with(LocalDensity.current) { (size * 0.42f).toSp() }
                Text(
                    text = initial.toString(),
                    color = colors.onPrimaryContainer,
                    fontSize = letterSize,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
            if (url != null) {
                val request = remember(url, context) {
                    ImageRequest.Builder(context)
                        .data(url)
                        .addHeader("User-Agent", AuthorImageUserAgent)
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onSuccess = { imageLoaded = true },
                    onError = { imageLoaded = false },
                    modifier = Modifier.matchParentSize()
                )
            }
        }
    }
}
