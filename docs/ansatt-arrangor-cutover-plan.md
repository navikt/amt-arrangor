# Plan for overgang fra JSONB til normaliserte ansatt–arrangør-tabeller

**Dato:** 26. september 2026  
**Team:** amt  
**Risiko:** Middels

## Mål

`ansatt.arrangorer` skal erstattes av disse tabellene som kilde til sannhet:

- `ansatt_arrangor`
- `ansatt_arrangor_rolle`
- `ansatt_arrangor_veileder`
- `ansatt_arrangor_koordinator`

Etter overgangen skal alle beslutninger om roller og tilganger, alle API-svar og alle
publiserte ansatt-hendelser bygges fra de normaliserte tabellene. JSONB-kolonnen skal
deretter fjernes sammen med kode, indekser og tester som bare støtter den gamle
representasjonen.

Planen bygger videre på dual-write-løsningen som er beskrevet i
[ansatt-arrangor-dual-write.md](ansatt-arrangor-dual-write.md).

## Føringer

1. De normaliserte tabellene skal være kilde til sannhet før JSONB-skriving stoppes.
2. JSONB skal ikke brukes til å reparere eller overskrive normaliserte data etter
   cutover.
3. Dual-write beholdes gjennom hele observasjonsperioden for normalisert lesing. Da kan
   forrige applikasjonsversjon deployes som rollback uten at JSONB mangler nye
   oppdateringer.
4. Stopp av JSONB-skriving og sletting av kolonnen skal skje i separate releaser.
5. Kjente feil og historiske duplikater i JSONB skal ikke kopieres inn i ny
   forretningslogikk. Avstemming må derfor sammenligne semantisk tilstand, ikke rå
   JSON eller antall historiske rader.
6. Alle steg skal være bakoverkompatible med forrige applikasjonsversjon.

## Målarkitektur

`AnsattRepository` skal eie personalia og synkroniseringsmetadata i `ansatt`.
`AnsattArrangorRepository` skal eie roller, veilederkoblinger og
koordinatorkoblinger. Et leselag skal sette disse delene sammen til domenetypen
`Ansatt`.

```text
ansatt                         ansatt_arrangor*
  personalia                     roller og tilganger
  last_synchronized                    |
        |                              |
        +---------- leselag -----------+
                       |
                    Ansatt
                       |
              API og Kafka-hendelser
```

Leselaget skal hente relasjoner i batch for lister. Det skal ikke gjøre ett oppslag i
`AnsattArrangorRepository` per ansatt.

Denne delingen er valgt fordi den gjør eierskapet tydelig og hindrer at et
`AnsattDbo` med foreldet JSONB-snapshot igjen kan bli brukt som autoritativ tilstand.
En direkte fallback fra manglende normaliserte rader til JSONB skal ikke innføres:
fallbacken ville skjult datamangler og gjort det uklart hvilken representasjon som er
kilde til sannhet.

> **Rød sone – forstå dette grundig:** Rolle- og tilgangskontroll skal etter cutover
> alltid bruke normaliserte data. En enkelt gjenværende kontroll mot
> `AnsattDbo.arrangorer` kan gi andre rettigheter enn API-svaret viser.

## Berørte leseflyter

Følgende flyter må flyttes samlet eller være eksplisitt dekket av overgangskode:

| Flyt | Dagens kilde | Ny kilde |
|------|--------------|----------|
| Hent ansatt med ID eller personident | `AnsattRepository.get` | Personalia fra `ansatt`, relasjoner fra normaliserte tabeller |
| Hent flere ansatte | `AnsattRepository.getAnsatte` | Batchvis sammensatt lesing |
| Synkroniser roller | `getToSynchronize` og JSONB i `AnsattDbo` | Kandidater fra `ansatt`, eksisterende roller fra normaliserte tabeller |
| Sidevis uthenting | `AnsattRepository.getAll` | Batchvis sammensatt lesing uten N+1-spørringer |
| Finn ansatte hos arrangør | JSONB-spørring i `getAnsatteHosArrangor` | Oppslag i `ansatt_arrangor` på `arrangor_id` |
| Sjekk koordinator- og veilederrolle | `AnsattDbo.arrangorer` | Normaliserte roller |
| Sjekk eksisterende tilgang | `AnsattDbo.arrangorer` | Normaliserte veileder- og koordinatorkoblinger |
| Publiser ansatt | Domenemodell mappet fra JSONB | Domenemodell mappet fra normaliserte tabeller |
| Bulk deaktivering og reaktivering | Resultat fra JSONB-oppdatering | Berørte `ansatt_id` fra normalisert oppdatering |

Personaliaflyten i `ConsumerService` påvirkes ikke. Den skal fortsatt oppdatere bare
personalia og `modified_at`.

## Fase 0 – produksjonsklar dual-write

Denne fasen skal være fullført før lesing flyttes.

- [ ] V13 og V14 er kjørt i dev og prod.
- [ ] Nye ansatte oppretter rader i alle relevante normaliserte tabeller.
- [ ] Rolle-, veileder- og koordinatorendringer dual-writes i én transaksjon.
- [ ] Bulk deaktivering og reaktivering oppdaterer de normaliserte tabellene.
- [ ] Logger for manglende normaliserte rader er gjennomgått.
- [ ] Alle kjente skriveflyter har integrasjonstester mot PostgreSQL.
- [ ] Antall aktive roller og tilganger er stabile etter deployment.

**Exit-kriterium:** Ingen uforklarte manglende rader fra nye
forretningsoperasjoner i minst ett døgn.

## Fase 1 – bygg normalisert leselag

Denne fasen endrer ikke produksjonens valgte lesekilde.

1. Legg til batchmetoder i `AnsattArrangorRepository`:
   - hent relasjoner for én eller flere `ansatt_id`
   - hent `ansatt_id` for en `arrangor_id`
   - hent berørte `ansatt_id` for bulkoperasjoner
2. Skill personalia og relasjoner i persistensmodellene. `AnsattRepository` skal ikke
   måtte deserialisere `arrangorer` for nye leseflyter.
3. Lag ett leselag som henter personalia og relasjoner og bygger `Ansatt` eller en
   overgangstype som resten av tjenestelaget kan bruke.
4. Sørg for deterministisk sortering av arrangører, roller og tilganger. SQL-rader har
   ingen garantert rekkefølge, mens JSONB-listene hadde en lagret rekkefølge.
5. Flytt mappingen til `TilknyttetArrangor` til det nye leselaget eller gi mapperen
   eksplisitt normaliserte `ArrangorDbo`-verdier.

**Ytelseskrav:** `getAll` og `getAnsatte` skal bruke et fast antall
databasekall per side eller batch, ikke ett kall per ansatt.

**Exit-kriterium:** Det nye leselaget kan produsere alle eksisterende API- og
Kafka-modeller i tester uten å lese `ansatt.arrangorer`.

## Fase 2 – verifiser data før cutover

Lag en avgrenset verifiseringsspørring eller engangsjobb. Den skal rapportere
aggregater og tekniske ID-er, men ikke personident eller annen personinformasjon.

Verifiseringen skal minst kontrollere:

- ansatte med JSONB-relasjoner, men uten rad i `ansatt_arrangor`
- aktive roller per rolle og arrangør
- aktive veilederkoblinger per type
- aktive koordinatorkoblinger
- foreldrerader uten barn og barn uten forventet foreldrerad
- flere samtidige aktive rader med samme forretningsnøkkel
- ugyldige perioder der `gyldig_til < gyldig_fra`

Avvik mot JSONB må klassifiseres:

| Klasse | Håndtering |
|--------|------------|
| Kjent duplikat eller kjent JSONB-feil | Dokumenter og aksepter |
| Normalisert tilstand følger korrekt forretningsoperasjon | Behold normalisert tilstand |
| Manglende normalisert rad etter V14 | Reparer med en kontrollert, idempotent migrering |
| Ukjent avvik i aktiv rolle eller tilgang | Stopp cutover og finn årsaken |

Ikke kjør en generell ny backfill fra JSONB etter at dual-write er startet. En slik
backfill kan overskrive nyere og riktigere normalisert tilstand. Eventuelle reparasjoner
skal være målrettede og dokumenterte.

**Exit-kriterium:** Ingen ukjente avvik i aktive roller eller tilganger.

## Fase 3 – flytt lesing, behold dual-write

Flytt alle leseflytene i tabellen over til det normaliserte leselaget i samme release.
Det gjelder også validering før skriving og data som publiseres til Kafka.

Bruk Unleash-toggle for valgt lesekilde mens releasen observeres:

```text
amt.arrangor-les-normaliserte-tabeller
```

Av betyr JSONB, og på betyr normaliserte tabeller. Togglen skal bare velge komplett
lesekilde; samme forretningsoperasjon skal ikke blande data fra begge kilder. Manglende
normaliserte rader skal ikke falle tilbake til JSONB, fordi det ville skjult datadrift
og kunne gitt ulike tilgangsbeslutninger innen samme flyt.

ApiToken-ressursen for `RemoteUnleash`-instansen `amt` skal deployes før applikasjonen
og oppretter secreten `amt-arrangor-unleash-api-token`. Togglen må opprettes i både
development- og production-miljøet i Unleash. En manglende eller avslått toggle betyr
JSONB-lesing.

Når togglen er på, er den normaliserte tilstanden også beslutningsgrunnlag for
mutasjoner. Dual-write kan derfor skrive denne tilstanden tilbake til JSONB for ansatte
som faktisk endres. Det er tilsiktet i fase 3: JSONB beholdes som en oppdatert
rollbackkopi, men brukes ikke til å korrigere eller bygge opp de normaliserte tabellene.

Rekkefølge:

1. Deploy til dev og slå på togglen.
2. Kjør smoke-tester for alle rolle- og tilgangsflyter.
3. Deploy samme image til prod med togglen avslått.
4. Slå på togglen i prod.
5. Observer minst 48 timer og gjennomfør en planlagt rollesynkronisering.
6. Behold dual-write under hele perioden.

**Exit-kriterium:** Alle leseflyter bruker normaliserte tabeller i prod i minst
48 timer uten ukjente avvik, funksjonelle feil eller uakseptabel
ytelsesforverring.

## Fase 4 – gjør normaliserte tabeller til eneste skrivekilde

Denne fasen skal være en egen pull request og release etter godkjent observasjonsperiode.

1. Endre `AnsattArrangorSyncService` til å skrive roller og tilganger bare til
   `AnsattArrangorRepository`.
2. Endre opprettelse av ansatt slik at `AnsattRepository` bare skriver personalia og
   synkroniseringsmetadata, mens relasjonene opprettes i de normaliserte tabellene i
   samme transaksjon.
3. Flytt bulk deaktivering og reaktivering helt til
   `AnsattArrangorRepository`. Bruk returnerte `ansatt_id` til å hente oppdatert
   normalisert tilstand før publisering.
4. Fjern JSONB-spesifikke mutasjoner fra `AnsattRepository`.
5. Fjern sammenligning mellom berørte JSONB-rader og normaliserte rader fra
   dual-write-koden.
6. La kolonnen og gammel lesekode stå urørt gjennom en kort observasjonsperiode, men
   ikke bruk dem i normal drift.

Etter dette steget blir JSONB umiddelbart foreldet. Rollback kan derfor ikke være å
slå lesing tilbake til JSONB.

**Exit-kriterium:** Ingen produksjonskode leser eller skriver `ansatt.arrangorer`, og
normaliserte operasjoner har vært stabile i minst 48 timer.

## Fase 5 – fjern JSONB

Utfør oppryddingen i en separat release:

1. Fjern `arrangorer` fra `AnsattDbo` eller erstatt overgangstypen med en
   personaliaorientert persistensmodell.
2. Fjern JSONB-serialisering, `PGobject`-hjelpere og ObjectMapper-avhengighet fra
   `AnsattRepository`.
3. Fjern `getAnsatteHosArrangor` og andre JSONB-spørringer.
4. Fjern `idx_ansatt_arrangorer_gin`.
5. Fjern kolonnen `ansatt.arrangorer` med en ny Flyway-migrering.
6. Fjern runtime-konfigurasjonen for lesekilde.
7. Fjern tester og testdata som bare verifiserer JSONB-representasjonen.
8. Oppdater dual-write-dokumentet og marker migreringen som fullført.

Kolonnen skal ikke slettes i samme release som JSONB-skriving stoppes. Det sikrer at
skjemaet fortsatt er kompatibelt med forrige image under utrullingen.

## Teststrategi

### Repository-tester

- Alle normaliserte lesemetoder testes med flere ansatte og flere arrangører.
- Batchmetoder testes med tom input, manglende relasjoner og historiske perioder.
- Oppslag på arrangør, deltaker og deltakerliste verifiserer riktige ansatte.
- Resultatrekkefølge testes eksplisitt dersom API- eller Kafka-kontrakten krever den.

### Tjenestetester

- Rollevalidering bruker bare normaliserte roller.
- Eksisterende veileder- og koordinatortilgang oppdages fra normaliserte rader.
- Rollefjerning deaktiverer tilhørende tilganger og publiserer oppdatert tilstand.
- Bulk deaktivering og reaktivering publiserer alle og bare berørte ansatte.
- Opprettelse og alle endringer rulles tilbake dersom en normalisert skriving feiler.

### Regresjonstester

- API-svar har samme kontrakt før og etter cutover.
- Kafka-payload har samme kontrakt før og etter cutover.
- Tester sammenligner forretningsmessig resultat, ikke historiske JSONB-duplikater.
- Migrasjonstestene dekker V13, V14 og migreringen som til slutt fjerner JSONB.

## Observerbarhet

Følgende signaler skal finnes før fase 3:

| Signal | Formål | Reaksjon |
|--------|--------|----------|
| Antall normaliserte lesinger | Bekrefter at ny lesekilde er aktiv | Undersøk hvis den er 0 etter cutover |
| Feil ved normalisert lesing | Oppdager mapping- og dataproblemer | Rollback ved vedvarende feil |
| Manglende relasjon ved deaktivering | Oppdager drift i dual-write | Undersøk alle nye forekomster |
| Varighet for `get`, `getAnsatte` og `getAll` | Oppdager N+1 eller tunge spørringer | Rollback ved tydelig regresjon |
| Antall publiserte ansatte etter bulkoperasjon | Oppdager tapte eller ekstra hendelser | Sammenlign med berørte `ansatt_id` |
| Aktive roller og tilganger per type | Oppdager brå dataskift | Stopp videre utrulling ved uforklart endring |

Logger skal bruke tekniske ID-er og antall. De skal ikke inneholde personident eller
navn.

## Rollback-plan

| Fase | Trigger | Handling | Datakonsekvens |
|------|---------|----------|----------------|
| 1–2 | Test- eller dataverifisering feiler | Ikke deploy lesebyttet | Ingen |
| 3 | Feilrate, feil rettigheter eller uakseptabel responstid | Slå av `amt.arrangor-les-normaliserte-tabeller` i Unleash, eller deploy forrige image | Ingen nye data mangler i JSONB fordi dual-write fortsatt er aktiv |
| 4 | Feil i normalisert skriving | Deploy forrige image bare dersom ingen nye operasjoner er utført; ellers lag en målrettet reparasjon og behold normalisert lesing | JSONB er foreldet og kan ikke brukes som automatisk fallback |
| 5 | Applikasjonen feiler mot nytt skjema | Deploy en fremoverrettet migrering eller et kompatibelt image | Droppet kolonne skal ikke gjenopprettes fra normaliserte data som akutt rollback |

Før fase 4 skal teamet eksplisitt godkjenne at rollback til JSONB ikke lenger er
trygt. Databasemigreringer skal rulles fremover, ikke reverseres ved å gjenskape
JSONB-kolonnen.

## Post-deploy-verifisering

Kjør denne sjekklisten etter fase 3 og fase 4:

- [ ] Hent ansatt med ID og personident.
- [ ] Hent en side med ansatte og kontroller responstid.
- [ ] Opprett en ny ansatt med minst én rolle.
- [ ] Legg til og fjern en veilederkobling.
- [ ] Legg til og fjern en koordinatorkobling.
- [ ] Fjern en rolle og kontroller at avhengige tilganger deaktiveres.
- [ ] Kjør eller observer rollesynkronisering.
- [ ] Observer deaktivering og reaktivering for en deltaker.
- [ ] Kontroller at publisert Kafka-payload reflekterer normalisert tilstand.
- [ ] Kontroller logger og metrikker for manglende rader og lesefeil.
- [ ] Kontroller at det ikke er nye ukjente datavvik.

## Ferdigkriterier

Migreringen er ferdig når:

- alle lesinger, beslutninger og publiseringer bruker normaliserte tabeller
- alle skriv går bare til normaliserte tabeller
- ingen produksjonskode refererer til `ansatt.arrangorer`
- JSONB-kolonnen og GIN-indeksen er fjernet
- overgangskonfigurasjon og dual-write-kode er fjernet
- tester dekker normalisert lesing og skriving uten JSONB-fixtures
- dokumentasjon og runbook beskriver normalisert modell som kilde til sannhet

Dette er en endring i kjernelogikk og tilgangsgrunnlag. Hvert steg bør gjennomgås med
særlig vekt på hvilke data som brukes til rollevalidering, og hvordan rollback fungerer
etter at JSONB-skriving er stoppet.
