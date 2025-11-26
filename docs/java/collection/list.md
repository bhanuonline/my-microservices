**Creating Lists**

    Standerd way
    Arrays.asList()
    List.of() (Java 9+) immutable list

Updating elements

    fruits.set(0, "Cherry");    

Iterating Through Lists

    For-each loop (recommended)
    Iterator
    Traditional for loop
    Stream API (Java 8+)

Sorting Lists

    Collections.sort(fruits);  // Natural ordering (alphabetical for Strings)

    Using custom Comparator
    Collections.sort(fruits, new Comparator<String>() {
    @Override
    public int compare(String f1, String f2) {
    return f1.length() - f2.length();  // Sort by length
    }
    });

    Using Lambda (Java 8+)
    fruits.sort((f1, f2) -> f1.length() - f2.length());
    
    Using Comparator methods (Java 8+)
    fruits.sort(Comparator.comparing(String::length));

Converting Between Lists and Arrays

    List to Array
    String[] fruitArray = fruits.toArray(new String[0]);
    Array to List
    Arrays.asList(colorArray); 
    // or for a modifiable copy:
    List<String> modifiableList = new ArrayList<>(Arrays.asList(colorArray));
