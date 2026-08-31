package no.nav.arbeidsgiver.toi.logging

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.boolex.OnMarkerEvaluator
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import ch.qos.logback.core.filter.EvaluatorFilter
import ch.qos.logback.core.spi.AppenderAttachable
import ch.qos.logback.core.spi.AppenderAttachableImpl
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
            .hasMessageContaining("logback.xml mangler markerfilter")
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
    fun `tillat bruk av alle appendernavn som starter med 'team-logs'`() {
        val appendernavnMedPostfix = "$teamlogsAppenderName-OTEL"
        val rootLogger = nyRootLogger().apply {
            addAppender(
                nyAppender(loggerContext, appendernavnMedPostfix).apply {
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
            .hasMessageContaining("Alle ROOT-appendere unntatt de med navn som starter med 'team-logs' må avvise marker 'TEAM_LOGS'")
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

    @Test
    fun `tillat wrapper-appendere (som OpenTelemetryAppender) som videresender til en innpakket appender med markerfilter`() {
        val context = LoggerContext()
        val innpakketTeamlogsAppender = nyAppender(context, teamlogsAppenderName).apply {
            addFilter(nyTeamLogsAksepterendeFilter(context))
        }
        val innpakketOrdinærAppender = nyAppender(context, "appLog").apply {
            addFilter(nyTeamLogsAvvisendeFilter(context))
        }

        val rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME).apply {
            detachAndStopAllAppenders()
            addAppender(nyWrapperAppender(context, "team-logs-OTEL", innpakketTeamlogsAppender))
            addAppender(nyWrapperAppender(context, "appLog-OTEL", innpakketOrdinærAppender))
        }

        assertThatCode {
            TeamLogLogger.validateTeamlogConfiguration(rootLogger)
        }.doesNotThrowAnyException()
    }

    @Test
    fun `avvis wrapper-appender som videresender til en innpakket appender som mangler filter som avviser TEAM_LOGS`() {
        val context = LoggerContext()
        val innpakketTeamlogsAppender = nyAppender(context, teamlogsAppenderName).apply {
            addFilter(nyTeamLogsAksepterendeFilter(context))
        }
        val innpakketOrdinærAppenderUtenFilter = nyAppender(context, "appLog")

        val rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME).apply {
            detachAndStopAllAppenders()
            addAppender(nyWrapperAppender(context, "team-logs-OTEL", innpakketTeamlogsAppender))
            addAppender(nyWrapperAppender(context, "appLog-OTEL", innpakketOrdinærAppenderUtenFilter))
        }

        assertThatThrownBy {
            TeamLogLogger.validateTeamlogConfiguration(rootLogger)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Alle ROOT-appendere unntatt de med navn som starter med 'team-logs' må avvise marker 'TEAM_LOGS'")
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

    /**
     * Simulerer en wrapper-appender som f.eks. OpenTelemetryAppender: den er selv ikke
     * navngitt likt som den innpakkede appenderen nødvendigvis, videresender events til
     * en innpakket appender via AppenderAttachable, og har ingen filtre direkte på seg selv.
     */
    private fun nyWrapperAppender(
        context: LoggerContext,
        navn: String,
        innpakketAppender: AppenderBase<ILoggingEvent>,
    ): AppenderBase<ILoggingEvent> {
        val aai = AppenderAttachableImpl<ILoggingEvent>().apply { addAppender(innpakketAppender) }

        return object : AppenderBase<ILoggingEvent>(), AppenderAttachable<ILoggingEvent> {
            override fun append(eventObject: ILoggingEvent?) {
                eventObject?.let { aai.appendLoopOnAppenders(it) }
            }

            override fun addAppender(newAppender: ch.qos.logback.core.Appender<ILoggingEvent>) = aai.addAppender(newAppender)
            override fun iteratorForAppenders() = aai.iteratorForAppenders()
            override fun getAppender(name: String?) = aai.getAppender(name)
            override fun isAttached(appender: ch.qos.logback.core.Appender<ILoggingEvent>?) = aai.isAttached(appender)
            override fun detachAndStopAllAppenders() = aai.detachAndStopAllAppenders()
            override fun detachAppender(appender: ch.qos.logback.core.Appender<ILoggingEvent>?) = aai.detachAppender(appender)
            override fun detachAppender(name: String?) = aai.detachAppender(name)
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
