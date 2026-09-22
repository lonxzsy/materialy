# 🎧 Guide d'Architecture UX & Interaction Audio : Style Google & Material Design 3

> **Document :** Spécification d'Expérience Utilisateur & Interaction  
> **Composants cibles :** Navigation Compose, Media3 ExoPlayer, Gestes & Rendu Réactif  
> **Dernière révision :** 22 Septembre 2026  
> **Statut :** Approuvé & Actif

---

## 1. Philosophie d'Interaction Centrée sur la Musique

L'architecture UX de **Materialy Music** est conçue pour éliminer toute friction entre l'intention de l'auditeur et la restitution sonore. Inspirée des meilleures pratiques de Google (YouTube Music, Pixel Recorder) et des services pionniers de flux continu (Yandex Music « Моя Волна »), l'application garantit :
- **Continuité Absolue :** Le son ne s'interrompt jamais brutalement ; les transitions de pistes, les mises en pause et les reprises sont adoucies par un moteur de fondu matériel (*Smooth Audio Engine*).
- **Visibilité Permanente de la Lecture :** Le MiniPlayer flottant reste accessible sur l'ensemble des écrans sans jamais masquer le contenu sous-jacent.
- **Retour Haptique & Élastique Immédiat :** Chaque commande physique procure une sensation tactile organique grâce aux animations de ressort et aux déformations proportionnelles.

---

## 2. Architecture de l'Information & Navigation Principale

L'application s'organise autour d'une barre de navigation inférieure à **4 onglets majeurs**, garantissant une découverte équilibrée entre exploration guidée et gestion de médiathèque locale :

```
┌────────────────────────────────────────────────────────────────────────┐
│                        ÉCRAN ACTIF DU CONTENU                          │
│                                                                        │
│  [ Onglet 1 : Accueil ] [ Onglet 2 : Моя Волна ] [ 3 : Recherche ] [ 4 : Médiathèque ]
├────────────────────────────────────────────────────────────────────────┤
│ 🎵 MiniPlayer Flottant (Pochette + Titre/Artiste + Play/Pause + Next)  │
├────────────────────────────────────────────────────────────────────────┤
│ 🧭 NavigationBar Dock (4 Onglets avec Indicateurs en Pilule 64x32 dp)  │
└────────────────────────────────────────────────────────────────────────┘
```

### 2.1. Description des 4 Piliers de Navigation
1. **Accueil (Home / Discover) :**
   - Bannière héroïque interactive de **« Моя Волна »** avec accès instantané en un clic.
   - Puces horizontales de filtrage d'énergie (*« Tout »*, *« Énergie »*, *« Relaxation »*, *« En route »*).
   - Sections éditoriales de découverte (*« Nouvelles découvertes »*, *« Atmosphère et détente »*).
   - Accès direct aux Paramètres, Téléchargements et Actualisation via l'en-tête supérieur.
2. **Моя Волна (Signature Experience) :**
   - Écran immersif dédié au flux infini génératif et personnalisé.
   - Sphère fluide cosmique réactive aux basses en temps réel.
   - Sélecteur de vibe en ruban de pilules (*« Mon Vibe »*, *« Énergique »*, *« Calme »*, *« Découvertes »*).
   - Contrôles de flux rapides (*« J'aime »* avec explosion de cœurs, *« Ignorer / Suivant »*).
3. **Recherche & Exploration (Search) :**
   - Champ de recherche expansif en pilule avec détection en temps réel.
   - Historique des recherches récentes sous forme de puces supprimables (*Input Chips*).
   - Fédération des sources locales et distantes avec catégorisation (*Titres*, *Artistes*, *Albums*, *Playlists*).
4. **Médiathèque Unifiée (Library) :**
   - Gestion des morceaux locaux sur le stockage de l'appareil et des pistes sauvegardées.
   - Création, édition et réorganisation dynamique de playlists.
   - Filtres rapides pour isoler les morceaux hors-ligne ou les favoris.

---

## 3. Le Dock de Contrôle Flottant (Player Dock)

Le couple constitué par le **MiniPlayer** et la **NavigationBar** forme une unité ergonomique indissociable :

### 3.1. Ergonomie du MiniPlayer
- **Position :** Flottant immédiatement au-dessus de la barre de navigation avec une marge de 8 dp.
- **Forme :** Coins arrondis de 20 dp, conteneur en `surfaceContainerHigh` avec bordure fine `outlineVariant`.
- **Indicateur de Progression :** Fine ligne de progression de 3 dp intégrée à la base du conteneur, animée au millième de seconde.
- **Comportement Tactile :**
  - Clic court : Ouvre le lecteur plein écran (*Full Player*) avec une transition d'expansion verticale douce.
  - Clic sur Play/Pause ou Suivant : Action immédiate sans ouvrir le plein écran.

### 3.2. Règle Critique des Marges de Défilement (Bottom Padding)
En raison de la hauteur combinée du MiniPlayer ($64\text{ dp}$) et de la barre de navigation ($80\text{ dp}$ plus les marges système), tout écran doté d'une liste déroulante (`LazyColumn`) doit impérativement définir :
```kotlin
contentPadding = PaddingValues(bottom = 140.dp) // ou 160.dp si actions flottantes
```
Cette règle garantit que les derniers éléments d'une liste ou les boutons situés au bas de l'écran restent entièrement visibles et accessibles sans être masqués par le dock.

---

## 4. Expérience du Lecteur Plein Écran (Full Player)

L'écran Now Playing s'ouvre par glissement vertical depuis le MiniPlayer :
1. **En-tête :** Bouton de fermeture flèche vers le bas, titre de la source / playlist, bouton d'options contextuelles.
2. **Pochette d'Album Flottante :**
   - Format carré généreux avec coins arrondis de 24 dp.
   - Ombre douce colorée dérivée des teintes dominantes de l'image.
   - Réduction d'échelle légère ($0.92\times$) lors de la mise en pause pour signifier l'arrêt de l'énergie cinétique.
3. **Bloc Titre & Artiste :**
   - Titre du morceau en `headlineMedium` (28 sp, Bold).
   - Nom de l'artiste en `titleMedium` avec lien cliquable vers sa discographie.
   - Bouton cœur favori interactif à pulsation élastique (*Bouncy Heart*).
4. **Curseur de Progression Expressif :**
   - Ligne active épaisse de 6 dp avec bords arrondis.
   - Tête de lecture dynamique qui grandit lors du contact du doigt.
   - Affichage des timecodes écoulé / total en `labelSmall`.
5. **Commandes de Lecture Centrales :**
   - Bouton central Play/Pause géant ($72\times72\text{ dp}$) en conteneur `primary`.
   - Boutons Précédent et Suivant avec saut de piste instantané.
   - Contrôles de lecture aléatoire (*Shuffle*) et de répétition (*Repeat*) en boutons tonaux.
6. **Panneau des Paroles (Synced Lyrics) :**
   - Défilement automatique synchronisé sur les timecodes du fichier LRC.
   - Ligne active mise en avant en typographie `titleLarge` avec accent de couleur vive.

---

## 5. Moteur Sonore & Transitions Fluides (Smooth Audio Engine)

L'expérience auditive se conforme aux standards des applications audio professionnelles :

### 5.1. Gestion des Fondues Enchaînées (Fade & Crossfade)
- **Fade-in au Démarrage :** La musique commence à volume zéro et atteint son niveau nominal en $500\text{ ms}$ selon une courbe logarithmique, éliminant les chocs acoustiques.
- **Fade-out à la Pause :** Le son s'atténue délicatement en $400\text{ ms}$ avant d'arrêter le moteur de lecture.
- **Crossfade Inter-Pistes :** Les 3 dernières secondes d'un morceau se fondent avec l'introduction du morceau suivant pour un flux musical ininterrompu.

### 5.2. Égaliseur Matériel & Effets Spatiaux
- **Égaliseur 5 Bandes :** Réglage précis des fréquences (60 Hz, 230 Hz, 910 Hz, 3600 Hz, 14000 Hz) avec mémorisation de préselections personnalisées.
- **Bass Boost & Virtualizer :** Renforcement dynamique des basses fréquences et élargissement spatial de l'image stéréo.

---

## 6. Système de Mise à Jour Intégré (In-App Auto-Updater)

L'expérience de mise à jour s'intègre naturellement dans l'interface sans perturber l'utilisateur :
- **Vérification Discrète en Arrière-Plan :** Au lancement de l'application, une requête légère vers le manifeste statique `version.json` vérifie la disponibilité d'une nouvelle version.
- **Vérification Manuelle dans les Paramètres :** Un bouton dédié permet à l'utilisateur de forcer la vérification et d'afficher le dialogue d'état.
- **Dialogue Expressive M3 :**
  - Présentation claire des notes de version (*Release Notes*).
  - Téléchargement avec barre de progression continue et pourcentage en temps réel.
  - Déclenchement sécurisé du gestionnaire d'installation Android via `FileProvider`.

---
*Ce document de conception UX constitue la base de travail pour toutes les interactions sonores et visuelles de Materialy Music.*
