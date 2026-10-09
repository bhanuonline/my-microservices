-- =====================================================================
-- mysql-shared init script — runs ONCE on first boot of a fresh volume.
-- MySQL image executes any .sql file in /docker-entrypoint-initdb.d/.
--
-- MYSQL_DATABASE in docker-compose creates ONE schema (userdb). This
-- script creates the other three plus any auxiliary schemas needed.
-- Idempotent (IF NOT EXISTS) so re-runs are safe.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS userdb
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS productdb
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS authdb
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS paymentdb
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- auth-server's jdbc profile uses a separate schema (authdb_jdbc).
-- Services already set createDatabaseIfNotExist=true in their JDBC URLs,
-- so this is belt-and-suspenders.
CREATE DATABASE IF NOT EXISTS authdb_jdbc
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Grant root all privileges on every schema (root already has these
-- globally in dev, but declaring explicitly makes the intent clear).
GRANT ALL PRIVILEGES ON userdb.*     TO 'root'@'%';
GRANT ALL PRIVILEGES ON productdb.*  TO 'root'@'%';
GRANT ALL PRIVILEGES ON authdb.*     TO 'root'@'%';
GRANT ALL PRIVILEGES ON authdb_jdbc.* TO 'root'@'%';
GRANT ALL PRIVILEGES ON paymentdb.*  TO 'root'@'%';
FLUSH PRIVILEGES;
