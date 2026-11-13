/* TicketIQ browser UI: plain JavaScript, no build step. It talks to the REST API under /api. */
(() => {
  'use strict';

  const CATEGORIES = ['ACCESS', 'BILLING', 'BUG', 'HOW_TO', 'INTEGRATION', 'FEATURE_REQUEST', 'OTHER'];
  const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
  const STATUSES = ['NEW', 'TRIAGED', 'NEEDS_HUMAN_TRIAGE', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'];
  const SAMPLES = [
    { label: 'Reset email missing', title: 'Reset email is not arriving',
      description: 'I pressed Forgot password but the reset email never arrives in my inbox, not even in spam. My email is asha.rao@example.com and my number is 9876543210.' },
    { label: 'Charged twice', title: 'Double payment on my subscription',
      description: 'I was charged twice this month for my subscription. Please refund the duplicate payment. Card ending 4111 1111 1111 1111.' },
    { label: 'Platform outage', title: 'Everything is down for everyone',
      description: 'The whole platform is down. Nobody in the company can load any page and it is a complete outage.' },
    { label: 'Dark mode idea', title: 'Add a dark theme',
      description: 'It would be great to have a dark mode in the web app. Nice to have, no rush.' }
  ];

  const state = { user: null, tokens: null, meta: null, view: 'tickets', ticketId: null,
                  filters: { q: '', status: '', category: '', priority: '' }, page: 0, poll: null };

  // ------------------------------------------------------------------ helpers
  const $app = document.getElementById('app');
  const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const label = (s) => String(s ?? '').replace(/_/g, ' ').toLowerCase().replace(/^./, (c) => c.toUpperCase());
  const chip = (value, extra = '') => `<span class="chip ${esc(value)} ${extra}">${esc(label(value))}</span>`;
  const when = (iso) => iso ? new Date(iso).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' }) : '';
  const isStaff = () => state.user && (state.user.role === 'AGENT' || state.user.role === 'ADMIN');
  const options = (list, selected, any) => (any ? `<option value="">${esc(any)}</option>` : '') +
    list.map((v) => `<option value="${v}" ${v === selected ? 'selected' : ''}>${esc(label(v))}</option>`).join('');
  const pageIntro = (title, description) => `<div class="page-intro"><h1>${esc(title)}</h1><p>${esc(description)}</p></div>`;
  const teamFor = (category) => (state.meta && state.meta.teams && state.meta.teams[category]) || 'General Support';
  const roleAccess = [
    ['USER', 'Raise incidents and follow only your own active tickets. Other users’ incidents and completed history are private.'],
    ['AGENT', 'Work the ticket queue, review incident history and AI analysis, assign tickets, triage, communicate, and resolve. Agents cannot manage users, edit the knowledge base, or control AI settings.'],
    ['ADMIN', 'All agent abilities, plus user and role administration, knowledge-base management, AI controls, and system health/usage information.']
  ];

  const store = {
    get(k) { try { return JSON.parse(sessionStorage.getItem(k)); } catch (e) { return null; } },
    set(k, v) { try { sessionStorage.setItem(k, JSON.stringify(v)); } catch (e) { /* private mode: ignore */ } },
    del(k) { try { sessionStorage.removeItem(k); } catch (e) { /* ignore */ } }
  };

  let toastTimer;
  function toast(message, isError = false) {
    const el = document.getElementById('toast');
    el.textContent = message;
    el.className = 'show' + (isError ? ' error' : '');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { el.className = ''; }, 3200);
  }

  async function api(path, { method = 'GET', body, auth = true, retry = true } = {}) {
    const headers = { 'Content-Type': 'application/json' };
    if (auth && state.tokens) headers.Authorization = 'Bearer ' + state.tokens.accessToken;
    const res = await fetch('/api' + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    if (res.status === 401 && auth && retry && state.tokens && await refreshTokens()) {
      return api(path, { method, body, auth, retry: false });
    }
    if (res.status === 401 && auth && !path.startsWith('/auth/login')) { logout(); throw new Error('Please sign in again'); }
    const text = await res.text();
    const data = text ? JSON.parse(text) : null;
    if (!res.ok) {
      const fields = data && data.fieldErrors ? ' ' + Object.entries(data.fieldErrors).map(([k, v]) => `${k}: ${v}`).join('; ') : '';
      throw new Error(((data && data.message) || 'Request failed') + fields);
    }
    return data;
  }

  async function refreshTokens() {
    try {
      const res = await fetch('/api/auth/refresh', { method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: state.tokens.refreshToken }) });
      if (!res.ok) return false;
      saveSession(await res.json());
      return true;
    } catch (e) { return false; }
  }

  function saveSession(login) {
    state.tokens = { accessToken: login.accessToken, refreshToken: login.refreshToken };
    state.user = login.user;
    store.set('session', { tokens: state.tokens, user: state.user });
  }

  function logout() {
    stopPolling();
    state.tokens = null; state.user = null; state.ticketId = null; state.view = 'tickets';
    state.filters = { q: '', status: '', category: '', priority: '' }; state.page = 0;   // never leak one person's filters to the next
    store.del('session');
    renderLogin();
  }

  function stopPolling() { if (state.poll) { clearInterval(state.poll); state.poll = null; } }

  // -------------------------------------------------------------------- login
  async function renderLogin(mode = 'login', prefillUserId = '') {
    try { state.meta = await api('/meta', { auth: false }); } catch (e) { state.meta = null; }
    const m = state.meta;
    const demo = [['Admin', 'admin@ticketiq.local', 'Admin@123'], ['Agent', 'agent@ticketiq.local', 'Agent@123'], ['User', 'user@ticketiq.local', 'User@123']];
    const signingUp = mode === 'signup';
    $app.innerHTML = `
      <div class="login-wrap"><section class="login stack">
        <div class="brand"><img src="favicon.svg" alt="">TicketIQ</div>
        <p class="muted">Create an account to raise and follow incidents. AI helps route issues; people remain responsible for decisions.</p>
        <div class="login-guide">
          <h2>How the portal works</h2>
          <ol><li>Sign in or create a profile using your employee ID.</li>
            <li>Describe the issue with a title, steps, and business impact.</li>
            <li>AI compares it with knowledge articles and resolved incidents, then suggests a category and team.</li>
            <li>Support staff review the recommendation and update your ticket.</li></ol>
          <p class="small muted">Your profile details identify you in the portal. Personal data in ticket text is redacted before AI processing.</p>
        </div>
        <div class="login-guide role-guide">
          <h2>Roles and access</h2>
          <p class="small muted">Access increases from USER to AGENT to ADMIN; each higher role includes the support work below it.</p>
          ${roleAccess.map(([role, description]) => `<p><b>${role}</b> — ${esc(description)}</p>`).join('')}
        </div>
        ${m ? `<div class="callout info small">AI provider: <b>${esc(m.aiProvider)}</b> (${esc(m.aiModel)})${m.aiProvider === 'mock' ? ' &middot; offline demo AI, no API key needed' : ''}</div>` : ''}
        ${signingUp ? `
          <form class="stack auth-form" id="signup-form" novalidate>
            <h2>Create your profile</h2>
            <div class="field"><label for="employee-id">Employee ID number</label><input id="employee-id" inputmode="numeric" autocomplete="off" maxlength="30" required></div>
            <div class="field"><label for="full-name">Full name</label><input id="full-name" autocomplete="name" maxlength="100" required></div>
            <div class="field"><label for="signup-user-id">User ID</label><input id="signup-user-id" autocomplete="username" minlength="3" maxlength="50" required></div>
            <div class="field"><label for="signup-password">Password</label><input id="signup-password" type="password" autocomplete="new-password" minlength="8" maxlength="72" required>
              <ul class="password-rules" id="password-rules">
                <li data-rule="length">8 to 72 printable characters</li><li data-rule="lower">A lowercase letter</li>
                <li data-rule="upper">An uppercase letter</li><li data-rule="number">A number</li><li data-rule="symbol">A symbol</li>
              </ul></div>
            <div class="field-error" id="signup-error" role="alert"></div>
            <button class="btn primary" type="submit" style="width:100%">Create account</button>
          </form>
          <p class="muted small">Already have an account? <button class="text-button" type="button" id="switch-auth">Sign in</button></p>`
          : `
          <form class="stack auth-form" id="login-form" novalidate>
            <h2>Sign in</h2>
            <div class="field"><label for="user-id">User ID</label><input id="user-id" autocomplete="username" required></div>
            <div class="field"><label for="password">Password</label><input id="password" type="password" autocomplete="current-password" required></div>
            <div class="field-error" id="login-error" role="alert"></div>
            <button class="btn primary" type="submit" style="width:100%">Sign in</button>
          </form>
          <div class="muted small">Demo accounts (click to fill):</div>
          <div class="demo-users">${demo.map((d, i) => `<button type="button" class="btn small" data-demo="${i}">${d[0]}</button>`).join('')}</div>
          <p class="muted small">New to TicketIQ? <button class="text-button" type="button" id="switch-auth">Create an account</button></p>`}
      </section></div>`;
    document.getElementById('switch-auth').addEventListener('click', () => renderLogin(signingUp ? 'login' : 'signup'));
    if (signingUp) {
      const form = document.getElementById('signup-form');
      const password = document.getElementById('signup-password');
      password.addEventListener('input', () => {
        const value = password.value;
        const rules = { length: value.length >= 8 && value.length <= 72 && /^[\x21-\x7E]*$/.test(value),
          lower: /[a-z]/.test(value), upper: /[A-Z]/.test(value), number: /[0-9]/.test(value),
          symbol: /[^A-Za-z0-9\s]/.test(value) };
        Object.entries(rules).forEach(([rule, passed]) => {
          document.querySelector(`[data-rule="${rule}"]`).classList.toggle('met', passed);
        });
      });
      form.addEventListener('submit', async (ev) => {
        ev.preventDefault();
        const err = document.getElementById('signup-error'); err.textContent = '';
        try {
          const profile = await api('/auth/signup', { method: 'POST', auth: false, body: {
            employeeId: document.getElementById('employee-id').value.trim(),
            fullName: document.getElementById('full-name').value.trim(),
            userId: document.getElementById('signup-user-id').value.trim(),
            password: password.value
          } });
          toast('Profile created. Sign in with your new user ID.');
          renderLogin('login', profile.userId);
        } catch (e) { err.textContent = e.message; }
      });
    } else {
      const form = document.getElementById('login-form');
      document.getElementById('user-id').value = prefillUserId;
      document.querySelectorAll('[data-demo]').forEach((b) => b.addEventListener('click', () => {
        const d = demo[Number(b.dataset.demo)];
        document.getElementById('user-id').value = d[1]; document.getElementById('password').value = d[2];
      }));
      form.addEventListener('submit', async (ev) => {
        ev.preventDefault();
        const err = document.getElementById('login-error'); err.textContent = '';
        try {
          const login = await api('/auth/login', { method: 'POST', auth: false,
            body: { userId: document.getElementById('user-id').value.trim(), password: document.getElementById('password').value } });
          saveSession(login);
          state.view = 'tickets';
          renderShell();
        } catch (e) { err.textContent = e.message; }
      });
    }
  }

  // -------------------------------------------------------------------- shell
  const NAV = [
    ['tickets', isStaff() ? 'Ticket queue' : 'Active incidents', false], ['new', 'New ticket', false], ['kb', 'Knowledge base', true],
    ['insights', 'Insights', true], ['lab', 'AI Lab', true], ['profile', 'My profile', false]
  ];

  async function renderShell() {
    stopPolling();
    try { state.meta = await api('/meta', { auth: false }); } catch (e) { /* banner is optional */ }
    const m = state.meta || {};
    $app.innerHTML = `
      <div class="shell">
        <header class="topbar">
          <div class="brand"><img src="favicon.svg" alt="">TicketIQ</div>
          <span class="chip ai" title="Active AI provider">AI: ${esc(m.aiProvider || '?')}${m.aiEnabled === false ? ' (off)' : ''}</span>
          <span class="right muted small">${esc(state.user.fullName)} &middot; ${chip(state.user.role)}</span>
          <button class="btn small" id="logout">Sign out</button>
        </header>
        <nav class="side" aria-label="Main">
          ${NAV.filter((n) => !n[2] || isStaff()).map((n) => `<button data-view="${n[0]}" class="${state.view === n[0] ? 'active' : ''}">${n[1]}</button>`).join('')}
        </nav>
        <main id="main"></main>
      </div>`;
    document.getElementById('logout').addEventListener('click', logout);
    document.querySelectorAll('[data-view]').forEach((b) => b.addEventListener('click', () => go(b.dataset.view)));
    renderView();
  }

  function go(view, ticketId = null) {
    stopPolling();
    state.view = view; state.ticketId = ticketId;
    document.querySelectorAll('[data-view]').forEach((b) => b.classList.toggle('active', b.dataset.view === (view === 'ticket' ? 'tickets' : view)));
    renderView();
  }

  function renderView() {
    const main = document.getElementById('main');
    main.innerHTML = '<div class="skeleton" style="width:40%"></div><div class="skeleton"></div><div class="skeleton" style="width:70%"></div>';
    const views = { tickets: viewTickets, ticket: viewTicket, new: viewNew, kb: viewKb, insights: viewInsights, lab: viewLab, profile: viewProfile };
    (views[state.view] || viewTickets)(main).catch((e) => { main.innerHTML = `<div class="card"><h2>Something went wrong</h2><p class="muted">${esc(e.message)}</p></div>`; });
  }

  // ------------------------------------------------------------------ tickets
  async function viewTickets(main) {
    const f = state.filters;
    const params = new URLSearchParams({ page: state.page, size: 15 });
    Object.entries(f).forEach(([k, v]) => { if (v) params.set(k, v); });
    const data = await api('/tickets?' + params);
    main.innerHTML = `
      ${pageIntro(isStaff() ? 'Ticket queue' : 'My active incidents', isStaff()
        ? (state.user.role === 'ADMIN'
          ? 'Review current and resolved incidents, inspect AI analysis, assign and resolve tickets, manage support resources, and administer AI settings.'
          : 'Review and assign incoming incidents, check AI analysis and past resolved cases, update status, and communicate with requesters. User administration, knowledge-base editing, and AI controls are reserved for admins.')
        : 'Track only your own active incidents. Completed incidents and other users’ ticket history are not available to USER accounts. To report another issue, choose New ticket.')}
      <div class="row" style="margin-bottom:1rem">
        <button class="btn primary right" id="new-ticket">New ticket</button></div>
      <div class="filters">
        <input id="f-q" placeholder="Search titles" value="${esc(f.q)}" aria-label="Search titles">
        ${isStaff() ? `<select id="f-status" aria-label="Status">${options(STATUSES, f.status, 'Any status')}</select>` : ''}
        <select id="f-category" aria-label="Category">${options(CATEGORIES, f.category, 'Any category')}</select>
        <select id="f-priority" aria-label="Priority">${options(PRIORITIES, f.priority, 'Any priority')}</select>
      </div>
      <div class="card table-wrap">${data.items.length === 0 ? '<div class="empty">No tickets match. Try creating one.</div>' : `
        <table><thead><tr><th>#</th><th>Title</th><th>Status</th><th>Category</th><th>Suggested team</th><th>Priority</th>${isStaff() ? '<th>AI</th>' : ''}<th>Created</th></tr></thead><tbody>
        ${data.items.map((t) => `<tr data-id="${t.id}" tabindex="0">
          <td class="muted">${t.id}</td><td><b>${esc(t.title)}</b>${t.aiSummary && isStaff() ? `<div class="small muted">${esc(t.aiSummary)}</div>` : ''}</td>
          <td>${chip(t.status)}</td><td>${esc(label(t.category))}</td><td>${esc(t.suggestedTeam || teamFor(t.category))}</td><td>${chip(t.priority)}</td>
          ${isStaff() ? `<td>${t.triageSource === 'AI' ? `<span class="chip ai">AI ${t.aiConfidence != null ? Math.round(t.aiConfidence * 100) + '%' : ''}</span>` : t.triageSource === 'MANUAL' ? '<span class="chip MANUAL">Manual</span>' : '<span class="muted small">waiting</span>'}</td>` : ''}
          <td class="small muted">${when(t.createdAt)}</td></tr>`).join('')}
        </tbody></table>`}</div>
      <div class="row" style="margin-top:1rem">
        <span class="muted small">${data.totalItems} ticket(s), page ${data.page + 1} of ${Math.max(data.totalPages, 1)}</span>
        <button class="btn small right" id="prev" ${data.page === 0 ? 'disabled' : ''}>Previous</button>
        <button class="btn small" id="next" ${data.page + 1 >= data.totalPages ? 'disabled' : ''}>Next</button></div>`;
    document.getElementById('new-ticket').addEventListener('click', () => go('new'));
    main.querySelectorAll('tbody tr').forEach((tr) => {
      const open = () => go('ticket', Number(tr.dataset.id));
      tr.addEventListener('click', open);
      tr.addEventListener('keydown', (e) => { if (e.key === 'Enter') open(); });
    });
    let debounce;
    document.getElementById('f-q').addEventListener('input', (e) => {
      clearTimeout(debounce);
      debounce = setTimeout(() => { state.filters.q = e.target.value; state.page = 0; renderView(); }, 350);
    });
    [['f-status', 'status'], ['f-category', 'category'], ['f-priority', 'priority']]
      .filter(([id]) => document.getElementById(id))
      .forEach(([id, key]) =>
      document.getElementById(id).addEventListener('change', (e) => { state.filters[key] = e.target.value; state.page = 0; renderView(); }));
    document.getElementById('prev').addEventListener('click', () => { state.page--; renderView(); });
    document.getElementById('next').addEventListener('click', () => { state.page++; renderView(); });
  }

  // --------------------------------------------------------------- new ticket
  async function viewNew(main) {
    main.innerHTML = `
      ${pageIntro('Raise an incident', 'Explain what happened, when it started, and who or what is affected. TicketIQ uses the title and description to compare your issue with the knowledge base and past resolved incidents, then recommends a priority, category, and support team. A support person reviews the recommendation.')}
      <div class="card route-guide"><h2>Suggested support teams</h2><p class="muted small">The team is recommended from the AI category and can be changed by support staff.</p>
        <div class="team-grid">${Object.entries((state.meta && state.meta.teams) || {}).map(([category, team]) =>
          `<div><b>${esc(label(category))}</b><span>${esc(team)}</span></div>`).join('')}</div></div>
      <form class="card stack" id="ticket-form" style="max-width:720px" novalidate>
        <div class="field"><label for="t-title">Title</label><input id="t-title" maxlength="200" required></div>
        <div class="field"><label for="t-desc">What is happening?</label><textarea id="t-desc" rows="6" maxlength="4000" required></textarea>
          <div class="muted small">Add error messages, steps to reproduce, and business impact. Personal data such as emails, phone and card numbers is removed before the AI sees ticket text.</div></div>
        <div class="field-error" id="t-error" role="alert"></div>
        <div class="row"><button class="btn primary" type="submit">Submit ticket</button></div>
        <div class="muted small">Try a sample:</div>
        <div class="row">${SAMPLES.map((s, i) => `<button type="button" class="btn small" data-sample="${i}">${esc(s.label)}</button>`).join('')}</div>
      </form>`;
    main.querySelectorAll('[data-sample]').forEach((b) => b.addEventListener('click', () => {
      const s = SAMPLES[Number(b.dataset.sample)];
      document.getElementById('t-title').value = s.title; document.getElementById('t-desc').value = s.description;
    }));
    document.getElementById('ticket-form').addEventListener('submit', async (ev) => {
      ev.preventDefault();
      const err = document.getElementById('t-error'); err.textContent = '';
      try {
        const t = await api('/tickets', { method: 'POST', body: { title: document.getElementById('t-title').value, description: document.getElementById('t-desc').value } });
        toast('Ticket #' + t.id + ' created. The AI is looking at it now.');
        go('ticket', t.id);
      } catch (e) { err.textContent = e.message; }
    });
  }

  // ------------------------------------------------------------ ticket detail
  async function viewTicket(main) {
    const id = state.ticketId;
    const [t, comments] = await Promise.all([api('/tickets/' + id), api('/tickets/' + id + '/comments')]);
    let reply = null;
    if (isStaff() && t.ai && t.ai.hasSuggestedReply) reply = await api('/tickets/' + id + '/suggested-reply');
    const waiting = t.status === 'NEW';
    main.innerHTML = `
      ${pageIntro('Ticket details', isStaff()
        ? 'Review the incident, AI analysis, supporting past cases, and reply sources. Confirm the suggested team and status; AI output is advisory and you make the final decision.'
        : 'Follow progress and communicate with the support team here. The suggested category and team are based on the incident description and past cases.')}
      <div class="row" style="margin-bottom:.8rem"><button class="btn small" id="back">&larr; Tickets</button>
        <span class="muted">#${t.id}</span>${chip(t.status)}${chip(t.priority)}<span class="chip">${esc(label(t.category))}</span>
        <span class="chip team-chip">${esc(t.suggestedTeam || teamFor(t.category))}</span>
        ${t.triageSource === 'AI' ? '<span class="chip ai">Triaged by AI</span>' : t.triageSource === 'MANUAL' ? '<span class="chip MANUAL">Triaged by an agent</span>' : ''}</div>
      <div class="grid-2"><div>
        <div class="card"><h1>${esc(t.title)}</h1>
          <p class="muted small">Raised by ${esc(t.requesterName)} &middot; ${when(t.createdAt)}${t.assigneeName ? ' &middot; Assigned to ' + esc(t.assigneeName) : ''}</p>
          <p style="white-space:pre-wrap">${esc(t.description)}</p>
          ${t.resolution ? `<div class="callout info"><b>Resolution:</b> ${esc(t.resolution)}</div>` : ''}</div>
        <div class="card"><h2>Conversation</h2>
          ${comments.length === 0 ? '<p class="muted">No replies yet.</p>' : comments.map((c) => `
            <div class="comment ${c.internal ? 'internal' : ''} ${c.authorRole !== 'USER' ? 'staff' : ''}">
              <div class="small"><b>${esc(c.authorName)}</b> ${chip(c.authorRole)} ${c.internal ? '<span class="chip">internal note</span>' : ''} <span class="muted">${when(c.createdAt)}</span></div>
              <div style="white-space:pre-wrap">${esc(c.body)}</div></div>`).join('')}
          <form id="comment-form" class="stack" style="margin-top:1rem">
            <textarea id="c-body" rows="4" placeholder="Write a reply" aria-label="Reply"></textarea>
            <div class="row">${isStaff() ? '<label style="margin:0;font-weight:500"><input type="checkbox" id="c-internal" style="width:auto"> Internal note</label>' : ''}
              <button class="btn primary right" type="submit">Send</button></div></form></div>
      </div><div>
        ${isStaff() ? aiPanel(t, reply, waiting) : (waiting ? '<div class="card"><div class="callout info">Your ticket is received and being reviewed.</div></div>' : '')}
        ${isStaff() ? staffControls(t) : ''}
      </div></div>`;
    document.getElementById('back').addEventListener('click', () => go('tickets'));
    document.getElementById('comment-form').addEventListener('submit', async (ev) => {
      ev.preventDefault();
      const body = document.getElementById('c-body').value.trim(); if (!body) return;
      try {
        await api('/tickets/' + id + '/comments', { method: 'POST', body: { body, internal: !!(document.getElementById('c-internal') || {}).checked } });
        renderView();
      } catch (e) { toast(e.message, true); }
    });
    if (isStaff()) bindStaff(t, reply);
    if (waiting) startPolling(id);
  }

  function aiPanel(t, reply, waiting) {
    if (!t.ai) {
      return `<div class="card ai-panel"><h2>AI assistant</h2>
        ${waiting ? '<p class="muted">Waiting for the AI&hellip; this page updates by itself.</p><div class="skeleton"></div><div class="skeleton" style="width:60%"></div>'
                  : '<p class="muted">The AI has not produced suggestions for this ticket.</p>'}
        <button class="btn ai small" id="retriage">Run AI triage</button></div>`;
    }
    const a = t.ai; const pct = Math.round((a.confidence || 0) * 100);
    return `<div class="card ai-panel stack"><h2>AI assistant <span class="chip ai">${esc(a.provider || '')}</span></h2>
      <div><div class="small muted">Summary</div><div>${esc(a.summary)}</div></div>
      <div><div class="row small"><span class="muted">Confidence</span><b>${pct}%</b></div>
        <div class="meter ${pct < 60 ? 'low' : ''}" role="progressbar" aria-valuenow="${pct}" aria-valuemin="0" aria-valuemax="100"><span style="width:${pct}%"></span></div></div>
      ${a.rationale ? `<div class="small muted">${esc(a.rationale)}</div>` : ''}
      ${a.needsHuman ? `<div class="callout warn"><b>Needs a human:</b> ${esc(a.reason || 'please check this ticket')}</div>` : ''}
      <div><h3>Similar past tickets</h3>${a.similar.length === 0 ? '<div class="muted small">No close matches.</div>' :
        a.similar.map((s) => `<div class="sim"><span>#${s.ticketId} ${esc(s.title)}</span><b>${Math.round(s.score * 100)}%</b></div>`).join('')}</div>
      <div><h3>Suggested reply</h3>${reply && reply.available ? `
        <div class="draft">${esc(reply.text)}</div>
        <div class="small muted" style="margin:.4rem 0">Sources: ${reply.sources.map((s) => `${esc(s.type)}:${esc(s.id)} ${esc(s.title)}`).join(' &middot; ')}</div>
        <div class="row"><button class="btn ai small" id="use-draft">Use as reply</button><button class="btn small" id="dismiss-draft">Not helpful</button></div>`
        : '<div class="muted small">No relevant knowledge found, so no draft was made.</div>'}</div>
      <button class="btn small" id="retriage">Run AI triage again</button>
      <div class="muted small">The AI only suggests. You decide.</div></div>`;
  }

  function staffControls(t) {
    return `<div class="card stack"><h2>Handle this ticket</h2>
      <div class="row"><div style="flex:1"><label for="tr-cat">Category</label><select id="tr-cat">${options(CATEGORIES, t.category)}</select></div>
        <div style="flex:1"><label for="tr-pri">Priority</label><select id="tr-pri">${options(PRIORITIES, t.priority)}</select></div></div>
      <button class="btn small" id="save-triage">Save triage (manual)</button>
      <div class="row"><button class="btn small" id="assign-me">Assign to me</button></div>
      <div><label for="st-status">Status</label><select id="st-status">${options(STATUSES, t.status)}</select></div>
      <div id="res-wrap" style="display:none"><label for="st-res">Resolution (required)</label><textarea id="st-res" rows="3"></textarea></div>
      <button class="btn primary small" id="save-status">Update status</button></div>`;
  }

  function bindStaff(t, reply) {
    const id = t.id;
    const on = (sel, fn) => { const el = document.getElementById(sel); if (el) el.addEventListener('click', fn); };
    const run = async (fn, okMsg) => { try { await fn(); if (okMsg) toast(okMsg); renderView(); } catch (e) { toast(e.message, true); } };
    on('retriage', () => run(async () => { await api('/tickets/' + id + '/retriage', { method: 'POST' }); }, 'Asked the AI to look again'));
    on('save-triage', () => run(() => api('/tickets/' + id + '/triage', { method: 'PATCH', body: { category: document.getElementById('tr-cat').value, priority: document.getElementById('tr-pri').value } }), 'Triage saved. Your choice overrides the AI.'));
    on('assign-me', () => run(() => api('/tickets/' + id + '/assign', { method: 'PATCH', body: { assigneeId: state.user.id } }), 'Assigned to you'));
    const statusSel = document.getElementById('st-status');
    if (statusSel) {
      const sync = () => { document.getElementById('res-wrap').style.display = statusSel.value === 'RESOLVED' ? 'block' : 'none'; };
      statusSel.addEventListener('change', sync); sync();
    }
    on('save-status', () => run(() => api('/tickets/' + id + '/status', { method: 'PATCH', body: { status: statusSel.value, resolution: document.getElementById('st-res').value } }), 'Status updated'));
    on('use-draft', () => { document.getElementById('c-body').value = reply.text; document.getElementById('c-body').focus();
      api('/tickets/' + id + '/ai-feedback', { method: 'POST', body: { accepted: true, edited: false } }).catch(() => {}); toast('Draft copied to the reply box. Edit it, then send.'); });
    on('dismiss-draft', () => run(() => api('/tickets/' + id + '/ai-feedback', { method: 'POST', body: { accepted: false, edited: false } }), 'Thanks, noted'));
  }

  function startPolling(id) {
    let n = 0;
    state.poll = setInterval(async () => {
      if (state.view !== 'ticket' || state.ticketId !== id || ++n > 20) return stopPolling();
      try { const t = await api('/tickets/' + id); if (t.status !== 'NEW') { stopPolling(); renderView(); } } catch (e) { stopPolling(); }
    }, 1500);
  }

  // ----------------------------------------------------------- knowledge base
  async function viewKb(main) {
    const items = await api('/kb');
    const admin = state.user.role === 'ADMIN';
    main.innerHTML = `${pageIntro('Knowledge base', 'These support articles provide trusted guidance for AI-generated reply drafts. TicketIQ retrieves relevant passages alongside similar resolved incidents; admins can add or update the material used for those suggestions.')}
      ${admin ? `<form class="card stack" id="kb-form" style="max-width:760px"><h2 id="kb-heading">Add an article</h2><input type="hidden" id="kb-id">
        <div class="field"><label for="kb-title">Title</label><input id="kb-title" maxlength="200"></div>
        <div class="field"><label for="kb-content">Content</label><textarea id="kb-content" rows="4" maxlength="4000"></textarea></div>
        <div class="field"><label for="kb-tags">Tags</label><input id="kb-tags" maxlength="200" placeholder="access,password"></div>
        <div class="field-error" id="kb-error" role="alert"></div>
        <div class="row"><button class="btn primary" type="submit">Save article</button><button class="btn" type="button" id="kb-clear">Clear</button></div></form>` : ''}
      <div style="margin-top:1rem">${items.map((a) => `<div class="card"><div class="row"><h2>${esc(a.title)}</h2><span class="muted small right">kb:${a.id} &middot; ${esc(a.tags || '')}</span></div>
        <p style="white-space:pre-wrap;margin:.3rem 0">${esc(a.content)}</p>
        ${admin ? `<div class="row"><button class="btn small" data-edit="${a.id}">Edit</button><button class="btn small danger" data-del="${a.id}">Delete</button></div>` : ''}</div>`).join('')}</div>`;
    if (!admin) return;
    const form = document.getElementById('kb-form');
    const clear = () => { ['kb-id', 'kb-title', 'kb-content', 'kb-tags'].forEach((i) => { document.getElementById(i).value = ''; }); document.getElementById('kb-heading').textContent = 'Add an article'; };
    document.getElementById('kb-clear').addEventListener('click', clear);
    main.querySelectorAll('[data-edit]').forEach((b) => b.addEventListener('click', () => {
      const a = items.find((x) => String(x.id) === b.dataset.edit);
      document.getElementById('kb-id').value = a.id; document.getElementById('kb-title').value = a.title;
      document.getElementById('kb-content').value = a.content; document.getElementById('kb-tags').value = a.tags || '';
      document.getElementById('kb-heading').textContent = 'Edit article ' + a.id; window.scrollTo({ top: 0, behavior: 'smooth' });
    }));
    main.querySelectorAll('[data-del]').forEach((b) => b.addEventListener('click', async () => {
      if (!confirm('Delete this article?')) return;
      try { await api('/kb/' + b.dataset.del, { method: 'DELETE' }); toast('Article deleted'); renderView(); } catch (e) { toast(e.message, true); }
    }));
    form.addEventListener('submit', async (ev) => {
      ev.preventDefault();
      const id = document.getElementById('kb-id').value;
      const body = { title: document.getElementById('kb-title').value, content: document.getElementById('kb-content').value, tags: document.getElementById('kb-tags').value };
      try { await api(id ? '/kb/' + id : '/kb', { method: id ? 'PUT' : 'POST', body }); toast('Article saved and sent to the AI index'); renderView(); }
      catch (e) { document.getElementById('kb-error').textContent = e.message; }
    });
  }

  // ----------------------------------------------------------------- insights
  function bars(map, total) {
    const entries = Object.entries(map || {}).sort((a, b) => b[1] - a[1]);
    if (!entries.length) return '<div class="muted small">No data yet.</div>';
    return entries.map(([k, v]) => `<div class="bar-row"><span>${esc(label(k))}</span><div class="bar"><span style="width:${total ? Math.round(100 * v / total) : 0}%"></span></div><b>${v}</b></div>`).join('');
  }

  async function viewInsights(main) {
    const s = await api('/stats');
    const ai = s.ai;
    const pct = (v) => v == null ? '–' : Math.round(v * 100) + '%';
    main.innerHTML = `${pageIntro('Insights', 'Understand ticket volume and workflow outcomes. AI confidence and agent feedback are signals for review, not a replacement for human judgment.')}
      <div class="kpis">
        <div class="kpi"><span class="muted small">Total tickets</span><b>${s.totalTickets}</b></div>
        <div class="kpi"><span class="muted small">Triaged by AI</span><b>${ai.triagedByAi}</b></div>
        <div class="kpi"><span class="muted small">Triaged by agents</span><b>${ai.triagedByHuman}</b></div>
        <div class="kpi"><span class="muted small">Average AI confidence</span><b>${pct(ai.averageConfidence)}</b></div>
        <div class="kpi"><span class="muted small">Drafts accepted</span><b>${pct(ai.acceptanceRate)}</b><span class="muted small">${ai.feedbackAccepted} yes, ${ai.feedbackRejected} no</span></div>
      </div>
      <div class="grid-2"><div class="card"><h2>By status</h2>${bars(s.byStatus, s.totalTickets)}</div>
        <div class="card"><h2>By priority</h2>${bars(s.byPriority, s.totalTickets)}</div></div>
      <div class="card"><h2>By category</h2>${bars(s.byCategory, s.totalTickets)}</div>`;
  }

  // -------------------------------------------------------------------- AI lab
  async function viewLab(main) {
    const admin = state.user.role === 'ADMIN';
    const stats = admin ? await api('/ai/stats') : null;
    main.innerHTML = `${pageIntro('AI Lab', 'Explore how the configured AI analyzes an example without creating a ticket. Admins can also check model health and temporarily switch AI off; tickets remain usable and wait for human review.')}
      <div class="grid-2"><form class="card stack" id="lab-form"><h2>Try the pipeline (nothing is saved)</h2>
        <div class="field"><label for="l-title">Title</label><input id="l-title" value="Reset email is not arriving"></div>
        <div class="field"><label for="l-desc">Description</label><textarea id="l-desc" rows="5">I pressed Forgot password but the reset email never arrives. Contact me at asha.rao@example.com or 9876543210.</textarea></div>
        <button class="btn ai" type="submit">Run AI pipeline</button><div id="lab-result"></div></form>
      <div>${admin ? `<div class="card stack"><h2>AI status</h2>
        <div class="row small"><span class="chip ai">${esc(stats.provider)}</span><span class="muted">${esc(stats.chatModel)} &middot; embeddings ${esc(stats.embeddingModel)}</span></div>
        <div class="row small"><span>Circuit breaker: <b>${esc(stats.circuit)}</b></span><span>Indexed documents: <b>${stats.indexedDocuments}</b></span></div>
        <div class="row small"><span>Model calls: <b>${stats.metrics.modelCalls}</b></span><span>Failures: <b>${stats.metrics.modelFailures}</b></span><span>Avg latency: <b>${stats.metrics.averageLatencyMs} ms</b></span><span>Cache hits: <b>${stats.metrics.cacheHits}</b></span></div>
        <label class="row" style="font-weight:500"><input type="checkbox" id="ai-switch" style="width:auto" ${stats.enabled ? 'checked' : ''}> AI enabled
          <span class="muted small">Switch off, create a ticket, switch back on: the ticket still gets triaged.</span></label></div>
        <div class="card table-wrap"><h2>Recent model calls</h2><table><thead><tr><th>Kind</th><th>Ticket</th><th>ms</th><th>OK</th></tr></thead><tbody>
          ${stats.recentCalls.map((c) => `<tr class="no-hover"><td>${esc(c.kind)}</td><td>${c.ticketId ?? '–'}</td><td>${c.latencyMs}</td><td>${c.success ? 'yes' : esc(c.error || 'no')}</td></tr>`).join('') || '<tr class="no-hover"><td colspan="4" class="muted">None yet</td></tr>'}</tbody></table></div>`
        : '<div class="card"><p class="muted">Admins also see AI health and the audit log here.</p></div>'}</div></div>`;
    document.getElementById('lab-form').addEventListener('submit', async (ev) => {
      ev.preventDefault();
      const out = document.getElementById('lab-result'); out.innerHTML = '<div class="skeleton"></div>';
      try {
        const r = await api('/ai/dry-run/triage', { method: 'POST', body: { title: document.getElementById('l-title').value, description: document.getElementById('l-desc').value } });
        out.innerHTML = `<div class="stack"><div class="row">${chip(r.category)}<span class="chip team-chip">${esc(teamFor(r.category))}</span>${chip(r.priority)}<span class="chip ai">confidence ${Math.round(r.confidence * 100)}%</span>${r.needsHuman ? '<span class="chip HIGH">needs human</span>' : ''}</div>
          <div><b>Summary:</b> ${esc(r.summary)}</div><div class="small muted">${esc(r.rationale || '')}</div>
          <div class="small">Personal data items removed before the model: <b>${r.redactions}</b> &middot; ${r.latencyMs} ms${r.cached ? ' (cached)' : ''}</div>
          <div><h3>Similar tickets</h3>${(r.similar || []).map((s) => `<div class="sim"><span>#${s.ticketId} ${esc(s.title)}</span><b>${Math.round(s.score * 100)}%</b></div>`).join('') || '<span class="muted small">none</span>'}</div>
          <div><h3>Suggested reply</h3>${r.suggestedReply ? `<div class="draft">${esc(r.suggestedReply)}</div>` : '<span class="muted small">none</span>'}</div></div>`;
      } catch (e) { out.innerHTML = `<div class="field-error">${esc(e.message)}</div>`; }
    });
    const sw = document.getElementById('ai-switch');
    if (sw) sw.addEventListener('change', async () => {
      try { await api('/ai/switch', { method: 'PUT', body: { enabled: sw.checked } }); toast('AI ' + (sw.checked ? 'enabled' : 'switched off')); renderShell(); } catch (e) { toast(e.message, true); }
    });
  }

  // -------------------------------------------------------------------- profile
  async function viewProfile(main) {
    const profile = await api('/auth/me');
    main.innerHTML = `${pageIntro('My profile', 'Your profile identifies you when you raise or follow a ticket. The employee ID and user ID are stored with your account; your password is never returned by the profile API.')}
      <div class="card profile-card"><dl>
        <div><dt>Full name</dt><dd>${esc(profile.fullName)}</dd></div>
        <div><dt>Employee ID</dt><dd>${esc(profile.employeeId || 'Not set for this demo account')}</dd></div>
        <div><dt>User ID</dt><dd>${esc(profile.userId)}</dd></div>
        <div><dt>Portal role</dt><dd>${esc(label(profile.role))}</dd></div>
      </dl></div>
      <div class="card role-access-card"><h2>Role hierarchy and permissions</h2>
        <p class="muted small">ADMIN includes the support permissions available to AGENT. USER accounts are limited to requester tasks.</p>
        ${roleAccess.map(([role, description]) => `<div class="role-access-row ${profile.role === role ? 'current-role' : ''}">
          <b>${role}${profile.role === role ? ' · Your role' : ''}</b><span>${esc(description)}</span></div>`).join('')}
      </div>`;
  }

  // -------------------------------------------------------------------- start
  const saved = store.get('session');
  if (saved && saved.tokens && saved.user) { state.tokens = saved.tokens; state.user = saved.user; renderShell(); } else { renderLogin(); }
})();
