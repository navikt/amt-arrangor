package no.nav.arrangor.ansatt

import no.nav.arrangor.ansatt.repositories.AnsattRepository
import no.nav.arrangor.ansatt.repositories.RolleRepository
import no.nav.arrangor.arrangor.ArrangorRepository
import no.nav.arrangor.client.person.PersonClient
import no.nav.arrangor.domain.Ansatt
import no.nav.arrangor.domain.VeilederType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AnsattService(
    private val personClient: PersonClient,
    private val arrangorRepository: ArrangorRepository,
    private val ansattRepository: AnsattRepository,
    private val rolleRepository: RolleRepository
) {

    fun get(id: UUID): Ansatt? = ansattRepository.get(id)?.let { getAnsatt(it) }

    fun get(personident: String): Ansatt? {
        val dbo = ansattRepository.get(personident)

        TODO()
    }

    fun setKoordinatorForDeltakerliste(personident: String, deltakerlisteId: UUID): Ansatt {
        TODO()
    }

    fun fjernKoordinatorForDeltakerliste(personident: String, deltakerlisteId: UUID): Ansatt {
        TODO()
    }

    fun setVeileder(personident: String, deltakerId: UUID, type: VeilederType): Ansatt {
        TODO()
    }

    fun fjernVeileder(personident: String, deltakerId: UUID): Ansatt {
        TODO()
    }

    @Transactional
    fun opprettAnsatt(personIdent: String): Result<Ansatt> {
        TODO()
    }

    @Transactional
    fun oppdaterAnsatt(id: UUID): Ansatt {
        TODO()
    }

    private fun getAnsatt(ansattDbo: AnsattRepository.AnsattDbo): Ansatt {
        return Ansatt(
            id = ansattDbo.id,
            personalia = ansattDbo.toPersonalia(),
            arrangorer = emptyList()
        )
    }
}
