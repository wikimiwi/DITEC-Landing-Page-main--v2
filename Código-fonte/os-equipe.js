/* ============================================================
   DITEC — os-equipe.js
   Painel de detalhes/ações de uma Ordem de Serviço para a EQUIPE
   (técnico e administrador). Usado por tecnico.js e admin.js.

   Mostra os dados completos, o indicador de etapas, o histórico
   (incluindo anotações internas) e os formulários de:
   mudança de status (somente transições válidas devolvidas pela API),
   diagnóstico/orçamento, previsão, observação, finalização e — para o
   admin — atribuição de técnico e cancelamento.
   ============================================================ */
(function (global) {
  'use strict';
  const O = global.DitecOS;
  const esc = O.esc;

  const EM_ANDAMENTO = ['EM_DIAGNOSTICO', 'AGUARDANDO_APROVACAO', 'AGUARDANDO_PECAS', 'EM_MANUTENCAO', 'EM_TESTES'];
  const hojeISO = () => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  };

  /**
   * Renderiza o painel dentro de `container`.
   * opts: { isAdmin: bool, tecnicos: [{id,nome,status}], onChange: fn(os) }
   */
  async function abrir(container, osId, opts = {}) {
    container.innerHTML = '<div class="dash-empty">Carregando detalhes...</div>';
    let os;
    try {
      os = await DitecAPI.buscarOSPorId(osId);
    } catch (err) {
      container.innerHTML = `<div class="dash-empty">${esc(err.message || 'Não foi possível carregar a OS.')}</div>`;
      return;
    }
    desenhar(container, os, opts);
  }

  function desenhar(container, os, opts) {
    const transicoes = (os.transicoesPermitidas || []).filter(s => s !== 'CONCLUIDO' && (opts.isAdmin || s !== 'CANCELADO'));
    const podeFinalizar = (os.transicoesPermitidas || []).includes('CONCLUIDO');
    const podeCancelar = opts.isAdmin && (os.transicoesPermitidas || []).includes('CANCELADO');
    const emAndamento = EM_ANDAMENTO.includes(os.status);
    const naoConcluida = os.status === 'AGENDADO' || emAndamento;
    const id = os.id;

    const botoesStatus = transicoes.filter(s => s !== 'CANCELADO').map(s =>
      `<button type="button" class="btn btn--primary btn--sm" data-acao="status" data-status="${esc(s)}">→ ${esc(O.statusInfo(s).label)}</button>`).join('');

    const blocoStatus = (botoesStatus || podeFinalizar) ? `
      <div class="os-form">
        <label class="form-label" for="obs-${id}">Avançar etapa <span style="font-weight:400;color:var(--text-light);">(observação opcional, visível ao cliente)</span></label>
        <input type="text" class="input" id="obs-${id}" maxlength="500" placeholder="Ex.: Técnico a caminho / peça encomendada" />
        <div class="os-actions">${botoesStatus}
          ${podeFinalizar ? `<button type="button" class="btn btn--primary btn--sm" data-acao="abrir-finalizar">✅ Finalizar serviço</button>` : ''}
        </div>
        ${os.status === 'AGENDADO' && os.agendamentoStatus === 'PENDENTE'
          ? '<p class="os-empty">⚠️ O agendamento ainda está pendente — o administrador precisa confirmá-lo antes do início.</p>' : ''}
        ${os.status === 'EM_DIAGNOSTICO' && !os.diagnostico ? '<p class="os-empty">Registre o diagnóstico abaixo para poder avançar.</p>' : ''}
      </div>` : '';

    const blocoFinalizar = podeFinalizar ? `
      <form class="os-form" data-form="finalizar" hidden>
        <strong>Finalizar serviço</strong>
        <div class="os-form__row">
          <div class="form-group"><label class="form-label" for="fVal-${id}">Valor cobrado (R$)</label>
            <input type="number" step="0.01" min="0" class="input" id="fVal-${id}" required value="${os.valorOrcamento != null ? esc(os.valorOrcamento) : ''}" /></div>
          <div class="form-group"><label class="form-label" for="fPag-${id}">Forma de pagamento</label>
            <select class="input" id="fPag-${id}">
              <option value="pix">Pix (5% de desconto)</option><option value="dinheiro">Dinheiro (5% de desconto)</option>
              <option value="cartao">Cartão</option><option value="boleto">Boleto</option>
            </select></div>
        </div>
        <div class="form-group"><label class="form-label" for="fDesc-${id}">Serviço realizado (obrigatório)</label>
          <textarea class="input" rows="2" id="fDesc-${id}" maxlength="2000" required></textarea></div>
        <div class="os-form__row">
          <div class="form-group"><label class="form-label" for="fPecas-${id}">Peças utilizadas</label><input type="text" class="input" id="fPecas-${id}" maxlength="2000" /></div>
          <div class="form-group"><label class="form-label" for="fNf-${id}">Nº do comprovante/nota (opcional)</label><input type="text" class="input" id="fNf-${id}" maxlength="50" /></div>
        </div>
        <div class="os-actions"><button type="submit" class="btn btn--primary btn--sm">Concluir OS</button></div>
      </form>` : '';

    const blocoDiagnostico = emAndamento ? `
      <form class="os-form" data-form="diagnostico">
        <strong>Diagnóstico e orçamento</strong>
        <textarea class="input" rows="3" id="dTxt-${id}" maxlength="4000" required placeholder="Descreva o defeito encontrado">${esc(os.diagnostico || '')}</textarea>
        <div class="os-form__row">
          <div class="form-group"><label class="form-label" for="dVal-${id}">Orçamento (R$)</label>
            <input type="number" step="0.01" min="0" class="input" id="dVal-${id}" value="${os.valorOrcamento != null ? esc(os.valorOrcamento) : ''}" ${os.status === 'AGUARDANDO_APROVACAO' ? 'disabled' : ''} /></div>
          <div class="form-group"><label class="form-label" for="dPrev-${id}">Previsão de conclusão</label>
            <input type="date" class="input" id="dPrev-${id}" min="${hojeISO()}" value="${esc(os.previsaoConclusao || '')}" /></div>
        </div>
        <div class="os-actions"><button type="submit" class="btn btn--outline btn--sm">Salvar diagnóstico</button></div>
      </form>` : '';

    const blocoPrevisao = (naoConcluida && !emAndamento) ? `
      <form class="os-form" data-form="previsao">
        <strong>Previsão de conclusão</strong>
        <div class="os-form__row">
          <input type="date" class="input" id="pData-${id}" min="${hojeISO()}" value="${esc(os.previsaoConclusao || '')}" required aria-label="Data prevista" />
          <button type="submit" class="btn btn--outline btn--sm">Salvar previsão</button>
        </div>
      </form>` : '';

    const blocoObs = `
      <form class="os-form" data-form="observacao">
        <strong>Registrar observação</strong>
        <textarea class="input" rows="2" id="oTxt-${id}" maxlength="500" required placeholder="Observação técnica ou de atendimento"></textarea>
        <label style="font-size:.82rem;display:flex;gap:6px;align-items:center;"><input type="checkbox" id="oInt-${id}" /> Anotação interna (não aparece para o cliente)</label>
        <div class="os-actions"><button type="submit" class="btn btn--outline btn--sm">Adicionar ao histórico</button></div>
      </form>`;

    const tecnicosAtivos = (opts.tecnicos || []).filter(t => t.status === 'ATIVO');
    const blocoAdmin = opts.isAdmin && naoConcluida ? `
      <div class="os-form">
        <strong>Administração</strong>
        <div class="os-form__row">
          <select class="input" id="aTec-${id}" aria-label="Técnico responsável">
            <option value="">— técnico responsável —</option>
            ${tecnicosAtivos.map(t => `<option value="${esc(t.id)}" ${t.id === os.tecnicoId ? 'selected' : ''}>${esc(t.nome)}</option>`).join('')}
          </select>
          <button type="button" class="btn btn--outline btn--sm" data-acao="atribuir">Atribuir técnico</button>
        </div>
        ${podeCancelar ? `<div class="os-form__row">
          <input type="text" class="input" id="aMot-${id}" maxlength="500" placeholder="Motivo do cancelamento (obrigatório)" />
          <button type="button" class="btn btn--outline btn--sm" data-acao="cancelar" style="color:#b91c1c;border-color:#fca5a5;">Cancelar OS</button>
        </div>` : ''}
      </div>` : '';

    container.innerHTML = `<div class="os-panel">
      <div class="os-header">
        <div><div class="os-number">${esc(os.protocolo)}${os.prioridadeGas ? ' <span title="Aparelho a gás: prioridade">🔥</span>' : ''}</div>
          <div class="os-sub">${esc(os.tipoAparelho || '')}</div></div>
        ${O.badge(os.status)}
      </div>
      ${O.stepper(os)}
      <p class="modal-error" data-msg hidden></p>
      ${blocoStatus}${blocoFinalizar}${blocoDiagnostico}${blocoPrevisao}${blocoObs}${blocoAdmin}
      ${O.infoGrid(os, { completo: true })}
      <section><h4 class="os-section-title">Histórico</h4>${O.timeline(os.timeline, { mostrarInternos: true })}</section>
    </div>`;

    ligarEventos(container, os, opts);
  }

  function ligarEventos(container, os, opts) {
    const id = os.id;
    const $c = s => container.querySelector(s);
    const msg = $c('[data-msg]');

    async function executar(botao, acao) {
      msg.hidden = true;
      if (botao) botao.disabled = true;
      try {
        const atualizada = await acao();
        desenhar(container, atualizada, opts);
        if (typeof opts.onChange === 'function') opts.onChange(atualizada);
      } catch (err) {
        msg.textContent = '⚠️ ' + (err.message || 'Não foi possível concluir a ação.');
        msg.hidden = false;
        msg.scrollIntoView({ behavior: 'smooth', block: 'center' });
        if (botao) botao.disabled = false;
      }
    }

    container.querySelectorAll('[data-acao="status"]').forEach(btn => btn.addEventListener('click', () => {
      const obs = ($c(`#obs-${id}`)?.value || '').trim();
      executar(btn, () => DitecAPI.atualizarStatusOS(id, { status: btn.dataset.status, observacao: obs || null }));
    }));

    $c('[data-acao="abrir-finalizar"]')?.addEventListener('click', () => {
      const f = $c('[data-form="finalizar"]');
      f.hidden = !f.hidden;
      if (!f.hidden) $c(`#fVal-${id}`).focus();
    });

    $c('[data-form="finalizar"]')?.addEventListener('submit', e => {
      e.preventDefault();
      const valor = parseFloat($c(`#fVal-${id}`).value);
      const descricaoFinal = $c(`#fDesc-${id}`).value.trim();
      if (isNaN(valor) || valor < 0) { msg.textContent = '⚠️ Informe o valor cobrado.'; msg.hidden = false; return; }
      if (!descricaoFinal) { msg.textContent = '⚠️ Descreva o serviço realizado.'; msg.hidden = false; return; }
      executar(e.submitter, () => DitecAPI.finalizarOS(id, {
        valor, formaPagamento: $c(`#fPag-${id}`).value, descricaoFinal,
        pecasUtilizadas: $c(`#fPecas-${id}`).value.trim() || null,
        notaFiscalNumero: $c(`#fNf-${id}`).value.trim() || null,
      }));
    });

    $c('[data-form="diagnostico"]')?.addEventListener('submit', e => {
      e.preventDefault();
      const diagnostico = $c(`#dTxt-${id}`).value.trim();
      if (!diagnostico) { msg.textContent = '⚠️ Descreva o diagnóstico.'; msg.hidden = false; return; }
      const valEl = $c(`#dVal-${id}`);
      const val = valEl.disabled || valEl.value === '' ? null : parseFloat(valEl.value);
      executar(e.submitter, () => DitecAPI.diagnosticoOS(id, {
        diagnostico, valorOrcamento: val, previsaoConclusao: $c(`#dPrev-${id}`).value || null,
      }));
    });

    $c('[data-form="previsao"]')?.addEventListener('submit', e => {
      e.preventDefault();
      const data = $c(`#pData-${id}`).value;
      if (!data) return;
      executar(e.submitter, () => DitecAPI.previsaoOS(id, { previsaoConclusao: data }));
    });

    $c('[data-form="observacao"]')?.addEventListener('submit', e => {
      e.preventDefault();
      const texto = $c(`#oTxt-${id}`).value.trim();
      if (!texto) return;
      executar(e.submitter, () => DitecAPI.observacaoOS(id, { texto, visivelCliente: !$c(`#oInt-${id}`).checked }));
    });

    $c('[data-acao="atribuir"]')?.addEventListener('click', e => {
      const tecnicoId = Number($c(`#aTec-${id}`).value);
      if (!tecnicoId) { msg.textContent = '⚠️ Escolha um técnico.'; msg.hidden = false; return; }
      executar(e.currentTarget, () => DitecAPI.admin.atribuirTecnicoOS(id, tecnicoId));
    });

    $c('[data-acao="cancelar"]')?.addEventListener('click', e => {
      const motivo = $c(`#aMot-${id}`).value.trim();
      if (!motivo) { msg.textContent = '⚠️ Informe o motivo do cancelamento.'; msg.hidden = false; return; }
      if (!global.confirm('Cancelar esta OS? Esta ação não pode ser desfeita.')) return;
      executar(e.currentTarget, () => DitecAPI.atualizarStatusOS(id, { status: 'CANCELADO', observacao: motivo }));
    });
  }

  global.DitecOSEquipe = { abrir, EM_ANDAMENTO };
})(window);
