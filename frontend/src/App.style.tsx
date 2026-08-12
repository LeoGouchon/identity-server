import styled from '@emotion/styled';
import {Flex, type FlexProps, Layout, type LayoutProps} from 'antd';

export const StyledLayout = styled(Layout)<LayoutProps>`
    min-height: 100vh;
    max-width: 100vw;
    position: relative;
    display: flex;
    justify-content: center;
    overflow-x: hidden;
`;

export const StyledContent = styled(Layout.Content)<{ isMobile: boolean }>`
    margin: ${({ isMobile }) => (isMobile ? '8px' : '16px')};
`;

export const StyledMainContent = styled.main<{ isMobile: boolean }>`
    padding: ${({ isMobile }) => (isMobile ? '12px 8px' : '24px')};
    min-height: 360px;
    background-color: var(--ant-color-bg-container);
    border-radius: var(--ant-border-radius-lg);

    display: flex;
    flex-direction: column;
    gap: ${({ isMobile }) => (isMobile ? '2px' : '16px')};
`;

export const StyledMainCards = styled(Layout.Content)<{ isMobile: boolean }>`
    display: flex;
    justify-content: center;
    align-items: center;
    margin: ${({ isMobile }) => (isMobile ? '8px' : '16px')};
`;

export const FlexFullWidth = styled(Flex)<FlexProps>`
    width: 100%;
`;

export const StyledFooterLayout = styled(Layout.Footer)<{ isMobile: boolean }>`
    width: 100%;
    padding: ${({ isMobile }) => (isMobile ? '12px 24px' : 'var(--ant-padding-xl)')};
`;