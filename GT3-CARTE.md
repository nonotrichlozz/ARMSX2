# GT3 : carte sur le 2ᵉ écran (Retroid Pocket Duo)

Ajout à ARMSX2 (licence GPL-3.0, comme le projet d'origine). Pendant une course de
**Gran Turismo 3 A-Spec (PAL, SCES-50294)**, l'écran du bas affiche la mini-carte du jeu en grand.

## Installation
1. Onglet **Actions** du dépôt → workflow **« Android APK (GT3 map) »** → dernière exécution réussie.
2. En bas de la page, section **Artifacts** : télécharger `armsx2-gt3map-apk` (un fichier .zip).
3. Dézipper, copier le `.apk` sur le Duo et l'ouvrir pour l'installer (autoriser les « sources inconnues »).

L'appli s'appelle **ARMSX2 GT3** et s'installe à côté d'un ARMSX2 normal sans le remplacer.
Elle a ses propres réglages : il faut lui indiquer le BIOS et les jeux comme la première fois.

## Réglages
Paramètres → **App** → activer **« 2ᵉ écran »**, puis en dessous :
- **Carte GT3 sur le 2ᵉ écran** : active la fonction (à la place du panneau de boutons).
- **Uniquement pour GT3** : la carte ne s'affiche que pour SCES-50294 ; les autres jeux gardent le panneau.
- **Carte lissée** : désactivé = pixels nets, activé = image adoucie.
- **Zone : gauche / droite / haut / bas** : le rectangle découpé, en % de l'image
  (par défaut 4 → 29 % en largeur, 18 → 35 % en hauteur).
  Pour régler : lancer une course, mettre en pause, ouvrir les paramètres et bouger les curseurs
  en regardant l'écran du bas.

## Limites
- La mini-carte du jeu est en basse résolution : agrandie, elle est floue ou pixelisée.
- Elle reste aussi affichée sur l'écran du haut.
- Les % comptent les bandes noires éventuelles : si vous changez le format d'image (4:3 / 16:9),
  il faut refaire le réglage de la zone.
- Dans les menus, le garage ou les replays, l'écran du bas montre ce qui se trouve à cet
  endroit de l'image (pas de plantage). Hors jeu, le panneau habituel revient.
- Sans 2ᵉ écran, rien ne change.

## Pour les développeurs
Code : `platforms/android/app/src/main/java/com/armsx2/gt3map/Gt3Map.kt`.
Branchements : quelques lignes marquées `GT3 map fork` dans `SecondScreen.kt` et `ui/settings/AppTab.kt`.
Copie de la zone par `PixelCopy` (~20 fois/s). Aucun fichier du jeu ni du BIOS n'est inclus.
