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
- **Zone : gauche / droite / haut / bas** : le rectangle découpé, en % de l'image
  (par défaut 4 → 29 % en largeur, 18 → 35 % en hauteur). Réglez en course : la case se met à jour
  en direct ; pendant le réglage, la détection est suspendue 3 secondes pour voir la zone brute.
- **Détection du tracé (‰ de blanc)** : la case n'affiche la carte que si la zone contient assez de
  pixels blancs (le contour du circuit). Sinon la case reste unie. 0 = toujours afficher.
- **Carte GT3 en plein écran** : ancien mode, la carte remplace tout le panneau. Désactivé par défaut.

## Limites
- La mini-carte du jeu est en basse résolution : agrandie, elle est floue ou pixelisée.
- Elle reste aussi affichée sur l'écran du haut.
- Les % comptent les bandes noires éventuelles : si vous changez le format d'image (4:3 / 16:9),
  il faut refaire le réglage de la zone.
- La détection est simple (pixels clairs) : un menu clair au même endroit peut la tromper ;
  ajustez le seuil si besoin.
- Sans 2ᵉ écran, rien ne change.

## Pour les développeurs
Code : `platforms/android/app/src/main/java/com/armsx2/gt3map/Gt3Map.kt`.
Branchements : quelques lignes marquées `GT3 map fork` dans `SecondScreenTiles.kt`,
`SecondScreen.kt` et `ui/settings/AppTab.kt`.
Copie de la zone par `PixelCopy` (~20 fois/s). Aucun fichier du jeu ni du BIOS n'est inclus.
