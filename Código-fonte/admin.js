(function () {
  'use strict';
  const $ = s => document.querySelector(s);
  const $$ = s => document.querySelectorAll(s);
  function escHtml(s) { return String(s ?? '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }
  function fmtData(iso) {
    if (!iso) return '';
    const d = new Date(iso);
    return isNaN(d) ? iso : d.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
  }
  function fmtMoeda(v) {
    if (v === null || v === undefined) return '—';
    return Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  const guestView = $('#guestView'), dashView = $('#dashView'), logoutBtn = $('#logoutBtn');
  let tecnicosCache = [];

  const STATUS_AG_LABEL = { PENDENTE: 'Pendente', AGENDADO: 'Confirmado', EM_ATENDIMENTO: 'Em atendimento', CONCLUIDO: 'Concluído', CANCELADO: 'Cancelado' };
  const STATUS_AG_BADGE = { PENDENTE: 'dash-badge--pendente', AGENDADO: 'dash-badge--diagnostico', EM_ATENDIMENTO: 'dash-badge--reparo', CONCLUIDO: 'dash-badge--concluido', CANCELADO: 'dash-badge--cancelado' };

  /* ---- Render principal ---- */
  async function render() {
    if (!DitecAPI.isLoggedIn() || DitecAPI.tipoUsuario() !== 'ADMINISTRADOR') {
      if (DitecAPI.isLoggedIn()) DitecAPI.logout(); /* logado, mas nao e' admin */
      guestView.hidden = false; dashView.hidden = true; logoutBtn.hidden = true;
      return;
    }
    guestView.hidden = true; dashView.hidden = false; logoutBtn.hidden = false;

    try {
      tecnicosCache = await DitecAPI.admin.tecnicos();
    } catch (err) { tecnicosCache = []; }

    await Promise.all([
      renderStats(),
      renderClientes(),
      renderAgendamentos(),
      renderOS(),
      renderTecnicos(),
      renderServicos(),
    ]);
  }

  async function renderStats() {
    try {
      const d = await DitecAPI.admin.dashboard();
      $('#statClientes').textContent = d.totalClientes;
      $('#statAgendamentos').textContent = d.totalAgendamentos;
      $('#statOSAbertas').textContent = d.osAbertas;
      $('#statOSConcluidas').textContent = d.osConcluidas;
      $('#statAtendimentosMes').textContent = d.atendimentosNoMes;
    } catch (err) { console.error(err); }
  }

  async function renderClientes() {
    const tbody = $('#clientesTbody');
    try {
      const clientes = await DitecAPI.admin.clientes();
      $('#clientesEmpty').hidden = clientes.length > 0;
      tbody.innerHTML = clientes.map(c => `
        <tr>
          <td>${escHtml(c.nome)}</td>
          <td>${escHtml(c.email)}</td>
          <td>${escHtml(c.telefone)}</td>
          <td>${escHtml(c.bairro || '—')}</td>
          <td>${escHtml(c.clienteDesde || '—')}</td>
        </tr>`).join('');
    } catch (err) {
      tbody.innerHTML = `<tr><td colspan="5">${escHtml(err.message)}</td></tr>`;
    }
  }

  function opcoesTecnicos(tecnicoAtualNome) {
    const semAtribuir = `<option value="">— atribuir técnico —</option>`;
    const opcoes = tecnicosCache.filter(t => t.status === 'ATIVO' || t.nome === tecnicoAtualNome).map(t => `<option value="${t.id}" ${t.nome === tecnicoAtualNome ? 'selected' : ''}>${escHtml(t.nome)}</option>`).join('');
    return semAtribuir + opcoes;
  }

  async function renderAgendamentos() {
    const tbody = $('#agendamentosTbody');
    try {
      const pagina = await DitecAPI.admin.agendamentos(0, 200);
      const lista = pagina.content || [];
      $('#agendamentosEmpty').hidden = lista.length > 0;
      tbody.innerHTML = lista.map(a => `
        <tr data-ag-id="${a.id}">
          <td>${escHtml(a.protocolo || '—')}</td>
          <td>${escHtml(a.nome)}<div style="font-size:.75rem;color:var(--text-light);">${escHtml(a.telefone)}</div></td>
          <td>${escHtml(a.tipoAparelho)}${a.prioridadeGas ? ' 🔥' : ''}</td>
          <td>${escHtml(a.bairro)}</td>
          <td>${fmtData(a.dataHora)}</td>
          <td><select class="input dash-inline-select" data-tecnico-ag="${a.id}">${opcoesTecnicos(a.tecnicoNome)}</select></td>
          <td><span class="dash-badge ${STATUS_AG_BADGE[a.status] || 'dash-badge--pendente'}">${STATUS_AG_LABEL[a.status] || a.status}</span></td>
          <td>${a.status === 'PENDENTE' ? `<button class="btn btn--outline btn--sm" data-confirmar-ag="${a.id}" type="button">Confirmar</button>` : ''}</td>
        </tr>`).join('');
    } catch (err) {
      tbody.innerHTML = `<tr><td colspan="8">${escHtml(err.message)}</td></tr>`;
    }
  }

  const O = window.DitecOS;

  function filtrosOS() {
    return {
      protocolo: $('#fProtocolo').value, cliente: $('#fCliente').value,
      status: $('#fStatus').value, tecnicoId: $('#fTecnico').value,
    };
  }

  function preencherFiltros() {
    const st = $('#fStatus');
    if (st.options.length <= 1) {
      Object.entries(O.STATUS).forEach(([k, v]) => st.insertAdjacentHTML('beforeend', `<option value="${k}">${O.esc(v.label)}</option>`));
    }
    const tec = $('#fTecnico');
    const atual = tec.value;
    tec.innerHTML = '<option value="">Todos os técnicos</option>' +
      tecnicosCache.map(t => `<option value="${O.esc(t.id)}">${O.esc(t.nome)}</option>`).join('');
    tec.value = atual;
  }

  async function renderOS() {
    const tbody = $('#osTbody');
    preencherFiltros();
    tbody.innerHTML = '<tr><td colspan="8">Carregando...</td></tr>';
    try {
      const lista = await DitecAPI.admin.ordensServico(filtrosOS());
      $('#osEmpty').hidden = lista.length > 0;
      tbody.innerHTML = lista.map(os => `
        <tr data-os-id="${os.id}">
          <td>${O.esc(os.protocolo)}${os.prioridadeGas ? ' 🔥' : ''}</td>
          <td>${O.esc(os.clienteNome || '—')}</td>
          <td>${O.esc(os.tecnicoNome || '—')}</td>
          <td>${O.esc(os.tipoAparelho || '—')}</td>
          <td>${O.badge(os.status)}</td>
          <td>${O.esc(O.fmtData(os.previsaoConclusao) || '—')}</td>
          <td>${O.fmtMoeda(os.valor)}</td>
          <td><button class="btn btn--outline btn--sm" data-detalhe-os="${os.id}" type="button" aria-expanded="false">Detalhes</button></td>
        </tr>
        <tr class="os-detail-row" data-detalhe-de="${os.id}" hidden><td colspan="8"><div class="os-detail-slot"></div></td></tr>`).join('');
    } catch (err) {
      tbody.innerHTML = `<tr><td colspan="8">${O.esc(err.message)}</td></tr>`;
    }
  }

  $('#osFiltros').addEventListener('submit', e => { e.preventDefault(); renderOS(); });
  $('#osFiltros').addEventListener('reset', () => setTimeout(renderOS, 0));

  $('#osTbody').addEventListener('click', e => {
    const btn = e.target.closest('[data-detalhe-os]');
    if (!btn) return;
    const id = btn.getAttribute('data-detalhe-os');
    const linha = $(`tr[data-detalhe-de="${id}"]`);
    const abrir = linha.hidden;
    linha.hidden = !abrir;
    btn.setAttribute('aria-expanded', String(abrir));
    btn.textContent = abrir ? 'Fechar' : 'Detalhes';
    if (abrir) {
      DitecOSEquipe.abrir(linha.querySelector('.os-detail-slot'), Number(id), {
        isAdmin: true,
        tecnicos: tecnicosCache,
        onChange: os => {
          const tr = $(`tr[data-os-id="${id}"]`);
          if (!tr) return;
          tr.children[2].textContent = os.tecnicoNome || '—';
          tr.children[4].innerHTML = O.badge(os.status);
          tr.children[5].textContent = O.fmtData(os.previsaoConclusao) || '—';
          tr.children[6].textContent = O.fmtMoeda(os.valor);
          renderStats(); renderAgendamentos();
        },
      });
    }
  });

  async function renderTecnicos() {
    const tbody = $('#tecnicosTbody');
    try {
      const lista = await DitecAPI.admin.tecnicos();
      tecnicosCache = lista; // reaproveitado no <select> de atribuição de técnico e na edição
      $('#tecnicosEmpty').hidden = lista.length > 0;
      tbody.innerHTML = lista.map(t => `
        <tr data-tec-id="${t.id}">
          <td>${escHtml(t.nome)}</td>
          <td>${escHtml(t.email)}</td>
          <td>${escHtml(t.telefone || '—')}</td>
          <td>${escHtml(t.especialidade || '—')}</td>
          <td><span class="dash-badge ${t.status === 'ATIVO' ? 'dash-badge--concluido' : 'dash-badge--cancelado'}">${t.status}</span></td>
          <td style="display:flex;gap:6px;">
            <button class="btn btn--outline btn--sm" data-editar-tecnico="${t.id}" type="button">Editar</button>
            <button class="btn btn--outline btn--sm" data-toggle-tecnico="${t.id}" data-ativo="${t.status === 'ATIVO'}" type="button">${t.status === 'ATIVO' ? 'Desativar' : 'Ativar'}</button>
          </td>
        </tr>`).join('');
    } catch (err) {
      tbody.innerHTML = `<tr><td colspan="6">${escHtml(err.message)}</td></tr>`;
    }
  }

  let servicosCache = [];
  async function renderServicos() {
    const tbody = $('#servicosTbody');
    try {
      const lista = await DitecAPI.admin.servicos();
      servicosCache = lista;
      $('#servicosEmpty').hidden = lista.length > 0;
      tbody.innerHTML = lista.map(s => `
        <tr data-serv-id="${s.id}">
          <td>${escHtml(s.nome)}</td>
          <td>${escHtml(s.descricao || '—')}</td>
          <td>${fmtMoeda(s.valorBase)}</td>
          <td><span class="dash-badge ${s.status === 'ATIVO' ? 'dash-badge--concluido' : 'dash-badge--cancelado'}">${s.status}</span></td>
          <td style="display:flex;gap:6px;">
            <button class="btn btn--outline btn--sm" data-editar-servico="${s.id}" type="button">Editar</button>
            <button class="btn btn--outline btn--sm" data-toggle-servico="${s.id}" data-ativo="${s.status === 'ATIVO'}" type="button">${s.status === 'ATIVO' ? 'Desativar' : 'Ativar'}</button>
          </td>
        </tr>`).join('');
    } catch (err) {
      tbody.innerHTML = `<tr><td colspan="5">${escHtml(err.message)}</td></tr>`;
    }
  }

  /* ---- Login admin ---- */
  $('#adminLoginForm').addEventListener('submit', async e => {
    e.preventDefault();
    const email = $('#aEmail').value.trim();
    const senha = $('#aSenha').value;
    const old = e.target.querySelector('.modal-error'); if (old) old.remove();
    const btn = e.target.querySelector('button[type="submit"]');
    btn.disabled = true;
    try {
      const data = await DitecAPI.login(email, senha);
      if (data.tipo !== 'ADMINISTRADOR') {
        DitecAPI.logout();
        throw new Error('Esta conta não tem acesso administrativo.');
      }
      await render();
    } catch (err) {
      const el = document.createElement('p');
      el.className = 'modal-error';
      el.textContent = '⚠️ ' + (err.message || 'Credenciais inválidas.');
      e.target.insertBefore(el, e.target.firstChild);
    } finally {
      btn.disabled = false;
    }
  });

  logoutBtn.addEventListener('click', () => { DitecAPI.logout(); render(); });

  /* ---- Tabs ---- */
  $$('.dash-tab').forEach(tab => {
    tab.addEventListener('click', () => {
      $$('.dash-tab').forEach(t => t.classList.remove('active'));
      $$('.dash-panel').forEach(p => p.classList.remove('active'));
      tab.classList.add('active');
      $('#' + tab.dataset.panel).classList.add('active');
    });
  });

  /* ---- Técnico: novo / editar ---- */
  function abrirFormTecnicoNovo() {
    $('#tecnicoForm').reset();
    $('#tecEditId').value = '';
    $('#tecnicoFormTitulo').textContent = 'Novo técnico';
    $('#tecEmail').disabled = false;
    $('#tecnicoFormMsg').textContent = '';
    $('#tecnicoForm').hidden = false;
  }
  function abrirFormTecnicoEditar(tecnico) {
    $('#tecEditId').value = tecnico.id;
    $('#tecnicoFormTitulo').textContent = `Editando: ${tecnico.nome}`;
    $('#tecNome').value = tecnico.nome || '';
    $('#tecEmail').value = tecnico.email || '';
    $('#tecEmail').disabled = true; // e-mail é a chave de login, não muda por aqui
    $('#tecTelefone').value = tecnico.telefone || '';
    $('#tecEspecialidade').value = tecnico.especialidade || '';
    $('#tecCertificacao').value = tecnico.certificacao || '';
    $('#tecnicoFormMsg').textContent = '';
    $('#tecnicoForm').hidden = false;
  }
  $('#novoTecnicoBtn').addEventListener('click', abrirFormTecnicoNovo);
  $('#cancelarTecnicoBtn').addEventListener('click', () => { $('#tecnicoForm').hidden = true; $('#tecnicoForm').reset(); $('#tecEmail').disabled = false; });
  $('#tecnicoForm').addEventListener('submit', async e => {
    e.preventDefault();
    const msg = $('#tecnicoFormMsg');
    msg.textContent = '';
    const editId = $('#tecEditId').value;
    const payload = {
      nome: $('#tecNome').value.trim(),
      email: $('#tecEmail').value.trim(),
      telefone: $('#tecTelefone').value.trim(),
      especialidade: $('#tecEspecialidade').value.trim(),
      certificacao: $('#tecCertificacao').value.trim(),
    };
    try {
      if (editId) {
        await DitecAPI.admin.atualizarTecnico(editId, payload);
        msg.style.color = '#16a34a';
        msg.textContent = 'Técnico atualizado com sucesso!';
      } else {
        const resultado = await DitecAPI.admin.criarTecnico(payload);
        msg.style.color = '#16a34a';
        msg.textContent = resultado.senhaTemporaria
          ? `Técnico criado! Senha temporária: ${resultado.senhaTemporaria} (repasse por um canal seguro).`
          : 'Técnico criado com sucesso!';
        $('#tecnicoForm').reset();
      }
      renderTecnicos();
    } catch (err) {
      msg.style.color = '#dc2626';
      msg.textContent = err.message || 'Não foi possível salvar o técnico.';
    }
  });

  /* ---- Serviço: novo / editar ---- */
  function abrirFormServicoNovo() {
    $('#servicoForm').reset();
    $('#servEditId').value = '';
    $('#servicoFormTitulo').textContent = 'Novo serviço';
    $('#servicoFormMsg').textContent = '';
    $('#servicoForm').hidden = false;
  }
  function abrirFormServicoEditar(servico) {
    $('#servEditId').value = servico.id;
    $('#servicoFormTitulo').textContent = `Editando: ${servico.nome}`;
    $('#servNome').value = servico.nome || '';
    $('#servValor').value = servico.valorBase != null ? servico.valorBase : '';
    $('#servDescricao').value = servico.descricao || '';
    $('#servicoFormMsg').textContent = '';
    $('#servicoForm').hidden = false;
  }
  $('#novoServicoBtn').addEventListener('click', abrirFormServicoNovo);
  $('#cancelarServicoBtn').addEventListener('click', () => { $('#servicoForm').hidden = true; $('#servicoForm').reset(); });
  $('#servicoForm').addEventListener('submit', async e => {
    e.preventDefault();
    const msg = $('#servicoFormMsg');
    msg.textContent = '';
    const editId = $('#servEditId').value;
    const payload = {
      nome: $('#servNome').value.trim(),
      descricao: $('#servDescricao').value.trim(),
      valorBase: parseFloat($('#servValor').value),
    };
    try {
      if (editId) {
        await DitecAPI.admin.atualizarServico(editId, payload);
        msg.style.color = '#16a34a';
        msg.textContent = 'Serviço atualizado com sucesso!';
      } else {
        await DitecAPI.admin.criarServico(payload);
        msg.style.color = '#16a34a';
        msg.textContent = 'Serviço criado com sucesso!';
        $('#servicoForm').reset();
      }
      renderServicos();
    } catch (err) {
      msg.style.color = '#dc2626';
      msg.textContent = err.message || 'Não foi possível salvar o serviço.';
    }
  });

  /* ---- Relatórios (CSV) ---- */
  function bindRelatorio(btnId, fn) {
    $('#' + btnId).addEventListener('click', async () => {
      const btn = $('#' + btnId);
      btn.disabled = true;
      try { await fn(); } catch (err) { alert(err.message || 'Não foi possível gerar o relatório.'); }
      finally { btn.disabled = false; }
    });
  }
  bindRelatorio('relAgendamentosBtn', DitecAPI.admin.relatorioAgendamentos);
  bindRelatorio('relOSBtn', DitecAPI.admin.relatorioOS);
  bindRelatorio('relAvaliacoesBtn', DitecAPI.admin.relatorioAvaliacoes);
  bindRelatorio('relChatbotBtn', DitecAPI.admin.relatorioChatbot);

  /* ---- Ações delegadas (clique) ---- */
  document.addEventListener('click', async e => {
    const confirmarAg = e.target.closest('[data-confirmar-ag]');
    if (confirmarAg) {
      confirmarAg.disabled = true;
      try { await DitecAPI.admin.confirmarAgendamento(confirmarAg.getAttribute('data-confirmar-ag')); renderAgendamentos(); renderStats(); renderOS(); }
      catch (err) { alert(err.message); confirmarAg.disabled = false; }
      return;
    }

    const toggleTec = e.target.closest('[data-toggle-tecnico]');
    if (toggleTec) {
      const ativo = toggleTec.getAttribute('data-ativo') === 'true';
      try { await DitecAPI.admin.statusTecnico(toggleTec.getAttribute('data-toggle-tecnico'), !ativo); renderTecnicos(); }
      catch (err) { alert(err.message); }
      return;
    }

    const editarTec = e.target.closest('[data-editar-tecnico]');
    if (editarTec) {
      const id = Number(editarTec.getAttribute('data-editar-tecnico'));
      const tecnico = tecnicosCache.find(t => t.id === id);
      if (tecnico) abrirFormTecnicoEditar(tecnico);
      $('#tecnicoForm').scrollIntoView({ behavior: 'smooth', block: 'center' });
      return;
    }

    const toggleServ = e.target.closest('[data-toggle-servico]');
    if (toggleServ) {
      const ativo = toggleServ.getAttribute('data-ativo') === 'true';
      try { await DitecAPI.admin.statusServico(toggleServ.getAttribute('data-toggle-servico'), !ativo); renderServicos(); }
      catch (err) { alert(err.message); }
      return;
    }

    const editarServ = e.target.closest('[data-editar-servico]');
    if (editarServ) {
      const id = Number(editarServ.getAttribute('data-editar-servico'));
      const servico = servicosCache.find(s => s.id === id);
      if (servico) abrirFormServicoEditar(servico);
      $('#servicoForm').scrollIntoView({ behavior: 'smooth', block: 'center' });
      return;
    }
  });

  /* ---- Ações delegadas (mudança de select) ---- */
  document.addEventListener('change', async e => {
    const tecnicoSel = e.target.closest('[data-tecnico-ag]');
    if (tecnicoSel) {
      const id = tecnicoSel.getAttribute('data-tecnico-ag');
      if (!tecnicoSel.value) return;
      try { await DitecAPI.admin.atribuirTecnico(id, Number(tecnicoSel.value)); renderAgendamentos(); renderOS(); }
      catch (err) { alert(err.message); }
      return;
    }
  });

  render();
})();
