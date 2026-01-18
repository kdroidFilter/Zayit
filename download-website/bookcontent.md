# Guide développeur — package `bookcontent`

Ce guide explique l’architecture, les règles et les bonnes pratiques pour utiliser et contribuer au package « bookcontent » de SeforimApp. Il s’adresse à un développeur débutant qui souhaite comprendre comment l’écran de lecture fonctionne (navigation, TOC, contenu paginé, commentaires, targum/liens) et comment y apporter des changements en toute sécurité.

## Vue d’ensemble

- Fonction: écran principal de lecture d’un livre avec trois panneaux: Navigation (catégories/livres), TOC (table des matières) et Contenu (lignes paginées), plus panneaux optionnels Commentaires et Targum/Liens.
- Plateforme: Compose Multiplatform (Desktop — source set `jvmMain`).
- États par onglets: chaque onglet possède son état persistant, isolé par `tabId` via `TabStateManager`.
- Persistance: positions de scroll, expansions, filtres sélectionnés, positions des split panes, etc.

## Où regarder (fichiers clés)

- ViewModel et événements
  - `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/BookContentViewModel.kt`
  - `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/bookcontent/BookContentEvents.kt`
- État unifié + persistance
  - `.../state/BookContentState.kt`
  - `.../state/BookContentStateManager.kt`
  - `.../state/StateKeys.kt`, `.../state/SplitDefaults.kt`
- Use cases (métier)
  - `.../usecases/NavigationUseCase.kt`
  - `.../usecases/TocUseCase.kt`
  - `.../usecases/ContentUseCase.kt`
  - `.../usecases/CommentariesUseCase.kt`
- UI shell et composants
  - `BookContentScreen.kt`, `ui/components/SplitPanes.kt`, `PaneHeader.kt`, `VerticalBars.kt`
- Panneaux
  - `ui/panels/categorytree/*`, `ui/panels/booktoc/*`, `ui/panels/bookcontent/*`
- DI + Navigation
  - `SeforimApp/.../framework/di/AppGraph.kt`
  - `SeforimApp/.../core/presentation/tabs/TabsNavHost.kt`
- Ressources (hebreu — user-visible):
  - `SeforimApp/src/commonMain/composeResources/values/strings.xml`

## Architecture

### ViewModel orchestrateur
- `BookContentViewModel` injecte:
  - `SeforimRepository` (données), `TabStateManager`, `TabTitleUpdateManager` et `TabsViewModel` (onglets).
- Instancie 4 UseCases: Navigation, Toc, Contenu, Commentaires.
- Expose `uiState: StateFlow<BookContentState>` enrichi de `Providers` (flows/fonctions pour la UI), pour éviter tout couplage direct de la UI au ViewModel.

### État unifié et persistant
- `BookContentState` contient: `NavigationState`, `TocState`, `ContentState`, `LayoutState`, `isLoading`, `providers`.
- `BookContentStateManager`:
  - charge l’état initial depuis `TabStateManager` (par `tabId`),
  - fournit des helpers `updateNavigation/updateToc/updateContent/updateLayout`,
  - `saveAllStates()` sérialise/sauvegarde toutes les clés `StateKeys`.
- `StateKeys` centralise toutes les clés persistées.
- `SplitDefaults` regroupe les positions et tailles mini des split panes (en pourcentage/pixels).

### Découpage métier (UseCases)
- `NavigationUseCase`:
  - chargement des catégories racine, expansion/collapse, chargement lazy des enfants et livres,
  - sélection livre, visibilité panneau « navigation », sauvegarde du scroll.
- `TocUseCase`:
  - chargement racine/enfants du TOC, expansion/collapse récursif, visibilité panneau TOC, sauvegarde du scroll,
  - reset du TOC au changement de livre.
- `ContentUseCase`:
  - Paging des lignes (Pager + `LinesPagingSource`), sélection de ligne, navigation ligne précédente/suivante,
  - gestion robuste du scroll/anchor (`anchorId` + `anchorIndex`),
  - toggles des panneaux Commentaires/ Targum avec restauration des positions mémorisées.
- `CommentariesUseCase`:
  - Paging des commentaires (`LineCommentsPagingSource`) et targum/liens (`LineTargumPagingSource`),
  - liste des commentateurs/sources disponibles pour une ligne,
  - sélection « sticky »: par ligne ET mémorisation par livre; réappliquée quand la ligne courante change
    (avec limite de 4 commentateurs).

### UI (Compose)
- `BookContentScreen` collecte `uiState` et le transmet à `BookContentView`.
- `BookContentView` compose trois split panes imbriqués: Navigation | TOC | Contenu. Les mouvements de séparateurs déclenchent `SaveState` (debounce).
- Panneaux:
  - Navigation: `CategoryTreePanel` + `CategoryBookTreeView` (liste plate hiérarchisée), scroll persisté.
  - TOC: `BookTocPanel` + `BookTocView` (liste visible aplatie, expansion/collapse), scroll persisté.
  - Contenu: `BookContentPanel` (contenu principal) + split vertical optionnel pour Targum; panneau Commentaires optionnel.
  - Vues ligne/Commentaires/Targum: pagination, sélections, texte avec police hébraïque, animations taille/hauteur de ligne.

### Intégration onglets + DI
- `TabsNavHost` passe `tabId`, `bookId`, `lineId` à `SavedStateHandle` de la destination.
- `AppGraph` (Metro DI) fournit `TabStateManager`, `TabsViewModel`, `SeforimRepository`, `BookContentViewModel`, etc.

## Flux de données

- Données: via `SeforimRepository` (catégories, livres, lignes, TOC, commentaires/links). Le ViewModel expose aux vues via `uiState` et `Providers`.
- Paging:
  - Lignes: `Flow<PagingData<Line>>` mis en cache (`cachedIn(viewModelScope)`).
  - Commentaires / Targum: Pagers dépendant de la ligne et des filtres sélectionnés.
- Scroll/Anchor:
  - Sauvegarde d’un anchor (première ligne visible) + index/offset. Restauration prioritaire par anchor, fallback index/offset.
  - Debounce des enregistrements de scroll via `snapshotFlow().debounce()`.

## Événements (cheat sheet)

- Navigation: `SearchTextChanged`, `CategorySelected`, `BookSelected`, `BookSelectedInNewTab`, `ToggleBookTree`, `BookTreeScrolled`
- TOC: `TocEntryExpanded`, `ToggleToc`, `TocScrolled`
- Contenu: `LineSelected`, `LoadAndSelectLine`, `NavigateToPreviousLine`, `NavigateToNextLine`, `ContentScrolled`, `ParagraphScrolled`, `ChapterScrolled`, `ChapterSelected`, `ToggleCommentaries`, `ToggleTargum`, `OpenCommentaryTarget`
- Commentaires/Targum: `CommentariesTabSelected`, `CommentariesScrolled`, `CommentatorsListScrolled`, `CommentaryColumnScrolled`, `SelectedCommentatorsChanged`, `SelectedTargumSourcesChanged`
- Persistance: `SaveState`

## Règles et conventions propres à « bookcontent »

- Source de vérité unique: toute mutation passe par `BookContentStateManager` (éviter la mutation directe depuis la UI).
- Clés d’état centralisées: toute nouvelle donnée persistée doit avoir une clé dans `StateKeys` + être gérée dans `loadInitialState()` et `saveAllStates()`.
- Providers dans l’état: passer les fonctions/flows nécessaires à la UI via `Providers` (éviter d’appeler le ViewModel depuis des vues profondes).
- Split panes: utiliser `EnhancedHorizontalSplitPane` / `EnhancedVerticalSplitPane` et conserver/restaurer les positions via `SplitDefaults` et `previousPositions`.
- Défilement: enregistrer avec debounce, restaurer seulement quand le contenu est prêt (items réellement présents).
- Sélections « sticky »: commentateurs/sources mémorisées par livre et re-projetées sur la ligne courante.

## Bonnes pratiques (du repo et respectées ici)

- Pas de texte user-visible en dur dans le code: utiliser `stringResource(Res.string.xxx)` et ajouter la clé en hébreu dans `strings.xml`.
- Nommage: Composables en PascalCase suffixés `View`/`Panel`, ViewModel/UseCase en PascalCase, fonctions/propriétés en `camelCase`.
- Partage logique: mettre la logique réutilisable en `commonMain` si possible; la UI Desktop reste en `jvmMain`.
- Tests: petits tests ciblés (UseCases), dans `SeforimApp/src/jvmTest/kotlin/...`. Exemple scaffold ci-dessous.

## Utilisation (pour démarrer)

```bash
# Build
./gradlew build

# Lancer desktop
./gradlew :SeforimApp:run

# Hot reload desktop
./gradlew :SeforimApp:hotRunJvm
```

- Ouvrir un livre: clic sur une catégorie pour l’étendre, puis sur un livre.
- TOC: s’ouvre automatiquement au premier livre si caché; clic sur une entrée charge la ligne ciblée.
- Navigation clavier: flèche haut/bas pour ligne précédente/suivante.
- Commentaires/Targum: toggles dans la barre latérale droite. Sélectionner 1–4 commentateurs/sources. Ctrl/Cmd + clic sur un commentaire pour ouvrir sa cible dans un nouvel onglet.
- Zoom: boutons +/− à droite (limites via `AppSettings`).

## Contribuer (ajouter une fonctionnalité)

1) État
- Ajouter les champs dans `BookContentState`.
- Si persistant: ajouter une clé dans `StateKeys`, puis charger/sauver dans `BookContentStateManager.loadInitialState()` et `saveAllStates()`.

2) Événements
- Déclarer dans `BookContentEvents`.
- Gérer dans `BookContentViewModel.onEvent()`.
- Implémenter la logique métier dans un UseCase existant ou nouveau.

3) UI
- Déclencher l’événement depuis la vue (`onEvent`).
- Pour un nouveau panneau: intégrer via `Enhanced*SplitPane`, respecter `SplitDefaults`.
- Toute chaîne UI via `Res.string.*` (hebreu) dans `strings.xml`.

4) DI / Navigation
- Ajouter les dépendances nécessaires dans `AppGraph` (`@Provides`).
- Si nouvelle destination d’onglet: ajouter une `TabsDestination` et son composable dans `TabsNavHost`.

5) Tests
- Cibler la logique pure des UseCases (input → état). Exemple scaffold:

```kotlin
// SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/SampleTest.kt
import kotlin.test.Test
import kotlin.test.assertTrue
class SampleTest {
  @Test fun runs() { assertTrue(true) }
}
```

## Exemples fréquents

- Ouvrir un livre dans un nouvel onglet: l’UI envoie `BookContentEvent.BookSelectedInNewTab`; le ViewModel copie l’état de navigation vers un nouveau `tabId`, puis `tabsViewModel.openTab(TabsDestination.BookContent(...))`.
- Sauvegarde de position de split: tout déplacement visible déclenche `BookContentEvent.SaveState` (debounce) et `BookContentStateManager.saveAllStates()` enregistre `positionPercentage` des splits.

## Points d’attention

- Restauration de scroll: attendre la fin du `refresh` Paging avant de scroller (sinon Compose clamp à 0).
- Persistance légère: privilégier des IDs/positions plutôt que des caches lourds (le `SessionManager` filtre déjà ce qui n’est pas essentiel).
- Limite des commentateurs: max 4; afficher l’avertissement et ne pas dépasser.
- `uiState.providers` peut être `null` très tôt: protéger les vues et retourner tôt si nécessaire.

---

Besoin d’aide pour ajouter un nouvel événement de bout en bout (événement → UseCase → UI) ou pour écrire un test sur un UseCase précis (ex: restauration anchor/scroll) ? Ouvrez une issue/PR et mentionnez ce guide.
