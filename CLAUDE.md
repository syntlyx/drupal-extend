# Drupal Extend — agent & contributor rules

PhpStorm/IntelliJ Platform plugin that enriches Drupal development (autocomplete,
references, type inference, inspections, code-style rules). Kotlin, built with the
IntelliJ Platform Gradle Plugin.

## Non-negotiable priorities

Every change is judged against these, in order:

1. **Drupal 11.3 first.** 11.3 is the primary supported target. Features must work
   on current Drupal core; version-gated behaviour goes through
   `DrupalProjectVersion` (`isAtLeast(project, DrupalCoreVersion(...))`), never
   hard-coded assumptions. Don't break older cores, but when there's a trade-off,
   11.3 correctness wins.
2. **Stability.** This runs inside the user's IDE. A thrown exception, a frozen UI,
   or a wrong reference is worse than a missing feature. Guard nullables, never
   `!!` on platform PSI, fail soft.
3. **Performance.** The IDE calls our code on every keystroke, completion, and
   indexing pass. Cheap, cancellable, off the EDT, index-backed. See *Performance*.
4. **One code style for the whole repo.** See *Code style*. No mixed conventions.

## Build, test, run

```bash
./gradlew test                 # unit tests (BasePlatformTestCase, headless)
./gradlew test --tests "com.github.nvelychenko.drupalextend.inspection.DrupalCodeStyleInspectionTest"
./gradlew buildPlugin          # produces the distributable zip
./gradlew verifyPlugin         # IntelliJ Plugin Verifier — run before any release
./gradlew runIde               # launch a sandbox PhpStorm with the plugin
./gradlew koverHtmlReport      # coverage
```

- Toolchain: **JVM 21, Kotlin 2.3**. Target platform: PhpStorm `PS` 2025.1
  (`pluginSinceBuild` 251+). Versions live in `gradle.properties` / `gradle/libs.versions.toml`.
- CI (`.github/workflows/build.yml`) runs tests + Qodana + `verifyPlugin`. A change
  isn't done until `./gradlew test verifyPlugin` is green locally.
- Always add a `## [Unreleased]` entry to `CHANGELOG.md` for user-visible changes.

## Architecture map

| Area | Path | Pattern |
|------|------|---------|
| Indexes | `index/` | `FileBasedIndexExtension<K,V>`, registered in `plugin.xml` |
| References | `reference/` | `PsiReferenceContributor` + `PlatformPatterns` |
| Completion | `completion/` | `CompletionContributor` with `init { extend(...) }` |
| Inspections | `inspection/` | `LocalInspectionTool.buildVisitor()` |
| Code-style rules | `inspection/codestyle/` | rule registry + PSI-type dispatcher (see below) |
| Type inference | `type/` | `PhpTypeProvider4`, registered as `typeProvider4` |
| Line markers | `lineMarker/` | `LineMarkerProvider.collectSlowLineMarkers()` |
| Drupal version | `project/DrupalProjectVersion.kt` | composer.lock/json detect + settings override |
| Settings | `project/` | `project.drupalExtendSettings` |

Every extension is wired in `src/main/resources/META-INF/plugin.xml`. User-facing
strings go through the resource bundle (`messages.DrupalExtendBundle`), not literals.

### Code-style rule subsystem (`inspection/codestyle/`)

Actively developed. To add a Drupal coding-standard rule:

1. Implement `DrupalCodeStyleRule` (`id`, `isEnabled(settings)`, `inspect(element, context)`).
2. Register it in `DrupalCodeStyleRuleRegistry` against the right `Target` PSI type(s).
   The dispatcher routes elements by type — pick the narrowest target, don't match `PHP_FILE`
   and re-walk.
3. Report via `context.register(DrupalCodeStyleProblem(..., fixes))`; build fixes with
   `DrupalCodeStyleTextEditQuickFix` returning `List<TextEdit>`.
4. Add a `BasePlatformTestCase` test (enable the rule in `setUp`, assert highlighting + fix).

Rules must be **idempotent** (applying the fix twice changes nothing) and operate only on
their own PSI subtree.

## Performance (read before touching hot paths)

- Guard index/PSI access with `if (DumbService.isDumb(project)) return` — never query
  indexes during dumb mode.
- Prefer **index lookups over PSI traversal** for project-wide queries. Don't walk the
  whole file tree.
- Cache expensive derivations with `CachedValuesManager` + the correct modification
  tracker (index modification tracker for index-derived data).
- Long loops over PSI: call `ProgressManager.checkCanceled()`. Respect read actions;
  don't block the EDT.
- Inspections/contributors run per-keystroke — keep `inspect`/`addCompletions` allocation-light.

## Code style (single style, whole repo)

Enforced by `.editorconfig`; documented here for agents.

- 4-space indent, no tabs, final newline, LF.
- **Explicit imports only — never wildcard imports.**
- `val` over `var`; immutable collections by default. `var`/mutable only with reason.
- Expression bodies for single-expression functions.
- `object` for singletons (registries, version logic); `data class` for value types;
  `@Serializable` for anything persisted in an index.
- `internal` visibility by default for plugin-internal types; `public` only when crossing
  package boundaries.
- Nullable-safe: `?.let` / `?.takeIf` / `as?` / `getOrNull()`. No `!!` on platform APIs.
- Match the surrounding file's idiom. If you change conventions, change them repo-wide,
  not in one file.

## Orchestration — when to go solo, spawn a subagent, or run a team

Default to working **inline** for focused, single-area changes. Reach for subagents when
the work is parallelizable, needs isolation, or benefits from a fresh adversarial pass.

- **Solo (inline):** one rule, one bug, one reference provider, a search you can scope
  yourself. Most tasks.
- **Single subagent:** a self-contained chunk you can hand off — "write tests for X",
  "explore how completion is wired", "implement this one code-style rule from spec".
  Use the project subagents in `.claude/agents/` (below).
- **Team / parallel fan-out** (multiple subagents, or a Workflow): genuinely independent
  work that fits one of these shapes —
  - adding **several code-style rules** at once → one `drupal-codestyle-rule` agent per rule;
  - a **broad audit/review** across many files → reviewers per dimension (correctness /
    threading / performance / style), then synthesize;
  - a **migration/sweep** touching many sites → discover sites inline, then one agent per
    site (use worktree isolation if they edit in parallel).
  Only fan out when the subtasks don't depend on each other. If stage B needs all of
  stage A's results, that's a barrier — keep it sequential.
- Always **verify** integration after a fan-out: `./gradlew test verifyPlugin`. Parallel
  agents don't see each other's edits.

### Model selection (by task complexity)

The project subagents pin a sensible default model; override per task when it doesn't fit.

| Model | Use for |
|-------|---------|
| **Opus 4.8** (`opus`) | Architecture & API design, PSI/threading/concurrency, type inference, tricky stability bugs, final pre-release review, anything where a wrong call costs the most. |
| **Sonnet 4.6** (`sonnet`) | Routine feature work to an existing pattern, new code-style rules, writing tests, straightforward references/completion. The everyday default. |
| **Haiku 4.5** (`haiku`) | Code search, mechanical edits, renames, doc/changelog tweaks, gathering file lists. |

Rule of thumb: **threading, performance, and release-gating work → Opus.** Pattern-following
implementation → Sonnet. Lookups and mechanical edits → Haiku.

## Definition of done

1. Behaviour correct on Drupal 11.3; version-gated via `DrupalProjectVersion` where relevant.
2. No new EDT blocking, no uncancellable loops, no un-guarded dumb-mode index access.
3. `./gradlew test verifyPlugin` green; new behaviour covered by a `BasePlatformTestCase` test.
4. Code matches `.editorconfig` and the conventions above.
5. `CHANGELOG.md` `[Unreleased]` updated for user-visible changes.
