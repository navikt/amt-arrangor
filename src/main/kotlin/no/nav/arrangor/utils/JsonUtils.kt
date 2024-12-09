package no.nav.arrangor.utils
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.InputStream

object JsonUtils {
	val mapper: ObjectMapper =
		ObjectMapper()
			.registerKotlinModule()
			.registerModule(JavaTimeModule())
			.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

	fun objectMapper(): ObjectMapper = mapper

	inline fun <reified T> fromJson(jsonStr: String): T = mapper.readValue(jsonStr)

	fun <T> fromJson(jsonNode: JsonNode, clazz: Class<T>): T = mapper.treeToValue(jsonNode, clazz)

	fun <T> fromJson(jsonStr: String, clazz: Class<T>): T = mapper.readValue(jsonStr, clazz)

	fun <T> fromJson(stream: InputStream, clazz: Class<T>): T = mapper.readValue(stream, clazz)

	fun toJson(any: Any): String = mapper.writeValueAsString(any)
}
