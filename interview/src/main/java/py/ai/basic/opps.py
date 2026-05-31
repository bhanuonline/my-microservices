class Emp:
    def __init__(self,name,age):
        self.name=name
        self.age=age

    def display(self):
        print("Name:", self.name)
        print("Age:", self.age)

emp = Emp("Bhanu", 34)
emp.display()
