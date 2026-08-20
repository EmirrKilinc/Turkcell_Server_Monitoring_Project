// Shared auth helpers for the multi-page vanilla JS frontend.
// Token lives in localStorage; every protected page calls requireAuth()
// (and optionally requireRole()) before rendering anything sensitive.

const TOKEN_KEY = 'monitoring_jwt';

function saveToken(token) {
    localStorage.setItem(TOKEN_KEY, token);
}

function getToken() {
    return localStorage.getItem(TOKEN_KEY);
}

function clearToken() {
    localStorage.removeItem(TOKEN_KEY);
}

function isLoggedIn() {
    return !!getToken();
}

function decodeJwtPayload(token) {
    try {
        const base64Url = token.split('.')[1];
        const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
        const json = decodeURIComponent(
            atob(base64)
                .split('')
                .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
                .join('')
        );
        return JSON.parse(json);
    } catch (e) {
        return null;
    }
}

function getRole() {
    const token = getToken();
    if (!token) return null;
    const payload = decodeJwtPayload(token);
    return payload ? payload.role : null;
}

function getUsername() {
    const token = getToken();
    if (!token) return null;
    const payload = decodeJwtPayload(token);
    return payload ? payload.sub : null;
}

function logout() {
    clearToken();
    window.location.href = 'login.html';
}

function requireAuth() {
    if (!isLoggedIn()) {
        window.location.href = 'login.html';
    }
}

function requireRole(allowedRoles) {
    requireAuth();
    const role = getRole();
    if (!allowedRoles.includes(role)) {
        window.location.href = 'dashboard.html';
    }
}

async function authFetch(url, options = {}) {
    const token = getToken();
    const headers = Object.assign({}, options.headers, {
        'Authorization': token ? `Bearer ${token}` : '',
    });

    const response = await fetch(url, Object.assign({}, options, { headers }));

    if (response.status === 401) {
        clearToken();
        window.location.href = 'login.html';
    }

    return response;
}
