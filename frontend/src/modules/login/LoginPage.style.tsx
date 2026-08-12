import styled from "@emotion/styled";
import {Card, Flex} from "antd";

export const StyledLoginCard = styled(Card)`
    padding-top: var(--ant-padding-sm);
`;

export const StyledIconImage = styled.img`
    width: 1.5rem;
`;

export const StyledTitleIconText = styled.p`
    font-family: "Big Shoulders", sans-serif;
    font-weight: bold;
    font-size: 1.3rem;
    color: #3C3C3B;
    line-height: 30px;
    padding: 0;
    margin: 0;
    text-wrap: nowrap;
`;

export const StyledLoginHeader = styled(Flex)`
    margin-bottom: 24px;
`;