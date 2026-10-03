--liquibase formatted sql

--changeset brendan:001-create-users
CREATE TABLE users (
    id         VARCHAR(36) CHARACTER SET ascii NOT NULL,
    name       VARCHAR(65)  NOT NULL,
    surname    VARCHAR(256) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    cell       VARCHAR(20),
    updated_by VARCHAR(36) CHARACTER SET ascii NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT fk_users_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
);

--changeset brendan:002-create-credential
CREATE TABLE credential (
    id                    VARCHAR(36) CHARACTER SET ascii NOT NULL,
    username              VARCHAR(65) NOT NULL,
    password              VARCHAR(65) NOT NULL,
    user_id               VARCHAR(36) CHARACTER SET ascii NOT NULL,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until          DATETIME(6) NULL,
    updated_by            VARCHAR(36) CHARACTER SET ascii NULL,
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_credential_username UNIQUE (username),
    CONSTRAINT uk_credential_user UNIQUE (user_id),
    CONSTRAINT fk_credential_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_credential_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
);

--changeset brendan:003-create-user-firebase-auth
CREATE TABLE user_firebase_auth (
    id             VARCHAR(36) CHARACTER SET ascii NOT NULL,
    user_id        VARCHAR(36) NOT NULL,
    firebase_uid   VARCHAR(128) NOT NULL,
    firebase_email VARCHAR(255),
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_firebase_uid UNIQUE (firebase_uid),
    CONSTRAINT uk_firebase_user UNIQUE (user_id),
    CONSTRAINT fk_firebase_user FOREIGN KEY (user_id) REFERENCES users (id)
);

--changeset brendan:004-create-houses
CREATE TABLE houses (
    id         VARCHAR(36) CHARACTER SET ascii NOT NULL,
    name       VARCHAR(255) NOT NULL,
    updated_by VARCHAR(36) CHARACTER SET ascii NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_houses_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
);

CREATE TABLE house_owners (
    house_id VARCHAR(36) CHARACTER SET ascii NOT NULL,
    user_id  VARCHAR(36) CHARACTER SET ascii NOT NULL,
    PRIMARY KEY (house_id, user_id),
    CONSTRAINT fk_house_owners_house FOREIGN KEY (house_id) REFERENCES houses (id) ON DELETE CASCADE,
    CONSTRAINT fk_house_owners_user  FOREIGN KEY (user_id)  REFERENCES users (id)  ON DELETE CASCADE
);

CREATE TABLE house_residents (
    house_id VARCHAR(36) CHARACTER SET ascii NOT NULL,
    user_id  VARCHAR(36) CHARACTER SET ascii NOT NULL,
    PRIMARY KEY (house_id, user_id),
    CONSTRAINT fk_house_residents_house FOREIGN KEY (house_id) REFERENCES houses (id) ON DELETE CASCADE,
    CONSTRAINT fk_house_residents_user  FOREIGN KEY (user_id)  REFERENCES users (id)  ON DELETE CASCADE
);

CREATE TABLE devices (
    id         VARCHAR(36) CHARACTER SET ascii NOT NULL,
    house_id   VARCHAR(36) CHARACTER SET ascii NOT NULL,
    name       VARCHAR(255) NOT NULL,
    type       VARCHAR(50)  NOT NULL,
    status     VARCHAR(50),
    updated_by VARCHAR(36) CHARACTER SET ascii NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_devices_house FOREIGN KEY (house_id) REFERENCES houses (id) ON DELETE CASCADE,
    CONSTRAINT fk_devices_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
);

--rollback DROP TABLE devices;
--rollback DROP TABLE house_residents;
--rollback DROP TABLE house_owners;
--rollback DROP TABLE houses;

--changeset brendan:005-create-brokers
CREATE TABLE brokers (
    id         VARCHAR(36) CHARACTER SET ascii NOT NULL,
    name       VARCHAR(255) NOT NULL,
    username   VARCHAR(65)  NOT NULL,
    password   VARCHAR(65)  NOT NULL,
    server     VARCHAR(255) NOT NULL,
    secure     BOOLEAN      NOT NULL,
    updated_by VARCHAR(36) CHARACTER SET ascii NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_brokers_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_brokers_name ON brokers (name);

--rollback DROP TABLE brokers;

--changeset brendan:006-create-refresh-token
CREATE TABLE refresh_token (
    id         VARCHAR(36) CHARACTER SET ascii NOT NULL,
    user_id    VARCHAR(36) CHARACTER SET ascii NOT NULL,
    token_hash VARCHAR(64) CHARACTER SET ascii NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

--rollback DROP TABLE refresh_token;

--changeset brendan:007-create-password-reset-code
CREATE TABLE password_reset_code (
    id         VARCHAR(36) CHARACTER SET ascii NOT NULL,
    user_id    VARCHAR(36) CHARACTER SET ascii NOT NULL,
    code_hash  VARCHAR(65) NOT NULL,
    attempts   INT NOT NULL DEFAULT 0,
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_password_reset_code_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

--rollback DROP TABLE password_reset_code;

--changeset brendan:008-create-user-role
CREATE TABLE user_role (
                           user_id VARCHAR(36) CHARACTER SET ascii NOT NULL,
                           role    VARCHAR(32) NOT NULL,
                           PRIMARY KEY (user_id, role),
                           CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

--rollback DROP TABLE user_role;