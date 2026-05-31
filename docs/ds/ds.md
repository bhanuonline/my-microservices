# Comparison-Based

Bubble Sort — repeatedly swaps adjacent elements; O(n²)
Selection Sort — finds the minimum and places it in order; O(n²)
Insertion Sort — builds sorted array one element at a time; O(n²), great for small/nearly-sorted data
Merge Sort — divide and conquer, stable; O(n log n)
Quick Sort — pivot-based partitioning; O(n log n) avg, O(n²) worst
Heap Sort — uses a binary heap; O(n log n), in-place but not stable
Shell Sort — generalized insertion sort with gap sequences; O(n log² n)
Tim Sort — hybrid merge + insertion sort (used in Python, Java); O(n log n)
Intro Sort — hybrid quick + heap + insertion sort (used in C++ STL); O(n log n)

# Non-Comparison (Linear Time)

Counting Sort — counts occurrences; O(n + k), integers only
Radix Sort — sorts digit by digit; O(nk)
Bucket Sort — distributes into buckets; O(n + k) avg

# Specialized / Exotic

Cycle Sort — minimizes writes to memory; O(n²)
Patience Sort — based on card game; used in LIS problems
Bogo Sort — randomly shuffles until sorted; O(∞) — not practical, just famous
Bitonic Sort — parallel-friendly; O(log² n) parallel
Strand Sort — extracts sorted sublists; O(n√n) avg