Ques1:
You're designing a URL shortener (like bit.ly) expected to handle 100M new URLs per day and 10x that in reads. 
Walk me through your high-level architecture: how do you generate short codes, what does your database schema look like, and how do you handle the read-heavy traffic at scale?

What Snowflake actually is:
It's an algorithm / ID format originally created by Twitter (around 2010) to solve one specific problem: how do you generate unique IDs across many servers, at high speed, without those servers talking to each other?

The core problem it solves
Imagine you have 5 app servers all creating short URLs at the same time. If you used a simple auto-incrementing counter (like 1, 2, 3, 4...), all 5 servers would need to ask one central database "what's the next number?" — that database becomes a bottleneck and a single point of failure at high scale.
Snowflake's insight: combine a timestamp + a unique machine identity + a local counter, so each server can generate unique IDs completely on its own, with zero coordination.

How it works
[1 bit unused] [41 bits timestamp] [10 bits machine ID] [12 bits sequence]

Ex:
Server A (machine_id = 1)  →  runs generateSnowflakeId()  →  IDs: ...001, ...002...
Server B (machine_id = 2)  →  runs generateSnowflakeId()  →  IDs: ...001, ...002...
Server C (machine_id = 3)  →  runs generateSnowflakeId()  →  IDs: ...001, ...002...

Sharding = splitting one big database into many smaller databases
The core problem: you have one database server. It has finite CPU, memory, disk, and network capacity. As your data grows (100M new rows/day, billions over time) and traffic grows (thousands of requests/sec), a single machine eventually can't keep up — no matter how powerful, there's a ceiling.
Sharding is the solution: instead of one giant database holding all your data, you split your data across multiple separate database instances (each a full, independent database, often on a different physical/virtual machine). Each of these smaller databases is called a shard.

one DB:
┌─────────────────┐
All 100M rows/day → │   ONE Database   │  ← everything lives here
│  (all URLs)      │
└─────────────────┘

sharding (say, 4 shards):
┌─────────────┐
25% of URLs  →   │  Shard 1    │
└─────────────┘
┌─────────────┐
25% of URLs  →   │  Shard 2    │
└─────────────┘
┌─────────────┐
25% of URLs  →   │  Shard 3    │
└─────────────┘
┌─────────────┐
25% of URLs  →   │  Shard 4    │
└─────────────┘

Multiple schemas in one DB instance:
This is typically used for logical separation — e.g., separating tables by feature or tenant (like analytics schema vs billing schema, or one schema per customer in a multi-tenant app). But it's still one machine, one set of CPU/RAM/disk resources shared by everything inside it.

Sharding (multiple DB instances):
┌───────────────┐    ┌───────────────┐
│  DB Server 1   │    │  DB Server 2   │
│ (own machine,  │    │ (own machine,  │
│  own CPU/RAM)  │    │  own CPU/RAM)  │
│                │    │                │
│  short_codes   │    │  short_codes   │
│  A–M           │    │  N–Z           │
└───────────────┘    └───────────────┘

key distinction to hold onto
| Aspect                                              | Multiple Schemas                                            | Sharding                                                        |
| --------------------------------------------------- | ----------------------------------------------------------- | --------------------------------------------------------------- |
| **Machines**                                        | 1                                                           | N (multiple)                                                    |
| **Purpose**                                         | Organize/separate data logically                            | Scale out capacity (more storage, more throughput)              |
| **Same table structure repeated?**                  | No — usually different tables or different logical purposes | Yes — same schema, different rows are distributed across shards |
| **Solves "too much traffic/data for one machine"?** | No                                                          | Yes                                                             |

So to directly answer: sharding means different machines, each running its own full database instance, each holding a slice of the rows from what would otherwise be one giant table.

Agenda
Here's how we'll approach setting this up locally, step by step:

Environment setup — spin up multiple database instances locally (using Docker, so each shard is isolated, simulating "different machines" on your one laptop)
Schema design — create the same urls table structure in each shard
Java project setup — Spring Boot app with multiple DataSource configs (one per shard)
Sharding/routing logic — implement the consistent hashing router we discussed, wire it into the app
Write path — test inserting a short_code, confirm it lands on the correct shard
Read path — test looking up a short_code, confirm it's found on the same shard it was routed to
Verification — manually inspect each DB to confirm data really is split (not all landing on shard 1)
(Optional) Simulate adding a shard — see how consistent hashing minimizes data movement, vs. naive hash % N

Design:
Your Laptop
│
├── Docker Container: postgres-shard-1  (port 5433)
├── Docker Container: postgres-shard-2  (port 5434)
├── Docker Container: postgres-shard-3  (port 5435)
├── Docker Container: postgres-shard-4  (port 5436)
│
└── Java Spring Boot App
├── ShardRouter (consistent hashing logic)
├── DataSource per shard (4 connection pools)
├── UrlService
│     ├── createShortUrl(longUrl) → generate ID → hash → pick shard → INSERT
│     └── getLongUrl(shortCode)   → hash → pick shard → SELECT
└── REST Controller (test endpoints)

Step 1:
install 4 db using docker
docker-compose up -d
docker ps
docker exec -it mysql-shard-1 mysql -uroot -proot urlshortener

                Your Spring Boot App
                        |
        ---------------------------------
        |        |        |            |
        |        |        |            |
     3311     3312      3313        3314
        |        |        |            |
+---------+ +---------+ +---------+ +---------+
|Shard 1  | |Shard 2  | |Shard 3  | |Shard 4  |
| MySQL   | | MySQL   | | MySQL   | | MySQL   |
+---------+ +---------+ +---------+ +---------+

step 2:
docker exec -it mysql-shard-1 mysql -uroot -proot urlshortener -e "CREATE TABLE IF NOT EXISTS urls (short_code VARCHAR(10) PRIMARY KEY, long_url TEXT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);"

docker exec -it mysql-shard-2 mysql -uroot -proot urlshortener -e "CREATE TABLE IF NOT EXISTS urls (short_code VARCHAR(10) PRIMARY KEY, long_url TEXT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);"

docker exec -it mysql-shard-3 mysql -uroot -proot urlshortener -e "CREATE TABLE IF NOT EXISTS urls (short_code VARCHAR(10) PRIMARY KEY, long_url TEXT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);"

docker exec -it mysql-shard-4 mysql -uroot -proot urlshortener -e "CREATE TABLE IF NOT EXISTS urls (short_code VARCHAR(10) PRIMARY KEY, long_url TEXT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);"

docker exec -it mysql-shard-1 mysql -uroot -proot urlshortener -e "DESCRIBE urls;"

testing :
curl -X POST http://localhost:8080/api/urls \
-H "Content-Type: application/json" \
-d '{"longUrl": "https://example.com/some/very/long/path"}'
curl -i http://localhost:8080/api/urls/ORZl76aemW
Load test 
brew install k6