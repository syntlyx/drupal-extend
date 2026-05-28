# drupal-extend

![Build](https://github.com/nvelychenko/drupal-extend/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/23474-drupal-extend.svg)](https://plugins.jetbrains.com/plugin/23474-drupal-extend)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/23474-drupal-extend.svg)](https://plugins.jetbrains.com/plugin/23474-drupal-extend)

The plugin enhances the Drupal development experience in PhpStorm.

## Features
* Autocomplete for Content/Config Entities Storage (eck support)
![Storage autocomplete example](./assets/storage_autocomplete.gif)

* Fields autocomplete (default configuration directory points to the `config/sync` directory, you can change it inside plugin settings. <kbd>Settings</kbd> -> <kbd>PHP</kbd> -> <kbd>Drupal Extend</kbd>)
  ![Fields autocomplete example](./assets/field_autocomplete.gif)

* Field properties autocomplete e.g. `$node->get('field_user')->en|` it will autocomplete **entity** in this case.

* Autocomplete for render element types and their properties.
  ![Render element types autocomplete](./assets/render_element_autocomplete.gif)

* Storage reference provider
  ![Storage reference](./assets/storage_reference_provider.gif)

* Fields reference provider
  ![Fields reference](./assets/field_reference.gif)

* Reference provider for Render element types and themes.
  ![Reference provider for Render element types and themes](./assets/theme_render_element_reference.gif)

* Entity Type Provider for `$storage->load/loadMultiple/loadByProperties`
  ![Entity type provider](./assets/entity_type_provider.png)

* Type Provider for static `Node::load/create/loadMultiple` methods

* Drupal services support:
  * autocomplete, references, hover, and type resolving for `\Drupal::service('')` and container `get()` calls
  * `*.services.yml` service id and class completion, references, Find Usages, and navigation to service classes
  * inspections for unknown services/classes and constructor argument mismatches

* Drupal routes and links support:
  * route name completion and references in PHP APIs such as `Url::fromRoute()`, `Link::createFromRoute()`, and form redirects
  * route name completion/references in `*.routing.yml`, `*.links.menu.yml`, `*.links.task.yml`, `*.links.action.yml`, and `*.links.contextual.yml`
  * navigation to controller/form/access handler classes and methods
  * inspections for unknown routes, missing route parameters, unknown permissions, and missing handler methods

* Dependency injection helper for replacing static Drupal service access with constructor injection.

* Version-aware Drupal plugin annotation quick fixes for core plugin attributes, including Field/Block/Views/Migrate/Render/Entity plugin annotations. Drupal core version is detected from Composer files or can be overridden in plugin settings.

* Autocomplete for `#theme`
  ![Theme autocomplete example](./assets/theme_autocomplete.gif)

* Render element type and theme highlight (Annotator)

* Fields autocomplete in content entity queries and entity storages autocomplete in `\Drupal::entityQuery()`
* Hooks autocomplete and linemarker
![hooks](https://github.com/user-attachments/assets/1a42dec5-a09a-4039-9040-29bc81adb878)


## Installation

- Using the IDE built-in plugin system:
  
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "drupal-extend"</kbd> >
  <kbd>Install</kbd>
  
- Manually:

  Download the [latest release](https://github.com/nvelychenko/drupal-extend/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

## Troubleshooting

- Entity type resolver has a chance to not work 10-20% of time if you are using `.phpstorm.meta.php` generated via `drush generate-metadata`. Plugin provides all features from that file, so you can safely delete it.
