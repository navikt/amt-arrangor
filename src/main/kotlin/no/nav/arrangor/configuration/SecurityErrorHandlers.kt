package no.nav.arrangor.configuration

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Sørger for at 401/403 fra Spring Security-filterkjeden (som kjører før DispatcherServlet
 * og dermed ikke fanges av GlobalExceptionHandler) returnerer samme Response-kontrakt som
 * resten av APIet.
 */
@Component
class RestAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper,
) : AuthenticationEntryPoint {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        // Denne kalles både når det mangler Authorization-header (ExceptionTranslationFilter) og når
        // selve JWT-en er ugyldig (BearerTokenAuthenticationFilter) - se SecurityConfig for hvor begge
        // kobles til denne samme beanen.
        // Full exception-melding logges kun server-side. Den kan inneholde interne detaljer
        // (forventet issuer/audience, årsak til token-parsing-feil o.l.) som ikke skal ut i
        // responsen til en uautentisert kaller.
        log.warn("Uautentisert forespørsel: ${authException.message}, ${request.method} ${request.requestURI}")
        response.writeErrorResponse(objectMapper, HttpStatus.UNAUTHORIZED, "Ikke autentisert")
    }
}

@Component
class RestAccessDeniedHandler(
    private val objectMapper: ObjectMapper,
) : AccessDeniedHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) {
        // Samme begrunnelse som i RestAuthenticationEntryPoint - ikke lekk interne detaljer
        // om hvorfor tilgang ble avvist.
        log.warn("Ikke tilgang til ressurs: ${accessDeniedException.message}, ${request.method} ${request.requestURI}")
        response.writeErrorResponse(objectMapper, HttpStatus.FORBIDDEN, "Ikke tilgang")
    }
}

// Skriver direkte til outputStream fordi vi er i Spring Security-filterkjeden, før DispatcherServlet
// og HttpMessageConverter-maskineriet er i bildet - se klassekommentaren øverst i filen.
// Gjenbruker GlobalExceptionHandler.Response slik at kontrakten er lik uansett hvor i kjeden feilen oppstår.
private fun HttpServletResponse.writeErrorResponse(
    objectMapper: ObjectMapper,
    status: HttpStatus,
    detail: String?,
) {
    this.status = status.value()
    contentType = MediaType.APPLICATION_JSON_VALUE
    objectMapper.writeValue(
        outputStream,
        GlobalExceptionHandler.Response(
            status = status.value(),
            // HttpStatus serialiseres til f.eks. "401 UNAUTHORIZED" (kode + navn), ikke bare "UNAUTHORIZED",
            // fordi Jackson 3 som standard bruker Enum.toString() ved enum-serialisering.
            title = status,
            detail = detail,
        ),
    )
}
