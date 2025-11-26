MySQL Server Configuration:

max_connections – Maximum number of client connections MySQL allows.
SHOW VARIABLES LIKE 'max_connections';
SET GLOBAL max_connections = 500;  -- example

This should be larger than your application pool size * number of applications connecting to the DB.

wait_timeout – Seconds before an idle connection is closed by the server.

SHOW VARIABLES LIKE 'wait_timeout';
SET GLOBAL wait_timeout = 300;  -- 5 minutes