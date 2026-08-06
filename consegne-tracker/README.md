# Consegne Tracker

App web installabile (PWA) per tracciare i pacchi dei principali corrieri italiani (Poste Italiane, BRT, GLS, SDA, DHL, UPS, FedEx, TNT, Amazon Logistics, InPost, Nexive, Fercam...) da un'unica lista, con notifica push quando un pacco risulta **in consegna** o **consegnato**.

Tema: nero assoluto (AMOLED), testo bianco/grigio, accento arancione. Nessun account da creare per l'app in sé: i tuoi dati vivono in questo repository GitHub.

## Come funziona (architettura)

Non c'è un server dedicato: uso solo servizi che hai già.

- **GitHub Pages** ospita l'app statica (HTML/CSS/JS).
- **Questo repository** fa da "database": le spedizioni sono salvate in `data/shipments.json`, le iscrizioni alle notifiche in `data/push-subscriptions.json`. L'app li legge/scrive tramite le API di GitHub.
- **GitHub Actions** fa da backend: uno workflow programmato interroga **17TRACK** (aggregatore che copre la maggior parte dei corrieri italiani) e, se un pacco passa a "in consegna" o "consegnato", invia una notifica push al telefono usando il protocollo Web Push (chiavi VAPID), senza bisogno di un server sempre acceso.

## Setup iniziale (una tantum, ~10 minuti)

### 1. Abilita GitHub Actions per questo repository
Su questo repo le Actions risultano **disattivate** (l'ho verificato: i workflow che ho aggiunto non partono, nemmeno manualmente, e non ho i permessi per riattivarle da qui). Vai su **Settings → Actions → General → Actions permissions** e seleziona **"Allow all actions and reusable workflows"**, poi salva. Senza questo passaggio l'app funziona per aggiungere/vedere i pacchi, ma nessuno stato verrà mai controllato e non arriverà nessuna notifica.

### 2. Abilita i permessi di scrittura per le Actions
Sempre in **Settings → Actions → General**, scorri a **Workflow permissions** e seleziona **Read and write permissions**, poi salva. Serve perché lo workflow di controllo aggiorna `data/shipments.json` da solo.

### 3. Crea un token GitHub per l'app (lato telefono)
Vai su **github.com → Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate new token**:
- Repository access: **solo** `0NLyM/0NLyM`
- Permissions → Repository permissions → **Contents: Read and write**
- Genera e copia il token (inizia con `github_pat_...`). Lo incollerai nell'app, nelle Impostazioni. Resta salvato solo sul tuo telefono (localStorage del browser), non viene mai inviato altrove.

### 4. Crea una API key gratuita su 17TRACK — *puoi saltare questo passaggio per ora*
Senza questa chiave l'app funziona comunque: puoi installarla, aggiungere pacchi e vederli in lista, restano solo fermi su "In attesa di aggiornamento" finché non colleghi 17TRACK (il controllo automatico si accorge da solo che la chiave manca e non fa nulla, senza errori). Quando vorrai attivare il controllo automatico dello stato:

Registrati su [17track.net](https://www.17track.net) (piano gratuito), sezione API/Developer, e genera una **API key**. Poi vai su **Settings → Secrets and variables → Actions → New repository secret** in questo repo e aggiungi:
- `TRACK17_API_KEY` = la tua chiave 17TRACK

### 5. Aggiungi le chiavi per le notifiche push (VAPID)
Sono già state generate per questa app. Aggiungi questi due secret (stesso posto del punto 4):
- `VAPID_PUBLIC_KEY` = `BFnzm5f83O45j5NxmSfRKOYNkwLfoB0cRke2M8no-wV-y71ZtNHjU_MfhUxSou8cqxDIkgbx9we-EkTJFulU1Ow`
- `VAPID_PRIVATE_KEY` = `gP7AP_x_XOJQFf87fHWafafo_bg7uqT-ZEGUrJftDCM`

Facoltativo: `VAPID_CONTACT_EMAIL` = `mailto:tuaemail@esempio.it` (email di contatto richiesta dallo standard Web Push).

> La chiave pubblica è già scritta anche in `app.js`. La chiave privata **non deve mai finire nel codice**: resta solo come secret, la usa esclusivamente lo workflow di GitHub Actions.

### 6. Pubblica GitHub Pages
Una volta abilitate le Actions (punto 1), il workflow `deploy-pages.yml` pubblica automaticamente l'app a ogni push su questo branch. Dopo il primo run, vai su **Settings → Pages** e verifica che la sorgente sia impostata su "GitHub Actions" (di solito lo fa da solo). L'URL sarà del tipo:

`https://0nlym.github.io/0NLyM/`

### 7. Installa l'app sul telefono Android
1. Apri l'URL sopra con **Chrome** su Android.
2. Tocca il menu (⋮) → **Aggiungi a schermata Home** / **Installa app**.
3. Apri l'app dall'icona: parte a schermo intero, tema nero.
4. Tocca l'icona ingranaggio in alto → incolla il token GitHub del punto 3 → **Attiva notifiche push** → concedi il permesso.
5. Aggiungi un pacco con il pulsante **+**: corriere, numero di tracking, etichetta facoltativa.

Entro pochi minuti (o al prossimo controllo pianificato) lo stato viene aggiornato e, quando il pacco risulta "in consegna oggi", ricevi la notifica.

## Limitazioni da sapere

- **Repository pubblico**: `data/shipments.json` è visibile a chiunque visiti il repo (i numeri di tracking non sono un dato sensibile critico, ma se preferisci privacy totale serve un repo privato con GitHub Pages a pagamento, oppure possiamo spostare i dati altrove: fammelo sapere).
- **Il controllo automatico ogni 30 minuti (cron) funziona solo dal branch di default** (`main`) di GitHub. Finché questo lavoro resta sul branch `claude/delivery-tracking-app-rszzf5`, lo stato si aggiorna comunque **subito dopo ogni aggiunta di un pacco** (trigger su push) e puoi sempre forzare un controllo manuale da **Actions → Check shipment status → Run workflow**. Quando sarai pronto a unire questo branch su `main`, il controllo ogni 30 minuti partirà in automatico.
- **17TRACK** copre la maggior parte dei corrieri italiani ma non garantisce dati in tempo reale al 100%: la frequenza di aggiornamento dipende dal corriere stesso.
- Le notifiche push funzionano quando Chrome (o il motore del telefono) è attivo in background, come per qualunque altra app: su Android sono generalmente affidabili anche ad app chiusa.

## Struttura del progetto

```
consegne-tracker/
├── index.html, style.css, app.js   # l'app (PWA)
├── sw.js                           # service worker: cache offline + notifiche push
├── manifest.webmanifest            # metadati installazione Android
├── icons/                          # icone dell'app
├── data/
│   ├── shipments.json              # le tue spedizioni (fa da "database")
│   └── push-subscriptions.json     # dispositivi iscritti alle notifiche
├── scripts/check-shipments.mjs     # script eseguito da GitHub Actions
└── .github/workflows/
    ├── deploy-pages.yml            # pubblica l'app su GitHub Pages
    └── check-shipments.yml         # controlla lo stato e invia le notifiche
```
