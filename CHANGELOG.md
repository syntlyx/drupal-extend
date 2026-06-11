<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# drupal-extend Changelog

## [Unreleased]

### Added

- New code-style rule "function docblock tags" (Drupal.Commenting.FunctionComment): a function/method that has a docblock must document every parameter (promoted constructor properties included) with `@param` and a declared non-void return with `@return`. The "Add missing doc tags" quick fix — also applied by the file-wide fix — generates Drupal-format tags with `\FQN` types and descriptions auto-humanized from the parameter names (`$entityTypeManager` → "The entity type manager."). `{@inheritdoc}` docblocks are skipped; name mismatches stay the param-name rule's job.
- The "Fix Drupal code style in file" action now also generates the missing `@file` docblock, and the generated description follows core conventions derived from the file name (`nll_core.install` → "Install, update and uninstall functions for the Nll Core module.", `.module` → "Hook implementations for …", `.theme`/`.profile` accordingly) instead of a TODO placeholder.
- The docblock summary length rule gained a "Wrap summary at 80 characters" quick fix (word-boundary wrapping onto continuation lines; also applied by the file-wide fix). Single words longer than the limit still get no fix.
- Form API support: form callback strings — `'#submit'`, `'#validate'`, `'#element_validate'`, `'#after_build'`, `'#pre_render'`, `'#process'`, and `'#ajax' => ['callback' => …]` — are now references: `'::method'` resolves to the class method (Ctrl-click + rename), `[Foo::class, 'method']` / `[$this, 'method']` and plain function names resolve too, and callback methods are no longer reported as unused. `$form_state->getValue()/hasValue()/setValue()` completes the keys declared via `$form['…'] = …` in the same class.
- Service tag support in `*.services.yml`: tag name completion under `tags:` (mapping and scalar forms, project tags plus a curated core-tag list), completion and navigation for `!tagged_iterator` / `!tagged_locator` argument tags (the reference resolves to the tagged services), and a weak warning for unknown service tags.
- Module machine-name support: completion and goto-definition for extension machine names. In PHP, inside `moduleHandler()->moduleExists()` / `moduleInstaller()->install()`/`uninstall()` calls, `themeExists()` calls, and test `protected static $modules = [...]` arrays (themes filtered out of the `$modules` suggestions, since themes are enabled differently); the literals are also references that navigate to the extension's `*.info.yml`. In `*.info.yml`, `dependencies:` entries become references that navigate to the dependency's `*.info.yml` (the `project:module` form targets the module part after the colon). The info.yml inspection additionally flags a dependency whose machine name is unknown to the module index as a weak warning (kept weak — the module may live in a not-yet-downloaded Composer project).
- Config API support: config object names are indexed from `config/install`, `config/optional`, and sync-export YAML files (recognized by their dotted machine names). On top of that: name completion and goto-definition in `\Drupal::config()`, `$this->config()`, and `configFactory()->get()/getEditable()` calls; key-path completion in `->get('page.front')` — both in direct chains and through variables (the config name rides the type signature); and `settings.php` support — `$config['name']` name completion + navigation, `$config['name']['key']` key completion from the config's YAML, and `$settings['…']` completion from a curated core Settings key list.
- Event subscriber support: methods registered by name inside an event subscriber's `getSubscribedEvents()` are now recognised as implicitly used and no longer reported as unused — covering the `Event::NAME => 'method'`, `=> ['method', 10]`, and `=> [['method', 10], ['other']]` mapping shapes. `getSubscribedEvents()` itself is recognised on classes implementing `EventSubscriberInterface`. The handler-name strings are also references: Ctrl-click navigates to the method and rename keeps them in sync.
- New inspection "Translation placeholder/argument mismatch": validates Drupal translation calls (`t()`, `$this->t()`, `new TranslatableMarkup()`, `\Drupal::translation()->translate()`, and `formatPlural()`) when both the message is a string literal and the arguments are an inline array. Flags placeholders (`@name`/`%name`/`:name`) with no replacement argument (warning), array keys not used in the string (weak warning), and array keys missing a `@`/`%`/`:` prefix (weak warning). `@count` in `formatPlural()` is recognised as auto-provided and never reported; variable strings, variable/open-ended argument arrays, and interpolated literals are conservatively skipped.
- Cache context completion: inside `->addCacheContexts([...])` / `->setCacheContexts([...])` / `->mergeCacheContexts([...])` and inside `'#cache' => ['contexts' => [...]]` render arrays (including the `$build['#cache']['contexts'][] = '...'` form), the plugin now suggests cache context ids derived from `cache_context.*` services (e.g. `user`, `url.path`).
- New inspection "Unknown hook name in `#[Hook]` attribute": flags hook names that match no known Drupal hook (from the Drupal plugin's hook index and `*.api.php` docs). Dynamic hook patterns (`hook_form_FORM_ID_alter`-style placeholders) are recognized, so concrete implementations like `form_node_edit_form_alter` are not reported.
- Config entity type inference: `\Drupal::entityTypeManager()->getStorage('view')` now resolves to the config entity storage, and `->load()` / `->loadMultiple()` on it resolve to the config entity class — matching what already worked for content entities.
- The full Drupal 11.x hook-attribute family — `#[Hook]`, `#[LegacyHook]`, `#[ReorderHook]`, `#[RemoveHook]` — now marks its methods/functions (and the classes hosting them) as implicitly used, so OOP hook implementations and BC shims are no longer reported as unused.
- New inspection "Drupal hook method must be public and non-static": a `#[Hook]` method the hook system can't invoke is reported at ERROR level, with quick fixes to make it public or drop `static`.
- New inspection for `*.info.yml`: missing `name`/`type`, invalid `type` value, missing `core_version_requirement` (legacy `core` key respected), and malformed `dependencies` entries.
- New code-style rule (Drupal.Commenting.FileComment): `.module`/`.install`/`.theme`/`.inc`/`.profile` files must start with a `@file` docblock; quick fix inserts the block or adds the missing `@file` tag. Toggleable like every other rule.
- Service id completion and navigation inside `#[Autowire(service: '...')]` (and the positional `#[Autowire('...')]` form) on constructor parameters — the same completion/reference the plugin already provides for `\Drupal::service()`.
- The dependency injection inspection now also covers `\Drupal::classResolver()` (suggests injecting the `class_resolver` service, with the same quick fix as `routeMatch()`/`currentUser()`).
- Five more code-style rules (each individually toggleable): **property naming** — class properties must be lowerCamelCase (snake_case, leading underscore, or uppercase-first reported; no auto-fix — renaming is the IDE refactoring's job); **scope keyword spacing** — exactly one space after `public`/`protected`/`private`/`static`/`abstract`/`final`/`readonly`, with a collapsing fix; **consecutive blank lines** — at most one blank line anywhere in the file, with a collapsing fix (string/heredoc content untouched by construction); **docblock short types** — `integer`/`boolean`/`double`/`real` in `@param`/`@return`/`@var` rewritten to `int`/`bool`/`float`; **docblock summary length** — summary lines over 80 characters reported (URLs exempt; report-only).
- Four new code-style rules (each individually toggleable): **else placement** — `else`/`elseif` must start on a new line after the closing brace (the Drupal-specific deviation from PSR-12), with a fix that moves the keyword and matches the `if` indentation; **string quotes** — double-quoted strings without interpolation or double-quote-only escapes should use single quotes, with a converting fix (strings containing single quotes are left alone — per the Drupal standard, double quotes are correct there, e.g. SQL with `''`); **inline comments** — `#` comments rewritten to `//`, `// ` spacing enforced, first line of a comment run capitalized and last line punctuated (annotations, directives, URLs, code references, and commented-out code are conservatively skipped); **discouraged debug functions** — `dpm`, `dsm`, `var_dump`, `dump`, `dd`, `kint` & co. (and `print_r` without the `TRUE` return-mode argument) flagged even when unresolved, with a "Remove debug call" fix for standalone statements.
- Bundled JSON Schemas for `*.routing.yml` and `*.permissions.yml` (completion and documentation hints for route/permission definition keys), mirroring the existing services.yml schema; deliberately permissive so contrib extensions aren't flagged, and the plugin's own references keep winning over schema navigation.
- Field plugin support: Field/Widget/Formatter plugin classes (attribute and legacy annotation forms) are indexed, and default field plugin classes referenced through field configuration are recognised as implicitly used, so they are no longer reported as unused.
- Procedural hook support: hook implementations in `.module`, `.install`, and `.theme` files (and their parameters) are recognised as implicitly used and no longer reported as unused/dead.
- DocBlock code-style rule "DocBlock must be immediately followed by attributes or the documented declaration", with a quick fix that removes blank lines between a docblock and the declaration it documents.

### Fixed

- "Inject Drupal service into constructor" quick fix no longer generates a `create()` override for classes whose `create()` comes from Drupal's `AutowireTrait` (used directly or inherited, e.g. via `ControllerBase` on Drupal 10.2+) — that would have replaced working autowiring with TODO placeholders. The injected constructor parameter now carries an `#[Autowire(service: '...')]` attribute instead, so the trait resolves it by service id.
- Fixed an `ArrayIndexOutOfBoundsException` in permission references/completion when `AccessResult::allowedIfHasPermission()` is called with a single argument (normal while typing).
- Fixed a crash in hook indexing and gutter line markers on a bare `#[Hook]` attribute with no arguments (normal while typing).
- Fixed a data race in the theme index: concurrent indexing could record the wrong hook name for `hook_theme()` definitions, corrupting indexed theme data.
- Fixed annotation parsing matching the wrong key by substring: e.g. looking up `id` could return the value of `field_id` or `valid`, silently corrupting indexed entity/field-type data for annotation-based (non-attribute) plugins.
- Fixed crashes on incomplete code in the entity-storage type provider (`getStorage()` on error PSI), in `#[Hook('...')]` name completion (empty argument list, attribute on a non-method, attribute as the last element of a class), and an entity type id containing the internal split character no longer shifts the type signature.
- "Class name should use PascalCase" / "Function name should use snake_case" code-style rules no longer fire (or crash) on half-typed declarations that have no name yet.
- "Fully qualify @throws exception class" fix (and the umbrella "Fix Drupal DocBlock" fix) now resolves an imported or namespace-relative exception class to its real FQN instead of blindly prepending `\`. Previously `@throws MyException` with a matching `use ...\MyException;` was "fixed" to `@throws \MyException`, which resolves to a non-existent root-namespace class; it now becomes `@throws \Full\Namespace\MyException`. Types that don't resolve to a class still fall back to the plain `\` prefix.
- Resolving whether a method is implicitly used as a service factory (used by every method during every highlighting pass) no longer parses every `*.services.yml` file in the project on each call. Service factory methods are now served from a dedicated file-based index, removing a per-method, per-pass whole-project PSI sweep.
- Hook name completion inside `#[Hook('...')]` no longer materializes every function name in the project on each keystroke; index keys are streamed, the per-hook containing-file lists are replaced with a stop-at-first-hit probe, and the lookup is cancellable. The duplicated suggestion logic of the two hook completion providers was unified.
- "Unused class/method" suppression for service classes and route handlers no longer performs a full services/routes index scan per symbol per highlighting pass. The reverse lookups are now cached and rebuilt only when a `*.services.yml` / `*.routing.yml` file changes, never on PHP edits.
- Removed a global lock around the shared index helpers that could stall the UI thread behind background indexing, and made the project-scope key enumeration cancellable.
- Services defined in the modern autowired style — the service id is the class FQN and `class:` is omitted (`Drupal\my_module\MyService: ~`) — are now resolved to their class, so type inference, completion, and the DI inspection work for them.
- All `#[Hook]` classes in a file are now indexed (previously only the first class was), and attribute-only render elements without a docblock are now recognized.
- Index data with a serialized value over 64 KB (e.g. a theme with very many variables) no longer fails to store; index versions were bumped, so a one-time reindex of Drupal-specific data will occur.
- Libraries and permissions indexing now only parses `*.libraries.yml` / `*.permissions.yml` / `user.role.*.yml` files instead of every YAML file in the project.
- Hardened various completion/reference paths against incomplete code (render array completions, `#`-insertion at file start, permission navigation to a vanished file).
- Indexing no longer depends on the plugin's settings: previously, toggling "Enabled" (or the services/routes switches) left stale index data on disk until a full manual reindex. Indexes are now always built and the features themselves respect the settings; the settings-change reindex workaround was removed.
- Removed the "Configuration directory path" setting: `field.storage.*`, `user.role.*`, and `eck.eck_entity_type.*` config files are now indexed wherever they live (still excluding test/fixture directories), so fields and permissions from module-provided `config/install` are recognized too.
- Multiline array code-style rule now also checks plain value lists (`['a', 'b']` without keys), not only `key => value` arrays, and locates the closing bracket via PSI, so `]` inside strings or comments can no longer produce a wrong fix.
- Function declaration spacing rule no longer miscounts parentheses inside `//`, `#`, and `/* */` comments in parameter lists (previously a comment containing `)` silently disabled the check for that function).
- Control structure spacing rule now locates the keyword inside the inspected element itself; when two control structures shared one line, the fix could previously target the wrong keyword.
- Fully-qualified class name rule now treats namespaces and FQNs case-insensitively (PHP semantics) and reads `use` imports from PSI, so heredoc/string content that looks like a `use` statement no longer corrupts import handling.
- "Add missing route parameters" quick fix now performs a single targeted insertion instead of rewriting the whole argument list: multiline arguments, comments, and formatting are preserved verbatim, legacy `array (...)` syntax is supported (previously a silent no-op), and parameter names are escaped in the generated entry.

- "Inject Drupal service into constructor" quick fix now inserts the injected property/constructor after any class-level trait `use` statements instead of before them (which produced invalid PHP).
- "Inject Drupal service into constructor" quick fix now matches the existing constructor's style: when the constructor already uses constructor property promotion it adds a promoted parameter (no separate field/assignment); for classic constructors it appends the new parameter cleanly after the last existing parameter, preserving trailing commas and multiline signatures (no more `$foo,, $bar`). Indentation is inferred from the surrounding code.
- Custom/unknown docblock tags (e.g. Drush annotations such as `@command`, `@aliases`, `@option`, `@usage`) no longer trigger spurious "DocBlock tag groups must be separated by a blank line" warnings. All non-standard tags are now treated as a single `other` group, so a run of mixed custom annotations is not required to be separated by blank lines. Standard groups (`@param`, `@return`, `@throws`, `@deprecated`/`@see`) are unaffected. The umbrella "Fix Drupal DocBlock" fix now follows the same grouping and no longer inserts blank lines between custom tags.
- "Add DocBlock summary punctuation" fix no longer inserts the period after the closing `*/` (corrupting the comment) when the summary text shares its line with the comment's closing marker.
- "Inject Drupal service into constructor" quick fix now locates the class body's opening brace via PSI instead of a raw text scan, so a class-level PHP 8 attribute whose argument contains a `{` (e.g. `#[Some\Attr('{token}')]`) no longer causes the injected property/constructor to be placed inside the attribute, producing unparseable PHP.
- "Inject Drupal service into constructor" quick fix now generates a `create()` override for container-injection targets that already have a constructor but no own `create()` (previously the added constructor parameter had nothing supplying it, causing a runtime `ArgumentCountError`). Existing constructor arguments are emitted as clearly-marked `null /* TODO: provide $<name> */` placeholders; a parameterless constructor yields a clean `create()` with no TODO.
- "Inject Drupal service into constructor" quick fix no longer silently leaves an own `create()` unchanged when it builds the instance indirectly (e.g. `$x = new static(...); return $x;`). It now inserts a `// TODO` pointing at the constructor call to wire the new service, while still adding the constructor parameter.

## [0.7.1] - 2026-03-12

### Fixed

- Fatal error caused by PermissionIndex

## [0.7.0] - 2025-02-22

PHPStorm 2025 support

## [0.6.6] - 2025-02-22

### Added

- Attribute hooks autocomplete and line marker 🕺
- Permissions autocomplete
- Theme reference provider for files
- Attributes support for content/config entities, fields, themes, render/form elements.

## [0.6.5] - 2025-02-09

### Added

- Attribute hooks autocomplete and line marker 🕺
- Permissions autocomplete
- Theme reference provider for files
- Attributes support for content/config entities, fields, themes, render/form elements.

## [0.6.4] - 2024-08-19

### Fixed

- Compatibility with latest PHPStorm version

## [0.6.3] - 2024-05-20

### Fixed

- Symfony error
- \Drupal::service type provider

## [0.6.2] - 2024-03-06

Temporary disabled `\Drupa::service `type resolving, because of changes made in the latest Symfony plugin version.

## [0.6.1] - 2024-02-27

### Added

- Field autocomplete in `hasField` method.
- PhpStorm metadata file with all missing features provided by `drush generate phpstorm-metadata`, so now you can delete it without worries.

## [0.6.0] - 2024-02-15

### Added

- Added entity reference type provider e.g. $node->get('field_entity_reference_reference')->entity

## [0.5.2] - 2024-02-08

### Added

- Implemented ability to provide a type for ::create method and to autocomplete fields in it

## [0.5.1] - 2024-02-07

### Added

- Libraries autocomplete 🕺

## [0.5.0] - 2024-02-07

### Added

- Theme index. Theme and its variables autocomplete.
- Implement reference provider for themes. Implement the ability to autocomplete/reference when a theme/hook is assigned.
- Fields autocomplete for eck entities.

### Reworked

- Field type provider and its autocomplete

## [0.4.1] - 2024-02-04

### Added

- Fields autocomplete for entity query. Entities autocomplete for \Drupal::entityQuery

## [0.4.0] - 2024-02-02

### Added

- Plugin settings 🎉 (ability to choose configuration directory, disabled plugin, clear index) 
- Annotator for render element type (highlighting) 
- (optional) Line marker for render element type

### Fixed

- Completion for storage didn't work on an empty string.
- in rare cases fields where autocomplete from another entity with the same ID prefix.
- Bug with \Drupal::service

## [0.3.2] - 2024-01-31

### Added

- Autocomplete for render elements and their properties.
- Line marker for Render/Form element
- Completion priority for direct properties.
- Implemented the ability to try to find entity from storage only knowing its interface/class.
- Storage autocomplete in case Symfony is enabled, and storage is returned from service.

## [0.3.1] - 2024-01-31

### Added

- Line marker for Render/Form element
- Completion priority for direct properties.

## [0.3.0] - 2024-01-30

Added:

- Implemented ability to try to find entity from storage only knowing its interface/class.
- annotator bug
- storage autocomplete in case Symfony is enabled, and storage is returned from service.

## [0.2.0] - 2024-01-30

### Added

- Autocomplete for render elements and their properties.

## [0.1.0] - 2024-01-28

### Added

- Integration with symfony service type provider for \Drupal::service
- Config entity support (autocomplete, references)
- Field properties autocomplete e.g. $node->get('field_address')->addr| (it will autocomplete address_line1, address_line2)
- Node::load/create/loadMultiple type provider
- Almost all base fields are autocompleted now. (fields specified in a few static methods on the same class WIP)
- Storage autocomplete for getAccessControlHandler, getStorage, getViewBuilder, getListBuilder, getFormObject, getRouteProviders, hasHandler methods.
- Type provider for storage again work! But now it provider only interfaces

### Fixed

- Fixed bug related to broken entities type provider.
- Fields autocomplete in inappropriate places.

## [0.0.5]

### Added

- Index caching
- Static content entity provider e.g. Node::load()
- Fields autocomplete only knowing entity interface e.g. NodeInterface
- ECK content entity type support.

### Fixed

- Fields autocomplete was not working in certain circumstances
- Content entity provider was not working in certain circumstances
- Deprecated usage of baseDir

[Unreleased]: https://github.com/nvelychenko/drupal-extend/compare/v0.7.1...HEAD
[0.7.1]: https://github.com/nvelychenko/drupal-extend/compare/v0.7.0...v0.7.1
[0.7.0]: https://github.com/nvelychenko/drupal-extend/compare/v0.6.6...v0.7.0
[0.6.6]: https://github.com/nvelychenko/drupal-extend/compare/v0.6.5...v0.6.6
[0.6.5]: https://github.com/nvelychenko/drupal-extend/compare/v0.6.4...v0.6.5
[0.6.4]: https://github.com/nvelychenko/drupal-extend/compare/v0.6.3...v0.6.4
[0.6.3]: https://github.com/nvelychenko/drupal-extend/compare/v0.6.2...v0.6.3
[0.6.2]: https://github.com/nvelychenko/drupal-extend/compare/v0.6.1...v0.6.2
[0.6.1]: https://github.com/nvelychenko/drupal-extend/compare/v0.6.0...v0.6.1
[0.6.0]: https://github.com/nvelychenko/drupal-extend/compare/v0.5.2...v0.6.0
[0.5.2]: https://github.com/nvelychenko/drupal-extend/compare/v0.5.1...v0.5.2
[0.5.1]: https://github.com/nvelychenko/drupal-extend/compare/v0.5.0...v0.5.1
[0.5.0]: https://github.com/nvelychenko/drupal-extend/compare/v0.4.1...v0.5.0
[0.4.1]: https://github.com/nvelychenko/drupal-extend/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/nvelychenko/drupal-extend/compare/v0.3.2...v0.4.0
[0.3.2]: https://github.com/nvelychenko/drupal-extend/compare/v0.3.1...v0.3.2
[0.3.1]: https://github.com/nvelychenko/drupal-extend/compare/v0.3.0...v0.3.1
[0.3.0]: https://github.com/nvelychenko/drupal-extend/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/nvelychenko/drupal-extend/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/nvelychenko/drupal-extend/compare/v0.0.5...v0.1.0
[0.0.5]: https://github.com/nvelychenko/drupal-extend/commits/v0.0.5
