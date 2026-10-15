import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Feiler hvis Jackson 2 (`com.fasterxml.jackson*`) er på klassestien, eller hvis egen kode bruker Jackson 2.
 *
 * Unntak:
 * - `jackson-annotations` og `jackson-bom`, som Jackson 3 selv bruker.
 * - Jackson 2-moduler som bare dras inn av `openapi-generator` (javalin-openapi-plugin) eller av andre Jackson 2-moduler.
 * - kapt-konfigurasjonene sjekkes ikke.
 * - `com.fasterxml.jackson.annotation` kan brukes i egen kode.
 */
abstract class SjekkJackson2Task : DefaultTask() {

    @get:Input
    abstract val rotkomponenter: MapProperty<String, ResolvedComponentResult>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val kildefiler: ConfigurableFileCollection

    @TaskAction
    fun sjekk() {
        val feil = rotkomponenter.get().flatMap { (konfigurasjon, rot) ->
            ulovligeJackson2Moduler(rot).map { "$konfigurasjon: $it" }
        } + ulovligBrukIKildekode()

        if (feil.isNotEmpty()) {
            throw GradleException(
                "Jackson 2 er ikke tillatt. Bruk Jackson 3 (tools.jackson).\n" + feil.joinToString("\n") { "  - $it" }
            )
        }
    }

    private fun ulovligeJackson2Moduler(rot: ResolvedComponentResult): List<String> {
        val besøkt = mutableSetOf<ResolvedComponentResult>()
        val kø = ArrayDeque(listOf(rot))
        while (kø.isNotEmpty()) {
            val komponent = kø.removeFirst()
            if (besøkt.add(komponent)) {
                komponent.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { kø.add(it.selected) }
            }
        }
        return besøkt
            .filter { erBegrensetJackson2(it) }
            .mapNotNull { komponent ->
                val ulovligeAvhengige = komponent.dependents
                    .filterNot { it.isConstraint }
                    .map { it.from }
                    .filterNot { erOpenapiGenerator(it) || erJackson2(it) }
                if (ulovligeAvhengige.isEmpty()) null
                else "${komponent.id.displayName} dras inn av ${ulovligeAvhengige.joinToString { it.id.displayName }}"
            }
    }

    private fun ulovligBrukIKildekode(): List<String> {
        val jackson2 = Regex("""com\.fasterxml\.jackson\.(?!annotation\.)""")
        return kildefiler.files
            .filter { jackson2.containsMatchIn(it.readText()) }
            .map { "${it.path} bruker Jackson 2" }
    }

    private fun modul(komponent: ResolvedComponentResult) = komponent.id as? ModuleComponentIdentifier

    private fun erJackson2(komponent: ResolvedComponentResult) =
        modul(komponent)?.group?.startsWith("com.fasterxml.jackson") == true

    private fun erBegrensetJackson2(komponent: ResolvedComponentResult) =
        erJackson2(komponent) && modul(komponent)?.module !in setOf("jackson-annotations", "jackson-bom")

    private fun erOpenapiGenerator(komponent: ResolvedComponentResult) =
        modul(komponent)?.let { it.group == "io.javalin.community.openapi" && it.module == "openapi-generator" } == true
}
