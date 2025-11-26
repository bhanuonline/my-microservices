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

