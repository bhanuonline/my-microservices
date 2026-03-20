# Lists — ordered collections of data
scores = [92, 87, 45, 100, 67]
#print(scores[0])      # 92 (first item)
#print(scores[-1])     # 67 (last item)
#print(len(scores))    # 5
#print(scores)

# Dictionaries — data with labels (like a mini dataset row)
person = {"age": 25, "salary": 50000, "churned": True}
#print(person["age"])  # 25


# Regular loop
#for score in scores:
    #print(score)

# List comprehension — very common in AI/data work
#passing = [score for sctraore in scores if score >= 60]
#print(passing)  # [92, 87, 100, 67]

def normalize(value, min_val, max_val):
    return (value - min_val) / (max_val - min_val)

# Normalizing data (0 to 1) is extremely common in AI
#print(normalize(87, 45, 100))  # 0.76

import numpy as np

# NumPy array — like a list but much more powerful
arr = np.array([92, 87, 45, 100, 67])
arr1=np.array([1,2,3,4,5])
#print(arr1)

# Math on the whole array at once (no loops needed!)
#print(arr * 2)         # [184 174  90 200 134]
#print(arr.mean())      # 78.2
#print(arr.max())       # 100
#print(arr.std())       # standard deviation

# 2D arrays — think of these as rows and columns of data
matrix = np.array([
    [1, 2, 3],
    [4, 5, 6],
    [7, 8, 9]
])
#print(matrix.shape)    # (3, 3) — 3 rows, 3 columns
#print(matrix[0])       # [1, 2, 3] — first row
#print(matrix[:, 1])    # [2, 5, 8] — second column


import pandas as pd

# Create a simple dataset
data = {
    "age":    [25, 32, 47, 51, 23],
    "salary": [50000, 80000, 120000, 95000, 40000],
    "churned":[0, 0, 1, 0, 1]
}

df = pd.DataFrame(data)
print("Data Format")
print(df)

# Explore your data
print(df.shape)          # (5, 3) — 5 rows, 3 columns
print(df.describe())     # statistics summary
print(df.head(3))        # first 3 rows

# Filter rows
high_earners = df[df["salary"] > 70000]

# Select a column
print(df["age"])

# Add a new column
df["senior"] = df["age"] > 40

