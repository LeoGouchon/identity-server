import {BrowserRouter} from 'react-router-dom';

import {AppContent} from './AppContent';


export const App = () => {
    return (
        <BrowserRouter>
            <AppContent/>
        </BrowserRouter>
    );
};
