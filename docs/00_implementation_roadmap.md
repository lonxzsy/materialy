# 🗺️ Materialy Music — Feuille de Route & Statut d'Implémentation

> **Document :** Feuille de Route d'Ingénierie & Suivi des Jalons  
> **Dernière révision :** 22 Septembre 2026  
> **Statut :** Document Actif de Suivi

---

## 1. Vue d'Ensemble & Jalons du Projet

Le projet **Materialy Music** a franchi avec succès les étapes fondamentales de son architecture : lecteur audio local et distant (Media3/ExoPlayer), interface **Material 3 Expressive**, flux de recommandation génératif **« Моя Волна »**, moteur sonore avec égaliseur et fondu (*Fade/Crossfade*), et système de distribution d'autorégénération et mises à jour en ligne (*GitHub Actions + Manifeste JSON*).

| Jalon | Périmètre Fonctionnel | Dépendances | Critères d'Acceptation | Statut |
|---|---|---|---|---|
| **Jalon 1** | Identifiants Déterministes `ContentId`, Coordinateur Backend, Base Room & Migrations | — | Aucun crash de migration Room 1→2, démarrage silencieux de l'extracteur embarqué | **Complété ✅** |
| **Jalon 2** | Media3 ExoPlayer, Queue de lecture, MiniPlayer persistant, Contrôles audio | Jalon 1 | Continuité sonore en arrière-plan, synchronisation temps réel des états de lecture | **Complété ✅** |
| **Jalon 3** | Interface Material 3 Expressive, Écran d'accueil, Recherche fédérée, Médiathèque | Jalon 2 | Respect des 15 rôles de typographie, conteneurs `SurfaceContainer`, retour élastique `bouncy` | **Complété ✅** |
| **Jalon 4** | Flux Signature « Моя Волна », Sphère Réactive aux Basses, Sélecteur de Vibes | Jalon 3 | Rendu 60/120fps sur Canvas, modulation instantanée par `bassEnergy`, recomposition isolée | **Complété ✅** |
| **Jalon 5** | Amélioration Audio : Égaliseur 5 bandes, Bass Boost, Fondu Fade-In/Out & Crossfade | Jalon 2 | Transitions sans clic sonore, persistance des préselections personnalisées d'égaliseur | **Complété ✅** |
| **Jalon 6** | Système de Mises à Jour en Ligne (GitHub Releases, Manifeste statique, FileProvider) | Jalon 3 | Détection silencieuse au démarrage, jauge de progression, installation sécurisée APK | **Complété ✅** |
| **Jalon 7** | Gestion des Téléchargements avancés & Mode Hors-Ligne strict | Jalon 4-5 | Mise en cache locale complète des flux audio et des métadonnées associées | *En cours ⏳* |
| **Jalon 8** | Optimisation Macrobenchmark, Profils Baseline, Tests d'Accessibilité TalkBack | Jalon 6-7 | Zéro régression de démarrage (`startup < 800ms`), score d'accessibilité 100% | *Planifié 📌* |

---

## 2. État Réalisé du Socle Technique (Baseline Implémentée)

1. **Expérience Visuelle Material 3 Expressive :**
   - Implémentation du dock flottant combiné (`PlayerDock`) avec MiniPlayer aux angles de 20 dp et barre de navigation 80 dp aux angles supérieurs de 24 dp.
   - Système de couleurs tonales HCT et extraction dynamique depuis la pochette du morceau via `DynamicThemeManager` sans contamination de la navigation globale.
   - Micro-animations tactiles avec le modificateur `Modifier.bouncy()`.
   - Ajustement strict du dégagement inférieur (`contentPadding = PaddingValues(bottom = 140.dp)`) pour un défilement fluide sans masquage.

2. **Flux Génératif « Моя Волна » :**
   - Écran dédié `MyWaveScreen` avec canevas procédural dessinant la sphère fluide déformée par les harmoniques trigonométriques et l'énergie des basses fréquences.
   - Bannière héroïque sur la page d'accueil avec amorce rapide de lecture.
   - Sélecteur de modes d'énergie et de vibes (*« Mon Vibe »*, *« Énergique »*, *« Calme »*, *« Découvertes »*).

3. **Moteur Sonore Pro & Traitement du Signal :**
   - Égaliseur matériel 5 bandes avec réglage en décibels (`AudioEffectsManager`).
   - Renforcement des basses fréquences (*Bass Boost*) et spatialisation stéréo (*Virtualizer*).
   - Gestionnaire de fondu doux (*Smooth Audio Engine*) avec montées en puissance progressives (*Fade-in*), extinctions silencieuses (*Fade-out*) et fondu enchaîné (*Crossfade*).

4. **Système de Mise à Jour Automatique :**
   - `AppUpdateManager` avec flux asynchrone `OkHttpClient` et notification de progression en continu.
   - Déclencheur d'installation système `ACTION_VIEW` compatible Android 8.0 à 15 avec gestion de `REQUEST_INSTALL_PACKAGES` et `FileProvider`.
   - Workflow GitHub Actions prêt à l'emploi (`.github/workflows/release.yml`) sur push de tags `v*`.

---

## 3. Chantiers & Orientations Futures

1. **Paroles Synchronisées Approfondies (Karaoke View) :**
   - Enrichissement du rendu des paroles synchronisées avec effet de vague lumineuse sur la syllabe active.
2. **Support Hors-Ligne Intégral :**
   - Stockage local chiffré des flux téléchargés avec lecture hors connexion garantie à 100%.
3. **Mise à Niveau Toolchain Adaptive :**
   - Transition prévue vers compileSdk 37 / AGP 9.x lorsque la chaîne d'outils sera stabilisée pour activer `Material 3 Adaptive 1.3.0`.
