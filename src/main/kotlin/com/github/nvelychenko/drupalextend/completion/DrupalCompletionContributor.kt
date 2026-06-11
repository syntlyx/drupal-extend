package com.github.nvelychenko.drupalextend.completion

import com.github.nvelychenko.drupalextend.completion.providers.*
import com.github.nvelychenko.drupalextend.patterns.Patterns.HOOK_ATTRIBUTE_PARAMETER_STRING_LITERAL
import com.github.nvelychenko.drupalextend.patterns.Patterns.LEAF_STRING_IN_SIMPLE_ARRAY_VALUE
import com.github.nvelychenko.drupalextend.patterns.Patterns.STRING_INSIDE_ARRAY_METHOD
import com.github.nvelychenko.drupalextend.patterns.Patterns.STRING_LEAF_IN_ARRAY_KEY_OR_ONLY_VALUE
import com.github.nvelychenko.drupalextend.patterns.Patterns.STRING_LITERAL_INSIDE_METHOD_PARAMETER
import com.github.nvelychenko.drupalextend.patterns.Patterns.TRIPLE_ARRAY_WITH_STRING_VALUE
import com.github.nvelychenko.drupalextend.patterns.Patterns.TRIPLE_NESTED_STRING_ARRAY_VALUE
import com.github.nvelychenko.drupalextend.patterns.Patterns.TRIPLE_SIMPLE_STRING_ARRAY_VALUE
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.patterns.PlatformPatterns.or
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.PhpLanguage
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression


class DrupalCompletionContributor : CompletionContributor() {

    init {
        // $account->hasPermission('|
        extend(CompletionType.BASIC, STRING_LITERAL_INSIDE_METHOD_PARAMETER, PermissionsCompletionProvider())

        // \Drupal::entityTypeManager->getStorage('no|
        extend(CompletionType.BASIC, STRING_LITERAL_INSIDE_METHOD_PARAMETER, EntityStorageCompletionProvider())

        // \Drupal::service('da|
        // $container->get('da|
        extend(CompletionType.BASIC, STRING_LITERAL_INSIDE_METHOD_PARAMETER, ServicesCompletionProvider())

        // #[Autowire(service: 'da|
        // (the pattern matches any attribute's string parameter; the provider narrows to Autowire)
        extend(CompletionType.BASIC, HOOK_ATTRIBUTE_PARAMETER_STRING_LITERAL, AutowireServiceCompletionProvider())

        // \Drupal::config('system.si|   $this->config('…')   configFactory()->getEditable('…')
        extend(CompletionType.BASIC, STRING_LITERAL_INSIDE_METHOD_PARAMETER, ConfigNameCompletionProvider())

        // \Drupal::config('system.site')->get('page.fr|
        extend(CompletionType.BASIC, STRING_LITERAL_INSIDE_METHOD_PARAMETER, ConfigKeyCompletionProvider())

        // settings.php: $config['system.si|']['…']  /  $settings['hash_s|']
        // (broad string pattern; the provider bails immediately outside settings*.php files)
        extend(CompletionType.BASIC, psiElement().withParent(StringLiteralExpression::class.java), SettingsPhpCompletionProvider())

        // Url::fromRoute('entity.node.canonical')
        // Link::createFromRoute('entity.node.canonical')
        // $form_state->setRedirect('entity.node.canonical')
        extend(CompletionType.BASIC, STRING_LITERAL_INSIDE_METHOD_PARAMETER, RoutesCompletionProvider())

        // \Drupal::entityTypeManager->getStorage('no|
        extend(CompletionType.BASIC, STRING_LITERAL_INSIDE_METHOD_PARAMETER, StaticEntityQueryCompletionProvider())

        // \Drupal::entityTypeManager->getQuery()->condition('
        extend(CompletionType.BASIC, STRING_LITERAL_INSIDE_METHOD_PARAMETER, EntityQueryConditionFieldCompletionProvider())

        // $node->set('fi
        // $node->get('fi|
        extend(CompletionType.BASIC, STRING_LITERAL_INSIDE_METHOD_PARAMETER, FieldCompletionCompletionProvider())

        // Noe::create(['fi]);
        extend(
            CompletionType.BASIC,
            psiElement(PsiElement::class.java).withParent(STRING_INSIDE_ARRAY_METHOD),
            FieldInCreateMethodCompletionCompletionProvider()
        )

        // $render = ['#them|
        extend(CompletionType.BASIC, STRING_LEAF_IN_ARRAY_KEY_OR_ONLY_VALUE, BasicThemeOrTypeCompletionProvider())

        // $render = [
        //    '#type' => '|
        // ];
        extend(CompletionType.BASIC, LEAF_STRING_IN_SIMPLE_ARRAY_VALUE, RenderElementTypeCompletionProvider())

        // $render = [
        //    '#theme' => '|
        // ];
        extend(CompletionType.BASIC, LEAF_STRING_IN_SIMPLE_ARRAY_VALUE, ThemeCompletionProvider())

        // $render = [
        //    '#type' => 'checkbox',
        //    '#|
        // ];
        extend(CompletionType.BASIC, STRING_LEAF_IN_ARRAY_KEY_OR_ONLY_VALUE, RenderElementTypePropertiesCompletionProvider())

        // $render = [
        //    '#theme' => 'item_list',
        //    '#|
        // ];
        extend(CompletionType.BASIC, STRING_LEAF_IN_ARRAY_KEY_OR_ONLY_VALUE, ThemePropertiesCompletionProvider())

        // $build['#attached']['library'][] = 'he|
        extend(
            CompletionType.BASIC,
            or(
                psiElement(PsiElement::class.java).withParent(TRIPLE_ARRAY_WITH_STRING_VALUE),
                psiElement(PsiElement::class.java).withParent(TRIPLE_SIMPLE_STRING_ARRAY_VALUE),
                psiElement(PsiElement::class.java).withParent(TRIPLE_NESTED_STRING_ARRAY_VALUE),
            ),
            LibrariesCompletionProvider()
        )

        // $renderer->addCacheContexts(['us|'])
        // $build['#cache']['contexts'][] = 'us|'
        // $build = ['#cache' => ['contexts' => ['us|']]]
        extend(
            CompletionType.BASIC,
            or(
                psiElement(PsiElement::class.java).withParent(STRING_INSIDE_ARRAY_METHOD),
                psiElement(PsiElement::class.java).withParent(TRIPLE_ARRAY_WITH_STRING_VALUE),
                psiElement(PsiElement::class.java).withParent(TRIPLE_SIMPLE_STRING_ARRAY_VALUE),
                psiElement(PsiElement::class.java).withParent(TRIPLE_NESTED_STRING_ARRAY_VALUE),
            ),
            CacheContextsCompletionProvider()
        )

        // $node->get('field_entity')->entity
        // $node->get('field_entity')[0]->entity
        // $node->get('field_entity')->first()->entity
        // $field->entity
        extend(CompletionType.BASIC, psiElement().withLanguage(PhpLanguage.INSTANCE), FieldPropertyCompletionProvider())
    }

}
