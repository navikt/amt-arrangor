# Dual-write for ansatt og arrangør

## Bakgrunn

Feltet `ansatt.arrangorer` er lagret som JSONB. Denne løsningen skal erstattes av de
normaliserte tabellene:

- `ansatt_arrangor`
- `ansatt_arrangor_rolle`
- `ansatt_arrangor_veileder`
- `ansatt_arrangor_koordinator`

Flyway-migreringene oppretter tabellene og fyller dem med eksisterende data. I perioden
før lesing flyttes til de nye tabellene, skrives endringer til begge representasjonene.

JSONB-løsningen har kjente datakvalitetsproblemer. Målet er derfor ikke å holde
representasjonene identiske ved å kopiere hele JSONB-feltet. Målet er å sende samme
forretningsoperasjon til begge representasjonene, slik at de nye tabellene gradvis blir
klare til å overta som kilde til sannhet.

## Beslutninger

### Operasjoner er grunnlaget for dual-write

`AnsattArrangorSyncService` koordinerer skriving til `AnsattRepository` og
`AnsattArrangorRepository` i samme transaksjon. Tjenesten mottar ferdig beregnede
operasjoner og utfører dem mot begge representasjonene.

De normaliserte tabellene oppdateres med egne operasjoner for å:

- legge til og deaktivere roller
- legge til og deaktivere veilederkoblinger
- legge til og deaktivere koordinatorkoblinger
- deaktivere og reaktivere alle veiledere for en deltaker

Dette gjør at de nye tabellene ikke blir overskrevet av et helt snapshot fra JSONB.

### Full erstatning brukes bare ved opprettelse

`AnsattArrangorRepository.replaceForAnsatt` brukes bare når en ny ansatt opprettes etter
at Flyway-backfillen er kjørt. Vanlige endringer bruker operasjonsspesifikke metoder.

Eksisterende ansatte har fått grunnlaget sitt gjennom backfillen. Det er derfor ikke
nødvendig å bygge opp hele den normaliserte tilstanden på nytt ved hver endring.

### JSONB normaliseres ikke mens JSONB er lesekilde

JSONB-data skrives uten å gå gjennom `withCanonicalRolleperioder` eller tilsvarende
deduplisering. Historiske duplikater og andre avvik i JSONB skal ikke repareres som en
bieffekt av en vanlig oppdatering så lenge JSONB er valgt som lesekilde.

De normaliserte tabellene håndterer unikhet med databasebegrensninger og idempotente
`INSERT ... ON CONFLICT DO NOTHING`. Det er akseptabelt at JSONB og de nye tabellene har
noe ulik representasjon av historiske duplikater i overgangsperioden.

Når `amt.arrangor-les-normaliserte-tabeller` er på, brukes den normaliserte tilstanden
også som beslutningsgrunnlag for mutasjoner. For ansatte som faktisk oppdateres, kan
dual-write da skrive denne tilstanden tilbake til JSONB og dermed fjerne historiske
duplikater. Det er tilsiktet fordi JSONB i denne fasen er en oppdatert rollbackkopi.
Det kjøres fortsatt ingen generell reparasjon eller ny backfill fra de normaliserte
tabellene til JSONB.

### Tidspunkter beregnes én gang

Når én forretningsoperasjon påvirker flere rader eller begge representasjonene, beregnes
tidspunktet én gang og sendes videre. Dette gjelder blant annet:

- deaktivering av flere veiledere
- fjerning av veileder- og koordinatortilganger
- deaktivering av en rolle og tilhørende tilganger
- reaktivering av veiledere

Felles tidspunkt hindrer at samme hendelse får små tidsforskjeller mellom rader eller
representasjoner.

### Personalia oppdateres uten å skrive `arrangorer`

Kafka-hendelser med personalia bruker `AnsattRepository.updatePersonalia`. Metoden
oppdaterer bare personaliafeltene og `modified_at`.

Dette hindrer at et foreldet `AnsattDbo`-snapshot overskriver nyere innhold i
`ansatt.arrangorer`. Personalia påvirker ikke de normaliserte arrangørtabellene og trenger
derfor ikke dual-write.

### Begrenset drift er akseptabelt

Flyway-backfillen etablerer utgangspunktet for de nye tabellene. Noe drift mellom JSONB
og de normaliserte tabellene er akseptabelt i de få dagene før lesing flyttes.

Vi prioriterer at nye forretningsoperasjoner blir skrevet riktig til de normaliserte
tabellene. Vi innfører ikke full avstemming eller automatisk gjenoppbygging fra JSONB i
denne fasen.

## Gjennomført

- Opprettet normaliserte tabeller og Flyway-backfill.
- Innført `AnsattArrangorSyncService` som transaksjonsgrense for dual-write.
- Lagt til operasjonsspesifikke repository-metoder for roller, veiledere og
  koordinatorer.
- Endret alle relevante skriveflyter i `AnsattService` til å bruke dual-write.
- Sørget for at tap av en Altinn-rolle også deaktiverer tilhørende veileder- eller
  koordinatortilganger i de nye tabellene.
- Beholdt egne bulkoperasjoner for deaktivering og reaktivering av veiledere for en
  deltaker.
- Gjort innsetting i barnetabellene idempotent.
- Lagt til varsling i loggen når en konkret rad som skal deaktiveres, ikke finnes.
- Fjernet canonicalisering av rolleperioder før skriving til JSONB.
- Lagt til tester for repository-operasjonene, transaksjonell dual-write og
  rolleendringer med tilhørende tilganger.

## Bevisste avgrensninger

- JSONB er fortsatt grunnlaget for eksisterende leseflyter frem til cutover.
- Det kjøres ingen kontinuerlig avstemming mellom representasjonene.
- Manglende rader ved deaktivering logges, men blokkerer ikke operasjonen.
- Historiske duplikater i JSONB beholdes.
- `replaceForAnsatt` er ikke en generell synkroniseringsmekanisme.

## Neste steg

1. Følg med på logger for manglende rader ved operasjonsspesifikke oppdateringer.
2. Verifiser de normaliserte tabellene mot forventede forretningsoperasjoner før
   cutover.
3. Flytt lesing fra `ansatt.arrangorer` til `AnsattArrangorRepository`.
4. Fjern dual-write til JSONB når de nye tabellene er bekreftet som kilde til sannhet.
5. Fjern JSONB-kolonnen og kode som bare støtter den gamle representasjonen i en senere
   migrering.
