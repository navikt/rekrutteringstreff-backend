package no.nav.toi.jsonkarakterisering

import org.junit.jupiter.api.Assertions.fail
import java.lang.reflect.Array
import java.lang.reflect.Modifier
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.Period
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.jvm.javaObjectType
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.jvm.isAccessible

enum class Variant {
    FYLT,
    NULL,
}

open class IkkeGenererbar(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

private class DypEllerRekursiv(message: String) : IkkeGenererbar(message)

/**
 * Golden-tester som låser JSON-formatet til data- og value-klassene i en app.
 *
 * Regenerer golden-filene med
 * `JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Djsonkarakterisering.oppdater=true" ./gradlew <app>:test --tests '*JsonKarakterisering*' --rerun-tasks`.
 * Gjør det bare når en formatendring er bevisst.
 */
object JsonKarakterisering {
    fun finnKlasser(rotklasse: Class<*>, pakkePrefiks: String): List<KClass<*>> {
        val rot = runCatching {
            Path.of(rotklasse.protectionDomain.codeSource.location.toURI())
        }.getOrNull() ?: return emptyList()
        if (!Files.isDirectory(rot)) return emptyList()

        val funnet = mutableListOf<KClass<*>>()
        runCatching {
            Files.walk(rot).use { stier ->
                stier.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".class") }
                    .forEach { sti ->
                        val binærnavn = rot.relativize(sti)
                            .toString()
                            .removeSuffix(".class")
                            .replace('/', '.')
                            .replace('\\', '.')
                        if (skalHoppesOver(binærnavn)) return@forEach

                        val pakke = binærnavn.substringBeforeLast('.', "")
                        if (!pakke.startsWith(pakkePrefiks)) return@forEach

                        val klasse = runCatching {
                            Class.forName(binærnavn, false, rotklasse.classLoader).kotlin
                        }.getOrNull() ?: return@forEach
                        runCatching {
                            if (klasse.isData || klasse.isValue) funnet += klasse
                        }
                    }
            }
        }

        return funnet
            .distinctBy { it.java.name }
            .sortedWith(compareBy<KClass<*>> { it.qualifiedName ?: it.java.name }.thenBy { it.java.name })
    }

    /**
     * Generates an instance using the class's primary constructor. The optional callback
     * keeps this module independent of any JSON implementation.
     */
    fun lagInstans(
        klasse: KClass<*>,
        variant: Variant,
        lagJsonNode: (String) -> Any = {
            throw IkkeGenererbar("Ingen JSON-node-generator er gitt for ${klasse.qualifiedName}")
        },
    ): Any? {
        var sisteFeil: Throwable? = null
        for (kandidatindeks in STRING_KANDIDATER.indices) {
            for (tallkandidatindeks in NUMERISKE_KANDIDATER.indices) {
                try {
                    return generer(
                        klasse.starProjectedType,
                        "p",
                        variant,
                        lagJsonNode,
                        kandidatindeks,
                        tallkandidatindeks,
                        0,
                        emptySet(),
                    )
                } catch (feil: Throwable) {
                    sisteFeil = feil
                }
            }
        }
        throw IkkeGenererbar(
            "Kunne ikke generere ${klasse.qualifiedName} med noen av kandidatverdiene",
            sisteFeil,
        )
    }

    fun verifiser(
        golden: Path,
        klasser: List<KClass<*>>,
        skriv: (Any) -> String,
        les: (String, Class<*>) -> Any?,
        lagJsonNode: (String) -> Any,
    ) {
        val faktiskeLinjer = klasser
            .distinctBy { it.java.name }
            .sortedWith(compareBy<KClass<*>> { it.qualifiedName ?: it.java.name }.thenBy { it.java.name })
            .flatMap { klasse ->
                val variantene = buildList {
                    add(Variant.FYLT)
                    if (klasse.primaryConstructor?.parameters?.any { it.type.isMarkedNullable } == true) {
                        add(Variant.NULL)
                    }
                }
                variantene.map { variant ->
                    lagLinje(klasse, variant, skriv, les, lagJsonNode)
                }
            }

        val faktiskTekst = if (faktiskeLinjer.isEmpty()) "" else faktiskeLinjer.joinToString("\n", postfix = "\n")
        val oppdater = System.getProperty("jsonkarakterisering.oppdater") == "true"
        val filEksisterer = Files.exists(golden)
        if (oppdater || !filEksisterer) {
            golden.parent?.let { Files.createDirectories(it) }
            Files.writeString(golden, faktiskTekst, StandardCharsets.UTF_8)
            if (!oppdater) {
                fail<Nothing>("Golden-fil skrevet, kjør på nytt og commit $golden")
            }
            return
        }

        val forventedeLinjer = Files.readAllLines(golden, StandardCharsets.UTF_8)
        val forventetEtterNøkkel = forventedeLinjer.associateBy(::nøkkel)
        val faktiskEtterNøkkel = faktiskeLinjer.associateBy(::nøkkel)
        val alleNøkler = (forventetEtterNøkkel.keys + faktiskEtterNøkkel.keys).toSortedSet(
            compareBy<Pair<String, String>> { it.first }.thenBy { it.second },
        )

        val avvik = alleNøkler.mapNotNull { nøkkel ->
            val forventet = forventetEtterNøkkel[nøkkel]
            val faktisk = faktiskEtterNøkkel[nøkkel]
            when {
                forventet == null -> "Forventet: <mangler>\nFaktisk:   $faktisk"
                faktisk == null -> "Forventet: $forventet\nFaktisk:   <mangler>"
                erTillattAvvik(forventet) -> null
                forventet == faktisk -> null
                else -> "Forventet: $forventet\nFaktisk:   $faktisk"
            }
        }

        if (avvik.isNotEmpty()) {
            fail<Nothing>("JSON-karakterisering avviker fra golden-filen $golden:\n${avvik.joinToString("\n\n")}")
        }
    }

    private fun lagLinje(
        klasse: KClass<*>,
        variant: Variant,
        skriv: (Any) -> String,
        les: (String, Class<*>) -> Any?,
        lagJsonNode: (String) -> Any,
    ): String {
        val binærnavn = klasse.java.name
        val instans = try {
            lagInstans(klasse, variant, lagJsonNode)
                ?: throw IkkeGenererbar("Instansgeneratoren returnerte null for ${klasse.qualifiedName}")
        } catch (_: Throwable) {
            return listOf(binærnavn, variant.name, "IKKE_GENERERT", "IKKE_GENERERT")
                .joinToString("\t", transform = ::escape)
        }
        val json = try {
            skriv(instans)
        } catch (_: Throwable) {
            return listOf(binærnavn, variant.name, "SKRIVEFEIL", "SKRIVEFEIL")
                .joinToString("\t", transform = ::escape)
        }

        val deserialisert = try {
            les(json, klasse.java)
                ?: throw IkkeGenererbar("Deserialisering returnerte null for ${klasse.qualifiedName}")
        } catch (_: Throwable) {
            return listOf(binærnavn, variant.name, json, "LESEFEIL")
                .joinToString("\t", transform = ::escape)
        }

        val rundtur = try {
            val serialisertIgjen = skriv(deserialisert)
            if (serialisertIgjen == json) "OK" else serialisertIgjen
        } catch (_: Throwable) {
            "SKRIVEFEIL"
        }

        return listOf(binærnavn, variant.name, json, rundtur)
            .joinToString("\t", transform = ::escape)
    }

    private fun erTillattAvvik(linje: String): Boolean {
        val felt = linje.split('\t')
        return felt.any { it in setOf("IKKE_GENERERT", "SKRIVEFEIL") } ||
            felt.getOrNull(3) == "LESEFEIL"
    }

    private fun nøkkel(linje: String): Pair<String, String> {
        val felt = linje.split('\t')
        return (felt.getOrNull(0) ?: "") to (felt.getOrNull(1) ?: "")
    }

    private fun escape(tekst: String): String =
        tekst.replace("\\", "\\\\")
            .replace("\t", "\\t")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

    private fun generer(
        type: KType,
        parameternavn: String,
        variant: Variant,
        lagJsonNode: (String) -> Any,
        kandidatindeks: Int,
        tallkandidatindeks: Int,
        dybde: Int,
        sti: Set<KClass<*>>,
    ): Any? {
        val klasse = type.classifier as? KClass<*>
            ?: throw IkkeGenererbar("Ukjent type for parameter $parameternavn: $type")

        if (variant == Variant.NULL && type.isMarkedNullable) return null
        if (klasse == Any::class) return parameternavn

        when (klasse) {
            String::class -> return stringKandidat(parameternavn, kandidatindeks)
            Char::class -> return 'x'
            Int::class -> return when (tallkandidatindeks) {
                0 -> 42
                1 -> 1
                else -> 0
            }
            Long::class -> return when (tallkandidatindeks) {
                0 -> 4242L
                1 -> 1L
                else -> 0L
            }
            Short::class -> return 7.toShort()
            Byte::class -> return 1.toByte()
            Double::class -> return 12.5
            Float::class -> return 1.25f
            Boolean::class -> return true
            BigDecimal::class -> return BigDecimal("1234.50")
            BigInteger::class -> return BigInteger.valueOf(99)
            UUID::class -> return UUID.nameUUIDFromBytes(parameternavn.toByteArray())
            ZonedDateTime::class -> return ZonedDateTime.of(2026, 1, 2, 3, 4, 5, 6_000_000, ZoneId.of("Europe/Oslo"))
            OffsetDateTime::class -> return ZonedDateTime
                .of(2026, 1, 2, 3, 4, 5, 6_000_000, ZoneId.of("Europe/Oslo"))
                .toOffsetDateTime()
            LocalDateTime::class -> return LocalDateTime.of(2026, 1, 2, 3, 4, 5, 6_000_000)
            LocalDate::class -> return LocalDate.of(2026, 1, 2)
            LocalTime::class -> return LocalTime.of(3, 4, 5)
            Instant::class -> return Instant.parse("2026-01-02T03:04:05.006Z")
            Duration::class -> return Duration.ofMinutes(90)
            Period::class -> return Period.ofDays(3)
            YearMonth::class -> return YearMonth.of(2026, 1)
        }

        if (klasse.java.isEnum) {
            return klasse.java.enumConstants?.firstOrNull()
                ?: throw IkkeGenererbar("Enum uten konstanter: ${klasse.qualifiedName}")
        }

        when (klasse.simpleName) {
            "JsonNode", "ObjectNode" -> return lagJsonNode("""{"a":1,"b":"tekst"}""")
            "ArrayNode" -> return lagJsonNode("[1]")
        }

        if (dybde >= MAX_DYBDE || klasse in sti) {
            if (type.isMarkedNullable) return null
            if (Map::class.java.isAssignableFrom(klasse.java)) return emptyMap<Any?, Any?>()
            if (Set::class.java.isAssignableFrom(klasse.java)) return emptySet<Any?>()
            if (Iterable::class.java.isAssignableFrom(klasse.java)) return emptyList<Any?>()
            if (klasse.java.isArray) return Array.newInstance(arrayElementClass(klasse, type), 0)
            throw DypEllerRekursiv("For dyp eller rekursiv type ${klasse.qualifiedName}")
        }

        val typeargumenter = type.arguments.map { it.type }
        if (Map::class.java.isAssignableFrom(klasse.java)) {
            val nøkkeltype = typeargumenter.getOrNull(0) ?: Any::class.starProjectedType
            val verditype = typeargumenter.getOrNull(1) ?: Any::class.starProjectedType
            return try {
                val nøkkel = generer(nøkkeltype, parameternavn, variant, lagJsonNode, kandidatindeks, tallkandidatindeks, dybde + 1, sti)
                val verdi = generer(verditype, parameternavn, variant, lagJsonNode, kandidatindeks, tallkandidatindeks, dybde + 1, sti)
                mapOf(nøkkel to verdi)
            } catch (_: DypEllerRekursiv) {
                emptyMap<Any?, Any?>()
            }
        }

        if (Set::class.java.isAssignableFrom(klasse.java)) {
            val elementtype = typeargumenter.firstOrNull() ?: Any::class.starProjectedType
            return try {
                setOf(generer(elementtype, parameternavn, variant, lagJsonNode, kandidatindeks, tallkandidatindeks, dybde + 1, sti))
            } catch (_: DypEllerRekursiv) {
                emptySet<Any?>()
            }
        }

        if (Iterable::class.java.isAssignableFrom(klasse.java)) {
            val elementtype = typeargumenter.firstOrNull() ?: Any::class.starProjectedType
            return try {
                listOf(generer(elementtype, parameternavn, variant, lagJsonNode, kandidatindeks, tallkandidatindeks, dybde + 1, sti))
            } catch (_: DypEllerRekursiv) {
                emptyList<Any?>()
            }
        }

        if (klasse.java.isArray) {
            val elementtype = typeargumenter.firstOrNull()
            val elementklasse = arrayElementClass(klasse, type)
            val array = Array.newInstance(elementklasse, 1)
            try {
                val verdi = if (elementtype != null) {
                    generer(elementtype, parameternavn, variant, lagJsonNode, kandidatindeks, tallkandidatindeks, dybde + 1, sti)
                } else {
                    parameternavn
                }
                Array.set(array, 0, verdi)
            } catch (_: DypEllerRekursiv) {
                return Array.newInstance(elementklasse, 0)
            }
            return array
        }

        if (klasse.objectInstance != null) return klasse.objectInstance

        if (klasse.isSealed || klasse.java.isInterface || Modifier.isAbstract(klasse.java.modifiers)) {
            val muligeUnderklasser = klasse.sealedSubclasses
                .sortedBy { it.qualifiedName ?: it.java.name }
            var dypEllerRekursivFeil: DypEllerRekursiv? = null
            var annenFeil = false
            for (underklasse in muligeUnderklasser) {
                try {
                    return generer(
                        underklasse.starProjectedType,
                        parameternavn,
                        variant,
                        lagJsonNode,
                        kandidatindeks,
                        tallkandidatindeks,
                        dybde + 1,
                        sti + klasse,
                    )
                } catch (feil: DypEllerRekursiv) {
                    dypEllerRekursivFeil = feil
                } catch (_: Throwable) {
                    annenFeil = true
                }
            }
            if (dypEllerRekursivFeil != null && !annenFeil) throw dypEllerRekursivFeil
            throw IkkeGenererbar("Ingen genererbar implementasjon av ${klasse.qualifiedName}")
        }

        val konstruktør = klasse.primaryConstructor
            ?: throw IkkeGenererbar("Ingen primærkonstruktør for ${klasse.qualifiedName}")
        return try {
            konstruktør.isAccessible = true
            val argumenter = konstruktør.parameters.associateWith { parameter ->
                generer(
                    parameter.type,
                    parameter.name ?: "p",
                    variant,
                    lagJsonNode,
                    kandidatindeks,
                    tallkandidatindeks,
                    dybde + 1,
                    sti + klasse,
                )
            }
            konstruktør.callBy(argumenter)
        } catch (feil: IkkeGenererbar) {
            throw feil
        } catch (feil: Throwable) {
            throw IkkeGenererbar("Kunne ikke generere ${klasse.qualifiedName}", feil)
        }
    }

    private const val MAX_DYBDE = 4
    private val NUMERISKE_KANDIDATER = listOf(42, 1, 0)
    private val STRING_KANDIDATER = listOf(
        "parameternavn",
        "12345678901",
        "123456789",
        "1234",
        "A123456",
        "0301",
        "tekst",
    )

    private fun stringKandidat(parameternavn: String, kandidatindeks: Int): String =
        if (kandidatindeks == 0) parameternavn else STRING_KANDIDATER[kandidatindeks]

    private fun arrayElementClass(klasse: KClass<*>, type: KType): Class<*> =
        ((type.arguments.firstOrNull()?.type?.classifier as? KClass<*>)?.javaObjectType)
            ?: klasse.java.componentType

    private fun skalHoppesOver(binærnavn: String): Boolean =
        Regex("\\$\\d").containsMatchIn(binærnavn) ||
            binærnavn.contains("\$lambda") ||
            binærnavn.contains("\$WhenMappings") ||
            binærnavn.contains("\$Companion") ||
            binærnavn.contains("\$DefaultImpls")
}
