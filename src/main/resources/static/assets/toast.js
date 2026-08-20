// Uygulama-ici toast bildirimleri. Sayfalardaki cıplak `alert()` cagrilarinin
// yerine gecer - tarayicinin kendi (site disi, markasiz) popup kutusu yerine
// sag ust kosede kisa sureli, uygulamanin tasarimina uygun bir bildirim
// kutucugu gosterir. Depends on nothing but this file - auth.js/nav.js'e
// ihtiyac duymaz, herhangi bir sayfaya tek basina eklenebilir.

function showToast(message, type) {
    type = type || 'info';
    let stack = document.getElementById('appToastStack');
    if (!stack) {
        stack = document.createElement('div');
        stack.id = 'appToastStack';
        stack.className = 'app-toast-stack';
        document.body.appendChild(stack);
    }

    const icons = { info: 'ℹ️', success: '✅', warning: '⚠️', danger: '⛔' };

    const toast = document.createElement('div');
    toast.className = 'app-toast' + (type !== 'info' ? ' ' + type : '');
    toast.innerHTML = `
        <span class="app-toast-icon">${icons[type] || icons.info}</span>
        <span class="app-toast-message"></span>
        <button type="button" class="app-toast-close" aria-label="Kapat">&times;</button>
    `;
    toast.querySelector('.app-toast-message').textContent = message;

    function dismiss() {
        if (!toast.isConnected) return;
        toast.classList.add('hide');
        setTimeout(() => toast.remove(), 200);
    }

    toast.querySelector('.app-toast-close').addEventListener('click', dismiss);
    stack.appendChild(toast);

    setTimeout(dismiss, 4500);
}

// Uygulama-ici onay dialoglari. Sayfalardaki native `confirm()` cagrilarinin
// yerine gecer - tarayicinin kendi popup'i yerine uygulamanin tasarimina
// uygun bir modal gosterir ve kullanicinin secimini Promise<boolean> olarak
// dondurur: `if (!(await showConfirm('...'))) return;` seklinde confirm()
// ile ayni kullanim. options.danger = true kirmizi (yikici islem) buton
// stilini kullanir.
function showConfirm(message, options) {
    options = options || {};
    const danger = !!options.danger;
    const confirmLabel = options.confirmLabel || 'Onayla';
    const cancelLabel = options.cancelLabel || 'Vazgec';
    const title = options.title || (danger ? 'Islemi onaylayin' : 'Onay gerekiyor');

    return new Promise((resolve) => {
        const overlay = document.createElement('div');
        overlay.className = 'app-confirm-overlay';
        overlay.innerHTML = `
            <div class="app-confirm-modal" role="alertdialog" aria-modal="true" aria-labelledby="appConfirmTitle">
                <div class="app-confirm-title" id="appConfirmTitle">${title}</div>
                <div class="app-confirm-message"></div>
                <div class="app-confirm-actions">
                    <button type="button" class="app-confirm-btn cancel">${cancelLabel}</button>
                    <button type="button" class="app-confirm-btn${danger ? ' danger' : ' primary'}">${confirmLabel}</button>
                </div>
            </div>
        `;
        overlay.querySelector('.app-confirm-message').textContent = message;

        const cancelBtn = overlay.querySelector('.app-confirm-btn.cancel');
        const confirmBtn = overlay.querySelector('.app-confirm-btn.danger, .app-confirm-btn.primary');

        function close(result) {
            document.removeEventListener('keydown', onKeydown);
            overlay.classList.add('hide');
            setTimeout(() => overlay.remove(), 150);
            resolve(result);
        }

        function onKeydown(e) {
            if (e.key === 'Escape') close(false);
        }

        cancelBtn.addEventListener('click', () => close(false));
        confirmBtn.addEventListener('click', () => close(true));
        overlay.addEventListener('click', (e) => {
            if (e.target === overlay) close(false);
        });
        document.addEventListener('keydown', onKeydown);

        document.body.appendChild(overlay);
        confirmBtn.focus();
    });
}
