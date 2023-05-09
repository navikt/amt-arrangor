package no.nav.arrangor.testutils

import no.nav.arrangor.ansatt.repositories.AnsattRepository
import no.nav.arrangor.arrangor.ArrangorRepository
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
        lastSynchronized: LocalDateTime = LocalDateTime.now()
    ): AnsattRepository.AnsattDbo = ansatt(personident, personId, fornavn, mellomnavn, etternavn, lastSynchronized)
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
        lastSynchronized: LocalDateTime = LocalDateTime.now()
    ): AnsattRepository.AnsattDbo = AnsattRepository.AnsattDbo(
        personident = personident,
        personId = personId,
        fornavn = fornavn,
        mellomnavn = mellomnavn,
        etternavn = etternavn,
        lastSynchronized = lastSynchronized
    )

    fun arrangor(
        navn: String = UUID.randomUUID().toString(),
        organisasjonsnummer: String = UUID.randomUUID().toString(),
        overordnetArrangorId: UUID? = null
    ): ArrangorRepository.ArrangorDbo = ArrangorRepository.ArrangorDbo(
        navn = navn,
        organisasjonsnummer = organisasjonsnummer,
        overordnetArrangorId = overordnetArrangorId
    )
}
