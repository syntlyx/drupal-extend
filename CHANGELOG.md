<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# drupal-extend Changelog

## [Unreleased]

### Added

- Bundle id completion and navigation: in `entityQuery('node')->condition('type', '…')` (when the condition field is the entity's bundle key from its `entity_keys`), `Node::create(['type' => '…'])`, `$storage->create()/loadByProperties([...])`, and `drupalCreateNode([...])`, bundle ids are suggested from the bundle config objects already in the project (`node.type.*`, `taxonomy.vocabulary.*`, media/block_content/comment/paragraph/contact/profile types), and the value navigates to the bundle's config YAML.
- The Drupal code-style linter now participates in the IDE's standard **Code Cleanup**: Code → Code Cleanup… and the "Cleanup code" checkbox in the commit dialog apply all rule quick fixes automatically, so style normalizes as part of the normal commit workflow (before your pre-commit phpcs ever sees the code).
- The routing YAML inspection's "unknown handler method" problem now offers a "Create method" quick fix that generates a documented stub on the existing handler class (`_controller`/`_form`/`_custom_access` get an `array`-returning handler, `_title_callback` a `string`-returning one). Offered only for writable project classes — never for core/vendor.
- The `*.services.yml` "Missing constructor argument" inspection now offers a "Fill constructor arguments" quick fix: it appends the missing positional arguments, matching each one to the best project service by the constructor parameter's type hint (the same exact-class-then-shortest-id ranking used by the type-mismatch fix), falling back to a `'TODO'` placeholder for scalars and unmatched types. It handles all three argument shapes — inserting a new `arguments: [...]` line when the key is absent, extending an inline list, or appending `- '@id'` lines to a block list — and adds a one-time `# TODO: provide remaining arguments` comment when any placeholder was used. The fix re-checks the live argument count and no-ops when the gap is already filled.
- Per-rule severity for the code-style linter: every rule's toggle in <kbd>Settings → PHP → Drupal Extend</kbd> is now an Off / Weak warning / Warning / Error dropdown, applied per problem — so e.g. debug-function calls can render as errors while spacing rules stay weak warnings. Discouraged debug functions default to Warning, everything else to Weak warning. No performance cost: severity is resolved once per inspection pass in the existing single-visitor dispatch.
- New code-style rule "function docblock tags" (Drupal.Commenting.FunctionComment): a function/method that has a docblock must document every parameter (promoted constructor properties included) with `@param` and a declared non-void return with `@return`. The "Add missing doc tags" quick fix — also applied by the file-wide fix — generates Drupal-format tags with `\FQN` types and descriptions auto-humanized from the parameter names (`$entityTypeManager` → "The entity type manager."). `{@inheritdoc}` docblocks are skipped; name mismatches stay the param-name rule's job.
- The "Fix Drupal code style in file" action now also generates the missing `@file` docblock, and the generated description follows core conventions derived from the file name (`nll_core.install` → "Install, update and uninstall functions for the Nll Core module.", `.module` → "Hook implementations for …", `.theme`/`.profile` accordingly) instead of a TODO placeholder.
- The docblock summary length rule gained a "Wrap summary at 80 characters" quick fix (word-boundary wrapping onto continuation lines; also applied by the file-wide fix). Single words longer than the limit still get no fix.
- Form API support: form callback strings — `'#submit'`, `'#validate'`, `'#element_validate'`, `'#after_build'`, `'#pre_render'`, `'#process'`, and `'#ajax' => ['callback' => …]` — are now references: `'::method'` resolves to the class method (Ctrl-click + rename), `[Foo::class, 'method']` / `[$this, 'method']` and plain function names resolve too, and callback methods are no longer reported as unused. `$form_state->getValue()/hasValue()/setValue()` completes the keys declared via `$form['…'] = …` in the same class.
- Service tag support in `*.services.yml`: tag name completion under `tags:` (mapping and scalar forms, project tags plus a curated core-tag list), completion and navigation for `!tagged_iterator` / `!tagged_locator` argument tags (navigation goes through the usage flow below), and a weak warning for unknown service tags.
- Tag navigation is unified through the usage flow service ids have: Ctrl+Click / Find Usages on a tag name **anywhere** — the `tags: - { name: '…' }` declaration, a `!tagged_iterator` / `!tagged_locator` argument, or a `{ name: service_collector, tag: '…' }` collector — shows the other side of the tag as native usages (file names, code lines). From a declaration that's the consumer site (usually exactly one, so it jumps straight there); from a consumer it's every service declaring the tag. Sibling declarations of the same tag don't pollute the list, and there are no more choose-declaration popups with bare target names. Completion and the unknown-tag warning cover the `service_collector` `tag:` value too.
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
- The "Convert Drupal plugin annotation to attribute" quick fix now also works for plugin types outside the built-in core list — contrib annotations like `@FieldDiffBuilder` (diff module) and core ones added after the list was compiled. The conversion is offered only when the owning module actually ships a matching `…\Attribute\<Name>` class (the signal that its plugin manager understands attribute discovery — converting without it would silently break the plugin), and only when every annotation key matches a constructor parameter of that attribute class. Long simple arrays (e.g. big `field_types` lists) now render one entry per line instead of a single over-long line.
- Find Usages on a plugin class now surfaces the enabling config even when the config-sync directory is excluded from the project: the plugin-id search widens the chosen scope (the default "Project Files" doesn't cover excluded/library locations) by the detected config directories. Renaming a plugin class never rewrites the `plugin_id` value in config — the id comes from the attribute, which a class rename doesn't change.
- Plugin ids in config YAML are now real references: Ctrl+Click on `plugin_id:` in `rest.resource.*`, a views handler `plugin_id`, a block placement `plugin:`, `system.action.*` `plugin:`, `media.type.*` `source:`, image style effect / text format filter ids, and Layout Builder `configuration.id`/`layout_id` navigates to the plugin class (derived ids like `my_block:derivative` land on the deriver base class). In the other direction, Find Usages on a config-enabled plugin class now lists the config lines that enable it — previously the default search looked for the class *name*, which never occurs in config, so the class looked used-nowhere despite being enabled.
- Config-enabled plugin classes are no longer reported as "Unused element" when project config references their plugin id (attribute and legacy annotation forms both recognised, derived ids like `my_block:derivative` match their deriver base class): REST resources (`rest.resource.*` → `plugin_id`), blocks (`block.block.*` placements and Layout Builder components), layouts (Layout Builder `layout_id`), actions (`system.action.*`), image effects (`image.style.*`), media sources (`media.type.*`), text format filters (`filter.format.*`), and views handler/display plugins (`views.view.*` — fields/filters/sorts/arguments/relationships/areas, style/row/pager/cache/access/exposed form/query/display, and contextual-filter default arguments). A plugin no config references keeps its warning.
- Procedural hook support: hook implementations in `.module`, `.install`, and `.theme` files (and their parameters) are recognised as implicitly used and no longer reported as unused/dead.
- DocBlock code-style rule "DocBlock must be immediately followed by attributes or the documented declaration", with a quick fix that removes blank lines between a docblock and the declaration it documents.

### Fixed

- Config-sync directories excluded from the project (a common setup — e.g. an excluded top-level `config/`) are now indexed anyway: the plugin contributes them as a synthetic library (detected by their `core.extension.yml`), the same mechanism the platform uses for excluded `node_modules`. Previously exclusion silently disabled every config-driven feature — plugin usage detection (REST resources, views handlers, blocks, field plugins looked "unused" despite being enabled by config), config name completion, and bundle ids.
- Field type / widget / formatter plugin classes are no longer reported as "Unused element". A `#[FieldType]` (or `@FieldType`) class is plugin-discovered and selectable in the Field UI the moment it exists, so it now counts as implicitly used unconditionally — and the `default_widget`/`default_formatter` of every field type defined in the project count with it. A widget/formatter also counts as used when its `field_types` names a field type that exists in the project (or is used by config), or when its id is referenced anywhere the plugin now looks: `field.storage.*.yml`/`field.field.*.yml`, entity form/view displays (including Layout Builder components), `views.view.*.yml` field handlers, and base field definitions in code (`BaseFieldDefinition::create('…')`, `->setDisplayOptions('form'|'view', ['type' => '…'])`). Only widgets/formatters whose declared `field_types` match nothing and that nothing references keep the warning.
- The string-quotes rule (and the whole-file "Fix Drupal code style" / Code Cleanup pass) no longer touches double-quoted strings inside doc-block annotations (`@FieldDiffBuilder(id = "…")`, `@Translation("…")`, plugin `field_types` lists, …). Those strings parse as regular string-literal PSI, so they were flagged and even rewritten to single quotes — which breaks the annotation, since Doctrine/Drupal annotation syntax only accepts double-quoted strings. PhpDoc string literals are now excluded from both the inspection and every fix path.
- The translation placeholder inspection no longer reports a false "has no replacement argument" / "is not used in the string" pair when a placeholder is immediately followed by sentence punctuation (`t('Paid $%payoff. Minimum is $%min', ['%payoff' => …, '%min' => …])`). The placeholder scanner's token includes `.`/`-` (dotted keys are legal), so the trailing period was swallowed into the token and exact-equality matching failed. Matching now mirrors `FormattableMarkup`'s `strtr()` semantics: a scanned token is satisfied by any argument key occurring at its start offset, and a key counts as used when it occurs literally anywhere in the string.
- The whole-file "Fix Drupal code style" action (and Code Cleanup) now applies the string-quotes rule: convertible double-quoted strings (`"hello"`) become single-quoted, using the same conversion the per-problem quick fix does — strings with interpolation, double-quote-only escapes (`\n`, `\t`, …), or embedded apostrophes stay double-quoted. Previously the rule was flagged by the inspection but silently skipped by the file-wide fixer.
- The control-structure spacing rule no longer flags a `foreach` with a destructured value — `foreach ($windows as [$from, $to])` — as a spacing violation. The PSI of a destructured value is flat (the value is a variable *inside* the brackets), so the rule measured the `as` gap up to that inner variable and its quick fix would even swallow the opening `[`. Gaps are now anchored on the token that follows `as`/`=>` (`[`, `list(`, `&`, or the variable), which also stops the fix from deleting an inline comment sitting between `as` and the value.
- The "Inject Drupal service into constructor" fix no longer generates broken code for a class that has no constructor of its own but extends a base that declares one with arguments (a plugin extending `PluginBase`/`BlockBase`, a form extending `ConfigFormBase`, etc.). Previously it synthesized a fresh `__construct(NewService $x)` that shadowed the parent's constructor, dropped its required parameters, and never called `parent::__construct()` — leaving a class that could not be instantiated. The inspection now declines to offer injection in that case (no warning, no quick fix) rather than emit code that won't run; classes with no inherited constructor, or with their own constructor, are unaffected.
- The whole-file "Fix Drupal code style" / Code Cleanup pass no longer freezes the IDE on files with several undocumented functions. The `array` → `array{…}` shape inference for generated `@param` tags could fall back to a **project-wide reference search** (to read the shape from call sites) for every pass-through array parameter, and the whole-file fixer ran that search once per function, synchronously on the EDT under a write lock. Call-site inference is now limited to a single, deliberate docblock fix ("Add missing doc tags" / "Fix Drupal DocBlock" on one docblock); the whole-file and batch paths keep the cheap in-body inference (keys read in the body, `return [...]` literals, locally built render arrays) and fall back to a plain `array` for pass-through parameters instead of searching.
- The whole-file "Fix Drupal code style" / Code Cleanup pass no longer strips the blank line that separates a docblock's one-line summary from its longer description (a valid, expected Drupal shape). The umbrella docblock rewrite dropped every blank body line and only re-inserted separators around tag groups, so a summary/description docblock — and multi-paragraph descriptions — got collapsed on any cleanup run even when the docblock had no other issue. Blank lines in the description area (before the first tag) are now preserved; redundant (consecutive) and edge blanks are still removed, and tag-group separators are unchanged.
- Parameter array shapes now also infer from call sites: when the function body itself reads no keys (pass-through parameters like a writer's `$values`), the shape comes from what the callers actually pass — literal array arguments and locally built variables (`$values = [...]` plus `$values['key'] = …` writes), merged across up to 8 call sites; for methods, interface-typed calls count for the implementation. Keys written under an `if`/loop/`switch` branch render optional (`file_id?:`), in both caller-derived and `@return` shapes.
- Array-shape key optionality now honours presence checks anywhere in the function: a key read inside an `array_key_exists('key', $param)` / `isset($param['key'])` / `empty($param['key'])` guard renders `(optional)` even though the read itself is unguarded — previously only reads directly wrapped in `??`/`isset()` counted.
- A multiline array-shape `@param` (`array{` … `} $options`) now counts as documenting its parameter: previously the missing-tags detection only looked at the tag line, so the fixpoint fixer re-inserted the same `@param` on every pass and the docblock filled up with duplicates.
- The tag-description-required rule now covers `@throws` and carries the "Fix Drupal DocBlock" quick fix, and the docblock fixer generates a description for **every** `@param`/`@return`/`@throws` tag that lacks one (same wording the missing-tags generator uses) — previously only tags the generator itself inserted were described, so a docblock with several pre-existing bare `@throws` ended up with a description on just the generated one.
- "Fix Drupal DocBlock" and "Add missing doc tags" now run the per-docblock fixpoint fixer: one action run normalizes the docblock AND inserts the missing `@param`/`@return`/`@throws` tags (plus the summary wrap and declaration spacing). Previously the docblock fix didn't add tags at all, and the tag fix could leave the docblock violating spacing rules — both required a second run.
- The "Add missing doc tags" fix infers array shapes: a plain `array` param/return renders as `array{key: type, …}` when the body makes the keys obvious — from `return [...]` literals (merged across returns, keys missing from some render optional `key?:`), from locally built variables (`$build = [...]` plus `$build['#markup'] = …` — the render-array pattern, `#`-keys quoted), and for parameters from `$param['key']` reads (`??`/`isset()`-guarded reads render optional). Uniform literal lists render as `string[]`-style; anything dynamic falls back to plain `array` — a wrong shape is worse than none. Shapes that would push the tag line past 80 characters render in the multiline form (`array{` / one key per line / `}`), which the docblock rules already understand and normalize.
- The missing-doc-comment rule now also covers class constants and properties (Drupal requires a docblock on every member): undocumented declarations are reported, and the "Add member doc comment" fix — also applied by the file-wide fix — generates a Drupal-format docblock with a summary humanized from the member name (`LOAN_PACKAGE` → "The loan package.") plus a fully qualified `@var` tag for properties.
- The function-docblock-tags rule now also covers `@throws`: exceptions the function demonstrably throws (`throw new FooException(...)`, and `throw $e` rethrows of a caught exception) but doesn't document are reported, and the "Add missing doc tags" fix generates the `@throws` group (fully qualified class, placeholder description) after `@return`. Conservative on purpose: throws inside nested closures belong to the closure, and throws lexically inside a `try` block are assumed handled — a wrong `@throws` is worse than a missing one.
- `@throws` detection also propagates from direct calls — the cases PhpStorm flags as "Unhandled \Drupal\Core\Entity\EntityStorageException" on `$storage->save()`: the `@throws` declared by the called method/function/constructor (chasing `{@inheritdoc}` up to the documented interface declaration) is reported on the caller and inserted by the fix. Unchecked families (`\RuntimeException`, `\LogicException`, `\Error` and subclasses) are not propagated, matching PhpStorm's default; calls inside `try` blocks are assumed handled.
- The "Add missing doc tags" fix now fully qualifies class types through the file's `use` statements (aliases included) and the current namespace — a parameter hinted `ContextFactory` renders as `@param \Drupal\my_module\ContextFactory` instead of the wrong `\ContextFactory`.
- "Fix Drupal code style in file" now runs its edit collection to a fixpoint (capped), so fixes that used to require a second action launch — the summary wrap held back by an umbrella docblock rewrite on the same block, the `@file` insertion, missing `@param`/`@return` insertion — land in a single run.
- "Missing @param for $x" / "Missing @return tag" problems now highlight the whole docblock instead of an arbitrary existing line (previously a missing `@param` could underline the `@return` line, which read as if the `@return` itself were wrong).
- The file-wide "Fix Drupal code style" could corrupt a procedural file's top docblock when the `@file`-tag insertion and the umbrella docblock rewrite both targeted it: overlapping edits are now filtered at the aggregator AND dropped defensively in the edit applier (the skipped fix converges on the next run).
- Service-id completion no longer hides config-name completion on receivers that look like both a container and a config factory (e.g. `$configFactoryContainer->get('…')`).
- The "DocBlock must be immediately followed by the documented declaration" rule got its own settings entry — it was silently sharing the blank-line-normalization rule's toggle and severity.
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
