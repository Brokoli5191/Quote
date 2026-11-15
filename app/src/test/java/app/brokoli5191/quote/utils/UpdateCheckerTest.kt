package app.brokoli5191.quote.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UpdateCheckerTest {

    @Test
    fun `numeric versions compare per segment`() {
        assertTrue(UpdateChecker.isNewerVersion("2.0.2", "2.0.10"))
        assertTrue(UpdateChecker.isNewerVersion("2.0.2", "v2.1"))
        assertFalse(UpdateChecker.isNewerVersion("2.1.0", "2.1"))
        assertFalse(UpdateChecker.isNewerVersion("2.1.0", "2.0.9"))
    }

    @Test
    fun `suffixes do not shift the version`() {
        assertTrue(UpdateChecker.isNewerVersion("2.0.2", "v2.0.3-hotfix"))
        assertFalse(UpdateChecker.isNewerVersion("2.0.3", "2.0.3+build5"))
    }

    @Test
    fun `pre-release sorts before its release`() {
        assertTrue(UpdateChecker.isNewerVersion("2.1.0-beta", "2.1.0"))
        assertFalse(UpdateChecker.isNewerVersion("2.1.0", "2.1.0-beta"))
        assertTrue(UpdateChecker.isNewerVersion("2.1.0-beta1", "2.1.0-beta2"))
        assertTrue(UpdateChecker.isNewerVersion("2.1.0-beta2", "2.1.0-beta10"))
        assertTrue(UpdateChecker.isNewerVersion("2.0.9", "2.1.0-beta"))
    }

    @Test
    fun `sha256 line is parsed from sha256sum output`() {
        val hash = "a".repeat(64)
        assertEquals(hash, UpdateChecker.parseSha256("${hash.uppercase()}  quote-2.0.2.apk\n"))
        assertNull(UpdateChecker.parseSha256("<html>captive portal</html>"))
    }

    @Test
    fun `release json is parsed into a result`() {
        val body = """{"tag_name":"v2.1.0","assets":[
            {"name":"quote-2.1.0.apk.sha256","browser_download_url":"https://x/s","size":90},
            {"name":"quote-2.1.0.apk","browser_download_url":"https://x/a.apk","size":1234}]}"""
        val result = UpdateChecker.parseRelease(body)
        assertEquals(
            ReleaseCheckResult.Found(ReleaseInfo("2.1.0", "https://x/a.apk", 1234L)),
            result
        )
        assertEquals(ReleaseCheckResult.NoApk, UpdateChecker.parseRelease("""{"tag_name":"v2.1.0","assets":[]}"""))
        assertEquals(ReleaseCheckResult.ServerError(200), UpdateChecker.parseRelease("<html>"))
    }

    @Test
    fun `http failures are told apart`() {
        assertEquals(ReleaseCheckResult.RateLimited, UpdateChecker.classifyFailure(429, null))
        assertEquals(ReleaseCheckResult.RateLimited, UpdateChecker.classifyFailure(403, "0"))
        assertEquals(ReleaseCheckResult.ServerError(403), UpdateChecker.classifyFailure(403, "12"))
        assertEquals(ReleaseCheckResult.NoApk, UpdateChecker.classifyFailure(404, null))
        assertEquals(ReleaseCheckResult.ServerError(503), UpdateChecker.classifyFailure(503, null))
    }
}
