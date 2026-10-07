import sys
#print(sys.executable)
#print(sys.version)
print("Hello from Terminal!")
if(5>2):
    print("Yes, 5 is greater than 2")

print("Hello /n",end=" "); print("How are you /n?"); print("Bye bye!") #separating them with ;


print("Hello World!", end=" ")
print("I will print on the same line.")

print(3)
print(358)
print(50000)
print(1+3)
print("1"+"2")
print("The price is:", 99)
x = 5
y = "John"
print(x)
print(y)

x = 4       # x is of type int
x = "Sally" # x is now of type str
print(x)

#casting
x = str(3)    # x will be '3'
y = int(3)    # y will be 3
z = float(3)  # z will be 3.0

#Creating Variables
x = 5
y = "John"
print(type(x))
print(type(y))

#Casting

x = str(3)    # x will be '3'
y = int(3)    # y will be 3
z = float(3)  # z will be 3.0
print(x)

#Case-Sensitive
a = 4
A = "Sally"
print(a)
print(A)

#Unpack a Collection
fruits = ["apple", "banana", "cherry"]
x, y, z = fruits
print(x)
print(y)
print(z)

#function, you output multiple variables, separated by a comma:
x = "Python"
y = "is"
z = "awesome"
print(x, y, z)

x = "Python "
y = "is "
z = "awesome"
print(x + y + z)

x = 5
y = "John"
print(x,y)

x = "awesome"

def myfunc():
  print("Python is " + x)

myfunc()

x = "awesome"

def myfunc():
  x = "fantastic"
  print("Python is " + x)

myfunc()

print("Python is " + x)


# global keyword
def myfunc():
  global x
  x = "fantastic"

myfunc()

print("Python is " + x)

#Data type
#list
thislist = ["apple", "banana", "cherry"]
print(thislist)