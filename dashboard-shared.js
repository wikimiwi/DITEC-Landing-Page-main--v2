/* ============================================================
   DITEC — dashboard-shared.js
   Camada de dados compartilhada entre conta.html e admin.html.
   MOCK: usa localStorage no lugar do banco de dados / backend.
   As mesmas chaves são gravadas pelo script.js da página inicial.
   TODO (próxima etapa do projeto): substituir tudo isto por
   chamadas HTTP para o backend Java/Spring Boot + banco de dados.
   ============================================================ */
(function (global) {
  'use strict';

  const KEYS = {
    USERS: 'ditec_users',
    SESSION: 'ditec_session',
    AGENDAMENTOS: 'ditec_agendamentos',
    OS_OVERRIDES: 'ditec_os_overrides',
    ADMIN_SESSION: 'ditec_admin_session',
  };

  function dbGet(key, fallback) {
    try { const v = localStorage.getItem(key); return v ? JSON.parse(v) : fallback; }
    catch { return fallback; }
  }
  function dbSet(key, value) { localStorage.setItem(key, JSON.stringify(value)); }

  /* ---- Usuários / sessão do cliente ---- */
  function getUsers() { return dbGet(KEYS.USERS, []); }
  function saveUsers(users) { dbSet(KEYS.USERS, users); }
  function findUserByEmail(email) {
    return getUsers().find(u => u.email.toLowerCase() === String(email || '').toLowerCase());
  }
  function getSession() { return dbGet(KEYS.SESSION, null); }
  function setSession(email) { dbSet(KEYS.SESSION, { email, since: Date.now() }); }
  function clearSession() { localStorage.removeItem(KEYS.SESSION); }
  function getCurrentUser() {
    const s = getSession();
    return s ? findUserByEmail(s.email) : null;
  }

  /* ---- Agendamentos ---- */
  function getAgendamentos() { return dbGet(KEYS.AGENDAMENTOS, []); }
  function saveAgendamentos(list) { dbSet(KEYS.AGENDAMENTOS, list); }

  /* ---- Ordens de Serviço (dados simulados + alterações do admin) ---- */
  const OS_DATA_MOCK = {
    'OS-2025-1001': {
      numero: 'OS-2025-1001', cliente: 'Maria Fernanda S.', aparelho: 'Geladeira Brastemp', bairro: 'Moema, SP',
      status: 'concluido', statusLabel: 'Concluído ✅',
    },
    'OS-2025-1002': {
      numero: 'OS-2025-1002', cliente: 'Carlos Roberto M.', aparelho: 'Máquina de Lavar LG', bairro: 'Pinheiros, SP',
      status: 'reparo', statusLabel: 'Em Reparo 🔧',
    },
    'OS-2025-1003': {
      numero: 'OS-2025-1003', cliente: 'Ana Paula T.', aparelho: 'Ar Condicionado Samsung', bairro: 'Vila Mariana, SP',
      status: 'diagnostico', statusLabel: 'Em Diagnóstico 🔍',
    },
  };
  const STATUS_LABELS = {
    aguardando: 'Aguardando ⏳', diagnostico: 'Em Diagnóstico 🔍',
    reparo: 'Em Reparo 🔧', concluido: 'Concluído ✅', cancelado: 'Cancelado ❌',
  };

  function getOSOverrides() { return dbGet(KEYS.OS_OVERRIDES, {}); }
  function saveOSOverrides(overrides) { dbSet(KEYS.OS_OVERRIDES, overrides); }
  function getAllOS() {
    const overrides = getOSOverrides();
    const merged = {};
    Object.keys(OS_DATA_MOCK).forEach(k => {
      merged[k] = Object.assign({}, OS_DATA_MOCK[k], overrides[k] || {});
    });
    return merged;
  }
  function updateOSStatus(numero, status) {
    const overrides = getOSOverrides();
    overrides[numero] = Object.assign({}, overrides[numero], { status, statusLabel: STATUS_LABELS[status] || status });
    saveOSOverrides(overrides);
  }

  /* ---- Sessão administrativa (mock — credencial fixa por enquanto) ---- */
  const ADMIN_CREDENTIALS = { email: 'admin@ditec.com.br', senha: 'ditec2025' };
  function getAdminSession() { return dbGet(KEYS.ADMIN_SESSION, null); }
  function loginAdmin(email, senha) {
    if (email.toLowerCase() === ADMIN_CREDENTIALS.email && senha === ADMIN_CREDENTIALS.senha) {
      dbSet(KEYS.ADMIN_SESSION, { email, since: Date.now() });
      return true;
    }
    return false;
  }
  function logoutAdmin() { localStorage.removeItem(KEYS.ADMIN_SESSION); }

  global.DitecDB = {
    KEYS,
    getUsers, saveUsers, findUserByEmail,
    getSession, setSession, clearSession, getCurrentUser,
    getAgendamentos, saveAgendamentos,
    getAllOS, updateOSStatus, STATUS_LABELS,
    getAdminSession, loginAdmin, logoutAdmin, ADMIN_CREDENTIALS,
  };
  /* mantém compatibilidade com o rastreador simples usado em conta.html */
  global.OS_DATA_MOCK = OS_DATA_MOCK;

})(window);
