# Portail (frontend)

Les règles du portail `frontend/` : les pages de connexion, d'inscription, d'invitation et de mot de passe oublié.
Il a la même stack que kicker-app (React 19, TypeScript, Vite, antd 6, emotion, TanStack Query) et en suit les
conventions ; ce fichier en garde l'essentiel et ce qui est propre au portail. Fonctionnement :
[../architecture/frontend.md](../architecture/frontend.md).

## Fichiers clés

- `frontend/src/api/constant.ts` : les URL de l'identity server (`API_ENDPOINTS`), à partir de `VITE_IDENTITY_URL`.
- `frontend/src/routes/constant.ts`, `frontend/src/routes/index.tsx` : les routes.
- `frontend/src/hooks/` : un hook TanStack Query par appel.
- `frontend/src/context/ThemeProvider.tsx` : thème antd clair / sombre et `App` d'antd.

## Commandes

```bash
npm --prefix frontend ci
npm --prefix frontend run typecheck   # vérification TypeScript
npm --prefix frontend run build       # build Vite : à lancer avant toute PR qui touche le portail
make frontend                         # serveur Vite sur http://localhost:5180
```

## Code

- **Imports React** nommés : `import { useState, type ReactNode } from 'react'`. Jamais `React.useState`,
  `React.FC`, `React.ReactNode` ni `import React`.
- **Composants** : fonctions fléchées, **exports nommés** (pas d'`export default`), props typées par un
  `type XxxProps` juste au-dessus.
- **TypeScript** : `type` plutôt qu'`interface`, pas d'`enum` (objet `as const` + type union, comme `ROUTES`),
  `import type` pour un type, pas d'`any`.
- **Imports d'antd** depuis le paquet (`import { Grid } from 'antd'`), jamais depuis `antd/es/...`.

## Structure

- Une page par dossier `src/pages/<page-kebab>/`, avec ses `components/` locaux. Ce qui sert à plusieurs pages va
  dans `src/components/` ou `src/hooks/`. **Une page n'importe jamais depuis une autre page** : un style partagé
  (la carte du portail) va dans `src/components/`.
- Fichiers satellites : `<Composant>.style.tsx` pour tout le style (casse exacte), `<Composant>.utils.ts` pour la
  logique pure.
- Toute nouvelle route s'ajoute à `ROUTES` et à `routes/index.tsx`.

## Style

- Pas de `style={{}}` inline, pas de couleur en dur : composants emotion dans le `.style.tsx`, tokens antd
  (`var(--ant-*)`). Composants stylés nommés `Styled<Rôle>` (`StyledCard`), comme le code existant.
- Chaque page se vérifie en thème clair et sombre, et sous le breakpoint `md` (768 px).

## Données serveur

- Toute URL vient de `API_ENDPOINTS` ; tout appel passe par un hook de `src/hooks/` (`useQuery` pour une lecture,
  `useMutation` pour une action), jamais un `fetch` dans une page.
- Les cookies de session sont envoyés quand l'appel en dépend (`credentials: 'include'`, comme le `claim`).
- Chaque requête gère ses états chargement, erreur et succès (`Spin`, `Result`, `Alert`).
- Feedback ponctuel (toast, confirmation) avec `App.useApp()` d'antd (`message`, `modal`, `notification`).

## Propre au portail

- **La connexion est un `POST` de formulaire natif** vers `API_ENDPOINTS.LOGIN` (`username`, `password`, et
  `invitation` si besoin), jamais un `fetch` : Spring Security crée la session et redirige le navigateur
  (`SavedRequest` vers `/oauth2/authorize`, ou `/login?error=authentication_failed`).
- Le portail n'affiche pas les messages de l'API : il a ses propres textes, en **français**, et déduit le cas du
  statut HTTP.
- Aucun secret ni jeton OAuth2 dans le portail : il ne fait que créer la session et appeler l'API publique.
- Le jeton d'invitation circule dans l'URL sous **un seul** nom de paramètre.

**État** : ⚠️ voir les lignes « Portail » de [../tech-debt.md](../tech-debt.md) : `React.ReactNode` et `interface`
(`ThemeProvider`), `style={{}}` (`LoginPage`, `ForgotPasswordPage`), pages dans `src/modules/` et `src/pages/`,
`LoginPage.style` importé par d'autres pages, `ForgotPassword.Style.tsx`, imports `antd/es/...`, `fetch` dans
`InvitationCompletePage`, jeton d'invitation lu sous trois noms, `chart.js` inutilisé ; pas de lint ni de test.
