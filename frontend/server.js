// Minimal statik dosya sunucusu - hicbir harici npm paketine ihtiyac duymaz,
// sadece Node.js'in yerlesik http/fs/path modullerini kullanir.
// Backend (Spring Boot, port 8080) ile ayni sunucuda ama ayri bir surec
// olarak 0.0.0.0:3000 uzerinden bu dizindeki tum sayfalari (dashboard.html,
// login.html, admin.html, ...) ve varlıkları servis eder.
//
// Bu artik tek sayfali (SPA) degil, cok sayfali bir site - her .html dosyasi
// kendi gercek dosyasindan direkt sunulur (orn. /login.html -> login.html).
// Sadece kok ('/') istegi ozel: Spring Boot'un statik kaynak sunucusunun
// varsayilan davranisiyla ayni sekilde index.html'e duser - o da zaten
// isLoggedIn() durumuna gore login.html/dashboard.html'e yonlendiriyor
// (bkz. index.html). Gercekten var olmayan bir yol istenirse duz 404 doner.

'use strict';

const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = process.env.PORT ? Number(process.env.PORT) : 3000;
const HOST = '0.0.0.0';
const ROOT_DIR = __dirname;

const MIME_TYPES = {
    '.html': 'text/html; charset=utf-8',
    '.js': 'text/javascript; charset=utf-8',
    '.css': 'text/css; charset=utf-8',
    '.json': 'application/json; charset=utf-8',
    '.png': 'image/png',
    '.jpg': 'image/jpeg',
    '.jpeg': 'image/jpeg',
    '.gif': 'image/gif',
    '.svg': 'image/svg+xml',
    '.ico': 'image/x-icon',
    '.woff': 'font/woff',
    '.woff2': 'font/woff2',
    '.map': 'application/json; charset=utf-8'
};

function sendFile(res, filePath) {
    const ext = path.extname(filePath).toLowerCase();
    const contentType = MIME_TYPES[ext] || 'application/octet-stream';
    fs.readFile(filePath, (err, data) => {
        if (err) {
            res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' });
            res.end('500 Internal Server Error');
            return;
        }
        res.writeHead(200, { 'Content-Type': contentType });
        res.end(data);
    });
}

const server = http.createServer((req, res) => {
    if (req.method !== 'GET' && req.method !== 'HEAD') {
        res.writeHead(405, { 'Content-Type': 'text/plain; charset=utf-8' });
        res.end('405 Method Not Allowed');
        return;
    }

    let urlPath;
    try {
        urlPath = decodeURIComponent(req.url.split('?')[0]);
    } catch (err) {
        res.writeHead(400, { 'Content-Type': 'text/plain; charset=utf-8' });
        res.end('400 Bad Request');
        return;
    }
    if (urlPath === '/') urlPath = '/index.html';

    // Path traversal koruması: cozumlenen yol her zaman ROOT_DIR icinde kalmali.
    const requestedPath = path.normalize(path.join(ROOT_DIR, urlPath));
    if (!requestedPath.startsWith(ROOT_DIR)) {
        res.writeHead(400, { 'Content-Type': 'text/plain; charset=utf-8' });
        res.end('400 Bad Request');
        return;
    }

    fs.stat(requestedPath, (err, stats) => {
        if (!err && stats.isFile()) {
            sendFile(res, requestedPath);
            return;
        }
        res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
        res.end('404 Not Found');
    });
});

server.listen(PORT, HOST, () => {
    console.log(`Frontend sunucusu http://${HOST}:${PORT} adresinde calisiyor (kok dizin: ${ROOT_DIR})`);
});
