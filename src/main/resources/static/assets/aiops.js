// AI Takip & Analiz - saf Vanilla JS.
// assets/auth.js + assets/nav.js'in once yuklenmis olmasina bagimlidir
// (requireAuth/authFetch/renderShell) - /api/v1/aiops/** SecurityConfig'de
// authenticated() altinda oldugu icin JWT olmadan cagrilar 401 doner ve
// asagidaki yerel mock/fallback'e duser.
//
// Metrik gruplari burada uydurma kategoriler (cpu/ram/disk/...) degil,
// Metrik Gruplari sayfasinda (metric-groups.html) tanimlanan gercek
// MetricGroup kayitlaridir - kullanici hangi gruplarin surekli AI
// tarafindan taranacagini burada secer, sohbette de o secili gruplardan
// birine ("scopeGroupId") ya da genele odaklanabilir.

(function () {
    'use strict';

    /* ------------------------------------------------------------------ */
    /* Static data                                                        */
    /* ------------------------------------------------------------------ */

    var QUICK_PROMPTS_FALLBACK = true; // butonlar HTML'de sabit tanimli

    var DEFAULT_CONFIG = {
        trackedGroupIds: [],
        alertEmailEnabled: false,
        alertEmail: '',
        dailySummaryEnabled: true
    };

    var FALLBACK_ANOMALIES = [
        {
            id: 'anomaly-correlation',
            title: 'Coklu Metrik Korelasyon',
            severity: 'warning',
            content: "CPU kullanimi %92'ye yukseldi ancak disk kuyrugu bos ve aktif HTTP oturumlari 10 katina cikti. Donanimsal darbogaz yok, ani gelen yuksek trafik yuku tespit edildi.",
            timestamp: new Date(Date.now() - 6 * 60 * 1000).toISOString()
        },
        {
            id: 'anomaly-rca',
            title: 'Log Analizi & Kok Neden Tespiti (RCA)',
            severity: 'critical',
            content: 'HikariCP connection pool tukendi. `application.yml` dosyasindaki maksimum havuz boyutu sinirina ulasildigi icin servis 500 hatalari uretiyor.',
            timestamp: new Date(Date.now() - 21 * 60 * 1000).toISOString()
        },
        {
            id: 'anomaly-daily',
            title: 'Gunluk Sistem Durum Ozeti',
            severity: 'info',
            content: 'Son 24 saatte genel altyapi stabil. Yalnizca 03:15 yedekleme islemi sirasinda gecici bellek dalgalanmasi yasandi.',
            timestamp: new Date(Date.now() - 3 * 60 * 60 * 1000).toISOString()
        }
    ];

    var INITIAL_MESSAGES = [
        {
            id: 'msg-welcome',
            role: 'assistant',
            content: 'Merhaba, ben lokal AIOps asistaniniz. Sol panelden izlemek istediginiz metrik gruplarini secip kaydedin, ben de sadece onlari takip edeyim. Sohbet kapsamini "Genel" ya da tek bir gruba daraltabilirsiniz.',
            timestamp: new Date().toISOString()
        }
    ];

    var SEVERITY_META = {
        info: { badgeClass: 'bg-primary', icon: 'bi-stars', label: 'Bilgi' },
        warning: { badgeClass: 'bg-warning', icon: 'bi-exclamation-triangle', label: 'Uyari' },
        critical: { badgeClass: 'bg-danger', icon: 'bi-exclamation-octagon', label: 'Kritik' }
    };

    /* ------------------------------------------------------------------ */
    /* State                                                              */
    /* ------------------------------------------------------------------ */

    var state = {
        availableGroups: [],          // [{id, name, serverId, serverHostname}]
        selectedGroupIds: new Set(),  // sol paneldeki (henuz kaydedilmemis olabilecek) toggle durumu
        selectedServerId: null,       // metric gruplari listesinde su an secili sunucu - once sunucu secilmeden liste hicbir sunucunun grubunu gostermez
        config: JSON.parse(JSON.stringify(DEFAULT_CONFIG)),
        anomalies: FALLBACK_ANOMALIES.slice(),
        messages: INITIAL_MESSAGES.slice(),
        isSending: false,
        scopeGroupId: null // null = "Genel"
    };

    var els = {}; // DOM element cache, filled on init

    /* ------------------------------------------------------------------ */
    /* Backend integration (fetch basarisiz olursa yerel mock'a duser)     */
    /* ------------------------------------------------------------------ */

    function fetchAvailableGroups() {
        return authFetch('/api/v1/aiops/available-groups')
            .then(function (res) {
                if (!res.ok) throw new Error('groups fetch failed');
                return res.json();
            })
            .catch(function () {
                return [];
            });
    }

    function fetchAIOpsConfig() {
        return authFetch('/api/v1/aiops/config')
            .then(function (res) {
                if (!res.ok) throw new Error('config fetch failed');
                return res.json();
            })
            .catch(function () {
                return JSON.parse(JSON.stringify(DEFAULT_CONFIG));
            });
    }

    function saveAIOpsConfig(config) {
        return authFetch('/api/v1/aiops/config', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(config)
        })
            .then(function (res) {
                if (!res.ok) throw new Error('config save failed');
                return res.json();
            })
            .then(function (data) { return { ok: true, config: data }; })
            .catch(function () { return { ok: false, config: null }; });
    }

    function fetchAnomalies() {
        return authFetch('/api/v1/aiops/anomalies')
            .then(function (res) {
                if (!res.ok) throw new Error('anomalies fetch failed');
                return res.json();
            })
            .then(function (data) {
                return (Array.isArray(data) && data.length > 0) ? data : FALLBACK_ANOMALIES;
            })
            .catch(function () {
                return FALLBACK_ANOMALIES;
            });
    }

    function sendChatMessage(message, scopeGroupId) {
        return authFetch('/api/v1/aiops/chat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ message: message, scopeGroupId: scopeGroupId })
        })
            .then(function (res) {
                if (!res.ok) throw new Error('chat request failed');
                return res.json();
            })
            .then(function (data) { return data.reply; })
            .catch(function () {
                return simulateAssistantReply(message);
            });
    }

    function simulateAssistantReply(message) {
        var lower = message.toLowerCase();
        if (lower.indexOf('hata') !== -1 || lower.indexOf('exception') !== -1) {
            return [
                '**Kok Neden Analizi**',
                '',
                'Son 24 saatte en cok hata `payment-service` tarafindan uretildi (42 adet 500 hatasi).',
                '',
                '```',
                'java.sql.SQLTransientConnectionException: HikariPool-1 - Connection is not available,',
                '  request timed out after 30000ms.',
                '    at com.zaxxer.hikari.pool.HikariPool.createTimeoutException(HikariPool.java:696)',
                '```',
                '',
                '> Onerilen aksiyon: `application.yml` icindeki `maximum-pool-size` degerini gecici olarak artirin ve pool sizing alarmini asagi cekin.'
            ].join('\n');
        }
        if (lower.indexOf('ozet') !== -1 || lower.indexOf('saglik') !== -1) {
            return 'Genel sistem sagligi **iyi** durumda. CPU ortalama %38, bellek kullanimi stabil, disk buyume hizi normal araliklarda. Tek dikkat noktasi: 03:15 yedekleme sirasinda kisa sureli bellek dalgalanmasi.';
        }
        return 'Su an secili metrik gruplarinda kritik seviyede bir anomali gozlemlemiyorum. Izlemeye devam ediyorum, esik degeri asan bir durum olustugunda anlik olarak bilgilendirecegim.';
    }

    /* ------------------------------------------------------------------ */
    /* Helpers                                                            */
    /* ------------------------------------------------------------------ */

    function escapeHtml(value) {
        if (value === null || value === undefined) return '';
        return String(value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    function timeAgo(iso) {
        var diffMs = Date.now() - new Date(iso).getTime();
        var mins = Math.floor(diffMs / 60000);
        if (mins < 1) return 'simdi';
        if (mins < 60) return mins + ' dk once';
        var hours = Math.floor(mins / 60);
        if (hours < 24) return hours + ' sa once';
        return Math.floor(hours / 24) + ' gun once';
    }

    /** Inline markdown: **bold**, `code`, ```fenced code``` and "> " warning lines. */
    function renderInline(text) {
        var escaped = escapeHtml(text);
        escaped = escaped.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
        escaped = escaped.replace(/`([^`]+)`/g, '<code>$1</code>');
        return escaped;
    }

    function renderAssistantContent(content) {
        var segments = content.split('```');
        var html = '';
        for (var i = 0; i < segments.length; i++) {
            var segment = segments[i];
            var isCode = i % 2 === 1;
            if (isCode) {
                var looksLikeError = /exception|error|at\s+\S+\(/i.test(segment);
                html += '<pre class="' + (looksLikeError ? 'error-block' : '') + '"><code>' +
                    escapeHtml(segment.trim()) + '</code></pre>';
                continue;
            }
            var lines = segment.split('\n').filter(function (l) { return l.length > 0; });
            for (var j = 0; j < lines.length; j++) {
                var line = lines[j];
                if (line.trim().indexOf('>') === 0) {
                    html += '<div class="warn-line"><i class="bi bi-exclamation-triangle"></i>' +
                        '<span>' + renderInline(line.replace(/^>\s*/, '')) + '</span></div>';
                } else {
                    html += '<p>' + renderInline(line) + '</p>';
                }
            }
        }
        return html;
    }

    /* ------------------------------------------------------------------ */
    /* Rendering: Izlenecek Metrik Gruplari                                */
    /* ------------------------------------------------------------------ */

    /** Sunucu dropdown'unu availableGroups'tan turetilen benzersiz sunucu
     * listesiyle doldurur. Secili sunucu artik gecerli degilse (veri yeniden
     * yuklendi, sunucu silindi vb.) ilk sunucuya duser. */
    function renderServerOptions() {
        var hostById = {};
        state.availableGroups.forEach(function (g) {
            hostById[g.serverId] = g.serverHostname;
        });
        var serverIds = Object.keys(hostById).sort(function (a, b) {
            return hostById[a].localeCompare(hostById[b]);
        });

        if (serverIds.length === 0) {
            els.serverSelect.innerHTML = '';
            state.selectedServerId = null;
            return;
        }

        if (state.selectedServerId === null || hostById[state.selectedServerId] === undefined) {
            state.selectedServerId = Number(serverIds[0]);
        }

        els.serverSelect.innerHTML = serverIds.map(function (id) {
            return '<option value="' + id + '">' + escapeHtml(hostById[id]) + '</option>';
        }).join('');
        els.serverSelect.value = String(state.selectedServerId);
    }

    /** Sadece dropdown'da secili sunucunun gruplarini listeler - tum
     * sunucularin gruplarini tek listede gostermek (eski davranis) sunucu
     * sayisi arttikca liste sonsuza dogru uzuyordu. */
    function renderMetricGroups() {
        if (state.availableGroups.length === 0) {
            els.metricGroupsList.innerHTML =
                '<p class="text-muted small mb-0">Henuz tanimli metrik grubu yok. ' +
                '<a href="metric-groups.html">Metrik Gruplari</a> sayfasindan sunucularinizda grup olusturun.</p>';
            return;
        }

        var groups = state.availableGroups.filter(function (g) {
            return g.serverId === state.selectedServerId;
        });

        if (groups.length === 0) {
            els.metricGroupsList.innerHTML = '<p class="text-muted small mb-0">Bu sunucuda tanimli metrik grubu yok.</p>';
            return;
        }

        var html = groups.map(function (g) {
            var checked = state.selectedGroupIds.has(g.id);
            return '<label class="form-check form-switch aiops-group-row">' +
                '<span class="form-check-label">' + escapeHtml(g.name) + '</span>' +
                '<input class="form-check-input" type="checkbox" role="switch" data-group-id="' + g.id + '"' + (checked ? ' checked' : '') + '>' +
                '</label>';
        }).join('');
        els.metricGroupsList.innerHTML = html;

        Array.prototype.forEach.call(
            els.metricGroupsList.querySelectorAll('[data-group-id]'),
            function (input) {
                input.addEventListener('change', function () {
                    var id = Number(input.getAttribute('data-group-id'));
                    if (input.checked) {
                        state.selectedGroupIds.add(id);
                    } else {
                        state.selectedGroupIds.delete(id);
                    }
                    setSaveStatus('idle');
                });
            }
        );
    }

    function renderChatScopeOptions() {
        var trackedIds = state.config.trackedGroupIds || [];
        var trackedGroups = state.availableGroups.filter(function (g) {
            return trackedIds.indexOf(g.id) !== -1;
        });

        var options = '<option value="">Genel (Tum Izlenen Gruplar)</option>' +
            trackedGroups.map(function (g) {
                return '<option value="' + g.id + '">' + escapeHtml(g.name) + ' — ' + escapeHtml(g.serverHostname) + '</option>';
            }).join('');
        els.chatScopeSelect.innerHTML = options;

        if (state.scopeGroupId && trackedIds.indexOf(state.scopeGroupId) === -1) {
            state.scopeGroupId = null;
        }
        els.chatScopeSelect.value = state.scopeGroupId || '';

        els.activeMetricBadge.textContent = trackedIds.length + (trackedIds.length === 1 ? ' Grup Bagli' : ' Grup Bagli');
    }

    function syncWatchdogUI() {
        els.alertEnabledSwitch.checked = !!state.config.alertEmailEnabled;
        els.alertEmailWrap.classList.toggle('d-none', !state.config.alertEmailEnabled);
        els.alertEmailInput.value = state.config.alertEmail || '';
        els.dailySummarySwitch.checked = !!state.config.dailySummaryEnabled;
    }

    function renderAnomalies() {
        if (state.anomalies.length === 0) {
            els.anomaliesList.innerHTML = '<p class="anomaly-body mb-0">Su an gosterilecek anomali yok.</p>';
            return;
        }
        els.anomaliesList.innerHTML = state.anomalies.map(function (a) {
            var meta = SEVERITY_META[a.severity] || SEVERITY_META.info;
            return (
                '<div class="anomaly-card">' +
                '<div class="anomaly-head">' +
                '<span class="anomaly-title">' + escapeHtml(a.title) + '</span>' +
                '<span class="badge ' + meta.badgeClass + '"><i class="bi ' + meta.icon + '"></i> ' + meta.label + '</span>' +
                '</div>' +
                '<p class="anomaly-body">' + escapeHtml(a.content) + '</p>' +
                '<div class="anomaly-time">' + timeAgo(a.timestamp) + '</div>' +
                '</div>'
            );
        }).join('');
    }

    function appendMessageEl(msg) {
        var row = document.createElement('div');
        row.className = 'chat-msg-row ' + (msg.role === 'user' ? 'user' : 'assistant');

        var avatar = document.createElement('div');
        avatar.className = 'chat-avatar ' + msg.role;
        avatar.innerHTML = '<i class="bi ' + (msg.role === 'user' ? 'bi-person' : 'bi-robot') + '"></i>';

        var bubble = document.createElement('div');
        bubble.className = 'chat-bubble ' + msg.role;

        if (msg.role === 'assistant') {
            bubble.innerHTML = renderAssistantContent(msg.content);
        } else {
            var p = document.createElement('p');
            p.textContent = msg.content;
            bubble.appendChild(p);
        }

        var time = document.createElement('div');
        time.className = 'chat-msg-time';
        time.textContent = new Date(msg.timestamp).toLocaleTimeString('tr-TR');
        bubble.appendChild(time);

        row.appendChild(avatar);
        row.appendChild(bubble);
        els.chatMessages.appendChild(row);
        scrollMessagesToBottom();
    }

    function renderAllMessages() {
        els.chatMessages.innerHTML = '';
        state.messages.forEach(appendMessageEl);
    }

    function scrollMessagesToBottom() {
        els.chatMessages.scrollTop = els.chatMessages.scrollHeight;
    }

    var typingEl = null;
    function showTyping() {
        if (typingEl) return;
        var row = document.createElement('div');
        row.className = 'chat-msg-row assistant';
        row.id = 'typingRow';
        row.innerHTML =
            '<div class="chat-avatar assistant"><i class="bi bi-robot"></i></div>' +
            '<div class="typing-indicator"><span></span><span></span><span></span></div>';
        els.chatMessages.appendChild(row);
        typingEl = row;
        scrollMessagesToBottom();
    }
    function hideTyping() {
        if (typingEl && typingEl.parentNode) typingEl.parentNode.removeChild(typingEl);
        typingEl = null;
    }

    /* ------------------------------------------------------------------ */
    /* Actions                                                            */
    /* ------------------------------------------------------------------ */

    function setSaveStatus(status) {
        els.saveStatus.classList.remove('text-success', 'text-danger');
        if (status === 'idle') {
            els.saveStatus.textContent = '';
            return;
        }
        els.saveStatus.classList.add(status === 'success' ? 'text-success' : 'text-danger');
        els.saveStatus.textContent = status === 'success'
            ? 'Ayarlar kaydedildi.'
            : 'Ayarlar kaydedilemedi, tekrar deneyin.';
    }

    function handleSaveConfig() {
        state.config.trackedGroupIds = Array.from(state.selectedGroupIds);

        els.saveBtn.disabled = true;
        els.saveBtnIcon.classList.add('spin');
        setSaveStatus('idle');

        saveAIOpsConfig(state.config).then(function (result) {
            els.saveBtn.disabled = false;
            els.saveBtnIcon.classList.remove('spin');

            if (result.ok) {
                state.config = result.config;
                state.selectedGroupIds = new Set(state.config.trackedGroupIds || []);
                renderChatScopeOptions();
                setSaveStatus('success');
            } else {
                setSaveStatus('error');
            }
        });
    }

    function loadAvailableGroups() {
        return fetchAvailableGroups().then(function (groups) {
            state.availableGroups = groups;
        });
    }

    function loadConfig() {
        return fetchAIOpsConfig().then(function (remote) {
            state.config = remote;
            state.selectedGroupIds = new Set(remote.trackedGroupIds || []);
            syncWatchdogUI();
        });
    }

    function loadAnomalies() {
        els.refreshBtnIcon.classList.add('spin');
        return fetchAnomalies().then(function (data) {
            state.anomalies = data;
            els.refreshBtnIcon.classList.remove('spin');
            renderAnomalies();
        });
    }

    function dispatchMessage(text) {
        var trimmed = (text || '').trim();
        if (!trimmed || state.isSending) return;

        var userMessage = {
            id: 'msg-' + Date.now() + '-u',
            role: 'user',
            content: trimmed,
            timestamp: new Date().toISOString()
        };
        state.messages.push(userMessage);
        appendMessageEl(userMessage);

        els.chatInput.value = '';
        autoGrowTextarea();
        state.isSending = true;
        updateSendButtonState();
        showTyping();

        sendChatMessage(trimmed, state.scopeGroupId).then(function (reply) {
            hideTyping();
            var assistantMessage = {
                id: 'msg-' + Date.now() + '-a',
                role: 'assistant',
                content: reply,
                timestamp: new Date().toISOString()
            };
            state.messages.push(assistantMessage);
            appendMessageEl(assistantMessage);
            state.isSending = false;
            updateSendButtonState();
        });
    }

    function updateSendButtonState() {
        els.sendBtn.disabled = state.isSending || !els.chatInput.value.trim();
        Array.prototype.forEach.call(els.quickPromptBtns, function (btn) {
            btn.disabled = state.isSending;
        });
    }

    function autoGrowTextarea() {
        els.chatInput.style.height = 'auto';
        els.chatInput.style.height = Math.min(els.chatInput.scrollHeight, 128) + 'px';
    }

    function handleClearChat() {
        state.messages = INITIAL_MESSAGES.map(function (m) {
            return Object.assign({}, m, { timestamp: new Date().toISOString() });
        });
        renderAllMessages();
    }

    /* ------------------------------------------------------------------ */
    /* Init                                                               */
    /* ------------------------------------------------------------------ */

    function cacheEls() {
        els.metricGroupsList = document.getElementById('metricGroupsList');
        els.serverSelect = document.getElementById('metricGroupServerSelect');
        els.activeMetricBadge = document.getElementById('activeMetricBadge');
        els.chatScopeSelect = document.getElementById('chatScopeSelect');

        els.alertEnabledSwitch = document.getElementById('alertEnabledSwitch');
        els.alertEmailWrap = document.getElementById('alertEmailWrap');
        els.alertEmailInput = document.getElementById('alertEmailInput');
        els.dailySummarySwitch = document.getElementById('dailySummarySwitch');

        els.saveBtn = document.getElementById('saveConfigBtn');
        els.saveBtnIcon = document.getElementById('saveConfigBtnIcon');
        els.saveStatus = document.getElementById('saveStatus');

        els.anomaliesList = document.getElementById('anomaliesList');
        els.refreshBtn = document.getElementById('refreshMetricsBtn');
        els.refreshBtnIcon = document.getElementById('refreshMetricsBtnIcon');

        els.clearChatBtn = document.getElementById('clearChatBtn');
        els.quickPromptBtns = document.querySelectorAll('.quick-prompt-btn');

        els.chatMessages = document.getElementById('chatMessages');
        els.chatInput = document.getElementById('chatInput');
        els.sendBtn = document.getElementById('sendChatBtn');
    }

    function bindStaticEvents() {
        els.alertEnabledSwitch.addEventListener('change', function () {
            state.config.alertEmailEnabled = els.alertEnabledSwitch.checked;
            syncWatchdogUI();
            setSaveStatus('idle');
        });
        els.alertEmailInput.addEventListener('input', function () {
            state.config.alertEmail = els.alertEmailInput.value;
            setSaveStatus('idle');
        });
        els.dailySummarySwitch.addEventListener('change', function () {
            state.config.dailySummaryEnabled = els.dailySummarySwitch.checked;
            setSaveStatus('idle');
        });
        els.saveBtn.addEventListener('click', handleSaveConfig);

        els.serverSelect.addEventListener('change', function () {
            state.selectedServerId = els.serverSelect.value ? Number(els.serverSelect.value) : null;
            renderMetricGroups();
        });

        els.refreshBtn.addEventListener('click', loadAnomalies);
        els.clearChatBtn.addEventListener('click', handleClearChat);

        els.chatScopeSelect.addEventListener('change', function () {
            state.scopeGroupId = els.chatScopeSelect.value ? Number(els.chatScopeSelect.value) : null;
        });

        Array.prototype.forEach.call(els.quickPromptBtns, function (btn) {
            btn.addEventListener('click', function () {
                dispatchMessage(btn.getAttribute('data-prompt'));
            });
        });

        els.chatInput.addEventListener('input', function () {
            autoGrowTextarea();
            updateSendButtonState();
        });
        els.chatInput.addEventListener('keydown', function (e) {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                dispatchMessage(els.chatInput.value);
            }
        });
        els.sendBtn.addEventListener('click', function () {
            dispatchMessage(els.chatInput.value);
        });
    }

    function init() {
        cacheEls();
        bindStaticEvents();

        renderAllMessages();
        updateSendButtonState();
        renderAnomalies();

        Promise.all([loadAvailableGroups(), loadConfig()]).then(function () {
            renderServerOptions();
            renderMetricGroups();
            renderChatScopeOptions();
        });
        loadAnomalies();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
