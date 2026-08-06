"use strict";

const GH_OWNER = "0NLyM";
const GH_REPO = "0NLyM";
const DATA_PREFIX = "consegne-tracker/data/";
const SHIPMENTS_PATH = DATA_PREFIX + "shipments.json";
const SUBS_PATH = DATA_PREFIX + "push-subscriptions.json";

// Public VAPID key used to subscribe this browser to Web Push.
// The matching private key lives only in the repo's GitHub Actions secret, never on the client.
const VAPID_PUBLIC_KEY = "BFnzm5f83O45j5NxmSfRKOYNkwLfoB0cRke2M8no-wV-y71ZtNHjU_MfhUxSou8cqxDIkgbx9we-EkTJFulU1Ow";

const STATUS_LABELS = {
  pending: "In attesa di aggiornamento",
  transit: "In transito",
  "out-for-delivery": "In consegna oggi",
  delivered: "Consegnato",
  exception: "Anomalia",
};

const el = (id) => document.getElementById(id);

const els = {
  list: el("shipment-list"),
  empty: el("empty-state"),
  btnAdd: el("btn-add"),
  btnSettings: el("btn-settings"),
  sheetAdd: el("sheet-add"),
  inputCourier: el("input-courier"),
  inputTracking: el("input-tracking"),
  inputLabel: el("input-label"),
  addError: el("add-error"),
  btnCancelAdd: el("btn-cancel-add"),
  btnConfirmAdd: el("btn-confirm-add"),
  sheetDetail: el("sheet-detail"),
  detailBody: el("detail-body"),
  btnDelete: el("btn-delete"),
  btnCloseDetail: el("btn-close-detail"),
  sheetSettings: el("sheet-settings"),
  inputPat: el("input-pat"),
  btnNotif: el("btn-notif"),
  notifStatus: el("notif-status"),
  settingsError: el("settings-error"),
  btnCloseSettings: el("btn-close-settings"),
  toast: el("toast"),
};

let shipments = [];
let shipmentsSha = null;
let activeDetailId = null;
let toastTimer = null;

function getPat() {
  return localStorage.getItem("gh_pat") || "";
}

function showToast(msg) {
  els.toast.textContent = msg;
  els.toast.classList.remove("hidden");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => els.toast.classList.add("hidden"), 2600);
}

function openSheet(sheet) {
  sheet.classList.remove("hidden");
}
function closeSheet(sheet) {
  sheet.classList.add("hidden");
}

// ---------- GitHub Contents API data layer ----------
// Reads/writes JSON files inside this repo so the app works with zero
// dedicated backend: GitHub itself is the database, GitHub Pages serves
// the static site, and a scheduled GitHub Action checks courier status.

async function ghRequest(path, options = {}) {
  const pat = getPat();
  if (!pat) throw new Error("NO_TOKEN");
  const res = await fetch(`https://api.github.com/repos/${GH_OWNER}/${GH_REPO}/contents/${path}`, {
    ...options,
    headers: {
      Authorization: `Bearer ${pat}`,
      Accept: "application/vnd.github+json",
      "X-GitHub-Api-Version": "2022-11-28",
      ...(options.headers || {}),
    },
  });
  return res;
}

function b64EncodeUnicode(str) {
  return btoa(unescape(encodeURIComponent(str)));
}
function b64DecodeUnicode(str) {
  return decodeURIComponent(escape(atob(str)));
}

async function readJsonFile(path, fallback) {
  const res = await ghRequest(path);
  if (res.status === 404) return { json: fallback, sha: null };
  if (!res.ok) throw new Error(`GitHub API ${res.status} leggendo ${path}`);
  const body = await res.json();
  const json = JSON.parse(b64DecodeUnicode(body.content.replace(/\n/g, "")));
  return { json, sha: body.sha };
}

async function writeJsonFile(path, json, sha, message) {
  const res = await ghRequest(path, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      message,
      content: b64EncodeUnicode(JSON.stringify(json, null, 2)),
      sha: sha || undefined,
      branch: GH_BRANCH,
    }),
  });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`GitHub API ${res.status}: ${body}`);
  }
  return res.json();
}

// The app lives on a specific branch until it's merged; GH Pages/Actions
// are wired to this branch, so writes must target it explicitly.
const GH_BRANCH = "claude/delivery-tracking-app-rszzf5";

// Public read of shipments (same-origin static file, no token needed) so the
// list renders even before the user has configured a token.
async function fetchPublicShipments() {
  try {
    const res = await fetch(`./data/shipments.json?_=${Date.now()}`, { cache: "no-store" });
    if (!res.ok) return { shipments: [] };
    return await res.json();
  } catch {
    return { shipments: [] };
  }
}

// ---------- Rendering ----------

function statusClass(s) {
  return (s && s.code) || "pending";
}

function renderList() {
  els.list.innerHTML = "";
  if (!shipments.length) {
    els.empty.classList.remove("hidden");
    return;
  }
  els.empty.classList.add("hidden");

  for (const s of shipments) {
    const li = document.createElement("li");
    li.className = "shipment-card";
    li.dataset.id = s.id;

    const cls = statusClass(s.status);
    const title = s.label && s.label.trim() ? s.label.trim() : s.courier;
    const statusText = (s.status && s.status.text) || STATUS_LABELS[cls] || STATUS_LABELS.pending;

    li.innerHTML = `
      <span class="status-dot ${cls}"></span>
      <div class="card-main">
        <div class="card-title">${escapeHtml(title)}</div>
        <div class="card-sub">${escapeHtml(s.courier)} · ${escapeHtml(s.trackingNumber)}</div>
        <div class="card-status ${cls}">${escapeHtml(statusText)}</div>
      </div>
      <svg class="chev" width="20" height="20" viewBox="0 0 24 24"><path fill="currentColor" d="M9 6l6 6-6 6"/></svg>
    `;
    li.addEventListener("click", () => openDetail(s.id));
    els.list.appendChild(li);
  }
}

function escapeHtml(str) {
  return String(str).replace(/[&<>"']/g, (c) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
  }[c]));
}

function formatDate(iso) {
  if (!iso) return "—";
  try {
    return new Date(iso).toLocaleString("it-IT", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" });
  } catch {
    return iso;
  }
}

function openDetail(id) {
  const s = shipments.find((x) => x.id === id);
  if (!s) return;
  activeDetailId = id;
  const cls = statusClass(s.status);
  const events = (s.status && s.status.events) || [];

  els.detailBody.innerHTML = `
    <div class="detail-row"><span class="k">Corriere</span><span class="v">${escapeHtml(s.courier)}</span></div>
    <div class="detail-row"><span class="k">Tracking</span><span class="v">${escapeHtml(s.trackingNumber)}</span></div>
    <div class="detail-row"><span class="k">Stato</span><span class="v">${escapeHtml((s.status && s.status.text) || STATUS_LABELS[cls])}</span></div>
    <div class="detail-row"><span class="k">Ultimo controllo</span><span class="v">${formatDate(s.status && s.status.lastCheckedAt)}</span></div>
    <div class="detail-events">
      ${events.slice(0, 8).map((e) => `
        <div class="detail-event">
          <div class="t">${formatDate(e.time)}</div>
          <div class="d">${escapeHtml(e.description)}</div>
        </div>
      `).join("") || `<p class="hint" style="margin-top:10px">Nessun evento ancora disponibile. Lo stato verrà aggiornato automaticamente entro breve.</p>`}
    </div>
  `;
  openSheet(els.sheetDetail);
}

// ---------- Add shipment ----------

async function refreshAndRender() {
  const pat = getPat();
  if (pat) {
    try {
      const { json, sha } = await readJsonFile(SHIPMENTS_PATH, { shipments: [] });
      shipments = json.shipments || [];
      shipmentsSha = sha;
      renderList();
      return;
    } catch (e) {
      console.warn("Lettura autenticata fallita, uso fallback pubblico", e);
    }
  }
  const json = await fetchPublicShipments();
  shipments = json.shipments || [];
  shipmentsSha = null; // unknown sha, will be re-read before any write
  renderList();
}

function uuid() {
  if (crypto.randomUUID) return crypto.randomUUID();
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

async function addShipment() {
  const pat = getPat();
  if (!pat) {
    els.addError.textContent = "Aggiungi prima un token GitHub nelle Impostazioni.";
    els.addError.classList.remove("hidden");
    return;
  }
  const courier = els.inputCourier.value;
  const trackingNumber = els.inputTracking.value.trim();
  const label = els.inputLabel.value.trim();

  if (!trackingNumber) {
    els.addError.textContent = "Inserisci un numero di tracking.";
    els.addError.classList.remove("hidden");
    return;
  }

  els.addError.classList.add("hidden");
  els.btnConfirmAdd.disabled = true;
  els.btnConfirmAdd.textContent = "Aggiunta...";

  try {
    const { json, sha } = await readJsonFile(SHIPMENTS_PATH, { shipments: [] });
    const list = json.shipments || [];
    list.push({
      id: uuid(),
      courier,
      trackingNumber,
      label,
      createdAt: new Date().toISOString(),
      status: { code: "pending", text: STATUS_LABELS.pending, lastCheckedAt: null, events: [] },
      notified: { outForDelivery: false, delivered: false },
    });
    await writeJsonFile(SHIPMENTS_PATH, { shipments: list }, sha, `Aggiungi spedizione: ${courier} ${trackingNumber}`);
    shipments = list;
    renderList();
    els.inputTracking.value = "";
    els.inputLabel.value = "";
    closeSheet(els.sheetAdd);
    showToast("Spedizione aggiunta. Lo stato arriverà a breve.");
  } catch (e) {
    console.error(e);
    els.addError.textContent = "Errore nel salvataggio su GitHub. Controlla il token nelle Impostazioni.";
    els.addError.classList.remove("hidden");
  } finally {
    els.btnConfirmAdd.disabled = false;
    els.btnConfirmAdd.textContent = "Aggiungi";
  }
}

async function deleteActiveShipment() {
  if (!activeDetailId) return;
  const pat = getPat();
  if (!pat) {
    showToast("Serve un token GitHub nelle Impostazioni per eliminare.");
    return;
  }
  els.btnDelete.disabled = true;
  try {
    const { json, sha } = await readJsonFile(SHIPMENTS_PATH, { shipments: [] });
    const list = (json.shipments || []).filter((s) => s.id !== activeDetailId);
    await writeJsonFile(SHIPMENTS_PATH, { shipments: list }, sha, "Rimuovi spedizione");
    shipments = list;
    renderList();
    closeSheet(els.sheetDetail);
    showToast("Spedizione eliminata.");
  } catch (e) {
    console.error(e);
    showToast("Errore durante l'eliminazione.");
  } finally {
    els.btnDelete.disabled = false;
  }
}

// ---------- Push notifications ----------

function urlBase64ToUint8Array(base64String) {
  const padding = "=".repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding).replace(/-/g, "+").replace(/_/g, "/");
  const rawData = atob(base64);
  return Uint8Array.from([...rawData].map((c) => c.charCodeAt(0)));
}

async function enablePush() {
  if (!("serviceWorker" in navigator) || !("PushManager" in window)) {
    els.notifStatus.textContent = "Le notifiche push non sono supportate da questo browser.";
    return;
  }
  const pat = getPat();
  if (!pat) {
    els.settingsError.textContent = "Salva prima il token GitHub, poi attiva le notifiche.";
    els.settingsError.classList.remove("hidden");
    return;
  }
  els.settingsError.classList.add("hidden");
  els.btnNotif.disabled = true;
  els.notifStatus.textContent = "Attivazione in corso...";
  try {
    const permission = await Notification.requestPermission();
    if (permission !== "granted") {
      els.notifStatus.textContent = "Permesso notifiche negato.";
      return;
    }
    const reg = await navigator.serviceWorker.ready;
    let sub = await reg.pushManager.getSubscription();
    if (!sub) {
      sub = await reg.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(VAPID_PUBLIC_KEY),
      });
    }
    const { json, sha } = await readJsonFile(SUBS_PATH, { subscriptions: [] });
    const list = json.subscriptions || [];
    const raw = sub.toJSON();
    if (!list.some((x) => x.endpoint === raw.endpoint)) {
      list.push({ ...raw, addedAt: new Date().toISOString() });
      await writeJsonFile(SUBS_PATH, { subscriptions: list }, sha, "Aggiungi dispositivo per notifiche push");
    }
    els.notifStatus.textContent = "Notifiche attive su questo dispositivo.";
    showToast("Notifiche attivate.");
  } catch (e) {
    console.error(e);
    els.notifStatus.textContent = "Errore nell'attivazione delle notifiche.";
  } finally {
    els.btnNotif.disabled = false;
  }
}

// ---------- Wiring ----------

els.btnAdd.addEventListener("click", () => {
  els.addError.classList.add("hidden");
  openSheet(els.sheetAdd);
});
els.btnCancelAdd.addEventListener("click", () => closeSheet(els.sheetAdd));
els.btnConfirmAdd.addEventListener("click", addShipment);

els.btnCloseDetail.addEventListener("click", () => closeSheet(els.sheetDetail));
els.btnDelete.addEventListener("click", deleteActiveShipment);

els.btnSettings.addEventListener("click", () => {
  els.inputPat.value = getPat();
  els.settingsError.classList.add("hidden");
  openSheet(els.sheetSettings);
});
els.btnCloseSettings.addEventListener("click", async () => {
  const val = els.inputPat.value.trim();
  if (val) localStorage.setItem("gh_pat", val);
  closeSheet(els.sheetSettings);
  await refreshAndRender();
});
els.btnNotif.addEventListener("click", enablePush);

for (const backdrop of [els.sheetAdd, els.sheetDetail, els.sheetSettings]) {
  backdrop.addEventListener("click", (e) => {
    if (e.target === backdrop) closeSheet(backdrop);
  });
}

if ("serviceWorker" in navigator) {
  navigator.serviceWorker.register("./sw.js").catch((e) => console.warn("SW registration failed", e));
}

refreshAndRender();
// Refresh whenever the user reopens the app so status looks live.
document.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "visible") refreshAndRender();
});
