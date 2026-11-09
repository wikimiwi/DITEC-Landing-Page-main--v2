// Auditoria Lighthouse local das paginas do DITEC (Chromium local em qa/.browsers).
// Uso: node lighthouse.mjs  (front-end em http://127.0.0.1:5500 e API em :8080)
import lighthouse from 'lighthouse';
import * as chromeLauncher from 'chrome-launcher';
import { chromium } from 'playwright';
import fs from 'node:fs';

const BASE = process.env.DITEC_FRONT_URL || 'http://127.0.0.1:5500';
const PAGINAS = [
  ['Página inicial (Sobre, Serviços, Agendamento, Rastreio, Contato)', '/index.html'],
  ['Área do cliente / login', '/conta.html'],
  ['Área do técnico / login', '/tecnico.html'],
  ['Painel administrativo / login', '/admin.html'],
];
const chrome = await chromeLauncher.launch({ chromePath: chromium.executablePath(), chromeFlags: ['--headless=new', '--no-sandbox'] });
const linhas = [];
fs.mkdirSync('relatorios', { recursive: true });
for (const formFactor of ['mobile', 'desktop']) {
  for (const [nome, caminho] of PAGINAS) {
    const config = formFactor === 'desktop'
      ? { extends: 'lighthouse:default', settings: { formFactor: 'desktop', screenEmulation: { mobile: false, width: 1350, height: 940, deviceScaleFactor: 1, disabled: false }, throttlingMethod: 'simulate' } }
      : { extends: 'lighthouse:default', settings: { formFactor: 'mobile', throttlingMethod: 'simulate' } };
    const r = await lighthouse(BASE + caminho, { port: chrome.port, output: 'html', logLevel: 'error' }, config);
    const c = r.lhr.categories;
    const pct = k => Math.round((c[k]?.score ?? 0) * 100);
    linhas.push({ formFactor, nome, caminho, performance: pct('performance'), acessibilidade: pct('accessibility'), boasPraticas: pct('best-practices'), seo: pct('seo'),
      falhas: Object.values(r.lhr.audits).filter(a => a.score !== null && a.score < 0.9 && a.scoreDisplayMode !== 'informative' && a.scoreDisplayMode !== 'manual' && a.scoreDisplayMode !== 'notApplicable').map(a => a.id) });
    fs.writeFileSync(`relatorios/lighthouse-${formFactor}-${caminho.replace(/\W/g, '')}.html`, r.report);
  }
}
await chrome.kill();
for (const l of linhas) console.log(`${l.formFactor.padEnd(7)} ${l.caminho.padEnd(14)} P=${l.performance} A=${l.acessibilidade} BP=${l.boasPraticas} SEO=${l.seo}  falhas: ${l.falhas.join(', ')}`);
fs.writeFileSync('relatorios/lighthouse-resumo.json', JSON.stringify(linhas, null, 2));
