# 0003. PostgreSQL et Flyway

- **Statut** : Accepté
- **Date** : 2026-11-27 (documente un choix déjà en place)

## Contexte

Le serveur garde des données qui doivent survivre aux redémarrages et aux déploiements : comptes, invitations,
sessions de refresh, jetons de réinitialisation et clé de signature. Le schéma évolue avec les features (audience des
sessions, nom des utilisateurs, client des invitations) et doit migrer les données de production sans intervention
manuelle.

## Options envisagées

1. **Schéma généré par Hibernate** (`ddl-auto: update`) : rien à écrire, mais aucune maîtrise des migrations de
   données ni des contraintes, et un risque de perte en production.
2. **Liquibase** : complet, mais plus verbeux (XML ou YAML) pour un schéma de quelques tables.
3. **Flyway** avec des migrations SQL versionnées, et Hibernate en `validate`.

## Décision

Nous utilisons **PostgreSQL 17** et **Flyway** :

- chaque changement de schéma est une migration `src/main/resources/db/migration/V<n>__<description>.sql`, jamais
  modifiée une fois mergée ;
- Hibernate valide le schéma au démarrage (`spring.jpa.hibernate.ddl-auto: validate`) : une entité désalignée
  empêche l'application de démarrer ;
- les données de dev sont dans `db/dev-data/`, un emplacement Flyway séparé.

## Conséquences

- **Positives** : migrations relues dans les PR, appliquées automatiquement au démarrage, identiques en local et en
  production.
- **Négatives / coûts** :
    - les tests actuels n'exercent ni PostgreSQL ni les migrations (mocks uniquement) : une migration se vérifie sur
      `make up` ;
    - le seed de dev en `V9999` masque les nouvelles migrations sur une base locale, et l'exemple de configuration le
      charge (voir [../tech-debt.md](../tech-debt.md)).
- **Suivi** : Testcontainers pour un test d'intégration, à décider par un ADR.
