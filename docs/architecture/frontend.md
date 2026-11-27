# Portail de connexion

Le portail `frontend/` (paquet `auth-portal`) : la SPA qui affiche la connexion, l'inscription sur invitation et la
réinitialisation du mot de passe, et qui poste les formulaires vers le serveur.

## Fichiers clés

- `frontend/package.json` : scripts `dev`, `build`, `typecheck`, `preview`.
- `frontend/vite.config.ts` : serveur de dev sur le port **5180** (valeur par défaut de `identity.auth-frontend-url`).
- `frontend/.env.example` : `VITE_IDENTITY_URL=http://localhost:8081`.
- `main.tsx`, `App.tsx`, `AppContent.tsx` : providers, routeur, mise en page et pied de page.
- `api/constant.ts` : `API_ENDPOINTS`, les URL du serveur.
- `routes/constant.ts`, `routes/index.tsx` : `ROUTES` et la table des routes.
- `hooks/` : `useLogin`, `useSignup`, `useInvitation`, `usePasswordReset`.
- `modules/` : pages `login`, `signup`, `forgot-password`, `about` ; `pages/NotFoundPage.tsx`.
- `context/ThemeProvider.tsx` : thème antd clair ou sombre, locale `fr_FR`.

## Stack

React 19, Vite 7, TypeScript, antd 6 (variables CSS activées), emotion, TanStack Query 5, React Router 7. Le portail
n'a ni lint ni test, et il n'est pas construit par la CI (voir [../tech-debt.md](../tech-debt.md)).

```bash
npm --prefix frontend install
npm --prefix frontend run dev        # ou make frontend
npm --prefix frontend run typecheck
npm --prefix frontend run build
```

## Appels au serveur

`API_ENDPOINTS` préfixe chaque chemin par `VITE_IDENTITY_URL` (sans `/` final) :

```ts
export const API_ENDPOINTS = {
    LOGIN: `${identityUrl}/login`,
    SIGNUP: `${identityUrl}/api/v1/signup`,
    INVITATION: (token: string) => `${identityUrl}/api/v1/invitations/${encodeURIComponent(token)}`,
    CLAIM_INVITATION: (token: string) => `${identityUrl}/api/v1/invitations/${encodeURIComponent(token)}/claim`,
    PASSWORD_FORGOT: `${identityUrl}/api/v1/password/forgot`,
    PASSWORD_RESET: `${identityUrl}/api/v1/password/reset`,
} as const;
```

- **Connexion** : pas de `fetch`. `LoginPage` rend un `<form action={API_ENDPOINTS.LOGIN} method="post">` natif
  (champs `username`, `password` et, si présent, `invitation` caché). Le navigateur suit les redirections de Spring
  Security, et le cookie de session est posé sur le domaine du serveur. `useLogin` fait la même chose en créant un
  formulaire caché.
- **Autres appels** : `fetch` dans les hooks TanStack Query (`useSignup`, `usePasswordReset` en mutations,
  `useInvitation` en query, clé `['invitation', token]`), sauf `InvitationCompletePage` qui appelle `claim` par un
  `fetch` direct avec `credentials: 'include'`.

## Routes

| Route                  | Page                     | Rôle                                                                 |
| ---------------------- | ------------------------ | -------------------------------------------------------------------- |
| `/`                    | —                        | redirige vers `/login`                                               |
| `/login`               | `LoginPage`              | formulaire de connexion ; `?error=authentication_failed` affiche l'erreur |
| `/signup`              | `SignupPage`             | inscription, uniquement avec un jeton d'invitation                   |
| `/invitation/complete` | `InvitationCompletePage` | `claim` de l'invitation après connexion, puis renvoi vers l'application |
| `/forgot-password`     | `ForgotPasswordPage`     | demande de lien, ou nouveau mot de passe si `?token=&email=`         |
| `/about`               | `AboutPage`              | présentation                                                         |
| `*`                    | `NotFoundPage`           | 404, lien vers `/about`                                              |

## Parcours

- **Connexion OAuth2** : un client envoie l'utilisateur sur `/oauth2/authorize` ; sans session, le serveur redirige
  vers `/login` du portail ; le formulaire poste sur le serveur, qui renvoie vers `/oauth2/authorize` puis vers le
  client ([oauth2.md](oauth2.md)). En cas d'échec, l'e-mail saisi est gardé dans `sessionStorage`
  (`identity-login-email`) pour pré-remplir le champ.
- **Inscription** : `/signup?invitationToken=` vérifie l'invitation (`useInvitation`), affiche le nom de
  l'application, puis poste prénom, nom, e-mail et mot de passe (`useSignup`). En cas de succès, un bouton renvoie
  vers `applicationUrl` ou vers `/login?invitation=`. « Déjà inscrit ? » mène aussi à `/login?invitation=`.
- **Compte existant invité** : `/login?invitation=` ajoute le champ caché `invitation` ; après connexion, le serveur
  redirige vers `/invitation/complete?invitation=`, qui appelle `claim` puis, après 500 ms, `applicationUrl`.
- **Mot de passe oublié** : `/forgot-password` envoie l'e-mail (`forgot`) ; le lien reçu rouvre la même page avec
  `token` et `email`, qui poste le nouveau mot de passe (`reset`) ([password-reset.md](password-reset.md)).

Le jeton d'invitation est lu sous trois noms de paramètre (`invitation`, `invitationToken`, `token`).

## Mise en page et thème

`AppContent` rend `StyledLayout` > `StyledMainCards` > routes, puis `Footer`. La largeur mobile est déduite de
`useBreakpoint()` (sous `md`). `ThemeProvider` choisit l'algorithme antd clair ou sombre selon `localStorage.theme`,
fournit `App` d'antd et un thème emotion `{ isDarkTheme }`. `main.tsx` enregistre aussi Chart.js, sans graphique dans
le portail.

## Déploiement

_À confirmer_ : ni l'image Docker ni les workflows GitHub ne construisent ou ne publient le portail. Son URL publique
doit correspondre à `identity.auth-frontend-url` et figurer dans `identity.cors.allowed-origins`.

Conventions du portail : [../conventions/frontend.md](../conventions/frontend.md).
