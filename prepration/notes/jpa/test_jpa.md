Why is orders_seq created?
Hibernate needs a way to generate unique IDs. Depending on the database and configuration, it may create a sequence table:
orders_seq
-----------
next_val