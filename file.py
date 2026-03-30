from datetime import datetime
import os

# Define your folder path
folder_path = "/Users/bhanupratap/Documents/my/my-microservices/docs/daytracker"

# Get current date in YYYY-MM-DD format
today_date = datetime.now().strftime("%Y-%m-%d")

# Create file name
file_name = f"date_{today_date}.md"

# Full path
full_path = os.path.join(folder_path, file_name)

# Create the file
with open(full_path, "w") as f:
    f.write(f"## {today_date} ##\n")

print(f"File created: {full_path}")