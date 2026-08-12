import {useMutation} from '@tanstack/react-query';

import {API_ENDPOINTS} from '../api/constant';

export type PasswordResetRequest =
    | {kind: 'forgot'; email: string}
    | {kind: 'reset'; email: string; token: string; password: string};

export class PasswordResetError extends Error {
    constructor(public readonly kind: PasswordResetRequest['kind']) {
        super('Échec de la réinitialisation du mot de passe.');
        this.name = 'PasswordResetError';
    }
}

const resetPassword = async (request: PasswordResetRequest): Promise<PasswordResetRequest['kind']> => {
    const response = await fetch(
        request.kind === 'reset' ? API_ENDPOINTS.PASSWORD_RESET : API_ENDPOINTS.PASSWORD_FORGOT,
        {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(request.kind === 'reset'
                ? {email: request.email, token: request.token, password: request.password}
                : {email: request.email}),
        },
    );

    if (!response.ok) {
        throw new PasswordResetError(request.kind);
    }

    return request.kind;
};

export const usePasswordReset = () => useMutation<
    PasswordResetRequest['kind'],
    PasswordResetError,
    PasswordResetRequest
>({mutationFn: resetPassword});
