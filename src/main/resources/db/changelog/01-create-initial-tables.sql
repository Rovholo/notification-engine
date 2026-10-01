--liquibase formatted sql

--changeset brendan:001-create-users
CREATE TABLE users (
                       id VARCHAR(36) CHARACTER SET ascii NOT NULL,
                       name VARCHAR(65) NOT NULL,
                       surname VARCHAR(256) NOT NULL,
                       email VARCHAR(255) NOT NULL,
                       cell VARCHAR(20),
                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                       PRIMARY KEY (id)
);

--changeset brendan:002-create-credential
CREATE TABLE credential (
                            id VARCHAR(36) CHARACTER SET ascii NOT NULL,
                            username VARCHAR(65) NOT NULL,
                            password VARCHAR(65) NOT NULL,
                            user_id INT UNSIGNED NOT NULL,
                            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                            PRIMARY KEY (id),
                            CONSTRAINT uk_credential_username UNIQUE (username),
                            CONSTRAINT fk_credential_user
                                FOREIGN KEY (user_id)
                                    REFERENCES users(id)
);

--changeset brendan:003-create-user-firebase-auth
CREATE TABLE user_firebase_auth (
                                    id VARCHAR(36) CHARACTER SET ascii NOT NULL,
                                    user_id VARCHAR(36) NOT NULL,
                                    firebase_uid VARCHAR(128) NOT NULL,
                                    firebase_email VARCHAR(255),
                                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                    PRIMARY KEY (id),
                                    CONSTRAINT uk_firebase_uid UNIQUE (firebase_uid),
                                    CONSTRAINT uk_firebase_user UNIQUE (user_id),
                                    CONSTRAINT fk_firebase_user
                                        FOREIGN KEY (user_id)
                                            REFERENCES users(id)
);

--changeset brendan:004-create-houses
CREATE TABLE houses (
                        id         VARCHAR(36) CHARACTER SET ascii NOT NULL,
                        name       VARCHAR(255) NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,
                        PRIMARY KEY (id)
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
                         created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
                         updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,
                         PRIMARY KEY (id),
                         CONSTRAINT fk_devices_house FOREIGN KEY (house_id) REFERENCES houses (id) ON DELETE CASCADE
);

--rollback DROP TABLE devices;
--rollback DROP TABLE house_residents;
--rollback DROP TABLE house_owners;
--rollback DROP TABLE houses;
