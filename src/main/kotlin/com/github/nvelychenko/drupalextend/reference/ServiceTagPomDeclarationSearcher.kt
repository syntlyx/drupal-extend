package com.github.nvelychenko.drupalextend.reference

import com.github.nvelychenko.drupalextend.util.Services
import com.github.nvelychenko.drupalextend.util.ServicesYaml
import com.intellij.openapi.util.TextRange
import com.intellij.pom.PomDeclarationSearcher
import com.intellij.pom.PomNamedTarget
import com.intellij.pom.PomTarget
import com.intellij.pom.PomTargetPsiElement
import com.intellij.pom.PsiDeclaredTarget
import com.intellij.psi.DelegatePsiTarget
import com.intellij.psi.PsiElement
import com.intellij.util.Consumer
import org.jetbrains.yaml.psi.YAMLScalar

/**
 * Declares every tag-name position in `*.services.yml` — declarations under `tags:` and
 * consumers (`!tagged_iterator` / `!tagged_locator` arguments, `service_collector` tags) alike —
 * as a POM declaration over exactly the tag-name range.
 *
 * This is what routes Ctrl+Click into the Show Usages branch. The goto-or-usages handler only
 * shows usages when the caret position yields declared-but-not-referenced data: a YAML value
 * scalar is not a named element (unlike service definition keys), and a self-resolving
 * [com.intellij.psi.PsiReference] doesn't help — the handler prefers referenced data and
 * "navigates" to the very same offset, a dead click. So the tag position must carry a
 * declaration and *no* reference. The POM target goes through the platform's own
 * declaration-provider bridge, which yields a PSI-backed symbol — the usages branch then falls
 * back to the classic find-usages flow ([ServiceTagsYamlFindUsagesHandlerFactory] +
 * [ServiceTagsReferencesSearch]) with no custom symbol searcher.
 */
class ServiceTagPomDeclarationSearcher : PomDeclarationSearcher() {
    override fun findDeclarationsAt(element: PsiElement, offsetInElement: Int, consumer: Consumer<in PomTarget>) {
        val scalar = element as? YAMLScalar ?: return
        if (!Services.isSupportEnabled(scalar.project)) return
        if (ServicesYaml.tagPositionScalar(scalar) != scalar) return
        val tagName = ServicesYaml.tagNameAtPosition(scalar) ?: return

        consumer.consume(ServiceTagPomTarget(scalar, tagName))
    }
}

/**
 * The declared target backing a tag-name scalar. [getNameIdentifierRange] covers only the tag
 * name — not quotes or a `!tagged_iterator` prefix inside the scalar element — it is both the
 * Ctrl+hover underline and the declaration range the platform matches the caret against.
 */
class ServiceTagPomTarget(
    private val scalar: YAMLScalar,
    private val tagName: String,
) : DelegatePsiTarget(scalar), PsiDeclaredTarget, PomNamedTarget {

    override fun getName(): String = tagName

    override fun getNameIdentifierRange(): TextRange = ServicesYaml.scalarTextValueRange(scalar, tagName)

    companion object {
        /** The tag-name scalar behind [element] when it is a wrapped [ServiceTagPomTarget], else [element]. */
        fun unwrap(element: PsiElement): PsiElement =
            ((element as? PomTargetPsiElement)?.target as? ServiceTagPomTarget)?.navigationElement ?: element
    }
}
