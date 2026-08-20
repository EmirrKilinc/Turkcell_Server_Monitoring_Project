// Native <select>'in tarayici/OS seviyesinde actigi acilir listeyi
// (sayfa disina tasan bir "pop up" gibi gorunuyor) sayfanin kendi
// tasarimina gomulu, stillendirilebilir bir dropdown ile degistirir.
// Depends on nothing but this file.
//
// Kullanim:
//   const picker = createCustomSelect(document.getElementById('serverSelectRoot'), {
//       placeholder: '-- Sunucu secin --',
//       onChange: (value) => { ... }
//   });
//   picker.setOptions([{ value: '1', label: 'web-01' }, ...]);
//   picker.getValue(); picker.setValue('1');

function createCustomSelect(container, opts) {
    opts = opts || {};
    let placeholder = opts.placeholder || '-- Secin --';
    const onChange = opts.onChange || function () {};

    container.classList.add('custom-select');
    container.innerHTML = `
        <button type="button" class="custom-select-btn" aria-haspopup="listbox" aria-expanded="false">
            <span class="custom-select-btn-label is-placeholder">${escapeHtmlLocal(placeholder)}</span>
            <span class="custom-select-btn-caret">▾</span>
        </button>
        <div class="custom-select-panel d-none" role="listbox"></div>
    `;

    const btn = container.querySelector('.custom-select-btn');
    const labelEl = container.querySelector('.custom-select-btn-label');
    const panel = container.querySelector('.custom-select-panel');

    let options = [];
    let value = '';

    function escapeHtmlLocal(v) {
        if (v === null || v === undefined) return '';
        return String(v)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    function close() {
        container.classList.remove('open');
        panel.classList.add('d-none');
        btn.setAttribute('aria-expanded', 'false');
    }

    function open() {
        container.classList.add('open');
        panel.classList.remove('d-none');
        btn.setAttribute('aria-expanded', 'true');
    }

    function renderLabel() {
        const selected = options.find(o => String(o.value) === String(value));
        if (selected) {
            labelEl.textContent = selected.label;
            labelEl.classList.remove('is-placeholder');
        } else {
            labelEl.textContent = placeholder;
            labelEl.classList.add('is-placeholder');
        }
    }

    function renderPanel() {
        if (options.length === 0) {
            panel.innerHTML = '<div class="custom-select-option disabled">Secenek yok</div>';
            return;
        }
        panel.innerHTML = options.map(o => `
            <div class="custom-select-option${String(o.value) === String(value) ? ' selected' : ''}${o.disabled ? ' disabled' : ''}"
                 role="option" data-value="${escapeHtmlLocal(o.value)}">${escapeHtmlLocal(o.label)}</div>
        `).join('');

        panel.querySelectorAll('.custom-select-option:not(.disabled)').forEach(el => {
            el.addEventListener('click', () => {
                const newValue = el.getAttribute('data-value');
                const changed = String(newValue) !== String(value);
                value = newValue;
                renderLabel();
                renderPanel();
                close();
                if (changed) {
                    onChange(value);
                }
            });
        });
    }

    btn.addEventListener('click', (e) => {
        e.stopPropagation();
        if (container.classList.contains('open')) {
            close();
        } else {
            open();
        }
    });

    document.addEventListener('click', (e) => {
        if (!container.contains(e.target)) {
            close();
        }
    });

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') close();
    });

    return {
        setOptions(newOptions, newPlaceholder) {
            btn.disabled = false;
            if (newPlaceholder) {
                placeholder = newPlaceholder;
            }
            options = newOptions || [];
            if (!options.some(o => String(o.value) === String(value))) {
                value = '';
            }
            renderLabel();
            renderPanel();
        },
        setPlaceholder(text) {
            placeholder = text;
            if (!value) {
                renderLabel();
            }
        },
        getValue() {
            return value;
        },
        setValue(newValue) {
            value = newValue !== null && newValue !== undefined ? String(newValue) : '';
            renderLabel();
            renderPanel();
        },
        disable(message) {
            btn.disabled = true;
            labelEl.textContent = message || placeholder;
            labelEl.classList.add('is-placeholder');
        },
        enable() {
            btn.disabled = false;
            renderLabel();
        },
    };
}
