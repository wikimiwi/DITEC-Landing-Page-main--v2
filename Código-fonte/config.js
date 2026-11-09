/* ============================================================
   DITEC — configuração do front-end por ambiente.
   Em produção, informe a URL pública da API (sem barra no final), ex.:
     window.DITEC_API_BASE_URL = 'https://ditec-api.onrender.com';
   Deixe null para detectar automaticamente:
     - localhost/127.0.0.1 -> http://localhost:8080
     - qualquer outro host -> https://ditec-api.onrender.com
   Lembre-se de liberar a mesma URL em "connect-src" na meta CSP das
   4 páginas HTML (index, conta, tecnico, admin).
   ============================================================ */
window.DITEC_API_BASE_URL = window.DITEC_API_BASE_URL || null;
