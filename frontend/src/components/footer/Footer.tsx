import {faGithub, faLinkedin} from '@fortawesome/free-brands-svg-icons';
import {faFolderClosed} from '@fortawesome/free-regular-svg-icons';
import {FontAwesomeIcon} from '@fortawesome/react-fontawesome';
import {Flex, Space, Typography} from 'antd';

import {FlexFullWidth, StyledFooterLayout} from '../../App.style';
import {Link} from "react-router-dom";
import {ROUTES} from "../../routes/constant";
import useBreakpoint from "antd/es/grid/hooks/useBreakpoint";

const {Text} = Typography;

export const Footer = () => {
    const screens = useBreakpoint();
    const isMobile = !screens.md;

    return (
        <StyledFooterLayout isMobile={isMobile} style={{width: '100%'}}>
            <FlexFullWidth justify={'space-between'} align={'center'} gap={'small'} wrap={'wrap'}>
                <Flex gap={'middle'} align={'center'}>
                    <Text>Created by Léo Gouchon ©2026</Text>
                    <Link to={ROUTES.ABOUT} style={{textWrap: 'nowrap'}}>À propos</Link>
                </Flex>
                <Flex gap={'small'}>
                    <a href={'https://github.com/LeoGouchon'} target={'_blank'}>
                        <FontAwesomeIcon icon={faGithub}/>
                    </a>
                    <a href={'https://www.linkedin.com/in/leo-gouchon/'} target={'_blank'}>
                        <FontAwesomeIcon icon={faLinkedin}/>
                    </a>
                    <a href={'https://leogouchon.com'} target={'_blank'}>
                        <FontAwesomeIcon icon={faFolderClosed}/>
                    </a>
                </Flex>
            </FlexFullWidth>
        </StyledFooterLayout>
    );
};
