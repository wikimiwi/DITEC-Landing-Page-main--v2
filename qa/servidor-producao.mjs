// Servidor estatico de TESTE que imita uma hospedagem real (GitHub Pages/CDN):
// compressao gzip/brotli e cabecalhos de cache. Serve ../Código-fonte na porta 5501.
// Uso: node servidor-producao.mjs   (apenas para medir desempenho localmente)
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import { fileURLToPath } from 'node:url';

const RAIZ = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../Código-fonte');
const PORTA = +(process.env.PORTA || 5501);
const TIPOS = { '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml', '.ico': 'image/x-icon', '.webp': 'image/webp' };
http.createServer((req, res) => {
  let rel = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  if (rel.endsWith('/')) rel += 'index.html';
  const arq = path.join(RAIZ, path.normalize(rel));
  if (!arq.startsWith(RAIZ) || !fs.existsSync(arq) || fs.statSync(arq).isDirectory()) { res.writeHead(404); return res.end('404'); }
  const ext = path.extname(arq).toLowerCase();
  const corpo = fs.readFileSync(arq);
  const headers = { 'Content-Type': TIPOS[ext] || 'application/octet-stream', 'Cache-Control': ext === '.html' ? 'no-cache' : 'public, max-age=31536000' };
  const aceita = req.headers['accept-encoding'] || '';
  const comprimivel = /text|javascript|json|svg/.test(headers['Content-Type']);
  if (comprimivel && aceita.includes('br')) { res.writeHead(200, { ...headers, 'Content-Encoding': 'br' }); return res.end(zlib.brotliCompressSync(corpo)); }
  if (comprimivel && aceita.includes('gzip')) { res.writeHead(200, { ...headers, 'Content-Encoding': 'gzip' }); return res.end(zlib.gzipSync(corpo)); }
  res.writeHead(200, headers); res.end(corpo);
}).listen(PORTA, '127.0.0.1', () => console.log(`servidor de teste em http://127.0.0.1:${PORTA}`));
