package no.nav.arrangor.client.person

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PersonClient(
    private val personApi: PersonApi,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun hentPersonalia(personident: String): Result<PersonApi.PersonResponse> = runCatching {
        personApi
            .hentPersonalia(PersonApi.PersonRequest(personident))
            .let {
                PersonApi.PersonResponse(
                    id = it.id,
                    personident = it.personident,
                    fornavn = it.fornavn,
                    mellomnavn = it.mellomnavn,
                    etternavn = it.etternavn,
                )
            }.also { log.debug("Hentet personalia for person") }
    }
}
