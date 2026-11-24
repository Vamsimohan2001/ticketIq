(() => {
  'use strict';

  const seedTickets = [
    {
      id: 'T-2417',
      title: 'VPN sign-in fails after password reset',
      requester: 'Maya Chen',
      initials: 'MC',
      category: 'Access',
      priority: 'High',
      status: 'In progress',
      team: 'Identity & Access',
      time: '8 min ago',
      confidence: 94,
      description: 'I reset my password this morning, but the VPN client still says my credentials are invalid. I can sign in to email normally. I need access before my 10:00 customer call.',
      summary: 'User cannot authenticate to VPN following a password reset; email access works.',
      similar: 'VPN account locked after a password change',
      resolution: 'Resynchronized the identity provider and cleared the cached VPN credential.',
      article: 'Refresh your VPN credentials',
      reply: 'Hi Maya, thanks for reporting this. Your VPN access appears to be out of sync with the recent password change. Please disconnect the VPN, remove the saved credential, and sign in again with your new password. We are checking the account sync on our side as well.'
    },
    {
      id: 'T-2416',
      title: 'Subscription charged twice this month',
      requester: 'Jordan Lee',
      initials: 'JL',
      category: 'Billing',
      priority: 'Medium',
      status: 'Triaged',
      team: 'Billing & Payments',
      time: '24 min ago',
      confidence: 88,
      description: 'I see two charges for the same subscription period. Could someone check whether one of them can be reversed?',
      summary: 'Possible duplicate subscription payment requiring a billing review.',
      similar: 'Duplicate monthly plan payment',
      resolution: 'Verified the duplicate transaction and initiated a refund.',
      article: 'Reviewing a duplicate payment',
      reply: 'Hi Jordan, we can help review the two transactions. Please share the transaction dates and amounts through the secure billing portal. We will verify whether a duplicate payment was captured and follow up on the next step.'
    },
    {
      id: 'T-2415',
      title: 'Cannot open the shared project folder',
      requester: 'Robin Patel',
      initials: 'RP',
      category: 'Access',
      priority: 'High',
      status: 'New',
      team: 'Identity & Access',
      time: '46 min ago',
      confidence: 76,
      description: 'The shared project folder says access denied. My teammates can still open it, and I need to review the latest files.',
      summary: 'Requester is missing access to a shared project folder.',
      similar: 'Team folder access removed after a role change',
      resolution: 'Restored the requester to the project group and refreshed permissions.',
      article: 'Request access to a team folder',
      reply: 'Hi Robin, thanks for letting us know. We will check the folder membership and confirm the right access level with the project owner. We will update you here once permissions have been reviewed.'
    },
    {
      id: 'T-2414',
      title: 'Suggestion: add a dark theme',
      requester: 'Sam Rivera',
      initials: 'SR',
      category: 'Feature',
      priority: 'Low',
      status: 'Resolved',
      team: 'Product Support',
      time: 'Yesterday',
      confidence: 82,
      description: 'A dark theme would make the dashboard easier to use during late shifts. This is a suggestion, not an outage.',
      summary: 'Feature request for an optional dark dashboard theme.',
      similar: 'Accessibility options for the dashboard',
      resolution: 'Shared the suggestion with the product team for review.',
      article: 'How product suggestions are reviewed',
      reply: 'Hi Sam, thanks for the thoughtful suggestion. We have recorded it for the product team to review as part of future accessibility and display improvements.'
    },
    {
      id: 'T-2413',
      title: 'Where can I find my monthly invoice?',
      requester: 'Uma User',
      initials: 'UU',
      category: 'Billing',
      priority: 'Low',
      status: 'Triaged',
      team: 'Billing & Payments',
      time: '2 days ago',
      confidence: 90,
      description: 'I need a copy of last month’s invoice for my expense report. Where can I download it?',
      summary: 'Requester needs guidance finding a previous monthly invoice.',
      similar: 'Download a billing receipt',
      resolution: 'Shared the billing page steps for downloading invoices.',
      article: 'Find and download an invoice',
      reply: 'Hi Uma, you can find previous invoices in the Billing section under Account settings. Choose the month you need and select Download invoice. If it is not listed, reply here and we will help you locate it.'
    }
  ];

  const state = {
    view: 'inbox',
    role: 'Agent',
    tickets: seedTickets.map((ticket) => ({ ...ticket, comments: [] })),
    selectedId: 'T-2417',
    filter: 'All',
    query: '',
    aiEnabled: true
  };

  const app = document.getElementById('app');
  const modalRoot = document.getElementById('modal-root');
  const toast = document.getElementById('toast');
  let toastTimer;

  const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g, (char) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[char]));
  const selectedTicket = () => state.tickets.find((ticket) => ticket.id === state.selectedId);
  const badgeClass = (value) => value.toLowerCase().replace(/\s+/g, '-');

  function showToast(message) {
    toast.textContent = message;
    toast.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => toast.classList.remove('show'), 2600);
  }

  function pageHeading(title, description, action = '') {
    return `<div class="page-heading"><div><h1>${title}</h1><p>${description}</p></div>${action}</div>`;
  }

  function renderShell(content) {
    const roleName = state.role === 'Agent' ? 'Asha Agent' : state.role === 'Admin' ? 'Alex Admin' : 'Uma User';
    const initials = state.role === 'Agent' ? 'AA' : state.role === 'Admin' ? 'AD' : 'UU';
    const nav = [
      ['inbox', '▤', state.role === 'User' ? 'My tickets' : 'Ticket queue'],
      ['insights', '◫', 'Insights'],
      ['knowledge', '▧', 'Knowledge base'],
      ['lab', '✳', 'AI Lab']
    ];

    app.innerHTML = `
      <div class="notice"><strong>Interactive preview</strong><span>Sample data only. This static demo has no real backend or sign-in.</span></div>
      <div class="layout">
        <aside class="sidebar">
          <div class="brand"><span class="brand-mark">T</span>TicketIQ</div>
          <div class="workspace"><small>WORKSPACE</small><strong>Northstar Support</strong></div>
          <div class="nav-label">Workspace</div>
          <nav class="nav" aria-label="Main navigation">
            ${nav.map(([view, icon, label]) => `<button type="button" class="${state.view === view ? 'active' : ''}" data-view="${view}"><span class="nav-icon" aria-hidden="true">${icon}</span>${label}</button>`).join('')}
          </nav>
          <div class="side-bottom">
            <div class="nav-label">Preview account</div>
            <div class="profile"><span class="avatar">${initials}</span><div><strong>${roleName}</strong><small>${state.role} view (simulated)</small></div></div>
          </div>
        </aside>
        <div class="main">
          <header class="topbar">
            <span class="crumb">Northstar Support <span aria-hidden="true">/</span> <b>${state.view === 'inbox' ? 'Inbox' : state.view === 'lab' ? 'AI Lab' : state.view === 'knowledge' ? 'Knowledge base' : 'Insights'}</b></span>
            <span class="topbar-spacer"></span>
            <span class="demo-tag"><i class="demo-dot"></i> Demo mode</span>
            <label class="sr-only" for="role-select">Preview role</label>
            <select class="role-select" id="role-select" aria-label="Preview role">
              ${['Agent', 'User', 'Admin'].map((role) => `<option ${state.role === role ? 'selected' : ''}>${role}</option>`).join('')}
            </select>
          </header>
          <main class="content">${content}<p class="footer-note">TicketIQ static preview · No real accounts, tickets, or AI service are connected.</p></main>
        </div>
      </div>`;
  }

  function filteredTickets() {
    const query = state.query.trim().toLowerCase();
    return state.tickets.filter((ticket) => {
      const matchesFilter = state.filter === 'All' || ticket.status === state.filter;
      const matchesQuery = !query || `${ticket.id} ${ticket.title} ${ticket.requester} ${ticket.category}`.toLowerCase().includes(query);
      return matchesFilter && matchesQuery && (state.role !== 'User' || ticket.requester === 'Uma User');
    });
  }

  function renderTicketRow(ticket) {
    return `<button type="button" class="ticket-row ${ticket.id === state.selectedId ? 'selected' : ''}" data-ticket="${escapeHtml(ticket.id)}">
      <span class="ticket-row-top"><span class="ticket-title">${escapeHtml(ticket.title)}</span><span class="ticket-id">${escapeHtml(ticket.id)}</span></span>
      <span class="ticket-row-bottom"><span class="ticket-meta">${escapeHtml(ticket.requester)} <span aria-hidden="true">·</span> ${escapeHtml(ticket.time)}</span><span class="badge ${badgeClass(ticket.priority)}">${escapeHtml(ticket.priority)}</span></span>
    </button>`;
  }

  function renderDetail(ticket) {
    if (!ticket) {
      return `<div class="panel"><div class="empty">No matching ticket selected. Choose another filter or search term.</div></div>`;
    }

    const aiPanel = state.aiEnabled ? `
      <section class="ai-card">
        <div class="ai-card-head"><span class="ai-orb">AI</span><div><strong>TicketIQ analysis</strong><small>Simulated suggestion · review before use</small></div><span class="topbar-spacer"></span><span class="badge triaged">Ready</span></div>
        <div class="ai-card-body">
          <div class="ai-recommendation">
            <div><span>Suggested category</span><strong>${escapeHtml(ticket.category)}</strong></div>
            <div><span>Suggested priority</span><strong>${escapeHtml(ticket.priority)}</strong></div>
            <div><span>Suggested team</span><strong>${escapeHtml(ticket.team)}</strong></div>
          </div>
          <div class="confidence"><div class="confidence-track"><i style="width:${ticket.confidence}%"></i></div><small>${ticket.confidence}% confidence</small></div>
          <div class="ai-excerpt"><b>Summary:</b> ${escapeHtml(ticket.summary)}<br><b>Similar incident:</b> ${escapeHtml(ticket.similar)}<br><b>Knowledge source:</b> ${escapeHtml(ticket.article)}</div>
          <label class="reply-label" for="reply-draft"><span>Suggested reply</span><span>Editable draft</span></label>
          <textarea class="reply" id="reply-draft">${escapeHtml(ticket.reply)}</textarea>
          <div class="detail-actions"><button class="button soft" type="button" data-action="copy-reply">Copy draft</button><button class="button" type="button" data-action="send-reply">Add demo reply</button></div>
        </div>
      </section>` : `<div class="callout">AI suggestions are switched off in this demo. You can turn them back on in AI Lab.</div>`;

    const comments = ticket.comments.length
      ? `<div class="comments">${ticket.comments.map((comment) => `<div class="comment"><strong>${escapeHtml(comment.author)}</strong>${escapeHtml(comment.text)}</div>`).join('')}</div>`
      : '';

    return `<article class="panel"><div class="panel-body ticket-detail">
      <div class="detail-title"><div><h2>${escapeHtml(ticket.title)}</h2><div class="ticket-id">${escapeHtml(ticket.id)} · created ${escapeHtml(ticket.time)}</div></div><span class="badge ${badgeClass(ticket.status)}">${escapeHtml(ticket.status)}</span></div>
      <div class="requester"><span class="avatar">${escapeHtml(ticket.initials)}</span><span><strong>${escapeHtml(ticket.requester)}</strong> reported an issue</span></div>
      <p class="description">${escapeHtml(ticket.description)}</p>
      <div class="detail-meta"><div><span>Category</span><strong>${escapeHtml(ticket.category)}</strong></div><div><span>Priority</span><strong>${escapeHtml(ticket.priority)}</strong></div><div><span>Assigned team</span><strong>${escapeHtml(ticket.team)}</strong></div></div>
      ${aiPanel}
      ${comments}
      <div class="detail-actions">${ticket.status !== 'Resolved' && state.role !== 'User' ? `<button class="button success" type="button" data-action="resolve">Mark resolved</button>` : ''}<span class="muted small">${ticket.status === 'Resolved' ? 'This sample ticket is resolved.' : 'Changes are only shown in this browser preview.'}</span></div>
    </div></article>`;
  }

  function renderInbox() {
    const filtered = filteredTickets();
    if (!filtered.some((ticket) => ticket.id === state.selectedId)) {
      state.selectedId = filtered[0]?.id || null;
    }
    const selected = selectedTicket();
    const openCount = state.tickets.filter((ticket) => ticket.status !== 'Resolved').length;
    const urgentCount = state.tickets.filter((ticket) => ticket.priority === 'High' || ticket.priority === 'Critical').length;
    const action = `<div class="actions"><button type="button" class="button primary" data-action="new-ticket">+ New ticket</button></div>`;

    return `${pageHeading(state.role === 'User' ? 'My tickets' : 'Support queue', 'Review incoming requests and work through AI-assisted suggestions.', action)}
      <section class="metrics" aria-label="Ticket metrics">
        <div class="metric"><div class="metric-top">Open tickets<span class="metric-icon">▤</span></div><strong>${openCount}<em>active</em></strong></div>
        <div class="metric"><div class="metric-top">Needs attention<span class="metric-icon">!</span></div><strong>${urgentCount}<em>priority</em></strong></div>
        <div class="metric"><div class="metric-top">Resolved today<span class="metric-icon">✓</span></div><strong>8<em>+2 this week</em></strong></div>
        <div class="metric"><div class="metric-top">AI-assisted<span class="metric-icon">AI</span></div><strong>92%<em>of sample queue</em></strong></div>
      </section>
      <section class="inbox-grid">
        <div class="panel">
          <div class="panel-head"><h2>${state.role === 'User' ? 'Your requests' : 'Recent tickets'}</h2><span class="topbar-spacer"></span><small>${filtered.length} shown</small></div>
          <div class="panel-body">
            <form id="search-form"><input class="search" id="ticket-search" type="search" placeholder="Search tickets..." value="${escapeHtml(state.query)}" aria-label="Search tickets"></form>
            <div class="filters" aria-label="Filter tickets">
              ${['All', 'New', 'In progress', 'Resolved'].map((filter) => `<button class="filter ${state.filter === filter ? 'active' : ''}" type="button" data-filter="${escapeHtml(filter)}">${escapeHtml(filter)}</button>`).join('')}
            </div>
          </div>
          <div class="ticket-list">${filtered.length ? filtered.map(renderTicketRow).join('') : '<div class="empty">No tickets match this view.</div>'}</div>
        </div>
        ${renderDetail(selected)}
      </section>`;
  }

  function renderInsights() {
    const action = `<div class="actions"><span class="demo-tag"><i class="demo-dot"></i> Sample metrics</span></div>`;
    return `${pageHeading('Insights', 'A snapshot of the sample support queue and triage activity.', action)}
      <section class="metrics">
        <div class="metric"><div class="metric-top">Tickets this week<span class="metric-icon">▤</span></div><strong>128<em>+12%</em></strong></div>
        <div class="metric"><div class="metric-top">Median first response<span class="metric-icon">◷</span></div><strong>18m<em>−6m</em></strong></div>
        <div class="metric"><div class="metric-top">Resolved<span class="metric-icon">✓</span></div><strong>86%<em>sample rate</em></strong></div>
        <div class="metric"><div class="metric-top">AI suggestions reviewed<span class="metric-icon">AI</span></div><strong>73%<em>sample rate</em></strong></div>
      </section>
      <section class="chart-grid">
        <article class="panel"><div class="panel-head"><h2>Tickets by category</h2><span class="topbar-spacer"></span><small>Last 7 days</small></div><div class="panel-body bars">
          ${[['Access', 74, 32], ['Billing', 52, 23], ['Product support', 38, 17], ['How-to', 31, 14], ['Other', 18, 8]].map(([label, width, count]) => `<div class="bar-row"><span>${label}</span><div class="bar-track"><div class="bar-fill" style="width:${width}%"></div></div><b>${count}</b></div>`).join('')}
        </div></article>
        <article class="panel"><div class="panel-head"><h2>AI vs. human triage</h2><span class="topbar-spacer"></span><small>Sample comparison</small></div><div class="panel-body bars">
          ${[['AI suggestions accepted', 73, '73%'], ['Adjusted by agent', 19, '19%'], ['Manual triage', 8, '8%']].map(([label, width, value]) => `<div class="bar-row"><span>${label}</span><div class="bar-track"><div class="bar-fill" style="width:${width}%;background:${label.startsWith('AI') ? '#20a17b' : ''}"></div></div><b>${value}</b></div>`).join('')}
          <div class="callout">These figures are illustrative sample metrics, not live service analytics.</div>
        </div></article>
      </section>`;
  }

  function renderKnowledge() {
    const articles = [
      ['Refresh your VPN credentials', 'Steps for clearing saved credentials and signing back in after a password change.', 'VPN', 'Access'],
      ['Reviewing a duplicate payment', 'How support staff verify duplicate transactions and guide a billing review.', 'Billing', 'Payments'],
      ['Request access to a team folder', 'Confirming project ownership and restoring the right group permissions.', 'Access', 'How-to'],
      ['How product suggestions are reviewed', 'What happens after a feature request is shared with the product team.', 'Product', 'Feedback']
    ];
    return `${pageHeading('Knowledge base', 'Sample support guidance used as context for suggested replies.')}
      <section class="panel"><div class="panel-head"><h2>Support articles</h2><span class="topbar-spacer"></span><small>${articles.length} sample articles</small></div><div class="panel-body kb-list">
        ${articles.map(([title, description, ...tags]) => `<article class="kb-item"><h3>${title}</h3><p>${description}</p><div class="kb-tags">${tags.map((tag) => `<span>${tag}</span>`).join('')}</div></article>`).join('')}
      </div></section>`;
  }

  function renderLab() {
    return `${pageHeading('AI Lab', 'Explore how AI-assisted triage behaves in this sample workspace.')}
      <section class="lab-grid">
        <article class="panel"><div class="panel-head"><h2>Demo controls</h2></div><div class="panel-body">
          <div class="switch-row"><div class="switch-copy"><strong>AI ticket suggestions</strong><small>Show simulated category, priority, and reply suggestions.</small></div><button class="toggle ${state.aiEnabled ? 'on' : ''}" type="button" role="switch" aria-checked="${state.aiEnabled}" aria-label="Toggle AI suggestions" data-action="toggle-ai"><span></span></button></div>
          <div class="switch-row"><div class="switch-copy"><strong>Provider</strong><small>Mock rules · deterministic sample behavior</small></div><span class="badge triaged">No API key</span></div>
          <div class="switch-row"><div class="switch-copy"><strong>Knowledge context</strong><small>Sample support articles and resolved tickets</small></div><span class="badge triaged">Enabled</span></div>
        </div></article>
        <article class="panel"><div class="panel-head"><h2>What this preview demonstrates</h2></div><div class="panel-body">
          <div class="kb-list">
            <div class="kb-item"><h3>Suggested triage</h3><p>Ticket text is matched against sample support patterns to illustrate category, priority, and team suggestions.</p></div>
            <div class="kb-item"><h3>Similar incidents</h3><p>Related sample resolutions are shown to make the retrieval-augmented support workflow easy to explore.</p></div>
            <div class="callout">This control changes the demo interface only. It does not configure or call the Java application's AI service.</div>
          </div>
        </div></article>
      </section>`;
  }

  function render() {
    const content = state.view === 'inbox' ? renderInbox()
      : state.view === 'insights' ? renderInsights()
      : state.view === 'knowledge' ? renderKnowledge()
      : renderLab();
    renderShell(content);
  }

  function openTicketModal() {
    modalRoot.innerHTML = `<div class="modal-backdrop" data-action="backdrop-close">
      <section class="modal" role="dialog" aria-modal="true" aria-labelledby="modal-title">
        <div class="modal-head"><h2 id="modal-title">Create a sample ticket</h2><button class="icon-button" type="button" aria-label="Close dialog" data-action="close-modal">×</button></div>
        <p class="muted small">Add a sample issue and see a simulated AI triage suggestion.</p>
        <div class="sample-buttons"><button class="sample" type="button" data-sample="reset">Reset email missing</button><button class="sample" type="button" data-sample="billing">Charged twice</button><button class="sample" type="button" data-sample="outage">Platform outage</button></div>
        <form id="new-ticket-form">
          <div class="form-field"><label for="new-title">Title</label><input class="field" id="new-title" name="title" maxlength="120" required placeholder="Briefly describe the issue"></div>
          <div class="form-field"><label for="new-description">Description</label><textarea class="field" id="new-description" name="description" maxlength="1000" required placeholder="What happened, and who is affected?"></textarea></div>
          <div class="modal-actions"><button class="button" type="button" data-action="close-modal">Cancel</button><button class="button primary" type="submit">Create &amp; analyze</button></div>
        </form>
      </section>
    </div>`;
    document.getElementById('new-title').focus();
  }

  function createTicket(title, description) {
    const text = `${title} ${description}`.toLowerCase();
    let category = 'Product support';
    let priority = 'Medium';
    let team = 'General Support';
    let confidence = 72;
    if (/password|vpn|sign.?in|access|login/.test(text)) {
      category = 'Access'; priority = /everyone|whole team|blocked/.test(text) ? 'High' : 'Medium';
      team = 'Identity & Access'; confidence = 91;
    } else if (/charge|payment|bill|refund/.test(text)) {
      category = 'Billing'; team = 'Billing & Payments'; confidence = 89;
    } else if (/down|outage|unavailable|crash/.test(text)) {
      category = 'Incident'; priority = 'Critical'; team = 'Application Engineering'; confidence = 87;
    } else if (/feature|suggest|would be nice|add /.test(text)) {
      category = 'Feature'; priority = 'Low'; team = 'Product Support'; confidence = 83;
    }

    const ticket = {
      id: `T-${2420 + state.tickets.length}`,
      title,
      requester: state.role === 'User' ? 'Uma User' : 'Demo Requester',
      initials: state.role === 'User' ? 'UU' : 'DR',
      category,
      priority,
      status: 'New',
      team,
      time: 'Just now',
      confidence,
      description,
      summary: `${category} issue reported: ${title}.`,
      similar: category === 'Access' ? 'VPN account locked after a password change' : category === 'Billing' ? 'Duplicate monthly plan payment' : 'Related support request from the sample queue',
      resolution: '',
      article: category === 'Access' ? 'Refresh your VPN credentials' : category === 'Billing' ? 'Reviewing a duplicate payment' : 'Contacting the support team',
      reply: `Hi ${state.role === 'User' ? 'Uma' : 'there'}, thanks for reporting this. We have received your request about "${title}" and will review it with the ${team} team.`,
      comments: []
    };
    state.tickets.unshift(ticket);
    state.selectedId = ticket.id;
    state.filter = 'All';
    state.query = '';
  }

  document.addEventListener('click', async (event) => {
    const button = event.target.closest('button');
    if (!button) return;
    if (button.dataset.view) {
      state.view = button.dataset.view;
      render();
      return;
    }
    if (button.dataset.ticket) {
      state.selectedId = button.dataset.ticket;
      render();
      return;
    }
    if (button.dataset.filter) {
      state.filter = button.dataset.filter;
      render();
      return;
    }
    if (button.dataset.sample) {
      const samples = {
        reset: ['Reset email is not arriving', 'I requested a password reset but the email has not arrived. I checked my spam folder and still cannot sign in.'],
        billing: ['Subscription charged twice', 'I see two charges for the same subscription period. Could support review the duplicate?'],
        outage: ['The support platform is down', 'The whole team cannot open the support portal. This is blocking work for everyone.']
      };
      document.getElementById('new-title').value = samples[button.dataset.sample][0];
      document.getElementById('new-description').value = samples[button.dataset.sample][1];
      return;
    }

    const ticket = selectedTicket();
    if (button.dataset.action === 'new-ticket') openTicketModal();
    if (button.dataset.action === 'close-modal') modalRoot.innerHTML = '';
    if (button.dataset.action === 'backdrop-close' && event.target === button) modalRoot.innerHTML = '';
    if (button.dataset.action === 'toggle-ai') {
      state.aiEnabled = !state.aiEnabled;
      render();
      showToast(`Simulated AI suggestions ${state.aiEnabled ? 'enabled' : 'disabled'}.`);
    }
    if (button.dataset.action === 'resolve' && ticket) {
      ticket.status = 'Resolved';
      ticket.time = 'Just now';
      render();
      showToast(`${ticket.id} marked resolved in this preview.`);
    }
    if (button.dataset.action === 'send-reply' && ticket) {
      const draft = document.getElementById('reply-draft')?.value.trim();
      if (!draft) return showToast('Add some text to the draft first.');
      ticket.comments.push({ author: state.role, text: draft });
      render();
      showToast('Demo reply added to the sample ticket.');
    }
    if (button.dataset.action === 'copy-reply' && ticket) {
      const draft = document.getElementById('reply-draft')?.value || '';
      try {
        await navigator.clipboard.writeText(draft);
        showToast('Draft copied.');
      } catch {
        showToast('Clipboard access is unavailable; select and copy the draft manually.');
      }
    }
  });

  app.addEventListener('change', (event) => {
    if (event.target.id === 'role-select') {
      state.role = event.target.value;
      state.view = 'inbox';
      state.filter = 'All';
      state.query = '';
      if (state.role === 'User') state.selectedId = null;
      else if (!selectedTicket()) state.selectedId = state.tickets[0]?.id || null;
      render();
      showToast(`Showing the ${state.role} demo view.`);
    }
  });

  document.addEventListener('submit', (event) => {
    if (event.target.id === 'search-form') {
      event.preventDefault();
      state.query = document.getElementById('ticket-search').value;
      render();
      return;
    }
    if (event.target.id === 'new-ticket-form') {
      event.preventDefault();
      const formData = new FormData(event.target);
      const title = String(formData.get('title') || '').trim();
      const description = String(formData.get('description') || '').trim();
      if (!title || !description) return;
      createTicket(title, description);
      modalRoot.innerHTML = '';
      state.view = 'inbox';
      render();
      showToast('Ticket created with a simulated triage suggestion.');
    }
  });

  modalRoot.addEventListener('click', (event) => {
    if (event.target.matches('.modal-backdrop')) modalRoot.innerHTML = '';
  });

  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') modalRoot.innerHTML = '';
  });

  render();
})();
