package no.nav.arrangor.utils

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.arrangor.utils.UrlUtils.toUriString
import org.junit.jupiter.api.Test
import org.springframework.web.util.InvalidUrlException

class UrlUtilsTest {
	@Test
	fun `skal returnere samme URL nar URL starter med http `() {
		val url = toUriString("http://localhost")

		url shouldBe "http://localhost"
	}

	@Test
	fun `skal returnere URL som starter med http nar URL ikke starter med skjema`() {
		val url = toUriString("localhost")

		url shouldBe "http://localhost"
	}

	@Test
	fun `skal kaste feil nar URL inneholder ugyldige tegn`() {
		val thrown = shouldThrow<InvalidUrlException> {
			toUriString("some-url/foo%ZZ")
		}

		thrown.message shouldBe "Bad path"
	}
}
