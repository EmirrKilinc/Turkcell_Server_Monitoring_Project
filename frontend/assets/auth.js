// Shared auth helpers for the multi-page vanilla JS frontend.
// Token lives in localStorage; every protected page calls requireAuth()
// (and optionally requireRole()) before rendering anything sensitive.
//
// This frontend is served standalone by frontend/server.js (Node, port
// 3000), separate from the Spring Boot backend (port 8080) - so every
// /api/... call needs to be an absolute URL against the backend instead of
// the frontend's own origin. API_BASE_URL/apiUrl() are the single source
// of truth for that: authFetch() below resolves through it automatically,
// and any script that calls the bare `fetch()` (login/register before a
// token exists, nav.js's 2FA check) wraps its /api/... path in apiUrl()
// too. Nothing else needs to know the backend's port.

const API_BASE_URL = 'http://' + window.location.hostname + ':8080';

function apiUrl(path) {
    return path.indexOf('/api/') === 0 ? API_BASE_URL + path : path;
}

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

    const response = await fetch(apiUrl(url), Object.assign({}, options, { headers }));

    if (response.status === 401) {
        clearToken();
        window.location.href = 'login.html';
    }

    return response;
}
