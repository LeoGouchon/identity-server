import {useRoutes} from 'react-router-dom';
import useBreakpoint from 'antd/es/grid/hooks/useBreakpoint';

import {Footer} from './components/footer/Footer';
import {routes} from './routes';
import {StyledLayout, StyledMainCards} from "./App.style";


export const AppContent = () => {

    const routing = useRoutes(routes);

    const screens = useBreakpoint();
    const isMobile = !screens.md;


    return (
        <StyledLayout>
            <StyledMainCards isMobile={isMobile}>
                {routing}
            </StyledMainCards>
            <Footer/>
        </StyledLayout>
    )

};
