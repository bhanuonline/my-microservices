import re
import csv

# Read the raw file
with open("csv.txt", encoding="utf-8") as f:
    data = f.read()

# Pattern for header info
order_pattern = re.compile(
    r"Order code: (\d+), Order Placed Time: ([^,]+), Language: (\w+), User Info: UID: ([^,]+), Name: ([^,]+), Type: (\w+),Location: ([^,]+), Bucket Size: (\d+), Payment: ([^ ]+) \(Card Type: ([^)]*)\), Sales Application: ([^,]+), Promotions: ([^\n]+)"
)

# Pattern for products
product_pattern = re.compile(
    r"Product: (\d+), Category Hierarchy: ([^\n]+)"
)

rows = []

# Split file by orders
orders = data.strip().split("-----------------------------------")
for order_block in orders:
    order_block = order_block.strip()
    if not order_block:
        continue

    order_match = order_pattern.search(order_block)
    if not order_match:
        continue

    order_code, placed_time, lang, email, name, user_type, location, bucket_size, payment, card_type, app, promotions = order_match.groups()

    product_matches = product_pattern.findall(order_block)

    for pid, category in product_matches:
        rows.append([
            order_code,
            placed_time.strip(),
            lang.strip(),
            email.strip(),
            name.strip(),
            user_type.strip(),
            location.strip(),
            bucket_size.strip(),
            payment.strip(),
            card_type.strip(),
            app.strip(),
            promotions.strip(),
            pid.strip(),
            category.strip()
        ])

# Output CSV
with open("flattened_orders.csv", "w", newline='', encoding="utf-8") as out_csv:
    writer = csv.writer(out_csv)
    writer.writerow([
        "OrderCode",
        "OrderPlacedTime",
        "Language",
        "Email",
        "Name",
        "UserType",
        "Location",
        "BucketSize",
        "PaymentType",
        "CardType",
        "SalesApplication",
        "Promotions",
        "ProductID",
        "CategoryHierarchy"
    ])
    writer.writerows(rows)

print(f"Done! {len(rows)} rows written to flattened_orders.csv")