package no.nav.arrangor.testutils

import io.kotest.matchers.date.shouldBeWithin
import io.kotest.matchers.shouldNotBe
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import java.time.Duration
import java.time.ZonedDateTime
import javax.sql.DataSource

object DbTestDataUtils {
	private const val SCHEMA = "public"

	private const val FLYWAY_SCHEMA_HISTORY_TABLE_NAME = "flyway_schema_history"

	infix fun ZonedDateTime.shouldBeEqualTo(expected: ZonedDateTime?) {
		expected shouldNotBe null
		expected!!.shouldBeWithin(Duration.ofSeconds(1), this)
	}

	fun runScript(dataSource: DataSource, script: String) {
		val jdbcTemplate = JdbcTemplate(dataSource)
		jdbcTemplate.update(script)
	}

	fun runScriptFile(dataSource: DataSource, scriptFilePath: String) {
		val script = javaClass.getResource(scriptFilePath).readText()
		runScript(dataSource, script)
	}

	fun cleanDatabase(dataSource: DataSource) {
		val jdbcTemplate = JdbcTemplate(dataSource)

		val tables = getAllTables(jdbcTemplate, SCHEMA).filter { it != FLYWAY_SCHEMA_HISTORY_TABLE_NAME }
		val sequences = getAllSequences(jdbcTemplate, SCHEMA)

		tables.forEach {
			jdbcTemplate.update("TRUNCATE TABLE $it CASCADE")
		}

		sequences.forEach {
			jdbcTemplate.update("ALTER SEQUENCE $it RESTART WITH 1")
		}
	}

	fun <V> parameters(vararg pairs: Pair<String, V>): MapSqlParameterSource = MapSqlParameterSource().addValues(pairs.toMap())

	private fun getAllTables(jdbcTemplate: JdbcTemplate, schema: String): List<String> {
		val sql = "SELECT table_name FROM information_schema.tables WHERE table_schema = ?"

		return jdbcTemplate.query(sql, { rs, _ -> rs.getString(1) }, schema)
	}

	private fun getAllSequences(jdbcTemplate: JdbcTemplate, schema: String): List<String> {
		val sql = "SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema = ?"

		return jdbcTemplate.query(sql, { rs, _ -> rs.getString(1) }, schema)
	}
}
