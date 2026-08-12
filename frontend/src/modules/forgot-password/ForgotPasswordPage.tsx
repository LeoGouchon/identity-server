import {Alert, Button, Form, Input, Result, Spin, Typography} from 'antd';
import {Link, useSearchParams} from 'react-router-dom';

import {usePasswordReset} from '../../hooks/usePasswordReset';
import {ROUTES} from '../../routes/constant';
import {StyledCard, StyledLoginHeader} from '../login/LoginPage.style';
import {StyledResult} from "./ForgotPassword.Style";
import useBreakpoint from "antd/es/grid/hooks/useBreakpoint";

type PasswordValues = {
    email?: string;
    password?: string;
    confirmation?: string;
};

const {Text, Paragraph, Title} = Typography;

export const ForgotPasswordPage = () => {
    const screens = useBreakpoint();
    const isMobile = !screens.md;

    const [params] = useSearchParams();
    const token = params.get('token');
    const email = params.get('email') ?? '';
    const reset = Boolean(token);
    const passwordReset = usePasswordReset();
    const completedAction = passwordReset.data;

    const submit = ({email: submittedEmail, password}: PasswordValues) => {
        if (reset && token) {
            passwordReset.mutate({kind: 'reset', email, token, password: password ?? ''});
            return;
        }

        passwordReset.mutate({kind: 'forgot', email: submittedEmail ?? ''});
    };

    const errorMessage = passwordReset.error?.kind === 'reset'
        ? 'Le lien est invalide ou expiré.'
        : 'Impossible d’envoyer la demande.';

    return (
        <StyledCard isMobile={isMobile}>
            {completedAction === 'reset' && <StyledResult
                status="success"
                title="Mot de passe modifié"
                subTitle="Un e-mail de confirmation vous a été envoyé."
                extra={<Button><Link to={ROUTES.LOGIN}>Se connecter</Link></Button>}
            />}

            {completedAction === 'forgot' && <StyledResult
                status="success"
                title="Vérifiez votre boîte mail"
                subTitle="Si cette adresse correspond à un compte, un lien de réinitialisation a été envoyé."
                extra={<Button><Link to={ROUTES.LOGIN}>Retour à la connexion</Link></Button>}
            />}

            {!completedAction && <>
                <StyledLoginHeader vertical>
                    <Title level={4} style={{margin: 0}}>Mot de passe oublié</Title>
                    <Text type="secondary">
                        {reset ? 'Choisissez un nouveau mot de passe' : 'Récupérez l’accès à votre compte'}
                    </Text>
                </StyledLoginHeader>
                {passwordReset.error && <Alert type="error" showIcon title={errorMessage} style={{marginBottom: '1rem'}}/>}
                <Form
                    layout="vertical"
                    onFinish={submit}
                    initialValues={{email}}
                    requiredMark={false}
                    size="large"
                    variant="filled"
                    styles={{label: {height: '100%'}}}
                >
                    {!reset && <Form.Item
                        label="Adresse mail"
                        name="email"
                        rules={[
                            {required: true, message: 'Veuillez entrer votre adresse mail.'},
                            {type: 'email', message: 'Veuillez entrer un mail valide.'},
                        ]}
                    >
                        <Input autoComplete="email"/>
                    </Form.Item>}
                    {reset && <>
                        <Form.Item
                            label="Nouveau mot de passe"
                            name="password"
                            rules={[
                                {required: true, message: 'Veuillez choisir un mot de passe.'},
                                {min: 8, message: '8 caractères minimum.'},
                            ]}
                        >
                            <Input.Password autoComplete="new-password"/>
                        </Form.Item>
                        <Form.Item
                            label="Confirmer le mot de passe"
                            name="confirmation"
                            dependencies={['password']}
                            rules={[
                                {required: true, message: 'Veuillez confirmer votre mot de passe.'},
                                ({getFieldValue}) => ({
                                    validator(_, value) {
                                        return !value || getFieldValue('password') === value
                                            ? Promise.resolve()
                                            : Promise.reject(new Error('Les mots de passe ne correspondent pas.'));
                                    },
                                }),
                            ]}
                        >
                            <Input.Password autoComplete="new-password"/>
                        </Form.Item>
                    </>}
                    <Button type="primary" htmlType="submit" block disabled={passwordReset.isPending} loading={passwordReset.isPending}>
                        {reset ? 'Modifier le mot de passe' : 'Recevoir un lien'}
                    </Button>
                </Form>
                <Paragraph style={{marginTop: '1rem', marginBottom: 0}}>
                    <Link to={ROUTES.LOGIN}>Retour à la connexion</Link>
                </Paragraph>
            </>}
        </StyledCard>
    );
};
