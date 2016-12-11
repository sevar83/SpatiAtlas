package eu.arateb.spatiatlas.util;

/**
 * Created by sevar on 10.12.16.
 */

public class Preconditions {

    public static <T> T checkNotNull(T value) {
        if (value == null) {
            throw new NullPointerException("Null value");
        }
        return value;
    }
}
