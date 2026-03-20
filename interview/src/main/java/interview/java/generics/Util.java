package interview.java.generics;

class Util<T> {

    public static <TM>  TM identity(TM value) { // ✅ <T> required
        return value;
    }
}
