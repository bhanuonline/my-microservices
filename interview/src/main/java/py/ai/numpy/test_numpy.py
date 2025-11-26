import numpy as np
import time
#print(np.__version__)


#From a Python list:
#[1, 2, 3, 4, 5] is a Python list.
my_list = [1, 2, 3, 4, 5,"bh"]
#print(type(my_list))   # <class 'list'>

#print(my_list[0])      # access first element
#print(my_list + [6]+[7])   # concatenates lists → [1, 2, 3, 4, 5, 6]
# But this does NOT do math element‑wise:
#print(my_list * 3)   # duplicates list → [1, 2, 3, 4, 5, 1, 2, 3, 4, 5]



my_array = np.array([1, 2, 3, 4, 5])
#print(type(my_array))  # <class 'numpy.ndarray'>

#print(my_array[0])     # access first element

# Mathematical operations apply element‑wise
#print(my_array * 2)    # [ 2  4  6  8 10]
#print(my_array + 5)    # [ 6  7  8  9 10]

a = np.array([1, 2, 3, 4, 5])
#print(a)
#print(type(a))      # numpy.ndarray

#2D array (matrix):

b = np.array([[1, 2, 3],
              [4, 5, 6],
              [1,5,8]])
#print(b)
#print(b.shape)      # (2, 3)

#print("number of dimensions : ",b.ndim)   # number of dimensions
#print("shape :",b.shape)  # shape (length per dimension)
#print("size :",b.size)   # total number of elements
#print("type : ",b.dtype)  # data type



#Let’s see how NumPy arrays crush Python lists in speed and efficiency

size = 10_000_000  # ten million elements

list1 = list(range(size))
list2 = list(range(size))

start = time.time()

result_list = [x + y for x, y in zip(list1, list2)]
#print(result_list)
end = time.time()
print("Python list took:", end - start, "seconds")


arr1 = np.arange(size)
arr2 = np.arange(size)

start = time.time()

result_array = arr1 + arr2   # element‑wise addition, done in C behind the scenes
print(result_array)
end = time.time()
print("NumPy array took:", end - start, "seconds")
