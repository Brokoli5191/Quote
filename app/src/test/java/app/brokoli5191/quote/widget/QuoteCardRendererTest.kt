package app.brokoli5191.quote.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class QuoteCardRendererTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun layout(text: String, widthDp: Float, heightDp: Float, density: Float, author: String = AUTHOR) =
        QuoteCardRenderer.layout(
            text,
            author,
            QuoteCardRenderer.LABEL,
            WidgetConfig(),
            (widthDp * density).toInt(),
            (heightDp * density).toInt(),
            density
        )

    @Test
    fun `render returns a bitmap of the requested size`() {
        val bitmap = QuoteCardRenderer.render(context, SHORT, AUTHOR, QuoteCardRenderer.LABEL, WidgetConfig(), 840, 420, 3f)
        assertEquals(840, bitmap.width)
        assertEquals(420, bitmap.height)
    }

    @Test
    fun `share image is 1080 x 1350 and needs no widget`() {
        val bitmap = QuoteCardRenderer.renderShareImage(context, LONG_DE, AUTHOR)
        assertEquals(QuoteCardRenderer.SHARE_IMAGE_WIDTH_PX, bitmap.width)
        assertEquals(QuoteCardRenderer.SHARE_IMAGE_HEIGHT_PX, bitmap.height)
    }

    @Test
    fun `quote never reaches into the author line`() {
        val sizesDp = listOf(280f to 140f, 276f to 220f, 250f to 110f, 180f to 70f, 360f to 450f)
        for ((widthDp, heightDp) in sizesDp) {
            for (density in listOf(2f, 2.75f, 3f)) {
                for (text in listOf(SHORT, NIETZSCHE, LONG_EN, LONG_DE)) {
                    val card = layout(text, widthDp, heightDp, density)
                    val author = card.author ?: continue
                    assertTrue(
                        "$widthDp x $heightDp dp at $density: quote ends at ${card.quoteBottom}, author starts at ${author.top}",
                        card.quoteBottom <= author.top
                    )
                }
            }
        }
    }

    @Test
    fun `quote that does not fit ends with an ellipsis inside its box`() {
        val card = layout(LONG_DE, 150f, 60f, 3f)
        val quote = card.quote
        assertTrue(quote.getEllipsisCount(quote.lineCount - 1) > 0)
        assertTrue(card.quoteBottom <= card.quoteBoxBottom)
    }

    @Test
    fun `quote that fits is shown in full`() {
        val card = layout(NIETZSCHE, 280f, 140f, 3f)
        val quote = card.quote
        assertEquals(0, quote.getEllipsisCount(quote.lineCount - 1))
        assertEquals(NIETZSCHE.length, quote.getLineEnd(quote.lineCount - 1))
        assertTrue(card.quoteBottom <= card.quoteBoxBottom)
    }

    @Test
    fun `overlong words stay inside the card`() {
        for (widthDp in listOf(57f, 70f, 130f)) {
            val card = layout(NETTES_MAEDCHEN, widthDp, 102f, 3f)
            val quote = card.quote
            assertTrue(card.quoteLeft + quote.width <= card.card.right)
            for (line in 0 until quote.lineCount) {
                assertTrue("line $line at $widthDp dp", quote.getLineWidth(line) <= quote.width)
            }
        }
    }

    @Test
    fun `a word wider than the box shrinks the text instead of being broken`() {
        val text = "Das ist meine Lieblingskombination."
        val card = layout(text, 110f, 102f, 3f)
        val quote = card.quote
        for (line in 1 until quote.lineCount) {
            assertTrue("line $line starts inside a word", text[quote.getLineStart(line) - 1].isWhitespace())
        }
        assertEquals(text.length, quote.getLineEnd(quote.lineCount - 1))
    }

    @Test
    fun `a hyphenated compound breaks at its hyphens instead of shrinking to the minimum`() {
        val card = layout(NETTES_MAEDCHEN, 130f, 180f, 3f)
        val quote = card.quote
        assertTrue("drawn at ${quote.paint.textSize / 3f} dp", quote.paint.textSize > 8f * 3f)
        assertEquals(0, quote.getEllipsisCount(quote.lineCount - 1))
        for (line in 1 until quote.lineCount) {
            val before = quote.text.subSequence(0, quote.getLineStart(line)).last { it != '​' }
            assertTrue("line $line starts inside a word", before.isWhitespace() || before == '-')
        }
    }

    private companion object {
        const val AUTHOR = "Friedrich Nietzsche"
        const val SHORT = "Be yourself."
        const val NIETZSCHE = "Without music, life would be a mistake."
        const val LONG_EN = "It is not the critic who counts; not the man who points out how the strong man stumbles, " +
            "or where the doer of deeds could have done them better."
        const val LONG_DE = "Das Talent gleicht dem Schützen, der ein Ziel trifft, welches die Übrigen nicht erreichen " +
            "können; das Genie dem, der eines trifft, bis zu welchem sie nicht einmal zu sehen vermögen."
        const val NETTES_MAEDCHEN = "Ich leide an Nettes-Mädchen-von-nebenan-itis: Der Typ ist mit dir befreundet, und das war’s."
    }
}
