#!/usr/bin/env node
/*
 * Print bridge ESC/POS — scarica le comande dal server e le invia a una stampante termica in LAN (porta 9100).
 * Nessuna dipendenza: richiede Node.js 18+ (fetch integrato).
 *
 * Uso:  node bridge.js [percorso/config.json]
 * config.json:
 *   {
 *     "serverUrl": "https://api.tuodominio.it",
 *     "deviceToken": "<token mostrato in dashboard>",
 *     "printer": { "host": "192.168.1.50", "port": 9100 },
 *     "pollIntervalMs": 2500
 *   }
 */
'use strict';

const fs = require('fs');
const net = require('net');
const path = require('path');

const configPath = process.argv[2] || path.join(__dirname, 'config.json');
let config;
try {
  config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
} catch (e) {
  console.error(`Impossibile leggere ${configPath}: ${e.message}`);
  process.exit(1);
}

const serverUrl = String(config.serverUrl || '').replace(/\/+$/, '');
const token = config.deviceToken;
const printerHost = config.printer && config.printer.host;
const printerPort = (config.printer && config.printer.port) || 9100;
const pollMs = Math.max(1000, config.pollIntervalMs || 2500);
const maxBackoffMs = 30000;

if (!serverUrl || !token || !printerHost) {
  console.error('config.json deve contenere serverUrl, deviceToken e printer.host');
  process.exit(1);
}

const base = `${serverUrl}/api/printers/bridge/${encodeURIComponent(token)}`;
const log = (...args) => console.log(new Date().toISOString(), ...args);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** Invia i byte alla stampante via TCP RAW (porta 9100). */
function sendToPrinter(bytes) {
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

let running = true;
process.on('SIGINT', () => { running = false; });
process.on('SIGTERM', () => { running = false; });

(async function main() {
  log(`print-bridge avviato: ${serverUrl} → ${printerHost}:${printerPort} (poll ${pollMs} ms)`);
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
