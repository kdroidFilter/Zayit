# Rapport comparatif — SeforimApp vs Otzaria

Ce rapport présente une analyse approfondie du code de deux projets open‑source de bibliothèque juive: SeforimApp (Kotlin Multiplatform Desktop avec Compose) et Otzaria (Flutter/Dart multiplateforme). Il couvre l’architecture, la recherche plein texte, les données, l’UI, la localisation, la distribution, les tests et les licences, avec preuves pointées vers des fichiers et lignes clés.

## Résumé exécutif
- SeforimApp met l’accent sur une expérience Desktop robuste, un moteur de recherche hébraïque de niveau production (Lucene 10 + HebMorph), une architecture DI claire et un packaging natif prêt (DMG/MSI/DEB).
- Otzaria priorise la couverture multiplateforme (Windows/macOS/Linux/Android/iOS/Web), l’import utilisateur (PDF/DOCX/TXT) et une UI Flutter moderne avec gestion d’état par BLoC.
- Vainqueur global (poste Desktop, recherche hébraïque de haute qualité): SeforimApp.
- Vainqueur selon cas d’usage mobile/web et import de documents: Otzaria.

## Méthodologie et périmètre
- Clonage local de Otzaria: `build/analysis/otzaria` (branche par défaut, `--depth 1`).
- Lecture approfondie:
  - SeforimApp (app Kotlin/Compose) et SeforimLibrary (modules core/dao/generator).
  - Otzaria (lib Flutter: navigation, search, data providers, modèles, settings) + `pubspec.yaml`.
- Vérification des commits récents et CI.

## Fiche d’identité technique
- SeforimApp
  - Kotlin Multiplatform + JetBrains Compose Multiplatform, thèmes Jewel, Metro DI. Entrée Desktop: `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/main.kt:51`.
  - Packaging natif (DMG/MSI/DEB): `SeforimApp/build.gradle.kts:190` avec `mainClass` `MainKt` `SeforimApp/build.gradle.kts:178`.
  - Moteur de recherche Lucene 10 + HebMorph: dépendances `lucene-core`, `hebmorph-lucene` `SeforimApp/build.gradle.kts:145`, `SeforimApp/build.gradle.kts:150`.
  - DI Metro: graphe `AppGraph` expose `SeforimRepository` et `LuceneSearchService` `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/di/AppGraph.kt:37`, `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/di/AppGraph.kt:38`.
  - Générateur d’index (outillage): SeforimLibrary/generator (voir plus bas).
- Otzaria
  - Flutter/Dart, BLoC, Material 3, locale par défaut he‑IL: `build/analysis/otzaria/lib/app.dart:17`, `build/analysis/otzaria/lib/app.dart:24`, `build/analysis/otzaria/lib/app.dart:27`.
  - Dépendances clés (moteur de recherche Tantivy, import PDF/DOCX, DB): voir `build/analysis/otzaria/pubspec.yaml:50` → `search_engine`, `pdfrx`, `docx_to_text`, `sqflite`, `isar`, `msix` (`build/analysis/otzaria/pubspec.yaml:62`, `:65`, `:75`, `:94`, `:60`, `:100`).
  - Page racine et navigation principale: `build/analysis/otzaria/lib/navigation/main_window_screen.dart:37`.

## Architecture applicative
- SeforimApp
  - DI Metro graph: `AppGraph` fournit composants et ViewModels (`Settings`, `SeforimRepository`, `LuceneSearchService`) `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/di/AppGraph.kt:36`, `:37`, `:38`.
  - Liaison des singletons dans `AppCoreBindings`: création du driver SQLite JDBC et services Lucene/Lookup `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/di/modules/AppCoreBindings.kt:45`, `:47`, `:53`, `:61`.
  - Navigation onglets RAM‑efficient: `TabsNavHost` (mode 1 NavHost partagé vs un par onglet) `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/core/presentation/tabs/TabsNavHost.kt:35`, `:65`, `:133`.
  - Entrée Desktop Compose + Jewel, single instance, locale he par défaut `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/main.kt:64`.
- Otzaria
  - BLoC partout (navigation, settings, search). Racine MaterialApp avec locale he‑IL `build/analysis/otzaria/lib/app.dart:24`, `:27`.
  - Navigation multipages avec `PageController` et `NavigationBar` `build/analysis/otzaria/lib/navigation/main_window_screen.dart:37`, `:150`.

## Données et persistance
- SeforimApp / SeforimLibrary
  - Modèles sérialisables (commonMain): `Book`, `SearchResult` `SeforimLibrary/core/src/commonMain/kotlin/io/github/kdroidfilter/seforimlibrary/core/models/Book.kt:19`, `SeforimLibrary/core/src/commonMain/kotlin/io/github/kdroidfilter/seforimlibrary/core/models/SearchResult.kt:15`.
  - DAO/Repository via SQLDelight (JDBC SQLite): init, PRAGMA et CRUD optimisés `SeforimLibrary/dao/src/commonMain/kotlin/io/github/kdroidfilter/seforimlibrary/dao/repository/SeforimRepository.kt:31`, `:34`.
- Otzaria
  - Stockage mixte: Hive (prefs/états), Isar (collections), Sqflite (notes) `build/analysis/otzaria/lib/data/data_providers/hive_data_provider.dart:7`, `build/analysis/otzaria/lib/models/isar_collections/line.dart:1`, `build/analysis/otzaria/lib/notes/data/notes_data_provider.dart:46`.
  - Initialisation Desktop (FFI): `sqflite_common_ffi` et Hive `build/analysis/otzaria/lib/main.dart:44`, `:166`, `:248`.

## Recherche plein texte (comparatif)
- SeforimApp
  - Génération d’index Lucene avec HebMorph (outillage “generator”):
    - Entrée de build: `BuildHebMorphIndex.kt` — chargement dictionnaire HSpell, index textuel + lookup `SeforimLibrary/generator/src/jvmMain/kotlin/io/github/kdroidfilter/seforimlibrary/generator/BuildHebMorphIndex.kt:49`, `:53`, `:56`, `:57`.
    - Écriture Lucene des lignes et titres: `LuceneTextIndexWriter` champs et points d’index `SeforimLibrary/generator/src/jvmMain/kotlin/io/github/kdroidfilter/seforimlibrary/generator/lucene/LuceneTextIndexWriter.kt:24`, `:69`, `:75`.
  - Exécution (runtime) Lucene côté app:
    - Chargement paresseux du dictionnaire HebMorph, analyseurs `HebrewQueryAnalyzer` / `HebrewExactAnalyzer` avec repli StandardAnalyzer `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/search/LuceneSearchService.kt:31`, `:38`, `:48`, `:49`.
    - Ouverture index et requêtes via `IndexSearcher` `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/search/LuceneSearchService.kt:51`, `:62`, `:86`.
    - Lookup TOC/Books (préfixe) dédié `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/search/LuceneLookupSearchService.kt:30`, `:51`.
- Otzaria
  - Moteur Tantivy (via package `search_engine`) et référentiels:
    - Fournisseur central `TantivyDataProvider` (index + ref_index, cache facettes, reset index) `build/analysis/otzaria/lib/data/data_providers/tantivy_data_provider.dart:15`, `:47`, `:56`, `:59`, `:63`.
    - BLoC de recherche orchestrant comptages et résultats, facettes, fuzzy, etc. `build/analysis/otzaria/lib/search/bloc/search_bloc.dart:9`, `:60`, `:77`.
    - Logique de requête: `SearchRepository` (variantes de graphies, regex, options par mot, ordre) `build/analysis/otzaria/lib/search/search_repository.dart:21`, `:23`, `:59`.
  - Morphologie hébraïque: heuristiques regex (préfixes/suffixes) plutôt qu’un dictionnaire morphologique complet `build/analysis/otzaria/lib/search/utils/hebrew_morphology.dart:6`, `:55`, `:61`, `:66`.

Conclusion recherche: SeforimApp dispose d’un pipeline Lucene + HebMorph plus avancé pour la langue hébraïque (analyzers dédiés, normalisation, scoring), alors qu’Otzaria s’appuie sur Tantivy + heuristiques regex solides mais moins riches morphologiquement.

## Formats, import et contenus
- SeforimApp
  - Corpus intégré via `SeforimLibrary` (génération DB/Index). Pas d’import public de PDF/DOCX dans l’app.
  - Le générateur sait récupérer les sorties “otzaria-library” pour constituer le corpus local si on le souhaite: `OtzariaFetcher` `SeforimLibrary/generator/src/jvmMain/kotlin/io/github/kdroidfilter/seforimlibrary/generator/OtzariaFetcher.kt:18`, `:21`, `:63`, `:147`.
- Otzaria
  - Import utilisateur: PDF (pdfrx), DOCX (docx_to_text), TXT; parsing et indexing côté app `build/analysis/otzaria/pubspec.yaml:65`, `:75`.
  - Sources externes: intégration HebrewBooks (CSV + détection locale) `build/analysis/otzaria/lib/data/data_providers/file_system_data_provider.dart:17`, `:61`, `:131`.

## UI et UX
- SeforimApp
  - Compose Desktop + Jewel, shell principal et barre de titre custom, Single instance, config et Onboarding `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/main.kt:64`, `:90`, `:126`.
  - Navigation par onglets RAM‑friendly ou “keep alive”: `TabsNavHost` `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/core/presentation/tabs/TabsNavHost.kt:35`, `:65`, `:141`.
- Otzaria
  - UI Flutter Material 3, `NavigationBar`, `PageController`, écrans clés: Bibliothèque, Recherche, Lecture, Réglages `build/analysis/otzaria/lib/navigation/main_window_screen.dart:37`, `:129`, `:150`.

## Localisation
- SeforimApp: ressources Compose en hébreu (clés stables) `SeforimApp/src/commonMain/composeResources/values/strings.xml:2`.
- Otzaria: `MaterialApp` localisé he‑IL et `title` en hébreu `build/analysis/otzaria/lib/app.dart:24`, `:27`, `:28`.

## Performances et optimisation
- SeforimApp: SQLite JDBC avec PRAGMA pragmatiques (WAL, cache_size, temp_store) côté repository `SeforimLibrary/dao/src/commonMain/kotlin/io/github/kdroidfilter/seforimlibrary/dao/repository/SeforimRepository.kt:34`, `:36`, `:37`.
- Otzaria: notes SQLite sqflite avec PRAGMA et validation schéma, logs d’erreurs détaillés `build/analysis/otzaria/lib/notes/data/notes_data_provider.dart:46`, `:92`, `:117`, `:55`.
- Otzaria: comptage facettes avec cache global et déduplication via verrous légers `build/analysis/otzaria/lib/data/data_providers/tantivy_data_provider.dart:21`, `:25`.

## CI/CD et tests
- SeforimApp: workflow GitHub `deploy.yaml` (packaging desktop). Tests JVM présents, ex. `RegionConfigUseCaseTest` `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/onboarding/region/RegionConfigUseCaseTest.kt:7`.
- Otzaria: workflows `build-and-announce.yml`. Environ 14 tests Dart (unit/widget), ex. `test/unit/settings/settings_repository_test.dart` `build/analysis/otzaria/test/unit/settings/settings_repository_test.dart:1`.

## Licences
- SeforimApp: AGPL‑3.0 (`LICENSE`). Copyleft fort pour redistributions modifiées.
- Otzaria: UNLICENSE (`build/analysis/otzaria/UNLICENSE`), domaine public — réutilisation très libre.

## Sécurité et réseau
- SeforimApp: pas d’usage réseau sensible dans le runtime desktop par défaut (hors fetchers/outils generator).
- Otzaria: envoi d’un “phone report” via Google Apps Script (HTTP) `build/analysis/otzaria/lib/services/phone_report_service.dart:10`.

## Intégration croisée (intéressant)
- Le module Generator de SeforimLibrary peut télécharger et extraire “otzaria‑library” pour alimenter le corpus: `DownloadOtzaria.kt` `SeforimLibrary/generator/src/jvmMain/kotlin/io/github/kdroidfilter/seforimlibrary/generator/DownloadOtzaria.kt:13` et `OtzariaFetcher` `SeforimLibrary/generator/src/jvmMain/kotlin/io/github/kdroidfilter/seforimlibrary/generator/OtzariaFetcher.kt:18`.

## Recommandations par profil
- Desktop intensif, recherche hébraïque avancée, packaging natif stable: SeforimApp recommandé.
- Multiplateforme (y compris mobile et web), import facile de documents (PDF/DOCX/TXT), UI Flutter: Otzaria recommandé.

## Points forts et faibles (synthèse)
- SeforimApp
  - + Lucene 10 + HebMorph (analyzers hébreux), index textuel + lookup, DI soignée, packaging multi‑OS.
  - − Pas d’import utilisateur de PDF/DOCX/TXT dans l’app.
- Otzaria
  - + Couverture plateformes très large, import de formats, BLoC, UX moderne.
  - − Morphologie hébraïque par heuristiques regex (pas de dictionnaire complet), dépendances multiples côté runtime.

## Verdict
- Vainqueur global: SeforimApp — pour les usages Desktop et la qualité de la recherche hébraïque (Lucene + HebMorph) couplée à une architecture Kotlin/Compose de production.
- Vainqueur ciblé multiplateforme/import: Otzaria — si mobile/web et l’import de documents sont prioritaires.

---

## Annexes — Preuves et extraits clés
- SeforimApp (entrée Desktop, DI, recherche)
  - `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/main.kt:51`
  - `SeforimApp/build.gradle.kts:145`
  - `SeforimApp/build.gradle.kts:150`
  - `SeforimApp/build.gradle.kts:178`
  - `SeforimApp/build.gradle.kts:190`
  - `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/di/AppGraph.kt:38`
  - `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/search/LuceneSearchService.kt:31`
  - `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/search/LuceneLookupSearchService.kt:30`
  - `SeforimApp/src/commonMain/composeResources/values/strings.xml:2`
- SeforimLibrary (modèles, repository, generator)
  - `SeforimLibrary/core/src/commonMain/kotlin/io/github/kdroidfilter/seforimlibrary/core/models/Book.kt:19`
  - `SeforimLibrary/core/src/commonMain/kotlin/io/github/kdroidfilter/seforimlibrary/core/models/SearchResult.kt:15`
  - `SeforimLibrary/dao/src/commonMain/kotlin/io/github/kdroidfilter/seforimlibrary/dao/repository/SeforimRepository.kt:34`
  - `SeforimLibrary/generator/src/jvmMain/kotlin/io/github/kdroidfilter/seforimlibrary/generator/BuildHebMorphIndex.kt:53`
  - `SeforimLibrary/generator/src/jvmMain/kotlin/io/github/kdroidfilter/seforimlibrary/generator/lucene/LuceneTextIndexWriter.kt:69`
  - `SeforimLibrary/generator/src/jvmMain/kotlin/io/github/kdroidfilter/seforimlibrary/generator/OtzariaFetcher.kt:18`
- Otzaria (app, navigation, recherche, import, DB, deps)
  - `build/analysis/otzaria/lib/app.dart:24`
  - `build/analysis/otzaria/lib/app.dart:27`
  - `build/analysis/otzaria/lib/navigation/main_window_screen.dart:37`
  - `build/analysis/otzaria/lib/search/bloc/search_bloc.dart:9`
  - `build/analysis/otzaria/lib/data/data_providers/tantivy_data_provider.dart:56`
  - `build/analysis/otzaria/lib/search/search_repository.dart:23`
  - `build/analysis/otzaria/lib/search/utils/hebrew_morphology.dart:6`
  - `build/analysis/otzaria/lib/data/data_providers/file_system_data_provider.dart:61`
  - `build/analysis/otzaria/lib/notes/data/notes_data_provider.dart:46`
  - `build/analysis/otzaria/pubspec.yaml:50`
  - `build/analysis/otzaria/pubspec.yaml:62`
  - `build/analysis/otzaria/pubspec.yaml:65`
  - `build/analysis/otzaria/pubspec.yaml:75`
  - `build/analysis/otzaria/pubspec.yaml:94`
