self join ?

SQL Optimization
“Optimize by indexing filter/group columns, avoiding full table scans, using EXPLAIN, and pre-aggregating frequently used data.”

SELECT *
FROM orders
WHERE order_date BETWEEN DATE_SUB(CURDATE(), INTERVAL 7 DAY) AND CURDATE();

SELECT *
FROM orders o
WHERE createdTS BETWEEN '2026-04-25 00:00:00'
AND '2026-04-30 23:59:59';