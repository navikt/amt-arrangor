package no.nav.arrangor.ansatt

import no.nav.arrangor.ansatt.repository.AnsattDbo
import no.nav.arrangor.ansatt.repository.ArrangorDbo
import no.nav.arrangor.ansatt.repository.RolleDbo
import no.nav.arrangor.arrangor.ArrangorService
import no.nav.arrangor.client.altinn.AltinnAclClient
import no.nav.arrangor.client.altinn.AltinnRolle
import no.nav.arrangor.domain.AnsattRolle
import no.nav.arrangor.domain.Arrangor
import no.nav.arrangor.utils.DataUpdateWrapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

@Service
class AnsattRolleService(
	private val altinnClient: AltinnAclClient,
	private val arrangorService: ArrangorService
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun getRollerFraAltinn(personIdent: String): List<AltinnRolle> {
		return altinnClient.hentRoller(personIdent).getOrThrow()
	}

	fun mapAltinnRollerTilArrangorListeForNyAnsatt(roller: List<AltinnRolle>): List<ArrangorDbo> {
		val unikeOrgnummer = roller.map { it.organisasjonsnummer }.distinct()
		val arrangorer = unikeOrgnummer.map { arrangorService.getOrCreate(it) }

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

	fun getAnsattDboMedOppdaterteRoller(ansattDbo: AnsattDbo): DataUpdateWrapper<AnsattDbo> {
		val nyeRollerFraAltinn = altinnClient.hentRoller(ansattDbo.personident).getOrThrow()

		val unikeOrgnummerFraAltinn = nyeRollerFraAltinn.map { it.organisasjonsnummer }.distinct()
		val unikeArrangorerFraAltinnMedRolle = arrangorService.getOrCreate(unikeOrgnummerFraAltinn)

		val nyeRoller = altinnToRolleOgArrangor(nyeRollerFraAltinn, unikeArrangorerFraAltinnMedRolle)

		return getAnsattDboMedOppdaterteRoller(ansattDbo, nyeRoller)
	}

	private fun getAnsattDboMedOppdaterteRoller(
		ansattDbo: AnsattDbo,
		aktiveRoller: List<RolleOgArrangor>
	): DataUpdateWrapper<AnsattDbo> {
		val gamleAktiveRoller = ansattDbo.arrangorer.flatMap { arrangor ->
			arrangor.roller.filter { it.erGyldig() }
				.map {
					RolleOgArrangor(
						arrangorId = arrangor.arrangorId,
						rolle = it.rolle
					)
				}
		}

		val rollerSomSkalDeaktiveres = gamleAktiveRoller.filter { gammelRolle ->
			aktiveRoller.none { nyRolle ->
				nyRolle.rolle == gammelRolle.rolle && nyRolle.arrangorId == gammelRolle.arrangorId
			}
		}

		val oppdaterteArrangorer = getOppdaterteArrangorerForAnsatt(ansattDbo, rollerSomSkalDeaktiveres, aktiveRoller)

		return DataUpdateWrapper(
			isUpdated = rollerSomSkalDeaktiveres.isNotEmpty() || aktiveRoller.size > gamleAktiveRoller.size,
			data = ansattDbo.copy(
				arrangorer = oppdaterteArrangorer,
				lastSynchronized = LocalDateTime.now()
			)
		)
	}

	private fun getOppdaterteArrangorerForAnsatt(
		ansattDbo: AnsattDbo,
		rollerSomSkalDeaktiveres: List<RolleOgArrangor>,
		aktiveRoller: List<RolleOgArrangor>
	): List<ArrangorDbo> {
		val oppdaterteArrangorer = mutableListOf<ArrangorDbo>()
		oppdaterteArrangorer.addAll(ansattDbo.arrangorer)

		rollerSomSkalDeaktiveres.forEach { rolleOgArrangor ->
			val arrangor = oppdaterteArrangorer.find { it.arrangorId == rolleOgArrangor.arrangorId }
			deaktiverRolle(arrangor, rolleOgArrangor, ansattDbo)
		}

		aktiveRoller.forEach { rolleOgArrangor ->
			leggTilRolle(oppdaterteArrangorer, rolleOgArrangor, ansattDbo)
		}
		return oppdaterteArrangorer
	}

	private fun deaktiverRolle(
		arrangor: ArrangorDbo?,
		rolleOgArrangor: RolleOgArrangor,
		ansatt: AnsattDbo
	) {
		if (arrangor == null) {
			logger.warn("Kan ikke deaktivere rolle hos arrangør som ikke er koblet til ansatt, arrangørid ${rolleOgArrangor.arrangorId}, ansattId ${ansatt.id}")
			return
		}

		when (rolleOgArrangor.rolle) {
			AnsattRolle.KOORDINATOR -> arrangor.koordinator.forEach {
				if (it.erGyldig()) it.gyldigTil = ZonedDateTime.now()
			}
			AnsattRolle.VEILEDER -> arrangor.veileder.forEach {
				if (it.erGyldig()) it.gyldigTil = ZonedDateTime.now()
			}
		}

		arrangor.roller.find { it.erGyldig() && it.rolle == rolleOgArrangor.rolle }
			?.let { it.gyldigTil = ZonedDateTime.now() }

		logger.info("Ansatt med ${ansatt.id} mistet ${rolleOgArrangor.rolle} hos ${arrangor.arrangorId}")
	}

	private fun leggTilRolle(
		oppdaterteArrangorer: MutableList<ArrangorDbo>,
		rolleOgArrangor: RolleOgArrangor,
		ansatt: AnsattDbo
	) {
		val arrangor = oppdaterteArrangorer.find { it.arrangorId == rolleOgArrangor.arrangorId }

		val eksisterendeRolle = arrangor?.roller?.find { it.rolle == rolleOgArrangor.rolle }
		if (eksisterendeRolle?.erGyldig() == true) {
			return
		}

		val oppdatertArrangor = arrangor?.copy(roller = arrangor.roller.plus(RolleDbo(rolleOgArrangor.rolle)))
			?: ArrangorDbo(
				arrangorId = rolleOgArrangor.arrangorId,
				roller = listOf(RolleDbo(rolleOgArrangor.rolle)),
				koordinator = emptyList(),
				veileder = emptyList()
			)

		oppdaterteArrangorer.remove(arrangor)
		oppdaterteArrangorer.add(oppdatertArrangor)

		logger.info("Ansatt med ${ansatt.id} fikk ${rolleOgArrangor.rolle} hos ${oppdatertArrangor.arrangorId}")
	}

	private fun altinnToRolleOgArrangor(roller: List<AltinnRolle>, arrangorer: List<Arrangor>): List<RolleOgArrangor> {
		return roller.flatMap { altinnRolle ->
			kombinerRollerOgArrangor(altinnRolle, arrangorer)
		}
	}

	private fun kombinerRollerOgArrangor(
		altinnRolle: AltinnRolle,
		arrangorer: List<Arrangor>
	) = altinnRolle.roller.mapNotNull { ansattRolle ->
		val arrangor = arrangorer.find { arrangor -> arrangor.organisasjonsnummer == altinnRolle.organisasjonsnummer }
			?: return@mapNotNull null
		RolleOgArrangor(
			arrangorId = arrangor.id,
			rolle = ansattRolle
		)
	}

	private data class RolleOgArrangor(
		val arrangorId: UUID,
		val rolle: AnsattRolle
	)
}
