import {useMutation} from '@tanstack/react-query';

export type SignupCredentials = {
    email: string;
    password: string;
    invitationToken: string;
};

export type SignupResponse = {
    id: string;
    email: string;
};

export class SignupError extends Error {
    constructor(public readonly status: number) {
        super('Échec de la création du compte.');
        this.name = 'SignupError';
    }
}

const identityUrl = (import.meta.env.VITE_IDENTITY_URL ?? '').replace(/\/$/, '');

const signup = async (credentials: SignupCredentials): Promise<SignupResponse> => {
    const response = await fetch(`${identityUrl}/api/v1/signup`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(credentials),
    });

    if (!response.ok) {
        throw new SignupError(response.status);
    }

    return response.json() as Promise<SignupResponse>;
};

export const useSignup = () => useMutation<SignupResponse, SignupError, SignupCredentials>({
    mutationFn: signup,
});
