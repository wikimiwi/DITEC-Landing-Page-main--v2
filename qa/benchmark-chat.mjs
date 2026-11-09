// Mede o tempo de resposta REAL do chatbot (POST /api/chat do backend DITEC).
// Separa a 1a requisicao (pode incluir "aquecimento" do modelo) das seguintes,
// e conta por fonte: "ia" (Google Gemini) x "automatica" (IA indisponivel).
// Uso: node benchmark-chat.mjs [quantidade=15] [intervaloMs=0]
//   (rate limit da API: 20/5min; no plano gratuito do Gemini use intervalo de ~7000 ms para ficar abaixo do limite por minuto)
const API = process.env.DITEC_API_URL || 'http://localhost:8080/api';
const N = +(process.argv[2] || 15);
const INTERVALO = +(process.argv[3] || 0);
const PERGUNTAS = [
  'Vocês consertam geladeira que não gela?', 'Qual a garantia do serviço?', 'O diagnóstico é gratuito?',
  'Minha máquina de lavar não centrifuga, o que pode ser?', 'Atendem aos domingos?', 'Tem desconto no Pix?',
  'Meu fogão a gás está com cheiro de gás, o que faço?', 'Como acompanho minha ordem de serviço?',
  'Vocês atendem em Osasco?', 'Quanto custa consertar um micro-ondas?',
];
const amostras = [];
for (let i = 0; i < N; i++) {
  const pergunta = PERGUNTAS[i % PERGUNTAS.length];
  const t0 = performance.now();
  let status = 0, fonte = '-', texto = '';
  try {
    const r = await fetch(API + '/chat', { method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ messages: [{ role: 'user', content: pergunta }], sessionId: 'benchmark-' + Date.now() }) });
    status = r.status;
    const d = await r.json().catch(() => ({}));
    fonte = d.fonte || '-'; texto = d?.choices?.[0]?.message?.content || '';
  } catch (e) { status = 0; }
  const ms = performance.now() - t0;
  amostras.push({ i: i + 1, ms, status, fonte, pergunta, resposta: texto.slice(0, 90).replace(/\s+/g, ' ') });
  console.log(`#${String(i + 1).padStart(2)} ${String(Math.round(ms)).padStart(6)} ms  HTTP ${status}  fonte=${fonte}  ${pergunta}`);
  if (INTERVALO && i < N - 1) await new Promise(r => setTimeout(r, INTERVALO));
}
/* intervalo aplicado depois de cada amostra (fora da medicao) */
const est = arr => {
  if (!arr.length) return null;
  const v = arr.map(a => a.ms).sort((a, b) => a - b);
  const p95 = v[Math.min(v.length - 1, Math.ceil(0.95 * v.length) - 1)];
  return { n: v.length, media: Math.round(v.reduce((s, x) => s + x, 0) / v.length), min: Math.round(v[0]), max: Math.round(v[v.length - 1]), p95: Math.round(p95) };
};
const ok = amostras.filter(a => a.status === 200);
const ia = ok.filter(a => a.fonte === 'ia');
const resumo = {
  total: amostras.length, sucesso: ok.length, falhas: amostras.length - ok.length,
  taxaSucesso: (100 * ok.length / amostras.length).toFixed(1) + '%',
  respostasDaIA: ia.length, respostasAutomaticas: ok.filter(a => a.fonte === 'automatica').length,
  primeira: est(amostras.slice(0, 1)), subsequentes: est(amostras.slice(1)), todas: est(amostras), somenteIA: est(ia),
};
console.log(JSON.stringify(resumo, null, 2));
import fs from 'node:fs';
fs.mkdirSync('relatorios', { recursive: true });
fs.writeFileSync(`relatorios/benchmark-chat-${new Date().toISOString().replace(/[:.]/g, '-')}.json`, JSON.stringify({ resumo, amostras }, null, 2));
