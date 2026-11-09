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
  async function request(path, { method = 'GET', body, timeoutMs = 20000 } = {}) {
    const headers = { 'Content-Type': 'application/json' };
    const token = getToken();
    if (token) headers['Authorization'] = 'Bearer ' + token;

    /* Toda chamada tem tempo máximo: a interface nunca fica travada esperando. */
    const ctrl = typeof AbortController !== 'undefined' ? new AbortController() : null;
    const timer = ctrl ? setTimeout(() => ctrl.abort(), timeoutMs) : null;
    let response;
    try {
      response = await fetch(BASE_URL + path, {
        method,
        headers,
        body: body !== undefined ? JSON.stringify(body) : undefined,
        signal: ctrl ? ctrl.signal : undefined,
      });
    } catch (redeErr) {
      if (redeErr && redeErr.name === 'AbortError') {
        throw new ApiError('O servidor demorou demais para responder. Tente novamente em instantes.', 408, null);
      }
      throw new ApiError(
        'Não foi possível falar com o servidor agora. Verifique sua conexão e tente novamente.',
        0, null
      );
    } finally {
      if (timer) clearTimeout(timer);
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
    /* Rastreio público: protocolo + telefone do agendamento (POST — o telefone não vai na URL). */
    async rastrear(protocolo, telefone) {
      return request('/ordens-servico/rastreio', { method: 'POST', body: { protocolo, telefone } });
    },
    async buscarOSPorId(id) { return request('/ordens-servico/' + id); },
    async minhasOS() { return request('/ordens-servico'); },
    async atualizarStatusOS(id, payload) { return request('/ordens-servico/' + id + '/status', { method: 'PUT', body: payload }); },
    async finalizarOS(id, payload) { return request('/ordens-servico/' + id + '/finalizar', { method: 'POST', body: payload }); },
    async avaliarOS(id, payload) { return request('/ordens-servico/' + id + '/avaliacao', { method: 'POST', body: payload }); },
    async diagnosticoOS(id, payload) { return request('/ordens-servico/' + id + '/diagnostico', { method: 'PUT', body: payload }); },
    async previsaoOS(id, payload) { return request('/ordens-servico/' + id + '/previsao', { method: 'PUT', body: payload }); },
    async observacaoOS(id, payload) { return request('/ordens-servico/' + id + '/observacoes', { method: 'POST', body: payload }); },
    async responderOrcamento(id, aprovado, observacao) {
      return request('/ordens-servico/' + id + '/orcamento/resposta', { method: 'POST', body: { aprovado, observacao: observacao || null } });
    },

    /* ---- Serviços (catálogo público) ---- */
    async servicos() { return request('/servicos'); },

    /* ---- Chatbot (proxy Google Gemini no backend) ---- */
    async chat(payload) { return request('/chat', { method: 'POST', body: payload, timeoutMs: 25000 }); },

    /* ---- Painel administrativo ---- */
    admin: {
      async dashboard() { return request('/admin/dashboard'); },
      async clientes() { return request('/admin/clientes'); },
      async agendamentos(page = 0, size = 100) { return request(`/admin/agendamentos?page=${page}&size=${size}`); },
      async confirmarAgendamento(id) { return request('/admin/agendamentos/' + id + '/confirmar', { method: 'PUT' }); },
      async atribuirTecnico(id, tecnicoId) {
        return request('/admin/agendamentos/' + id + '/tecnico', { method: 'PUT', body: { tecnicoId } });
      },
      /* filtros opcionais: { status, tecnicoId, protocolo, cliente } */
      async ordensServico(filtros = {}) {
        const qs = Object.entries(filtros)
          .filter(([, v]) => v !== undefined && v !== null && String(v).trim() !== '')
          .map(([k, v]) => encodeURIComponent(k) + '=' + encodeURIComponent(String(v).trim())).join('&');
        return request('/admin/ordens-servico' + (qs ? '?' + qs : ''));
      },
      async atribuirTecnicoOS(osId, tecnicoId) {
        return request('/admin/ordens-servico/' + osId + '/tecnico', { method: 'PUT', body: { tecnicoId } });
      },

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

  /* ============================================================
     DitecOS — apresentação da Ordem de Serviço, compartilhada por
     index (rastreio público), conta (cliente), técnico e admin.
     Tudo que vem do servidor passa por esc() antes de ir ao HTML.
     ============================================================ */
  function esc(v) {
    return String(v ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }
  function fmtDataHora(iso) {
    if (!iso) return '';
    const d = new Date(iso);
    return isNaN(d) ? String(iso) : d.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
  }
  function fmtData(iso) {
    if (!iso) return '';
    const [a, m, d] = String(iso).slice(0, 10).split('-');
    return d && m && a ? `${d}/${m}/${a}` : String(iso);
  }
  function fmtMoeda(v) {
    if (v === null || v === undefined || v === '') return '—';
    return Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  const STATUS = {
    AGENDADO:             { label: 'Aguardando avaliação',              badge: 'aguardando',  icon: '🗓️' },
    EM_DIAGNOSTICO:       { label: 'Em diagnóstico',                    badge: 'diagnostico', icon: '🔍' },
    AGUARDANDO_APROVACAO: { label: 'Aguardando aprovação do orçamento', badge: 'aguardando',  icon: '💬' },
    AGUARDANDO_PECAS:     { label: 'Aguardando peças',                  badge: 'aguardando',  icon: '📦' },
    EM_MANUTENCAO:        { label: 'Em manutenção',                     badge: 'reparo',      icon: '🔧' },
    EM_TESTES:            { label: 'Em testes',                         badge: 'reparo',      icon: '🧪' },
    CONCLUIDO:            { label: 'Serviço concluído',                 badge: 'concluido',   icon: '✅' },
    AGUARDANDO_RETIRADA:  { label: 'Aguardando retirada/entrega',       badge: 'concluido',   icon: '🚚' },
    ENTREGUE:             { label: 'Entregue ao cliente',               badge: 'concluido',   icon: '🏁' },
    CANCELADO:            { label: 'Cancelada',                         badge: 'cancelado',   icon: '❌' },
  };
  const ETAPAS = ['Recebida', 'Diagnóstico', 'Reparo', 'Testes', 'Concluída', 'Entregue'];
  const TIPO_EVENTO = {
    ABERTURA: 'Abertura', STATUS: 'Status', DIAGNOSTICO: 'Diagnóstico', ORCAMENTO: 'Orçamento',
    OBSERVACAO: 'Observação', ATRIBUICAO: 'Técnico', PREVISAO: 'Previsão',
  };

  function statusInfo(s) { return STATUS[s] || { label: s || '—', badge: 'pendente', icon: '•' }; }
  function badge(s, prefixo = 'dash-badge') {
    const i = statusInfo(s);
    return `<span class="${prefixo} ${prefixo}--${i.badge}">${i.icon} ${esc(i.label)}</span>`;
  }

  /* Indicador visual das etapas (status.etapa: 0..5; -1 = cancelada). */
  function stepper(os) {
    if (os.status === 'CANCELADO') {
      return `<div class="os-cancelada" role="status">❌ Esta Ordem de Serviço foi cancelada.</div>`;
    }
    const atual = typeof os.etapa === 'number' ? os.etapa : 0;
    const itens = ETAPAS.map((nome, i) => {
      const estado = i < atual || (i === atual && os.status === 'ENTREGUE') ? 'done' : (i === atual ? 'current' : 'todo');
      const marcador = estado === 'done' ? '✓' : String(i + 1);
      return `<li class="os-stepper__item os-stepper__item--${estado}" ${estado === 'current' ? 'aria-current="step"' : ''}>
        <span class="os-stepper__dot">${marcador}</span><span class="os-stepper__label">${nome}</span></li>`;
    }).join('');
    return `<ol class="os-stepper" aria-label="Etapas do atendimento">${itens}</ol>`;
  }

  /* Histórico real (mais recente primeiro). */
  function timeline(lista, { mostrarInternos = false } = {}) {
    const itens = (lista || []).slice().reverse();
    if (!itens.length) return '<p class="os-empty">Sem movimentações registradas.</p>';
    return `<ol class="os-history">${itens.map(t => {
      const mudouStatus = t.tipo === 'STATUS' || t.tipo === 'ABERTURA';
      const titulo = mudouStatus ? (statusInfo(t.statusNovo).label) : (TIPO_EVENTO[t.tipo] || t.tipo);
      const interno = mostrarInternos && t.visivelCliente === false ? ' <span class="os-tag-interna">interna</span>' : '';
      return `<li class="os-history__item os-history__item--${esc((t.tipo || '').toLowerCase())}">
        <div class="os-history__head"><strong>${esc(titulo)}</strong>${interno}<time datetime="${esc(t.dataHora)}">${esc(fmtDataHora(t.dataHora))}</time></div>
        ${t.observacao ? `<p class="os-history__obs">${esc(t.observacao)}</p>` : ''}
        <p class="os-history__by">por ${esc(t.alteradoPor || 'Sistema DITEC')}</p>
      </li>`;
    }).join('')}</ol>`;
  }

  /* Grade de informações da OS (campos ausentes não aparecem). */
  function infoGrid(os, { completo = false } = {}) {
    const linhas = [
      ['Aparelho', os.tipoAparelho],
      ['Abertura', fmtDataHora(os.dataAbertura)],
      ['Visita agendada', fmtDataHora(os.dataAgendada)],
      ['Última atualização', fmtDataHora(os.ultimaAtualizacao)],
      ['Técnico responsável', os.tecnicoNome],
      ['Previsão de conclusão', fmtData(os.previsaoConclusao)],
      ['Problema relatado', os.descricaoProblema],
      ['Diagnóstico', os.diagnostico],
      ['Orçamento', os.valorOrcamento != null ? fmtMoeda(os.valorOrcamento) +
        (os.orcamentoAprovado === true ? ' (aprovado)' : os.orcamentoAprovado === false ? ' (recusado)' : '') : null],
      ['Concluída em', fmtDataHora(os.dataConclusao)],
      ['Entregue em', fmtDataHora(os.dataEntrega)],
      ['Garantia', os.garantiaAte ? `90 dias — até ${fmtData(os.garantiaAte)}` : null],
    ];
    if (completo) {
      linhas.push(
        ['Cliente', os.clienteNome], ['Telefone', os.clienteTelefone],
        ['Endereço', [os.endereco, os.bairro].filter(Boolean).join(' — ')],
        ['Serviço realizado', os.descricaoFinal], ['Peças', os.pecasUtilizadas],
        ['Valor cobrado', os.valor != null ? fmtMoeda(os.valor) + (Number(os.desconto) > 0 ? ' (5% à vista)' : '') : null],
        ['Pagamento', os.formaPagamento], ['Nº do comprovante/nota', os.notaFiscalNumero],
      );
    }
    return `<dl class="os-info">${linhas.filter(([, v]) => v !== null && v !== undefined && v !== '')
      .map(([k, v]) => `<div class="os-info__row"><dt>${esc(k)}</dt><dd>${esc(v)}</dd></div>`).join('')}</dl>`;
  }

  global.DitecOS = { STATUS, ETAPAS, esc, fmtData, fmtDataHora, fmtMoeda, statusInfo, badge, stepper, timeline, infoGrid };
})(window);
