(function () {
  'use strict';
  const $ = s => document.querySelector(s);
  function escHtml(s) { return String(s ?? '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }
  function fmtData(iso) {
    if (!iso) return '';
    const d = new Date(iso);
    return isNaN(d) ? iso : d.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
  }

  const guestView = $('#guestView'), dashView = $('#dashView'), logoutBtn = $('#logoutBtn');

  const STATUS_LABEL = { AGENDADO: 'Agendado', EM_ATENDIMENTO: 'Em atendimento', CONCLUIDO: 'Concluído', CANCELADO: 'Cancelado' };
  const STATUS_BADGE = { AGENDADO: 'dash-badge--diagnostico', EM_ATENDIMENTO: 'dash-badge--reparo', CONCLUIDO: 'dash-badge--concluido', CANCELADO: 'dash-badge--cancelado' };

  async function render() {
    if (!DitecAPI.isLoggedIn() || DitecAPI.tipoUsuario() !== 'TECNICO') {
      if (DitecAPI.isLoggedIn()) DitecAPI.logout(); // logado, mas não é técnico
      guestView.hidden = false; dashView.hidden = true; logoutBtn.hidden = true;
      return;
    }
    guestView.hidden = true; dashView.hidden = false; logoutBtn.hidden = false;

    try {
      const me = await DitecAPI.me();
      $('#welcomeTitle').textContent = `Olá, ${me.nome.split(' ')[0]}! 👋`;
    } catch (err) { /* segue mesmo se falhar, não é crítico pra essa tela */ }

    await renderOS();
  }

  async function renderOS() {
    const wrap = $('#osList');
    wrap.innerHTML = `<div class="dash-empty">Carregando...</div>`;
    let lista;
    try {
      lista = await DitecAPI.minhasOS();
    } catch (err) {
      wrap.innerHTML = `<div class="dash-empty">${escHtml(err.message || 'Não foi possível carregar suas OS.')}</div>`;
      return;
    }

    $('#statAgendadas').textContent = lista.filter(o => o.status === 'AGENDADO').length;
    $('#statEmAtendimento').textContent = lista.filter(o => o.status === 'EM_ATENDIMENTO').length;
    $('#statConcluidas').textContent = lista.filter(o => o.status === 'CONCLUIDO').length;

    $('#osEmpty').hidden = lista.length > 0;
    if (!lista.length) { wrap.innerHTML = ''; return; }

    wrap.innerHTML = lista.map(os => `
      <div class="dash-row" data-os-id="${os.id}" data-status="${os.status}" style="flex-direction:column;align-items:stretch;gap:10px;">
        <div style="display:flex;justify-content:space-between;align-items:flex-start;gap:10px;flex-wrap:wrap;">
          <div>
            <div class="dash-row__main">${escHtml(os.protocolo)} — ${escHtml(os.tipoAparelho || '')}</div>
            <div class="dash-row__sub">👤 ${escHtml(os.clienteNome || '—')} · 📍 ${escHtml(os.bairro || '—')}</div>
            <div class="dash-row__sub">${escHtml(os.descricaoProblema || '')}</div>
          </div>
          <span class="dash-badge ${STATUS_BADGE[os.status] || ''}">${STATUS_LABEL[os.status] || os.status}</span>
        </div>
        <div class="os-actions-slot" style="display:flex;gap:8px;flex-wrap:wrap;"></div>
      </div>
    `).join('');

    lista.forEach(os => {
      const slot = wrap.querySelector(`.dash-row[data-os-id="${os.id}"] .os-actions-slot`);
      if (!slot) return;

      if (os.status === 'AGENDADO') {
        const btn = document.createElement('button');
        btn.className = 'btn btn--primary btn--sm';
        btn.type = 'button';
        btn.textContent = '▶️ Iniciar atendimento';
        btn.addEventListener('click', () => iniciarAtendimento(os.id, btn));
        slot.appendChild(btn);
      }

      if (os.status === 'EM_ATENDIMENTO') {
        const btn = document.createElement('button');
        btn.className = 'btn btn--primary btn--sm';
        btn.type = 'button';
        btn.textContent = '✅ Finalizar atendimento';
        btn.addEventListener('click', () => abrirFinalizar(os.id, slot, btn));
        slot.appendChild(btn);
      }
    });
  }

  async function iniciarAtendimento(osId, btn) {
    btn.disabled = true;
    try {
      await DitecAPI.atualizarStatusOS(osId, { status: 'EM_ATENDIMENTO', observacao: 'Técnico iniciou o atendimento.' });
      renderOS();
    } catch (err) {
      alert(err.message || 'Não foi possível iniciar o atendimento.');
      btn.disabled = false;
    }
  }

  function abrirFinalizar(osId, slot, btn) {
    btn.hidden = true;
    const form = document.createElement('div');
    form.style.cssText = 'width:100%;padding:12px;border:1px solid var(--border);border-radius:10px;';
    form.innerHTML = `
      <div class="profile-form__grid" style="margin-bottom:8px;">
        <input type="number" step="0.01" min="0" class="input" placeholder="Valor cobrado (R$)" id="fVal-${osId}" />
        <select class="input" id="fPag-${osId}">
          <option value="pix">Pix</option><option value="dinheiro">Dinheiro (5% desc.)</option>
          <option value="cartao">Cartão</option><option value="boleto">Boleto</option>
        </select>
      </div>
      <input type="text" class="input" placeholder="Peças utilizadas (opcional)" id="fPecas-${osId}" style="margin-bottom:8px;" />
      <textarea class="input" rows="2" placeholder="Descrição do serviço realizado (opcional)" id="fDesc-${osId}" style="margin-bottom:8px;"></textarea>
      <input type="text" class="input" placeholder="Nº da nota fiscal (opcional)" id="fNf-${osId}" style="margin-bottom:8px;" />
      <div style="display:flex;gap:8px;">
        <button class="btn btn--primary btn--sm" type="button" id="fEnviar-${osId}">Concluir OS</button>
        <button class="btn btn--outline btn--sm" type="button" id="fCancelar-${osId}">Cancelar</button>
      </div>
    `;
    slot.appendChild(form);

    $('#fCancelar-' + osId).addEventListener('click', () => { form.remove(); btn.hidden = false; });
    $('#fEnviar-' + osId).addEventListener('click', async () => {
      const valor = parseFloat($('#fVal-' + osId).value);
      if (isNaN(valor)) { alert('Informe o valor cobrado.'); return; }
      try {
        await DitecAPI.finalizarOS(osId, {
          valor, formaPagamento: $('#fPag-' + osId).value,
          pecasUtilizadas: $('#fPecas-' + osId).value.trim() || null,
          descricaoFinal: $('#fDesc-' + osId).value.trim() || null,
          notaFiscalNumero: $('#fNf-' + osId).value.trim() || null,
        });
        renderOS();
      } catch (err) {
        alert(err.message || 'Não foi possível finalizar a OS.');
      }
    });
  }

  $('#tecLoginForm').addEventListener('submit', async e => {
    e.preventDefault();
    const email = $('#tEmail').value.trim();
    const senha = $('#tSenha').value;
    const old = e.target.querySelector('.modal-error'); if (old) old.remove();
    const btn = e.target.querySelector('button[type="submit"]');
    btn.disabled = true;
    try {
      const data = await DitecAPI.login(email, senha);
      if (data.tipo !== 'TECNICO') {
        DitecAPI.logout();
        throw new Error('Esta conta não é de técnico. Use a Área do Cliente ou o Painel Admin.');
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

  render();
})();
