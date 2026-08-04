# Quote App Improvements — Design

Date: 2026-08-04
Branch: `worktree-quote-improvements`

## Goal

Seven user-requested improvements to the Quote Android app (Kotlin/Compose, Room, single-Activity Compose UI). Grouped into independent workstreams so each can be built, reviewed, and — if needed — reverted on its own.

Build note: Gradle cannot run in this sandbox (loopback error). Verification is by code review + a compile-sanity pass in Android Studio on the real machine. Any code must be self-consistent and compile-safe by inspection.

---

## Workstream A — Larger, better-tagged quote database (item 2)

**Decision (user):** bundle a bigger *offline* dataset; overhaul categories (fix mapping + more categories). No online API. No fabricated quotes.

**Source (confirmed reachable):** `Abirate/english_quotes` (HuggingFace), 2508 fully-tagged quotes, `{quote, author, tags[]}` — the same Goodreads-derived format the current `quotes_seed.json` is a filtered subset of. Using the full set roughly doubles the library (1170 → ~2500) with real tags. Optional stretch: merge unique quotes from `JamesFT/Database-Quotes-JSON` (~5400, untagged) for extra volume, tagged best-effort — decided at build time only if quality holds; default is the clean ~2500.

**Data pipeline (build-time script, not shipped):**
1. Fetch `quotes.jsonl`, strip smart-quotes/whitespace, drop empty author/text.
2. Deduplicate on normalized `(text, author)`.
3. Normalize tags: lowercase, trim, drop noise tags (`misattributed-*`, `attributed-no-source`, author-name tags).
4. Emit `quotes_seed.json` in the existing shape (`{quote, author, tags}`) so the loader is unchanged.

**Category overhaul (`QuoteRepository.mapTagsToCategory`):**
- Replace the 15 hardcoded categories + "everything unmatched → Inspirational" dump with an expanded category set (~20–24) plus a **tag-cluster synonym map** (many source tags → one category), e.g. `hope, optimism, positivity → Optimism`; `death, mortality, grief → Death`; `science, knowledge, learning → Knowledge`.
- Matching order: exact tag → synonym-map tag → keyword-in-text → **"Uncategorized"** final fallback (not Inspirational). "Uncategorized" is a real browsable category so the dump is visible and honest, not hidden inside Inspirational.
- The full source tag string stays stored per quote (already the case) so search over tags keeps working.
- Re-seed trigger: bump the `database_json_seeded_v5_2` pref key to a new version so existing installs re-seed once. User-added quotes and favorites are preserved (favorites live on separate rows; re-seed clears only non-user rows — verify `checkAndSeedDatabase` clears then re-inserts without touching `isUserAdded`).

**Risk:** re-seed on update wipes non-favorited seed rows and re-selects the daily quote pool. Acceptable (seed rows are not user data). Must confirm favorites/user quotes survive — favorites on seed rows are keyed by row id, which changes on re-seed. **Mitigation:** re-favoriting is lost on re-seed today already; to be safe, match-and-restore favorites by `(text, author)` after re-seed, or gate the destructive clear. Design choice: add a favorite-preserving re-seed (restore favorites by text+author match) so the bigger DB doesn't cost users their saved quotes.

**Category UI:** the category lists are duplicated in `LibraryScreen` (bento grid + filter sheet). Extract the category list to a single source of truth (e.g. `Categories.kt`) consumed by both, so adding categories is one edit.

---

## Workstream B — True low-performance mode = zero animations (item 3)

Today `lowPerformanceMode` only *reduces* animation (still slides+fades on tab switch, still fades on quote change, still runs infinite empty-state animations conditionally). Target: when on, **no** animations.

- MainActivity tab `AnimatedContent`: when low-perf, use `EnterTransition.None`/`ExitTransition.None` (instant swap) instead of the tween slide/fade branch.
- Predictive-back preview/graphicsLayer motion: when low-perf, skip the peek/translate (instant pop).
- DailyScreen quote `AnimatedContent`: low-perf → no fade.
- Library `AnimatedContent` + card entry animations (`QuoteBrowseItemCard` offset/alpha, empty-state infinite transitions): low-perf → static.
- Heart bounce / pull-to-refresh elastic: low-perf → no scale spring (action still works, just instant). Pull-to-refresh gesture stays functional; only the elastic visual is dropped.

Implementation: thread `lowPerformanceMode` where it isn't yet, and standardize a small helper pattern so "off = instant" is consistent.

---

## Workstream C — General jank fixes in normal mode (items 1 + 7)

Target the stutters on high-end devices even with animations on.

**Startup (item 1):**
- Add `androidx.core:core-splashscreen` `installSplashScreen()` so the first frame is owned and cold-start feels instant instead of a blank window.
- Keep DB build + `loadThemeSettings()` synchronous in `onCreate` (already correct — prevents theme flash); confirm no other main-thread I/O in `onCreate`.
- Seeding already runs on `Dispatchers.IO` via `LaunchedEffect`; the bigger seed parse (~650KB) must stay off the main thread and show content immediately (fallback quote already exists). Confirm the update check stays deferred.

**Runtime jank (item 7):**
- **Baseline Profile:** add a `baseline-prof.txt` / baseline profile module so hot Compose paths are AOT-compiled — the highest-leverage fix for "ruckler auf premium geräten". This is the headline item-7 change.
- **Predictive-back double composition:** during back gesture MainActivity composes a *second full* `DailyScreen(viewModel)` as the left peek. That's an expensive extra subtree every gesture frame. Reduce cost (lighter preview, or reuse) — investigate and cut.
- **Spring `AnimatedContent` on tab switch:** the scale+slide+fade spring on full-screen subtrees is heavy. Tune to a lighter spec / fewer simultaneous animated properties in normal mode.
- **`stateIn` `WhileSubscribed(5000)`** on `allQuotes` with ~2500 rows: confirm the list isn't re-collected/re-filtered on every recomposition; ensure `filteredQuotes` combine is not doing O(n) work on the main thread per frame. Move filtering off main if needed.
- Remove unused heavy imports (e.g. `blur`, `AsyncImage` where no image is loaded) if they pull work.

Each fix is measured/justified by inspection; no speculative rewrites.

---

## Workstream D — Daily ("start") screen cleanup + author personalization (item 4)

"Start menu" = the Daily tab. The "About the Sage" card shows generic filler (`aboutAuthor` is empty for all seeds, so every quote shows "A wisdom practitioner with deep teachings…").

**Decision (user):** remove the filler card; personalize the author cleanly (no invented bios).

- Drop the fake "About the Sage" description text entirely.
- Replace with a slim, honest author block: author initial avatar (kept, it's generated not fake) + author name + the quote's real tags as chips. No fabricated biography.
- Keep the action row (favorite / copy / share / "Learn More" → Wikipedia). "Learn More" already links to the real Wikipedia page — that's the legitimate "personalization" path for author info.
- Net effect: less vertical filler, nothing fake, author gets real prominence.

Also re-apply the trivial in-flight tweak (`ContentTransform(sizeTransform = null)` on the quote `AnimatedContent`) that exists uncommitted on the user's main copy, since this file is being rewritten.

---

## Workstream E — Theme options polish: AMOLED + Material You (item 5a)

Mostly already built: Settings exposes AMOLED / DARK / LIGHT / DYNAMIC (System = Material You) + accent colors, and Theme.kt implements all of them including a true-black AMOLED path and `dynamic*ColorScheme`.

Scope here is **polish/verification**, not new architecture:
- Confirm DYNAMIC (Material You) correctly follows system light/dark and that accents are properly disabled in dynamic mode (the Settings screen already greys them out — verify).
- Confirm AMOLED true-black (`0xFF000000`) is applied to all surfaces including nav bar background.
- Make the two options clearly labeled/discoverable in Settings (user may not have noticed they exist).
- No breaking changes to persisted `theme_mode` values.

---

## Workstream F — Library category subtitles removed (item 5b)

In `LibraryScreen`, each `CategoryBentoCard` renders a `description` line ("Ignite your inner fire…", etc.) under the category name. Remove the description text under the headings.

- Drop the description `Text` from `CategoryBentoCard` (and the now-unused `description` field / data).
- Rebalance card layout so the name sits well without the subtitle (adjust height/alignment). Keep the icon and name.

---

## Workstream G — New app icon: open book, adaptive (item 6)

**Decision (user):** open-book motif, simple but nice, adaptive icon (foreground + background layers, like now).

- Redraw `ic_launcher_foreground.xml` as a clean minimalist open-book vector (single accent color, centered, within the adaptive safe zone ~66dp of 108dp).
- Update `ic_launcher_background.xml` to a simple solid or subtle gradient matching the app's violet/primary brand.
- Keep the existing adaptive `mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml` referencing the two layers (vector-based, so density webp regeneration is optional; if legacy webp mipmaps must match, note that they can't be regenerated in-sandbox and will be updated in Android Studio).
- Verify monochrome/themed-icon layer if present for Android 13+ themed icons.

---

## Build / integration order

1. F (subtitles) + E (theme polish) + D (Daily cleanup) — small, isolated UI edits.
2. A (data + categories) — the biggest change; single source of truth for categories, favorite-preserving re-seed.
3. B (true low-perf mode) — threads a flag through the screens touched above.
4. C (jank + startup + Baseline Profile) — cross-cutting, done after UI is stable so profiles capture the real hot paths.
5. G (icon) — independent, any time.

Each workstream is a separate commit on `worktree-quote-improvements`. Ship as one draft PR with per-workstream commits so the user can cherry-pick/revert.

## Out of scope / non-goals

- No online quote fetching or new network dependencies.
- No fabricated author biographies or quotes.
- No change to the widget, notifications, backup/restore, or updater beyond what perf work touches.
- Legacy density `.webp` launcher mipmaps regenerated only in Android Studio (can't rasterize in sandbox).

## Testing

- Data pipeline: run the build-time script, assert count, no empty fields, dedup worked, every quote maps to a real category, "Uncategorized" count is small.
- Categories: unit-test `mapTagsToCategory` against representative tag sets.
- Re-seed: verify favorites/user quotes survive a version bump (favorite restore by text+author).
- UI: manual pass in Android Studio (build blocked in sandbox) — low-perf shows zero motion; theme modes render; Library has no subtitles; Daily has no filler; icon renders adaptive.
