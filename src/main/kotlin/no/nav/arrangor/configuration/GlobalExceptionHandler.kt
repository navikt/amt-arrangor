package no.nav.arrangor.configuration

import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(Exception::class)
    fun handleException(ex: Exception, request: HttpServletRequest): ResponseEntity<Response> {
        return when (ex) {
            is NoSuchElementException -> buildResponse(HttpStatus.NOT_FOUND, ex)
            is IllegalStateException -> buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex)
            else -> {
                log.error("Internal server error - ${ex.message} - ${request.method}: ${request.requestURI}", ex)
                buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex)
            }
        }
    }

    private fun buildResponse(
        status: HttpStatus,
        exception: Throwable
    ): ResponseEntity<Response> {
        if (status.is4xxClientError) {
            log.warn("Noe er feil med request: ${exception.message}, statuskode ${status.value()}", exception)
        } else {
            log.error("Noe gikk galt: ${exception.message}, statuskode ${status.value()}", exception)
        }
        return ResponseEntity
            .status(status)
            .body(
                Response(
                    status = status.value(),
                    title = status,
                    detail = exception.message
                )
            )
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class Response(
        val status: Int,
        val title: HttpStatus,
        val detail: String?
    )
}
