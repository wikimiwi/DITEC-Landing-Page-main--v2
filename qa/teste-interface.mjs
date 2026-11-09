// Testes de interface (Playwright) contra o site + API locais. Dados ficticios.
// Uso: npm run interface
import { chromium } from 'playwright';

const BASE = process.env.DITEC_FRONT_URL || 'http://127.0.0.1:5500';
let falhas = 0;
const ok = (c, m) => { console.log((c ? 'PASS ' : 'FAIL ') + m); if (!c) falhas++; };

const b = await chromium.launch();
for (const vp of [{ width: 1280, height: 900 }, { width: 375, height: 812 }]) {
  console.log(`--- viewport ${vp.width}px`);
  const ctx = await b.newContext({ viewport: vp });
  const p = await ctx.newPage();
  const erros = [];
  p.on('pageerror', e => erros.push(e.message));
  // respostas 4xx esperadas da API (ex.: rastreio com telefone errado) nao contam como erro
  p.on('console', m => { if (m.type() === 'error' && !m.text().startsWith('Failed to load resource')) erros.push(m.text()); });
  p.on('response', r => { if (r.status() >= 400 && !r.url().includes('/api/ordens-servico/rastreio')) erros.push(r.status() + ' ' + r.url()); });
  await p.goto(BASE + '/index.html', { waitUntil: 'networkidle' });

  // links internos: todo href="#id" aponta para um elemento existente
  const quebrados = await p.$$eval('a[href^="#"]', as => as.map(a => a.getAttribute('href'))
    .filter(h => h.length > 1 && !document.getElementById(h.slice(1))));
  ok(quebrados.length === 0, `links internos válidos ${quebrados.join(',')}`);
  ok(await p.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), 'sem rolagem horizontal');

  // aviso de cookies
  await p.waitForSelector('#cookieBanner:not(.hidden)', { timeout: 4000 }).catch(() => {});
  await p.click('#cookieReject');
  ok(await p.$eval('#cookieBanner', e => e.classList.contains('hidden')), 'aviso de cookies fecha');

  // agendamento pelo formulario + "Fazer novo agendamento"
  await p.click('#calNext');
  await p.waitForTimeout(200);
  const dias = await p.$$('.cal-day--available');
  await dias[Math.floor(Math.random() * dias.length)].click();
  await p.waitForSelector('.timeslot:not([disabled])');
  const slots = await p.$$('.timeslot:not([disabled])');
  await slots[Math.floor(Math.random() * slots.length)].click();
  await p.fill('#sNome', 'Teste Interface');
  await p.fill('#sTel', '11955557777');
  if ((await p.$eval('#sAparelho', e => e.tagName)) === 'SELECT') await p.selectOption('#sAparelho', { index: 1 });
  else await p.fill('#sAparelho', 'Geladeira');
  await p.fill('#sBairro', 'Moema');
  await p.fill('#sEndereco', 'Rua Teste, 1');
  await p.fill('#sDefeito', 'Nao gela');
  await p.evaluate(() => { window.open = () => null; });
  await p.click('#scheduleSubmit');
  await p.waitForSelector('#scheduleSuccess', { state: 'visible', timeout: 8000 }).catch(() => {});
  const textoSucesso = (await p.textContent('#scheduleSuccess')) || '';
  const achado = textoSucesso.match(/DITEC-\d{4}-\d{6}/);
  const protocolo = achado ? achado[0] : '';
  ok(!!protocolo, `agendamento pelo site gera protocolo (${protocolo})`);
  await p.waitForTimeout(1200);
  const pular = await p.$('#completeProfileSkip');
  if (pular && await pular.isVisible()) await pular.click();
  await p.click('#scheduleAgain');
  ok(await p.$eval('#scheduleForm', e => getComputedStyle(e).display !== 'none'), 'botão "Fazer novo agendamento" volta ao formulário');

  // rastreamento do protocolo recem-criado
  await p.fill('#osInput', protocolo);
  await p.fill('#osTelefone', '11955557777');
  await p.click('#osSearch');
  await p.waitForSelector('.os-history', { timeout: 8000 }).catch(() => {});
  ok(/Aguardando avaliação/.test(await p.textContent('#trackingResult')), 'rastreio do novo protocolo mostra "Aguardando avaliação"');
  await p.fill('#osTelefone', '11900000000');
  await p.click('#osSearch');
  await p.waitForSelector('.os-msg__title');
  ok(/não encontrada/i.test(await p.textContent('#trackingResult')), 'telefone errado: mensagem clara de OS não encontrada');

  // chatbot
  await p.click('#chatbotTrigger');
  await p.fill('#chatbotInput', 'Qual a garantia?');
  await p.press('#chatbotInput', 'Enter');
  await p.waitForFunction(() => document.querySelectorAll('.chat-msg--bot').length >= 2 && !document.querySelector('#chatTyping'),
    null, { timeout: 25000 }).catch(() => {});
  const bolhas = await p.$$eval('.chat-msg--bot .chat-msg__bubble', bs => bs.map(x => x.innerText));
  const resp = bolhas[bolhas.length - 1] || '';
  ok(/90 dias|garantia/i.test(resp), `chatbot responde ("${resp.slice(0, 60).replace(/\n/g, ' ')}...")`);
  ok(!(await p.$eval('#chatbotInput', e => e.disabled)), 'campo do chat liberado após a resposta');
  await p.press('#chatbotInput', 'Enter');
  ok((await p.$$('.chat-msg--user')).length === 1, 'mensagem vazia não é enviada');

  ok(erros.length === 0, `sem erros de JavaScript no console ${erros.join(' | ').slice(0, 200)}`);
  await ctx.close();
}
await b.close();
console.log(falhas ? `${falhas} falha(s)` : 'todos os testes de interface passaram');
