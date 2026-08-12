import {useLocation, useRoutes} from 'react-router-dom';
import useBreakpoint from 'antd/es/grid/hooks/useBreakpoint';

import {Footer} from './components/footer/Footer';
import {routes} from './routes';
import {StyledContent, StyledLayout, StyledMainCards, StyledMainContent} from "./App.style";
import {ROUTES} from "./routes/constant";


export const AppContent = () => {
    const {pathname} = useLocation();

    const routing = useRoutes(routes);
    const CARD_PAGE_ROUTE: string[] = [ROUTES.LOGIN, ROUTES.SIGNUP]

    const screens = useBreakpoint();
    const isMobile = !screens.md;

    if (CARD_PAGE_ROUTE.includes(pathname)) {
        return (
            <StyledLayout>
                <StyledMainCards isMobile={isMobile}>
                    {routing}
                </StyledMainCards>
                <Footer/>
            </StyledLayout>
        )
    }

    return (
        <StyledLayout>
            <StyledContent isMobile={isMobile}>
                <StyledMainContent isMobile={isMobile}>
                    {routing}
                </StyledMainContent>
            </StyledContent>
            <Footer/>
        </StyledLayout>
    );
};
