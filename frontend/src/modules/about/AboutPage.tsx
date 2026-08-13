import {Button, Flex, Typography} from 'antd';
import {Link} from 'react-router-dom';
import useBreakpoint from "antd/es/grid/hooks/useBreakpoint";
import {StyledCard} from "../login/LoginPage.style";

export const AboutPage = () => {
    const screens = useBreakpoint();
    const isMobile = !screens.md;

    return (
        <StyledCard isMobile={isMobile}>
            <Typography.Paragraph>
                Cette URL gère la connexion, les sessions et l'accès aux applications Gouchon via OAuth2/OIDC.
                Les comptes sont créés sur invitation. Il faut avoir reçu un lien d'invitation pour pouvoir s'inscrire.
            </Typography.Paragraph>
            <Typography.Paragraph>
                Pour une question ou un problème d'accès, contactez l'équipe du projet
                <Typography.Text type={'secondary'}> (il n'y a que Léo mais chut).</Typography.Text>
            </Typography.Paragraph>
            <Flex gap="small">
                <Button><Link to="/login">Se connecter</Link></Button>
            </Flex>
        </StyledCard>
    );
};
