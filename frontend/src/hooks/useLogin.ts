import {useMutation} from '@tanstack/react-query';

export type LoginCredentials = {
    email: string;
    password: string;
};

const identityUrl = (import.meta.env.VITE_IDENTITY_URL ?? '').replace(/\/$/, '');
export const LOGIN_ENDPOINT = `${identityUrl}/login`;

const login = async ({email, password}: LoginCredentials): Promise<void> => {
    const form = document.createElement('form');
    form.method = 'POST';
    form.action = LOGIN_ENDPOINT;
    form.style.display = 'none';

    for (const [name, value] of [['username', email], ['password', password]]) {
        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = name;
        input.value = value;
        form.appendChild(input);
    }

    document.body.appendChild(form);
    form.submit();
};

export const useLogin = () => useMutation<void, Error, LoginCredentials>({
    mutationFn: login,
});
