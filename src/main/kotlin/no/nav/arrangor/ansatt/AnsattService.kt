package no.nav.arrangor.ansatt

import no.nav.arrangor.MetricsService
import no.nav.arrangor.ansatt.repository.AnsattDbo
import no.nav.arrangor.ansatt.repository.AnsattRepository
import no.nav.arrangor.ansatt.repository.ArrangorDbo
import no.nav.arrangor.ansatt.repository.KoordinatorsDeltakerlisteDbo
import no.nav.arrangor.ansatt.repository.VeilederDeltakerDbo
import no.nav.arrangor.arrangor.ArrangorService
import no.nav.arrangor.client.person.PersonClient
import no.nav.arrangor.domain.Ansatt
import no.nav.arrangor.domain.AnsattRolle
import no.nav.arrangor.domain.Arrangor
import no.nav.arrangor.domain.TilknyttetArrangor
import no.nav.arrangor.domain.Veileder
import no.nav.arrangor.domain.VeilederType
import no.nav.arrangor.ingest.PublishService
import no.nav.arrangor.ingest.model.DeltakerStatusType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

@Service
class AnsattService(
	private val personClient: PersonClient,
	private val ansattRepository: AnsattRepository,
	private val rolleService: AnsattRolleService,
	private val publishService: PublishService,
	private val metricsService: MetricsService,
	private val arrangorService: ArrangorService,
) {
	private val logger = LoggerFactory.getLogger(javaClass)

	fun get(id: UUID): Ansatt? = ansattRepository
		.get(id)
		?.let { getAndMaybeUpdateAnsatt(it) }

	fun get(personident: String): Ansatt? = ansattRepository
		.get(personident)
		?.let { getAndMaybeUpdateAnsatt(it) }
		?: opprettAnsatt(personident)

	fun getAnsattIdForPersonident(personident: String): UUID? = ansattRepository.get(personident)?.id

	fun setKoordinatorForDeltakerliste(
		personident: String,
		arrangorId: UUID,
		deltakerlisteId: UUID,
	): Ansatt {
		val (ansatt, arrangor) = hentKoordinatorOgArrangor(personident, arrangorId)

		val eksisterendeDeltakerliste = arrangor.koordinator.find { it.deltakerlisteId == deltakerlisteId }

		return when {
			eksisterendeDeltakerliste?.erGyldig() == true -> {
				logger.info("Deltakerliste med id $deltakerlisteId er allerede lagt til")
				getAndMaybeUpdateAnsatt(ansatt)
			}
			else -> {
				opprettKoordinatorTilgang(arrangor, deltakerlisteId, ansatt)
			}
		}
	}

	private fun opprettKoordinatorTilgang(
		arrangor: ArrangorDbo,
		deltakerlisteId: UUID,
		ansatt: AnsattDbo,
	): Ansatt {
		val oppdatertDeltakerlisterForArrangor = arrangor.koordinator + KoordinatorsDeltakerlisteDbo(deltakerlisteId)
		val oppdatertAnsattDbo = oppdaterAnsattArrangorer(ansatt, arrangor.copy(koordinator = oppdatertDeltakerlisterForArrangor))

		val oppdatertAnsatt = mapToAnsatt(ansattRepository.insertOrUpdate(oppdatertAnsattDbo))

		publishService.publishAnsatt(oppdatertAnsatt)
		metricsService.incLagtTilSomKoordinator()
		logger.info("Ansatt ${oppdatertAnsattDbo.id} ble koordinator for deltakerliste $deltakerlisteId")

		return oppdatertAnsatt
	}

	fun fjernKoordinatorForDeltakerliste(
		personident: String,
		arrangorId: UUID,
		deltakerlisteId: UUID,
	): Ansatt {
		val (ansattDbo, arrangor) = hentKoordinatorOgArrangor(personident, arrangorId)

		return fjernDeltakerlisteTilgang(arrangor, deltakerlisteId, ansattDbo)
	}

	private fun fjernDeltakerlisteTilgang(
		arrangor: ArrangorDbo,
		deltakerlisteId: UUID,
		ansattDbo: AnsattDbo,
	): Ansatt {
		arrangor.koordinator.find { it.deltakerlisteId == deltakerlisteId && it.erGyldig() }?.let {
			it.gyldigTil = ZonedDateTime.now()

			mapToAnsatt(ansattRepository.insertOrUpdate(ansattDbo))
				.also { ansatt -> publishService.publishAnsatt(ansatt) }
				.also { metricsService.incFjernetSomKoodrinator() }
				.also { logger.info("Ansatt ${ansattDbo.id} mistet koordinator for deltakerliste $deltakerlisteId") }
		}

		return getAndMaybeUpdateAnsatt(ansattDbo)
	}

	private fun hentKoordinatorOgArrangor(personident: String, arrangorId: UUID): Pair<AnsattDbo, ArrangorDbo> {
		val ansattDbo = ansattRepository.get(personident) ?: throw NoSuchElementException("Ansatt finnes ikke")

		val arrangor = finnArrangorMedRolle(ansattDbo, arrangorId, AnsattRolle.KOORDINATOR).getOrThrow()

		return Pair(ansattDbo, arrangor)
	}

	private fun finnArrangorMedRolle(
		ansattDbo: AnsattDbo,
		arrangorId: UUID,
		rolle: AnsattRolle,
	): Result<ArrangorDbo> {
		val arrangor =
			ansattDbo.arrangorer.find { it.arrangorId == arrangorId }
				?: return Result.failure(IllegalArgumentException("Ansatt har ikke tilgang til arrangør med id $arrangorId"))
		if (arrangor.roller.find { it.erGyldig() && it.rolle == rolle } == null) {
			return Result.failure(IllegalArgumentException("Ansatt har ikke ${rolle.name.lowercase()}tilgang for arrangør med id $arrangorId"))
		}
		return Result.success(arrangor)
	}

	fun oppdaterVeiledereForDeltaker(
		personident: String,
		deltakerId: UUID,
		request: AnsattController.OppdaterVeiledereForDeltakerRequest,
	) {
		val ansattDbo = ansattRepository.get(personident) ?: throw NoSuchElementException("Ansatt finnes ikke")
		finnArrangorMedRolle(ansattDbo, request.arrangorId, AnsattRolle.KOORDINATOR).getOrThrow()

		val ansatteSomFjernes = ansattRepository.getAnsatte(request.veilederSomFjernes.map { it.ansattId })
		ansatteSomFjernes.forEach { ansatt ->
			fjernVeileder(
				ansattDbo = ansatt,
				arrangorId = request.arrangorId,
				deltakerId = deltakerId,
				type =
					request.veilederSomFjernes.find { it.ansattId == ansatt.id }?.type
						?: throw IllegalStateException("Fant ikke ansatt fra listen, skal ikke kunne skje!"),
			)
		}

		val ansatteSomLeggesTil = ansattRepository.getAnsatte(request.veilederSomLeggesTil.map { it.ansattId })
		ansatteSomLeggesTil.forEach { ansatt ->
			setVeileder(
				ansattDbo = ansatt,
				arrangorId = request.arrangorId,
				deltakerId = deltakerId,
				type =
					request.veilederSomLeggesTil.find { it.ansattId == ansatt.id }?.type
						?: throw IllegalStateException("Fant ikke ansatt fra listen, skal ikke kunne skje!"),
			)
		}
		logger.info("Oppdatert veiledere for deltaker $deltakerId")
	}

	private fun setVeileder(
		ansattDbo: AnsattDbo,
		arrangorId: UUID,
		deltakerId: UUID,
		type: VeilederType,
	) {
		val ansattArrangor = finnArrangorMedRolle(ansattDbo, arrangorId, AnsattRolle.VEILEDER).getOrThrow()

		val eksisterendeVeilederRelasjon = ansattArrangor.veileder.find { it.deltakerId == deltakerId && it.veilederType == type }
		if (eksisterendeVeilederRelasjon?.erGyldig() == true) {
			logger.info("Ansatt er allerede veileder for deltaker med id $deltakerId")
			return
		}

		val oppdatertAnsattDbo =
			oppdaterAnsattArrangorer(
				ansattDbo = ansattDbo,
				oppdatertArrangor = ansattArrangor.copy(veileder = ansattArrangor.veileder + VeilederDeltakerDbo(deltakerId, type)),
			)

		val oppdaterAnsatt = mapToAnsatt(ansattRepository.insertOrUpdate(oppdatertAnsattDbo))
		publishService.publishAnsatt(oppdaterAnsatt)
		metricsService.incLagtTilSomVeileder()
		logger.info("Ansatt ${ansattDbo.id} ble $type for deltaker $deltakerId")
	}

	private fun oppdaterAnsattArrangorer(ansattDbo: AnsattDbo, oppdatertArrangor: ArrangorDbo): AnsattDbo {
		val oppdaterteArrangorer =
			ansattDbo.arrangorer
				.filter { it.arrangorId != oppdatertArrangor.arrangorId }
				.plus(oppdatertArrangor)
		return ansattDbo.copy(arrangorer = oppdaterteArrangorer)
	}

	fun fjernVeileder(
		ansattDbo: AnsattDbo,
		arrangorId: UUID,
		deltakerId: UUID,
		type: VeilederType,
	) {
		val ansattArrangor = ansattDbo.arrangorer.find { it.arrangorId == arrangorId }
		if (ansattArrangor == null) {
			logger.warn("Ansatt ${ansattDbo.id} har ingen tilganger hos arrangør $arrangorId, ingen deltakere å fjerne")
			return
		}

		ansattArrangor.veileder.find { it.deltakerId == deltakerId && it.veilederType == type && it.erGyldig() }?.let {
			it.gyldigTil = ZonedDateTime.now()

			val oppdatertAnsatt = mapToAnsatt(ansattRepository.insertOrUpdate(ansattDbo))
			publishService.publishAnsatt(oppdatertAnsatt)
			metricsService.incFjernetSomVeileder()
		}
		logger.info("Ansatt ${ansattDbo.id} mistet veilederrolle for $deltakerId")
	}

	fun opprettAnsatt(personIdent: String): Ansatt? {
		val altinnRoller = rolleService.getRollerFraAltinn(personIdent)
		if (altinnRoller.isEmpty()) {
			logger.info("Bruker uten rettigheter i Altinn har logget seg inn")
			return null
		}
		val person = personClient.hentPersonalia(personIdent).getOrThrow()

		val arrangorer = rolleService.mapAltinnRollerTilArrangorListeForNyAnsatt(altinnRoller)

		val ansattDbo =
			ansattRepository.insertOrUpdate(
				AnsattDbo(
					id = UUID.randomUUID(),
					personident = personIdent,
					personId = person.id,
					fornavn = person.fornavn,
					mellomnavn = person.mellomnavn,
					etternavn = person.etternavn,
					arrangorer = arrangorer,
				),
			)
		logger.info("Opprettet ny ansatt og lagret roller for ansattId ${ansattDbo.id}")
		return mapToAnsatt(ansattDbo).also { publishService.publishAnsatt(it) }
	}

	fun oppdaterAnsattesRoller() = ansattRepository
		.getToSynchronize(
			maxSize = 50,
			synchronizedBefore = LocalDateTime.now().minusDays(7),
		).forEach { oppdaterRoller(it) }

	private fun getAndMaybeUpdateAnsatt(ansattDbo: AnsattDbo): Ansatt {
		val shouldSynchronize = ansattDbo.lastSynchronized.isBefore(LocalDateTime.now().minusHours(1))

		return if (shouldSynchronize) {
			oppdaterRoller(ansattDbo)
		} else {
			mapToAnsatt(ansattDbo)
		}
	}

	fun oppdaterRoller(ansattDbo: AnsattDbo): Ansatt {
		val ansattDboMedOppdaterteRoller = rolleService.getAnsattDboMedOppdaterteRoller(ansattDbo)

		val oppdatertAnsattDbo = ansattRepository.insertOrUpdate(ansattDboMedOppdaterteRoller.data)

		return mapToAnsatt(oppdatertAnsattDbo)
			.also { if (ansattDboMedOppdaterteRoller.isUpdated) publishService.publishAnsatt(it) }
	}

	fun getAll(offset: Int, limit: Int): List<Ansatt> = ansattRepository.getAll(offset, limit).map { mapToAnsatt(it) }

	private fun mapToAnsatt(ansattDbo: AnsattDbo): Ansatt = Ansatt(
		id = ansattDbo.id,
		personalia = ansattDbo.toPersonalia(),
		arrangorer = mapToTilknyttetArrangorListe(ansattDbo.arrangorer),
	)

	private fun mapToTilknyttetArrangorListe(arrangorDboListe: List<ArrangorDbo>): List<TilknyttetArrangor> {
		if (arrangorDboListe.isEmpty()) {
			return emptyList()
		}
		val unikeArrangorIder = arrangorDboListe.map { it.arrangorId }.distinct()
		val arrangorer = arrangorService.getArrangorerMedOverordnetArrangor(unikeArrangorIder)

		return arrangorDboListe.mapNotNull { arrangorDbo ->
			val arrangor = arrangorer.find { it.id == arrangorDbo.arrangorId }
			if (arrangor == null || arrangorDbo.roller.none { it.erGyldig() }) {
				return@mapNotNull null
			}
			TilknyttetArrangor(
				arrangorId = arrangorDbo.arrangorId,
				arrangor =
					Arrangor(
						id = arrangor.id,
						navn = arrangor.navn,
						organisasjonsnummer = arrangor.organisasjonsnummer,
						overordnetArrangorId = arrangor.overordnetArrangor?.id,
					),
				overordnetArrangor = arrangor.overordnetArrangor,
				roller = arrangorDbo.roller.filter { it.erGyldig() }.map { it.rolle },
				veileder = arrangorDbo.veileder.filter { it.erGyldig() }.map { Veileder(deltakerId = it.deltakerId, type = it.veilederType) },
				koordinator = arrangorDbo.koordinator.filter { it.erGyldig() }.map { it.deltakerlisteId },
			)
		}
	}

	fun deaktiverVeiledereForDeltaker(
		deltakerId: UUID,
		deaktiveringsdato: ZonedDateTime,
		status: DeltakerStatusType?,
	) {
		val ansatteEndret = ansattRepository.deaktiverVeiledereForDeltaker(deltakerId, deaktiveringsdato)
		ansatteEndret.forEach { publishService.publishAnsatt(mapToAnsatt(it)) }

		if (ansatteEndret.isNotEmpty()) {
			logger.info("Deaktiverte veiledere for deltaker $deltakerId med status ${status?.name ?: "slettet"}")
		}
	}

	fun maybeReaktiverVeiledereForDeltaker(deltakerId: UUID, status: DeltakerStatusType) {
		val ansatteEndret = ansattRepository.maybeReaktiverVeiledereForDeltaker(deltakerId)
		ansatteEndret.forEach { publishService.publishAnsatt(mapToAnsatt(it)) }

		if (ansatteEndret.isNotEmpty()) {
			logger.info("Reaktiverte veiledere ${ansatteEndret.size} for deltaker $deltakerId med status ${status.name}")
		}
	}

	fun fjernTilgangerHosArrangor(
		deltakerlisteId: UUID,
		deltakerIder: List<UUID>,
		arrangorId: UUID,
	) {
		val ansatte = ansattRepository.getAnsatteHosArrangor(arrangorId)

		for (ansatt in ansatte) {
			fjernGammelKoordinatorTilgang(ansatt, arrangorId, deltakerlisteId)

			finnArrangorMedRolle(ansatt, arrangorId, AnsattRolle.VEILEDER).onSuccess { arrangor ->
				val fjernedeTilganger = fjernGamleVeilederTilganger(arrangor, deltakerIder)

				if (fjernedeTilganger.isNotEmpty()) {
					val oppdatertAnsatt = mapToAnsatt(ansattRepository.insertOrUpdate(ansatt))
					publishService.publishAnsatt(oppdatertAnsatt)
					metricsService.incFjernetSomVeileder(fjernedeTilganger.size)
					logger.info("Ansatt ${ansatt.id} mistet veilederroller for deltakere på deltakerlisten $deltakerlisteId")
				}
			}
		}
	}

	private fun fjernGamleVeilederTilganger(arrangor: ArrangorDbo, deltakerIder: List<UUID>): List<VeilederDeltakerDbo> {
		val deltakere = arrangor.veileder.filter { it.erGyldig() && it.deltakerId in deltakerIder }
		deltakere.forEach { it.gyldigTil = ZonedDateTime.now() }
		return deltakere
	}

	private fun fjernGammelKoordinatorTilgang(
		ansatt: AnsattDbo,
		gammelArrangorId: UUID,
		deltakerlisteId: UUID,
	) {
		finnArrangorMedRolle(ansatt, gammelArrangorId, AnsattRolle.KOORDINATOR).onSuccess { arrangor ->
			fjernDeltakerlisteTilgang(arrangor, deltakerlisteId, ansatt)
		}
	}
}
