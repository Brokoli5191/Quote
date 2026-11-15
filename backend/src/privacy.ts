import type { QuoteLanguage } from "./validation";

interface PolicyText {
  title: string;
  eyebrow: string;
  heading: string;
  effective: string;
  switchLabel: string;
  body: string;
}

// Both versions carry the same content; a change to one must be made to the other.
const POLICY: Record<QuoteLanguage, PolicyText> = {
  en: {
    title: "Quote Privacy & Submission Policy",
    eyebrow: "Quote community",
    heading: "Privacy & submission policy",
    effective: "Effective November 15, 2026",
    switchLabel: "Deutsch",
    body: `
      <h2>What is sent</h2>
      <p>Submitting a custom quote sends its text, author, category, tags, language (English or German), and app version to the Quote moderation service. Submission is optional; custom quotes remain local unless you choose Submit for review.</p>
      <p>The app also sends a randomly generated installation identifier. The service stores salted hashes of that identifier and the request IP address for rate limiting and abuse prevention. It does not store the raw values in the moderation database. The installation identifier is also sent when the app checks the review status of your submissions or withdraws one.</p>

      <h2>How submissions are used</h2>
      <p>Submissions enter a private review queue. A moderator may correct formatting, attribution, category, tags, or language, and may add a translation into the other language before approval. Approved submissions become public community quotes and may be delivered to every Quote user. Rejected submissions are not published. A moderator may add a short note that the app shows to you with the review result.</p>
      <p>You can withdraw a submission while it is still waiting for review. Withdrawing deletes it from the review queue.</p>

      <h2>Retention</h2>
      <p>The hashed IP address is only needed for the 24-hour submission limit. It is erased automatically from submissions older than 30 days. The hashed installation identifier is kept with each submission so that your app can keep checking its review status.</p>
      <p>When a submission is withdrawn or deleted, its hashed installation identifier, hashed IP address, and submission time are kept separately for at most two days after it was sent, so that it still counts toward the 24-hour submission limit. They are then erased automatically.</p>
      <p>Submission records and moderation outcomes may be retained for duplicate detection, review history, and abuse prevention. When a moderator deletes a submission permanently, a one-way hash of its text is kept so that the same text is not accepted again, even with different punctuation or a different author. Published quotes remain available until they are unpublished by a moderator.</p>

      <h2>Author pictures and descriptions</h2>
      <p>To show pictures and short descriptions of quote authors, the app loads them directly from Wikipedia and Wikimedia Commons (operated by the Wikimedia Foundation). These requests do not go through the Quote service and contain no submission data, but Wikimedia receives your IP address and the name of the author being looked up, as with any visit to Wikipedia. Wikimedia's own privacy policy applies to those requests.</p>

      <h2>Your responsibility</h2>
      <p>Only submit material you have the right to share. Do not submit private information, harassment, unlawful content, or knowingly false attribution. Submission does not guarantee publication.</p>

      <h2>Service provider</h2>
      <p>The moderation API, access controls, and database run on Cloudflare infrastructure. Cloudflare may process network data according to its own privacy terms. Quote does not sell submission data or use it for advertising.</p>

      <h2>Questions</h2>
      <p>For policy questions or removal requests, contact the project through the <a href="https://github.com/Brokoli5191/Quote">Quote GitHub repository</a>.</p>`
  },
  de: {
    title: "Quote – Datenschutz- und Einreichungsrichtlinie",
    eyebrow: "Quote-Community",
    heading: "Datenschutz- und Einreichungsrichtlinie",
    effective: "Gültig ab 15. November 2026",
    switchLabel: "English",
    body: `
      <h2>Was gesendet wird</h2>
      <p>Wenn du ein eigenes Quote einreichst, werden sein Text, Autor, Kategorie, Tags, Sprache (Englisch oder Deutsch) und die App-Version an den Moderationsdienst von Quote gesendet. Das Einreichen ist freiwillig; eigene Quotes bleiben lokal, solange du nicht „Zur Prüfung einreichen“ wählst.</p>
      <p>Die App sendet außerdem eine zufällig erzeugte Installationskennung. Der Dienst speichert gesalzene Hashes dieser Kennung und der IP-Adresse der Anfrage, um die Zahl der Einreichungen zu begrenzen und Missbrauch zu verhindern. Die Originalwerte speichert er nicht in der Moderationsdatenbank. Die Installationskennung wird auch gesendet, wenn die App den Prüfstatus deiner Einreichungen abfragt oder eine Einreichung zurückzieht.</p>

      <h2>Wie Einreichungen verwendet werden</h2>
      <p>Einreichungen kommen in eine nicht öffentliche Prüfwarteschlange. Vor der Freigabe kann die Moderation Formatierung, Zuschreibung, Kategorie, Tags oder Sprache korrigieren und eine Übersetzung in die jeweils andere Sprache ergänzen. Freigegebene Einreichungen werden zu öffentlichen Community-Quotes und können an alle ausgeliefert werden, die Quote nutzen. Abgelehnte Einreichungen werden nicht veröffentlicht. Die Moderation kann einen kurzen Hinweis hinzufügen, den dir die App zusammen mit dem Prüfergebnis anzeigt.</p>
      <p>Du kannst eine Einreichung zurückziehen, solange sie noch auf die Prüfung wartet. Beim Zurückziehen wird sie aus der Prüfwarteschlange gelöscht.</p>

      <h2>Speicherdauer</h2>
      <p>Die gehashte IP-Adresse wird nur für die 24-Stunden-Grenze für Einreichungen benötigt. Bei Einreichungen, die älter als 30 Tage sind, wird sie automatisch gelöscht. Die gehashte Installationskennung bleibt bei jeder Einreichung gespeichert, damit deine App weiterhin ihren Prüfstatus abfragen kann.</p>
      <p>Wird eine Einreichung zurückgezogen oder gelöscht, bleiben ihre gehashte Installationskennung, die gehashte IP-Adresse und der Einreichungszeitpunkt gesondert gespeichert, höchstens bis zwei Tage nach dem Einreichen, damit sie weiter auf die 24-Stunden-Grenze angerechnet wird. Danach werden sie automatisch gelöscht.</p>
      <p>Einreichungen und Moderationsergebnisse können zur Erkennung von Duplikaten, für den Prüfverlauf und zur Verhinderung von Missbrauch aufbewahrt werden. Löscht die Moderation eine Einreichung endgültig, wird ein Einweg-Hash ihres Textes aufbewahrt, damit derselbe Text nicht erneut angenommen wird, auch nicht mit anderer Zeichensetzung oder einem anderen Autor. Veröffentlichte Quotes bleiben verfügbar, bis die Moderation ihre Veröffentlichung aufhebt.</p>

      <h2>Bilder und Beschreibungen von Autoren</h2>
      <p>Um Bilder und kurze Beschreibungen der Autoren von Quotes anzuzeigen, lädt die App diese direkt von Wikipedia und Wikimedia Commons (betrieben von der Wikimedia Foundation). Diese Anfragen laufen nicht über den Quote-Dienst und enthalten keine Einreichungsdaten. Wikimedia erhält dabei aber, wie bei jedem Besuch von Wikipedia, deine IP-Adresse und den Namen des abgefragten Autors. Für diese Anfragen gilt die Datenschutzrichtlinie von Wikimedia.</p>

      <h2>Deine Verantwortung</h2>
      <p>Reiche nur Inhalte ein, die du teilen darfst. Reiche keine privaten Informationen, keine Belästigungen, keine rechtswidrigen Inhalte und keine wissentlich falschen Zuschreibungen ein. Eine Einreichung garantiert keine Veröffentlichung.</p>

      <h2>Dienstanbieter</h2>
      <p>Die Moderations-API, die Zugangskontrollen und die Datenbank laufen auf der Infrastruktur von Cloudflare. Cloudflare kann Netzwerkdaten gemäß seinen eigenen Datenschutzbestimmungen verarbeiten. Quote verkauft keine Einreichungsdaten und nutzt sie nicht für Werbung.</p>

      <h2>Fragen</h2>
      <p>Bei Fragen zu dieser Richtlinie oder für Löschanfragen wende dich über das <a href="https://github.com/Brokoli5191/Quote">GitHub-Repository von Quote</a> an das Projekt.</p>`
  }
};

// Served at /privacy (English) and /privacy?lang=de; /privacy?lang=en is the explicit English URL.
export function privacyPage(language: QuoteLanguage): Response {
  const text = POLICY[language];
  const other: QuoteLanguage = language === "de" ? "en" : "de";
  return new Response(`<!doctype html>
<html lang="${language}">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${text.title}</title>
  <link rel="alternate" hreflang="en" href="/privacy?lang=en">
  <link rel="alternate" hreflang="de" href="/privacy?lang=de">
  <style>
    :root { color-scheme: dark; font-family: ui-sans-serif, system-ui, sans-serif; background: #0b090e; color: #f4edfc; }
    * { box-sizing: border-box; }
    body { margin: 0; background: radial-gradient(circle at 85% 0, #32214d, transparent 30rem), #0b090e; }
    main { width: min(46rem, calc(100% - 2rem)); margin: auto; padding: 4rem 0 6rem; }
    a { color: #d4b7ff; }
    .top { display: flex; align-items: baseline; justify-content: space-between; gap: 1rem; }
    .eyebrow { color: #cbb4ec; font-size: .72rem; font-weight: 800; letter-spacing: .16em; text-transform: uppercase; }
    .language { font-size: .9rem; font-weight: 700; }
    h1 { margin: .6rem 0 1rem; font-family: Georgia, serif; font-size: clamp(2.4rem, 8vw, 4.7rem); line-height: 1; letter-spacing: -.04em; hyphens: auto; overflow-wrap: break-word; }
    h2 { margin-top: 2.3rem; font-size: 1.15rem; color: #dfc8ff; }
    p, li { color: #cfc4da; line-height: 1.7; }
    section { margin-top: 2rem; padding: 1.5rem; border: 1px solid #3b3147; border-radius: 1.3rem; background: #17131dcf; }
    .date { color: #93879f; font-size: .82rem; }
  </style>
</head>
<body>
  <main>
    <div class="top">
      <div class="eyebrow">${text.eyebrow}</div>
      <a class="language" href="/privacy?lang=${other}" hreflang="${other}" lang="${other}">${text.switchLabel}</a>
    </div>
    <h1>${text.heading}</h1>
    <p class="date">${text.effective}</p>
    <section>${text.body}
    </section>
  </main>
</body>
</html>`, {
    headers: {
      "Content-Type": "text/html; charset=UTF-8",
      "Content-Language": language,
      "Cache-Control": "public, max-age=3600",
      "X-Content-Type-Options": "nosniff",
      "Content-Security-Policy": "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'"
    }
  });
}
