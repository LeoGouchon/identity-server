import {Alert, Button, Flex, Form, Input, Typography} from 'antd';
import {Link, useSearchParams} from 'react-router-dom';

import {ROUTES} from "../../routes/constant";
import {LOGIN_ENDPOINT} from "../../hooks/useLogin";
import {StyledIconImage, StyledLoginCard, StyledLoginHeader, StyledTitleIconText} from "./LoginPage.style";

const {Text} = Typography;

export const LoginPage = () => {
    const [params] = useSearchParams();
    const hasError = params.get('error') === 'true';

    return (
        <StyledLoginCard>
            {hasError && <Alert type="error" showIcon title="Email ou mot de passe incorrect."/>}
            <StyledLoginHeader vertical>
                <Flex gap={'small'} align={'baseline'} wrap={'nowrap'}>
                    <StyledIconImage src={'/assets/icon.svg'} alt={'Gouchon icon\'s'}/>
                    <StyledTitleIconText>GOUCHON AUTHENTICATION</StyledTitleIconText>
                </Flex>
                <Text type={'secondary'}>Portail de connexion aux applications Gouchon</Text>
            </StyledLoginHeader>
            <form action={LOGIN_ENDPOINT} method="post">
                <Form
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
                    name="email"
                    label="Adresse mail"
                    validateDebounce={1500}
                    rules={[{
                        type: 'email',
                        message: 'Veuillez entrer un mail valide.'
                    }]}
                >
                    <Input id="username" name="username" type="email" autoComplete="email" required/>
                </Form.Item>
                <Form.Item
                    name="password"
                    label="Mot de passe"
                    required
                >
                    <Flex
                        vertical
                        align='end'
                    >
                        <Input.Password id="password" name="password" autoComplete="current-password" required/>
                        <Link to={ROUTES.FORGOT_PASSWORD}>Mot de passe oublié ?</Link>
                    </Flex>
                </Form.Item>
                    <Button type="primary" htmlType="submit" block>Se connecter</Button>
                </Form>
            </form>
        </StyledLoginCard>
    );
};
