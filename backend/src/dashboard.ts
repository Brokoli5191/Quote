export function dashboardPage(): Response {
  return new Response(`<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Quote Moderation</title>
  <style>
    :root { color-scheme: dark; font-family: Inter, ui-sans-serif, system-ui, sans-serif; background: #0b090e; color: #f5efff; }
    * { box-sizing: border-box; }
    body { margin: 0; min-height: 100vh; background: radial-gradient(circle at 80% -10%, #38255a 0, transparent 34rem), #0b090e; }
    button, input, textarea { font: inherit; }
    header { max-width: 76rem; margin: auto; padding: 3.5rem 1.25rem 1.5rem; display: flex; gap: 1rem; align-items: end; justify-content: space-between; }
    .eyebrow { color: #cbb4ec; font-size: .72rem; font-weight: 800; letter-spacing: .16em; text-transform: uppercase; }
    h1 { margin: .35rem 0 0; font-family: Georgia, serif; font-size: clamp(2.4rem, 7vw, 4.8rem); font-weight: 500; letter-spacing: -.05em; }
    .live { color: #a5e6bc; border: 1px solid #355944; border-radius: 99rem; padding: .5rem .8rem; font-size: .78rem; font-weight: 700; background: #102419; }
    main { max-width: 76rem; margin: auto; padding: 0 1.25rem 5rem; }
    nav { display: flex; gap: .5rem; overflow-x: auto; padding: .8rem 0 1.5rem; }
    nav button { border: 1px solid #3c3347; color: #cfc3db; background: #17131d; border-radius: .8rem; padding: .7rem .9rem; cursor: pointer; white-space: nowrap; }
    nav button.active { color: #241633; border-color: #d8baff; background: #d8baff; font-weight: 800; }
    .count { opacity: .7; margin-left: .25rem; }
    #notice { min-height: 1.5rem; color: #cbbbd8; }
    #list { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(100%, 22rem), 1fr)); gap: 1rem; }
    article { display: flex; flex-direction: column; min-height: 19rem; padding: 1.25rem; border: 1px solid #352d3f; border-radius: 1.3rem; background: linear-gradient(145deg, #1c1723, #121016); box-shadow: 0 1.2rem 3rem #0005; }
    blockquote { margin: .8rem 0; font-family: Georgia, serif; font-size: 1.45rem; line-height: 1.35; }
    .author { color: #d1b9ef; font-weight: 750; }
    .meta { margin-top: auto; color: #a99db5; font-size: .78rem; line-height: 1.6; }
    .tags { display: flex; flex-wrap: wrap; gap: .35rem; margin: .8rem 0; }
    .tag { padding: .22rem .5rem; border-radius: 99rem; background: #2a2135; color: #d9c3f5; font-size: .72rem; }
    .actions { display: flex; gap: .5rem; margin-top: 1rem; }
    .actions button, dialog button { border: 0; border-radius: .75rem; padding: .7rem .9rem; font-weight: 800; cursor: pointer; }
    .approve { background: #a6e9bd; color: #102519; }
    .reject { background: #38232a; color: #ffc2cb; }
    .empty { grid-column: 1 / -1; padding: 5rem 1rem; text-align: center; color: #9e91aa; }
    dialog { width: min(36rem, calc(100% - 2rem)); border: 1px solid #51445f; border-radius: 1.4rem; padding: 0; color: #f5efff; background: #17131d; box-shadow: 0 2rem 7rem #000c; }
    dialog::backdrop { background: #050307c7; backdrop-filter: blur(5px); }
    form { display: grid; gap: .9rem; padding: 1.25rem; }
    form h2 { margin: 0; font-family: Georgia, serif; font-size: 1.8rem; }
    label { display: grid; gap: .35rem; color: #c8bbd3; font-size: .78rem; font-weight: 700; }
    input, textarea { width: 100%; border: 1px solid #493c57; border-radius: .7rem; padding: .75rem; color: #fff; background: #0e0b12; resize: vertical; }
    .dialog-actions { display: flex; justify-content: end; gap: .5rem; }
    .cancel { color: #d3c5df; background: transparent; }
    @media (max-width: 36rem) { header { padding-top: 2rem; align-items: start; } .live { margin-top: .5rem; } article { min-height: 0; } }
  </style>
</head>
<body>
  <header>
    <div><div class="eyebrow">Private review desk</div><h1>Quote queue</h1></div>
    <div class="live">Access protected</div>
  </header>
  <main>
    <nav id="tabs"></nav>
    <p id="notice">Loading submissions...</p>
    <section id="list"></section>
  </main>
  <dialog id="editor">
    <form id="form">
      <h2 id="editorTitle">Edit and approve</h2>
      <input id="id" type="hidden">
      <input id="communityId" type="hidden">
      <label>Quote<textarea id="quoteText" rows="5" maxlength="500" required></textarea></label>
      <label>Author<input id="author" maxlength="100"></label>
      <label>Category<input id="category" maxlength="50" required></label>
      <label>Tags, comma-separated<input id="tags"></label>
      <label>Private reviewer note<textarea id="note" rows="2" maxlength="500"></textarea></label>
      <div class="dialog-actions">
        <button class="cancel" type="button" id="cancel">Cancel</button>
        <button class="approve" type="submit">Approve quote</button>
      </div>
    </form>
  </dialog>
  <script>
    const statuses = ['pending', 'approved', 'rejected'];
    let active = 'pending';
    let current = [];
    const list = document.querySelector('#list');
    const notice = document.querySelector('#notice');
    const tabs = document.querySelector('#tabs');
    const editor = document.querySelector('#editor');
    const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));

    async function load(status = active) {
      active = status;
      notice.textContent = 'Loading submissions...';
      list.innerHTML = '';
      try {
        const response = await fetch('/admin/api/submissions?status=' + encodeURIComponent(status));
        const data = await response.json();
        if (!response.ok) throw new Error(data.error || 'Could not load submissions.');
        current = data.submissions;
        renderTabs(data.counts);
        render();
        notice.textContent = current.length + ' ' + status + ' submission' + (current.length === 1 ? '' : 's');
      } catch (error) {
        notice.textContent = error.message;
      }
    }

    function renderTabs(counts) {
      const countMap = Object.fromEntries(counts.map(item => [item.status, item.count]));
      tabs.innerHTML = statuses.map(status => '<button data-status="' + status + '" class="' + (status === active ? 'active' : '') + '">' + status[0].toUpperCase() + status.slice(1) + '<span class="count">' + (countMap[status] || 0) + '</span></button>').join('');
      tabs.querySelectorAll('button').forEach(button => button.onclick = () => load(button.dataset.status));
    }

    function render() {
      if (!current.length) {
        list.innerHTML = '<div class="empty">Nothing here. The queue is clear.</div>';
        return;
      }
      list.innerHTML = current.map(item => {
        const tags = JSON.parse(item.tagsJson || '[]');
        let actions = '';
        if (item.status === 'pending') actions = '<div class="actions"><button class="approve" data-approve="' + item.id + '">Review & approve</button><button class="reject" data-reject="' + item.id + '">Reject</button></div>';
        if (item.status === 'approved' && item.communityQuoteId) actions = '<div class="actions"><button class="approve" data-edit-community="' + item.communityQuoteId + '">Edit</button><button class="reject" data-toggle-community="' + item.communityQuoteId + '" data-active="' + item.communityActive + '">' + (item.communityActive ? 'Unpublish' : 'Republish') + '</button></div>';
        const publication = item.status === 'approved' ? '<br>' + (item.communityActive ? 'Published' : 'Unpublished') + ' · revision ' + item.communityRevision : '';
        return '<article><div class="eyebrow">' + escapeHtml(item.category) + '</div><blockquote>“' + escapeHtml(item.quoteText) + '”</blockquote><div class="author">— ' + escapeHtml(item.author || 'Unknown') + '</div><div class="tags">' + tags.map(tag => '<span class="tag">#' + escapeHtml(tag) + '</span>').join('') + '</div><div class="meta">Submitted ' + new Date(item.submittedAt * 1000).toLocaleString() + (item.appVersion ? '<br>App ' + escapeHtml(item.appVersion) : '') + publication + '</div>' + actions + '</article>';
      }).join('');
      list.querySelectorAll('[data-approve]').forEach(button => button.onclick = () => openEditor(button.dataset.approve));
      list.querySelectorAll('[data-reject]').forEach(button => button.onclick = () => reject(button.dataset.reject));
      list.querySelectorAll('[data-edit-community]').forEach(button => button.onclick = () => openCommunityEditor(button.dataset.editCommunity));
      list.querySelectorAll('[data-toggle-community]').forEach(button => button.onclick = () => toggleCommunity(button.dataset.toggleCommunity, button.dataset.active === '1'));
    }

    function openEditor(id) {
      const item = current.find(value => value.id === id);
      document.querySelector('#id').value = item.id;
      document.querySelector('#communityId').value = '';
      document.querySelector('#editorTitle').textContent = 'Edit and approve';
      document.querySelector('#quoteText').value = item.quoteText;
      document.querySelector('#author').value = item.author;
      document.querySelector('#category').value = item.category;
      document.querySelector('#tags').value = JSON.parse(item.tagsJson || '[]').join(', ');
      document.querySelector('#note').value = '';
      editor.showModal();
    }

    function openCommunityEditor(id) {
      const item = current.find(value => value.communityQuoteId === id);
      document.querySelector('#id').value = item.id;
      document.querySelector('#communityId').value = id;
      document.querySelector('#editorTitle').textContent = 'Edit community quote';
      document.querySelector('#quoteText').value = item.quoteText;
      document.querySelector('#author').value = item.author;
      document.querySelector('#category').value = item.category;
      document.querySelector('#tags').value = JSON.parse(item.tagsJson || '[]').join(', ');
      document.querySelector('#note').value = '';
      editor.showModal();
    }

    async function moderate(id, action, values) {
      const response = await fetch('/admin/api/submissions/' + id, {
        method: 'PATCH', headers: {'Content-Type':'application/json'}, body: JSON.stringify({...values, action})
      });
      const data = await response.json();
      if (!response.ok) throw new Error(data.error || 'Review failed.');
      await load(active);
    }

    async function reject(id) {
      const item = current.find(value => value.id === id);
      if (!confirm('Reject this submission?')) return;
      try {
        await moderate(id, 'reject', {quoteText:item.quoteText, author:item.author, category:item.category, tags:JSON.parse(item.tagsJson || '[]'), note:''});
      } catch (error) { notice.textContent = error.message; }
    }

    async function updateCommunity(id, action, values = {}) {
      const response = await fetch('/admin/api/community/' + id, {
        method: 'PATCH', headers: {'Content-Type':'application/json'}, body: JSON.stringify({...values, action})
      });
      const data = await response.json();
      if (!response.ok) throw new Error(data.error || 'Community update failed.');
      await load(active);
    }

    async function toggleCommunity(id, activeNow) {
      const action = activeNow ? 'unpublish' : 'republish';
      if (!confirm((activeNow ? 'Unpublish' : 'Republish') + ' this community quote?')) return;
      try { await updateCommunity(id, action); }
      catch (error) { notice.textContent = error.message; }
    }

    document.querySelector('#cancel').onclick = () => editor.close();
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
      }
      catch (error) { alert(error.message); }
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
