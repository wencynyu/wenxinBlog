\restrict zfCCTv8R19cp8qBi84E90UvDZetvEdwYE3NHSbsHpr3EdPuXuSPcm42G88mAmc0
CREATE TABLE public.users (
    id character varying(36) NOT NULL,
    username character varying(50) NOT NULL,
    email character varying(255) NOT NULL,
    password_hash character varying(255) NOT NULL,
    avatar_url character varying(500) DEFAULT ''::character varying,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    two_fa_enabled boolean DEFAULT false NOT NULL,
    two_fa_secret character varying(255) DEFAULT ''::character varying,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);
ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);
ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_username_key UNIQUE (username);
\unrestrict zfCCTv8R19cp8qBi84E90UvDZetvEdwYE3NHSbsHpr3EdPuXuSPcm42G88mAmc0
