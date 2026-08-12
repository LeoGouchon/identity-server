export const ROUTES = {
    HOME: '/',
    ABOUT: '/about',
    LOGIN: '/login',
    FORGOT_PASSWORD: '/forgot-password',
    SIGNUP: '/signup',
    NOT_FOUND: '*',
} as const;

export type Route = (typeof ROUTES)[keyof typeof ROUTES];
