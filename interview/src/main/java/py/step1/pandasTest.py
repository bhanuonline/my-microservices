import pandas as pd
#print(pd.__version__)

#In pandas there are only 2 main objects:
#Series(like a column) and dataFrame(Excel)

s = pd.Series([10, 20, 30,50,40,40,0.54000,"1",1])
#print(s)

s1 = pd.Series([25, 30, 28], index=["A", "B", "C"])
#print(s1)
#print(s1["C"])

#DataFrame = Collection of Series
data = {
    "Name": ["Bhanu","Nidhi","Kiran","Kiyara","Rohit","Kanti","DayaShankar"],
    "Age": [35, 25, 32,1.5,30,50,60],
    "Salary":[1000,50,400,0,0,0,100],
    "City": ["Delhi", "Mumbai", "Noida","Kanpur","lucknow","meerut","Vanarsi"]
}

df = pd.DataFrame(data)
#print(df)

#print(df.head())
#print(df.tail())
#df.info()
#df.describe()
#print(df.columns)
#print(df.shape)
#print(df["Name"])
#print(df[["Name", "Age"]])
#print(df.loc[0]) #uses index label

#print(df.iloc[0]) #Position Based

#print(df.head())

data = {
    "Product": ["Pen", "Book", "Pencil", "Notebook"],
    "Price": [10, 50, 5, 80],
    "Quantity": [100, 200, 300, 150]
}

dff = pd.DataFrame(data)
#print(dff)
#print(dff.head())
#print(dff[dff["Price"] > 20])

#print(dff[(dff["Price"] > 10) & (dff["Quantity"] > 150)])
#print(dff[(dff["Price"] > 50) | (dff["Quantity"] > 250)])

#print(dff[dff["Product"] == "Pen"])

dff["Price"] = dff["Price"] + 10

dff.loc[dff["Product"] == "Pen", "Price"] = 20

dff["Total_Value"] = dff["Price"] * dff["Quantity"]

print(dff)

#print(dff["Price"])
dff.loc[dff["Price"] < 20, "Price"] += 5

#df.loc[rows_condition, column_to_modify] = value