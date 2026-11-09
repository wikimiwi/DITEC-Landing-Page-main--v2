(function () {
  'use strict';
  const $ = s => document.querySelector(s);
  function escHtml(s) { return String(s ?? '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }

  const guestView = $('#guestView'), dashView = $('#dashView'), logoutBtn = $('#logoutBtn');

  const O = window.DitecOS;

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
    } catch (err) {
      if (err.status === 401) { render(); return; } // sessão expirada
    }

    await renderOS();
  }

  async function renderOS() {
    const wrap = $('#osList');
    wrap.innerHTML = `<div class="dash-empty">Carregando...</div>`;
    let lista;
    try {
      lista = await DitecAPI.minhasOS();
    } catch (err) {
      if (err.status === 401) { render(); return; }
      wrap.innerHTML = `<div class="dash-empty">${escHtml(err.message || 'Não foi possível carregar suas OS.')}</div>`;
      return;
    }

    $('#statAgendadas').textContent = lista.filter(o => o.status === 'AGENDADO').length;
    $('#statEmAtendimento').textContent = lista.filter(o => DitecOSEquipe.EM_ANDAMENTO.includes(o.status)).length;
    $('#statConcluidas').textContent = lista.filter(o => ['CONCLUIDO', 'AGUARDANDO_RETIRADA', 'ENTREGUE'].includes(o.status)).length;

    $('#osEmpty').hidden = lista.length > 0;
    if (!lista.length) { wrap.innerHTML = ''; return; }

    /* abertas primeiro (gás no topo), depois as finalizadas */
    const peso = o => (['ENTREGUE', 'CANCELADO'].includes(o.status) ? 2 : 0) + (o.prioridadeGas ? 0 : 1);
    lista.sort((a, b) => peso(a) - peso(b));

    wrap.innerHTML = lista.map(os => `
      <div class="dash-row" data-os-id="${os.id}" style="flex-direction:column;align-items:stretch;gap:10px;">
        <div style="display:flex;justify-content:space-between;align-items:flex-start;gap:10px;flex-wrap:wrap;">
          <div>
            <div class="dash-row__main">${O.esc(os.protocolo)} — ${O.esc(os.tipoAparelho || '')}${os.prioridadeGas ? ' 🔥' : ''}</div>
            <div class="dash-row__sub">👤 ${O.esc(os.clienteNome || '—')} · 📞 ${O.esc(os.clienteTelefone || '—')}</div>
            <div class="dash-row__sub">📍 ${O.esc(os.endereco || '')} — ${O.esc(os.bairro || '')} · 🗓️ ${O.esc(O.fmtDataHora(os.dataAgendada))}</div>
            ${os.previsaoConclusao ? `<div class="dash-row__sub">⏱️ Previsão: ${O.esc(O.fmtData(os.previsaoConclusao))}</div>` : ''}
          </div>
          <div style="display:flex;flex-direction:column;align-items:flex-end;gap:8px;">
            ${O.badge(os.status)}
            <button class="btn btn--outline btn--sm" type="button" data-detalhe="${os.id}" aria-expanded="false">Abrir / atualizar</button>
          </div>
        </div>
        <div class="os-detail-slot" hidden></div>
      </div>
    `).join('');
  }

  /* abre/fecha o painel de detalhes da OS */
  $('#osList').addEventListener('click', e => {
    const btn = e.target.closest('[data-detalhe]');
    if (!btn) return;
    const slot = btn.closest('.dash-row').querySelector('.os-detail-slot');
    const abrir = slot.hidden;
    slot.hidden = !abrir;
    btn.setAttribute('aria-expanded', String(abrir));
    btn.textContent = abrir ? 'Fechar' : 'Abrir / atualizar';
    if (abrir) {
      DitecOSEquipe.abrir(slot, Number(btn.dataset.detalhe), {
        isAdmin: false,
        onChange: os => {
          const row = btn.closest('.dash-row');
          const badgeEl = row.querySelector('.dash-badge');
          if (badgeEl) badgeEl.outerHTML = O.badge(os.status);
        },
      });
    }
  });

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
