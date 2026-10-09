# APK autonome avec NFC Android

Le projet inclut maintenant un module NFC local et la page autonome mise à jour. Aucun plugin NFC payant ni compte supplémentaire n'est nécessaire. Le module utilise les fonctions Android de lecture et d'écriture NDEF.

## Utilisation

Après l'enregistrement d'un flacon, choisir « Écrire sur le tag NFC », puis maintenir le tag contre la zone NFC du téléphone jusqu'au message de réussite. Le tag contient le nom du produit, son code CHEM unique et son stockage. L'étiquette imprimée du produit ne contient plus le stockage.

La fiche produit propose « Écrire / mettre à jour le tag NFC ». Après un déplacement, utiliser ce bouton pour réécrire le stockage. Le tag reste réinscriptible. Un appareil sans NFC peut toujours utiliser l'inventaire et les QR codes.

Les données sont limitées à 488 octets NDEF pour conserver une marge sur un NTAG215. Les noms longs sont comptés en octets UTF-8 : aucune donnée n'est tronquée. Un tag verrouillé ou trop petit donne un message d'erreur. L'écriture est annulée quand l'application passe à l'arrière-plan. Les tags déjà formatés sont relus immédiatement pour contrôler l'écriture ; un tag vierge formatable est initialisé en NDEF, sans verrouillage.

## Construire avec GitHub Actions

1. Décompresser le projet et remplacer les fichiers du dépôt **y compris** `.github/workflows/build-standalone-apk.yml`, `package.json`, `package-lock.json`, `capacitor.config.json`, `native-android/`, `scripts/`, `tests/` et la page HTML.
2. Ouvrir Actions → **Build Standalone APK (NFC Android, sans backend)** → Run workflow.
3. Récupérer `app-debug.apk` dans l'artefact `ChemicalStockManager-Standalone-NFC-APK`.

L'ancien workflow « Extraire le zip du projet » conserve les anciens fichiers `.github`. Pour ce cas, le projet comprend aussi un hook `capacitor:sync:before` qui installe le module local lors de `npx cap sync android` : le NFC est donc intégré même si l'ancien workflow de compilation est conservé. Le nouveau workflow reste recommandé pour lancer également les tests Android et utiliser les dépendances figées.

L'identifiant Android reste `com.chemicalstockmanager.standalone`. Pour mettre à jour une installation existante sans perdre ses données, l'APK doit être signé avec la même clé. Une compilation debug réalisée ailleurs peut avoir une autre signature. Faire une sauvegarde JSON complète depuis l'application avant toute installation ou désinstallation.

## Construire localement

Prérequis : Node 24, JDK 21 et SDK Android 35.

```sh
npm ci
npm test
npm run prepare:web
npx cap add android
npm run android:prepare
npx cap sync android
cd android
./gradlew testDebugUnitTest assembleDebug
```

Le module `Nfc` est enregistré dans MainActivity avant la création du bridge Capacitor. Il fournit `isSupported`, `isEnabled`, `startScanSession`, `stopScanSession`, `write`, `nfcTagScanned` et `scanSessionError`, utilisés par la page HTML. Les opérations sur le tag s'exécutent hors du thread de l'interface. Les fichiers d'inventaire et les sauvegardes conservent leur fonctionnement existant.

Une compilation et des tests logiciels ne remplacent pas l'essai final avec un téléphone NFC et un vrai NTAG215.
