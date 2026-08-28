// Shared app shell (topbar + left sidebar) injected into
// <div id="topbar-root"></div> and <div id="sidebar-root"></div>. Every
// authenticated page calls renderShell('pageKey') once at load time.
// Depends on assets/auth.js already being loaded (getUsername/getRole/
// logout/authFetch) - does NOT depend on the Bootstrap JS bundle, the
// notification bell and avatar dropdown are self-contained show/hide
// toggles.

const NOTIF_POLL_MS = 20000;

const SIDEBAR_PAGES = [
    { key: 'dashboard', href: 'dashboard.html', label: 'Dashboard', icon: 'bi-grid-1x2-fill' },
    { key: 'metrics', href: 'metrics.html', label: 'Metrikler', icon: 'bi-bar-chart-line-fill' },
    { key: 'metric-groups', href: 'metric-groups.html', label: 'Metrik Gruplari', icon: 'bi-collection-fill' },
    { key: 'configs', href: 'configs.html', label: 'Yapilandirma Takibi', icon: 'bi-file-earmark-diff-fill' },
    { key: 'aiops', href: 'aiops.html', label: 'AI Takip & Analiz', icon: 'bi-robot' },
];

function renderShell(activePage) {
    renderTopbar(activePage);
    renderSidebar(activePage);
    initNotificationBell();
    initAvatarDropdown();
    initClock();
    loadTwoFactorStatus();
}

function renderTopbar() {
    const role = getRole();
    const username = getUsername();
    const initial = username ? username.charAt(0).toUpperCase() : '?';

    document.getElementById('topbar-root').innerHTML = `
        <header class="app-topbar">
            <span class="app-brand"><i class="bi bi-hdd-network-fill"></i> Sistem Izleme &amp; Yapilandirma Takibi</span>
            <div class="app-topbar-right">
                <span class="app-clock" id="appClock">--:--:--</span>
                <span id="twofaPill" class="twofa-pill off d-none"></span>
                <div class="position-relative">
                    <button type="button" class="notif-bell-btn" id="notifBellBtn" aria-label="Bildirimler">
                        <i class="bi bi-bell-fill"></i>
                        <span id="notifBadge" class="badge bg-danger notif-bell-badge d-none">0</span>
                    </button>
                    <div id="notifPanel" class="notif-panel d-none" style="position:absolute; right:0; left:auto;"></div>
                </div>
                <div class="position-relative">
                    <button type="button" class="app-avatar-btn" id="avatarBtn">
                        <span class="app-avatar-circle">${initial}</span>
                        <span class="app-avatar-meta d-none d-sm-block">
                            <span class="app-avatar-name d-block">${username || ''}</span>
                            <span class="app-avatar-role d-block">Rol: ${role || ''}</span>
                        </span>
                        <i class="bi bi-chevron-down text-muted small"></i>
                    </button>
                    <div id="avatarMenu" class="app-dropdown-menu d-none">
                        <a href="profile.html" class="app-dropdown-item"><i class="bi bi-person-circle me-1"></i> Profilim</a>
                        <button type="button" class="app-dropdown-item" onclick="logout()"><i class="bi bi-box-arrow-right me-1"></i> Cikis</button>
                    </div>
                </div>
            </div>
        </header>
    `;
}

function renderSidebar(activePage) {
    const role = getRole();
    const links = SIDEBAR_PAGES.map(p =>
        `<a href="${p.href}" class="app-sidebar-link${p.key === activePage ? ' active' : ''}"><i class="bi ${p.icon}"></i> ${p.label}</a>`
    ).join('\n');

    const adminLink = role === 'ADMIN'
        ? `<a href="admin.html" class="app-sidebar-link${activePage === 'admin' ? ' active' : ''}"><i class="bi bi-people-fill"></i> Admin Paneli</a>`
        : '';

    document.getElementById('sidebar-root').innerHTML = `
        <aside class="app-sidebar">
            ${links}
            ${adminLink}
        </aside>
    `;
}

function initClock() {
    const clockEl = document.getElementById('appClock');
    function tick() {
        clockEl.textContent = new Date().toLocaleTimeString('tr-TR');
    }
    tick();
    setInterval(tick, 1000);
}

async function loadTwoFactorStatus() {
    const pill = document.getElementById('twofaPill');
    try {
        const res = await fetch(apiUrl('/api/auth/2fa-status'));
        if (!res.ok) return;
        const data = await res.json();
        pill.textContent = data.enabled ? '2FA Aktif' : '2FA Kapali';
        pill.classList.remove('on', 'off');
        pill.classList.add(data.enabled ? 'on' : 'off');
        pill.classList.remove('d-none');
    } catch (err) {
        // sessizce vazgec - kritik degil
    }
}

function initAvatarDropdown() {
    const btn = document.getElementById('avatarBtn');
    const menu = document.getElementById('avatarMenu');

    btn.addEventListener('click', (e) => {
        e.stopPropagation();
        menu.classList.toggle('d-none');
        document.getElementById('notifPanel').classList.add('d-none');
    });

    document.addEventListener('click', (e) => {
        if (!menu.contains(e.target) && e.target !== btn) {
            menu.classList.add('d-none');
        }
    });
}

function initNotificationBell() {
    const bellBtn = document.getElementById('notifBellBtn');
    const panel = document.getElementById('notifPanel');
    const badge = document.getElementById('notifBadge');

    function timeAgo(iso) {
        const diffMs = Date.now() - new Date(iso).getTime();
        const mins = Math.floor(diffMs / 60000);
        if (mins < 1) return 'simdi';
        if (mins < 60) return `${mins} dk once`;
        const hours = Math.floor(mins / 60);
        if (hours < 24) return `${hours} sa once`;
        return `${Math.floor(hours / 24)} gun once`;
    }

    function escapeHtml(value) {
        if (value === null || value === undefined) return '';
        return String(value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    async function refreshUnreadCount() {
        try {
            const res = await authFetch('/api/notifications/unread-count');
            if (!res.ok) return;
            const data = await res.json();
            if (data.count > 0) {
                badge.textContent = data.count > 99 ? '99+' : String(data.count);
                badge.classList.remove('d-none');
            } else {
                badge.classList.add('d-none');
            }
        } catch (err) {
            // sessizce vazgec - bildirim sayaci kritik degil
        }
    }

    function renderHeader(hasUnread) {
        return `
            <div class="notif-panel-header">
                <span class="notif-panel-title">Bildirimler</span>
                <button type="button" class="notif-mark-all-btn" id="notifMarkAllBtn" ${hasUnread ? '' : 'disabled'}>Tumunu okundu isaretle</button>
            </div>
        `;
    }

    async function loadPanel() {
        panel.innerHTML = renderHeader(false) + '<div class="notif-empty">Yukleniyor...</div>';
        try {
            const res = await authFetch('/api/notifications');
            if (!res.ok) throw new Error('failed');
            const notifications = await res.json();

            const hasUnread = notifications.some(n => !n.isRead);
            const bodyHtml = notifications.length === 0
                ? '<div class="notif-empty">Henuz bildirim yok</div>'
                : notifications.map(n => `
                    <a href="${n.link ? escapeHtml(n.link) : '#'}" class="notif-item${n.isRead ? '' : ' unread'}" data-id="${n.id}">
                        <div class="notif-item-title">${escapeHtml(n.title)}</div>
                        <div class="notif-item-message">${escapeHtml(n.message)}</div>
                        <div class="notif-item-time">${timeAgo(n.createdAt)}</div>
                    </a>
                `).join('');

            panel.innerHTML = renderHeader(hasUnread) + bodyHtml;

            panel.querySelectorAll('.notif-item').forEach(el => {
                el.addEventListener('click', async () => {
                    await authFetch(`/api/notifications/${el.dataset.id}/read`, { method: 'POST' });
                });
            });

            const markAllBtn = document.getElementById('notifMarkAllBtn');
            if (markAllBtn) {
                markAllBtn.addEventListener('click', async (e) => {
                    e.stopPropagation();
                    markAllBtn.disabled = true;
                    markAllBtn.textContent = 'Isaretleniyor...';
                    try {
                        await authFetch('/api/notifications/read-all', { method: 'POST' });
                        await loadPanel();
                        await refreshUnreadCount();
                    } catch (err) {
                        markAllBtn.disabled = false;
                        markAllBtn.textContent = 'Tumunu okundu isaretle';
                    }
                });
            }
        } catch (err) {
            panel.innerHTML = renderHeader(false) + '<div class="notif-empty">Bildirimler yuklenemedi</div>';
        }
    }

    bellBtn.addEventListener('click', async (e) => {
        e.stopPropagation();
        document.getElementById('avatarMenu').classList.add('d-none');
        const isOpen = !panel.classList.contains('d-none');
        if (isOpen) {
            panel.classList.add('d-none');
            return;
        }
        panel.classList.remove('d-none');
        await loadPanel();
        await refreshUnreadCount();
    });

    document.addEventListener('click', (e) => {
        if (!panel.contains(e.target) && e.target !== bellBtn) {
            panel.classList.add('d-none');
        }
    });

    refreshUnreadCount();
    setInterval(refreshUnreadCount, NOTIF_POLL_MS);
}
