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
  const STATUS_OS_LABEL = { AGENDADO: 'Agendado', EM_ATENDIMENTO: 'Em atendimento', CONCLUIDO: 'Concluído', CANCELADO: 'Cancelado' };

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
    const opcoes = tecnicosCache.map(t => `<option value="${t.id}" ${t.nome === tecnicoAtualNome ? 'selected' : ''}>${escHtml(t.nome)}</option>`).join('');
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

  async function renderOS() {
    const tbody = $('#osTbody');
    try {
      const lista = await DitecAPI.admin.ordensServico();
      $('#osEmpty').hidden = lista.length > 0;
      tbody.innerHTML = lista.map(os => {
        const opts = Object.keys(STATUS_OS_LABEL).map(k =>
          `<option value="${k}" ${os.status === k ? 'selected' : ''}>${STATUS_OS_LABEL[k]}</option>`).join('');
        const finalizarBtn = os.status === 'EM_ATENDIMENTO'
          ? `<button class="btn btn--primary btn--sm" data-finalizar-os="${os.id}" type="button">Finalizar</button>` : '';
        return `
        <tr data-os-id="${os.id}">
          <td>${escHtml(os.protocolo)}</td>
          <td>${escHtml(os.clienteNome || '—')}</td>
          <td>${escHtml(os.tecnicoNome || '—')}</td>
          <td>${escHtml(os.tipoAparelho || '—')}</td>
          <td><select class="input dash-inline-select" data-status-os="${os.id}">${opts}</select></td>
          <td>${fmtMoeda(os.valor)}</td>
          <td>${finalizarBtn}<div class="finalizar-form-slot"></div></td>
        </tr>`;
      }).join('');
    } catch (err) {
      tbody.innerHTML = `<tr><td colspan="7">${escHtml(err.message)}</td></tr>`;
    }
  }

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
      try { await DitecAPI.admin.confirmarAgendamento(confirmarAg.getAttribute('data-confirmar-ag')); renderAgendamentos(); renderStats(); }
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

    const finalizarBtn = e.target.closest('[data-finalizar-os]');
    if (finalizarBtn) {
      abrirFormFinalizar(finalizarBtn);
      return;
    }
  });

  function abrirFormFinalizar(btn) {
    const osId = btn.getAttribute('data-finalizar-os');
    const slot = btn.closest('td').querySelector('.finalizar-form-slot');
    btn.hidden = true;
    slot.innerHTML = `
      <div style="margin-top:8px;padding:10px;border:1px solid var(--border);border-radius:10px;min-width:220px;">
        <input type="number" step="0.01" min="0" class="input" placeholder="Valor (R$)" id="fVal-${osId}" style="margin-bottom:6px;" />
        <select class="input" id="fPag-${osId}" style="margin-bottom:6px;">
          <option value="pix">Pix</option><option value="dinheiro">Dinheiro (5% desc.)</option>
          <option value="cartao">Cartão</option><option value="boleto">Boleto</option>
        </select>
        <input type="text" class="input" placeholder="Peças utilizadas" id="fPecas-${osId}" style="margin-bottom:6px;" />
        <input type="text" class="input" placeholder="Nota fiscal (opcional)" id="fNf-${osId}" style="margin-bottom:6px;" />
        <button class="btn btn--primary btn--sm" type="button" id="fEnviar-${osId}">Concluir OS</button>
      </div>`;
    $('#fEnviar-' + osId).addEventListener('click', async () => {
      const valor = parseFloat($('#fVal-' + osId).value);
      if (!valor && valor !== 0) { alert('Informe o valor cobrado.'); return; }
      try {
        await DitecAPI.finalizarOS(osId, {
          valor, formaPagamento: $('#fPag-' + osId).value,
          pecasUtilizadas: $('#fPecas-' + osId).value.trim() || null,
          notaFiscalNumero: $('#fNf-' + osId).value.trim() || null,
        });
        renderOS(); renderStats(); renderAgendamentos();
      } catch (err) { alert(err.message || 'Não foi possível finalizar.'); }
    });
  }

  /* ---- Ações delegadas (mudança de select) ---- */
  document.addEventListener('change', async e => {
    const tecnicoSel = e.target.closest('[data-tecnico-ag]');
    if (tecnicoSel) {
      const id = tecnicoSel.getAttribute('data-tecnico-ag');
      if (!tecnicoSel.value) return;
      try { await DitecAPI.admin.atribuirTecnico(id, Number(tecnicoSel.value)); renderAgendamentos(); }
      catch (err) { alert(err.message); }
      return;
    }

    const statusOsSel = e.target.closest('[data-status-os]');
    if (statusOsSel) {
      const id = statusOsSel.getAttribute('data-status-os');
      try {
        await DitecAPI.atualizarStatusOS(id, { status: statusOsSel.value });
        renderOS(); renderStats(); renderAgendamentos();
      } catch (err) {
        alert(err.message);
        renderOS(); // desfaz a selecao visual, redesenhando com o status real do servidor
      }
      return;
    }
  });

  render();
})();
