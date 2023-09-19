package no.nav.arrangor.testutils

import no.nav.arrangor.ansatt.repository.AnsattDbo
import no.nav.arrangor.ansatt.repository.AnsattRepository
import no.nav.arrangor.ansatt.repository.ArrangorDbo
import no.nav.arrangor.ansatt.repository.KoordinatorsDeltakerlisteDbo
import no.nav.arrangor.ansatt.repository.RolleDbo
import no.nav.arrangor.ansatt.repository.VeilederDeltakerDbo
import no.nav.arrangor.arrangor.ArrangorRepository
import no.nav.arrangor.domain.AnsattRolle
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.LocalDateTime
import java.util.UUID

class DbTestData(
	template: NamedParameterJdbcTemplate
) {

	val ansattRepository = AnsattRepository(template)
	val arrangorRepository = ArrangorRepository(template)

	fun insertAnsatt(
		personident: String = UUID.randomUUID().toString(),
		personId: UUID = UUID.randomUUID(),
		fornavn: String = UUID.randomUUID().toString(),
		mellomnavn: String = UUID.randomUUID().toString(),
		etternavn: String = UUID.randomUUID().toString(),
		arrangorer: List<ArrangorDbo>,
		lastSynchronized: LocalDateTime = LocalDateTime.now()
	): AnsattDbo = ansatt(personident, personId, fornavn, mellomnavn, etternavn, arrangorer, lastSynchronized)
		.let { ansattRepository.insertOrUpdate(it) }

	fun insertArrangor(
		navn: String = UUID.randomUUID().toString(),
		organisasjonsnummer: String = UUID.randomUUID().toString(),
		overordnetArrangorId: UUID? = null
	): ArrangorRepository.ArrangorDbo = arrangor(navn, organisasjonsnummer, overordnetArrangorId)
		.let { arrangorRepository.insertOrUpdate(it) }

	fun ansatt(
		personident: String = UUID.randomUUID().toString(),
		personId: UUID = UUID.randomUUID(),
		fornavn: String = UUID.randomUUID().toString(),
		mellomnavn: String = UUID.randomUUID().toString(),
		etternavn: String = UUID.randomUUID().toString(),
		arrangorer: List<ArrangorDbo> = listOf(ArrangorDbo(UUID.randomUUID(), listOf(RolleDbo(AnsattRolle.KOORDINATOR)), emptyList(), emptyList())),
		lastSynchronized: LocalDateTime = LocalDateTime.now()
	): AnsattDbo = AnsattDbo(
		id = UUID.randomUUID(),
		personident = personident,
		personId = personId,
		fornavn = fornavn,
		mellomnavn = mellomnavn,
		etternavn = etternavn,
		arrangorer = arrangorer,
		lastSynchronized = lastSynchronized
	)

	fun arrangor(
		navn: String = UUID.randomUUID().toString(),
		organisasjonsnummer: String = UUID.randomUUID().toString(),
		overordnetArrangorId: UUID? = null
	): ArrangorRepository.ArrangorDbo = ArrangorRepository.ArrangorDbo(
		id = UUID.randomUUID(),
		navn = navn,
		organisasjonsnummer = organisasjonsnummer,
		overordnetArrangorId = overordnetArrangorId
	)

	fun ansattArrangorDbo(
		arrangorId: UUID = UUID.randomUUID(),
		roller: List<RolleDbo> = listOf(RolleDbo(AnsattRolle.KOORDINATOR)),
		veileder: List<VeilederDeltakerDbo> = listOf(),
		koordinator: List<KoordinatorsDeltakerlisteDbo> = listOf(
			KoordinatorsDeltakerlisteDbo(UUID.randomUUID())
		)
	) = ArrangorDbo(
		arrangorId = arrangorId,
		roller = roller,
		veileder = veileder,
		koordinator = koordinator
	)
}
