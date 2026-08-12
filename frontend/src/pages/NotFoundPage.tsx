import { Result } from 'antd';
import { Link } from 'react-router-dom';

export const NotFoundPage = () => <Result status="404" title="Page introuvable" extra={<Link to="/about">Retour à l'accueil</Link>} />;
