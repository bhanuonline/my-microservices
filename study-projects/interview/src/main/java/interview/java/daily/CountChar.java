package interview.java.daily;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class CountChar {
    public static void main(String[] args) {
        String s="BhanuuB";

       // Map<Character, Integer> integerCharacterMap = getVoiMap(s);

        int[] freq =countCharsArray(s);
        for (int i = 0; i < freq.length; i++) {
            System.out.println(freq[i]);
        }
    }

    private static Map<Character, Integer> getVoiMap(String s) {
        Map<Character,Integer> integerCharacterMap=new HashMap<>();
        for (char c: s.toCharArray()){
            if(integerCharacterMap.containsKey(c)){
                integerCharacterMap.put(c, integerCharacterMap.get(c) + 1);
            }
            else{
                integerCharacterMap.put(c,1);
            }
        }
        return integerCharacterMap;
    }

    /**
     * The normal for loop + HashMap is more efficient than the Stream version.
     * @param s
     * @return
     */
    private static Map<Character, Integer> interviewFriendly(String s) {
        Map<Character, Integer> map = new HashMap<>();

        for (char c : s.toCharArray()) {
            map.put(c, map.getOrDefault(c, 0) + 1);
        }
        return map;
    }
    /**
     * Counts the frequency of each character in the given string using Java Streams.
     *
     * @param s the input string whose characters need to be counted
     * @return a Map where:
     *         - key   → character from the string
     *         - value → number of occurrences of that character
     *
     * Example:
     * Input  : "BhanuuB"
     * Output : {B=2, h=1, a=1, n=1, u=2}
     */
    public static Map<Character, Long> countCharacters(String s) {

        // Convert the String into an IntStream of character ASCII values
        // Example: 'B' -> 66, 'h' -> 104
        return s.chars()

                // Convert each int (ASCII value) into a Character object
                // This is required because Collectors work with objects, not primitives
                .mapToObj(c -> (char) c)

                // Group characters by themselves and count occurrences
                // groupingBy -> uses character as key
                // counting()  -> counts how many times each character appears
                .collect(Collectors.groupingBy(
                        c -> c,
                        Collectors.counting()
                ));
    }

    public static int[] countCharsArray(String s) {
        int[] freq = new int[256]; // ASCII size

        for (char c : s.toCharArray()) {
            freq[c]++;
        }
        // Print result
        for (int i = 0; i < freq.length; i++) {
            if (freq[i] > 0) {
                System.out.println((char) i + " -> " + freq[i]);
            }
        }
        return freq;
    }




}
