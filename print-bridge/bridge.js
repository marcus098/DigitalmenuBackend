#!/usr/bin/env node
/*
 * Print bridge ESC/POS — scarica le comande dal server e le invia a una stampante termica:
 *   - di rete (LAN/Wi-Fi) via TCP RAW, porta 9100;
 *   - Bluetooth o seriale tramite porta COM di Windows (es. Netum abbinata via Bluetooth → "COM5").
 * Nessuna dipendenza: richiede Node.js 18+ (fetch integrato).
 *
 * Uso:  node bridge.js [percorso/config.json]
 *       node bridge.js --test [percorso/config.json]   → stampa uno scontrino di prova SENZA passare dal server
 *
 * config.json (stampante di rete):
 *   {
 *     "serverUrl": "https://api.tuodominio.it",
 *     "deviceToken": "<token mostrato in dashboard>",
 *     "printer": { "host": "192.168.1.50", "port": 9100 },
 *     "pollIntervalMs": 2500
 *   }
 * config.json (Bluetooth / seriale, solo Windows):
 *   { ..., "printer": { "type": "serial", "path": "COM5" } }
 *   opzionale "baudRate": 9600 per stampanti seriali/USB-seriale vere (le porte Bluetooth lo ignorano).
 */
'use strict';

const fs = require('fs');
const net = require('net');
const path = require('path');
const { execSync } = require('child_process');

const args = process.argv.slice(2);
const testMode = args.includes('--test');
const configPath = args.find((a) => !a.startsWith('--')) || path.join(__dirname, 'config.json');
let config;
try {
  config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
} catch (e) {
  console.error(`Impossibile leggere ${configPath}: ${e.message}`);
  process.exit(1);
}

const printerCfg = config.printer || {};
const isSerial = printerCfg.type === 'serial' || (!!printerCfg.path && !printerCfg.host);
const serverUrl = String(config.serverUrl || '').replace(/\/+$/, '');
const token = config.deviceToken;
const printerHost = printerCfg.host;
const printerPort = printerCfg.port || 9100;
const serialPath = printerCfg.path ? String(printerCfg.path).trim().toUpperCase() : '';
const pollMs = Math.max(1000, config.pollIntervalMs || 2500);
const maxBackoffMs = 30000;

if (isSerial ? !serialPath : !printerHost) {
  console.error(isSerial
    ? 'config.json: per la stampante seriale/Bluetooth serve printer.path (es. "COM5")'
    : 'config.json: per la stampante di rete serve printer.host (es. "192.168.1.50")');
  process.exit(1);
}
if (!testMode && (!serverUrl || !token)) {
  console.error('config.json deve contenere serverUrl e deviceToken');
  process.exit(1);
}
if (isSerial && process.platform !== 'win32') {
  console.error('La stampa su porta COM è supportata solo su Windows. Su Linux/macOS usa una stampante di rete.');
  process.exit(1);
}

const base = `${serverUrl}/api/printers/bridge/${encodeURIComponent(token)}`;
const log = (...a) => console.log(new Date().toISOString(), ...a);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const printerLabel = isSerial ? serialPath : `${printerHost}:${printerPort}`;

/** Invia i byte alla stampante via TCP RAW (porta 9100). */
function sendTcp(bytes) {
  return new Promise((resolve, reject) => {
    const socket = new net.Socket();
    let done = false;
    const finish = (err) => {
      if (done) return;
      done = true;
      socket.destroy();
      err ? reject(err) : resolve();
    };
    socket.setTimeout(10000);
    socket.on('timeout', () => finish(new Error('timeout stampante')));
    socket.on('error', (err) => finish(err));
    socket.connect(printerPort, printerHost, () => {
      socket.end(bytes, () => setTimeout(() => finish(), 300)); // piccolo margine prima di chiudere
    });
  });
}

// ── Porta COM (Windows) ─────────────────────────────────────────────────────

/** "COM5" → "\\.\COM5" (obbligatorio da COM10 in su, innocuo sotto). */
const devicePath = (p) => (p.startsWith('\\\\.\\') ? p : `\\\\.\\${p}`);

/** Imposta velocità/parametri con il comando "mode" di Windows (solo per seriali vere; Bluetooth lo ignora). */
function configureSerial() {
  if (!printerCfg.baudRate) return;
  const port = serialPath.replace(/^\\\\\.\\/, '');
  try {
    execSync(`mode ${port}: BAUD=${Number(printerCfg.baudRate)} PARITY=N DATA=8 STOP=1`, { stdio: 'ignore' });
    log(`${port}: impostati ${printerCfg.baudRate} baud, 8N1`);
  } catch (e) {
    log(`attenzione: impossibile impostare la velocità di ${port} (${e.message.split('\n')[0]})`);
  }
}

const SERIAL_OPEN_TIMEOUT_MS = 15000; // la connessione Bluetooth può richiedere qualche secondo
const SERIAL_CHUNK = 512;             // le stampanti Bluetooth economiche hanno buffer piccoli

/**
 * Scrive sulla porta COM. La porta viene aperta solo per il tempo della stampa: così altri programmi
 * (o un riavvio della stampante) non la trovano occupata.
 */
async function sendSerial(bytes) {
  const target = devicePath(serialPath);
  let timedOut = false;
  const opening = fs.promises.open(target, 'r+'); // r+ = OPEN_EXISTING: obbligatorio per i dispositivi COM
  const handle = await Promise.race([
    opening,
    sleep(SERIAL_OPEN_TIMEOUT_MS).then(() => { timedOut = true; return null; }),
  ]);
  if (!handle) {
    // Se l'apertura finisce dopo il timeout, chiudi la porta per non lasciarla occupata
    opening.then((h) => h.close()).catch(() => {});
    throw new Error(`timeout apertura ${serialPath}: stampante spenta, fuori portata o non abbinata`);
  }
  try {
    for (let i = 0; i < bytes.length; i += SERIAL_CHUNK) {
      await handle.write(bytes.subarray(i, i + SERIAL_CHUNK));
      if (i + SERIAL_CHUNK < bytes.length) await sleep(30);
    }
    await sleep(800); // lascia svuotare il buffer Bluetooth prima di chiudere il collegamento
  } finally {
    await handle.close().catch(() => {});
  }
  if (timedOut) log('nota: porta aperta in ritardo');
}

function friendlySerialError(err) {
  if (err.code === 'ENOENT') return `${serialPath} non esiste: controlla il numero della porta COM (vedi README)`;
  if (err.code === 'EBUSY' || err.code === 'EACCES' || err.code === 'EPERM')
    return `${serialPath} occupata o non accessibile: chiudi altri programmi che la usano (es. app del produttore)`;
  return err.message;
}

async function sendToPrinter(bytes) {
  if (!isSerial) return sendTcp(bytes);
  try {
    await sendSerial(bytes);
  } catch (err) {
    throw new Error(friendlySerialError(err));
  }
}

// ── Server ──────────────────────────────────────────────────────────────────

async function fetchWithTimeout(url, options = {}, ms = 15000) {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), ms);
  try {
    return await fetch(url, { ...options, signal: ctrl.signal });
  } finally {
    clearTimeout(t);
  }
}

async function ack(jobId, ok, error) {
  const res = await fetchWithTimeout(`${base}/jobs/${encodeURIComponent(jobId)}/ack`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify(ok ? { ok: true } : { ok: false, error: String(error).slice(0, 250) }),
  });
  if (!res.ok) log(`ack ${jobId}: HTTP ${res.status}`);
}

/** Un ciclo: true se ha stampato un job (→ riprova subito, potrebbero essercene altri). */
async function tick() {
  const res = await fetchWithTimeout(`${base}/next`, { headers: { Accept: 'application/json' } });
  if (res.status === 204) return false;
  if (res.status === 404) throw new Error('token non valido o stampante eliminata (404)');
  if (!res.ok) throw new Error(`HTTP ${res.status}`);

  const job = await res.json();
  const bytes = Buffer.from(job.escposBase64, 'base64');
  try {
    await sendToPrinter(bytes);
    log(`stampato job ${job.jobId} (${job.kind}${job.comandId ? ' ' + job.comandId : ''})`);
    await ack(job.jobId, true);
  } catch (err) {
    log(`errore stampa job ${job.jobId}: ${err.message}`);
    await ack(job.jobId, false, err.message);
  }
  return true;
}

/** Scontrino di prova locale (ESC/POS minimo, ASCII): verifica collegamento senza server né token. */
function testTicket() {
  const ESC = 0x1b, GS = 0x1d, LF = 0x0a;
  const text = (s) => Buffer.from(s + '\n', 'ascii');
  return Buffer.concat([
    Buffer.from([ESC, 0x40]),                 // init
    Buffer.from([ESC, 0x61, 1]),              // centrato
    Buffer.from([GS, 0x21, 0x11]), text('PROVA STAMPA'), Buffer.from([GS, 0x21, 0x00]),
    text('print-bridge'),
    text(new Date().toLocaleString('it-IT')),
    text(`Stampante: ${printerLabel}`),
    Buffer.from([ESC, 0x61, 0]),
    text('--------------------------------'),
    text('Se leggi questo scontrino il'),
    text('collegamento funziona.'),
    Buffer.from([LF, LF, LF]),
    Buffer.from([GS, 0x56, 0x42, 0]),         // taglio (ignorato se non c'è la taglierina)
  ]);
}

let running = true;
process.on('SIGINT', () => { running = false; });
process.on('SIGTERM', () => { running = false; });

(async function main() {
  if (isSerial) configureSerial();

  if (testMode) {
    log(`prova di stampa su ${printerLabel}…`);
    try {
      await sendToPrinter(testTicket());
      log('inviato: controlla la stampante');
      process.exit(0);
    } catch (err) {
      log(`errore: ${err.message}`);
      process.exit(2);
    }
  }

  log(`print-bridge avviato: ${serverUrl} → ${printerLabel} (poll ${pollMs} ms)`);
  let backoff = pollMs;
  while (running) {
    try {
      const printed = await tick();
      backoff = pollMs;
      if (!printed) await sleep(pollMs);
    } catch (err) {
      log(`errore: ${err.message} — nuovo tentativo tra ${Math.round(backoff / 1000)} s`);
      await sleep(backoff);
      backoff = Math.min(backoff * 2, maxBackoffMs);
    }
  }
  log('print-bridge arrestato');
})();
