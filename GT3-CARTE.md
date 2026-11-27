# GT3 : carte sur le 2ᵉ écran (Retroid Pocket Duo)

Ajout à ARMSX2 (licence GPL-3.0, comme le projet d'origine). Pendant une course de
**Gran Turismo 3 A-Spec (PAL, SCES-50294)**, le panneau de l'écran du bas affiche une case
**« Carte GT3 »** : la mini-carte du jeu, agrandie et mise à jour en direct.

## Installation
1. Onglet **Actions** du dépôt → workflow **« Android APK (GT3 map) »** → dernière exécution réussie.
2. En bas de la page, section **Artifacts** : télécharger `armsx2-gt3map-apk` (un fichier .zip).
3. Dézipper, copier le `.apk` sur le Duo et l'ouvrir pour l'installer (autoriser les « sources inconnues »).

L'appli s'appelle **ARMSX2 GT3** et s'installe à côté d'un ARMSX2 normal sans le remplacer.

## Activer
Accueil → **☰** → **Paramètres** → **Appli** → activer **Panneau écran secondaire**.
Disposition par défaut du panneau : **Carte GT3** en grand en haut, puis **Sauvegarder**, **Charger**
et **Photo**. Dans **Disposition du panneau**, chaque case s'active ou se retire, et
se déplace avec ▲ ▼.

## Réglages (sous « Réinitialiser la disposition », rubrique « Carte GT3 »)
- **Taille de la case Carte GT3** : Petite (1 colonne), Moyenne (2 colonnes), Grande (toute la largeur).
- **Uniquement pour GT3** : la case est masquée dans les autres jeux.
- **Carte GT3 lissée** : désactivé = pixels nets, activé = image adoucie.
- **Cadrage automatique** (activé par défaut) : la case trouve seule la carte dans le quart
  haut-gauche de l'image, grâce aux points jaunes et verts des voitures et au tracé, ajoute 8 % de
  marge et suit en douceur. Désactivé : curseurs **Zone : gauche / droite / haut / bas** (en % de
  l'image, par défaut 14 → 34 % et 17 → 35 %).
- **Carte GT3 sur fond noir** (activé par défaut) : seulement le tracé (gris clair) et les voitures
  sur du noir. Désactivé : l'image du jeu.
- **Carte GT3 en plein écran** : ancien mode, la carte remplace tout le panneau. Désactivé par défaut.

La case reste unie quand aucun point de voiture n'est visible (menus, garage, chargement).
La case **Jaquette** n'apparaît qu'une fois l'image chargée ; sinon la place va aux autres cases.

## Limites
- Le tracé est retrouvé parce qu'il reste immobile quand le décor défile : il apparaît après
  2 à 3 secondes de course. Voiture à l'arrêt, des traits du décor peuvent s'y ajouter.
- Au tout début, le cadrage se fait autour des voitures et peut couper une partie du circuit.
- La mini-carte du jeu est en basse résolution : agrandie, elle reste un peu grossière.
- Elle reste aussi affichée sur l'écran du haut.
- Sans 2ᵉ écran, rien ne change.

## Pour les développeurs
Code : `platforms/android/app/src/main/java/com/armsx2/gt3map/Gt3Map.kt`.
Branchements : quelques lignes marquées `GT3 map fork` dans `SecondScreenTiles.kt`,
`SecondScreen.kt` et `ui/settings/AppTab.kt`.
Copie par `PixelCopy` (~20 fois/s), analyse sur un fil séparé. Aucun fichier du jeu ni du BIOS n'est inclus.
