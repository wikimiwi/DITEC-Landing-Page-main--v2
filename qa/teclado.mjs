// Verificacoes complementares (nao cobertas pelo axe): ordem de titulos, link
// "pular para o conteudo", foco visivel ao navegar com Tab e uso do calendario,
// do rastreamento e do chatbot somente pelo teclado. Uso: npm run teclado
import { chromium } from 'playwright';
const BASE = process.env.DITEC_FRONT_URL || 'http://127.0.0.1:5500';
const b = await chromium.launch();
const ctx = await b.newContext({ viewport: { width: 1280, height: 900 } });
const p = await ctx.newPage();
await p.goto(BASE + '/index.html', { waitUntil: 'networkidle' });
let falhas = 0; const ok = (c, m) => { console.log((c ? 'PASS ' : 'FAIL ') + m); if (!c) falhas++; };

const titulos = await p.$$eval('h1,h2,h3,h4,h5,h6', hs => hs.map(h => +h.tagName[1]));
ok(titulos.filter(n => n === 1).length === 1, `exatamente um <h1> (encontrados: ${titulos.filter(n => n === 1).length})`);
const saltos = titulos.map((n, i) => i && n - titulos[i - 1] > 1 ? `h${titulos[i - 1]}→h${n}` : null).filter(Boolean);
ok(saltos.length === 0, `sem saltos de nível nos títulos ${saltos.length ? '(' + saltos.join(', ') + ')' : ''}`);

// Tab: os primeiros 25 elementos focados precisam ter indicador de foco visível
await p.keyboard.press('Tab');
const primeiro = await p.evaluate(() => ({ txt: document.activeElement.textContent.trim().slice(0, 40), href: document.activeElement.getAttribute('href') }));
ok(/conte/i.test(primeiro.txt) || primeiro.href === '#main' || primeiro.href === '#conteudo', `1º Tab vai para "pular para o conteúdo" (foi: "${primeiro.txt}")`);
let semFoco = [];
for (let i = 0; i < 25; i++) {
  const r = await p.evaluate(() => {
    const e = document.activeElement; const cs = getComputedStyle(e);
    const visivel = (cs.outlineStyle !== 'none' && parseFloat(cs.outlineWidth) > 0) || (cs.boxShadow && cs.boxShadow !== 'none');
    return { visivel, id: e.id || e.className || e.tagName, txt: (e.getAttribute('aria-label') || e.textContent || '').trim().slice(0, 25) };
  });
  if (!r.visivel) semFoco.push(r.txt || r.id);
  await p.keyboard.press('Tab');
}
ok(semFoco.length === 0, `foco visível em todos os 25 primeiros elementos ${semFoco.length ? '(sem foco: ' + semFoco.join(' | ') + ')' : ''}`);

// Calendario por teclado
await p.focus('.cal-day--available');
await p.keyboard.press('Enter');
await p.waitForSelector('.timeslot:not([disabled])');
ok(true, 'calendário: dia escolhido com Enter e horários carregados');
await p.focus('.timeslot:not([disabled])'); await p.keyboard.press('Enter');
ok(await p.$eval('#scheduleSelected, .schedule__selected, [id*=Sel]', e => getComputedStyle(e).display !== 'none').catch(() => false), 'calendário: horário escolhido com Enter');

// Rastreamento por teclado (Enter envia)
await p.focus('#osInput'); await p.keyboard.type('DITEC-2026-000001');
await p.focus('#osTelefone'); await p.keyboard.type('11977772222'); await p.keyboard.press('Enter');
await p.waitForSelector('.os-history', { timeout: 8000 }).catch(() => null);
ok(!!(await p.$('.os-history')), 'rastreamento enviado com Enter mostra o histórico');
ok(await p.$eval('#trackingResult', e => e.getAttribute('aria-live') === 'polite'), 'resultado do rastreamento anunciado (aria-live)');

// Chatbot por teclado
await p.focus('#chatbotTrigger'); await p.keyboard.press('Enter');
await p.waitForTimeout(500);
ok(await p.evaluate(() => document.activeElement.id === 'chatbotInput'), 'chat abre com Enter e o foco vai para o campo de mensagem');
await p.keyboard.press('Escape');
ok(await p.evaluate(() => document.activeElement.id === 'chatbotTrigger' && document.querySelector('#chatbotTrigger').getAttribute('aria-expanded') === 'false'), 'Esc fecha o chat e devolve o foco ao botão');
await b.close();
console.log(falhas ? `${falhas} verificação(ões) falharam` : 'todas as verificações passaram');
