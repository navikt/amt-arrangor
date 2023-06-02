package no.nav.arrangor.ansatt

import no.nav.arrangor.MetricsService
import no.nav.arrangor.ansatt.repository.AnsattDbo
import no.nav.arrangor.ansatt.repository.ArrangorDbo
import no.nav.arrangor.ansatt.repository.RolleDbo
import no.nav.arrangor.arrangor.ArrangorService
import no.nav.arrangor.client.altinn.AltinnAclClient
import no.nav.arrangor.client.altinn.AltinnRolle
import no.nav.arrangor.domain.AnsattRolle
import no.nav.arrangor.domain.Arrangor
import no.nav.arrangor.domain.TilknyttetArrangor
import no.nav.arrangor.utils.DataUpdateWrapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.ZonedDateTime
import java.util.UUID

@Service
class AnsattRolleService(
	private val altinnClient: AltinnAclClient,
	private val metricsService: MetricsService,
	private val arrangorService: ArrangorService
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun getRollerFraAltinn(personIdent: String): List<AltinnRolle> {
		return altinnClient.hentRoller(personIdent).getOrThrow()
	}

	fun getArrangorListeForNyAnsatt(roller: List<AltinnRolle>): List<ArrangorDbo> {
		val unikeOrgnummer = roller.map { it.organisasjonsnummer }.distinct()
		val arrangorer = unikeOrgnummer.map { arrangorService.getOrUpsert(it) }

		val arrangorListe = roller.mapNotNull { altinnRolle ->
			val arrangor = arrangorer.find { altinnRolle.organisasjonsnummer == it.organisasjonsnummer }
			if (arrangor != null) {
				ArrangorDbo(
					arrangorId = arrangor.id,
					roller = altinnRolle.roller.map { RolleDbo(it) },
					veileder = emptyList(),
					koordinator = emptyList()
				)
			} else {
				null
			}
		}
		return arrangorListe
	}

	fun getAnsattDboMedOppdaterteRoller(ansattDbo: AnsattDbo, personident: String): DataUpdateWrapper<AnsattDbo> {
		val nyeRollerFraAltinn = altinnClient.hentRoller(personident).getOrThrow()

		val unikeOrgnummerFraAltinn = nyeRollerFraAltinn.map { it.organisasjonsnummer }.distinct()
		val unikeArrangorerFraAltinnMedRolle = arrangorService.get(unikeOrgnummerFraAltinn) // skal endres til getOrUpsert når denne appen er master

		val nyeRoller = altinnToRolleOgArrangor(nyeRollerFraAltinn, unikeArrangorerFraAltinnMedRolle)

		return getAnsattDboMedOppdaterteRoller(ansattDbo, nyeRoller)
	}

	fun getOppdatertArrangorListeForIngestAvAnsatt(ansattDbo: AnsattDbo, tilknyttedeArrangorer: List<TilknyttetArrangor>): List<ArrangorDbo> {
		val nyeRoller = tilknyttetArrangorToRolleOgArrangor(tilknyttedeArrangorer)

		return getAnsattDboMedOppdaterteRoller(ansattDbo, nyeRoller).data.arrangorer
	}

	private fun getAnsattDboMedOppdaterteRoller(ansattDbo: AnsattDbo, nyeRoller: List<RolleOgArrangor>): DataUpdateWrapper<AnsattDbo> {
		val gamleAktiveRoller = ansattDbo.arrangorer.flatMap { arrangor ->
			arrangor.roller
				.filter { it.erGyldig() }
				.map { RolleOgArrangor(arrangor.arrangorId, it.rolle) }
		}

		val rollerSomSkalDeaktiveres = gamleAktiveRoller
			.filter { nyeRoller.find { nyRolle -> nyRolle.rolle == it.rolle && nyRolle.arrangorId == it.arrangorId } == null }
		val rollerSomSkalLeggesTil = nyeRoller
			.filter { gamleAktiveRoller.find { gammelRolle -> gammelRolle.rolle == it.rolle && gammelRolle.arrangorId == it.arrangorId } == null }

		val oppdaterteArrangorerForAnsatt = getOppdaterteArrangorerForAnsatt(ansattDbo, rollerSomSkalDeaktiveres, rollerSomSkalLeggesTil)

		if (rollerSomSkalDeaktiveres.isNotEmpty()) {
			logFjernet(ansattDbo.id, rollerSomSkalDeaktiveres)
		}
		if (rollerSomSkalLeggesTil.isNotEmpty()) {
			logLagtTil(ansattDbo.id, rollerSomSkalLeggesTil)
		}
		val isUpdated = rollerSomSkalDeaktiveres.isNotEmpty() || rollerSomSkalLeggesTil.isNotEmpty()
		if (isUpdated) {
			metricsService.incEndretAnsattRolle(rollerSomSkalLeggesTil.size + rollerSomSkalDeaktiveres.size)
		}

		return DataUpdateWrapper(
			isUpdated = isUpdated,
			data = ansattDbo.copy(arrangorer = oppdaterteArrangorerForAnsatt)
		)
	}

	private fun getOppdaterteArrangorerForAnsatt(
		ansattDbo: AnsattDbo,
		rollerSomSkalDeaktiveres: List<RolleOgArrangor>,
		rollerSomSkalLeggesTil: List<RolleOgArrangor>
	): List<ArrangorDbo> {
		val oppdaterteArrangorer = mutableListOf<ArrangorDbo>()
		oppdaterteArrangorer.addAll(ansattDbo.arrangorer)

		rollerSomSkalDeaktiveres.forEach { rolleOgArrangor ->
			val arrangor = oppdaterteArrangorer.find { it.arrangorId == rolleOgArrangor.arrangorId }
			if (arrangor != null) {
				if (rolleOgArrangor.rolle == AnsattRolle.KOORDINATOR) {
					arrangor.koordinator.forEach {
						if (it.erGyldig()) {
							it.gyldigTil = ZonedDateTime.now()
						}
					}
				}
				if (rolleOgArrangor.rolle == AnsattRolle.VEILEDER) {
					arrangor.veileder.forEach {
						if (it.erGyldig()) {
							it.gyldigTil = ZonedDateTime.now()
						}
					}
				}
				arrangor.roller.find { it.erGyldig() && it.rolle == rolleOgArrangor.rolle }?.let { it.gyldigTil = ZonedDateTime.now() }
			} else {
				logger.warn("Kan ikke deaktivere rolle hos arrangør som ikke er koblet til ansatt, arrangørid ${rolleOgArrangor.arrangorId}, ansattId ${ansattDbo.id}")
			}
		}

		rollerSomSkalLeggesTil.forEach { rolleOgArrangor ->
			val arrangor = oppdaterteArrangorer.find { it.arrangorId == rolleOgArrangor.arrangorId }
			if (arrangor != null) {
				val eksisterendeRolle = arrangor.roller.find { it.rolle == rolleOgArrangor.rolle }
				if (eksisterendeRolle != null && !eksisterendeRolle.erGyldig()) {
					eksisterendeRolle.gyldigTil = null
				} else {
					val oppdaterteRoller = mutableListOf<RolleDbo>()
					oppdaterteRoller.addAll(arrangor.roller)
					oppdaterteRoller.add(RolleDbo(rolleOgArrangor.rolle))
					val oppdatertArrangor = arrangor.copy(roller = oppdaterteRoller)
					oppdaterteArrangorer.add(oppdatertArrangor)
				}
			} else {
				oppdaterteArrangorer.add(
					ArrangorDbo(
						arrangorId = rolleOgArrangor.arrangorId,
						roller = listOf(RolleDbo(rolleOgArrangor.rolle)),
						koordinator = emptyList(),
						veileder = emptyList()
					)
				)
			}
		}
		return oppdaterteArrangorer
	}

	private fun logFjernet(ansattId: UUID, fjernet: List<RolleOgArrangor>) = fjernet.forEach {
		logger.info("Ansatt med $ansattId mistet ${it.rolle} hos ${it.arrangorId}")
	}

	private fun logLagtTil(ansattId: UUID, lagtTil: List<RolleOgArrangor>) = lagtTil.forEach {
		logger.info("Ansatt med $ansattId fikk ${it.rolle} hos ${it.arrangorId}")
	}

	private fun altinnToRolleOgArrangor(roller: List<AltinnRolle>, arrangorer: List<Arrangor>): List<RolleOgArrangor> {
		return roller
			.flatMap { entry ->
				entry.roller.map {
					val arrangorId = arrangorer.find { arrangor -> arrangor.organisasjonsnummer == entry.organisasjonsnummer }?.id
					arrangorId to it
				}
			}.mapNotNull { it.first?.let { arrangorId -> RolleOgArrangor(arrangorId, it.second) } }
	}

	private fun tilknyttetArrangorToRolleOgArrangor(tilknyttedeArrangorer: List<TilknyttetArrangor>): List<RolleOgArrangor> {
		return tilknyttedeArrangorer
			.flatMap { entry -> entry.roller.map { entry.arrangorId to it } }
			.map { RolleOgArrangor(it.first, it.second) }
	}

	private data class RolleOgArrangor(
		val arrangorId: UUID,
		val rolle: AnsattRolle
	)
}
