/* ============================================================
   DITEC Assistência — script.js  v5 (Com Backend em Java)
   Chatbot com IA: Mistral 7B via Proxy Spring Boot
   ============================================================ */
   'use strict';

   /* ─────────────────────────────────────────────────────────────
      CONFIGURAÇÃO — edite aqui antes de publicar
      ───────────────────────────────────────────────────────────── */
   const CONFIG = {
     /* Modelo — compatível com API gratuita HF */
     HF_MODEL: 'Qwen/Qwen2.5-72B-Instruct',
   
     /* Número do WhatsApp da DITEC (DDI+DDD+número) */
     whatsappNumber: '5511999999999',
   
     /* Bairros atendidos para o verificador de área */
     bairrosAtendidos: [
       'moema','vila mariana','brooklin','campo belo','jabaquara','saude','saúde',
       'ipiranga','cursino','sacoma','sacomã','santo amaro','santo andre','sao bernardo',
       'são bernardo','diadema','pinheiros','itaim bibi','jardins','jardim paulista',
       'consolacao','consolação','higienopolis','higienópolis','perdizes','lapa',
       'butanta','butantã','morumbi','vila madalena','barra funda','santana','tucuruvi',
       'vila guilherme','tremembe','tremembé','casa verde','limao','limão','penha',
       'tatuape','tatuapé','sao miguel','são miguel','itaquera','mooca','belem','belém',
       'carrao','carrão','vila formosa','se','sé','republica','república','bela vista',
       'liberdade','cambuci','centro','bras','brás','bom retiro','osasco','carapicuiba',
       'carapicuíba','barueri','jandira','cotia','embu','taboao','taboão',
     ],
   
     /* Faixas de CEP atendidas */
     cepRanges: [
       { start: '01000000', end: '05999999' },
       { start: '06000000', end: '08499999' },
       { start: '09000000', end: '09999999' },
     ],
   };
   
   /* ─────────────────────────────────────────────────────────────
      SYSTEM PROMPT — instrução enviada ao Mistral antes de cada
      conversa. Define quem é o bot, o que pode/não pode responder.
      ───────────────────────────────────────────────────────────── */
   const SYSTEM_PROMPT = `Você é o assistente virtual da DITEC Assistência Técnica, uma empresa especializada em conserto de eletrodomésticos em São Paulo e Grande SP.
   
   INFORMAÇÕES DA EMPRESA:
   - Nome: DITEC Assistência Técnica
   - Telefone: (11) 99999-9999
   - WhatsApp: (11) 99999-9999
   - Horário: Segunda a Sábado, das 7h às 20h
   - Área: São Paulo Capital e Grande SP
   
   SERVIÇOS QUE REALIZAMOS:
   - Geladeiras e freezers
   - Máquinas de lavar e secadoras
   - Fogões e fornos
   - Micro-ondas
   - Ar condicionado
   
   DIFERENCIAIS:
   - Diagnóstico gratuito
   - Garantia de 90 dias em peças e mão de obra
   - Atendimento no mesmo dia (sujeito a disponibilidade)
   - Formas de pagamento: Pix, cartão débito/crédito, parcelamento em até 12x, boleto, dinheiro (5% de desconto)
   
   REGRAS DE COMPORTAMENTO:
   1. Responda APENAS sobre a DITEC e eletrodomésticos.
   2. Seja sempre gentil, objetivo e profissional (máximo 3 parágrafos).
   3. Quando pertinente, sugira entrar em contato pelo WhatsApp (11) 99999-9999 para agendar.`;
   
   /* ─────────────────────────────────────────────────────────────
      DADOS DE ORDENS DE SERVIÇO (simulados para demo)
      ───────────────────────────────────────────────────────────── */
   const OS_DATA = {
     'OS-2025-1001': {
       numero: 'OS-2025-1001', cliente: 'Maria Fernanda S.', aparelho: 'Geladeira Brastemp', bairro: 'Moema, SP',
       status: 'concluido', statusLabel: 'Concluído ✅',
       steps: [
         { label: 'Agendamento recebido',  detail: '02/01/2025 às 09:15', done: true },
         { label: 'Técnico a caminho',     detail: '02/01/2025 às 11:30', done: true },
         { label: 'Diagnóstico realizado', detail: 'Compressor com defeito', done: true },
         { label: 'Reparo em andamento',   detail: 'Peça solicitada e instalada', done: true },
         { label: 'Serviço concluído',     detail: '02/01/2025 às 14:45 — Garantia ativa', done: true },
       ],
     },
     'OS-2025-1002': {
       numero: 'OS-2025-1002', cliente: 'Carlos Roberto M.', aparelho: 'Máquina de Lavar LG', bairro: 'Pinheiros, SP',
       status: 'reparo', statusLabel: 'Em Reparo 🔧',
       steps: [
         { label: 'Agendamento recebido',  detail: '15/01/2025 às 10:00', done: true },
         { label: 'Técnico a caminho',     detail: '15/01/2025 às 13:00', done: true },
         { label: 'Diagnóstico realizado', detail: 'Rolamentos e correia com desgaste', done: true },
         { label: 'Reparo em andamento',   detail: 'Peças em instalação — previsão: hoje', done: true, current: true },
         { label: 'Serviço concluído',     detail: 'Aguardando finalização', done: false },
       ],
     },
     'OS-2025-1003': {
       numero: 'OS-2025-1003', cliente: 'Ana Paula T.', aparelho: 'Ar Condicionado Samsung', bairro: 'Vila Mariana, SP',
       status: 'diagnostico', statusLabel: 'Em Diagnóstico 🔍',
       steps: [
         { label: 'Agendamento recebido',  detail: '20/01/2025 às 14:22', done: true },
         { label: 'Técnico a caminho',     detail: '21/01/2025 — Hoje às 09:00', done: true },
         { label: 'Diagnóstico realizado', detail: 'Técnico no local agora', done: false, current: true },
         { label: 'Reparo em andamento',   detail: 'Aguardando aprovação do orçamento', done: false },
         { label: 'Serviço concluído',     detail: 'Pendente', done: false },
       ],
     },
   };
   
   const QUICK_REPLIES_INICIAIS = [
     'Quais serviços vocês fazem?',
     'O diagnóstico é gratuito?',
     'Qual a garantia do serviço?',
     'Como agendar uma visita?',
     'Quais formas de pagamento?',
   ];
   
   /* ─────────────────────────────────────────────────────────────
      UTILITÁRIOS
      ───────────────────────────────────────────────────────────── */
   const $  = (s) => document.querySelector(s);
   const $$ = (s) => document.querySelectorAll(s);
   
   function normalize(str) {
     return str.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim();
   }
   function onlyDigits(s) { return s.replace(/\D/g, ''); }
   function isCepAtendido(cep) {
     const d = onlyDigits(cep);
     if (d.length !== 8) return false;
     return CONFIG.cepRanges.some(({ start, end }) => d >= start && d <= end);
   }
   function isBairroAtendido(b) {
     const n = normalize(b);
     return CONFIG.bairrosAtendidos.some(x => n.includes(normalize(x)) || normalize(x).includes(n));
   }
   function timeStr() {
     return new Date().toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
   }
   function escHtml(s) {
     return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
   }
   function mdToHtml(t) {
     return escHtml(t)
       .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
       .replace(/\*(.*?)\*/g, '<em>$1</em>')
       .replace(/\n/g, '<br>');
   }
   function scrollToSection(id) {
     const el = $(id);
     if (el) {
       const h = $('#header');
       const offset = h ? h.getBoundingClientRect().height + 8 : 78;
       window.scrollTo({ top: el.getBoundingClientRect().top + window.pageYOffset - offset, behavior: 'smooth' });
     }
   }
   
   /* ─────────────────────────────────────────────────────────────
      INTEGRAÇÃO COM BACKEND JAVA (Proxy Hugging Face)
      ───────────────────────────────────────────────────────────── */
   
   async function askMistral(userMessage, history = []) {
     const recentHistory = history.slice(-4);
   
     const systemMsg = [
       'Você é o assistente virtual da DITEC Assistência Técnica em São Paulo.',
       'Responda SOMENTE sobre: conserto de eletrodomésticos, serviços, preços, garantia e agendamento da DITEC.',
       'Diagnóstico gratuito. Garantia 90 dias. Seg-Sab 7h-20h. WhatsApp: (11) 99999-9999.',
       'Pagamento: Pix, cartão, parcelamento 12x, dinheiro (5% desconto).',
       'Responda em português brasileiro. Seja objetivo. Máximo 2 parágrafos curtos.',
     ].join(' ');
   
     const messages = [
       { role: 'system', content: systemMsg },
       ...recentHistory,
       { role: 'user',   content: userMessage },
     ];
   
     /* ── Endpoint apontando para o seu Eclipse/Spring Boot ── */
     const response = await fetch(
       'http://localhost:8080/api/chat',
       {
         method: 'POST',
         headers: {
           'Content-Type': 'application/json',
         },
         body: JSON.stringify({
           model: CONFIG.HF_MODEL,
           messages,
           max_tokens: 250,
           temperature: 0.6,
           stream: false,
         }),
       }
     );
   
     if (!response.ok) {
       throw new Error(`api_erro_${response.status}`);
     }
   
     const data = await response.json();
     const texto = data?.choices?.[0]?.message?.content;
     if (texto && texto.trim()) return texto.trim();
   
     throw new Error('formato_inesperado');
   }
   
   /* ─────────────────────────────────────────────────────────────
      COMPONENTES DE TELA (Cookies, Header, Scroll, etc)
      ───────────────────────────────────────────────────────────── */
   (function initCookies() {
     const banner = $('#cookieBanner');
     if (!banner) return;
     if (localStorage.getItem('ditec_cookies')) { banner.classList.add('hidden'); return; }
     setTimeout(() => banner.classList.remove('hidden'), 1500);
     $('#cookieAccept').addEventListener('click', () => {
       localStorage.setItem('ditec_cookies', 'all');
       banner.classList.add('hidden');
     });
     $('#cookieReject').addEventListener('click', () => {
       localStorage.setItem('ditec_cookies', 'essential');
       banner.classList.add('hidden');
     });
   })();
   
   (function initHeader() {
     const header = $('#header'), hamburger = $('#hamburger'), nav = $('#nav'), links = $$('.nav__link');
     window.addEventListener('scroll', () => {
       header.classList.toggle('scrolled', window.scrollY > 10);
       highlight();
     }, { passive: true });
     hamburger.addEventListener('click', () => {
       const o = hamburger.classList.toggle('open');
       nav.classList.toggle('open', o);
       hamburger.setAttribute('aria-expanded', String(o));
     });
     links.forEach(l => l.addEventListener('click', () => {
       hamburger.classList.remove('open');
       nav.classList.remove('open');
       hamburger.setAttribute('aria-expanded', 'false');
     }));
     function highlight() {
       let cur = '';
       $$('section[id]').forEach(s => { if (s.getBoundingClientRect().top <= 100) cur = s.id; });
       links.forEach(l => l.classList.toggle('active', l.getAttribute('href') === '#' + cur));
     }
   })();
   
   (function initSmoothScroll() {
     function headerHeight() {
       const h = $('#header');
       return h ? h.getBoundingClientRect().height : 70;
     }
     document.querySelectorAll('a[href^="#"]').forEach(function (a) {
       a.addEventListener('click', function (e) {
         const href = a.getAttribute('href');
         if (!href || href === '#') return;
         const target = document.querySelector(href);
         if (!target) return;
         e.preventDefault();
         const top = target.getBoundingClientRect().top + window.pageYOffset - headerHeight() - 8;
         window.scrollTo({ top, behavior: 'smooth' });
       });
     });
   })();
   
   (function initAnimations() {
     const seletores = [
       '.service-card', '.testimonial-card', '.hero__content', '.hero__visual',
       '.section-header', '.contact__info', '.contact__form-wrapper', '.stat-card',
       '.brand-card', '.portfolio-card', '.faq-item', '.team-card',
       '.about__value', '.payment-card',
     ];
     const comDelay = ['.service-card', '.stat-card', '.brand-card', '.payment-card'];
     seletores.forEach(sel => {
       $$(sel).forEach((el, i) => {
         el.classList.add('fade-up');
         if (comDelay.includes(sel)) el.style.transitionDelay = `${i * 60}ms`;
       });
     });
     const obs = new IntersectionObserver(
       entries => entries.forEach(e => { if (e.isIntersecting) { e.target.classList.add('visible'); obs.unobserve(e.target); } }),
       { threshold: 0.1, rootMargin: '0px 0px -40px 0px' }
     );
     $$('.fade-up').forEach(el => obs.observe(el));
   })();
   
   (function initForm() {
     const form = $('#contactForm'), tel = $('#telefone');
     if (!form) return;
     tel && tel.addEventListener('input', () => {
       let v = onlyDigits(tel.value).slice(0, 11);
       if (v.length > 6) v = `(${v.slice(0,2)}) ${v.slice(2,7)}-${v.slice(7)}`;
       else if (v.length > 2) v = `(${v.slice(0,2)}) ${v.slice(2)}`;
       tel.value = v;
     });
     form.addEventListener('submit', e => {
       e.preventDefault();
       const nome    = ($('#nome').value || '').trim();
       const telefone= ($('#telefone').value || '').trim();
       const aparelho= ($('#aparelho').value || '').trim();
       const defeito = ($('#defeito').value || '').trim();
       if (!nome || !telefone || !aparelho || !defeito) return showErr('Preencha todos os campos obrigatórios.');
       if (onlyDigits(telefone).length < 10) return showErr('Informe um telefone válido com DDD.');
       const msg = encodeURIComponent(
         `Olá, meu nome é ${nome}. Preciso de conserto para um(a) ${aparelho} com o seguinte defeito: ${defeito}. Meu telefone é ${telefone}.`
       );
       window.open(`https://wa.me/${CONFIG.whatsappNumber}?text=${msg}`, '_blank');
     });
     function showErr(m) {
       let el = form.querySelector('.form-error');
       if (!el) {
         el = document.createElement('p');
         el.className = 'form-error';
         el.style.cssText = 'color:#dc2626;font-size:.85rem;font-weight:500;background:#fee2e2;padding:10px 14px;border-radius:8px;border:1px solid #fecaca;';
         form.prepend(el);
       }
       el.textContent = '⚠️ ' + m;
       setTimeout(() => el.remove(), 5000);
     }
   })();
   
   (function initArea() {
     const inp = $('#areaInput'), btn = $('#checkAreaBtn'), res = $('#areaResult');
     if (!inp) return;
     btn.addEventListener('click', check);
     inp.addEventListener('keypress', e => e.key === 'Enter' && check());
     inp.addEventListener('input', () => {
       const d = onlyDigits(inp.value);
       if (d.length > 5 && d.length <= 8 && inp.value.match(/^\d/))
         inp.value = d.slice(0, 5) + '-' + d.slice(5, 8);
     });
     function check() {
       const v = inp.value.trim();
       if (!v) { res.style.display = 'none'; return; }
       const ok = onlyDigits(v).length >= 7 ? isCepAtendido(v) : isBairroAtendido(v);
       res.textContent = ok
         ? '✅ Ótima notícia! Atendemos a sua região.'
         : '❌ Infelizmente, ainda não cobrimos essa área. Ligue para verificar.';
       res.className = 'area-checker__result ' + (ok ? 'success' : 'error');
       res.style.display = 'block';
     }
   })();
   
   (function initCalendar() {
     const calDays = $('#calDays'), calMonth = $('#calMonth');
     const calPrev = $('#calPrev'), calNext = $('#calNext');
     const timeslots = $('#timeslots'), tsTitle = $('#timeslotsTitle'), tsGrid = $('#timeslotsGrid');
     const schedForm = $('#scheduleForm'), schedSel = $('#scheduleSelected'), schedSelText = $('#scheduleSelectedText');
     const schedSuccess = $('#scheduleSuccess'), schedSuccessText = $('#scheduleSuccessText');
     if (!calDays) return;
   
     const now = new Date();
     let viewYear = now.getFullYear(), viewMonth = now.getMonth();
     let selectedDate = null, selectedTime = null;
   
     const HORARIOS = ['07:00','08:30','10:00','11:30','13:00','14:30','16:00','17:30','19:00'];
     const OCUPADOS = { '1': ['07:00','10:00'], '3': ['14:30'], '8': ['07:00','08:30','10:00'], '15': ['13:00','14:30','16:00'] };
     const MESES = ['Janeiro','Fevereiro','Março','Abril','Maio','Junho','Julho','Agosto','Setembro','Outubro','Novembro','Dezembro'];
   
     function render() {
       calMonth.textContent = `${MESES[viewMonth]} ${viewYear}`;
       calDays.innerHTML = '';
       const first = new Date(viewYear, viewMonth, 1).getDay();
       const last  = new Date(viewYear, viewMonth + 1, 0).getDate();
       for (let i = 0; i < first; i++) {
         const d = document.createElement('div');
         d.className = 'cal-day cal-day--empty';
         calDays.appendChild(d);
       }
       for (let day = 1; day <= last; day++) {
         const d = document.createElement('div');
         const date   = new Date(viewYear, viewMonth, day);
         const isPast = date < new Date(now.getFullYear(), now.getMonth(), now.getDate());
         const isToday = date.toDateString() === now.toDateString();
         const isSun  = date.getDay() === 0;
         const isSel  = selectedDate && date.toDateString() === selectedDate.toDateString();
         d.className = 'cal-day'
           + (isPast || isSun ? ' cal-day--disabled' : ' cal-day--available')
           + (isToday ? ' cal-day--today' : '')
           + (isSel   ? ' cal-day--selected' : '');
         d.textContent = day;
         d.setAttribute('role', 'gridcell');
         d.setAttribute('aria-label', `${day} de ${MESES[viewMonth]}`);
         if (!isPast && !isSun) {
           d.tabIndex = 0;
           d.addEventListener('click', () => selectDate(date, day));
           d.addEventListener('keypress', e => { if (e.key === 'Enter') selectDate(date, day); });
         }
         calDays.appendChild(d);
       }
     }
   
     function selectDate(date, day) {
       selectedDate = date; selectedTime = null;
       render();
       tsTitle.textContent = `Horários — ${day} de ${MESES[viewMonth]}`;
       tsGrid.innerHTML = '';
       const ocupadosHoje = OCUPADOS[String(day)] || [];
       HORARIOS.forEach(h => {
         const btn = document.createElement('button');
         const ocupado = ocupadosHoje.includes(h);
         btn.className = 'timeslot' + (ocupado ? ' timeslot--taken' : '');
         btn.textContent = h; btn.type = 'button';
         btn.setAttribute('aria-label', h + (ocupado ? ' — indisponível' : ''));
         if (ocupado) { btn.disabled = true; }
         else { btn.addEventListener('click', () => selectTime(h, day, MESES[viewMonth])); }
         tsGrid.appendChild(btn);
       });
       timeslots.style.display = 'block';
     }
   
     function selectTime(time, day, monthName) {
       selectedTime = time;
       $$('.timeslot').forEach(b => b.classList.remove('timeslot--selected'));
       event.target.classList.add('timeslot--selected');
       schedSelText.textContent = `📅 ${day} de ${monthName} às ${time}`;
       schedSel.style.display = 'flex';
     }
   
     calPrev.addEventListener('click', () => { viewMonth--; if (viewMonth < 0) { viewMonth = 11; viewYear--; } render(); });
     calNext.addEventListener('click', () => { viewMonth++; if (viewMonth > 11) { viewMonth = 0; viewYear++; } render(); });
     render();
   
     if (schedForm) {
       const sTel = $('#sTel');
       sTel && sTel.addEventListener('input', () => {
         let v = onlyDigits(sTel.value).slice(0, 11);
         if (v.length > 6) v = `(${v.slice(0,2)}) ${v.slice(2,7)}-${v.slice(7)}`;
         else if (v.length > 2) v = `(${v.slice(0,2)}) ${v.slice(2)}`;
         sTel.value = v;
       });
       schedForm.addEventListener('submit', e => {
         e.preventDefault();
         const nome = ($('#sNome').value || '').trim();
         const tel  = ($('#sTel').value || '').trim();
         const ap   = ($('#sAparelho').value || '').trim();
         const end  = ($('#sEndereco').value || '').trim();
         if (!nome || !tel || !ap || !end) { alert('Preencha todos os campos.'); return; }
         if (!selectedDate || !selectedTime) { alert('Selecione uma data e horário no calendário.'); return; }
         const dateStr = `${selectedDate.getDate()} de ${MESES[selectedDate.getMonth()]} às ${selectedTime}`;
         schedSuccessText.textContent = `${nome}, seu agendamento para ${ap} em ${end} está confirmado para ${dateStr}.`;
         schedForm.style.display = 'none';
         schedSuccess.style.display = 'block';
         const msg = encodeURIComponent(
           `Olá! Gostaria de confirmar meu agendamento:\n\nNome: ${nome}\nAparelho: ${ap}\nEndereço: ${end}\nData: ${dateStr}\nTelefone: ${tel}`
         );
         setTimeout(() => window.open(`https://wa.me/${CONFIG.whatsappNumber}?text=${msg}`, '_blank'), 1200);
       });
     }
   })();
   
   (function initTracking() {
     const inp = $('#osInput'), btn = $('#osSearch'), res = $('#trackingResult');
     if (!btn) return;
     btn.addEventListener('click', search);
     inp.addEventListener('keypress', e => e.key === 'Enter' && search());
   
     function search() {
       const val = (inp.value || '').trim().toUpperCase();
       if (!val) { res.style.display = 'none'; return; }
       const os = OS_DATA[val];
       if (!os) {
         res.innerHTML = `<div style="text-align:center;padding:20px;">
           <p style="font-size:1.5rem;margin-bottom:12px;">🔍</p>
           <p style="font-weight:700;margin-bottom:8px;">OS não encontrada</p>
           <p style="font-size:.88rem;color:#6b7280;">Verifique o número e tente novamente.<br>O número é enviado por WhatsApp no agendamento.</p>
         </div>`;
         res.style.display = 'block';
         return;
       }
       const badgeClass = {
         aguardando: 'os-badge--aguardando', diagnostico: 'os-badge--diagnostico',
         reparo: 'os-badge--reparo', concluido: 'os-badge--concluido',
       }[os.status] || 'os-badge--aguardando';
   
       const stepsHtml = os.steps.map((s, i) => {
         const cls = 'os-step' + (s.current ? ' os-step--current' : s.done ? ' os-step--done' : ' os-step os-step--disabled');
         const icon = s.done && !s.current ? '✓' : String(i + 1);
         return `<div class="${cls}">
           <div class="os-step__dot">${icon}</div>
           <div class="os-step__info">
             <div class="os-step__label">${escHtml(s.label)}</div>
             <div class="os-step__detail">${escHtml(s.detail)}</div>
           </div>
         </div>`;
       }).join('');
   
       res.innerHTML = `<div class="os-status">
         <div class="os-header">
           <div>
             <div class="os-number">${escHtml(os.numero)}</div>
             <div style="font-size:.82rem;color:#6b7280;margin-top:2px;">${escHtml(os.cliente)} · ${escHtml(os.aparelho)}</div>
           </div>
           <span class="os-badge ${badgeClass}">${os.statusLabel}</span>
         </div>
         <div class="os-timeline">${stepsHtml}</div>
       </div>`;
       res.style.display = 'block';
     }
   })();
   
   /* ─────────────────────────────────────────────────────────────
      CHATBOT COM IA — Mistral 7B via Spring Boot
      ───────────────────────────────────────────────────────────── */
   (function initChatbot() {
     const chatbot   = $('#chatbot');
     const trigger   = $('#chatbotTrigger');
     const win       = $('#chatbotWindow');
     const closeBtn  = $('#chatbotClose');
     const msgsEl    = $('#chatbotMessages');
     const quickEl   = $('#chatbotQuickReplies');
     const inpEl     = $('#chatbotInput');
     const sendBtn   = $('#chatbotSend');
     const badge     = $('#chatbotBadge');
     if (!chatbot) return;
   
     let isOpen    = false;
     let greeted   = false;
     let isLoading = false;
   
     const history = [];
   
     function open() {
       isOpen = true;
       chatbot.classList.add('open');
       win.classList.add('visible');
       badge.classList.add('hidden');
       trigger.setAttribute('aria-expanded', 'true');
       if (!greeted) { greeted = true; botGreet(); }
       setTimeout(() => inpEl.focus(), 350);
     }
     function close() {
       isOpen = false;
       chatbot.classList.remove('open');
       win.classList.remove('visible');
       trigger.setAttribute('aria-expanded', 'false');
     }
     trigger.addEventListener('click', () => isOpen ? close() : open());
     closeBtn.addEventListener('click', close);
   
     function botGreet() {
       const msg = 'Olá! 👋 Sou o assistente virtual da **DITEC**, com inteligência artificial. Pode me perguntar qualquer coisa sobre nossos serviços!';
       addBotMsg(msg);
       setTimeout(() => showQR(QUICK_REPLIES_INICIAIS), 400);
     }
   
     function send(text) {
       const m = text || inpEl.value.trim();
       if (!m || isLoading) return;
       inpEl.value = '';
       quickEl.innerHTML = '';
       addUserMsg(m);
       callMistral(m);
     }
   
     sendBtn.addEventListener('click', () => send());
     inpEl.addEventListener('keypress', e => e.key === 'Enter' && !e.shiftKey && send());
   
     async function callMistral(userMessage) {
       isLoading = true;
       inpEl.disabled = true;
       sendBtn.disabled = true;
   
       showTyping(true);
   
       try {
         const resposta = await askMistral(userMessage, history);
         removeTyping();
   
         history.push({ role: 'user',      content: userMessage });
         history.push({ role: 'assistant', content: resposta });
   
         if (history.length > 20) history.splice(0, 2);
   
         addBotMsg(resposta);
   
         const rNorm = normalize(resposta);
         if (rNorm.includes('whatsapp') || rNorm.includes('ligar') || rNorm.includes('contato')) {
           setTimeout(() => showQR(['Chamar no WhatsApp', 'Agendar online']), 300);
         } else if (rNorm.includes('agendar') || rNorm.includes('visita') || rNorm.includes('tecnico')) {
           setTimeout(() => showQR(['Agendar online', 'Chamar no WhatsApp']), 300);
         } else if (rNorm.includes('garantia') || rNorm.includes('peca') || rNorm.includes('preco')) {
           setTimeout(() => showQR(['Como agendar?', 'Diagnóstico é grátis?', 'Chamar no WhatsApp']), 300);
         }
   
       } catch (err) {
         removeTyping();
         console.error('[DITEC Chatbot] Erro na API (verifique se o Spring Boot está rodando):', err.message);
         
         const msgErro = '😕 Não consegui me conectar à IA agora. Verifique se o servidor Java está rodando na porta 8080!';
   
         addBotMsg(msgErro);
         showQR(['Tentar novamente', 'Chamar no WhatsApp']);
   
       } finally {
         isLoading = false;
         inpEl.disabled = false;
         sendBtn.disabled = false;
         inpEl.focus();
       }
     }
   
     function addBotMsg(text) {
       const w = document.createElement('div');
       w.className = 'chat-msg chat-msg--bot';
       w.innerHTML = `
         <div class="chat-msg__avatar" aria-hidden="true">DT</div>
         <div>
           <div class="chat-msg__bubble">${mdToHtml(text)}</div>
           <span class="chat-msg__time">${timeStr()}</span>
         </div>`;
       msgsEl.appendChild(w);
       scrollBottom();
     }
   
     function addUserMsg(text) {
       const w = document.createElement('div');
       w.className = 'chat-msg chat-msg--user';
       w.innerHTML = `
         <div class="chat-msg__avatar" aria-hidden="true">Eu</div>
         <div>
           <div class="chat-msg__bubble">${escHtml(text)}</div>
           <span class="chat-msg__time">${timeStr()}</span>
         </div>`;
       msgsEl.appendChild(w);
       scrollBottom();
     }
   
     function showTyping(comAviso = false) {
       const el = document.createElement('div');
       el.className = 'chat-msg chat-msg--bot';
       el.id = 'chatTyping';
       const aviso = comAviso
         ? `<div style="font-size:.72rem;color:#9ca3af;margin-top:6px;">⏳ Processando via Java...</div>`
         : '';
       el.innerHTML = `
         <div class="chat-msg__avatar" aria-hidden="true">DT</div>
         <div>
           <div class="chat-msg__bubble">
             <div class="chat-typing"><span></span><span></span><span></span></div>
             ${aviso}
           </div>
         </div>`;
       msgsEl.appendChild(el);
       scrollBottom();
     }
   
     function removeTyping() {
       const t = $('#chatTyping');
       if (t) t.remove();
     }
   
     function showQR(list) {
       quickEl.innerHTML = '';
       list.forEach(label => {
         const b = document.createElement('button');
         b.className = 'qr-btn';
         b.textContent = label;
         b.type = 'button';
         b.addEventListener('click', () => {
           quickEl.innerHTML = '';
           if (label === 'Chamar no WhatsApp') {
             window.open(`https://wa.me/${CONFIG.whatsappNumber}?text=Ol%C3%A1!%20Preciso%20de%20assist%C3%AAncia%20t%C3%A9cnica%20DITEC.`, '_blank');
             return;
           }
           if (label === 'Agendar online') {
             scrollToSection('#agendamento');
             close();
             return;
           }
           if (label === 'Rastrear minha OS') {
             scrollToSection('#rastreamento');
             close();
             return;
           }
           if (label === 'Tentar novamente') {
             addBotMsg('Claro! Pode fazer sua pergunta novamente. 😊');
             return;
           }
           send(label);
         });
         quickEl.appendChild(b);
       });
       scrollBottom();
     }
   
     function scrollBottom() {
       setTimeout(() => { msgsEl.scrollTop = msgsEl.scrollHeight; }, 50);
     }
   
     setTimeout(() => { if (!isOpen && badge) badge.classList.remove('hidden'); }, 6000);
   })();
   
   (function initWAFloat() {
     const btn = $('#whatsappFloat');
     if (!btn) return;
     window.addEventListener('scroll', () => {
       btn.style.opacity = window.scrollY > 200 ? '1' : '0.75';
     }, { passive: true });
   })();
   
   document.addEventListener('DOMContentLoaded', () => {
     console.log('%c DITEC v5 — Chatbot com IA + Backend Java ', 'background:#0047AB;color:white;padding:4px 8px;border-radius:4px;font-weight:bold;');
     console.log(`%c Conexão: ✅ Configurado para rodar via localhost:8080`, `color:#16a34a`);
   });
/* ============================================================
   DITEC — animations.js  (movimento premium)
   ============================================================ */

(function() {
  'use strict';

  /* --- Scroll progress bar --- */
  const progressBar = document.getElementById('scrollProgress');
  if (progressBar) {
    window.addEventListener('scroll', () => {
      const scrollTop = window.scrollY;
      const docHeight = document.documentElement.scrollHeight - window.innerHeight;
      progressBar.style.width = (docHeight > 0 ? (scrollTop / docHeight) * 100 : 0) + '%';
    }, { passive: true });
  }

  /* --- Intersection Observer for [data-reveal] --- */
  const revealEls = document.querySelectorAll('[data-reveal]');
  if (revealEls.length) {
    const io = new IntersectionObserver((entries) => {
      entries.forEach(e => {
        if (e.isIntersecting) {
          e.target.classList.add('revealed');
          io.unobserve(e.target);
        }
      });
    }, { threshold: 0.12, rootMargin: '0px 0px -40px 0px' });
    revealEls.forEach(el => io.observe(el));
  }

  /* --- Animated number counters --- */
  function animateCounter(el, target, duration) {
    const start = performance.now();
    const update = (now) => {
      const elapsed = Math.min((now - start) / duration, 1);
      const ease = 1 - Math.pow(1 - elapsed, 3);
      const val = Math.round(ease * target);
      el.textContent = val.toLocaleString('pt-BR') + el.dataset.suffix;
      if (elapsed < 1) requestAnimationFrame(update);
      else { el.classList.remove('counting'); }
    };
    el.classList.add('counting');
    requestAnimationFrame(update);
  }

  const statValues = document.querySelectorAll('.stat-card__value');
  const counterIO = new IntersectionObserver((entries) => {
    entries.forEach(e => {
      if (!e.isIntersecting) return;
      const el = e.target;
      const raw = el.textContent.trim();
      const match = raw.match(/(\d+)/);
      if (!match) return;
      const num = parseInt(match[1]);
      const suffix = raw.replace(match[1], '');
      el.dataset.suffix = suffix;
      el.textContent = '0' + suffix;
      animateCounter(el, num, 1200);
      counterIO.unobserve(el);
    });
  }, { threshold: 0.5 });
  statValues.forEach(el => counterIO.observe(el));

  /* --- Magnetic hover on CTA buttons --- */
  document.querySelectorAll('.btn--primary, .btn--whatsapp').forEach(btn => {
    btn.addEventListener('mousemove', (e) => {
      const r = btn.getBoundingClientRect();
      const x = e.clientX - r.left - r.width / 2;
      const y = e.clientY - r.top - r.height / 2;
      btn.style.transform = `translate(${x * 0.15}px, ${y * 0.2}px) translateY(-1px)`;
    });
    btn.addEventListener('mouseleave', () => {
      btn.style.transform = '';
    });
  });

  /* --- Parallax on hero background shapes --- */
  const heroShapes = document.querySelectorAll('.shape');
  if (heroShapes.length) {
    window.addEventListener('scroll', () => {
      const y = window.scrollY;
      heroShapes[0] && (heroShapes[0].style.transform = `translateY(${y * 0.12}px)`);
      heroShapes[1] && (heroShapes[1].style.transform = `translateY(${y * -0.06}px)`);
      heroShapes[2] && (heroShapes[2].style.transform = `translateY(${y * 0.08}px)`);
    }, { passive: true });
  }

  /* --- Card tilt on mouse move --- */
  document.querySelectorAll('.service-card, .testimonial-card, .portfolio-card').forEach(card => {
    card.addEventListener('mousemove', (e) => {
      const r = card.getBoundingClientRect();
      const x = (e.clientX - r.left) / r.width - 0.5;
      const y = (e.clientY - r.top) / r.height - 0.5;
      card.style.transform = `perspective(600px) rotateY(${x * 6}deg) rotateX(${-y * 5}deg) translateY(-6px)`;
    });
    card.addEventListener('mouseleave', () => {
      card.style.transform = '';
    });
  });

  /* --- Ripple effect on buttons --- */
  document.querySelectorAll('.btn').forEach(btn => {
    btn.addEventListener('click', (e) => {
      const circle = document.createElement('span');
      const r = btn.getBoundingClientRect();
      const size = Math.max(r.width, r.height);
      Object.assign(circle.style, {
        position:'absolute', borderRadius:'50%',
        width: size + 'px', height: size + 'px',
        left: (e.clientX - r.left - size/2) + 'px',
        top: (e.clientY - r.top - size/2) + 'px',
        background:'rgba(255,255,255,0.35)',
        transform:'scale(0)', animation:'ripple .55s ease-out forwards',
        pointerEvents:'none', zIndex:'1'
      });
      if (!btn.style.position || btn.style.position === 'static')
        btn.style.position = 'relative';
      btn.style.overflow = 'hidden';
      btn.appendChild(circle);
      circle.addEventListener('animationend', () => circle.remove());
    });
  });

  /* Inject ripple keyframe once */
  if (!document.getElementById('rippleStyle')) {
    const s = document.createElement('style');
    s.id = 'rippleStyle';
    s.textContent = '@keyframes ripple{to{transform:scale(2.8);opacity:0;}}';
    document.head.appendChild(s);
  }

  /* --- Staggered FAQ items on first view --- */
  document.querySelectorAll('.faq-item').forEach((item, i) => {
    item.style.transitionDelay = `${i * 0.06}s`;
  });

})();
