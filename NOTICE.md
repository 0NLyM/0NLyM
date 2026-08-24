# Note / Attribuzioni

Questo è un progetto **non ufficiale**, creato per uso personale, e non è
affiliato con Nothing Technology Limited, con Tandem Diabetes Care, con
Dexcom, con Abbott né con l'autore di ControlX2.

## Componenti di terze parti incluse

- `app/libs/glyphsdk_0606.aar` e il pattern del servizio Glyph Toy in
  `app/src/main/java/it/mattia/glucoseglyph/glyph/GlyphMatrixServiceBase.kt`
  provengono dal repository ufficiale
  [Nothing-Developer-Programme/GlyphMatrix-Example-Project](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Example-Project),
  distribuito da Nothing Technology Limited con licenza MIT.
- Il protocollo di comunicazione con il pump (`/api/pump/messages`,
  `CurrentEGVGuiDataRequest`/`Response`) è quello esposto dalla "HTTP Debug
  API" già presente in [jwoglom/controlX2](https://github.com/jwoglom/controlX2);
  questa app si limita a interrogarla via rete locale, non modifica né
  include codice di ControlX2.

## Avvertenza medica

**Questa app non è un dispositivo medico.** Il valore mostrato sul Glyph
Matrix è solo una comodità per un'occhiata rapida: può essere in ritardo
rispetto alla lettura reale del sensore, può fallire silenziosamente se
ControlX2 non è in esecuzione o non è connesso al pump, e non ha alcuna
validazione clinica. Per qualunque decisione terapeutica (dosaggio di
insulina, trattamento di ipo/iperglicemie) fai sempre riferimento
all'app ufficiale del tuo CGM/pump o al ricevitore dedicato, mai a questa
applicazione.
