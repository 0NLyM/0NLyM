# Glucose Glyph

App Android minimale che legge il valore del glucosio dal tuo CGM tramite
la **HTTP Debug API** già integrata in [ControlX2](https://github.com/jwoglom/controlX2)
e lo mostra sul **Glyph Matrix** del retro del Nothing Phone (3), in stile
Nothing (nero, monospazio, un solo accento rosso, matrice a punti
monocromatica).

> ⚠️ **Non è un dispositivo medico.** Vedi [NOTICE.md](NOTICE.md) per
> l'avvertenza completa: usa sempre l'app ufficiale del tuo CGM/pump per le
> decisioni terapeutiche.

## Come funziona

```
CGM (Dexcom/Libre) --BLE--> pump --BLE--> ControlX2 (Debug HTTP API :18282)
                                                |
                                                | HTTP locale (Basic Auth)
                                                v
                                    Glucose Glyph (questa app)
                                                |
                                                v
                                    Glyph Matrix (retro del telefono)
```

ControlX2 espone in locale (`127.0.0.1:18282`) un'API HTTP di debug con
autenticazione Basic. Questa app, in un servizio in background, invia
periodicamente una richiesta `CurrentEGVGuiDataRequest` al pump tramite
quell'API, legge la risposta (mg/dL + tendenza), la mostra sul Glyph
Matrix tramite l'SDK ufficiale Nothing (`com.nothing.ketchum`) e la tiene
in cache per la prossima apertura del toy.

## 1. Prepara ControlX2

1. Apri **ControlX2** → **Impostazioni** → **Debug** → **HTTP API**.
2. Attiva l'API, imposta un **nome utente** e una **password** (a tua
   scelta) e verifica la porta (default `18282`).
3. Lascia ControlX2 connesso al pump in background come fai di solito.

## 2. Ottieni l'APK

Il repository include un workflow GitHub Actions
(`.github/workflows/build-apk.yml`) che compila l'APK automaticamente ad
ogni push, perché l'SDK Android non è scaricabile da questo ambiente
sandbox. Per ottenere l'APK:

1. Vai sulla scheda **Actions** del repository → workflow **Build APK** →
   l'ultima esecuzione sul branch `claude/android-cgm-glucose-app-38pp02`.
2. Scarica l'artifact **glucose-glyph-debug-apk** (contiene `app-debug.apk`).
3. Trasferiscilo sul telefono e installalo (potrebbe servire abilitare
   "Installa app sconosciute" per l'app che usi per aprirlo).

In alternativa puoi compilarlo tu stesso con Android Studio (Meerkat o
successivo): apri la cartella del progetto e fai *Run* su un Nothing
Phone (3), oppure `./gradlew assembleDebug` da terminale con l'Android
SDK installato.

## 3. Configura l'app

Apri **Glucose Glyph**:

- **ControlX2** → host `127.0.0.1`, porta `18282`, utente/password uguali
  a quelli impostati al punto 1. Premi **Testa connessione** per
  verificare.
- **Visualizzazione** → scegli `mg/dL` o `mmol/L` e l'intervallo di
  aggiornamento (default 60s; il CGM stesso aggiorna ogni ~5 minuti, un
  intervallo più corto serve solo a essere più reattivi quando apri il
  toy).
- **Servizio** → attiva "Lettura in background". Al primo avvio Android
  chiederà il permesso di mostrare notifiche: è la notifica persistente
  a bassa priorità del servizio, serve per farlo funzionare in modo
  affidabile in background.

## 4. Aggiungi il Glyph Toy

Sul retro del telefono, apri il pannello **Glyph Toys** (tasto Glyph /
gesto configurato) e scegli **Glucose** dal carosello. Il valore appare
in stile a punti; una pressione lunga alterna mg/dL ↔ mmol/L. Se il dato
non viene aggiornato da più di 15 minuti, la matrice si affievolisce
automaticamente per segnalarlo, senza icone aggiuntive.

## Struttura del progetto

```
app/src/main/java/it/mattia/glucoseglyph/
├── GlucoseGlyphApp.kt          canale di notifica
├── model/                      dati (GlucoseReading, Trend, AppSettings, GlucoseState)
├── net/ControlX2Client.kt      client HTTP verso la Debug API di ControlX2
├── service/                    servizio in foreground + avvio al boot
├── glyph/                      rendering 25x25 e servizio Glyph Toy (SDK Nothing)
└── ui/                         schermata impostazioni Compose in stile Nothing
```

## Limiti noti

- Richiede che ControlX2 sia installato, in esecuzione e connesso al
  pump: questa app non parla mai direttamente con CGM o pump.
- L'aggiornamento è "a richiesta" (polling), non uno stream continuo:
  ogni tick invia un comando reale al pump via Bluetooth, quindi un
  intervallo troppo aggressivo (sotto i 30-60s) non porta dati più
  freschi (il CGM aggiorna ogni ~5 minuti) ma consuma solo più batteria.
- Se cambi le credenziali/porta della Debug API dentro ControlX2, vanno
  aggiornate anche qui.

Vedi [NOTICE.md](NOTICE.md) per licenze di terze parti e l'avvertenza
medica.
