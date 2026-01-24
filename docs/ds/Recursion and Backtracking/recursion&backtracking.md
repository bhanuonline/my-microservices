
Core Recursion Problems

| Problem               | Idea                  |
| --------------------- | --------------------- |
| Factorial / Fibonacci | Basic recursion       |
| Reverse a string      | Break + solve smaller |
| Power(x,n)            | Divide & conquer      |
| Print all subsets     | Pick / not pick       |
| Tower of Hanoi        | Move disks            |
| Merge Sort            | Recursion on halves   |
| Quick Sort            | Partition recursion   |


Backtracking Problems

| Problem                 | Key Concept             |
| ----------------------- | ----------------------- |
| Permutations            | Try all positions, undo |
| Combinations            | Choose or skip          |
| Subsets                 | Binary decision tree    |
| N-Queens                | Place & backtrack       |
| Sudoku Solver           | Try digits, revert      |
| Rat in a Maze           | Try all paths           |
| Word Search             | DFS + backtracking      |
| Palindrome Partitioning | Cut string recursively  |


Template for Backtracking

void solve(params) {

    if (baseCase) {
        saveAnswer();
        return;
    }

    for (choice in choices) {
        makeChoice();
        solve(updatedParams);
        undoChoice();   // backtrack
    }
}

Rule to Decide
Use recursion when you see:

| Keyword in problem              | Example                        |
| ------------------------------- | ------------------------------ |
| divide into same structure      | tree, graph, folder, directory |
| repeated sub-problem            | factorial, fibonacci           |
| all combinations / permutations | subsets, N-Queens              |
| hierarchical data               | XML, JSON, org chart           |
| backtracking                    | sudoku, maze                   |
| depth-first traversal           | DFS, tree height               |


| Problem        | Why recursion fits       |
| -------------- | ------------------------ |
| Tree traversal | Each node has sub-trees  |
| Folder search  | Folder contains folders  |
| Binary search  | Left & right half        |
| Merge sort     | Split into halves        |
| Permutations   | Solve remaining elements |
| Tower of Hanoi | Move smaller problem     |


When NOT to use recursion
| Scenario             | Better approach                 |
| -------------------- | ------------------------------- |
| Very deep calls      | Iteration (stack overflow risk) |
| Simple loops         | for / while                     |
| Performance-critical | Iterative DP                    |


Use recursion when problem shape repeats inside itself.


**backtracking**
We use backtracking when the problem asks us to try all possible choices and we must undo choices when a path fails.

Simple Rule
Use backtracking when:
You must explore many options
You must return to previous state and try again
There is no single direct formula solution


Keywords in Problem
| Word in Question  |
| ----------------- |
| all possible      |
| every combination |
| generate          |
| permutations      |
| choose k          |
| find all paths    |
| solve puzzle      |
| valid arrangement |

Typical Backtracking Problems

| Problem                 |
| ----------------------- |
| N-Queens                |
| Sudoku Solver           |
| Word Search             |
| Subsets / Combinations  |
| Permutations            |
| Maze path               |
| Palindrome partitioning |


Recursion and Backtracking are related, but they are not the same thing.
Backtracking is actually a special use of recursion.

**Relationship**
Recursion → general technique
Backtracking → recursion + undo logic



**Differences**

| Feature            | Recursion                         | Backtracking               |
| ------------------ | --------------------------------- | -------------------------- |
| Purpose            | Break problem into smaller pieces | Try all possible choices   |
| Undo previous step | No                                | **Yes (must undo)**        |
| Example            | Factorial, Fibonacci, Merge sort  | Sudoku, N-Queens           |
| Stop early         | No                                | Often prunes invalid paths |
