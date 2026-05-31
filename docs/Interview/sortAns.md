1. Comparable- The object knows how to compare itself — "I know my own order"
2. Comparator- An outsider defines the order — "You tell me how to compare", Ordering that varies by use case
3. Best code :// Use natural order by default, but allow override
   public static <T extends Comparable<T>> void selectionSort(T[] arr) {
   selectionSort(arr, Comparator.naturalOrder()); // delegates below
   }

    public static <T> void selectionSort(T[] arr, Comparator<T> comp) {
    // actual logic here
    }
4. "Comparable defines natural ordering inside the class, Comparator defines custom ordering outside the class."
5. HashSet works like a locker system 
   each element → hashCode() → finds the locker number
   then         → equals()   → checks if same element in that locker
6. 
