
# Basic
name ="Bhanu"
age=34

print("My name: " + name + " and age is: " + str(age))
print(f"My name is {name} and age is {age}")
print("My name:", name, "and age is:", age)

num1 =int(input("Enter num1 :"))
num2 =int(input("Enter num2:"))
print("sum of two num ",num1+num2)

if num1%2==0:
    print("Even num")
else:
    print("odd num")

for i in range(10):
    print(i)

def hello(name):
    print("Function hello called",name)
    print("Function hello called")

hello("hi")

# Basic 2
name = "Hleoo"

print(name.upper())

#Tuples
colors = ("red", "green", "blue")

print(colors[0])

#set
nums = {1, 2, 3, 3, 2}
print(nums)

#File Handling
with open("test.txt", "w") as file:
    file.write("Hello Python")

with open("test.txt", "r") as file:
    print(file.read())



