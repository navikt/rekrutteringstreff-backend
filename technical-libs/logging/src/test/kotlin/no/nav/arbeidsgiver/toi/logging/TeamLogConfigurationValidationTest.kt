package no.nav.arbeidsgiver.toi.logging

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.boolex.OnMarkerEvaluator
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import ch.qos.logback.core.filter.EvaluatorFilter
import ch.qos.logback.core.spi.FilterReply
import no.nav.arbeidsgiver.toi.logging.TeamLogLogger.Companion.teamlogsAppenderName
import no.nav.arbeidsgiver.toi.logging.TeamLogLogger.Companion.teamlogsMarkerName
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.slf4j.Logger

class TeamLogConfigurationValidationTest {

    @Test
    fun `bruk av TeamLogLogger avvises når rootLogger mangler teamlog-appender`() {
        val rootLogger = nyRootLogger()

        assertThatThrownBy {
            TeamLogLogger.validateTeamlogConfiguration(rootLogger)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("logback.xml mangler ROOT-appender")
    }

    @Test
    fun `bruk av TeamLogLogger avvises når teamlog-appender mangler TEAM_LOGS-filter`() {
        val rootLogger = nyRootLogger().apply {
            addAppender(nyAppender(loggerContext, teamlogsAppenderName))
        }

        assertThatThrownBy {
            TeamLogLogger.validateTeamlogConfiguration(rootLogger)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("logback.xml mangler markerfilter på ROOT-appender")
    }

    @Test
    fun `bruk av TeamLogLogger tillates når teamlog er konfigurert riktig`() {
        val rootLogger = nyRootLogger().apply {
            addAppender(
                nyAppender(loggerContext, teamlogsAppenderName).apply {
                    addFilter(nyTeamLogsAksepterendeFilter(loggerContext))
                }
            )
        }

        assertThatCode {
            TeamLogLogger.validateTeamlogConfiguration(rootLogger)
        }.doesNotThrowAnyException()
    }

    @Test
    fun `bruk av TeamLogLogger utenfor Nais-cluster tillates uten teamlog-konfigurasjon`() {
        assumeTrue(System.getenv("NAIS_CLUSTER_NAME") == null, "Testen gjelder kun utenfor NAIS-cluster")
        val logger = org.slf4j.LoggerFactory.getLogger("test")

        assertThatCode {
            TeamLogLogger.teamlog(logger)
        }.doesNotThrowAnyException()
    }

    @Test
    fun `bruk av TeamLogLogger avvises når ordinær appender mangler filter som avviser TEAM_LOGS`() {
        val rootLogger = nyRootLogger().apply {
            addAppender(
                nyAppender(loggerContext, teamlogsAppenderName).apply {
                    addFilter(nyTeamLogsAksepterendeFilter(loggerContext))
                }
            )
            addAppender(nyAppender(loggerContext, "STDOUT_JSON"))
        }

        assertThatThrownBy {
            TeamLogLogger.validateTeamlogConfiguration(rootLogger)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Alle ROOT-appendere unntatt 'team-logs' må avvise marker 'TEAM_LOGS'")
    }

    @Test
    fun `bruk av TeamLogLogger tillates når ordinær appender har filter som avviser TEAM_LOGS`() {
        val rootLogger = nyRootLogger().apply {
            addAppender(
                nyAppender(loggerContext, teamlogsAppenderName).apply {
                    addFilter(nyTeamLogsAksepterendeFilter(loggerContext))
                }
            )
            addAppender(
                nyAppender(loggerContext, "STDOUT_JSON").apply {
                    addFilter(nyTeamLogsAvvisendeFilter(loggerContext))
                }
            )
        }

        assertThatCode {
            TeamLogLogger.validateTeamlogConfiguration(rootLogger)
        }.doesNotThrowAnyException()
    }

    private fun nyRootLogger(): ch.qos.logback.classic.Logger =
        LoggerContext().getLogger(Logger.ROOT_LOGGER_NAME).apply { detachAndStopAllAppenders() }

    private fun nyAppender(context: LoggerContext, navn: String): AppenderBase<ILoggingEvent> {
        return object : AppenderBase<ILoggingEvent>() {
            override fun append(eventObject: ILoggingEvent?) = Unit
        }.apply {
            this.context = context
            this.name = navn
            start()
        }
    }

    private fun nyTeamLogsAksepterendeFilter(context: LoggerContext): EvaluatorFilter<ILoggingEvent> {
        val evaluator = OnMarkerEvaluator().apply {
            this.context = context
            addMarker(teamlogsMarkerName)
            start()
        }

        return EvaluatorFilter<ILoggingEvent>().apply {
            this.context = context
            this.evaluator = evaluator
            this.onMatch = FilterReply.ACCEPT
            this.onMismatch = FilterReply.DENY
            start()
        }
    }

    private fun nyTeamLogsAvvisendeFilter(context: LoggerContext): EvaluatorFilter<ILoggingEvent> {
        val evaluator = OnMarkerEvaluator().apply {
            this.context = context
            addMarker(teamlogsMarkerName)
            start()
        }

        return EvaluatorFilter<ILoggingEvent>().apply {
            this.context = context
            this.evaluator = evaluator
            this.onMatch = FilterReply.DENY
            this.onMismatch = FilterReply.ACCEPT
            start()
        }
    }
}
