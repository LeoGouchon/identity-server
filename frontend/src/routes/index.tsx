import { Navigate, type RouteObject } from 'react-router-dom';

import { AboutPage } from '../pages/AboutPage';
import { ForgotPasswordPage } from '../pages/ForgotPasswordPage';
import { LoginPage } from '../modules/login/LoginPage';
import { NotFoundPage } from '../pages/NotFoundPage';
import { SignupPage } from '../pages/SignupPage';
import { ROUTES } from './constant';

export const routes: RouteObject[] = [
    { path: ROUTES.HOME, element: <Navigate to={ROUTES.LOGIN} replace /> },
    { path: ROUTES.ABOUT, element: <AboutPage /> },
    { path: ROUTES.LOGIN, element: <LoginPage /> },
    { path: ROUTES.FORGOT_PASSWORD, element: <ForgotPasswordPage /> },
    { path: ROUTES.SIGNUP, element: <SignupPage /> },
    { path: ROUTES.NOT_FOUND, element: <NotFoundPage /> },
];
