import { Alert, Button, Input, Form } from 'antd';
import { Link } from 'react-router-dom';

export const ForgotPasswordPage = () => (
    <>
        <Alert
            type="info"
            showIcon
            message="Aucun flux de réinitialisation n'est encore exposé par le serveur."
            description="Contactez l'équipe du projet pour récupérer l'accès à votre compte."
        />
        <Form layout="vertical" className="identity-form-disabled">
            <Form.Item label="Adresse email">
                <Input disabled placeholder="vous@exemple.com" />
            </Form.Item>
            <Button disabled block>Recevoir un lien</Button>
        </Form>
        <Link to="/login">Retour à la connexion</Link>
    </>
);
