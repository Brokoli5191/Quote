export function privacyPage(): Response {
  return new Response(`<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Quote Privacy & Submission Policy</title>
  <style>
    :root { color-scheme: dark; font-family: ui-sans-serif, system-ui, sans-serif; background: #0b090e; color: #f4edfc; }
    * { box-sizing: border-box; }
    body { margin: 0; background: radial-gradient(circle at 85% 0, #32214d, transparent 30rem), #0b090e; }
    main { width: min(46rem, calc(100% - 2rem)); margin: auto; padding: 4rem 0 6rem; }
    a { color: #d4b7ff; }
    .eyebrow { color: #cbb4ec; font-size: .72rem; font-weight: 800; letter-spacing: .16em; text-transform: uppercase; }
    h1 { margin: .6rem 0 1rem; font-family: Georgia, serif; font-size: clamp(2.4rem, 8vw, 4.7rem); line-height: 1; letter-spacing: -.04em; }
    h2 { margin-top: 2.3rem; font-size: 1.15rem; color: #dfc8ff; }
    p, li { color: #cfc4da; line-height: 1.7; }
    section { margin-top: 2rem; padding: 1.5rem; border: 1px solid #3b3147; border-radius: 1.3rem; background: #17131dcf; }
    .date { color: #93879f; font-size: .82rem; }
  </style>
</head>
<body>
  <main>
    <div class="eyebrow">Quote community</div>
    <h1>Privacy & submission policy</h1>
    <p class="date">Effective October 28, 2026</p>
    <section>
      <h2>What is sent</h2>
      <p>Submitting a custom quote sends its text, author, category, tags, language (English or German), and app version to the Quote moderation service. Submission is optional; custom quotes remain local unless you choose Submit for review.</p>
      <p>The app also sends a randomly generated installation identifier. The service stores salted hashes of that identifier and the request IP address for rate limiting and abuse prevention. It does not store the raw values in the moderation database. The installation identifier is also sent when the app checks the review status of your submissions or withdraws one.</p>

      <h2>How submissions are used</h2>
      <p>Submissions enter a private review queue. A moderator may correct formatting, attribution, category, tags, or language, and may add a translation into the other language before approval. Approved submissions become public community quotes and may be delivered to every Quote user. Rejected submissions are not published. A moderator may add a short note that the app shows to you with the review result.</p>
      <p>You can withdraw a submission while it is still waiting for review. Withdrawing deletes it from the review queue.</p>

      <h2>Retention</h2>
      <p>The hashed IP address is only needed for the 24-hour submission limit. It is erased automatically from submissions older than 30 days. The hashed installation identifier is kept with each submission so that your app can keep checking its review status.</p>
      <p>Submission records and moderation outcomes may be retained for duplicate detection, review history, and abuse prevention. When a moderator deletes a submission permanently, a one-way hash of its text is kept so that the same text is not accepted again. Published quotes remain available until they are unpublished by a moderator.</p>

      <h2>Author pictures and descriptions</h2>
      <p>To show pictures and short descriptions of quote authors, the app loads them directly from Wikipedia and Wikimedia Commons (operated by the Wikimedia Foundation). These requests do not go through the Quote service and contain no submission data, but Wikimedia receives your IP address and the name of the author being looked up, as with any visit to Wikipedia. Wikimedia's own privacy policy applies to those requests.</p>

      <h2>Your responsibility</h2>
      <p>Only submit material you have the right to share. Do not submit private information, harassment, unlawful content, or knowingly false attribution. Submission does not guarantee publication.</p>

      <h2>Service provider</h2>
      <p>The moderation API, access controls, and database run on Cloudflare infrastructure. Cloudflare may process network data according to its own privacy terms. Quote does not sell submission data or use it for advertising.</p>

      <h2>Questions</h2>
      <p>For policy questions or removal requests, contact the project through the <a href="https://github.com/Brokoli5191/Quote">Quote GitHub repository</a>.</p>
    </section>
  </main>
</body>
</html>`, {
    headers: {
      "Content-Type": "text/html; charset=UTF-8",
      "Cache-Control": "public, max-age=3600",
      "X-Content-Type-Options": "nosniff",
      "Content-Security-Policy": "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'"
    }
  });
}
