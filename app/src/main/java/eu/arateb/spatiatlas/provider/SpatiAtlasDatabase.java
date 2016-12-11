package eu.arateb.spatiatlas.provider;

import android.content.Context;
import android.net.Uri;

import static eu.arateb.spatiatlas.util.LogUtils.makeLogTag;

public class SpatiAtlasDatabase extends SpatialiteFileDbHelper {

    private static final String TAG = makeLogTag(SpatiAtlasDatabase.class);

    private static final String DATABASE_NAME = "geodb.sqlite";

    public SpatiAtlasDatabase(Context context, String dbName) {
        super(context, dbName);
    }

    public SpatiAtlasDatabase(Context context, String assetName, String dbName) {
        super(context, assetName, dbName);
    }

    public SpatiAtlasDatabase(Context context, Uri dbUri, String dbName) {
        super(context, dbUri, dbName);
    }

    public static void deleteDatabase(Context context) {
        context.deleteDatabase(DATABASE_NAME);
    }

    interface Tables {
        String SPATIAL_REF_SYS = "spatial_ref_sys";
        String VECTOR_LAYERS = "vector_layers";
        String VECTOR_LAYERS_STATS = "vector_layers_statistics";

        String SPATIAL_INDEX = "SpatialIndex";

        String TOWNS = "towns";
        String MUNICIPALITIES = "municipalities";
        String COUNTIES = "counties";
        String REGIONS = "regions";
        String COUNTRIES = "countries";

        String MUNICIPALITIES_JOIN_COUNTIES = "municipalities "
                + "LEFT OUTER JOIN counties ON municipalities.county_id=counties._id";

        String COUNTIES_JOIN_REGIONS = "counties "
                + "LEFT OUTER JOIN regions ON counties.region_id=regions._id";
    }

    private interface Triggers {
    }

    /**
     * Fully-qualified field names.
     */
    private interface Qualified {
    }

    /**
     * {@code REFERENCES} clauses.
     */
    private interface References {
    }
}
