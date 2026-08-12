const identityUrl = (import.meta.env.VITE_IDENTITY_URL ?? '').replace(/\/$/, '');

export const API_ENDPOINTS = {
    LOGIN: `${identityUrl}/login`,
    SIGNUP: `${identityUrl}/api/v1/signup`,
    PASSWORD_FORGOT: `${identityUrl}/api/v1/password/forgot`,
    PASSWORD_RESET: `${identityUrl}/api/v1/password/reset`,
} as const;
