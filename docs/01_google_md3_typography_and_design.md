# 📐 Spécification du Design Système : Google & Material Design 3 Expressive

> **Document :** Spécification Technique de Référence  
> **Composants cibles :** Jetpack Compose Material 3 `1.3.x` / `1.4.x`  
> **Dernière révision :** 22 Septembre 2026  
> **Statut :** Approuvé & Actif

---

## 1. Vue d'Ensemble

Cette spécification définit l'implémentation formelle du design système **Material Design 3 Expressive (M3E)** pour l'application **Materialy Music**. Elle s'articule autour de quatre principes directeurs :
1. **Harmonie de Couleur HCT :** Les teintes sont dérivées de l'espace HCT (Hue, Chroma, Tone) pour assurer un contraste perceptuel constant et certifié conforme aux normes d'accessibilité WCAG AA/AAA.
2. **Typographie Hiérarchisée & Émotive :** Utilisation complète des 15 rôles canoniques de la grille typographique Material 3, complétés par les graisses *Emphasized* pour souligner le tempo et l'importance des éléments d'interface.
3. **Morphologie Tactile & Organique :** Abandon des angles rigides au profit de rayons généreux (16 à 28 dp) et de pilules (Pill Shapes), combinés à un retour haptique et élastique (`bouncy`).
4. **Physique Cinétique des Ressorts :** Animation basée sur les équations de dynamique des ressorts (`SpringSpec`), garantissant une transition fluide et interruptible lors des gestes de l'utilisateur.

---

## 2. Grille Typographique Formelle (Type Scale)

Tous les textes affichés au sein de l'application doivent consommer les styles de `MaterialTheme.typography`. L'injection de valeurs arbitraires en points ou sp (ex: `13.sp`, `17.sp`) est formellement proscrite.

| Rôle Typographique | Taille (`sp`) | Hauteur de ligne (`sp`) | Espacement (`sp`) | Graisse (Weight) | Usage Spécifique dans l'Application |
|---|---|---|---|---|---|
| **`displayLarge`** | 57 | 64 | -0.25 | Regular / Medium | Grands chiffres d'horloge, compteurs de morceaux |
| **`displayMedium`** | 45 | 52 | 0.00 | Regular / SemiBold | Titres de bienvenue sur l'écran d'accueil |
| **`displaySmall`** | 36 | 44 | 0.00 | Bold (Emphasized) | Titre de la bannière héroïque « Моя Волна » |
| **`headlineLarge`** | 32 | 40 | 0.00 | SemiBold | Grands titres d'écrans (Médiathèque, Paramètres) |
| **`headlineMedium`** | 28 | 36 | 0.00 | Bold (Emphasized) | Titre du morceau actif en cours de lecture |
| **`headlineSmall`** | 24 | 32 | 0.00 | SemiBold | Titres des sections (« Nouveautés », « Atmosphère ») |
| **`titleLarge`** | 22 | 28 | 0.00 | SemiBold | Titres des grandes cartes, nom de l'artiste (Player) |
| **`titleMedium`** | 16 | 24 | +0.15 | Medium / SemiBold | Titres des morceaux dans les listes et files d'attente |
| **`titleSmall`** | 14 | 20 | +0.10 | SemiBold (Emphasized) | Noms d'albums dans les listes compactes, badges |
| **`bodyLarge`** | 16 | 24 | +0.50 | Regular | Paroles karaoké inactives, descriptions d'albums |
| **`bodyMedium`** | 14 | 20 | +0.25 | Regular | Nom de l'artiste secondaire dans les rangées de morceaux |
| **`bodySmall`** | 12 | 16 | +0.40 | Regular | Métadonnées de format (FLAC, MP3 320k, durée) |
| **`labelLarge`** | 14 | 20 | +0.10 | SemiBold | Libellé des boutons principaux (Play, Mettre à jour) |
| **`labelMedium`** | 12 | 16 | +0.50 | SemiBold | Texte des puces de filtrage (Vibes de « Моя Волна ») |
| **`labelSmall`** | 11 | 16 | +0.50 | SemiBold | Timecodes de lecture (ex: `01:24`, `03:45`), tags |

---

## 3. Système des Couleurs & Conteneurs de Surface

L'application élimine les teintes d'élévation grises ou artificielles (`surfaceTint`). La stratification visuelle en mode sombre comme en mode clair repose sur les cinq paliers `SurfaceContainer` :

```
[Fond de l'application / Canvas]  -> surfaceContainerLowest  (Tone 4 en mode sombre)
  └── [Arrière-plan des listes]    -> surfaceContainerLow     (Tone 10 en mode sombre)
        └── [Cartes & Navbar]      -> surfaceContainer        (Tone 12 en mode sombre)
              └── [Dock & Modales] -> surfaceContainerHigh   (Tone 17 en mode sombre)
                    └── [Inputs]   -> surfaceContainerHighest (Tone 22 en mode sombre)
```

### 3.1. Rôles de Teinte & Accents
- **`primary` / `onPrimary` :** Couleur phare haute visibilité utilisée pour les boutons majeurs (FAB, bouton lecture principal, barre d'avancement du morceau).
- **`primaryContainer` / `onPrimaryContainer` :** Fond enrichi à luminance modérée utilisé pour les indicateurs d'onglets sélectionnés dans la barre de navigation et les puces d'ambiance actives.
- **`secondary` / `secondaryContainer` :** Teinte complémentaire harmonisée pour les contrôles secondaires (Shuffle, Repeat, boutons d'action rapide).
- **`outline` / `outlineVariant` :** Lignes de séparation ultra-fines (1 dp) avec opacité réduite (`0.25f` à `0.35f`) pour structurer sans encombrer.

---

## 4. Échelle Géométrique des Formes (Shapes)

L'échelle morphologique de Material 3 Expressive attribue des formes géométriques signifiantes selon l'importance du composant :

```kotlin
val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),   // Badges compacts, indicateurs d'état
    small      = RoundedCornerShape(12.dp),  // Vignettes de pochettes 52x52 dp, champs
    medium     = RoundedCornerShape(16.dp),  // Cartes moyennes, conteneurs d'effets sonores
    large      = RoundedCornerShape(20.dp),  // MiniPlayer flottant, dialogue système
    extraLarge = RoundedCornerShape(28.dp)   // Grandes cartes albums, BottomSheets modales
)
```

- **Pill Shape (`RoundedCornerShape(percent = 50)`) :** Utilisé systématiquement pour les puces de filtrage, les boutons flottants étendus et les indicateurs d'état actifs.
- **Micro-interaction `bouncy` :** Déformation physique élastique ($0.94\times$) lors du toucher avec restitution instantanée via amorti de ressort.

---

## 5. Spécifications Temporelles et Dynamiques du Mouvement

| Type de Mouvement | Damping Ratio | Stiffness | Application |
|---|---|---|---|
| **Spring Snappy** | `0.80f` | `StiffnessMedium` | Coche des filtres, bascule d'égaliseur, cœur favori |
| **Spring Spatial** | `0.85f` | `StiffnessLow` | Déploiement du Full Player depuis le MiniPlayer |
| **Spring Elastic** | `0.75f` | `StiffnessMediumLow` | Appui sur les boutons d'action, pulsation de la sphère |
| **Transition Linaire** | `1.00f` | `StiffnessVeryLow` | Glissement du slider de progression audio |

---

## 6. Accessibilité & Ergonomie Tactile

1. **Taille de Cible Minimale :** Tous les composants interactifs respectent une zone cliquable minimale de **48 × 48 dp**, même si leur élément visuel interne est plus petit (ex: icône 24 dp au sein d'une cible de 48 dp).
2. **Contraste de Texte Élevé :** Tout texte de premier plan possède un contraste supérieur ou égal à **4.5:1** par rapport à son conteneur direct (garanti par le calcul de luminance HCT).
3. **Gestion du Défilement et des Marges :** L'empilement du MiniPlayer et de la barre de navigation impose un dégagement de fond (`contentPadding`) de minimum **140 dp** pour permettre le défilement complet des listes.
