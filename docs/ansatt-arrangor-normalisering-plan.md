# Plan: Normalisering av `ansatt.arrangorer` (jsonb → egne tabeller)

📐 Fase 2: Plan — kompakt tier (kjent mønster, skjema-refaktor, ingen nye service­grenser, ingen auth-endring)

## 1. Bakgrunn og omfang

`ansatt.arrangorer` (jsonb) inneholder i dag, per arrangørtilknytning:

```json
{
  "arrangorId": "uuid",
  "roller": [{ "rolle": "VEILEDER", "gyldigFra": "...", "gyldigTil": null }],
  "veileder": [{ "deltakerId": "uuid", "veilederType": "VEILEDER", "gyldigFra": "...", "gyldigTil": null }],
  "koordinator": [{ "deltakerlisteId": "uuid", "gyldigFra": "...", "gyldigTil": null }]
}
```

Kilde: `AnsattDbo.kt` (`ArrangorDbo`, `RolleDbo`, `VeilederDeltakerDbo`, `KoordinatorsDeltakerlisteDbo`).

**Mål for V13/V14:** opprette de nye tabellene og **backfille** dem fra eksisterende
jsonb-data. Operasjonsspesifikk dual-write er senere implementert som del av samme
overgangsarbeid og er beskrevet i
[ansatt-arrangor-dual-write.md](ansatt-arrangor-dual-write.md). Planen for å gjøre de
normaliserte tabellene til kilde til sannhet finnes i
[ansatt-arrangor-cutover-plan.md](ansatt-arrangor-cutover-plan.md).

**Neste ledige versjon:** `V13` (siste er `V12__recreate_ansatt_arrangorer_gin_idx.sql`).

## 2. Justeringer av foreslått skjema

Forslaget fra Copilot er godt som utgangspunkt, men jeg foreslår disse justeringene før implementasjon:

| Endring | Begrunnelse |
|---|---|
| Legg til `created_at` og `modified_at timestamptz not null default current_timestamp` på alle 4 tabeller | Konsistent med `database.instructions.md` ("Always include created_at and updated_at") og med `ansatt`/`arrangor`. Tidsstemplene gjelder radens fysiske levetid: `replaceForAnsatt` sletter og oppretter radene på nytt, mens direkte deaktivering/reaktivering oppdaterer `modified_at`. |
| Legg til `UNIQUE`-constraint på (`ansatt_id, arrangor_id, rolle, gyldig_fra`) for `ansatt_arrangor_rolle`, tilsvarende for veileder/koordinator | Nødvendig for å kunne kjøre backfill idempotent med `ON CONFLICT DO NOTHING`, og forhindrer at duplikater i kildedataen (se pkt. 3) skaper duplikate rader |
| Sørg for at (`ansatt_id`, `arrangor_id`) er ledende kolonner i indeksene på barnetabellene | De unike indeksene starter med disse kolonnene og dekker derfor FK-oppslag uten egne duplikate indekser |
| Legg til indeks på `ansatt_arrangor(arrangor_id)` | Primærnøkkelen starter med `ansatt_id` og dekker derfor ikke søk eller FK-sjekker på `arrangor_id` alene. |
| **Avklart:** `deltaker_id`/`deltakerliste_id` får **ingen FK i denne migreringen** — kun indeks | Begge tabeller finnes (`V10`, `V02`), men vi vil ikke risikere at V13/V14 feiler eller senere blir uendelig fastlåst pga. mulige "foreldreløse" referanser i historisk jsonb-data. FK vurderes som **egen, separat migrering senere**, etter en dedikert verifiseringssjekk mot faktisk data (ikke del av denne migreringen) |

Ingen `id`-surrogatnøkkel er nødvendig på barnetabellene — den sammensatte unique-constrainten er tilstrekkelig som naturlig nøkkel i overgangsfasen.

## 3. Håndtering av duplikater — REVIDERT (ikke avrunding)

**Opprinnelig forslag (avrunding til nærmeste minutt) er forkastet.** `TIMESTAMPTZ` i PostgreSQL har mikrosekund-presisjon. Eksempeldataen skiller seg allerede på mikrosekundnivå (`.728017Z` vs `.728018Z`), så en cast til `timestamptz` **mister ikke** denne forskjellen — radene forblir distinkte etter cast, uavhengig av avrunding. Å trunkere til minutt ville vært en lossy transformasjon av historiske data, gjort **før** vi har bekreftet at det faktisk finnes duplikater som krever håndtering.

### 3.1 Analyse først (kjøres manuelt mot dev/preprod før V14 ferdigstilles)

```sql
-- Tell reelle duplikatgrupper på full presisjon, per tabell/nøkkel
SELECT
    a.id AS ansatt_id,
    (arr.value ->> 'arrangorId')::uuid AS arrangor_id,
    rolle.value ->> 'rolle' AS rolle,
    (rolle.value ->> 'gyldigFra')::timestamptz AS gyldig_fra,
    (rolle.value ->> 'gyldigTil')::timestamptz AS gyldig_til,
    count(*) AS antall
FROM ansatt a,
     LATERAL jsonb_array_elements(a.arrangorer) AS arr(value),
     LATERAL jsonb_array_elements(arr.value -> 'roller') AS rolle(value)
GROUP BY 1, 2, 3, 4, 5
HAVING count(*) > 1
ORDER BY antall DESC;

-- Tilsvarende spørringer kjøres for 'veileder' (gruppert på deltakerId + veilederType)
-- og 'koordinator' (gruppert på deltakerlisteId), med samme mønster.

-- I tillegg: hvor mange rader har identisk (ansatt_id, arrangor_id, rolle) men med
-- INGEN identisk gyldig_fra på mikrosekund-nivå (dvs. "duplikater" bare på
-- forretningsnivå, ikke på databasenivå) — dette er trolig det egentlige problemet:
SELECT
    a.id AS ansatt_id,
    (arr.value ->> 'arrangorId')::uuid AS arrangor_id,
    rolle.value ->> 'rolle' AS rolle,
    count(*) AS antall_rader,
    count(DISTINCT (rolle.value ->> 'gyldigFra')::timestamptz) AS antall_distinkte_fra
FROM ansatt a,
     LATERAL jsonb_array_elements(a.arrangorer) AS arr(value),
     LATERAL jsonb_array_elements(arr.value -> 'roller') AS rolle(value)
GROUP BY 1, 2, 3
HAVING count(*) > count(DISTINCT (rolle.value ->> 'gyldigFra')::timestamptz)
   OR count(*) > 1;
```

Denne analysen avgjør hvilket av følgende scenarioer vi faktisk står i:

- **A: Ingen reelle duplikater ved full presisjon** — hver rad er unik på `(ansatt_id, arrangor_id, rolle, gyldig_fra)` og settes rett inn.
- **B: Reelle duplikater finnes** (identisk `ansatt_id, arrangor_id, rolle, gyldig_fra` på mikrosekund-nivå). Da er dedup nødvendig, med en eksplisitt regel (se 3.2).

### ✅ Analyseresultat — endelig, verifisert mot faktiske data (2026-09-25)

Full analyse kjørt i flere trinn ga følgende bilde, per barnetabell:

| Tabell | Duplikater (`count(*) > 1` på full nøkkel) | Reelle konflikter (samme nøkkel, ulik `gyldig_til`) | `[ZoneId]`-serialiseringsformat |
|---|---|---|---|
| `ansatt_arrangor_rolle` | 184 grupper | **162 grupper**, isolert til 2 `ansatt_id` (`4b9ce873-...`: 92, `36384a1a-...`: 69–70) | 0 rader |
| `ansatt_arrangor_veileder` | ikke talt separat | 0 grupper | **242 rader** har format `...+01:00[Europe/Oslo]` i stedet for `...Z` |
| `ansatt_arrangor_koordinator` | ikke talt separat | 0 grupper | 0 rader |

**Funn 1 — reelle konflikter i `rolle`, men trivielle.** De 162 konfliktgruppene har alle **eksakt 2 rader**, og maksimalt avvik i `gyldig_til` mellom de to radene er **under 1 millisekund** (f.eks. `2025-10-16T09:42:34.994467Z` vs `...994471Z`). Dette er ikke to reelt ulike historiske tilstander — det er samme deaktiverings-hendelse skrevet til jsonb to ganger, mikrosekunder fra hverandre (trolig en dobbel prosessering i synk-jobben, der `ZonedDateTime.now()` ble kalt to separate ganger for samme logiske hendelse). Ingen tilfeller har `NULL` blandet med en verdi.

**Konsekvens:** Scenario B er teknisk sett bekreftet (det finnes rader med lik nøkkel og ulik `gyldig_til`), men den generiske "høyest `gyldig_til`, NULL størst"-regelen fra §3.2 er unødvendig komplisert for dette tilfellet — begge rader er i praksis samme hendelse. Vi bruker en enklere, presist dokumentert regel:

> **Endelig dedup-regel for `ansatt_arrangor_rolle`:** ved eksakt likt `(ansatt_id, arrangor_id, rolle, gyldig_fra)`, behold raden med **høyest `gyldig_til`** (representerer den sist skrevne/mest oppdaterte versjonen av samme hendelse). Siden observert avvik i alle 162 tilfeller er < 1 ms, medfører regelen ingen reell endring av historisk betydning — den er kun nødvendig for å få et deterministisk resultat inn i `UNIQUE`-constrainten.

**Funn 2 — inkonsistent tidsstempel-serialisering i `veileder`.** 242 rader i `veileder`-feltet har `gyldigFra`/`gyldigTil` serialisert som `<offset>[<IANA-sone>]` (f.eks. `2026-11-10T14:36:05.504509103+01:00[Europe/Oslo]`) i stedet for UTC med `Z`-suffiks. Dette er gyldig `ZonedDateTime.toString()`-output fra Kotlin/Java, men **PostgreSQLs `::timestamptz`-cast forstår ikke `[...]`-suffikset** og feiler med `invalid input syntax`. **Dette er ikke et duplikatproblem, men en obligatorisk teknisk fiks**: V14 må kjøre `regexp_replace(verdi, '\[.*\]$', '')` før cast på **alle** felt i **alle** tre barnetabeller (selv om kun `veileder` har vist seg å inneholde dette per nå — vi kan ikke garantere at fremtidige data i `rolle`/`koordinator` aldri vil ha samme format, og fiksen er kostnadsfri når verdien allerede er UTC).

**`ansatt_arrangor_koordinator`:** verken duplikater eller formatavvik. Ren `SELECT DISTINCT` uten videre logikk er tilstrekkelig.

### 3.2 Endelige dedup-/normaliserings-regler for V14

1. **Alle tre tabeller:** normaliser `gyldigFra`/`gyldigTil` med `regexp_replace(felt, '\[.*\]$', '')` før `::timestamptz`-cast, for å håndtere `[ZoneId]`-suffiks uansett hvor det måtte forekomme.
2. **`ansatt_arrangor_rolle`:** dedupliser eksplisitt med `DISTINCT ON (ansatt_id, arrangor_id, rolle, gyldig_fra) ... ORDER BY ..., gyldig_til DESC NULLS FIRST` (høyest/`NULL` vinner), se implementasjon i §5.
3. **`ansatt_arrangor_veileder`, `ansatt_arrangor_koordinator`:** ren `SELECT DISTINCT` er tilstrekkelig — ingen nøkkel-konflikter funnet.

`gyldig_til IS NULL` bevares alltid som `NULL` (betyr "fortsatt gyldig") — ingen avrunding av verdier, kun eksakt (normalisert) kopiering av kildeverdiene.

## 4. Skjema (V13)

```sql
-- V13__create_ansatt_arrangor_tables.sql

CREATE TABLE ansatt_arrangor (
    ansatt_id   UUID NOT NULL,
    arrangor_id UUID NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_ansatt_arrangor PRIMARY KEY (ansatt_id, arrangor_id),
    CONSTRAINT fk_ansatt_arrangor_ansatt FOREIGN KEY (ansatt_id) REFERENCES ansatt (id),
    CONSTRAINT fk_ansatt_arrangor_arrangor FOREIGN KEY (arrangor_id) REFERENCES arrangor (id)
);

CREATE TABLE ansatt_arrangor_rolle (
    ansatt_id   UUID NOT NULL,
    arrangor_id UUID NOT NULL,
    rolle       TEXT NOT NULL,
    gyldig_fra  TIMESTAMPTZ NOT NULL,
    gyldig_til  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_ansatt_arrangor_rolle UNIQUE (ansatt_id, arrangor_id, rolle, gyldig_fra),
    CONSTRAINT fk_ansatt_arrangor_rolle_arrangor
        FOREIGN KEY (ansatt_id, arrangor_id) REFERENCES ansatt_arrangor (ansatt_id, arrangor_id) ON DELETE CASCADE
);
-- Ingen egen indeks på (ansatt_id, arrangor_id): dekkes allerede som prefiks
-- av UNIQUE-indeksen uq_ansatt_arrangor_rolle.

CREATE TABLE ansatt_arrangor_veileder (
    ansatt_id     UUID NOT NULL,
    arrangor_id   UUID NOT NULL,
    deltaker_id   UUID NOT NULL,
    veileder_type TEXT NOT NULL,
    gyldig_fra    TIMESTAMPTZ NOT NULL,
    gyldig_til    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_ansatt_arrangor_veileder UNIQUE (ansatt_id, arrangor_id, deltaker_id, veileder_type, gyldig_fra),
    CONSTRAINT fk_ansatt_arrangor_veileder_arrangor
        FOREIGN KEY (ansatt_id, arrangor_id) REFERENCES ansatt_arrangor (ansatt_id, arrangor_id) ON DELETE CASCADE
);
-- (ansatt_id, arrangor_id) dekkes av UNIQUE-prefiks; deltaker_id trenger egen
-- indeks siden det ikke er første kolonne i noen annen indeks.
CREATE INDEX idx_ansatt_arrangor_veileder_deltaker ON ansatt_arrangor_veileder (deltaker_id);
CREATE INDEX idx_ansatt_arrangor_arrangor_id ON ansatt_arrangor (arrangor_id);

CREATE TABLE ansatt_arrangor_koordinator (
    ansatt_id        UUID NOT NULL,
    arrangor_id      UUID NOT NULL,
    deltakerliste_id UUID NOT NULL,
    gyldig_fra       TIMESTAMPTZ NOT NULL,
    gyldig_til       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_ansatt_arrangor_koordinator UNIQUE (ansatt_id, arrangor_id, deltakerliste_id, gyldig_fra),
    CONSTRAINT fk_ansatt_arrangor_koordinator_arrangor
        FOREIGN KEY (ansatt_id, arrangor_id) REFERENCES ansatt_arrangor (ansatt_id, arrangor_id) ON DELETE CASCADE
);
-- Samme resonnement: kun deltakerliste_id trenger egen indeks.
CREATE INDEX idx_ansatt_arrangor_koordinator_deltakerliste ON ansatt_arrangor_koordinator (deltakerliste_id);
```

`modified_at` leses ikke av applikasjonen fra disse tabellene i dag, men beholdes i tråd med skjemaets tidsstempelkonvensjon og oppdateres ved direkte deaktivering/reaktivering av veiledertilganger. Ved `replaceForAnsatt` slettes og opprettes radene på nytt, så tidsstemplene er ikke historikk for en logisk tilknytning som overlever erstatningen.

## 5. Backfill (V14)

Separat migrering (data-only, ingen DDL) slik at skjema- og databehandling kan testes/rulles tilbake separat.

**Status:** Full analyse er gjennomført (§3). Backfillen normaliserer `[ZoneId]`-suffiks i alle felt (nødvendig pga. 242 rader i `veileder`), og bruker eksplisitt `DISTINCT ON`-basert dedup for `rolle` (nødvendig pga. 162 trivielle nær-duplikater), mens `veileder`/`koordinator` klarer seg med ren `SELECT DISTINCT`.

```sql
-- V14__populate_ansatt_arrangor_tables.sql

-- 1) Foreldretabell: distinkte (ansatt_id, arrangor_id)-par
INSERT INTO ansatt_arrangor (ansatt_id, arrangor_id)
SELECT DISTINCT
    a.id,
    (arr.value ->> 'arrangorId')::uuid
FROM ansatt a,
     LATERAL jsonb_array_elements(a.arrangorer) AS arr(value)
ON CONFLICT (ansatt_id, arrangor_id) DO NOTHING;

-- 2) Roller — normaliserer [ZoneId]-suffiks, og deduplikerer eksplisitt på
-- (ansatt_id, arrangor_id, rolle, gyldig_fra): 162 bekreftede nær-duplikater i
-- kildedata har identisk nøkkel med gyldig_til som avviker < 1 ms. Vi beholder
-- alltid raden med høyest gyldig_til (NULL = fortsatt gyldig = "høyest").
INSERT INTO ansatt_arrangor_rolle (ansatt_id, arrangor_id, rolle, gyldig_fra, gyldig_til)
SELECT DISTINCT ON (ansatt_id, arrangor_id, rolle, gyldig_fra)
    ansatt_id, arrangor_id, rolle, gyldig_fra, gyldig_til
FROM (
    SELECT
        a.id AS ansatt_id,
        (arr.value ->> 'arrangorId')::uuid AS arrangor_id,
        rolle.value ->> 'rolle' AS rolle,
        (regexp_replace(rolle.value ->> 'gyldigFra', '\[.*\]$', ''))::timestamptz AS gyldig_fra,
        CASE
            WHEN rolle.value ->> 'gyldigTil' IS NULL THEN NULL
            ELSE (regexp_replace(rolle.value ->> 'gyldigTil', '\[.*\]$', ''))::timestamptz
        END AS gyldig_til
    FROM ansatt a,
         LATERAL jsonb_array_elements(a.arrangorer) AS arr(value),
         LATERAL jsonb_array_elements(arr.value -> 'roller') AS rolle(value)
) normalisert
ORDER BY ansatt_id, arrangor_id, rolle, gyldig_fra, gyldig_til DESC NULLS FIRST
ON CONFLICT (ansatt_id, arrangor_id, rolle, gyldig_fra) DO NOTHING;

-- 3) Veiledere — normaliserer [ZoneId]-suffiks (242 rader i kildedata har dette
-- formatet). Ingen reelle nøkkelkonflikter funnet i analysen, så ren DISTINCT holder.
INSERT INTO ansatt_arrangor_veileder (ansatt_id, arrangor_id, deltaker_id, veileder_type, gyldig_fra, gyldig_til)
SELECT DISTINCT
    a.id,
    (arr.value ->> 'arrangorId')::uuid,
    (veileder.value ->> 'deltakerId')::uuid,
    veileder.value ->> 'veilederType',
    (regexp_replace(veileder.value ->> 'gyldigFra', '\[.*\]$', ''))::timestamptz,
    CASE
        WHEN veileder.value ->> 'gyldigTil' IS NULL THEN NULL
        ELSE (regexp_replace(veileder.value ->> 'gyldigTil', '\[.*\]$', ''))::timestamptz
    END
FROM ansatt a,
     LATERAL jsonb_array_elements(a.arrangorer) AS arr(value),
     LATERAL jsonb_array_elements(arr.value -> 'veileder') AS veileder(value)
ON CONFLICT (ansatt_id, arrangor_id, deltaker_id, veileder_type, gyldig_fra) DO NOTHING;

-- 4) Koordinatorer — ingen duplikater eller formatavvik funnet, men normaliserer
-- [ZoneId]-suffiks likevel for robusthet mot fremtidige data med samme mønster.
INSERT INTO ansatt_arrangor_koordinator (ansatt_id, arrangor_id, deltakerliste_id, gyldig_fra, gyldig_til)
SELECT DISTINCT
    a.id,
    (arr.value ->> 'arrangorId')::uuid,
    (koordinator.value ->> 'deltakerlisteId')::uuid,
    (regexp_replace(koordinator.value ->> 'gyldigFra', '\[.*\]$', ''))::timestamptz,
    CASE
        WHEN koordinator.value ->> 'gyldigTil' IS NULL THEN NULL
        ELSE (regexp_replace(koordinator.value ->> 'gyldigTil', '\[.*\]$', ''))::timestamptz
    END
FROM ansatt a,
     LATERAL jsonb_array_elements(a.arrangorer) AS arr(value),
     LATERAL jsonb_array_elements(arr.value -> 'koordinator') AS koordinator(value)
ON CONFLICT (ansatt_id, arrangor_id, deltakerliste_id, gyldig_fra) DO NOTHING;
```

**Om `DISTINCT ON` i steg 2:** `DISTINCT ON (partisjonskolonner)` kombinert med `ORDER BY partisjonskolonner, gyldig_til DESC NULLS FIRST` er PostgreSQLs idiomatiske måte å velge "én vinnende rad per nøkkel" på — det tilsvarer funksjonelt `ROW_NUMBER() OVER (PARTITION BY ... ORDER BY ...) WHERE rn = 1`, men er kortere og mer lesbart. `NULLS FIRST` sammen med `DESC` sikrer at `NULL` (fortsatt gyldig) alltid rangeres høyest, uavhengig av om det finnes andre konkrete `gyldig_til`-verdier i samme gruppe.

**Om `regexp_replace(..., '\[.*\]$', '')`:** fjerner et eventuelt `[IANA-sonenavn]`-suffiks (f.eks. `[Europe/Oslo]`) som kan følge etter offset i `ZonedDateTime.toString()`-format. Uten denne normaliseringen feiler `::timestamptz`-cast med `invalid input syntax`. Verdier som allerede er i `Z`/UTC-format (uten `[...]`) påvirkes ikke av `regexp_replace`.

Rekkefølgen er viktig: (1) må kjøre før (2)–(4) pga. FK mot `ansatt_arrangor`.

## 6. Testing

- Legg til/utvid `MigrationTest` (Testcontainers, jf. `database.instructions.md`) med tester som dekker de bekreftede datamønstrene:
  1. **Trivielle nær-duplikater i rolle:** sett inn en `ansatt`-rad med `arrangorer`-jsonb som har to `roller`-elementer med identisk `rolle`/`gyldigFra`, men `gyldigTil` som avviker med 1 mikrosekund (som i faktisk observert data). Verifiser at `ansatt_arrangor_rolle` får **1** rad, med `gyldig_til` lik den **høyeste** av de to kildeverdiene.
  2. **`[ZoneId]`-serialiseringsformat:** sett inn en `veileder`-rad med `gyldigTil` i format `"...+01:00[Europe/Oslo]"`. Verifiser at migreringen kjører uten feil, og at raden i `ansatt_arrangor_veileder` får korrekt `gyldig_til` som `timestamptz` (dvs. at `[Europe/Oslo]`-suffikset er fjernet, ikke bevart som tekst).
  3. **`gyldigTil IS NULL`:** verifiser at `NULL` bevares som `NULL` i alle tre barnetabeller (ikke f.eks. tolket som "eldste").
  4. **Foreldretabell:** verifiser at `ansatt_arrangor` får riktig antall distinkte `(ansatt_id, arrangor_id)`-par uavhengig av hvor mange roller/veiledere/koordinator-elementer som finnes per arrangør.
- Kjør migreringen mot en kopi/backup av dev-databasen før preprod for å bekrefte resultatet i full skala (2497 grupper med `count(*) > 1` er allerede kartlagt manuelt, se §3).

## 7. Rollback-strategi

V13 og V14 er additive og endrer ikke `ansatt.arrangorer`. Hvis en migrering feiler,
ruller Flyway-transaksjonen tilbake og applikasjonen skal ikke starte på et delvis
migrert skjema.

Etter at migreringene er brukt i et delt miljø, skal filene ikke endres og tabellene
skal ikke slettes som ordinær rollback. Forrige applikasjonsversjon kan fortsatt
deployes fordi den ignorerer de nye tabellene. Eventuelle skjemaendringer gjøres med en
ny, fremoverrettet Flyway-migrering.

## 8. Implementert dual-write

Dual-write er implementert med disse beslutningene:

- `AnsattArrangorSyncService` er transaksjonsgrense og koordinerer skriving til JSONB
  og de normaliserte tabellene.
- Nye tabeller oppdateres fra konkrete forretningsoperasjoner, ikke ved å kopiere et
  komplett JSONB-snapshot.
- `replaceForAnsatt` brukes ved opprettelse av en ansatt. Vanlige endringer bruker
  operasjonsspesifikke metoder for roller, veiledere og koordinatorer.
- Tap av en rolle deaktiverer også tilhørende tilganger i de normaliserte tabellene.
- Innsettinger er idempotente med `ON CONFLICT DO NOTHING`.
- Ett tidspunkt beregnes per logiske operasjon og brukes i begge representasjoner.
- JSONB canonicaliseres eller dedupliseres ikke som bieffekt av vanlige
  oppdateringer.
- Personalia oppdateres separat og overskriver ikke `arrangorer`.
- Kjente feil i eksisterende JSONB-spørringer beholdes frem til JSONB fases ut.

Begrunnelsene, bevisste avgrensninger og gjennomførte kodeendringer er dokumentert i
[ansatt-arrangor-dual-write.md](ansatt-arrangor-dual-write.md).

## 9. Gjenstående arbeid

Før de normaliserte tabellene kan bli kilde til sannhet skal teamet:

1. verifisere produksjonsdata etter backfill og dual-write
2. flytte alle rolle- og tilgangslesinger til de normaliserte tabellene
3. beholde dual-write gjennom observasjons- og rollback-perioden
4. stoppe JSONB-skriving i en separat release
5. fjerne JSONB-kolonnen og GIN-indeksen med en senere Flyway-migrering

Detaljert utrullingsrekkefølge, observerbarhet, rollback og ferdigkriterier finnes i
[ansatt-arrangor-cutover-plan.md](ansatt-arrangor-cutover-plan.md).

FK-er for `deltaker_id` og `deltakerliste_id` er fortsatt utsatt. De kan vurderes i en
egen migrering etter at foreldreløse historiske referanser er kartlagt.
