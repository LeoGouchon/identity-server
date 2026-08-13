import { Navigate, type RouteObject } from 'react-router-dom';

import { AboutPage } from '../modules/about/AboutPage';
import { ForgotPasswordPage } from '../modules/forgot-password/ForgotPasswordPage';
import { LoginPage } from '../modules/login/LoginPage';
import { NotFoundPage } from '../pages/NotFoundPage';
import { SignupPage } from '../modules/signup/SignupPage';
import { InvitationCompletePage } from '../modules/signup/InvitationCompletePage';
import { ROUTES } from './constant';

export const routes: RouteObject[] = [
    { path: ROUTES.HOME, element: <Navigate to={ROUTES.LOGIN} replace /> },
    { path: ROUTES.ABOUT, element: <AboutPage /> },
    { path: ROUTES.LOGIN, element: <LoginPage /> },
    { path: ROUTES.FORGOT_PASSWORD, element: <ForgotPasswordPage /> },
    { path: ROUTES.SIGNUP, element: <SignupPage /> },
    { path: ROUTES.INVITATION_COMPLETE, element: <InvitationCompletePage /> },
    { path: ROUTES.NOT_FOUND, element: <NotFoundPage /> },
];
