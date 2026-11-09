// Auditoria de acessibilidade (axe-core, regras WCAG 2.0/2.1 A e AA) com Playwright.
// Cobre paginas publicas, paineis LOGADOS (contas de demonstracao do seed local) e os
// fluxos de rastreamento e agendamento. Uso: npm run a11y
import { chromium } from 'playwright';
import { AxeBuilder } from '@axe-core/playwright';
import fs from 'node:fs';

const BASE = process.env.DITEC_FRONT_URL || 'http://127.0.0.1:5500';
const API = process.env.DITEC_API_URL || 'http://localhost:8080/api';
const TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];

async function token(email, senha) {
  const r = await fetch(API + '/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, senha }) });
  const d = await r.json();
  return { token: d.token, user: { nome: d.nome, email: d.email, tipo: d.tipo } };
}

const browser = await chromium.launch();
const resultados = [];
async function auditar(nome, url, { sessao, viewport = { width: 1280, height: 900 }, preparar } = {}) {
  const ctx = await browser.newContext({ viewport });
  const page = await ctx.newPage();
  if (sessao) {
    await page.addInitScript(s => { localStorage.setItem('ditec_token', s.token); localStorage.setItem('ditec_user', JSON.stringify(s.user)); }, sessao);
  }
  await page.goto(BASE + url, { waitUntil: 'networkidle' });
  if (preparar) await preparar(page);
  await page.waitForTimeout(600);
  const r = await new AxeBuilder({ page }).withTags(TAGS).analyze();
  resultados.push({ nome, url, viewport: viewport.width, violacoes: r.violations.map(v => ({ id: v.id, impacto: v.impact, ajuda: v.help, ocorrencias: v.nodes.length, alvos: v.nodes.slice(0, 4).map(n => n.target.join(' ')) })) });
  await ctx.close();
}

const admin = await token('admin@ditec.com.br', 'admin123');
const tecnico = await token('tecnico@ditec.com.br', 'tecnico123');
const cliente = await token('cliente@ditec.com.br', 'cliente123');

for (const vp of [{ width: 1280, height: 900 }, { width: 375, height: 812 }]) {
  await auditar('Página inicial', '/index.html', { viewport: vp, preparar: async p => { await p.evaluate(() => document.querySelectorAll('.fade-up,[data-reveal]').forEach(e => e.classList.add('visible', 'revealed'))); } });
  await auditar('Rastreamento com resultado', '/index.html#rastreamento', { viewport: vp, preparar: async p => {
    await p.fill('#osInput', 'DITEC-2026-000001'); await p.fill('#osTelefone', '11977772222'); await p.click('#osSearch'); await p.waitForSelector('.os-history');
  } });
  await auditar('Rastreamento com erro', '/index.html#rastreamento', { viewport: vp, preparar: async p => {
    await p.fill('#osInput', 'DITEC-2026-999999'); await p.fill('#osTelefone', '11977772222'); await p.click('#osSearch'); await p.waitForSelector('.os-msg__title');
  } });
  await auditar('Chatbot aberto', '/index.html', { viewport: vp, preparar: async p => { await p.click('#chatbotTrigger'); } });
  await auditar('Modal de login/cadastro', '/index.html', { viewport: vp, preparar: async p => { await p.evaluate(() => { const b = [...document.querySelectorAll('button,a')].find(x => /Entrar/.test(x.textContent)); b && b.click(); }); } });
  await auditar('Login cliente', '/conta.html', { viewport: vp });
  await auditar('Área do cliente (logado)', '/conta.html', { viewport: vp, sessao: cliente, preparar: async p => { await p.waitForSelector('#osListConta details'); await p.evaluate(() => document.querySelectorAll('#osListConta details').forEach(d => d.open = true)); } });
  await auditar('Login técnico', '/tecnico.html', { viewport: vp });
  await auditar('Painel técnico (logado, OS aberta)', '/tecnico.html', { viewport: vp, sessao: tecnico, preparar: async p => { await p.waitForSelector('[data-detalhe]'); await p.click('[data-detalhe]'); await p.waitForSelector('.os-panel'); } });
  await auditar('Login admin', '/admin.html', { viewport: vp });
  await auditar('Painel admin (logado, OS aberta)', '/admin.html', { viewport: vp, sessao: admin, preparar: async p => {
    await p.click('[data-panel="osPanel"]'); await p.waitForSelector('[data-detalhe-os]'); await p.click('[data-detalhe-os]'); await p.waitForSelector('.os-panel');
  } });
}
await browser.close();
fs.mkdirSync('relatorios', { recursive: true });
fs.writeFileSync('relatorios/axe-resultado.json', JSON.stringify(resultados, null, 2));
let total = 0;
for (const r of resultados) {
  total += r.violacoes.length;
  console.log(`${String(r.viewport).padEnd(5)} ${r.nome}: ${r.violacoes.length ? r.violacoes.map(v => `${v.id}[${v.impacto}]x${v.ocorrencias}`).join(', ') : 'sem violações'}`);
}
console.log('TOTAL de regras violadas (somando páginas):', total);
