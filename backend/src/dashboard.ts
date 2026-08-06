export function dashboardPage(): Response {
  return new Response(`<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
  <title>Quote Queue</title>
  <script>try{const t=localStorage.getItem('quote-theme');if(t)document.documentElement.dataset.theme=t;else if(matchMedia('(prefers-color-scheme: dark)').matches)document.documentElement.dataset.theme='dark'}catch(e){}</script>
  <style>
    :root {
      color-scheme: light;
      --paper: #eef0ec;
      --ink: #14201b;
      --muted: #69756f;
      --line: #c5ccc7;
      --panel: #f7f8f5;
      --panel-strong: #fbfcfa;
      --accent: #1f6950;
      --accent-ink: #ffffff;
      --soft: #dce8e2;
      --danger: #8c403d;
      --danger-soft: #f1dfdc;
      --shadow: #14201b2b;
    }
    :root[data-theme="dark"] {
      color-scheme: dark;
      --paper: #111713;
      --ink: #edf2ee;
      --muted: #94a29b;
      --line: #35423b;
      --panel: #19211c;
      --panel-strong: #1d2721;
      --accent: #72c5a4;
      --accent-ink: #102119;
      --soft: #243e33;
      --danger: #ef9b95;
      --danger-soft: #402724;
      --shadow: #00000066;
    }
    * { box-sizing: border-box; }
    html { background: var(--paper); }
    body { margin: 0; min-height: 100vh; background: var(--paper); color: var(--ink); font: 14px Arial, sans-serif; }
    button, input, textarea { font: inherit; }
    button { color: inherit; cursor: pointer; }
    button:focus-visible, input:focus-visible, textarea:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
    .mast { height: 66px; display: flex; align-items: center; justify-content: space-between; padding: 0 clamp(20px, 4vw, 64px); border-bottom: 1px solid var(--line); position: sticky; top: 0; z-index: 20; background: color-mix(in srgb, var(--paper) 94%, transparent); backdrop-filter: blur(14px); }
    .brand { display: flex; align-items: center; gap: 11px; text-transform: uppercase; letter-spacing: .13em; font-size: 10px; font-weight: bold; }
    .brand-mark { font: 28px Georgia, serif; border-right: 1px solid var(--line); padding-right: 11px; }
    .theme { width: 38px; height: 38px; display: grid; place-items: center; border: 1px solid var(--line); border-radius: 50%; background: var(--panel); font-size: 16px; transition: transform .45s cubic-bezier(.2, 1.65, .4, 1), background .2s ease; }
    .theme:hover { transform: rotate(16deg) scale(1.08); background: var(--soft); }
    .theme .sun { display: none; }
    [data-theme="dark"] .theme .sun { display: inline; }
    [data-theme="dark"] .theme .moon { display: none; }
    .page { padding: 34px clamp(20px, 5vw, 72px) 64px; max-width: 1500px; margin: auto; }
    .intro { display: flex; align-items: end; justify-content: space-between; padding: 8px 0 30px; }
    .over, .head, .review-top, .field-label, .origin { font-size: 11px; text-transform: uppercase; letter-spacing: .13em; font-weight: bold; }
    .over { color: var(--accent); margin: 0 0 13px; }
    .intro h1 { font: clamp(46px, 5.2vw, 76px)/.94 Georgia, serif; margin: 0; letter-spacing: -.045em; }
    .intro h1 i { font-weight: normal; color: var(--accent); }
    .count { display: flex; align-items: end; gap: 12px; }
    .count b { font: 52px/.82 monospace; color: var(--accent); }
    .count span { font-size: 11px; line-height: 1.35; text-transform: uppercase; color: var(--muted); }
    .controls { display: flex; justify-content: space-between; align-items: center; background: var(--panel); padding: 8px 10px; border: 1px solid var(--line); }
    .tabs { display: flex; align-items: stretch; gap: 3px; }
    .tabs button { min-height: 42px; display: inline-flex; align-items: center; justify-content: center; gap: 9px; border: 0; background: transparent; color: var(--muted); padding: 9px 15px; font-size: 13px; line-height: 1; transition: color .2s ease, background .2s ease, transform .4s cubic-bezier(.2, 1.65, .4, 1); }
    .tabs button:hover { background: var(--soft); transform: translateY(-2px); }
    .tabs button.active { background: var(--accent); color: var(--accent-ink); }
    .tabs b { flex: 0 0 22px; width: 22px; height: 22px; display: inline-grid; place-items: center; border: 1px solid currentColor; border-radius: 50%; font-size: 11px; line-height: 1; }
    .search { display: flex; align-items: center; gap: 8px; color: var(--muted); border-left: 1px solid var(--line); padding-left: 17px; }
    .search input { width: min(24vw, 270px); border: 0; outline: 0; color: var(--ink); background: transparent; }
    .search input::placeholder { color: var(--muted); }
    .content { display: grid; grid-template-columns: minmax(0, 1.55fr) minmax(320px, .65fr); margin-top: 22px; border: 1px solid var(--line); background: var(--panel); }
    .queue { min-width: 0; padding: 22px 24px 32px; }
    .head { display: flex; justify-content: space-between; color: var(--muted); padding: 0 2px 15px; }
    .list { border-top: 1px solid var(--line); }
    .row { width: 100%; min-height: 108px; display: grid; grid-template-columns: 36px minmax(0, 1fr) auto 24px; align-items: center; gap: 15px; padding: 17px 12px; text-align: left; border: 0; border-bottom: 1px solid var(--line); background: transparent; transition: background .2s ease, transform .42s cubic-bezier(.2, 1.65, .4, 1), box-shadow .2s ease; animation: row-in .55s backwards cubic-bezier(.2, .9, .3, 1.2); animation-delay: var(--delay, 0ms); }
    .row:hover, .row.active { background: var(--soft); transform: translateX(5px); box-shadow: -5px 0 0 var(--accent); }
    .num, .row time { color: var(--muted); font: 12px/1.2 monospace; }
    .copy { min-width: 0; }
    .copy q { display: block; font: 19px/1.4 Georgia, serif; quotes: none; }
    .copy small { display: block; margin-top: 10px; color: var(--accent); font-size: 13px; line-height: 1.35; font-weight: bold; }
    .status-dot { width: 7px; height: 7px; margin-right: 6px; display: inline-block; border-radius: 50%; background: var(--accent); }
    .row.unpublished { background: color-mix(in srgb, var(--soft) 38%, transparent); }
    .review { min-width: 0; position: relative; display: flex; flex-direction: column; padding: 28px 30px; border-left: 1px solid var(--line); background: var(--panel-strong); }
    .review-top { display: flex; justify-content: space-between; color: var(--muted); }
    .mark { height: 74px; margin-top: 25px; color: var(--accent); font: 82px/.9 Georgia, serif; }
    .review blockquote { margin: 0; font: clamp(23px, 1.8vw, 31px)/1.22 Georgia, serif; letter-spacing: -.02em; overflow-wrap: anywhere; }
    .author { margin: 31px 0 20px; padding-top: 18px; border-top: 1px solid var(--line); }
    .author strong { display: block; margin-top: 8px; font: 19px Georgia, serif; }
    .details { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin: 0 0 18px; }
    .detail span { display: block; margin-top: 6px; color: var(--muted); font-size: 14px; line-height: 1.4; overflow-wrap: anywhere; }
    .tags { display: flex; flex-wrap: wrap; gap: 5px; margin-top: 6px; }
    .tag { padding: 5px 8px; background: var(--soft); color: var(--accent); font-size: 12px; }
    .origin { margin: auto 0 0; padding-top: 24px; color: var(--muted); }
    .actions { display: grid; grid-template-columns: repeat(auto-fit, minmax(105px, 1fr)); gap: 7px; margin-top: 19px; }
    .actions button { min-height: 46px; border: 1px solid var(--line); background: transparent; font-size: 11px; font-weight: bold; text-transform: uppercase; letter-spacing: .06em; transition: transform .4s cubic-bezier(.2, 1.65, .4, 1), background .2s ease, box-shadow .2s ease; }
    .actions button:hover, .dialog-actions button:hover { transform: translateY(-3px); box-shadow: 0 7px 16px var(--shadow); }
    .actions button:active, .dialog-actions button:active { transform: scale(.96); }
    .actions .primary { border-color: var(--accent); background: var(--accent); color: var(--accent-ink); }
    .actions .danger { color: var(--danger); border-color: var(--danger); }
    .empty { padding: 80px 20px; text-align: center; color: var(--muted); font: 15px Georgia, serif; }
    .review-empty { margin: auto; }
    .close { display: none; position: absolute; top: 17px; right: 18px; width: 34px; height: 34px; border: 0; background: transparent; font-size: 25px; }
    .backdrop { display: none; }
    dialog { width: min(580px, calc(100% - 28px)); max-height: calc(100dvh - 28px); overflow: auto; border: 1px solid var(--line); border-radius: 0; padding: 0; color: var(--ink); background: var(--panel-strong); box-shadow: 0 24px 80px var(--shadow); }
    dialog::backdrop { background: color-mix(in srgb, var(--ink) 45%, transparent); backdrop-filter: blur(4px); }
    .editor-form { display: grid; gap: 16px; padding: 28px; }
    .editor-head { display: flex; justify-content: space-between; align-items: start; padding-bottom: 18px; border-bottom: 1px solid var(--line); }
    .editor-head h2 { margin: 5px 0 0; font: 30px Georgia, serif; letter-spacing: -.03em; }
    .editor-close { border: 0; background: transparent; font-size: 24px; }
    .editor-form label { display: grid; gap: 7px; color: var(--muted); font-size: 10px; font-weight: bold; text-transform: uppercase; letter-spacing: .1em; }
    .editor-form input, .editor-form textarea { width: 100%; border: 1px solid var(--line); border-radius: 0; padding: 11px 12px; color: var(--ink); background: var(--paper); resize: vertical; text-transform: none; letter-spacing: normal; font-weight: normal; }
    .dialog-actions { display: flex; justify-content: end; gap: 7px; padding-top: 5px; }
    .dialog-actions button { min-height: 44px; padding: 0 17px; border: 1px solid var(--line); background: transparent; font-size: 10px; font-weight: bold; text-transform: uppercase; letter-spacing: .08em; }
    .dialog-actions .primary { border-color: var(--accent); background: var(--accent); color: var(--accent-ink); }
    #notice { position: fixed; z-index: 60; left: 50%; bottom: 24px; width: max-content; max-width: calc(100% - 28px); margin: 0; padding: 11px 15px; transform: translateX(-50%); border: 1px solid var(--line); background: var(--panel-strong); color: var(--muted); box-shadow: 0 10px 35px var(--shadow); font-size: 12px; }
    #notice.error { color: var(--danger); border-color: var(--danger); }
    .mast { animation: mast-in .55s both cubic-bezier(.2, .9, .3, 1.15); }
    .intro { animation: rise-in .65s .08s both cubic-bezier(.2, .9, .3, 1.15); }
    .controls { animation: rise-in .65s .16s both cubic-bezier(.2, .9, .3, 1.15); }
    .content { animation: rise-in .7s .24s both cubic-bezier(.2, .9, .3, 1.15); }
    .review > * { animation: detail-in .45s both cubic-bezier(.2, .9, .3, 1.15); }
    @keyframes mast-in { from { opacity: 0; transform: translateY(-14px); } }
    @keyframes rise-in { from { opacity: 0; transform: translateY(22px) scale(.985); } }
    @keyframes row-in { from { opacity: 0; transform: translateY(12px); } }
    @keyframes detail-in { from { opacity: 0; transform: translateX(10px); } }
    @media (min-width: 721px) and (max-width: 1050px) {
      .page { padding-inline: 28px; }
      .content { grid-template-columns: minmax(0, 1.25fr) minmax(290px, .75fr); }
      .queue { padding-inline: 18px; }
      .review { padding-inline: 22px; }
      .row { grid-template-columns: 26px minmax(0, 1fr) 20px; gap: 10px; }
      .row time { display: none; }
    }
    @media (max-width: 720px) {
      body.sheet-open { overflow: hidden; }
      .mast { height: 58px; padding: 0 17px; }
      .brand-mark { font-size: 23px; }
      .theme { width: 34px; height: 34px; }
      .page { padding: 18px 14px 76px; }
      .intro { padding: 12px 3px 22px; }
      .intro h1 { font-size: clamp(36px, 11vw, 42px); line-height: .98; letter-spacing: -.04em; }
      .count { display: none; }
      .controls { display: block; padding: 6px; position: sticky; top: 58px; z-index: 10; }
      .tabs { display: grid; grid-template-columns: repeat(3, 1fr); }
      .tabs button { gap: 6px; padding: 9px 5px; font-size: 12px; }
      .tabs b { flex-basis: 21px; width: 21px; height: 21px; font-size: 10px; }
      .search { margin-top: 6px; border: 0; border-top: 1px solid var(--line); padding: 5px 7px 0; }
      .search input { width: 100%; height: 38px; font-size: 13px; }
      .content { display: block; border: 0; background: none; margin-top: 16px; }
      .queue { padding: 0; }
      .head { padding-inline: 4px; }
      .list { background: var(--panel); border: 1px solid var(--line); }
      .row { min-height: 100px; grid-template-columns: 29px minmax(0, 1fr) 20px; gap: 9px; padding: 15px 11px; }
      .row time { display: none; }
      .copy q { font-size: 17px; line-height: 1.42; }
      .copy small { font-size: 12px; }
      .review { position: fixed; z-index: 40; left: 0; right: 0; bottom: 0; min-height: 0; max-height: 86dvh; border: 0; border-top: 1px solid var(--line); border-radius: 18px 18px 0 0; padding: 22px 20px max(20px, env(safe-area-inset-bottom)); box-shadow: 0 -18px 60px var(--shadow); transform: translateY(105%); transition: transform .25s ease; overflow: auto; }
      .review.open { transform: translateY(0); }
      .review.open::before { content: ""; width: 38px; height: 4px; border-radius: 4px; background: var(--line); position: absolute; top: 8px; left: 50%; transform: translateX(-50%); }
      .review-top { padding-top: 13px; padding-right: 30px; }
      .mark { height: 55px; font-size: 58px; margin-top: 24px; }
      .review blockquote { font-size: 22px; line-height: 1.28; }
      .actions { position: sticky; bottom: -1px; background: var(--panel-strong); padding-top: 10px; }
      .actions button { min-height: 50px; font-size: 10px; }
      .close { display: block; }
      .backdrop.open { display: block; position: fixed; inset: 0; z-index: 30; border: 0; background: color-mix(in srgb, var(--ink) 45%, transparent); }
      .review-empty { display: none; }
      .editor-form { padding: 22px; }
    }
    @media (prefers-reduced-motion: reduce) { *, *::before, *::after { scroll-behavior: auto !important; transition: none !important; animation: none !important; } }
  </style>
</head>
<body>
  <header class="mast">
    <div class="brand"><span class="brand-mark">Q</span> Quote queue</div>
    <button class="theme" id="theme" type="button" aria-label="Switch color theme"><span class="moon">☾</span><span class="sun">☀</span></button>
  </header>
  <main class="page">
    <section class="intro">
      <div><h1>Words awaiting<br><i>consideration.</i></h1></div>
      <div class="count"><b id="pendingCount">00</b><span>quotes<br>to review</span></div>
    </section>
    <section class="controls">
      <nav class="tabs" id="tabs" aria-label="Submission status"></nav>
      <label class="search">⌕ <input id="search" type="search" placeholder="Search quote or author" autocomplete="off"></label>
    </section>
    <section class="content">
      <div class="queue">
        <div class="head"><span id="heading">Pending</span><span id="results">Loading</span></div>
        <div class="list" id="list" aria-live="polite"></div>
      </div>
      <aside class="review" id="review"><div class="empty review-empty">Select a quote to review.</div></aside>
    </section>
    <p id="notice" role="status" hidden></p>
  </main>
  <button class="backdrop" id="backdrop" aria-label="Close review"></button>
  <dialog id="editor">
    <form class="editor-form" id="form">
      <div class="editor-head"><div><span class="over">Moderation details</span><h2 id="editorTitle">Edit and approve</h2></div><button class="editor-close" type="button" id="editorClose" aria-label="Close editor">×</button></div>
      <input id="id" type="hidden">
      <input id="communityId" type="hidden">
      <label>Quote<textarea id="quoteText" rows="5" maxlength="500" required></textarea></label>
      <label>Author<input id="author" maxlength="100"></label>
      <label>Category<input id="category" maxlength="50" required></label>
      <label>Tags, comma-separated<input id="tags"></label>
      <label>Private reviewer note<textarea id="note" rows="2" maxlength="500"></textarea></label>
      <div class="dialog-actions"><button type="button" id="cancel">Cancel</button><button class="primary" type="submit">Approve quote</button></div>
    </form>
  </dialog>
  <script>
    const statuses = ['pending', 'approved', 'rejected'];
    let active = 'pending';
    let current = [];
    let counts = [];
    let selectedId = null;
    let query = '';
    const list = document.querySelector('#list');
    const notice = document.querySelector('#notice');
    const tabs = document.querySelector('#tabs');
    const review = document.querySelector('#review');
    const editor = document.querySelector('#editor');
    const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, character => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[character]));
    const parseTags = value => { try { const tags = JSON.parse(value || '[]'); return Array.isArray(tags) ? tags : []; } catch (error) { return []; } };
    const titleCase = value => value[0].toUpperCase() + value.slice(1);
    const selected = () => current.find(item => item.id === selectedId);

    function showNotice(message, isError = false) {
      notice.textContent = message;
      notice.classList.toggle('error', isError);
      notice.hidden = !message;
    }

    async function load(status = active) {
      active = status;
      showNotice('Loading submissions...');
      list.innerHTML = '';
      try {
        const response = await fetch('/admin/api/submissions?status=' + encodeURIComponent(status));
        const data = await response.json();
        if (!response.ok) throw new Error(data.error || 'Could not load submissions.');
        current = data.submissions;
        counts = data.counts;
        if (!current.some(item => item.id === selectedId)) selectedId = current[0]?.id ?? null;
        renderTabs();
        render();
        showNotice('');
      } catch (error) {
        showNotice(error.message, true);
        list.innerHTML = '<div class="empty">The queue could not be loaded.</div>';
        document.querySelector('#results').textContent = 'Unavailable';
      }
    }

    function renderTabs() {
      const countMap = Object.fromEntries(counts.map(item => [item.status, Number(item.count)]));
      tabs.innerHTML = statuses.map(status => '<button type="button" data-status="' + status + '" class="' + (status === active ? 'active' : '') + '">' + titleCase(status) + '<b>' + (countMap[status] || 0) + '</b></button>').join('');
      tabs.querySelectorAll('button').forEach(button => button.onclick = () => { closeReview(); load(button.dataset.status); });
      document.querySelector('#pendingCount').textContent = String(countMap.pending || 0).padStart(2, '0');
    }

    function render() {
      const visible = current.filter(item => (item.quoteText + ' ' + (item.author || '') + ' ' + item.category).toLowerCase().includes(query));
      document.querySelector('#heading').textContent = titleCase(active);
      document.querySelector('#results').textContent = visible.length + ' quote' + (visible.length === 1 ? '' : 's');
      if (!visible.length) {
        list.innerHTML = '<div class="empty">' + (query ? 'No quotes match your search.' : 'Nothing here. The queue is clear.') + '</div>';
      } else {
        list.innerHTML = visible.map((item, index) => {
          const unpublished = item.status === 'approved' && item.communityActive === 0;
          const state = unpublished ? '<span class="status-dot"></span>Unpublished · ' : '';
          return '<button type="button" class="row ' + (item.id === selectedId ? 'active ' : '') + (unpublished ? 'unpublished' : '') + '" style="--delay:' + Math.min(index * 45, 360) + 'ms" data-id="' + escapeHtml(item.id) + '"><span class="num">' + String(index + 1).padStart(2, '0') + '</span><span class="copy"><q>' + escapeHtml(item.quoteText) + '</q><small>' + state + escapeHtml(item.author || 'Unknown') + '</small></span><time>' + escapeHtml(formatDate(item.submittedAt)) + '</time><span>↗</span></button>';
        }).join('');
      }
      list.querySelectorAll('.row').forEach(button => button.onclick = () => { selectedId = button.dataset.id; render(); openReview(); });
      renderReview();
    }

    function renderReview() {
      const item = selected();
      if (!item) {
        review.innerHTML = '<div class="empty review-empty">Select a quote to review.</div>';
        return;
      }
      const tags = parseTags(item.tagsJson);
      const publication = item.status === 'approved' ? (item.communityActive ? 'Published' : 'Unpublished') + ' · revision ' + item.communityRevision : titleCase(item.status);
      let actions = '';
      if (item.status === 'pending') actions = '<button class="danger" data-reject>Reject</button><button class="primary" data-approve>Review & approve ↗</button>';
      if (item.status === 'rejected') actions = '<button class="danger" data-delete>Delete permanently</button><button class="primary" data-approve>Review & approve</button>';
      if (item.status === 'approved' && item.communityQuoteId) actions = (item.communityActive ? '<button data-toggle>Unpublish</button>' : '<button class="danger" data-delete-community>Delete permanently</button><button data-toggle>Republish</button>') + '<button class="primary" data-edit>Edit quote</button>';
      review.innerHTML = '<button class="close" type="button" aria-label="Close review">×</button><div class="review-top"><span>Selected quote</span><span>' + escapeHtml(formatDate(item.submittedAt)) + '</span></div><span class="mark">“</span><blockquote>' + escapeHtml(item.quoteText) + '</blockquote><div class="author"><span class="field-label">Author</span><strong>' + escapeHtml(item.author || 'Unknown') + '</strong></div><div class="details"><div class="detail"><span class="field-label">Category</span><span>' + escapeHtml(item.category) + '</span></div><div class="detail"><span class="field-label">Status</span><span>' + escapeHtml(publication) + '</span></div></div>' + (tags.length ? '<div><span class="field-label">Tags</span><div class="tags">' + tags.map(tag => '<span class="tag">#' + escapeHtml(tag) + '</span>').join('') + '</div></div>' : '') + '<p class="origin">Received through the app' + (item.appVersion ? ' · Version ' + escapeHtml(item.appVersion) : '') + '</p><div class="actions">' + actions + '</div>';
      review.querySelector('.close').onclick = closeReview;
      const approveButton = review.querySelector('[data-approve]');
      const rejectButton = review.querySelector('[data-reject]');
      const deleteButton = review.querySelector('[data-delete]');
      const deleteCommunityButton = review.querySelector('[data-delete-community]');
      const editButton = review.querySelector('[data-edit]');
      const toggleButton = review.querySelector('[data-toggle]');
      if (approveButton) approveButton.onclick = () => openEditor(item.id);
      if (rejectButton) rejectButton.onclick = () => reject(item.id);
      if (deleteButton) deleteButton.onclick = () => deleteRejected(item.id);
      if (deleteCommunityButton) deleteCommunityButton.onclick = () => deleteCommunity(item.communityQuoteId);
      if (editButton) editButton.onclick = () => openCommunityEditor(item.communityQuoteId);
      if (toggleButton) toggleButton.onclick = () => toggleCommunity(item.communityQuoteId, item.communityActive === 1);
    }

    function formatDate(timestamp) {
      if (!timestamp) return '';
      return new Date(timestamp * 1000).toLocaleString(undefined, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
    }

    function openReview() {
      review.classList.add('open');
      document.querySelector('#backdrop').classList.add('open');
      document.body.classList.add('sheet-open');
    }

    function closeReview() {
      review.classList.remove('open');
      document.querySelector('#backdrop').classList.remove('open');
      document.body.classList.remove('sheet-open');
    }

    function fillEditor(item, communityId = '') {
      document.querySelector('#id').value = item.id;
      document.querySelector('#communityId').value = communityId;
      document.querySelector('#editorTitle').textContent = communityId ? 'Edit community quote' : 'Edit and approve';
      document.querySelector('#quoteText').value = item.quoteText;
      document.querySelector('#author').value = item.author || '';
      document.querySelector('#category').value = item.category;
      document.querySelector('#tags').value = parseTags(item.tagsJson).join(', ');
      document.querySelector('#note').value = '';
      editor.showModal();
    }

    function openEditor(id) {
      const item = current.find(value => value.id === id);
      if (item) fillEditor(item);
    }

    function openCommunityEditor(id) {
      const item = current.find(value => value.communityQuoteId === id);
      if (item) fillEditor(item, id);
    }

    async function moderate(id, action, values) {
      const response = await fetch('/admin/api/submissions/' + id, { method: 'PATCH', headers: {'Content-Type':'application/json'}, body: JSON.stringify({...values, action}) });
      const data = await response.json();
      if (!response.ok) throw new Error(data.error || 'Review failed.');
      selectedId = null;
      closeReview();
      await load(active);
    }

    async function reject(id) {
      const item = current.find(value => value.id === id);
      if (!item || !confirm('Reject this submission?')) return;
      try { await moderate(id, 'reject', { quoteText:item.quoteText, author:item.author, category:item.category, tags:parseTags(item.tagsJson), note:'' }); }
      catch (error) { showNotice(error.message, true); }
    }

    async function deleteRejected(id) {
      if (!confirm('Permanently delete this rejected submission? This cannot be undone.')) return;
      try {
        const response = await fetch('/admin/api/submissions/' + id, {method: 'DELETE'});
        if (!response.ok) { const data = await response.json(); throw new Error(data.error || 'Delete failed.'); }
        selectedId = null;
        closeReview();
        await load(active);
      } catch (error) { showNotice(error.message, true); }
    }

    async function updateCommunity(id, action, values = {}) {
      const response = await fetch('/admin/api/community/' + id, { method: 'PATCH', headers: {'Content-Type':'application/json'}, body: JSON.stringify({...values, action}) });
      const data = await response.json();
      if (!response.ok) throw new Error(data.error || 'Community update failed.');
      await load(active);
    }

    async function toggleCommunity(id, activeNow) {
      const action = activeNow ? 'unpublish' : 'republish';
      if (!confirm((activeNow ? 'Unpublish' : 'Republish') + ' this community quote?')) return;
      try { await updateCommunity(id, action); }
      catch (error) { showNotice(error.message, true); }
    }

    async function deleteCommunity(id) {
      if (!confirm('Permanently delete this unpublished quote? This cannot be undone.')) return;
      try {
        const response = await fetch('/admin/api/community/' + id, {method: 'DELETE'});
        if (!response.ok) { const data = await response.json(); throw new Error(data.error || 'Delete failed.'); }
        selectedId = null;
        closeReview();
        await load(active);
      } catch (error) { showNotice(error.message, true); }
    }

    document.querySelector('#search').oninput = event => { query = event.target.value.trim().toLowerCase(); render(); };
    document.querySelector('#backdrop').onclick = closeReview;
    document.querySelector('#cancel').onclick = () => editor.close();
    document.querySelector('#editorClose').onclick = () => editor.close();
    document.querySelector('#theme').onclick = () => {
      const theme = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
      document.documentElement.dataset.theme = theme;
      try { localStorage.setItem('quote-theme', theme); } catch (error) {}
    };
    document.querySelector('#form').onsubmit = async event => {
      event.preventDefault();
      const values = {
        quoteText: document.querySelector('#quoteText').value,
        author: document.querySelector('#author').value,
        category: document.querySelector('#category').value,
        tags: document.querySelector('#tags').value.split(',').map(value => value.trim()).filter(Boolean),
        note: document.querySelector('#note').value
      };
      try {
        const communityId = document.querySelector('#communityId').value;
        if (communityId) await updateCommunity(communityId, 'update', values);
        else await moderate(document.querySelector('#id').value, 'approve', values);
        editor.close();
      } catch (error) { alert(error.message); }
    };
    load();
  </script>
</body>
</html>`, {
    headers: {
      "Content-Type": "text/html; charset=UTF-8",
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff",
      "Content-Security-Policy": "default-src 'none'; connect-src 'self'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'"
    }
  });
}
