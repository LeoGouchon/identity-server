import {Alert, Button, Form, Input, Result, Typography} from 'antd';
import {useState} from 'react';
import {Link, useSearchParams} from 'react-router-dom';

type SignupValues = { email: string; password: string; confirmation: string };

export const SignupPage = () => {
    const [params] = useSearchParams();
    const invitationToken = params.get('invitation') ?? params.get('token');
    const [error, setError] = useState<string>();
    const [created, setCreated] = useState(false);

    if (!invitationToken) {
        return <Result status="warning" title="Invitation requise"
                       subTitle="La création de compte est uniquement accessible depuis un lien d'invitation."
                       extra={<Button type="primary"><Link to="/about">En savoir
                           plus</Link></Button>}/>;
    }

    const submit = async ({email, password}: SignupValues) => {
        setError(undefined);
        const response = await fetch(`${import.meta.env.VITE_IDENTITY_URL ?? ''}/api/v1/signup`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({email, password, invitationToken}),
        });
        if (!response.ok) {
            setError(response.status === 409 ? 'Cette adresse email est déjà utilisée.' : 'Cette invitation est invalide ou expirée.');
            return;
        }
        setCreated(true);
    };

    if (created) return <Result status="success" title="Votre compte est prêt"
                                subTitle="Vous pouvez maintenant vous connecter."
                                extra={<Button type="primary"><Link to="/login">Se
                                    connecter</Link></Button>}/>;

    return <>
        {error && <Alert type="error" showIcon message={error}/>}
        <Form layout="vertical" onFinish={submit}>
            <Form.Item label="Adresse email" name="email" rules={[{required: true, type: 'email'}]}><Input
                autoComplete="email"/></Form.Item>
            <Form.Item label="Mot de passe" name="password"
                       rules={[{required: true, min: 8, message: '8 caractères minimum.'}]}><Input.Password
                autoComplete="new-password"/></Form.Item>
            <Form.Item label="Confirmer le mot de passe" name="confirmation" dependencies={['password']}
                       rules={[{required: true}, ({getFieldValue}) => ({
                           validator(_, value) {
                               return !value || getFieldValue('password') === value ? Promise.resolve() : Promise.reject(new Error('Les mots de passe ne correspondent pas.'));
                           }
                       })]}><Input.Password autoComplete="new-password"/></Form.Item>
            <Button type="primary" htmlType="submit" block>Créer mon compte</Button>
        </Form>
        <Typography.Paragraph className="identity-links">Déjà inscrit ? <Link to="/login">Se
            connecter</Link></Typography.Paragraph>
    </>;
};
