package no.nav.arbeidsgiver.toi.logging

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.boolex.OnMarkerEvaluator
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.core.Appender
import ch.qos.logback.core.filter.EvaluatorFilter
import ch.qos.logback.core.spi.FilterReply
import ch.qos.logback.core.spi.FilterReply.ACCEPT
import ch.qos.logback.core.spi.FilterReply.DENY
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MarkerFactory

@Suppress("unused")
val Any.log: Logger
    get() = LoggerFactory.getLogger(this::class.java)


/**
 * Brukes i Kotlin-kode som ikke er inne i en klasse, typisk i en "top level function".
 *
 * Kalles fra den filen du ønsker å logge i slik:
 * ```
 * import no.nav.arbeidsgiver.toi.logging.noClassLogger
 * private val log = noClassLogger()
 * fun myToplevelFunction() {
 *     log.info("yada yada yada")
 *     ...
 * }
 * ```
 *
 * @return En Logger med samme navn som Kotlin-filen du logger fra, prefikset med pakkenavn.
 */
@Suppress("unused")
fun noClassLogger(): Logger {
    val callerClassName = Throwable().stackTrace[1].className
    return LoggerFactory.getLogger(callerClassName)
}

/**
 * Forutsetter at appen bruker en logback.xml-fil med filter slik:
 * ```
 * <?xml version="1.0" encoding="UTF-8"?>
 * <configuration>
 *     <appender name="STDOUT_JSON" class="ch.qos.logback.core.ConsoleAppender">
 *         <encoder class="net.logstash.logback.encoder.LogstashEncoder" />
 *         <filter class="ch.qos.logback.core.filter.EvaluatorFilter">
 *             <evaluator class="ch.qos.logback.classic.boolex.OnMarkerEvaluator">
 *                 <marker>TEAM_LOGS</marker>
 *             </evaluator>
 *             <OnMatch>DENY</OnMatch>
 *             <OnMismatch>ACCEPT</OnMismatch>
 *         </filter>
 *     </appender>
 *
 *     <appender name="team-logs" class="net.logstash.logback.appender.LogstashTcpSocketAppender">
 *         <destination>team-logs.nais-system:5170</destination>
 *         <encoder class="net.logstash.logback.encoder.LogstashEncoder">
 *             <customFields>{"google_cloud_project":"${GOOGLE_CLOUD_PROJECT}","nais_namespace_name":"${NAIS_NAMESPACE}","nais_pod_name":"${HOSTNAME}","nais_container_name":"${NAIS_APP_NAME}"}</customFields>
 *             <includeContext>false</includeContext>
 *         </encoder>
 *         <filter class="ch.qos.logback.core.filter.EvaluatorFilter">
 *             <evaluator class="ch.qos.logback.classic.boolex.OnMarkerEvaluator">
 *                 <marker>TEAM_LOGS</marker>
 *             </evaluator>
 *             <OnMatch>ACCEPT</OnMatch>
 *             <OnMismatch>DENY</OnMismatch>
 *         </filter>
 *     </appender>
 *
 *     <root level="INFO">
 *         <appender-ref ref="STDOUT_JSON"/>
 *         <appender-ref ref="team-logs" />
 *     </root>
 * </configuration>
 *
 * ```
 */
@Suppress("unused")
class TeamLogLogger private constructor(private val l: Logger) {
    fun info(msg: String) {
        l.info(m, msg)
    }

    fun info(msg: String, t: Throwable) {
        l.info(m, msg, t)
    }

    fun warn(msg: String) {
        l.warn(m, msg)
    }

    fun warn(msg: String, t: Throwable) {
        l.warn(m, msg, t)
    }

    fun error(msg: String) {
        l.error(m, msg)
    }

    fun error(msg: String, t: Throwable) {
        l.error(m, msg, t)
    }


    companion object {
        internal const val teamlogsMarkerName = "TEAM_LOGS"
        internal const val teamlogsAppenderName = "team-logs"
        private val m = MarkerFactory.getMarker(teamlogsMarkerName)

        fun teamlog(l: Logger): TeamLogLogger {
            val isRunningInNaisCluster = System.getenv("NAIS_CLUSTER_NAME") != null
            if (isRunningInNaisCluster) {
                validateTeamlogConfiguration(rootLogger())
            }
            return TeamLogLogger(l)
        }


        /**
         * Validerer at det finnes en logback.xml konfigurasjon med routing til Team Logs.
         */
        internal fun validateTeamlogConfiguration(rootLogger: ch.qos.logback.classic.Logger) {
            val teamlogsAppender = requireNotNull(teamlogsAppender(rootLogger)) {
                "Kan ikke logge til Team Logs. logback.xml mangler ROOT-appender med navn '$teamlogsAppenderName'."
            }
            require(hasTeamlogsMarkerFilter(teamlogsAppender)) {
                "Loggmeldinger med potensielt sensitive data beregnet til Team Logs kan havne i feil logg. logback.xml mangler markerfilter på ROOT-appender '$teamlogsAppenderName'. Forventet marker-navn: '$teamlogsMarkerName'."
            }
            require(allNonTeamlogsAppendersDenyTeamlogsMarker(rootLogger)) {
                "Loggmeldinger med potensielt sensitive data beregnet til Team Logs kan havne i feil logg. Alle ROOT-appendere unntatt '$teamlogsAppenderName' må avvise marker '$teamlogsMarkerName'."
            }
        }


        private fun teamlogsAppender(logger: ch.qos.logback.classic.Logger): Appender<ILoggingEvent?>? =
            appenders(logger).firstOrNull { it.name == teamlogsAppenderName }


        private fun hasTeamlogsMarkerFilter(appender: Appender<ILoggingEvent?>): Boolean {
            fun acceptsOnlyTeamLogs(filter: EvaluatorFilter<*>): Boolean =
                decisionForMarker(filter, teamlogsMarkerName) == ACCEPT &&
                        decisionForMarker(filter, "NOT_TEAM_LOGS") == DENY

            return filters(appender).any(::acceptsOnlyTeamLogs)
        }


        private fun allNonTeamlogsAppendersDenyTeamlogsMarker(rootLogger: ch.qos.logback.classic.Logger): Boolean {
            fun isNonTeamlogsAppender(appender: Appender<ILoggingEvent?>): Boolean =
                appender.name != teamlogsAppenderName

            fun firstApplicableDecisionForTeamlogsMarker(appender: Appender<ILoggingEvent?>): FilterReply? =
                filters(appender).firstNotNullOfOrNull { decisionForMarker(it, teamlogsMarkerName) }

            fun appenderDeniesTeamlogsMarker(appender: Appender<ILoggingEvent?>): Boolean =
                firstApplicableDecisionForTeamlogsMarker(appender) == DENY

            return appenders(rootLogger).filter(::isNonTeamlogsAppender).all(::appenderDeniesTeamlogsMarker)
        }


        private fun rootLogger(): ch.qos.logback.classic.Logger {
            val context = LoggerFactory.getILoggerFactory() as? LoggerContext
                ?: error("TeamLogLogger krever logback-classic som SLF4J-backend")
            return context.getLogger(Logger.ROOT_LOGGER_NAME)
        }


        private fun appenders(logger: ch.qos.logback.classic.Logger): List<Appender<ILoggingEvent?>> =
            logger.iteratorForAppenders()?.asSequence()?.toList() ?: emptyList()


        private fun filters(appender: Appender<ILoggingEvent?>): List<EvaluatorFilter<*>> =
            appender.copyOfAttachedFiltersList.filterIsInstance<EvaluatorFilter<*>>()


        private fun decisionForMarker(filter: EvaluatorFilter<*>, markerName: String): FilterReply? {
            val evaluator = filter.evaluator as? OnMarkerEvaluator ?: return null
            val event = LoggingEvent().apply { addMarker(MarkerFactory.getMarker(markerName)) }
            val isMatch = runCatching { evaluator.evaluate(event) }.getOrNull() ?: return null
            return if (isMatch) filter.onMatch else filter.onMismatch
        }
    }
}
