class Emp:
    def __init__(self,name,age):
        self.name=name
        self.age=age

    def display(self):
        print("Name:", self.name)
        print("Age:", self.age)

emp = Emp("Bhanu", 34)
emp.display()

class Dog:
    species = "Canis familiaris"  # Class attribute

    def __init__(self, name, age):  # Constructor
        self.name = name           # Instance attributes
        self.age = age

    def bark(self):
        return f"{self.name} says Woof!"

# Creating objects
dog1 = Dog("Buddy", 3)
dog2 = Dog("Max", 5)

print(dog1.bark())      # Buddy says Woof!
print(dog1.species)     # Canis familiaris

class Animal:
    def __init__(self, name):
        self.name = name

    def speak(self):
        return "Some sound"

class Cat(Animal):          # Cat inherits from Animal
    def speak(self):        # Method overriding
        return f"{self.name} says Meow!"

class Dog(Animal):
    def speak(self):
        return f"{self.name} says Woof!"

cat = Cat("Whiskers")
print(cat.speak())          # Whiskers says Meow!

class BankAccount:
    def __init__(self, balance):
        self.__balance = balance    # Private attribute (name mangling)

    def deposit(self, amount):
        if amount > 0:
            self.__balance += amount

    def get_balance(self):          # Getter
        return self.__balance

acc = BankAccount(1000)
acc.deposit(500)
print(acc.get_balance())            # 1500
# print(acc.__balance)             # AttributeError!

class Shape:
    def area(self):
        pass

class Circle(Shape):
    def __init__(self, radius):
        self.radius = radius
    def area(self):
        return 3.14 * self.radius ** 2

class Rectangle(Shape):
    def __init__(self, w, h):
        self.w, self.h = w, h
    def area(self):
        return self.w * self.h

shapes = [Circle(5), Rectangle(4, 6)]
for shape in shapes:
    print(shape.area())     # 78.5, then 24

    from abc import ABC, abstractmethod

class Vehicle(ABC):
    @abstractmethod
    def start(self):        # Must be implemented by subclasses
        pass

    @abstractmethod
    def stop(self):
        pass

class Car(Vehicle):
    def start(self):
        return "Car engine started"
    def stop(self):
        return "Car stopped"

car = Car()
print(car.start())          # Car engine started
# Vehicle()                 # TypeError: Can't instantiate abstract class

class Point:
    def __init__(self, x, y):
        self.x, self.y = x, y

    def __str__(self):          # print(obj)
        return f"Point({self.x}, {self.y})"

    def __repr__(self):         # repr(obj)
        return f"Point(x={self.x}, y={self.y})"

    def __add__(self, other):   # p1 + p2
        return Point(self.x + other.x, self.y + other.y)

    def __len__(self):          # len(obj)
        return int((self.x**2 + self.y**2) ** 0.5)

p1 = Point(1, 2)
p2 = Point(3, 4)
print(p1 + p2)              # Point(4, 6)

class MathUtils:
    count = 0

    def __init__(self):
        MathUtils.count += 1

    @classmethod
    def get_count(cls):         # Access class state
        return cls.count

    @staticmethod
    def add(a, b):              # No access to class or instance
        return a + b

print(MathUtils.add(3, 4))     # 7
m = MathUtils()
print(MathUtils.get_count())   # 1


class Temperature:
    def __init__(self, celsius):
        self._celsius = celsius

    @property
    def fahrenheit(self):           # Getter
        return self._celsius * 9/5 + 32

    @fahrenheit.setter
    def fahrenheit(self, value):    # Setter
        self._celsius = (value - 32) * 5/9

t = Temperature(100)
print(t.fahrenheit)     # 212.0
t.fahrenheit = 32
print(t._celsius)       # 0.0