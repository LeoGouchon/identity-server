import {Button, Flex, Typography} from 'antd';
import {Link} from 'react-router-dom';
import {StyledMainContent} from "../App.style";
import useBreakpoint from "antd/es/grid/hooks/useBreakpoint";

export const AboutPage = () => {
    const screens = useBreakpoint();
    const isMobile = !screens.md;

    return (
        <StyledMainContent isMobile={isMobile}>
            <Typography.Paragraph>
                Identity Server gère la connexion, les sessions et l'accès aux applications clientes via OAuth2/OIDC.
                Les comptes sont créés sur invitation.
            </Typography.Paragraph>
            <Typography.Paragraph>
                Pour une question ou un problème d'accès, contactez l'équipe du projet.
            </Typography.Paragraph>
            <Flex gap="small">
                <Button type="primary"><Link to="/login">Se connecter</Link></Button>
                <Button><Link to="/signup">Créer un compte</Link></Button>
            </Flex>
        </StyledMainContent>
    );
};
