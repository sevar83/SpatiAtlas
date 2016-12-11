package eu.arateb.spatiatlas.util;

import java.io.Closeable;
import java.io.IOException;

/**
 * Created by sevar on 10.12.16.
 */

public class Utils {

    public static void closeSilently(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
            }
        }
    }
}
