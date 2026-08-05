\restrict zHEdlcSG5rbdAy1NHiL3RC2Xrb6ZtZUsuPX6bihwwtolFqnL835CrzD9whneF18
CREATE TABLE public.follows (
    follower_id uuid NOT NULL,
    following_id uuid NOT NULL,
    created_at timestamp without time zone NOT NULL
);
CREATE TABLE public.user_profiles (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    display_name character varying(255) NOT NULL,
    avatar_url character varying(500),
    bio text,
    website character varying(500),
    location character varying(255),
    company character varying(255),
    birthday timestamp without time zone,
    view_count integer DEFAULT 0 NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);
CREATE TABLE public.user_stats (
    user_id uuid NOT NULL,
    post_count integer DEFAULT 0 NOT NULL,
    follower_count integer DEFAULT 0 NOT NULL,
    following_count integer DEFAULT 0 NOT NULL,
    like_count integer DEFAULT 0 NOT NULL,
    updated_at timestamp without time zone NOT NULL
);
CREATE TABLE public.users (
    id uuid NOT NULL,
    username character varying(255) NOT NULL,
    email character varying(255),
    password_hash character varying(255),
    avatar_url character varying(500),
    status character varying(50) DEFAULT 'ACTIVE'::character varying NOT NULL,
    two_fa_enabled boolean DEFAULT false NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);
ALTER TABLE ONLY public.follows
    ADD CONSTRAINT follows_pkey PRIMARY KEY (follower_id, following_id);
ALTER TABLE ONLY public.user_profiles
    ADD CONSTRAINT user_profiles_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.user_profiles
    ADD CONSTRAINT user_profiles_user_id_key UNIQUE (user_id);
ALTER TABLE ONLY public.user_stats
    ADD CONSTRAINT user_stats_pkey PRIMARY KEY (user_id);
-- email 改为部分唯一索引：允许多个 NULL（社交/手机号用户无邮箱），非 NULL 仍唯一。
CREATE UNIQUE INDEX users_email_unique ON public.users USING btree (email) WHERE email IS NOT NULL;
ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_username_key UNIQUE (username);
CREATE INDEX idx_follows_created_at ON public.follows USING btree (created_at DESC);
CREATE INDEX idx_follows_follower_id ON public.follows USING btree (follower_id);
CREATE INDEX idx_follows_following_id ON public.follows USING btree (following_id);
CREATE INDEX idx_user_profiles_display_name_gin ON public.user_profiles USING gin (to_tsvector('simple'::regconfig, (display_name)::text));
CREATE INDEX idx_user_profiles_user_id ON public.user_profiles USING btree (user_id);
CREATE INDEX idx_user_profiles_view_count ON public.user_profiles USING btree (view_count DESC);
ALTER TABLE ONLY public.follows
    ADD CONSTRAINT follows_follower_id_fkey FOREIGN KEY (follower_id) REFERENCES public.users(id) ON DELETE CASCADE;
ALTER TABLE ONLY public.follows
    ADD CONSTRAINT follows_following_id_fkey FOREIGN KEY (following_id) REFERENCES public.users(id) ON DELETE CASCADE;
ALTER TABLE ONLY public.user_profiles
    ADD CONSTRAINT user_profiles_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;
ALTER TABLE ONLY public.user_stats
    ADD CONSTRAINT user_stats_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;
\unrestrict zHEdlcSG5rbdAy1NHiL3RC2Xrb6ZtZUsuPX6bihwwtolFqnL835CrzD9whneF18
