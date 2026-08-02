-- Development credentials shared with the hubscore development seed.
-- admin@gmail.com / admin
-- user@gmail.com / user
INSERT INTO public.identity_user (id, email, password_hash, enabled)
VALUES ('f027e5f9-31a1-42ac-90b3-dc437585b839', 'admin@gmail.com',
        '$2y$10$aiZniPufX3F4eUAQTseNLeAWClxcp5u.rauU2RVf2c3kW8x/oeSHG', TRUE);

INSERT INTO public.identity_user (id, email, password_hash, enabled)
VALUES ('dc437585-98ea-4efe-90b3-b83f027e5f99', 'user@gmail.com',
        '$2y$10$VkLzPdzANN51bnn8oLn74.ynGE6uQe7pDJtZgy64SP18Pev7iQjX.', TRUE);

INSERT INTO public.invitation_token (token, player_id, expires_at)
VALUES ('dev-invitation-token', '3b0fbccc-ec60-48b5-a3bd-ce8cac6a6648', CURRENT_TIMESTAMP + INTERVAL '30 days');
