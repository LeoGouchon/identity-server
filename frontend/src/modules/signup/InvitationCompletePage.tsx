import {Alert, Button, Result, Spin} from 'antd';
import {useEffect, useState} from 'react';
import {Link, useSearchParams} from 'react-router-dom';
import {API_ENDPOINTS} from '../../api/constant';
import {ROUTES} from '../../routes/constant';
import {StyledCard} from '../login/LoginPage.style';

export const InvitationCompletePage = () => {
    const [params] = useSearchParams();
    const token = params.get('invitation') ?? params.get('invitationToken') ?? params.get('token');
    const [state, setState] = useState<'loading' | 'success' | 'error'>('loading');

    useEffect(() => {
        if (!token) { setState('error'); return; }
        fetch(API_ENDPOINTS.CLAIM_INVITATION(token), {method: 'POST', credentials: 'include'})
            .then(response => { if (!response.ok) throw new Error(); return response.json(); })
            .then(() => setState('success'))
            .catch(() => setState('error'));
    }, [token]);

    if (state === 'loading') return <StyledCard><Spin /> Liaison de votre compte à l’application…</StyledCard>;
    if (state === 'error') return <StyledCard><Alert type="error" showIcon title="Cette invitation est invalide, expirée ou votre connexion a échoué." /></StyledCard>;
    return <StyledCard><Result status="success" title="Accès activé" subTitle="Votre compte est maintenant lié à cette application." extra={<Button type="primary"><Link to={ROUTES.LOGIN}>Retour</Link></Button>} /></StyledCard>;
};
