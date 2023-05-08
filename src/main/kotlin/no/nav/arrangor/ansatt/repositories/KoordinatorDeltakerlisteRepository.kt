package no.nav.arrangor.ansatt.repositories

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class KoordinatorDeltakerlisteRepository(
    private val template: NamedParameterJdbcTemplate
)
