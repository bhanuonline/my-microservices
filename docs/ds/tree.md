What is a Tree?
A Tree is a hierarchical, non-linear data structure consisting of nodes connected by edges.

RBT:
TreeMap impl Map
TreeSet impl Set

3. PriorityQueue (java.util)
Internally uses Binary Heap Tree (Min-Heap)
Always retrieves the smallest element first

4. HashMap (java.util — Java 8+)
Normally uses array + linked list
But when bucket size > 8, it converts to a Red-Black Tree internally
Internally uses tree for performance when collisions are high

