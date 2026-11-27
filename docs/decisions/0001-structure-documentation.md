# 0001. Structure de la documentation

- **Statut** : Proposé
- **Date** : 2026-11-27

## Contexte

La doc du projet tenait en trois fichiers en anglais : un `README.md` long (protocole, configuration, modèle de
données), un `SETUP.md` (intégrer une application) et un `AGENTS.md` (règles d'architecture pour les agents). Le
README était en partie périmé (il cite `config/application-dev.yml` et un `application.yml` dans
`src/main/resources`, qui n'existent plus). Rien n'indiquait la dette ni le pourquoi des choix.

kicker-app et hubscore, qui consomment ce serveur, ont adopté une doc par thème avec des sommaires progressifs, pensée
pour être chargée par morceaux par un agent IA. Un fichier unique et long consomme le contexte pour rien.

## Options envisagées

1. **Garder les trois fichiers** et les enrichir : simple, mais ils grossissent et doivent être lus en entier.
2. **Diátaxis** (tutorials / how-to / reference / explanation) : référence reconnue, trop lourde pour ce projet.
3. **Dossiers par thème avec sommaires progressifs**, comme kicker-app et hubscore.

## Décision

Nous retenons l'option 3, identique aux deux autres projets pour qu'on passe d'un dépôt à l'autre sans réapprendre :

- **Organisation** : `docs/` est la source de vérité, en français : `architecture` (ce qui est), `conventions` (ce qui
  doit être), `process` (comment faire), `decisions` (pourquoi), plus `getting-started.md`, `glossary.md` et
  `tech-debt.md`.
- **Sommaires** : un `README.md` par dossier, chaque entrée avec un lien, une description et une ligne « À lire si ».
- **Taille** : 200 lignes visées par fichier. Le hook du plugin `gouchon` signale le dépassement et l'agent propose un
  découpage ; les exceptions assumées vont dans `.claude/doc-length-ignore.txt`.
- **Point d'entrée IA** : `CLAUDE.md` importe `docs/README.md` et résume les règles non négociables.
- **Cible plutôt que constat** : les conventions décrivent la cible ; les écarts du code actuel sont listés dans
  `tech-debt.md` au lieu d'être documentés comme normes.
- **Fichiers d'origine** : les règles d'`AGENTS.md` passent dans les conventions et
  l'[ADR 0004](0004-organisation-par-feature.md), le contenu de `SETUP.md` dans `process/add-an-oauth-client.md`,
  celui du README dans `architecture/` ; les deux premiers sont supprimés, le README est réduit au démarrage rapide.

## Conséquences

- **Positives** : un agent ne charge que ce qui sert sa tâche ; les règles sont explicites ; la dette est visible et
  priorisée.
- **Coûts** :
    - la doc se met à jour dans la même PR que le code qui la contredit, en particulier `architecture/api.md` à chaque
      endpoint et `architecture/oauth2.md` à chaque changement de protocole ;
    - le hook ne surveille que les écritures faites par Claude Code : la relecture de PR vérifie la taille et
      `doc-length-ignore.txt` ;
    - un agent autre que Claude Code ne lit plus `AGENTS.md` : il doit être orienté vers `CLAUDE.md`.
- **Suivi** : migrer le code vers les conventions au fil de `tech-debt.md`.
