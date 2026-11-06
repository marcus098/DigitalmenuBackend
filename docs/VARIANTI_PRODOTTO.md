# Gruppi di varianti del prodotto

Obiettivo: una sola "Margherita" con formato (Baby / Normale / Maxi), impasto (Classico / Senza glutine),
mozzarella (Classica / Senza lattosio), invece di più prodotti.

## Modello

- `Product.options` (esistente, invariato): il **formato**, gruppo a scelta singola che fissa il **prezzo base**.
  Un prodotto con una sola opzione "Standard" non mostra il gruppo formato.
- `Product.variantGroups` (nuovo, colonna TEXT con JSON, null/vuoto = nessun gruppo): altri gruppi a scelta
  singola, sempre obbligatori (c'è sempre una scelta predefinita).

```json
[{
  "id": "impasto",            // id stabile (slug o uuid corto), unico nel prodotto
  "name": "Impasto",
  "choices": [
    { "id": "classico", "name": "Classico", "priceDelta": 0, "isDefault": true, "available": true,
      "removesAllergens": [], "addsAllergens": [], "certifiedFree": false, "dietaryTags": [], "highlight": false },
    { "id": "senza-glutine", "name": "Senza glutine", "priceDelta": 2.0, "isDefault": false, "available": true,
      "removesAllergens": [1], "addsAllergens": [], "certifiedFree": false, "dietaryTags": ["senza-glutine"], "highlight": true }
  ]
}]
```

Validazione (backend, su add/update prodotto): nomi non vuoti, id unici, ≥ 2 scelte per gruppo, esattamente una
`isDefault`, allergeni in 1..14, max 6 gruppi e 12 scelte per gruppo.

## Regole allergeni (approvate dal titolare del progetto)

Allergeni effettivi di una combinazione = (allergeni prodotto + ingredienti) − `removesAllergens` + `addsAllergens`.

1. **Un allergene tolto finisce nelle tracce** ("può contenere tracce di") salvo `certifiedFree: true`
   (il locale garantisce l'assenza, es. senza glutine < 20 ppm con procedure dedicate).
2. **"Senza lattosio" non toglie l'allergene 7 (Latte)**: le proteine del latte restano. Si usa
   `dietaryTags: ["senza-lattosio"]`. L'editor propone i preset:
   - *Senza glutine*: removesAllergens [1], certifiedFree false, highlight true, tag senza-glutine
   - *Senza lattosio*: nessun allergene tolto, highlight true, tag senza-lattosio
   e avvisa se si prova a togliere il 7 da una scelta che parla di lattosio.

## Ordini

- Richiesta (`AddProductToOrder` e simili): `variants: [{ groupId, choiceId }]`. Gruppi omessi = scelta predefinita.
  Scelta inesistente o non disponibile → 400 con messaggio chiaro.
- Prezzo unitario (calcolato **solo dal server**, dal DB) = prezzo formato + Σ priceDelta + extra.
- Nella comanda (`ProductToOrder`) si salva `selectedVariants: [{ groupId, groupName, choiceId, choiceName, priceDelta, highlight }]`
  (snapshot: se il prodotto cambia dopo, la comanda resta com'era).
- Stessa riga di carrello solo se prodotto, formato, varianti, extra, rimozioni e nota coincidono.
- Comanda stampata: ogni variante su una riga; quelle `highlight` in evidenza, es. `⚠ SENZA GLUTINE`.
- Disponibilità: una scelta `available: false` non è selezionabile (mostrata come esaurita); il prodotto resta ordinabile.

## Traduzioni

Nomi di gruppi e scelte sono testi del menu come nome/descrizione prodotto: entrano nel sistema di traduzione
esistente (MenuTranslation) e il catalogo pubblico li restituisce nella lingua richiesta.

## API

- `POST /api/products/addProduct` e `/updateProduct` (multipart, come oggi): nuovo campo form `variantGroups`
  con il JSON dei gruppi **così com'è** (JSON standard, senza il trucco `;`→`,` usato per `options`). Assente = nessuna modifica
  in update / nessun gruppo in add; `"[]"` = rimuovi tutti i gruppi.
- `GET /api/products/setVariantAvailable/{idProduct}/{groupId}/{choiceId}/{available}`: esaurisce/ripristina una scelta
  (ROLE_ADMIN e ROLE_WAITER, come `setAvailable` del prodotto). Pubblica lo stesso evento di aggiornamento menu
  del prodotto, così il menu cliente si aggiorna in tempo reale.
- Catalogo pubblico (webflux `/api/public/client/getAll/{localname}`) e `/api/dashboard/getAll`: ogni prodotto
  espone `variantGroups` (array, mai null) con i nomi già tradotti nella lingua richiesta dal catalogo pubblico.

## Implementazione backend (note e precisazioni al contratto)

Migrazione: `deploy/migrations/2026-11_product_variant_groups.sql` (idempotente): `products.variant_groups text`
e `menu_translations.field` allargato a varchar(128) (Hibernate non allarga le colonne esistenti).

- **Limiti di validazione** in più rispetto all'elenco sopra: id ≤ 40 caratteri, nomi ≤ 60, `|priceDelta|` ≤ 1000
  (arrotondato al centesimo), `dietaryTags` ≤ 10 da ≤ 30 caratteri. Campi assenti in una scelta = valori predefiniti
  (`available: true`, liste vuote, `false`). Errore di validazione su add/update → **400 `{"message": "..."}`**.
- **Errori sugli ordini** (scelta inesistente, gruppo inesistente, due scelte per lo stesso gruppo, scelta esaurita,
  oppure gruppo omesso con la predefinita esaurita) → **400 `{"message": "..."}`** con testo in italiano, su
  `/api/public/orders/insert`, `/api/public/orders/takeaway/{localname}` e `/api/orders/insertWaiter`
  (prima insertWaiter rispondeva 400 senza corpo). `POST /api/public/sessions/{id}/ready` ora verifica la bozza sul
  catalogo e risponde **400 `{"error":"INVALID_ORDER","message":"..."}`** invece di far saltare la comanda all'invio.
- **setVariantAvailable**: 200 `OK`; 404 `Prodotto non trovato` / `Variante non trovata`. Evento SSE: lo stesso
  `data.availability[]` del prodotto, `{kind:"PRODUCT", id, available}` (available = disponibilità del *prodotto*,
  invariata) con in più `groupId`, `choiceId`, `choiceAvailable`. Il menu cliente attuale ricarica il catalogo
  quando riceve `available: true`, quindi si aggiorna già; dashboard/app staff possono usare i tre campi nuovi.
- **Traduzioni**: oggi le traduzioni non passano dal catalogo webflux ma da `GET /api/public/menu-translations/{local}/{lang}`
  sovrapposte dal client. Quindi:
  - `texts.VARIANT[<idProdotto>]["<groupId>"]` = nome del gruppo tradotto,
    `texts.VARIANT[<idProdotto>]["<groupId>/<choiceId>"]` = nome della scelta tradotto (solo traduzioni aggiornate);
  - in più il catalogo pubblico accetta `?lang=en` (`/api/public/client/getAll/{localname}?lang=en`) e restituisce
    i nomi di gruppi e scelte già tradotti (solo per lingue attive del locale; senza `lang` = italiano).
    Nomi/descrizioni di prodotti e categorie restano tradotti dal client come oggi.
  - Editor traduzioni (`/api/translations/{lang}`, `PUT /api/translations/manual`): righe con `type: "VARIANT"`,
    `id` = id prodotto, `field` = `"<groupId>"` o `"<groupId>/<choiceId>"`. Il job di traduzione le include
    (kind "option").
- **Comanda stampata** (ESC/POS, StarPRNT, testo CloudPRNT, tablet RawBT): sotto la riga del prodotto, una riga per
  variante. Normale: `> Mozzarella: Classica`. Highlight: riga in negativo e grassetto `/!\ SENZA GLUTINE`
  (il simbolo ⚠ non è stampabile: le stampanti usano solo ASCII). Vengono stampate tutte le varianti, anche le predefinite.
- `GET /api/printers/tablet/ticket/{comandId}` restituisce anche `text`: anteprima testuale degli scontrini (varianti incluse).
- Totali: pagamenti, checkout, tessere e statistiche usano già `unitPriceCents` (che ora include le varianti).
  Il backend non unisce righe di carrello: la regola "stessa riga" è lato client.
