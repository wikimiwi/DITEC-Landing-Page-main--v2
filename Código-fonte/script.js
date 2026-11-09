/* ============================================================
   DITEC Assistência — script.js  v6 (Backend Java real + MySQL)
   Chatbot com IA via proxy Spring Boot (Google Gemini API)
   Autenticação, agendamento e rastreamento de OS via API REST —
   ver dashboard-shared.js (window.DitecAPI) e /backend.
   ============================================================ */
   'use strict';

   /* ─────────────────────────────────────────────────────────────
      CONFIGURAÇÃO — edite aqui antes de publicar
      ───────────────────────────────────────────────────────────── */
   const CONFIG = {
   
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
   
   /* O prompt de sistema do chatbot fica SOMENTE no servidor (ChatService.SYSTEM_PROMPT). */
   
/* ─────────────────────────────────────────────────────────────
   DADOS DE ORDENS DE SERVIÇO — agora vêm da API real
   (GET /api/ordens-servico/protocolo/{protocolo}, ver DitecAPI.rastrear
   em dashboard-shared.js). O mock OS_DATA que existia aqui foi removido.
   ───────────────────────────────────────────────────────────── */
   
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
      SESSÃO DO CLIENTE — agora via DitecAPI (JWT real emitido pelo
      backend, ver dashboard-shared.js). getCurrentUser() é mantida
      com o mesmo nome/assinatura de antes para não precisar reescrever
      cada trecho do arquivo que já depende dela.
      ───────────────────────────────────────────────────────────── */
   function getCurrentUser() {
     return (typeof DitecAPI !== 'undefined' && DitecAPI.isLoggedIn()) ? DitecAPI.getUser() : null;
   }

   function showToast(msg, type) {
     const el = document.createElement('div');
     el.className = 'ditec-toast' + (type === 'error' ? ' ditec-toast--error' : ' ditec-toast--success');
     el.textContent = msg;
     document.body.appendChild(el);
     requestAnimationFrame(() => el.classList.add('show'));
     setTimeout(() => { el.classList.remove('show'); setTimeout(() => el.remove(), 300); }, 3400);
   }

   function maskTelefoneInput(input) {
     if (!input) return;
     input.addEventListener('input', () => {
       let v = onlyDigits(input.value).slice(0, 11);
       if (v.length > 6) v = `(${v.slice(0,2)}) ${v.slice(2,7)}-${v.slice(7)}`;
       else if (v.length > 2) v = `(${v.slice(0,2)}) ${v.slice(2)}`;
       input.value = v;
     });
   }
   
   /* ─────────────────────────────────────────────────────────────
      INTEGRAÇÃO COM BACKEND JAVA (proxy da Google Gemini API)
      ───────────────────────────────────────────────────────────── */

   /* sessionId estável durante a aba (não é PII) — usado pelo admin
      para agrupar o historico de uma conversa no relatorio do chatbot. */
   function getChatSessionId() {
     let id = sessionStorage.getItem('ditec_chat_session');
     if (!id) {
       id = (window.crypto && crypto.randomUUID) ? crypto.randomUUID() : 'sess-' + Date.now() + '-' + Math.random().toString(16).slice(2);
       sessionStorage.setItem('ditec_chat_session', id);
     }
     return id;
   }

   async function perguntarIA(userMessage, history = []) {
     const recentHistory = history.slice(-4);

     /* O prompt de sistema e o modelo ficam SOMENTE no servidor (ChatService):
        o navegador envia apenas a conversa. */
     const messages = [
       ...recentHistory,
       { role: 'user', content: userMessage },
     ];

     const data = await DitecAPI.chat({
       messages,
       max_tokens: 250,
       temperature: 0.6,
       stream: false,
       sessionId: getChatSessionId(),
     });

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
     /* enquanto o aviso aparece, os botões flutuantes (chat/WhatsApp) sobem para não ficarem cobertos */
     const ajustar = visivel => document.documentElement.style.setProperty('--cookie-offset', visivel ? banner.offsetHeight + 'px' : '0px');
     setTimeout(() => { banner.classList.remove('hidden'); ajustar(true); }, 1500);
     window.addEventListener('resize', () => { if (!banner.classList.contains('hidden')) ajustar(true); });
     function fechar(valor) {
       localStorage.setItem('ditec_cookies', valor);
       banner.classList.add('hidden');
       ajustar(false);
     }
     $('#cookieAccept').addEventListener('click', () => fechar('all'));
     $('#cookieReject').addEventListener('click', () => fechar('essential'));
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
   
   (function initAuth() {
     const authBtn = $('#authBtn'), authBtnText = $('#authBtnText');
     const modal = $('#authModal'), closeBtn = $('#authModalClose');
     const tabLogin = $('#tabLoginBtn'), tabCadastro = $('#tabCadastroBtn');
     const loginForm = $('#loginForm'), cadastroForm = $('#cadastroForm');
     const goCad = $('#goToCadastro'), goLogin = $('#goToLogin');
     if (!authBtn || !modal) return;

     maskTelefoneInput($('#cadTel'));

     function refreshAuthBtn() {
       const user = getCurrentUser();
       if (user) {
         authBtnText.textContent = user.nome.split(' ')[0];
         authBtn.onclick = () => {
           const tipo = String(user.tipo || '').toUpperCase();

           if (tipo === 'ADMINISTRADOR') {
             window.location.href = 'admin.html';
           } else if (tipo === 'TECNICO') {
             window.location.href = 'tecnico.html';
           } else {
             window.location.href = 'conta.html';
           }
         };
       } else {
         authBtnText.textContent = 'Entrar';
         authBtn.onclick = openModal;
       }
     }

     function openModal() {
       modal.hidden = false;
       requestAnimationFrame(() => modal.classList.add('active'));
       document.body.classList.add('modal-open');
     }
     function closeModal() {
       modal.classList.remove('active');
       document.body.classList.remove('modal-open');
       setTimeout(() => { modal.hidden = true; }, 250);
     }
     closeBtn.addEventListener('click', closeModal);
     modal.addEventListener('click', e => { if (e.target === modal) closeModal(); });
     document.addEventListener('keydown', e => { if (e.key === 'Escape' && modal.classList.contains('active')) closeModal(); });

     function showTab(which) {
       const isLogin = which === 'login';
       tabLogin.classList.toggle('active', isLogin);
       tabCadastro.classList.toggle('active', !isLogin);
       tabLogin.setAttribute('aria-selected', String(isLogin));
       tabCadastro.setAttribute('aria-selected', String(!isLogin));
       loginForm.hidden = !isLogin;
       cadastroForm.hidden = isLogin;
       clearFormError(loginForm); clearFormError(cadastroForm);
     }
     tabLogin.addEventListener('click', () => showTab('login'));
     tabCadastro.addEventListener('click', () => showTab('cadastro'));
     goCad.addEventListener('click', () => showTab('cadastro'));
     goLogin.addEventListener('click', () => showTab('login'));

     function clearFormError(form) {
       const el = form.querySelector('.modal-error'); if (el) el.remove();
     }
     function showFormError(form, msg) {
       clearFormError(form);
       const el = document.createElement('p');
       el.className = 'modal-error';
       el.textContent = '⚠️ ' + msg;
       form.insertBefore(el, form.firstChild);
     }

     loginForm.addEventListener('submit', async e => {
       e.preventDefault();
       const email = ($('#loginEmail').value || '').trim().toLowerCase();
       const senha = $('#loginSenha').value || '';
       if (!email || !senha) return showFormError(loginForm, 'Preencha e-mail e senha.');

       const submitBtn = loginForm.querySelector('button[type="submit"]');
       if (submitBtn) submitBtn.disabled = true;
       try {
         const data = await DitecAPI.login(email, senha);

         let destino;

         switch (data.tipo) {
           case 'ADMINISTRADOR':
             destino = 'admin.html';
             break;

           case 'TECNICO':
             destino = 'tecnico.html';
             break;

           case 'CLIENTE':
             destino = 'conta.html';
             break;

           default:
             throw new Error('Tipo de usuário inválido.');
         }

         closeModal();
         refreshAuthBtn();
         showToast(`Bem-vindo(a) de volta, ${data.nome.split(' ')[0]}!`);

         setTimeout(() => {
           window.location.href = destino;
         }, 600);
       } catch (err) {
         showFormError(loginForm, err.message || 'E-mail ou senha incorretos.');
       } finally {
         if (submitBtn) submitBtn.disabled = false;
       }
     });

     cadastroForm.addEventListener('submit', async e => {
       e.preventDefault();
       const nome  = ($('#cadNome').value || '').trim();
       const email = ($('#cadEmail').value || '').trim().toLowerCase();
       const tel   = ($('#cadTel').value || '').trim();
       const senha = $('#cadSenha').value || '';
       const senha2= $('#cadSenha2').value || '';
       if (!nome || !email || !tel || !senha) return showFormError(cadastroForm, 'Preencha todos os campos.');
       if (senha.length < 8) return showFormError(cadastroForm, 'A senha deve ter ao menos 8 caracteres.');
       if (senha !== senha2) return showFormError(cadastroForm, 'As senhas não coincidem.');

       const submitBtn = cadastroForm.querySelector('button[type="submit"]');
       if (submitBtn) submitBtn.disabled = true;
       try {
         await DitecAPI.registrar({ nome, email, telefone: tel, senha });
         closeModal();
         refreshAuthBtn();
         showToast('Conta criada com sucesso!');
         const data = await DitecAPI.login(email, senha);

         let destino;

         switch (data.tipo) {
           case 'ADMINISTRADOR':
             destino = 'admin.html';
             break;

           case 'TECNICO':
             destino = 'tecnico.html';
             break;

           case 'CLIENTE':
             destino = 'conta.html';
             break;

           default:
             throw new Error('Tipo de usuário inválido.');
         }

         closeModal();
         refreshAuthBtn();
         showToast(`Bem-vindo(a) de volta, ${data.nome.split(' ')[0]}!`);

         setTimeout(() => {
           window.location.href = destino;
         }, 600);
       } catch (err) {
         showFormError(cadastroForm, err.message || 'Não foi possível criar sua conta. Tente novamente.');
       } finally {
         if (submitBtn) submitBtn.disabled = false;
       }
     });

     refreshAuthBtn();
     /* exposto para o pop-up de completar cadastro do agendamento */
     window.ditecRefreshAuthBtn = refreshAuthBtn;
     window.ditecOpenAuthModal = openModal;
   })();

   (function initCompleteProfile() {
     const modal = $('#completeProfileModal');
     if (!modal) return;
     const closeBtn = $('#completeProfileClose'), skipBtn = $('#completeProfileSkip');
     const form = $('#completeProfileForm');

     maskTelefoneInput($('#cpTel'));

     function open(prefill) {
       if (getCurrentUser()) return; /* já tem conta — não precisa completar cadastro */
       prefill = prefill || {};
       $('#cpNome').value = prefill.nome || '';
       $('#cpTel').value  = prefill.telefone || '';
       modal.hidden = false;
       requestAnimationFrame(() => modal.classList.add('active'));
       document.body.classList.add('modal-open');
     }
     function close() {
       modal.classList.remove('active');
       document.body.classList.remove('modal-open');
       setTimeout(() => { modal.hidden = true; }, 250);
     }
     closeBtn.addEventListener('click', close);
     skipBtn.addEventListener('click', close);
     modal.addEventListener('click', e => { if (e.target === modal) close(); });

     form.addEventListener('submit', async e => {
       e.preventDefault();
       const nome  = ($('#cpNome').value || '').trim();
       const email = ($('#cpEmail').value || '').trim().toLowerCase();
       const tel   = ($('#cpTel').value || '').trim();
       const senha = $('#cpSenha').value || '';
       const old = form.querySelector('.modal-error'); if (old) old.remove();
       if (!nome || !email || !tel || !senha) return showErr('Preencha todos os campos.');
       if (senha.length < 8) return showErr('A senha deve ter ao menos 8 caracteres.');

       const submitBtn = form.querySelector('button[type="submit"]');
       if (submitBtn) submitBtn.disabled = true;
       try {
         /* o backend vincula automaticamente o agendamento mais recente
            sem conta que tenha o MESMO telefone informado aqui. */
         await DitecAPI.registrar({ nome, email, telefone: tel, senha });

         if (typeof window.ditecRefreshAuthBtn === 'function') window.ditecRefreshAuthBtn();
         close();
         showToast('Cadastro completo! Você já pode acompanhar tudo pela Área do Cliente. 🎉');
       } catch (err) {
         showErr(err.message || 'Não foi possível concluir o cadastro. Tente novamente.');
       } finally {
         if (submitBtn) submitBtn.disabled = false;
       }

       function showErr(m) {
         const el = document.createElement('p');
         el.className = 'modal-error';
         el.textContent = '⚠️ ' + m;
         form.insertBefore(el, form.firstChild);
       }
     });

     window.ditecOpenCompleteProfile = open;
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
   
     const MESES = ['Janeiro','Fevereiro','Março','Abril','Maio','Junho','Julho','Agosto','Setembro','Outubro','Novembro','Dezembro'];

     /* YYYY-MM-DD local (evita o shift de fuso horário do toISOString()) */
     function toISODateLocal(date) {
       return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
     }
   
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
         /* cada dia é um botão acessível (teclado: Enter ou Espaço) */
         d.setAttribute('role', 'button');
         d.setAttribute('aria-label', `${day} de ${MESES[viewMonth]}` + (isPast || isSun ? ' — indisponível' : ''));
         if (isSel) d.setAttribute('aria-pressed', 'true');
         if (isPast || isSun) {
           d.setAttribute('aria-disabled', 'true');
         } else {
           d.tabIndex = 0;
           d.addEventListener('click', () => selectDate(date, day));
           d.addEventListener('keydown', e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); selectDate(date, day); } });
         }
         calDays.appendChild(d);
       }
     }
   
     async function selectDate(date, day) {
       selectedDate = date; selectedTime = null;
       render();
       tsTitle.textContent = `Horários — ${day} de ${MESES[viewMonth]}`;
       schedSel.style.display = 'none';
       tsGrid.innerHTML = '<p style="grid-column:1/-1;color:#6b7280;font-size:.85rem;padding:8px 0;">Carregando horários...</p>';
       timeslots.style.display = 'block';

       let disponibilidade;
       try {
         disponibilidade = await DitecAPI.disponibilidade(toISODateLocal(date));
       } catch (err) {
         tsGrid.innerHTML = `<p style="grid-column:1/-1;color:#dc2626;font-size:.85rem;padding:8px 0;">${escHtml(err.message || 'Não foi possível carregar os horários.')}</p>`;
         return;
       }

       tsGrid.innerHTML = '';
       if (!disponibilidade.diaAtendido) {
         tsGrid.innerHTML = '<p style="grid-column:1/-1;color:#6b7280;font-size:.85rem;padding:8px 0;">Não atendemos nesta data.</p>';
         return;
       }
       const ocupadosHoje = disponibilidade.horariosOcupados || [];
       const todosHorarios = [...(disponibilidade.horariosDisponiveis || []), ...ocupadosHoje].sort();
       todosHorarios.forEach(h => {
         const btn = document.createElement('button');
         const ocupado = ocupadosHoje.includes(h);
         btn.className = 'timeslot' + (ocupado ? ' timeslot--taken' : '');
         btn.textContent = h; btn.type = 'button';
         btn.setAttribute('aria-label', h + (ocupado ? ' — indisponível' : ''));
         if (ocupado) { btn.disabled = true; }
         else { btn.addEventListener('click', () => selectTime(h, day, MESES[viewMonth], btn)); }
         tsGrid.appendChild(btn);
       });
     }
   
     function selectTime(time, day, monthName, btnEl) {
       selectedTime = time;
       $$('.timeslot').forEach(b => b.classList.remove('timeslot--selected'));
       if (btnEl) btnEl.classList.add('timeslot--selected');
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
       /* "Fazer novo agendamento": antes era um onclick inline, bloqueado pela CSP (o botão não funcionava) */
       const btnNovo = $('#scheduleAgain');
       btnNovo && btnNovo.addEventListener('click', () => {
         schedSuccess.style.display = 'none';
         schedForm.reset();
         schedForm.style.display = 'block';
         selectedDate = null; selectedTime = null; /* força escolher de novo: o horário recém-agendado fica ocupado */
         schedSel.style.display = 'none';
         if (tsGrid) tsGrid.innerHTML = '';
         render();
         schedForm.querySelector('input,select,textarea')?.focus();
       });
       schedForm.addEventListener('submit', async e => {
         e.preventDefault();
         const nome    = ($('#sNome').value || '').trim();
         const tel     = ($('#sTel').value || '').trim();
         const ap      = ($('#sAparelho').value || '').trim();
         const bairro  = ($('#sBairro').value || '').trim();
         const end     = ($('#sEndereco').value || '').trim();
         const defeito = ($('#sDefeito') ? $('#sDefeito').value || '' : '').trim();
         if (!nome || !tel || !ap || !bairro || !end) { alert('Preencha todos os campos obrigatórios.'); return; }
         if (!selectedDate || !selectedTime) { alert('Selecione uma data e horário no calendário.'); return; }

         const dataHoraISO = `${toISODateLocal(selectedDate)}T${selectedTime}:00`;
         const dateStr = `${selectedDate.getDate()} de ${MESES[selectedDate.getMonth()]} às ${selectedTime}`;

         const submitBtn = $('#scheduleSubmit');
         if (submitBtn) submitBtn.disabled = true;
         try {
           const agendamento = await DitecAPI.criarAgendamento({
             nome, telefone: tel, tipoAparelho: ap, descricaoProblema: defeito || null,
             bairro, endereco: end, dataHora: dataHoraISO,
           });

           schedSuccessText.innerHTML = `${escHtml(nome)}, seu agendamento para ${escHtml(ap)} em ${escHtml(end)} está confirmado para ${escHtml(dateStr)}.<br>` +
             `<strong>Protocolo: ${escHtml(agendamento.protocolo)}</strong> — guarde para rastrear sua OS.`;
           schedForm.style.display = 'none';
           schedSuccess.style.display = 'block';

           const msg = encodeURIComponent(
             `Olá! Gostaria de confirmar meu agendamento:\n\nNome: ${nome}\nAparelho: ${ap}\nEndereço: ${end}, ${bairro}\nData: ${dateStr}\nProtocolo: ${agendamento.protocolo}\nTelefone: ${tel}`
           );
           setTimeout(() => window.open(`https://wa.me/${CONFIG.whatsappNumber}?text=${msg}`, '_blank'), 1200);

           /* logo após o agendamento, convida a pessoa a completar o cadastro */
           if (!getCurrentUser() && typeof window.ditecOpenCompleteProfile === 'function') {
             setTimeout(() => window.ditecOpenCompleteProfile({ nome, telefone: tel }), 900);
           }
         } catch (err) {
           alert(err.message || 'Não foi possível concluir o agendamento. Tente novamente.');
         } finally {
           if (submitBtn) submitBtn.disabled = false;
         }
       });
     }
   })();
   
   (function initTracking() {
     const form = $('#osForm'), inp = $('#osInput'), tel = $('#osTelefone'), res = $('#trackingResult');
     if (!form) return;
     maskTelefoneInput(tel);
     const O = window.DitecOS;

     form.addEventListener('submit', e => { e.preventDefault(); search(); });

     function mensagem(icone, titulo, texto) {
       res.style.display = 'block';
       res.innerHTML = `<div class="os-msg"><p class="os-msg__icon" aria-hidden="true">${icone}</p>
         <p class="os-msg__title">${O.esc(titulo)}</p><p class="os-msg__text">${O.esc(texto)}</p></div>`;
     }

     async function search() {
       const protocolo = (inp.value || '').trim().toUpperCase();
       const telefone = (tel.value || '').trim();
       if (!/^DITEC-\d{4}-\d{6}$/.test(protocolo)) {
         mensagem('⚠️', 'Protocolo inválido', 'Use o formato DITEC-AAAA-NNNNNN (ex.: DITEC-2026-000001).');
         inp.focus(); return;
       }
       if (onlyDigits(telefone).length < 10) {
         mensagem('⚠️', 'Informe o telefone', 'Digite o telefone com DDD usado no agendamento.');
         tel.focus(); return;
       }
       inp.value = protocolo;
       const btn = $('#osSearch');
       btn.disabled = true;
       res.style.display = 'block';
       res.setAttribute('aria-busy', 'true');
       res.innerHTML = `<div class="os-msg"><span class="os-spinner" aria-hidden="true"></span><p class="os-msg__text">Buscando sua OS...</p></div>`;

       let os;
       try {
         os = await DitecAPI.rastrear(protocolo, telefone);
       } catch (err) {
         if (err.status === 404) mensagem('🔍', 'OS não encontrada', err.message || 'Confira o protocolo e o telefone.');
         else if (err.status === 429) mensagem('⏳', 'Muitas consultas', err.message);
         else mensagem('⚠️', 'Não foi possível consultar agora', err.message || 'Tente novamente em instantes.');
         return;
       } finally {
         btn.disabled = false;
         res.removeAttribute('aria-busy');
       }

       const orcamentoPendente = os.status === 'AGUARDANDO_APROVACAO'
         ? `<div class="os-alert">💬 O orçamento de <strong>${O.fmtMoeda(os.valorOrcamento)}</strong> aguarda sua aprovação.
            Entre na <a href="conta.html">Minha Conta</a> para aprovar ou recusar, ou fale conosco pelo WhatsApp.
            Lembrando: o diagnóstico é gratuito.</div>` : '';

       res.innerHTML = `<article class="os-status">
         <header class="os-header">
           <div>
             <div class="os-number">${O.esc(os.protocolo)}</div>
             <div class="os-sub">${O.esc(os.clienteNomeAbreviado || '')} · ${O.esc(os.tipoAparelho || '')}</div>
           </div>
           ${O.badge(os.status, 'os-badge')}
         </header>
         ${O.stepper(os)}
         ${orcamentoPendente}
         ${O.infoGrid(os)}
         <section><h3 class="os-section-title">Histórico de movimentações</h3>${O.timeline(os.timeline)}</section>
         <p class="os-foot">Consulta feita em ${O.esc(new Date().toLocaleString('pt-BR'))}. Os dados vêm diretamente do sistema da DITEC.</p>
       </article>`;
     }
   })();
   
   /* ─────────────────────────────────────────────────────────────
      CHATBOT COM IA — Google Gemini via Spring Boot
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
       trigger.setAttribute('aria-label', 'Fechar assistente virtual DITEC');
       if (!greeted) { greeted = true; botGreet(); }
       setTimeout(() => inpEl.focus(), 350);
     }
     function close() {
       isOpen = false;
       chatbot.classList.remove('open');
       win.classList.remove('visible');
       trigger.setAttribute('aria-expanded', 'false');
       trigger.setAttribute('aria-label', 'Abrir assistente virtual DITEC');
     }
     trigger.addEventListener('click', () => isOpen ? close() : open());
     /* Esc fecha o chat e devolve o foco ao botão (navegação por teclado) */
     document.addEventListener('keydown', e => { if (e.key === 'Escape' && isOpen) { close(); trigger.focus(); } });
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
       chamarIA(m);
     }
   
     sendBtn.addEventListener('click', () => send());
     inpEl.addEventListener('keypress', e => e.key === 'Enter' && !e.shiftKey && send());
   
     async function chamarIA(userMessage) {
       isLoading = true;
       inpEl.disabled = true;
       sendBtn.disabled = true;
   
       showTyping(true);
   
       try {
         const resposta = await perguntarIA(userMessage, history);
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
         console.warn('[DITEC Chatbot] Falha ao obter resposta:', err && err.status, err && err.message);

         const msgErro = err && err.status === 429
           ? '😕 Recebi muitas mensagens em pouco tempo. Aguarde alguns minutos e tente de novo — ou fale conosco pelo WhatsApp.'
           : '😕 Não consegui responder agora. Tente novamente em instantes ou fale com a equipe pelo WhatsApp (11) 99999-9999.';

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
         ? `<div style="font-size:.72rem;color:#6b7280;margin-top:6px;">⏳ Pensando na resposta...</div>`
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
     console.log(`%c API: ${DitecAPI.BASE_URL}`, `color:#16a34a`);
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
