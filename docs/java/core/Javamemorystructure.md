Java Memory Structure (JVM Runtime Layout)
When your Java application starts, the Java Virtual Machine (JVM) requests memory from the operating system.

This memory is organized into several regions with specific responsibilities.

Heap Memory – Objects area

    Purpose: Stores all Java objects and arrays
    Managed by: Garbage Collector (GC).
    It is shared by all threads in the application.
    Subdivisions (Generations):
        Young Generation
            Eden Space: Newly created objects.
            Survivor Spaces (S0, S1): Still-alive objects after minor GCs.
        Old/Tenured Generation
            Long-lived objects that survived multiple GCs.Promoted from the Young Generation.

    ex:
        -Xms512m    # initial heap size
        -Xmx1024m   # max heap size

java -XX:+PrintFlagsFinal -version | grep HeapSize
java -XshowSettings:properties -version | grep 'java.home'

You can monitor heap memory usage using:
jconsole

#Using jstat for Live Heap Stats
jstat -gc 30402

#Using jcmd to Check Heap Info
jcmd 30402 GC.heap_info

#create heap dump file
jcmd 30402 GC.heap_dump /tmp/heap30402.hprof

#Quick Human-Friendly Conversion
jstat -gc 30402 | awk 'NR==2 {print "OldGenUsed: " $8/1024 " MB"}'