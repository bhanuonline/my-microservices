show databases;

brew list mysql
or which mysql
ls -l /etc/my.cnf[check file is exist or not ]
sudo nano /etc/my.cnf [if not create it ]

sudo nano /etc/my.cnf
[mysqld]
server-id=1
log-bin=mysql-bin
binlog-format=ROW
✅ Step 3 — Restart MySQL

sudo /usr/local/mysql/support-files/mysql.server restart

✅ Step 4 — Verify Binary Log Enabled
mysql -u root -p
SHOW VARIABLES LIKE 'log_bin';


docker run --name mysql-master -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root -d mysql
docker run --name mysql-replica -p 3307:3306 -e MYSQL_ROOT_PASSWORD=root -d mysql