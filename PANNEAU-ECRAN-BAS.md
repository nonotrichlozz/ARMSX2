# ARMSX2 GT3 : panneau de l'écran du bas

Ce fork d'ARMSX2 modifie le panneau qui s'affiche sur le second écran (Retroid Pocket Duo, Ayn Thor…). Il n'est pas maintenu par l'équipe d'ARMSX2.

## Ce qui change

- **Cases placées librement.** Chaque case reste dans la grille (6 × 6) à l'endroit où vous la posez, sans trou ni décalage.
- **Éditeur sur l'écran du bas.** On peut y déplacer, agrandir, retirer ou ajouter des cases au doigt.
- **Disposition par jeu**, avec l'option « Même disposition pour tous les jeux ».
- **Jaquette** aux proportions de l'image, sans cadre ni texte. Le titre est dans la case « Jeu ».
- **Case « Résolution »** : passe de x2 à x3, et inversement, en direct.
- La carte GT3 des versions précédentes a été retirée.

## Installer

1. Ouvrez le dernier build réussi de *Android APK (GT3 map)* dans l'onglet **Actions** du dépôt.
2. Téléchargez l'artefact `armsx2-gt3map-apk`.
3. Installez `app-github-release.apk`.

Les builds sont signés avec la même clé (secret `GT3_DEBUG_KEYSTORE_B64`). Une nouvelle version s'installe donc par-dessus l'ancienne sans rien perdre.

## Activer le panneau

☰ → **Paramètres** → **Appli** → activer **Panneau écran secondaire**.

## Modifier la disposition (écran du bas)

1. Ouvrez le mode édition :
   - touchez le crayon **✎** en haut à droite de l'écran du bas ;
   - ou appuyez longuement sur le fond du panneau, ou sur une case d'information (FPS, Jeu, Jaquette…) ;
   - ou, depuis l'écran du haut, ☰ → Paramètres → Appli → **Modifier la disposition sur l'écran du bas**.
2. Modifiez les cases :
   - **glisser** une case la déplace ;
   - **glisser son coin ◢** (en bas à droite) l'agrandit ou la réduit ;
   - toucher **✕** (en haut à gauche) la retire.

   Les cases s'aimantent à la grille. Un déplacement qui recouvrirait une autre case est refusé : la case revient à sa place et clignote en rouge.
3. Les boutons du haut :
   - **+ Ajouter** ajoute une case depuis la liste ;
   - **Réinitialiser** remet la disposition par défaut (touchez deux fois : « Confirmer ? ») ;
   - **Terminé** quitte le mode édition. Chaque modification est enregistrée dès qu'elle est faite.

Le réglage *Même disposition pour tous les jeux* est en haut de l'éditeur et dans Paramètres → Appli.
- Désactivé :
  - pendant une partie, vous modifiez la disposition de ce jeu ;
  - depuis la bibliothèque, vous modifiez celle des jeux qui n'en ont pas de propre.
- Activé : une seule disposition pour tous. Si vous l'activez pendant une partie, la disposition de ce jeu devient celle de tous.

Dans Paramètres → Appli → **Disposition du panneau**, cocher ou décocher une case l'ajoute ou la retire tout de suite sur l'écran du bas. Une case cochée prend la première place libre.

**Disposition par défaut :**
- en haut : Pause / Reprendre (grande), Sauvegarder, Charger ;
- dessous : FPS, FRAME, Photo, Résolution ;
- à droite : la jaquette, avec le titre du jeu en dessous.

## Case Résolution

Chaque appui passe de x2 à x3, et inversement. La case affiche la valeur actuelle. Elle ignore un second appui dans les 1,5 seconde. Si le changement échoue, elle remet l'ancienne valeur et affiche « Échec ».

- Par défaut, la valeur ne vaut que jusqu'à l'arrêt du jeu.
- Pour la garder : Paramètres → Appli → **Case Résolution : mémoriser pour ce jeu**. Elle est alors enregistrée comme réglage propre au jeu.

## Macros

Les cases Macro 1 à 4 ne sont pas affichées par défaut. Une macro sans boutons apparaît grisée et ne fait rien.

1. ☰ → **Paramètres** → **Commandes** → section **Macros** : touchez pour l'ouvrir.
2. Touchez la ligne **Macro 1**. Elle indique « Non défini » tant qu'elle est vide.
3. Touchez les boutons que la macro doit presser ensemble, puis **Enregistrer**.

Ensuite, ajoutez la case **Macro 1**, avec la puce dans Paramètres → Appli → Disposition du panneau ou avec **+ Ajouter** dans l'éditeur.

## Pour les développeurs

- Code ajouté : `platforms/android/app/src/main/java/com/armsx2/SecondScreenGrid.kt`. Il contient le modèle de disposition (`SecondScreenLayout`) et la vue grille avec l'éditeur (`PanelGrid`).
- Modifiés :
  - `SecondScreen.kt` : construction du panneau, jaquette, case Résolution ;
  - `SecondScreenTiles.kt` : case `RESOLUTION` ; l'ancienne liste ordonnée est retirée ;
  - `ui/settings/AppTab.kt` : réglages.
- Préférences : `secondScreen.grid` (disposition commune), `secondScreen.grid.game.<série>` (disposition par jeu), `secondScreen.grid.shared`, `secondScreen.resolution.remember`.
- Build : `.github/workflows/android-gt3.yml`, déclenché par un push sur `feature/gt3-map`.

## Licence

ARMSX2 est distribué sous GPL-3.0, et ce fork aussi. Les mentions de licence d'origine sont conservées. Aucun jeu ni BIOS n'est fourni.
