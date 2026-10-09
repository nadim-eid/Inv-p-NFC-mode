# Vérifications NFC Android — 9 octobre 2026

## Résultats logiciels

- Compilation `testDebugUnitTest assembleDebug` : **réussie**. APK 3.4.0 (code 34), Android minimum 6.0, cible Android 15. Signature vérifiée, module NFC présent dans le code compilé, page HTML embarquée identique à la source et équipement NFC facultatif dans le manifeste.
- Préparation et intégration Android (`npm test`) : **2 tests réussis**. Déclaration NFC, matériel facultatif, enregistrement du module avant le bridge, version, idempotence et syntaxe JavaScript.
- Module Android (`testDebugUnitTest`) : **9 tests NFC réussis**, aucune erreur. Texte accentué, aller-retour NDEF, limite exacte de 488 octets, dépassement, comptage UTF-8, rejet des enregistrements multiples ou non textuels et des octets invalides. Le test Android d’exemple passe également.
- Interface NFC de la page autonome : **21 vérifications réussies**, avec les ponts NFC natif et Web simulés. Proposition après ajout, payload nom/code CHEM/stockage, annulation, nettoyage, tag verrouillé ou trop petit, mise à jour du stockage et suppression du stockage sur l’étiquette produit.

## Essai sur appareil à effectuer

1. Sauvegarder intégralement les données en JSON avant installation.
2. Sur un Android doté du NFC activé, ajouter un flacon, accepter l’écriture et présenter un NTAG215 réinscriptible.
3. Lire le tag avec un lecteur NDEF et contrôler le nom, le code CHEM et le stockage.
4. Déplacer le flacon, puis utiliser « Écrire / mettre à jour le tag NFC » et vérifier le nouveau stockage.
5. Vérifier l’annulation, le retrait prématuré du tag, un tag verrouillé et un appareil sans NFC.

L’écriture physique et la compatibilité avec la tablette de l’utilisateur n’ont pas été testées ici. Le tag reste réinscriptible ; il n’est pas verrouillé par le module.

## Installation

L’APK fournie est une compilation debug. L’identifiant de l’application est conservé, mais une mise à jour directe exige la même clé de signature que l’ancienne APK. Si Android refuse la mise à jour pour signature différente, conserver la sauvegarde JSON avant toute désinstallation, puis restaurer les données après installation.
