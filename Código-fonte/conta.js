(function () {
  'use strict';
  const $ = s => document.querySelector(s);
  const $$ = s => document.querySelectorAll(s);

  const guestView = $('#guestView'), dashView = $('#dashView');
  const logoutBtn = $('#logoutBtn');

  function onlyDigits(s) { return (s || '').replace(/\D/g, ''); }
  function maskTel(input) {
    if (!input) return;
    input.addEventListener('input', () => {
      let v = onlyDigits(input.value).slice(0, 11);
      if (v.length > 6) v = `(${v.slice(0,2)}) ${v.slice(2,7)}-${v.slice(7)}`;
      else if (v.length > 2) v = `(${v.slice(0,2)}) ${v.slice(2)}`;
      input.value = v;
    });
  }
  function maskCep(input) {
    if (!input) return;
    input.addEventListener('input', () => {
      let v = onlyDigits(input.value).slice(0, 8);
      if (v.length > 5) v = v.slice(0, 5) + '-' + v.slice(5);
      input.value = v;
    });
  }
  function escHtml(s) { return String(s ?? '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }
  function getInitials(nome) {
    const parts = String(nome || '').trim().split(/\s+/).filter(Boolean);
    if (!parts.length) return '?';
    if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }

  /* ---- Render principal ---- */
  async function render() {
    if (!DitecAPI.isLoggedIn()) {
      guestView.hidden = false; dashView.hidden = true; logoutBtn.hidden = true;
      return;
    }

    let perfil;
    try {
      perfil = await DitecAPI.meuPerfil();
    } catch (err) {
      /* token invalido/expirado (DitecAPI ja limpa a sessao em 401) ou backend fora do ar */
      guestView.hidden = false; dashView.hidden = true; logoutBtn.hidden = true;
      if (err.status !== 401) alert(err.message || 'Não foi possível carregar sua conta agora.');
      return;
    }

    guestView.hidden = true; dashView.hidden = false; logoutBtn.hidden = false;
    $('#welcomeTitle').textContent = `Olá, ${perfil.nome.split(' ')[0]}! 👋`;
    $('#profileAvatar').textContent = getInitials(perfil.nome);
    $('#profileName').textContent = perfil.nome;
    $('#profileEmailDisplay').textContent = perfil.email;
    $('#profileSince').textContent = `Cliente desde ${perfil.clienteDesde || '—'}`;
    $('#dNome').value = perfil.nome || '';
    $('#dEmail').value = perfil.email || '';
    $('#dTel').value = perfil.telefone || '';
    $('#dEndereco').value = perfil.endereco || '';
    $('#dBairro').value = perfil.bairro || '';
    $('#dCidade').value = perfil.cidade || '';
    $('#dUf').value = perfil.uf || '';
    $('#dCep').value = perfil.cep || '';

    renderAgendamentos();
  }

  const STATUS_BADGE = {
    PENDENTE: 'dash-badge--pendente', AGENDADO: 'dash-badge--diagnostico',
    EM_ATENDIMENTO: 'dash-badge--diagnostico', CONCLUIDO: 'dash-badge--concluido', CANCELADO: 'dash-badge--cancelado',
  };
  const STATUS_LABEL = {
    PENDENTE: 'Aguardando confirmação', AGENDADO: 'Confirmado', EM_ATENDIMENTO: 'Em atendimento',
    CONCLUIDO: 'Concluído', CANCELADO: 'Cancelado',
  };

  async function renderAgendamentos() {
    const el = $('#agendamentosList');
    el.innerHTML = `<div class="dash-empty">Carregando...</div>`;

    let list;
    try {
      list = await DitecAPI.meusAgendamentos();
    } catch (err) {
      el.innerHTML = `<div class="dash-empty">${escHtml(err.message || 'Não foi possível carregar seus agendamentos.')}</div>`;
      return;
    }

    $('#agendamentosCount').textContent = list.length;
    if (!list.length) {
      el.innerHTML = `<div class="dash-empty">Você ainda não tem agendamentos. <a href="index.html#agendamento" style="color:var(--blue);font-weight:600;">Agendar uma visita →</a></div>`;
      return;
    }

    el.innerHTML = list.map(a => `
      <div class="dash-row" data-id="${a.id}" data-protocolo="${escHtml(a.protocolo || '')}" data-status="${a.status}">
        <div>
          <div class="dash-row__main">${escHtml(a.tipoAparelho)} — ${escHtml(a.endereco)}</div>
          <div class="dash-row__sub">📅 ${formatarDataHora(a.dataHora)} ${a.protocolo ? '· Protocolo: ' + escHtml(a.protocolo) : ''}</div>
          <div class="dash-row__actions" style="margin-top:8px;display:flex;gap:8px;flex-wrap:wrap;"></div>
        </div>
        <span class="dash-badge ${STATUS_BADGE[a.status] || 'dash-badge--pendente'}">${STATUS_LABEL[a.status] || a.status}</span>
      </div>
    `).join('');

    /* acoes por linha: editar/cancelar (se elegivel) / avaliar (se concluido) */
    list.forEach(a => {
      const row = el.querySelector(`.dash-row[data-id="${a.id}"] .dash-row__actions`);
      if (!row) return;

      if (a.elegivelParaAlteracao) {
        const btnEditar = document.createElement('button');
        btnEditar.className = 'btn btn--outline btn--sm';
        btnEditar.type = 'button';
        btnEditar.textContent = '✏️ Editar';
        btnEditar.addEventListener('click', () => abrirEdicaoAgendamento(a, row));
        row.appendChild(btnEditar);

        const btnCancelar = document.createElement('button');
        btnCancelar.className = 'btn btn--outline btn--sm';
        btnCancelar.type = 'button';
        btnCancelar.textContent = 'Cancelar agendamento';
        btnCancelar.addEventListener('click', () => cancelarAgendamento(a.id, btnCancelar));
        row.appendChild(btnCancelar);
      }

      if (a.status === 'CONCLUIDO') {
        const btnAvaliar = document.createElement('button');
        btnAvaliar.className = 'btn btn--outline btn--sm';
        btnAvaliar.type = 'button';
        btnAvaliar.textContent = '⭐ Avaliar atendimento';
        btnAvaliar.addEventListener('click', () => abrirAvaliacao(a, btnAvaliar));
        row.appendChild(btnAvaliar);
      }
    });
  }

  /* YYYY-MM-DDTHH:mm a partir de um ISO com segundos — formato aceito por <input type="datetime-local"> */
  function toDatetimeLocalValue(iso) {
    if (!iso) return '';
    const d = new Date(iso);
    if (isNaN(d)) return '';
    const pad = n => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }

  function abrirEdicaoAgendamento(agendamento, actionsRow) {
    const jaAberto = actionsRow.querySelector('.edicao-agendamento-form');
    if (jaAberto) { jaAberto.remove(); return; }

    const form = document.createElement('div');
    form.className = 'edicao-agendamento-form';
    form.style.cssText = 'margin-top:10px;padding:12px;border:1px solid var(--border);border-radius:10px;width:100%;';
    form.innerHTML = `
      <div class="profile-form__grid" style="margin-bottom:8px;">
        <input type="text" class="input" id="eAparelho-${agendamento.id}" placeholder="Tipo de aparelho" value="${escHtml(agendamento.tipoAparelho)}" />
        <input type="datetime-local" class="input" id="eData-${agendamento.id}" value="${toDatetimeLocalValue(agendamento.dataHora)}" />
        <input type="text" class="input" id="eBairro-${agendamento.id}" placeholder="Bairro" value="${escHtml(agendamento.bairro)}" />
        <input type="text" class="input" id="eEndereco-${agendamento.id}" placeholder="Endereço completo" value="${escHtml(agendamento.endereco)}" />
      </div>
      <textarea class="input" rows="2" placeholder="Descrição do problema (opcional)" id="eDefeito-${agendamento.id}" style="margin-bottom:8px;">${escHtml(agendamento.descricaoProblema || '')}</textarea>
      <p class="edicao-erro" style="color:#dc2626;font-size:.8rem;display:none;margin-bottom:8px;"></p>
      <div style="display:flex;gap:8px;">
        <button class="btn btn--primary btn--sm" type="button" id="eSalvar-${agendamento.id}">Salvar alterações</button>
        <button class="btn btn--outline btn--sm" type="button" id="eCancelar-${agendamento.id}">Fechar</button>
      </div>`;
    actionsRow.appendChild(form);

    form.querySelector(`#eCancelar-${agendamento.id}`).addEventListener('click', () => form.remove());
    form.querySelector(`#eSalvar-${agendamento.id}`).addEventListener('click', async () => {
      const erroEl = form.querySelector('.edicao-erro');
      erroEl.style.display = 'none';
      const dataVal = form.querySelector(`#eData-${agendamento.id}`).value;
      if (!dataVal) { erroEl.textContent = 'Selecione data e horário.'; erroEl.style.display = 'block'; return; }

      try {
        await DitecAPI.atualizarAgendamento(agendamento.id, {
          tipoAparelho: form.querySelector(`#eAparelho-${agendamento.id}`).value.trim(),
          descricaoProblema: form.querySelector(`#eDefeito-${agendamento.id}`).value.trim() || null,
          bairro: form.querySelector(`#eBairro-${agendamento.id}`).value.trim(),
          endereco: form.querySelector(`#eEndereco-${agendamento.id}`).value.trim(),
          dataHora: dataVal + ':00',
        });
        form.remove();
        renderAgendamentos();
      } catch (err) {
        erroEl.textContent = err.message || 'Não foi possível salvar. Verifique o horário escolhido.';
        erroEl.style.display = 'block';
      }
    });
  }

  function formatarDataHora(iso) {
    if (!iso) return '';
    const d = new Date(iso);
    if (isNaN(d)) return iso;
    return d.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
  }

  async function cancelarAgendamento(id, btn) {
    if (!confirm('Tem certeza que deseja cancelar este agendamento?')) return;
    btn.disabled = true;
    try {
      await DitecAPI.cancelarAgendamento(id);
      renderAgendamentos();
    } catch (err) {
      alert(err.message || 'Não foi possível cancelar. Tente novamente.');
      btn.disabled = false;
    }
  }

  async function abrirAvaliacao(agendamento, btn) {
    const container = btn.closest('.dash-row__actions');
    btn.remove();

    /* precisamos do ID interno da OS pra chamar /avaliacao — isso vem só da
       listagem autenticada (minhasOS), nunca do rastreamento público (que,
       por segurança, não expõe IDs internos nem exige login). */
    let os;
    try {
      const minhasOS = await DitecAPI.minhasOS();
      os = minhasOS.find(o => o.protocolo === agendamento.protocolo);
      if (!os) throw new Error('Ordem de serviço não encontrada.');
    } catch (err) {
      alert(err.message || 'Não foi possível abrir a avaliação agora.');
      return;
    }

    const form = document.createElement('div');
    form.style.cssText = 'margin-top:6px;padding:12px;border:1px solid var(--border);border-radius:10px;width:100%;';
    form.innerHTML = `
      <div style="display:flex;gap:4px;margin-bottom:8px;" id="starsPick-${os.id}"></div>
      <textarea class="input" rows="2" placeholder="Comentário (opcional)" id="comentario-${os.id}" style="margin-bottom:8px;"></textarea>
      <button class="btn btn--primary btn--sm" type="button" id="enviarAvaliacao-${os.id}">Enviar avaliação</button>
    `;
    container.appendChild(form);

    let notaEscolhida = 0;
    const starsWrap = form.querySelector(`#starsPick-${os.id}`);
    for (let i = 1; i <= 5; i++) {
      const s = document.createElement('button');
      s.type = 'button';
      s.textContent = '☆';
      s.style.cssText = 'font-size:1.3rem;background:none;border:none;cursor:pointer;color:#f59e0b;';
      s.addEventListener('click', () => {
        notaEscolhida = i;
        [...starsWrap.children].forEach((el, idx) => { el.textContent = idx < i ? '★' : '☆'; });
      });
      starsWrap.appendChild(s);
    }

    form.querySelector(`#enviarAvaliacao-${os.id}`).addEventListener('click', async () => {
      if (!notaEscolhida) { alert('Escolha uma nota de 1 a 5 estrelas.'); return; }
      const comentario = form.querySelector(`#comentario-${os.id}`).value.trim();
      try {
        await DitecAPI.avaliarOS(os.id, { nota: notaEscolhida, comentario: comentario || null });
        form.outerHTML = `<p style="font-size:.85rem;color:var(--text-light);">✅ Avaliação enviada. Obrigado!</p>`;
      } catch (err) {
        alert(err.message || 'Não foi possível enviar a avaliação.');
      }
    });
  }

  /* ---- Auth (login/cadastro quando deslogado) ---- */
  maskTel($('#gCadTel'));
  $('#gTabLogin').addEventListener('click', () => showGuestTab('login'));
  $('#gTabCadastro').addEventListener('click', () => showGuestTab('cadastro'));
  function showGuestTab(which) {
    const isLogin = which === 'login';
    $('#gTabLogin').classList.toggle('active', isLogin);
    $('#gTabCadastro').classList.toggle('active', !isLogin);
    $('#gLoginForm').hidden = !isLogin;
    $('#gCadastroForm').hidden = isLogin;
  }
  function formError(form, msg) {
    const old = form.querySelector('.modal-error'); if (old) old.remove();
    const el = document.createElement('p');
    el.className = 'modal-error';
    el.textContent = '⚠️ ' + msg;
    form.insertBefore(el, form.firstChild);
  }

  $('#gLoginForm').addEventListener('submit', async e => {
    e.preventDefault();
    const email = $('#gLoginEmail').value.trim().toLowerCase();
    const senha = $('#gLoginSenha').value;
    const btn = e.target.querySelector('button[type="submit"]');
    btn.disabled = true;
    try {
      await DitecAPI.login(email, senha);
      await render();
    } catch (err) {
      formError(e.target, err.message || 'E-mail ou senha incorretos.');
    } finally {
      btn.disabled = false;
    }
  });

  $('#gCadastroForm').addEventListener('submit', async e => {
    e.preventDefault();
    const nome = $('#gCadNome').value.trim();
    const email = $('#gCadEmail').value.trim().toLowerCase();
    const tel = $('#gCadTel').value.trim();
    const senha = $('#gCadSenha').value;
    const senha2 = $('#gCadSenha2').value;
    if (!nome || !email || !tel || !senha) return formError(e.target, 'Preencha todos os campos.');
    if (senha.length < 6) return formError(e.target, 'A senha deve ter ao menos 6 caracteres.');
    if (senha !== senha2) return formError(e.target, 'As senhas não coincidem.');

    const btn = e.target.querySelector('button[type="submit"]');
    btn.disabled = true;
    try {
      await DitecAPI.registrar({ nome, email, telefone: tel, senha });
      await render();
    } catch (err) {
      formError(e.target, err.message || 'Não foi possível criar sua conta.');
    } finally {
      btn.disabled = false;
    }
  });

  /* ---- Editar dados ---- */
  maskTel($('#dTel'));
  maskCep($('#dCep'));
  $('#editDataBtn').addEventListener('click', () => {
    ['#dNome', '#dTel', '#dEndereco', '#dBairro', '#dCidade', '#dUf', '#dCep'].forEach(id => $(id).disabled = false);
    $('#saveDataBtn').hidden = false;
  });
  $('#dataForm').addEventListener('submit', async e => {
    e.preventDefault();
    const btn = $('#saveDataBtn');
    btn.disabled = true;
    try {
      await DitecAPI.atualizarPerfil({
        nome: $('#dNome').value.trim(),
        telefone: $('#dTel').value.trim(),
        endereco: $('#dEndereco').value.trim(),
        bairro: $('#dBairro').value.trim(),
        cidade: $('#dCidade').value.trim(),
        uf: $('#dUf').value.trim().toUpperCase(),
        cep: $('#dCep').value.trim(),
      });
      ['#dNome', '#dTel', '#dEndereco', '#dBairro', '#dCidade', '#dUf', '#dCep'].forEach(id => $(id).disabled = true);
      btn.hidden = true;
      await render();
    } catch (err) {
      alert(err.message || 'Não foi possível salvar. Tente novamente.');
    } finally {
      btn.disabled = false;
    }
  });

  /* ---- Rastreamento de OS ---- */
  $('#osInputConta').addEventListener('keypress', e => { if (e.key === 'Enter') $('#osSearchConta').click(); });
  $('#osSearchConta').addEventListener('click', async () => {
    const val = ($('#osInputConta').value || '').trim().toUpperCase();
    const res = $('#osResultConta');
    if (!val) return;
    res.innerHTML = `<p style="font-size:.85rem;color:var(--text-light);padding:10px 0;">Buscando...</p>`;
    try {
      const os = await DitecAPI.rastrear(val);
      res.innerHTML = `<div class="dash-row"><div><div class="dash-row__main">${escHtml(os.protocolo)}</div><div class="dash-row__sub">${escHtml(os.tipoAparelho || '')}</div></div><span class="dash-badge ${STATUS_BADGE[os.status] || 'dash-badge--pendente'}">${STATUS_LABEL[os.status] || os.status}</span></div>`;
    } catch (err) {
      res.innerHTML = `<p style="font-size:.85rem;color:var(--text-light);padding:10px 0;">${escHtml(err.message || 'OS não encontrada.')}</p>`;
    }
  });

  /* ---- Logout ---- */
  logoutBtn.addEventListener('click', () => {
    DitecAPI.logout();
    render();
  });

  render();
})();
