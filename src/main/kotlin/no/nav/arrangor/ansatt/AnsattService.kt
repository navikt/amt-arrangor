package no.nav.arrangor.ansatt

import no.nav.arrangor.MetricsService
import no.nav.arrangor.ansatt.repository.AnsattDbo
import no.nav.arrangor.ansatt.repository.AnsattRepository
import no.nav.arrangor.ansatt.repository.ArrangorDbo
import no.nav.arrangor.ansatt.repository.KoordinatorsDeltakerlisteDbo
import no.nav.arrangor.ansatt.repository.RolleDbo
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
import no.nav.arrangor.utils.isDev
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
	private val arrangorService: ArrangorService
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun get(id: UUID): Ansatt? = ansattRepository.get(id)
		?.let { getAndMaybeUpdateAnsatt(it) }

	fun get(personident: String): Ansatt? {
		return ansattRepository.get(personident)
			?.let { getAndMaybeUpdateAnsatt(it) }
			?: opprettAnsatt(personident)
	}

	fun getAnsattIdForPersonident(personident: String): UUID? {
		return ansattRepository.get(personident)?.id
	}

	fun setKoordinatorForDeltakerliste(personident: String, arrangorId: UUID, deltakerlisteId: UUID): Ansatt {
		val ansattDbo = ansattRepository.get(personident) ?: throw NoSuchElementException("Ansatt finnes ikke")
		val arrangor = ansattDbo.arrangorer.find { it.arrangorId == arrangorId }
			?: throw IllegalArgumentException("Ansatt har ikke tilgang til arrangør med id $arrangorId")
		if (arrangor.roller.find { it.erGyldig() && it.rolle == AnsattRolle.KOORDINATOR } == null) {
			throw IllegalArgumentException("Ansatt har ikke koordinatortilgang for arrangør med id $arrangorId")
		}

		val eksisterendeDeltakerliste = arrangor.koordinator.find { it.deltakerlisteId == deltakerlisteId }
		if (eksisterendeDeltakerliste != null && eksisterendeDeltakerliste.erGyldig()) {
			logger.info("Deltakerliste med id $deltakerlisteId er allerede lagt til")
			return getAndMaybeUpdateAnsatt(ansattDbo)
		}
		val oppdatertAnsattDbo = if (eksisterendeDeltakerliste != null && !eksisterendeDeltakerliste.erGyldig()) {
			eksisterendeDeltakerliste.gyldigTil = null
			ansattDbo
		} else {
			val oppdatertDeltakerlisterForArrangor = mutableListOf<KoordinatorsDeltakerlisteDbo>()
			oppdatertDeltakerlisterForArrangor.addAll(arrangor.koordinator)
			oppdatertDeltakerlisterForArrangor.add(KoordinatorsDeltakerlisteDbo(deltakerlisteId))
			val oppdatertArrangor = arrangor.copy(koordinator = oppdatertDeltakerlisterForArrangor)
			val oppdaterteArrangorer = mutableListOf<ArrangorDbo>()
			ansattDbo.arrangorer.forEach {
				if (it.arrangorId != arrangorId) {
					oppdaterteArrangorer.add(it)
				}
			}
			oppdaterteArrangorer.add(oppdatertArrangor)
			ansattDbo.copy(arrangorer = oppdaterteArrangorer)
		}

		return mapToAnsatt(ansattRepository.insertOrUpdate(oppdatertAnsattDbo))
			.also { ansatt -> publishService.publishAnsatt(ansatt) }
			.also { metricsService.incLagtTilSomKoordinator() }
			.also { logger.info("Ansatt ${ansattDbo.id} ble koordinator for deltakerliste $deltakerlisteId") }
	}

	fun fjernKoordinatorForDeltakerliste(personident: String, arrangorId: UUID, deltakerlisteId: UUID): Ansatt {
		val ansattDbo = ansattRepository.get(personident) ?: throw NoSuchElementException("Ansatt finnes ikke")
		val arrangor = ansattDbo.arrangorer.find { it.arrangorId == arrangorId }
			?: throw IllegalArgumentException("Ansatt har ikke tilgang til arrangør med id $arrangorId")
		if (arrangor.roller.find { it.erGyldig() && it.rolle == AnsattRolle.KOORDINATOR } == null) {
			throw IllegalArgumentException("Ansatt har ikke koordinatortilgang for arrangør med id $arrangorId")
		}

		arrangor.koordinator.find { it.deltakerlisteId == deltakerlisteId && it.erGyldig() }?.let {
			it.gyldigTil = ZonedDateTime.now()

			return mapToAnsatt(ansattRepository.insertOrUpdate(ansattDbo))
				.also { ansatt -> publishService.publishAnsatt(ansatt) }
				.also { metricsService.incFjernetSomKoodrinator() }
				.also { logger.info("Ansatt ${ansattDbo.id} mistet koordinator for deltakerliste $deltakerlisteId") }
		}

		return getAndMaybeUpdateAnsatt(ansattDbo)
	}

	fun oppdaterVeiledereForDeltaker(
		personident: String,
		deltakerId: UUID,
		request: AnsattController.OppdaterVeiledereForDeltakerRequest
	) {
		val ansattDbo = ansattRepository.get(personident) ?: throw NoSuchElementException("Ansatt finnes ikke")
		val arrangor = ansattDbo.arrangorer.find { it.arrangorId == request.arrangorId }
			?: throw IllegalArgumentException("Ansatt har ikke tilgang til arrangør med id ${request.arrangorId}")
		if (arrangor.roller.find { it.erGyldig() && it.rolle == AnsattRolle.KOORDINATOR } == null) {
			throw IllegalArgumentException("Ansatt har ikke koordinatortilgang for arrangør med id ${request.arrangorId}")
		}

		val ansatteSomFjernes = ansattRepository.getAnsatte(request.veilederSomFjernes.map { it.ansattId })
		ansatteSomFjernes.forEach { ansatt ->
			fjernVeileder(
				ansattDbo = ansatt,
				arrangorId = request.arrangorId,
				deltakerId = deltakerId,
				type = request.veilederSomFjernes.find { it.ansattId == ansatt.id }?.type
					?: throw IllegalStateException("Fant ikke ansatt fra listen, skal ikke kunne skje!")
			)
		}

		val ansatteSomLeggesTil = ansattRepository.getAnsatte(request.veilederSomLeggesTil.map { it.ansattId })
		ansatteSomLeggesTil.forEach { ansatt ->
			setVeileder(
				ansattDbo = ansatt,
				arrangorId = request.arrangorId,
				deltakerId = deltakerId,
				type = request.veilederSomLeggesTil.find { it.ansattId == ansatt.id }?.type
					?: throw IllegalStateException("Fant ikke ansatt fra listen, skal ikke kunne skje!")
			)
		}
		logger.info("Oppdatert veiledere for deltaker $deltakerId")
	}

	private fun setVeileder(ansattDbo: AnsattDbo, arrangorId: UUID, deltakerId: UUID, type: VeilederType) {
		val ansattArrangor = ansattDbo.arrangorer.find { it.arrangorId == arrangorId }
		if (ansattArrangor == null || ansattArrangor.roller.find { it.erGyldig() && it.rolle == AnsattRolle.VEILEDER } == null) {
			throw IllegalArgumentException("Ansatt har ikke veiledertilgang for arrangør med id $arrangorId")
		}

		val eksisterendeVeilederRelasjon = ansattArrangor.veileder.find { it.deltakerId == deltakerId && it.veilederType == type }
		if (eksisterendeVeilederRelasjon != null && eksisterendeVeilederRelasjon.erGyldig()) {
			logger.info("Ansatt er allerede veileder for deltaker med id $deltakerId")
			return
		}
		val oppdatertAnsattDbo = if (eksisterendeVeilederRelasjon != null && !eksisterendeVeilederRelasjon.erGyldig()) {
			eksisterendeVeilederRelasjon.gyldigTil = null
			ansattDbo
		} else {
			val oppdatertVeilederDeltakerForArrangor = mutableListOf<VeilederDeltakerDbo>()
			oppdatertVeilederDeltakerForArrangor.addAll(ansattArrangor.veileder)
			oppdatertVeilederDeltakerForArrangor.add(VeilederDeltakerDbo(deltakerId, type))
			val oppdatertArrangor = ansattArrangor.copy(veileder = oppdatertVeilederDeltakerForArrangor)
			val oppdaterteArrangorer = mutableListOf<ArrangorDbo>()
			ansattDbo.arrangorer.forEach {
				if (it.arrangorId != arrangorId) {
					oppdaterteArrangorer.add(it)
				}
			}
			oppdaterteArrangorer.add(oppdatertArrangor)
			ansattDbo.copy(arrangorer = oppdaterteArrangorer)
		}

		val oppdaterAnsatt = mapToAnsatt(ansattRepository.insertOrUpdate(oppdatertAnsattDbo))
		publishService.publishAnsatt(oppdaterAnsatt)
		metricsService.incLagtTilSomVeileder()
		logger.info("Ansatt ${ansattDbo.id} ble $type for deltaker $deltakerId")
	}

	fun fjernVeileder(ansattDbo: AnsattDbo, arrangorId: UUID, deltakerId: UUID, type: VeilederType) {
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

		val ansattDbo = ansattRepository.insertOrUpdate(
			AnsattDbo(
				id = UUID.randomUUID(),
				personident = personIdent,
				personId = person.id,
				fornavn = person.fornavn,
				mellomnavn = person.mellomnavn,
				etternavn = person.etternavn,
				arrangorer = arrangorer
			)
		)
		logger.info("Opprettet ny ansatt og lagret roller for ansattId ${ansattDbo.id}")
		return mapToAnsatt(ansattDbo).also { publishService.publishAnsatt(it) }
	}

	fun ingestAnsatt(ansatt: Ansatt) {
		val ansattFromDb = ansattRepository.get(ansatt.id)

		if (ansattFromDb == null) {
			logger.info("Ansatt med id ${ansatt.id} finnes ikke fra før, oppretter ansatt")
			ingestNyAnsatt(ansatt)
		} else {
			logger.info("Ansatt med id ${ansatt.id} finnes fra før, oppdaterer ansatt")
			val oppdatertArrangorlisteForAnsatt = rolleService.getOppdatertArrangorListeForIngestAvAnsatt(ansattFromDb, ansatt.arrangorer)
			ansattRepository.insertOrUpdate(
				ansattFromDb.copy(
					personident = ansatt.personalia.personident,
					fornavn = ansatt.personalia.navn.fornavn,
					mellomnavn = ansatt.personalia.navn.mellomnavn,
					etternavn = ansatt.personalia.navn.etternavn,
					arrangorer = oppdatertArrangorlisteForAnsatt
				)
			)
		}
	}
	private fun ingestNyAnsatt(ansatt: Ansatt) {
		val person = personClient.hentPersonalia(ansatt.personalia.personident).getOrElse {
			logger.error("Noe gikk galt ved henting av person for ansattId ${ansatt.id}")
			if (isDev()) {
				logger.warn("Ignorerer ansatt i dev ${ansatt.id}")
				null
			} else {
				throw it
			}
		} ?: return
		val arrangorer = ansatt.arrangorer.map { tilknyttetArrangor ->
			ArrangorDbo(
				arrangorId = tilknyttetArrangor.arrangorId,
				roller = tilknyttetArrangor.roller.map { RolleDbo(it) },
				veileder = tilknyttetArrangor.veileder.map {
					VeilederDeltakerDbo(
						deltakerId = it.deltakerId,
						veilederType = it.type
					)
				},
				koordinator = tilknyttetArrangor.koordinator.map {
					KoordinatorsDeltakerlisteDbo(
						deltakerlisteId = it
					)
				}
			)
		}
		ansattRepository.insertOrUpdate(
			AnsattDbo(
				id = ansatt.id,
				personId = person.id,
				personident = ansatt.personalia.personident,
				fornavn = ansatt.personalia.navn.fornavn,
				mellomnavn = ansatt.personalia.navn.mellomnavn,
				etternavn = ansatt.personalia.navn.etternavn,
				arrangorer = arrangorer
			)
		)
	}

	fun oppdaterAnsattesRoller() = ansattRepository.getToSynchronize(
		maxSize = 50,
		synchronizedBefore = LocalDateTime.now().minusDays(7)
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
		var updated = false

		val ansattDboMedOppdaterteRoller = rolleService.getAnsattDboMedOppdaterteRoller(ansattDbo, ansattDbo.personident)
			.also { if (it.isUpdated) updated = true }
			.data

		val oppdatertAnsattDbo = ansattRepository.insertOrUpdate(ansattDboMedOppdaterteRoller)

		return mapToAnsatt(oppdatertAnsattDbo)
			.also { if (updated) publishService.publishAnsatt(it) }
	}

	private fun mapToAnsatt(ansattDbo: AnsattDbo): Ansatt {
		return Ansatt(
			id = ansattDbo.id,
			personalia = ansattDbo.toPersonalia(),
			arrangorer = mapToTilknyttetArrangorListe(ansattDbo.arrangorer)
		)
	}

	private fun mapToTilknyttetArrangorListe(arrangorDboListe: List<ArrangorDbo>): List<TilknyttetArrangor> {
		if (arrangorDboListe.isEmpty()) {
			return emptyList()
		}
		val unikeArrangorIder = arrangorDboListe.map { it.arrangorId }.distinct()
		val arrangorer = arrangorService.getArrangorerMedOverordnetArrangorForArrangorIder(unikeArrangorIder)

		return arrangorDboListe.mapNotNull { arrangorDbo ->
			val arrangor = arrangorer.find { it.id == arrangorDbo.arrangorId }
			if (arrangor == null || arrangorDbo.roller.none { it.erGyldig() }) {
				return@mapNotNull null
			}
			TilknyttetArrangor(
				arrangorId = arrangorDbo.arrangorId,
				arrangor = Arrangor(
					id = arrangor.id,
					navn = arrangor.navn,
					organisasjonsnummer = arrangor.organisasjonsnummer,
					overordnetArrangorId = arrangor.overordnetArrangor?.id
				),
				overordnetArrangor = arrangor.overordnetArrangor,
				roller = arrangorDbo.roller.filter { it.erGyldig() }.map { it.rolle },
				veileder = arrangorDbo.veileder.filter { it.erGyldig() }.map { Veileder(deltakerId = it.deltakerId, type = it.veilederType) },
				koordinator = arrangorDbo.koordinator.filter { it.erGyldig() }.map { it.deltakerlisteId }
			)
		}
	}
}
