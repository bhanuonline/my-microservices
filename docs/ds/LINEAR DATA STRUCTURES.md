Address of arr[i] = Base Address + (i × size of element)=1000+(1*4)=1000+4
Address = Base + (Index − LowerBound) × Size
Each integer takes 4 bytes, so they're stored at consecutive memory addresses.

**Visual representation:**
```
Memory Address:  1000   1004   1008   1012   1016
Array Index:      [0]    [1]    [2]    [3]    [4]
Values:           10     20     30     40     50
```
Cache Friendly - Since elements are together, the CPU cache loads nearby elements automatically, making iterations very fast.
No Extra Memory - No pointers or links needed between elements (unlike linked lists)

How Cache Works: Spatial Locality
When CPU fetches data from RAM, it doesn't fetch just ONE element. It fetches a cache line (typically 64 bytes) containing nearby elements.

Journey of arr[0]:
        RAM → CPU Cache → CPU Register


Check CPU Cache Size
sysctl -a | grep cache

getconf -a | grep CACHE

### CPU Cache Levels
```
┌─────────────────────────────────────┐
│  L1 Cache (Smallest, Fastest)       │
│  - L1i (Instruction): 32-64 KB      │
│  - L1d (Data): 32-64 KB             │
│  - Per core                         │
│  - Access: ~1 nanosecond            │
└─────────────────────────────────────┘
            ↓
┌─────────────────────────────────────┐
│  L2 Cache (Medium)                  │
│  - Size: 256 KB - 1 MB              │
│  - Per core or shared               │
│  - Access: ~3-10 nanoseconds        │
└─────────────────────────────────────┘
            ↓
┌─────────────────────────────────────┐
│  L3 Cache (Largest, Slowest)        │
│  - Size: 8-32 MB                    │
│  - Shared across all cores          │
│  - Access: ~10-20 nanoseconds       │
└─────────────────────────────────────┘
            ↓
┌─────────────────────────────────────┐
│  RAM                                │
│  - Access: ~100 nanoseconds         │
└─────────────────────────────────────┘

#  | Program                | Difficulty | Concept
---|------------------------|------------|---------------------------
1  | Array Basics           | Easy       | Declaration, Initialization [done]
2  | Sum & Average          | Easy       | Traversal, Arithmetic
3  | Max & Min              | Easy       | Comparison
4  | Reverse Array          | Easy       | Two Pointers
5  | Linear Search          | Easy       | Searching
6  | Binary Search          | Medium     | Divide & Conquer
7  | Bubble Sort            | Medium     | Sorting
8  | Remove Duplicates      | Medium     | Two Pointers
9  | Rotate Array           | Medium     | Array Manipulation
10 | Second Largest         | Medium     | Logic Building
11 | Merge Sorted Arrays    | Medium     | Merging
12 | Find Missing Number    | Medium     | Mathematical
13 | Kadane's Algorithm     | Hard       | Dynamic Programming
14 | Dutch National Flag    | Hard       | Three Pointers
15 | Two Sum                | Medium     | HashMap
16 | Matrix Operations      | Medium     | 2D Arrays

=== BASIC ARRAY OPERATIONS ===
1.  Array Declaration & Initialization
2.  Array Traversal (Loop through elements)
3.  Sum & Average of Array
4.  Find Maximum & Minimum
5.  Count Even & Odd Numbers
6.  Count Positive & Negative Numbers
7.  Copy Array
8.  Compare Two Arrays
9.  Print Array in Reverse
10. Frequency of Elements
11. Insert Element at Position
12. Delete Element from Position
13. Check if Array is Sorted
14. Check if Array is Palindrome
15. Find Unique Elements

=== SEARCHING ALGORITHMS ===
16. Linear Search
17. Binary Search (Iterative)
18. Binary Search (Recursive)
19. Jump Search
20. Interpolation Search
21. Exponential Search
22. Ternary Search
23. Search in Rotated Sorted Array
24. Find First & Last Position of Element
25. Search in 2D Matrix

=== SORTING ALGORITHMS ===
26. Bubble Sort
27. Selection Sort
28. Insertion Sort
29. Merge Sort
30. Quick Sort
31. Heap Sort
32. Counting Sort
33. Radix Sort
34. Bucket Sort
35. Shell Sort
36. Cycle Sort

=== ARRAY MANIPULATION ===
37. Reverse Array (Two Pointers)
38. Rotate Array Left
39. Rotate Array Right
40. Cyclically Rotate Array
41. Rearrange Array (Positive & Negative)
42. Move Zeros to End
43. Separate Even & Odd Numbers
44. Remove Duplicates from Sorted Array
45. Remove Duplicates from Unsorted Array
46. Merge Two Sorted Arrays
47. Merge Overlapping Intervals
48. Union of Two Arrays
49. Intersection of Two Arrays
50. Array Shuffle (Randomize)

=== TWO POINTER TECHNIQUE ===
51. Two Sum Problem
52. Three Sum Problem
53. Four Sum Problem
54. Pair with Given Sum
55. Count Pairs with Given Sum
56. Find Triplet with Given Sum
57. Container with Most Water
58. Trapping Rain Water
59. Remove Element
60. Sort Colors (Dutch National Flag)

=== SLIDING WINDOW ===
61. Maximum Sum Subarray of Size K
62. Smallest Subarray with Sum > X
63. Longest Substring with K Unique Characters
64. Maximum of All Subarrays of Size K
65. First Negative in Every Window
66. Count Distinct Elements in Window
67. Longest Subarray with Sum K
68. Minimum Window Substring

=== SUBARRAY PROBLEMS ===
69. Kadane's Algorithm (Max Subarray Sum)
70. Maximum Product Subarray
71. Longest Increasing Subarray
72. Longest Decreasing Subarray
73. Count Subarrays with Given Sum
74. Subarray with 0 Sum
75. Largest Subarray with 0 Sum
76. Longest Alternating Subarray
77. Maximum Circular Subarray Sum
78. Count Subarrays with Equal 0s and 1s

=== PREFIX/SUFFIX SUM ===
79. Prefix Sum Array
80. Suffix Sum Array
81. Range Sum Queries
82. Equilibrium Index
83. Find Pivot Index
84. Product of Array Except Self
85. Maximum Prefix Sum

=== FREQUENCY & COUNTING ===
86. Frequency of Each Element
87. First Repeating Element
88. First Non-Repeating Element
89. Find Duplicates in Array
90. Find All Duplicates
91. Missing Number in Array (1 to N)
92. Find Two Missing Numbers
93. Find Repeating & Missing Number
94. Majority Element (> N/2)
95. Majority Element (> N/3)
96. Moore's Voting Algorithm

=== ARRAY REARRANGEMENT ===
97. Rearrange Array in Alternating Positive & Negative
98. Rearrange Array in Max-Min Form
99. Segregate 0s and 1s
100. Segregate 0s, 1s and 2s (Dutch Flag)
101. Move All Negative to Beginning
102. Rearrange Array by Index
103. Double the First Element and Move Zero to End

=== MATHEMATICAL PROBLEMS ===
104. Leaders in Array
105. Stock Buy & Sell (Single Transaction)
106. Stock Buy & Sell (Multiple Transactions)
107. Maximum Difference (arr[j] - arr[i] where j > i)
108. Smallest Positive Missing Number
109. Find the Element that Appears Once
110. Count Inversions in Array
111. Minimum Jumps to Reach End
112. Maximum Index Difference
113. Chocolate Distribution Problem

=== MATRIX (2D ARRAY) PROBLEMS ===
114. Print Matrix in Spiral Order
115. Rotate Matrix 90 Degrees
116. Transpose of Matrix
117. Search in Row-Column Sorted Matrix
118. Set Matrix Zeros
119. Diagonal Sum of Matrix
120. Print Matrix Diagonally
121. Matrix Multiplication
122. Pascal's Triangle
123. Wave Print of Matrix
124. Zigzag Traversal of Matrix
125. Boundary Traversal of Matrix
126. Snake Pattern in Matrix

=== DYNAMIC PROGRAMMING ON ARRAYS ===
127. Longest Increasing Subsequence (LIS)
128. Longest Decreasing Subsequence
129. Longest Bitonic Subsequence
130. Maximum Sum Increasing Subsequence
131. Longest Common Subsequence
132. 0/1 Knapsack Problem
133. Unbounded Knapsack
134. Subset Sum Problem
135. Partition Equal Subset Sum
136. Minimum Subset Sum Difference
137. Count of Subset with Given Sum
138. Coin Change Problem
139. Rod Cutting Problem
140. Egg Dropping Problem

=== BITWISE OPERATIONS ON ARRAYS ===
141. Find Two Non-Repeating Elements
142. Find Missing Number using XOR
143. Count Set Bits in Array
144. Maximum XOR of Two Numbers
145. Single Number (All appear twice except one)
146. Single Number II (All appear thrice except one)
147. Subarray XOR

=== GREEDY ALGORITHMS ===
148. Activity Selection Problem
149. Fractional Knapsack
150. Job Sequencing Problem
151. Minimum Platforms Required
152. Huffman Coding (using arrays)
153. Maximum Meetings in One Room

=== ADVANCED PROBLEMS ===
154. Next Permutation
155. Previous Permutation
156. Longest Consecutive Sequence
157. Longest Palindromic Subarray
158. Maximum Length of Repeated Subarray
159. Minimum Size Subarray Sum
160. Gas Station (Circular Array)
161. Jump Game
162. Jump Game II
163. Candy Distribution
164. Best Time to Buy and Sell Stock III
165. Best Time to Buy and Sell Stock IV
166. Sliding Window Maximum
167. Median of Two Sorted Arrays
168. K-th Largest Element
169. K-th Smallest Element
170. Top K Frequent Elements
171. Find K Pairs with Smallest Sum
172. Kth Largest Element in Stream

=== DIVIDE & CONQUER ===
173. Merge Sort Implementation
174. Quick Sort Implementation
175. Count Inversions using Merge Sort
176. Find Peak Element
177. Maximum Subarray (D&C approach)

=== BACKTRACKING WITH ARRAYS ===
178. Generate All Subsets
179. Generate All Permutations
180. Combination Sum
181. N-Queens using Array
182. Sudoku Solver using 2D Array
183. Rat in Maze
184. Word Search in Matrix

=== STACK/QUEUE USING ARRAYS ===
185. Implement Stack using Array
186. Implement Queue using Array
187. Implement Circular Queue
188. Next Greater Element
189. Previous Greater Element
190. Next Smaller Element
191. Stock Span Problem
192. Largest Rectangle in Histogram
193. Celebrity Problem

=== HASH-BASED PROBLEMS ===
194. Two Sum using HashMap
195. Four Sum using HashMap
196. Longest Substring without Repeating Characters
197. Group Anagrams
198. Isomorphic Strings (using arrays as hash)
199. Valid Anagram
200. Find Duplicate Subtrees

=== SPECIAL TECHNIQUES ===
201. Fisher-Yates Shuffle
202. Reservoir Sampling
203. Boyer-Moore Majority Vote
204. Quickselect Algorithm
205. Tortoise and Hare (Cycle Detection in Array)
206. Floyd's Cycle Detection
207. Manacher's Algorithm (Palindrome)

=== COMPETITIVE PROGRAMMING CLASSICS ===
208. Rain Water Trapping 2D
209. Maximal Rectangle in Binary Matrix
210. Minimum Path Sum in Grid
211. Unique Paths in Grid
212. Unique Paths II (with obstacles)
213. Dungeon Game
214. Cherry Pickup
215. Burst Balloons
216. Russian Doll Envelopes
217. Longest Valid Parentheses (using array)
218. Largest Rectangle in Binary Matrix
219. Count of Range Sum
220. Max Sum of Rectangle No Larger than K

=== INTERVIEW FAVORITES ===
221. Design Circular Deque
222. Design Hit Counter
223. LRU Cache (using array concepts)
224. LFU Cache
225. Time-Based Key-Value Store
226. Design HashMap
227. Design HashSet
228. Moving Average from Data Stream
229. Design Snake Game
230. Design Tic-Tac-Toe


## SUMMARY OF ADVANCED ALGORITHMS
```
ALGORITHM                      | TIME        | SPACE  | REAL-WORLD USE
-------------------------------|-------------|--------|----------------------------------
Ternary Search                 | O(log n)    | O(1)   | Optimization, ML
Exponential Search             | O(log n)    | O(1)   | Search engines, databases
Merge Sort                     | O(n log n)  | O(n)   | External sorting, parallel
Quick Sort                     | O(n log n)  | O(log n)| Standard library sorting
Counting Sort                  | O(n + k)    | O(k)   | Limited range sorting
LIS (Binary Search)            | O(n log n)  | O(n)   | DNA analysis, stock trends
0/1 Knapsack                   | O(n × W)    | O(n×W) | Resource allocation
Matrix Chain Multiplication    | O(n³)       | O(n²)  | Compiler optimization
Sliding Window Maximum         | O(n)        | O(k)   | Real-time data processing
Kadane's (2D)                  | O(n³)       | O(n)   | Image processing
Convex Hull                    | O(n log n)  | O(n)   | Graphics, collision detection

Compare algo :

| Approach            | Idea                           |
| ------------------- | ------------------------------ |
| Greedy              | Choose best option immediately |
| Dynamic Programming | Try many possibilities         |
| Brute Force         | Try every combination          |
