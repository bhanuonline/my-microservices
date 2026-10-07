package testing;

import java.lang.instrument.Instrumentation;

public class ObjectSize {
    private static Instrumentation instrumentation;

    public static void premain(String args, Instrumentation inst) {
        instrumentation = inst;
    }

    public static long sizeOf(Object obj) {
        return instrumentation.getObjectSize(obj);
    }

    public static void main(String[] args) {
        System.out.println(sizeOf(new int[10]));     // array size
        System.out.println(sizeOf(new Object()));    // object size
        System.out.println(sizeOf("Hello"));         // string size
    }
}