// Runs inside GitHub Actions (server-side, so no browser CORS restrictions apply).
// Polls 17TRACK for every tracked shipment, updates data/shipments.json and
// sends Web Push notifications on meaningful status transitions.

import { readFile, writeFile } from "node:fs/promises";
import webpush from "web-push";

const DATA_DIR = new URL("../data/", import.meta.url);
const SHIPMENTS_FILE = new URL("shipments.json", DATA_DIR);
const SUBS_FILE = new URL("push-subscriptions.json", DATA_DIR);

const TRACK17_API_KEY = process.env.TRACK17_API_KEY;
const VAPID_PUBLIC_KEY = process.env.VAPID_PUBLIC_KEY;
const VAPID_PRIVATE_KEY = process.env.VAPID_PRIVATE_KEY;
const VAPID_CONTACT_EMAIL = process.env.VAPID_CONTACT_EMAIL || "mailto:example@example.com";

const STATUS_MAP = {
  NotFound: { code: "pending", text: "In attesa di aggiornamento" },
  InfoReceived: { code: "pending", text: "Corriere avvisato, pacco non ancora ritirato" },
  InTransit: { code: "transit", text: "In transito" },
  AvailableForPickup: { code: "transit", text: "Disponibile per il ritiro" },
  OutForDelivery: { code: "out-for-delivery", text: "In consegna oggi" },
  DeliveryFailure: { code: "exception", text: "Consegna non riuscita" },
  Delivered: { code: "delivered", text: "Consegnato" },
  Exception: { code: "exception", text: "Anomalia nella spedizione" },
  Expired: { code: "exception", text: "Tracking scaduto" },
};

function normalizeStatus(raw) {
  return STATUS_MAP[raw] || { code: "pending", text: "In attesa di aggiornamento" };
}

async function readJson(url, fallback) {
  try {
    return JSON.parse(await readFile(url, "utf8"));
  } catch {
    return fallback;
  }
}

async function track17(path, body) {
  const res = await fetch(`https://api.17track.net/track/v2.2/${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "17token": TRACK17_API_KEY,
    },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(`17TRACK ${path} HTTP ${res.status}: ${text}`);
  }
  return res.json();
}

function extractEvents(trackInfo) {
  const providers = trackInfo?.tracking?.providers || [];
  const events = [];
  for (const p of providers) {
    for (const e of p.events || []) {
      events.push({
        time: e.time_iso || e.time_utc || e.time || null,
        description: e.description || e.stage || "",
      });
    }
  }
  events.sort((a, b) => new Date(b.time || 0) - new Date(a.time || 0));
  return events;
}

async function fetchStatus(number) {
  await track17("register", [{ number }]).catch((e) => {
    console.warn(`register(${number}) fallita (spesso normale se già registrato):`, e.message);
  });

  const info = await track17("gettrackinfo", [{ number }]);
  const accepted = info?.data?.accepted?.[0];
  if (!accepted) {
    return { code: "pending", text: "In attesa di aggiornamento", events: [] };
  }
  const rawStatus = accepted.track_info?.latest_status?.status;
  const normalized = normalizeStatus(rawStatus);
  const events = extractEvents(accepted.track_info);
  return { ...normalized, events };
}

async function sendPush(subscriptions, payload) {
  if (!VAPID_PUBLIC_KEY || !VAPID_PRIVATE_KEY) {
    console.warn("VAPID keys mancanti: notifiche push saltate.");
    return subscriptions;
  }
  webpush.setVapidDetails(VAPID_CONTACT_EMAIL, VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY);
  const alive = [];
  for (const sub of subscriptions) {
    try {
      await webpush.sendNotification(sub, JSON.stringify(payload));
      alive.push(sub);
    } catch (e) {
      if (e.statusCode === 404 || e.statusCode === 410) {
        console.log("Subscription scaduta, rimossa:", sub.endpoint);
      } else {
        console.warn("Invio push fallito:", e.message);
        alive.push(sub);
      }
    }
  }
  return alive;
}

async function main() {
  if (!TRACK17_API_KEY) {
    console.error("TRACK17_API_KEY non configurata: aggiungi il secret nel repository. Uscita senza controlli.");
    return;
  }

  const shipmentsDoc = await readJson(SHIPMENTS_FILE, { shipments: [] });
  const subsDoc = await readJson(SUBS_FILE, { subscriptions: [] });
  let subscriptions = subsDoc.subscriptions || [];

  for (const s of shipmentsDoc.shipments || []) {
    try {
      const status = await fetchStatus(s.trackingNumber);
      const prevCode = s.status?.code;
      s.status = {
        code: status.code,
        text: status.text,
        events: status.events,
        lastCheckedAt: new Date().toISOString(),
      };
      s.notified = s.notified || { outForDelivery: false, delivered: false };

      const title = s.label?.trim() || s.courier;

      if (status.code === "out-for-delivery" && !s.notified.outForDelivery) {
        subscriptions = await sendPush(subscriptions, {
          title: "📦 In consegna oggi",
          body: `${title} (${s.courier}) sarà consegnato oggi.`,
          tag: `shipment-${s.id}`,
          url: "./",
        });
        s.notified.outForDelivery = true;
      }

      if (status.code === "delivered" && !s.notified.delivered) {
        subscriptions = await sendPush(subscriptions, {
          title: "✅ Pacco consegnato",
          body: `${title} (${s.courier}) è stato consegnato.`,
          tag: `shipment-${s.id}`,
          url: "./",
        });
        s.notified.delivered = true;
      }

      if (status.code === "exception" && prevCode !== "exception") {
        subscriptions = await sendPush(subscriptions, {
          title: "⚠️ Anomalia spedizione",
          body: `${title} (${s.courier}): ${status.text}.`,
          tag: `shipment-${s.id}`,
          url: "./",
        });
      }
    } catch (e) {
      console.error(`Errore controllando ${s.trackingNumber}:`, e.message);
    }
  }

  await writeFile(SHIPMENTS_FILE, JSON.stringify(shipmentsDoc, null, 2) + "\n");
  await writeFile(SUBS_FILE, JSON.stringify({ subscriptions }, null, 2) + "\n");
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
