package testing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class MergeSortTest {

    @Test
    void sortsRandomArray() {
        int[] arr = {5, 2, 8, 1, 9, 3};
        MergeSort.sort(arr);
        assertArrayEquals(new int[]{1, 2, 3, 5, 8, 9}, arr);
    }

    @Test
    void sortsAlreadySortedArray() {
        int[] arr = {1, 2, 3, 4, 5};
        MergeSort.sort(arr);
        assertArrayEquals(new int[]{1, 2, 3, 4, 5}, arr);
    }

    @Test
    void sortsReverseSortedArray() {
        int[] arr = {5, 4, 3, 2, 1};
        MergeSort.sort(arr);
        assertArrayEquals(new int[]{1, 2, 3, 4, 5}, arr);
    }

    @Test
    void handlesDuplicates() {
        int[] arr = {3, 1, 3, 2, 1, 2};
        MergeSort.sort(arr);
        assertArrayEquals(new int[]{1, 1, 2, 2, 3, 3}, arr);
    }

    @Test
    void handlesSingleElement() {
        int[] arr = {42};
        MergeSort.sort(arr);
        assertArrayEquals(new int[]{42}, arr);
    }

    @Test
    void handlesEmptyArray() {
        int[] arr = {};
        MergeSort.sort(arr);
        assertArrayEquals(new int[]{}, arr);
    }

    @Test
    void handlesNegativeNumbers() {
        int[] arr = {-3, 1, -5, 2, 0};
        MergeSort.sort(arr);
        assertArrayEquals(new int[]{-5, -3, 0, 1, 2}, arr);
    }

    @Test
    void handlesNullArray() {
        MergeSort.sort(null);
    }
}
