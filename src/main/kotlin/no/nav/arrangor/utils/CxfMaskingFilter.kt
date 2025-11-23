package no.nav.arrangor.utils

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.filter.Filter
import ch.qos.logback.core.spi.FilterReply

class CxfMaskingFilter : Filter<ILoggingEvent>() {
	override fun decide(event: ILoggingEvent): FilterReply = if (event.level != Level.ERROR &&
		exclusionList.any { event.loggerName.startsWith(it) }
	) {
		FilterReply.DENY
	} else {
		FilterReply.NEUTRAL
	}

	companion object {
		private val exclusionList = listOf(
			"org.apache.cxf",
			"no.nav.common.cxf",
		)
	}
}
