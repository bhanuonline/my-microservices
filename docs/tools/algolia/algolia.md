Account :
https://dashboard.algolia.com/apps/ZZLM5LNV17/explorer/browse/categories?searchMode=search
bhanu.mmmec2013@gmail.com
Algolia is a Search-as-a-Service platform.You give it your data.

The 3 core concepts you need to know
1. 📦 Index
   An index is where your searchable data lives inside Algolia. Think of it like a table in a database, but optimized purely for search.
   Your products database  →  copy relevant fields  →  Algolia Index
2. 📄 Record
      A record is one item inside an index — like one product, one article, one user. It's just a JSON object.
      json{
      "name": "Nike Air Max 90",
      "color": "blue",
      "price": 120,
      "brand": "Nike"
      }
3. 🔎 Query
      A query is what the user types in the search box. Algolia takes that and returns matching records in milliseconds.

How does it work? (Big picture)
1. You push your data → Algolia Index
2. User types in search box → Query sent to Algolia
3. Algolia returns results → You display them

Step 1 — Setting Up Algolia & Creating Your First Index 🛒
1. Create a free account
2. Create an Application
3. Create your first Index
4. Grab your API Keys
   Application IDIdentifies your app — used in every request
   Search-Only API KeyUsed in your frontend (safe to expose)
   Admin API KeyUsed in your backend to push/delete data — never expose this publicly
5. What your product records will look like
   Before pushing data, let's think about what a product record looks like for your shop. Algolia stores records as JSON:
   {
   "objectID": "prod-001",
   "name": "Nike Air Max 90",
   "brand": "Nike",
   "category": "Sneakers",
   "color": "Blue",
   "price": 120,
   "rating": 4.5,
   "in_stock": true,
   "image_url": "https://..."
   }
Step 2 — Pushing Product Data into Algolia with Java + Spring Boot 🚀
1. Create a Spring Boot Project
2. Add the Algolia Java Client dependency
   <dependency>
   <groupId>com.algolia</groupId>
   <artifactId>algoliasearch</artifactId>
   <version>4.8.0</version>
   </dependency>
   mvn clean install
3. Add your API Keys to application.properties
   algolia.application-id=YOUR_APP_ID
   algolia.api-key=YOUR_ADMIN_API_KEY
   algolia.index-name=products
   🔒 Never hardcode API keys directly in Java classes. Always use properties files (and never commit your Admin key to GitHub — add .env or use environment variables in production).
4. Create the Product model
5. Create the Algolia Service
6. Create a Controller to trigger the push
7. Run & Test

Step 3 — Searching Products from Spring Boot 🔍
1. Add the search method to AlgoliaService.java
4. Understanding what Algolia returns
   SearchResponse
   ├── hits          → the matching records (your products)
   ├── nbHits        → total number of results found
   ├── page          → current page (for pagination)
   ├── nbPages       → total pages
   ├── hitsPerPage   → results per page (default: 20)
   ├── processingTimeMS → how fast the search was (usually <10ms!)
   └── query         → the original search term

How Algolia pagination worksAlgolia uses two parameters:
Parameter   What it means                                                         Default
page        Which page you want (starts from 0)                                    0
hitsPerPage How many results per page20
So if you have 100 products and hitsPerPage = 10:
Page 0 → products 1–10
Page 1 → products 11–20
Page 2 → products 21–30
...
Page 9 → products 91–100
1. Create a SearchResult response wrapper SearchResult.java:
2. Update AlgoliaService.java — add paginated search
3. Update ProductController.java — add paginated endpoint

Visual — how pages flow
User searches "sneakers"
↓
Algolia finds 4 results
↓
┌─────────────────────────┐
│  Page 0  (hitsPerPage=2)│
│  • Nike Air Max 90      │
│  • Adidas Ultraboost    │
└─────────────────────────┘
↓ user clicks "Next"
┌─────────────────────────┐
│  Page 1  (hitsPerPage=2)│
│  • Puma RS-X            │
│  • Reebok Classic       │
└─────────────────────────┘

⚠️ How sorting works in Algolia (key concept)In a database you just do ORDER BY price ASC. In Algolia, you can't do that directly.Instead, Algolia uses Replica Indices for sorting.Think of it like this:Main Index: "products"          ← default relevance ranking
├── Replica: "products_price_asc"    ← sorted by price low→high
├── Replica: "products_price_desc"   ← sorted by price high→low
└── Replica: "products_rating_desc"  ← sorted by rating high→lowEach replica is a copy of your main index, but with a different sort order. When the user wants to sort, you just search a different replica.
Each replica is a copy of your main index, but with a different sort order. When the user wants to sort, you just search a different replica.

Step 1 — Create Replica Indices in Algolia Dashboard

Go to Algolia Dashboard → Indices → products
Click "Configuration" tab
Scroll to "Replicas" section
Click "Add Replica" and create these three:

Replica Name                Sort by
products_price_asc      price ascending
products_price_desc     price descending
products_rating_desc    rating descending

Step 2 — Configure ranking for each replica
Now set what each replica sorts by:
For products_price_asc:

Click on the replica index
Go to Configuration → Ranking and Sorting
Click "Add sort-by attribute"
Add: asc(price)
Save ✅

For products_price_desc:
Add: desc(price) ✅

For products_rating_desc:
Add: desc(rating) ✅

Step 3 — Update application.properties
Add the replica names:
propertiesalgolia.application-id=YOUR_APP_ID
algolia.api-key=YOUR_ADMIN_API_KEY
algolia.index-name=products

# Replicas for sorting
algolia.index-price-asc=products_price_asc
algolia.index-price-desc=products_price_desc
algolia.index-rating-desc=products_rating_desc

Step 4 — Create a SortOption enum
Create SortOption.java — this makes your code clean and safe:

Full picture — how everything connects
User Request
↓
/search-sorted?query=sneakers&sort=PRICE_ASC&page=0&hitsPerPage=2
↓
Controller → AlgoliaService
↓
resolveIndex(PRICE_ASC) → "products_price_asc"
↓
Search replica index with page & hitsPerPage
↓
Algolia returns sorted + paginated results
↓
SearchResult { products, totalResults, currentPage, totalPages }
↓
JSON response to client