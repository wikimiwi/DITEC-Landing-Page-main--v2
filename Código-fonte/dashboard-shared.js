/* ============================================================
   DITEC — dashboard-shared.js
   Cliente de API compartilhado por index.html, conta.html e
   admin.html — window.DitecAPI.

   ANTES: esta camada gravava tudo em localStorage (mock, sem
   backend real). AGORA: fala com a API Spring Boot real
   (DITEC-Landing-Page-main--v2/backend), que persiste em MySQL.

   Se o backend estiver rodando em outro host/porta, mude a
   linha BASE_URL abaixo (o mesmo padrão que o script.js já usava
   para o chatbot: http://localhost:8080).
   ============================================================ */
(function (global) {
  'use strict';

const API_ROOT = global.DITEC_API_BASE_URL ||
  (['localhost', '127.0.0.1'].includes(window.location.hostname)
    ? 'http://localhost:8080'
    : 'https://ditec-api.onrender.com');

const BASE_URL = API_ROOT + '/api';

  const TOKEN_KEY = 'ditec_token';
  const USER_KEY  = 'ditec_user'; // cache local só p/ exibição (nome/e-mail/tipo) — nunca a senha

  /* ---- Token / sessão ---- */
  function getToken() { return localStorage.getItem(TOKEN_KEY); }
  function setToken(t) { localStorage.setItem(TOKEN_KEY, t); }
  function clearToken() { localStorage.removeItem(TOKEN_KEY); }

  function getUser() {
    try { return JSON.parse(localStorage.getItem(USER_KEY)); } catch { return null; }
  }
  function setUser(u) { localStorage.setItem(USER_KEY, JSON.stringify(u)); }
  function clearUser() { localStorage.removeItem(USER_KEY); }

  function isLoggedIn() { return !!getToken() && !!getUser(); }
  function tipoUsuario() { const u = getUser(); return u ? u.tipo : null; }
  function logout() { clearToken(); clearUser(); }

  /* ---- Erro padronizado (espelha ErrorResponse do backend) ---- */
  class ApiError extends Error {
    constructor(message, status, body) {
      super(message);
      this.name = 'ApiError';
      this.status = status;
      this.body = body;
    }
  }

  /* ---- Requisição HTTP genérica ---- */
  async function request(path, { method = 'GET', body } = {}) {
    const headers = { 'Content-Type': 'application/json' };
    const token = getToken();
    if (token) headers['Authorization'] = 'Bearer ' + token;

    let response;
    try {
      response = await fetch(BASE_URL + path, {
        method,
        headers,
        body: body !== undefined ? JSON.stringify(body) : undefined,
      });
    } catch (redeErr) {
      throw new ApiError(
        'Não foi possível falar com o servidor. Verifique se o backend Java está rodando (porta 8080).',
        0, null
      );
    }

    if (response.status === 204) return null;

    const contentType = response.headers.get('content-type') || '';
    let data = null;
    if (contentType.includes('application/json')) {
      data = await response.json().catch(() => null);
    }

    if (!response.ok) {
      if (response.status === 401) logout(); // token invalido/expirado — limpa sessao local
      const msg = (data && data.message) || `Erro ${response.status}. Tente novamente.`;
      throw new ApiError(msg, response.status, data);
    }
    return data;
  }

  /* ---- Download de relatorio CSV (precisa do header Authorization,
     por isso nao da' pra usar um <a href> simples) ---- */
  async function baixarArquivo(path, nomeArquivo) {
    const token = getToken();
    const response = await fetch(BASE_URL + path, {
      headers: token ? { Authorization: 'Bearer ' + token } : {},
    });
    if (!response.ok) {
      throw new ApiError('Não foi possível gerar o relatório.', response.status, null);
    }
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = nomeArquivo;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 2000);
  }

  const DitecAPI = {
    ApiError,
    BASE_URL,

    /* ---- Sessão ---- */
    isLoggedIn,
    getUser,
    getToken,
    tipoUsuario,
    logout,

    /* ---- Autenticação ---- */
    async registrar({ nome, email, telefone, senha }) {
      const data = await request('/auth/register', { method: 'POST', body: { nome, email, telefone, senha } });
      setToken(data.token);
      setUser({ nome: data.nome, email: data.email, tipo: data.tipo });
      return data;
    },
    async login(email, senha) {
      const data = await request('/auth/login', { method: 'POST', body: { email, senha } });
      setToken(data.token);
      setUser({ nome: data.nome, email: data.email, tipo: data.tipo });
      return data;
    },
    async me() { return request('/auth/me'); },

    /* ---- Cliente (área logada) ---- */
    async meuPerfil() { return request('/clientes/me'); },
    async atualizarPerfil(payload) { return request('/clientes/me', { method: 'PUT', body: payload }); },
    async meusAgendamentos() { return request('/clientes/me/agendamentos'); },

    /* ---- Agendamentos (site público + cliente) ---- */
    async disponibilidade(dataISO) { return request('/agendamentos/disponibilidade?data=' + encodeURIComponent(dataISO)); },
    async criarAgendamento(payload) { return request('/agendamentos', { method: 'POST', body: payload }); },
    async buscarAgendamento(id) { return request('/agendamentos/' + id); },
    async atualizarAgendamento(id, payload) { return request('/agendamentos/' + id, { method: 'PUT', body: payload }); },
    async cancelarAgendamento(id) { return request('/agendamentos/' + id, { method: 'DELETE' }); },

    /* ---- Ordens de Serviço ---- */
    async rastrear(protocolo) { return request('/ordens-servico/protocolo/' + encodeURIComponent(protocolo)); },
    async buscarOSPorId(id) { return request('/ordens-servico/' + id); },
    async minhasOS() { return request('/ordens-servico'); },
    async atualizarStatusOS(id, payload) { return request('/ordens-servico/' + id + '/status', { method: 'PUT', body: payload }); },
    async finalizarOS(id, payload) { return request('/ordens-servico/' + id + '/finalizar', { method: 'POST', body: payload }); },
    async avaliarOS(id, payload) { return request('/ordens-servico/' + id + '/avaliacao', { method: 'POST', body: payload }); },

    /* ---- Serviços (catálogo público) ---- */
    async servicos() { return request('/servicos'); },

    /* ---- Chatbot (proxy Hugging Face) ---- */
    async chat(payload) { return request('/chat', { method: 'POST', body: payload }); },

    /* ---- Painel administrativo ---- */
    admin: {
      async dashboard() { return request('/admin/dashboard'); },
      async clientes() { return request('/admin/clientes'); },
      async agendamentos(page = 0, size = 100) { return request(`/admin/agendamentos?page=${page}&size=${size}`); },
      async confirmarAgendamento(id) { return request('/admin/agendamentos/' + id + '/confirmar', { method: 'PUT' }); },
      async atribuirTecnico(id, tecnicoId) {
        return request('/admin/agendamentos/' + id + '/tecnico', { method: 'PUT', body: { tecnicoId } });
      },
      async ordensServico() { return request('/admin/ordens-servico'); },

      async tecnicos() { return request('/admin/tecnicos'); },
      async criarTecnico(payload) { return request('/admin/tecnicos', { method: 'POST', body: payload }); },
      async atualizarTecnico(id, payload) { return request('/admin/tecnicos/' + id, { method: 'PUT', body: payload }); },
      async statusTecnico(id, ativo) { return request('/admin/tecnicos/' + id + '/status', { method: 'PUT', body: { ativo } }); },

      async servicos() { return request('/admin/servicos'); },
      async criarServico(payload) { return request('/admin/servicos', { method: 'POST', body: payload }); },
      async atualizarServico(id, payload) { return request('/admin/servicos/' + id, { method: 'PUT', body: payload }); },
      async statusServico(id, ativo) { return request('/admin/servicos/' + id + '/status', { method: 'PUT', body: { ativo } }); },

      async relatorioAgendamentos() { return baixarArquivo('/admin/relatorios/agendamentos', 'relatorio-agendamentos.csv'); },
      async relatorioOS() { return baixarArquivo('/admin/relatorios/os', 'relatorio-ordens-servico.csv'); },
      async relatorioAvaliacoes() { return baixarArquivo('/admin/relatorios/avaliacoes', 'relatorio-avaliacoes.csv'); },
      async relatorioChatbot() { return baixarArquivo('/admin/relatorios/chatbot', 'relatorio-chatbot.csv'); },
    },
  };

  global.DitecAPI = DitecAPI;
})(window);
