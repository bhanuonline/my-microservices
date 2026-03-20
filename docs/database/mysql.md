1: How to use group wise ?

**Q: Database Connection Pooling?**

Connection Pooling is a technique used to manage and reuse database connections efficiently.
Instead of opening and closing a new database connection for every request, a "pool" of pre-established connections is maintained.

Establishing a new database connection is a relatively expensive operation in terms of:

* Network latency
* Authentication
* Resource allocation

Pooling helps:

* Reduce latency (connections are already open)
* Minimize resource usage on both the client and server
* Improve throughput and scalability

Java conection pool lib: HikariCP, C3P0, Apache DBCP

Key Parameters

| Parameter           | Meaning                                                 |
| ------------------- | ------------------------------------------------------- |
| `maxPoolSize`       | Maximum number of connections in the pool.              |
| `minIdle`           | Minimum idle connections kept in pool.                  |
| `maxIdle`           | Maximum idle connections allowed.                       |
| `connectionTimeout` | Max time to wait for a free connection.                 |
| `idleTimeout`       | Time after which an idle connection is removed.         |
| `maxLifetime`       | Maximum lifetime of a connection before being replaced. |


Ex : DB handling 200 requests/min, a pool of 10–20 connections is ok

Note :
Connection pooling for MySQL is almost always configured at the client side or application layer, not inside the MySQL server itself.


Master And Slave database :
Source (Primary) server – handles reads/writes and produces binary logs.
Replica (Secondary) server – copies changes from the source and can respond to read requests.
Types of Replication
Synchronous replication:
ASynchronous replication: will use this one

which mysql
mysql --help | grep -A 1 "Default options"
/usr/local/mysql/bin/mysql --help | grep -A 1 "Default options"

For restart need full path
sudo /usr/local/mysql/support-files/mysql.server restart
make the sort :
sudo ln -s /usr/local/mysql/support-files/mysql.server /usr/local/bin/mysql.server
then run
sudo mysql.server restart

#docker
docker run -d \
--name mysql-slave \
-e MYSQL_ROOT_PASSWORD=rootpass \
--server-id=2 \
-p 3307:3306 \
mysql:8.0

🔁 How MySQL Replication Works

In MySQL:
Master writes data
Master writes changes to binary log (binlog)
Replica reads binlog
Replica applies same changes
Replica stays synchronized

⚠️ Important Production Concept
Replication is usually:
Asynchronous (slight delay possible)
Eventually consistent

Next important concept:
What happens if replica lags?
What if replica goes down?
How to fallback to master?
How transactions behave in read/write split?
Semi-synchronous replication
GTID-based replication
Failover handling
Auto switch to master if replica fails
Proxy layer (ProxySQL)
Production banking-grade HA setup
How to design SaaS DB schema


### Replica setup with Docker :

🏗️ Architecture
Spring App
↓
MASTER (3306)  → binlog →  REPLICA (3307)

You must use:
SOURCE instead of MASTER
REPLICA instead of SLAVE
🟢 MySQL Master (port 3307)
🔵 MySQL Replica(SLAVE) (port 3308)
SELECT VERSION();
SHOW VARIABLES LIKE 'log_bin';
SHOW BINARY LOG STATUS;(8.4 version)
✅ Asynchronous replication
🚀 STEP 1 — Create Docker Network
This allows containers to talk internally.
docker network create mysql-net

🚀 STEP 2 — Start MASTER Container
docker run -d \
--name mysql-master \
--network mysql-net \
-p 3307:3306 \
-e MYSQL_ROOT_PASSWORD=root \
-e MYSQL_DATABASE=testdb \
mysql:8.4 \
--server-id=1 \
--log-bin=mysql-bin \
--binlog-format=ROW

| Host Port | Container Port |
| --------- | -------------- |
| 3307      | 3306           |

From your Mac → you connect using 3307
From replica container → it connects to master container using 3306

test connection
mysql -h 127.0.0.1 -P 3307 -u root -p

🚀 STEP 3 — Create Replication User (On MASTER)

Inside master:
CREATE USER 'repl_user'@'%' IDENTIFIED BY 'repl_pass';
GRANT REPLICATION SLAVE ON *.* TO 'repl_user'@'%';
FLUSH PRIVILEGES;

CREATE USER 'repl_user'@'%'
IDENTIFIED WITH mysql_native_password
BY 'repl_pass';
GRANT REPLICATION SLAVE ON *.* TO 'repl_user'@'%';
FLUSH PRIVILEGES;

DROP USER IF EXISTS 'repl_user'@'%';

CREATE USER 'repl_user'@'%' IDENTIFIED WITH caching_sha2_password BY 'repl_pass';
GRANT REPLICATION SLAVE ON *.* TO 'repl_user'@'%';
FLUSH PRIVILEGES;
SELECT user, host, plugin
FROM mysql.user
WHERE user='repl_user';

SHOW MASTER STATUS;

🚀 STEP 4 — Start REPLICA Container
docker run -d \
--name mysql-replica \
--network mysql-net \
-p 3308:3306 \
-e MYSQL_ROOT_PASSWORD=root \
mysql:8.4 \
--server-id=2 \
--relay-log=relay-log

🚀 STEP 5 — Configure Replica To Follow Master
Login to replica:
mysql -h 127.0.0.1 -P 3307 -u root -p

✅ 1️⃣ Login into Replica Container / Server
docker exec -it mysql-replica mysql -uroot -proot

Now run (replace file & position):
STOP REPLICA;
RESET REPLICA ALL;

this command need to run oni replica:
CHANGE REPLICATION SOURCE TO
SOURCE_HOST='mysql-master',
SOURCE_PORT=3306,
SOURCE_USER='repl_user',
SOURCE_PASSWORD='repl_pass',
SOURCE_LOG_FILE='mysql-bin.000003',
SOURCE_LOG_POS=1799,
GET_SOURCE_PUBLIC_KEY=1,
SOURCE_SSL=0;

START REPLICA;
RESET REPLICA ALL;
SHOW REPLICA STATUS\G

Replica_IO_Running: Yes
Replica_SQL_Running: Yes

Mac
|
|-- localhost:3307 → mysql-master (container port 3306)
|
|-- localhost:3308 → mysql-replica (container port 3306)
mysql-replica  →  mysql-master:3306
docker exec -it mysql-replica bash
docker rm mysql-replica
docker volume ls
docker ps
docker inspect mysql-replica
docker inspect -f '{{ range .Mounts }}{{ .Name }}{{ end }}' mysql-replica
docker volume prune
docker volume rm volume_name
docker ps -a

SELECT @@server_uuid;
SHOW VARIABLES LIKE 'log_bin';
in case of ant error:
Last_IO_Error:
Last_SQL_Error:

test :
USE testdb;

CREATE TABLE users (
id INT PRIMARY KEY AUTO_INCREMENT,
name VARCHAR(50)
);

INSERT INTO users(name) VALUES('Bhanu');

Multi-tenant ≠ Replication
