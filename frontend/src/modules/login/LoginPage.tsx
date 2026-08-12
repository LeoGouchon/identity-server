import {Button, Flex, Form, Input, Typography} from 'antd';
import {useEffect, useState} from 'react';
import {Link, useSearchParams} from 'react-router-dom';

import {ROUTES} from "../../routes/constant";
import {LOGIN_ENDPOINT} from "../../hooks/useLogin";
import {StyledIconImage, StyledLoginCard, StyledLoginHeader, StyledTitleIconText} from "./LoginPage.style";

const LOGIN_EMAIL_STORAGE_KEY = 'identity-login-email';

const {Text} = Typography;

export const LoginPage = () => {
    const [params] = useSearchParams();
    const hasError = params.get('error') === 'authentication_failed';
    const [showError, setShowError] = useState(hasError);
    const [email, setEmail] = useState('');
    const [form] = Form.useForm();

    useEffect(() => {
        if (!hasError) {
            sessionStorage.removeItem(LOGIN_EMAIL_STORAGE_KEY);
            return;
        }

        const storedEmail = sessionStorage.getItem(LOGIN_EMAIL_STORAGE_KEY);
        if (storedEmail) {
            setEmail(storedEmail);
            form.setFieldsValue({username: storedEmail});
        }
    }, [form, hasError]);

    return (
        <StyledLoginCard>
            <StyledLoginHeader vertical>
                <Flex gap={'small'} align={'baseline'} wrap={'nowrap'}>
                    <StyledIconImage src={'/assets/icon.svg'} alt={'Gouchon icon\'s'}/>
                    <StyledTitleIconText>GOUCHON AUTHENTICATION</StyledTitleIconText>
                </Flex>
                <Text type={'secondary'}>Portail de connexion aux applications Gouchon</Text>
            </StyledLoginHeader>
            <form
                action={LOGIN_ENDPOINT}
                method="post"
                onSubmit={() => sessionStorage.setItem(LOGIN_EMAIL_STORAGE_KEY, email)}
                style={{marginBottom: '1rem'}}
            >
                <Form
                    form={form}
                    component={false}
                    variant='filled'
                    requiredMark={false}
                    size="large"
                    layout="vertical"
                    styles={{
                        label: {
                            height: '100%',
                        }
                    }}
                >
                    <Form.Item
                        name="username"
                        label="Adresse mail"
                        validateStatus={showError ? 'error' : undefined}
                        help={showError ? 'Email ou mot de passe incorrect.' : undefined}
                        validateDebounce={1500}
                        rules={[{
                            type: 'email',
                            message: 'Veuillez entrer un mail valide.'
                        }]}
                    >
                        <Input
                            id="username"
                            name="username"
                            type="email"
                            autoComplete="email"
                            onChange={(event) => {
                                const nextEmail = event.target.value;
                                setEmail(nextEmail);
                                sessionStorage.setItem(LOGIN_EMAIL_STORAGE_KEY, nextEmail);
                                setShowError(false);
                            }}
                            required
                        />
                    </Form.Item>
                    <Form.Item
                        name="password"
                        label="Mot de passe"
                        validateStatus={showError ? 'error' : undefined}
                        help={showError ? 'Email ou mot de passe incorrect.' : undefined}
                        required
                    >
                        <Flex
                            vertical
                            align='end'
                        >
                            <Input.Password
                                id="password"
                                name="password"
                                autoComplete="current-password"
                                onChange={() => setShowError(false)}
                                required
                            />
                        </Flex>
                    </Form.Item>
                    <Button type="primary" danger={showError} htmlType="submit" block>
                        Se connecter
                    </Button>
                </Form>
            </form>
            <Link to={ROUTES.FORGOT_PASSWORD}>Mot de passe oublié ?</Link>
        </StyledLoginCard>
    );
};
