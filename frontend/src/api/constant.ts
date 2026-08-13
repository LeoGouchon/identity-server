const identityUrl = (import.meta.env.VITE_IDENTITY_URL ?? '').replace(/\/$/, '');

export const API_ENDPOINTS = {
    LOGIN: `${identityUrl}/login`,
    SIGNUP: `${identityUrl}/api/v1/signup`,
    CLAIM_INVITATION: (token: string) => `${identityUrl}/api/v1/invitations/${encodeURIComponent(token)}/claim`,
    PASSWORD_FORGOT: `${identityUrl}/api/v1/password/forgot`,
    PASSWORD_RESET: `${identityUrl}/api/v1/password/reset`,
} as const;
