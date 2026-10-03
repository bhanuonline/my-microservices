CREATE TABLE app_user_role (
    user_id BIGINT      NOT NULL,
    role    VARCHAR(64) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_app_user_role_user
        FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);
