# drupal-extend

![Build](https://github.com/nvelychenko/drupal-extend/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/23474-drupal-extend.svg)](https://plugins.jetbrains.com/plugin/23474-drupal-extend)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/23474-drupal-extend.svg)](https://plugins.jetbrains.com/plugin/23474-drupal-extend)

Deep Drupal integration for PhpStorm: completion, navigation, type inference, inspections with
quick fixes, and a Drupal coding-standards linter — built for modern Drupal (11.x first, PHP
attributes, OOP hooks, autowired services), while still understanding legacy annotations and
procedural code.

## Entities & fields

* Content/Config entity storage completion in `getStorage()`, `entityQuery()`, and static
  `Node::load()`-style calls (eck supported)
  ![Storage autocomplete example](./assets/storage_autocomplete.gif)
* Field name completion from `field.storage.*` config — module-provided `config/install`
  definitions and sync exports are picked up automatically
  ![Fields autocomplete example](./assets/field_autocomplete.gif)
* Field property completion (`$node->get('field_user')->en|` → `entity`) and field completion
  inside entity queries and `Node::create([...])`
* Type inference across the whole chain: `getStorage('node')` → storage class,
  `->load()/loadMultiple()/loadByProperties()` → entity class (content **and** config entities),
  `\Drupal::entityQuery()`, field item lists, and `->entity` on reference fields
  ![Entity type provider](./assets/entity_type_provider.png)
* Navigation from storage ids and field names to their definitions
  ![Storage reference](./assets/storage_reference_provider.gif)

## Services & dependency injection

* Service id completion, navigation, hover docs, and type resolution for `\Drupal::service()`
  and container `get()` calls — including modern autowired definitions where the service id is
  the class FQN (`Drupal\my_module\MyService: ~`)
* `*.services.yml`: service id and class completion, references, Find Usages, and inspections
  for unknown services/classes and constructor argument mismatches
* "Inject Drupal service into constructor" quick fix: adds the property, constructor parameter
  (promoted constructors supported), and a `create()` override when the class needs one —
  `AutowireTrait` classes get an `#[Autowire(service: '...')]` parameter instead, so working
  autowiring is never replaced
* Service id completion and navigation inside `#[Autowire(service: '...')]`
* Service tags: name completion under `tags:`, completion + navigation for
  `!tagged_iterator` / `!tagged_locator` argument tags (resolves to the tagged services), and a
  warning for unknown tags
* `\Drupal::routeMatch()`, `currentUser()`, and `classResolver()` are covered by the DI
  inspection too

## Routes & links

* Route name completion and references in `Url::fromRoute()`, `Link::createFromRoute()`, form
  redirects, and across `*.routing.yml` / `*.links.*.yml`
* Navigation to controller/form/access handler classes and methods
* Inspections for unknown routes, unknown permissions, missing handler methods, and missing
  route parameters — with a quick fix that adds the missing parameters without touching your
  formatting

## Hooks (OOP & procedural)

* `#[Hook('...')]` name completion with method scaffolding, plus completion of whole hook
  implementations inside hook classes
  ![hooks](https://github.com/user-attachments/assets/1a42dec5-a09a-4039-9040-29bc81adb878)
* Line markers between `hook_*()` api.php docs and OOP implementations
* Inspections: unknown hook names in `#[Hook]` (dynamic patterns like `form_FORM_ID_alter` are
  understood) and hook methods that aren't public/non-static (with quick fixes)
* The whole 11.x attribute family — `#[Hook]`, `#[LegacyHook]`, `#[ReorderHook]`,
  `#[RemoveHook]` — and procedural implementations in `.module`/`.install`/`.theme` files are
  recognized as used, so they're never reported as dead code

## Config API & settings.php

* Config object name completion and goto-definition in `\Drupal::config()`, `$this->config()`,
  and `configFactory()->get()/getEditable()`
* Key path completion in `->get('page.front')` — works through variables too
* `settings.php`: `$config['name']` completion + navigation, `$config['name']['key']` key
  completion from the config's YAML, and `$settings['...']` completion of core Settings keys

## Form API

* Form callback strings are real references: `'#submit' => ['::submitHandler']`,
  `'#validate'`, `'#element_validate'`, `'#ajax' => ['callback' => '::ajaxCallback']`,
  `[$this, 'method']` — Ctrl-click, rename, and no false "unused method" reports
* `$form_state->getValue('…')` completes the keys your `buildForm()` actually declares

## Render arrays & theming

* `#theme` / `#type` completion with their properties, render element navigation and
  highlighting
  ![Render element types autocomplete](./assets/render_element_autocomplete.gif)
  ![Theme autocomplete example](./assets/theme_autocomplete.gif)
* Library completion in `#attached` and cache context completion in `#cache['contexts']` /
  `addCacheContexts()`

## Translations, events, permissions

* "Translation placeholder/argument mismatch" inspection for `t()`, `$this->t()`,
  `TranslatableMarkup`, and `formatPlural()` — missing/unused/incorrectly prefixed placeholders
  are flagged (`@count` is understood as auto-provided)
* Event subscribers: handler methods named in `getSubscribedEvents()` are navigable, renameable,
  and never reported unused
* Permission completion and navigation in `hasPermission()`-style calls and routing YAML

## Drupal coding standards (42 rules)

A configurable linter implementing the phpcs Drupal/DrupalPractice rules that make sense in an
IDE — naming (classes, functions, properties, constants), `else` placement, string quotes,
inline comment style, discouraged debug functions (`dpm`, `var_dump`, ...), spacing (operators,
commas, casts, scope keywords, control structures, blank lines), `use` statement sorting and
FQN-to-import conversion, `@file` docblocks for procedural files, and a full set of DocBlock
content rules (summaries, tag grouping, `@param`/`@return` descriptions, short scalar types,
80-character summaries, `@throws`/`@see`/`@deprecated` syntax).

Every rule is individually toggleable (<kbd>Settings</kbd> → <kbd>PHP</kbd> →
<kbd>Drupal Extend</kbd>), ships a quick fix where one is safe, and there's a file-wide
"Fix Drupal code style" action.

## Extension descriptors & YAML

* `*.info.yml` inspection: missing `name`/`type`/`core_version_requirement`, invalid `type`,
  malformed or unknown `dependencies`
* Module machine names everywhere: completion + navigation to `*.info.yml` in
  `moduleExists()`-style calls, test `$modules` arrays, and info.yml `dependencies`
* Bundled JSON Schemas for `*.services.yml`, `*.routing.yml`, and `*.permissions.yml` —
  completion and documentation hints for definition keys, without fighting the plugin's own
  navigation
* Version-aware annotation → PHP attribute conversion for ~45 core plugin types
  (Field/Block/Views/Migrate/Render/Entity and more); the Drupal core version is detected from
  Composer files or can be overridden in settings

## Installation

- Using the IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "drupal-extend"</kbd> >
  <kbd>Install</kbd>

- Manually:

  Download the [latest release](https://github.com/nvelychenko/drupal-extend/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

## Compatibility

PhpStorm 2025.1+. Drupal 11.x is the primary target; Drupal 9/10 projects work, with
version-gated behaviour (attribute availability, autowiring) detected from `composer.lock` /
`composer.json`.

## Troubleshooting

- The entity type resolver can occasionally conflict with a `.phpstorm.meta.php` generated via
  `drush generate-metadata`. The plugin provides everything that file does, so you can safely
  delete it.
- Index data looks stale? <kbd>Settings</kbd> → <kbd>PHP</kbd> → <kbd>Drupal Extend</kbd> →
  <kbd>Clear index</kbd>.
