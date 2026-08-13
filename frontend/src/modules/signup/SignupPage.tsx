import {Alert, Button, Flex, Form, Input, Result, Spin, Typography} from 'antd';
import {useEffect} from 'react';
import {Link, useSearchParams} from 'react-router-dom';

import {useSignup} from '../../hooks/useSignup';
import {
    StyledIconImage,
    StyledCard,
    StyledLoginHeader,
    StyledTitleIconText,
} from '../login/LoginPage.style';
import {ROUTES} from '../../routes/constant';
import useBreakpoint from "antd/es/grid/hooks/useBreakpoint";

type SignupValues = {
    firstName: string;
    lastName: string;
    email: string;
    password: string;
    confirmation: string;
};

const {Text, Paragraph} = Typography;

export const SignupPage = () => {
    const screens = useBreakpoint();
    const isMobile = !screens.md;

    const [params] = useSearchParams();
    const invitationToken = params.get('invitation') ?? params.get('invitationToken') ?? params.get('token');
    const signup = useSignup();

    useEffect(() => {
        signup.reset();
    }, [invitationToken]);

    if (!invitationToken) {
        return <StyledCard isMobile={isMobile}>
            <Result
                status="warning"
                title="Invitation requise"
                subTitle="La création de compte est uniquement accessible depuis un lien d’invitation."
                extra={<Button type="primary"><Link to={ROUTES.ABOUT}>En savoir plus</Link></Button>}
            />
        </StyledCard>;
    }

    const submit = ({firstName, lastName, email, password}: SignupValues) => {
        signup.mutate({firstName, lastName, email, password, invitationToken});
    };

    if (signup.isSuccess) return (
        <StyledCard isMobile={isMobile}>
            <Result
                status="success"
                title="Votre compte est prêt"
                subTitle="Vous pouvez maintenant vous connecter."
                extra={<Button type="primary"><Link to={`${ROUTES.LOGIN}?invitation=${encodeURIComponent(invitationToken)}`}>Se connecter</Link></Button>}
            />
        </StyledCard>
    );

    const errorMessage = signup.error?.status === 409
        ? 'Cette adresse email est déjà utilisée.'
        : signup.error
            ? 'Cette invitation est invalide ou expirée.'
            : undefined;

    return (
        <StyledCard isMobile={isMobile}>
            <StyledLoginHeader vertical>
                <Flex gap="small" align="baseline" wrap="nowrap">
                    <StyledIconImage src="/assets/icon.svg" alt="Icône Gouchon"/>
                    <StyledTitleIconText>GOUCHON AUTHENTICATION</StyledTitleIconText>
                </Flex>
                <Text type="secondary">Créez votre compte pour accéder aux applications Gouchon</Text>
            </StyledLoginHeader>
            {errorMessage && <Alert type="error" showIcon title={errorMessage} style={{marginBottom: '1rem'}}/>}
            <Form
                layout="vertical"
                onFinish={submit}
                requiredMark={false}
                size="large"
                variant="filled"
                styles={{
                    label: {
                        height: '100%',
                    }
                }}>
                <Flex gap="small" align="baseline">
                    <Form.Item label="Prénom" name="firstName" rules={[{
                        required: true,
                        whitespace: true,
                        message: 'Veuillez entrer votre prénom.',
                    }]}>
                        <Input autoComplete="given-name"/>
                    </Form.Item>
                    <Form.Item label="Nom" name="lastName" rules={[{
                        required: true,
                        whitespace: true,
                        message: 'Veuillez entrer votre nom.',
                    }]}>
                        <Input autoComplete="family-name"/>
                    </Form.Item>
                </Flex>
                <Form.Item
                    label="Adresse mail"
                    name="email"
                    validateDebounce={1000}
                    rules={[
                        {required: true, message: 'Veuillez entrer votre adresse mail.'},
                        {type: 'email', message: 'Veuillez entrer un mail valide.'},
                    ]}
                >
                    <Input autoComplete="email"/>
                </Form.Item>
                <Form.Item
                    label="Mot de passe"
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
                <Button
                    type="primary"
                    htmlType="submit"
                    block
                    disabled={signup.isPending}
                >
                    {signup.isPending ? <Spin size="small"/> : 'Créer mon compte'}
                </Button>
            </Form>
            <Paragraph style={{marginTop: '1rem', marginBottom: 0}}>
                Déjà inscrit ? <Link to={`${ROUTES.LOGIN}?invitation=${encodeURIComponent(invitationToken)}`}>Se connecter</Link>
            </Paragraph>
        </StyledCard>);
};
