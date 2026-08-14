import {useQuery} from '@tanstack/react-query';
import {API_ENDPOINTS} from '../api/constant';

export type Invitation = {
    token: string;
    clientId: string;
    applicationName: string;
    invitationUrl: string;
};

const fetchInvitation = async (token: string): Promise<Invitation> => {
    const response = await fetch(API_ENDPOINTS.INVITATION(token));
    if (!response.ok) throw new Error('Invalid invitation');
    return response.json() as Promise<Invitation>;
};

export const useInvitation = (token: string | null) => useQuery({
    queryKey: ['invitation', token],
    queryFn: () => fetchInvitation(token!),
    enabled: Boolean(token),
});
