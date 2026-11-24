Uses less memory outside the Java heap when saving a recording.

- With `zip_while_recording`, free the native memory of the zip (about 256 KB) right away when a recording is stopped without saving, or can no longer be added to the zip. Before, it stayed until the GC got to it
- Write the zip of a recording through a buffer of 64 KB instead of 1 MB. The JDK keeps a direct buffer of the same size for each thread that writes it, so it took about 1 MB outside the heap per thread. Writing in batches works about as well as before
