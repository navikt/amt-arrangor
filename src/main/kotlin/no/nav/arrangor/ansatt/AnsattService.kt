package no.nav.arrangor.ansatt

import no.nav.arrangor.MetricsService
import no.nav.arrangor.ansatt.repositories.AnsattRepository
import no.nav.arrangor.ansatt.repositories.KoordinatorDeltakerlisteRepository
import no.nav.arrangor.ansatt.repositories.RolleRepository
import no.nav.arrangor.ansatt.repositories.VeilederDeltakerRepository
import no.nav.arrangor.arrangor.ArrangorService
import no.nav.arrangor.client.person.PersonClient
import no.nav.arrangor.domain.Ansatt
import no.nav.arrangor.domain.Arrangor
import no.nav.arrangor.domain.TilknyttetArrangor
import no.nav.arrangor.domain.Veileder
import no.nav.arrangor.domain.VeilederType
import no.nav.arrangor.ingest.PublishService
import no.nav.arrangor.utils.DataUpdateWrapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

@Service
class AnsattService(
	private val personClient: PersonClient,
	private val ansattRepository: AnsattRepository,
	private val koordinatorDeltakerlisteRepository: KoordinatorDeltakerlisteRepository,
	private val veilederDeltakerRepository: VeilederDeltakerRepository,
	private val rolleService: AnsattRolleService,
	private val publishService: PublishService,
	private val metricsService: MetricsService,
	private val arrangorService: ArrangorService,
	private val ansattRolleService: AnsattRolleService
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun get(id: UUID): Ansatt? = ansattRepository.get(id)
		?.let { getAndMaybeUpdateAnsatt(it) }

	fun get(personident: String): Ansatt? {
		return ansattRepository.get(personident)
			?.let { getAndMaybeUpdateAnsatt(it) }
			?: opprettAnsatt(personident)
	}

	fun setKoordinatorForDeltakerliste(personident: String, deltakerlisteId: UUID): Ansatt {
		val ansattDbo = ansattRepository.get(personident) ?: throw NoSuchElementException("Ansatt finnes ikke")

		val currentDeltakerlister = koordinatorDeltakerlisteRepository.getAktive(ansattDbo.id)

		if (currentDeltakerlister.find { it.deltakerlisteId == deltakerlisteId } != null) {
			return getAndMaybeUpdateAnsatt(ansattDbo)
		}

		koordinatorDeltakerlisteRepository.leggTilKoordinatorDeltakerlister(ansattDbo.id, listOf(deltakerlisteId))

		return getAndMaybeUpdateAnsatt(ansattDbo)
			.also { publishService.publishAnsatt(it) }
			.also { metricsService.incLagtTilSomKoordinator() }
			.also { logger.info("Ansatt ${ansattDbo.id} ble koordinator for deltakerliste $deltakerlisteId") }
	}

	fun fjernKoordinatorForDeltakerliste(personident: String, deltakerlisteId: UUID): Ansatt {
		val ansattDbo = ansattRepository.get(personident) ?: throw NoSuchElementException("Ansatt finnes ikke")

		val currentDeltakerlister = koordinatorDeltakerlisteRepository.getAktive(ansattDbo.id)

		currentDeltakerlister.find { it.deltakerlisteId == deltakerlisteId }?.let { it ->
			koordinatorDeltakerlisteRepository.deaktiverKoordinatorDeltakerliste(listOf(it.id))

			return getAndMaybeUpdateAnsatt(ansattDbo)
				.also { ansatt -> publishService.publishAnsatt(ansatt) }
				.also { metricsService.incFjernetSomKoodrinator() }
				.also { logger.info("Ansatt ${ansattDbo.id} mistet koordinator for deltakerliste $deltakerlisteId") }
		}

		return getAndMaybeUpdateAnsatt(ansattDbo)
	}

	fun setVeileder(personident: String, arrangorId: UUID, deltakerId: UUID, type: VeilederType): Ansatt {
		val ansattDbo = ansattRepository.get(personident) ?: throw NoSuchElementException("Ansatt finnes ikke")

		val currentVeilederFor = veilederDeltakerRepository.getAktive(ansattDbo.id)

		currentVeilederFor.find { it.deltakerId == deltakerId }?.let {
			if (it.veilederType != type) {
				veilederDeltakerRepository.deaktiver(listOf(it.id))
				veilederDeltakerRepository.leggTil(
					ansattDbo.id,
					listOf(VeilederDeltakerRepository.VeilederDeltakerInput(deltakerId, arrangorId, type))
				)

				return getAndMaybeUpdateAnsatt(ansattDbo)
					.also { ansatt -> publishService.publishAnsatt(ansatt) }
					.also { metricsService.incLagtTilSomVeileder() }
					.also { _ -> logger.info("Ansatt ${ansattDbo.id} byttet fra ${it.veilederType} til $type for deltaker $deltakerId") }
			}

			return getAndMaybeUpdateAnsatt(ansattDbo)
		}

		veilederDeltakerRepository.leggTil(
			ansattDbo.id,
			listOf(
				VeilederDeltakerRepository.VeilederDeltakerInput(deltakerId, arrangorId, type)
			)
		)

		return getAndMaybeUpdateAnsatt(ansattDbo)
			.also { publishService.publishAnsatt(it) }
			.also { metricsService.incLagtTilSomVeileder() }
			.also { logger.info("Ansatt ${ansattDbo.id} ble $type for deltaker $deltakerId") }
	}

	fun fjernVeileder(personident: String, deltakerId: UUID): Ansatt {
		val ansattDbo = ansattRepository.get(personident) ?: throw NoSuchElementException("Ansatt finnes ikke")

		val currentVeilederFor = veilederDeltakerRepository.getAktive(ansattDbo.id)

		currentVeilederFor.find { it.deltakerId == deltakerId }?.let {
			veilederDeltakerRepository.deaktiver(listOf(it.id))
			return getAndMaybeUpdateAnsatt(ansattDbo)
				.also { publishService.publishAnsatt(it) }
				.also { metricsService.incFjernetSomVeileder() }
				.also { _ -> logger.info("Ansatt ${ansattDbo.id} mistet veilederrolle for $deltakerId") }
		}

		return getAndMaybeUpdateAnsatt(ansattDbo)
	}

	fun opprettAnsatt(personIdent: String): Ansatt? {
		val altinnRoller = ansattRolleService.getRollerFraAltinn(personIdent)
		if (altinnRoller.isEmpty()) {
			logger.info("Bruker uten rettigheter i Altinn har logget seg inn")
			return null
		}
		val person = personClient.hentPersonalia(personIdent).getOrThrow()
		val ansattDbo = ansattRepository.insertOrUpdate(
			AnsattRepository.AnsattDbo(
				personident = personIdent,
				personId = person.id,
				fornavn = person.fornavn,
				mellomnavn = person.mellomnavn,
				etternavn = person.etternavn
			)
		)
		ansattRolleService.lagreRollerForNyAnsatt(ansattDbo.id, altinnRoller)

		logger.info("Opprettet ansatt og lagret roller for ansattId ${ansattDbo.id}")

		return get(ansattDbo.id).also { it?.let { publishService.publishAnsatt(it) } }
	}

	fun oppdaterAnsatte() = ansattRepository.getToSynchronize(
		maxSize = 50,
		synchronizedBefore = LocalDateTime.now().minusDays(7)
	).forEach { oppdaterAnsatt(it) }

	private fun getAndMaybeUpdateAnsatt(ansattDbo: AnsattRepository.AnsattDbo): Ansatt {
		val shouldSynchronize = ansattDbo.lastSynchronized.isBefore(LocalDateTime.now().minusHours(1))

		return if (shouldSynchronize) {
			oppdaterAnsatt(ansattDbo)
		} else {
			val roller = rolleService.getRoller(ansattDbo.id)
			mapToAnsatt(ansattDbo, roller)
		}
	}

	fun oppdaterAnsatt(ansattDbo: AnsattRepository.AnsattDbo): Ansatt {
		var updated = false

		val nyAnsattDbo = oppdaterAnsattDetaljer(ansattDbo)
			.also { if (it.isUpdated) updated = true }
			.data

		val roller = rolleService.oppdaterRoller(nyAnsattDbo.id, nyAnsattDbo.personident)
			.also { if (it.isUpdated) updated = true }
			.data

		return mapToAnsatt(nyAnsattDbo, roller)
			.also { ansattRepository.setSynchronized(it.id) }
			.also { if (updated) publishService.publishAnsatt(it) }
	}

	private fun oppdaterAnsattDetaljer(oldData: AnsattRepository.AnsattDbo): DataUpdateWrapper<AnsattRepository.AnsattDbo> {
		val nyPersonalia = personClient.hentPersonalia(oldData.personident).getOrThrow()
		if (oldData.toPersonalia().navn != nyPersonalia.navn()) {
			logger.info("Ansatt ${oldData.id} har oppdatert personalia")
			val stored = ansattRepository.insertOrUpdate(
				oldData.copy(
					fornavn = nyPersonalia.fornavn,
					mellomnavn = nyPersonalia.mellomnavn,
					etternavn = nyPersonalia.etternavn
				)
			)

			return DataUpdateWrapper(true, stored)
				.also { metricsService.incEndretAnsattPersonalia() }
		}

		return DataUpdateWrapper(false, oldData)
	}

	private fun mapToAnsatt(ansatt: AnsattRepository.AnsattDbo, roller: List<RolleRepository.RolleDbo>): Ansatt {
		val arrangorIds = roller.map { it.arrangorId }.toSet()
		val veilederFor =
			veilederDeltakerRepository.getAktive(ansatt.id).map { Veileder(it.deltakerId, it.veilederType) }
		val koordinatorFor = koordinatorDeltakerlisteRepository.getAktive(ansatt.id).map { it.deltakerlisteId }
		val arrangorer = arrangorIds.map { arrangorService.getArrangorMedOverordnetArrangorForArrangorId(it) }

		return Ansatt(
			id = ansatt.id,
			personalia = ansatt.toPersonalia(),
			arrangorer = arrangorIds.map { arrangorId ->
				val arrangor = arrangorer.find { it.id == arrangorId } ?: throw IllegalStateException("Mangler arrangør med id $arrangorId")
				TilknyttetArrangor(
					arrangorId = arrangorId,
					arrangor = Arrangor(
						id = arrangor.id,
						navn = arrangor.navn,
						organisasjonsnummer = arrangor.organisasjonsnummer,
						overordnetArrangorId = arrangor.overordnetArrangor?.id
					),
					overordnetArrangor = arrangor.overordnetArrangor?.let {
						Arrangor(
							id = it.id,
							navn = it.navn,
							organisasjonsnummer = it.organisasjonsnummer,
							overordnetArrangorId = it.overordnetArrangorId
						)
					},
					roller = roller.filter { it.arrangorId == arrangorId }.map { it.rolle },
					veileder = veilederFor,
					koordinator = koordinatorFor
				)
			}
		)
	}
}
