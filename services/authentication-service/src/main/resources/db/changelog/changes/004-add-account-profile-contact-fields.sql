--liquibase formatted sql
--changeset flashsale:authentication-004-account-profile-contact-fields

ALTER TABLE users
    ADD COLUMN full_name VARCHAR(150),
    ADD COLUMN phone VARCHAR(32),
    ADD COLUMN address VARCHAR(500);

--rollback ALTER TABLE users DROP COLUMN address, DROP COLUMN phone, DROP COLUMN full_name;
