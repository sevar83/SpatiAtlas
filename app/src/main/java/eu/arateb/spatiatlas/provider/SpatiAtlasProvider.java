package eu.arateb.spatiatlas.provider;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;

import org.spatialite.SQLException;
import org.spatialite.database.SQLiteDatabase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import eu.arateb.spatiatlas.R;
import eu.arateb.spatiatlas.util.Utils;

import static android.content.ContentValues.TAG;
import static eu.arateb.spatiatlas.provider.SpatiAtlasContract.SystemInfoColumns.GEOS_VERSION;
import static eu.arateb.spatiatlas.provider.SpatiAtlasContract.SystemInfoColumns.HAS_EPSG;
import static eu.arateb.spatiatlas.provider.SpatiAtlasContract.SystemInfoColumns.HAS_FREEXL;
import static eu.arateb.spatiatlas.provider.SpatiAtlasContract.SystemInfoColumns.HAS_GEOCALLBACKS;
import static eu.arateb.spatiatlas.provider.SpatiAtlasContract.SystemInfoColumns.HAS_GEOS;
import static eu.arateb.spatiatlas.provider.SpatiAtlasContract.SystemInfoColumns.HAS_GEOS_ADVANCED;
import static eu.arateb.spatiatlas.provider.SpatiAtlasContract.SystemInfoColumns.HAS_GEOS_TRUNK;
import static eu.arateb.spatiatlas.provider.SpatiAtlasContract.SystemInfoColumns.HAS_ICONV;
import static eu.arateb.spatiatlas.provider.SpatiAtlasContract.SystemInfoColumns.HAS_LIBXML2;
import static eu.arateb.spatiatlas.provider.SpatiAtlasContract.SystemInfoColumns.HAS_LWGEOM;
import static eu.arateb.spatiatlas.provider.SpatiAtlasContract.SystemInfoColumns.HAS_MATHSQL;
import static eu.arateb.spatiatlas.provider.SpatiAtlasContract.SystemInfoColumns.HAS_PROJ;
import static eu.arateb.spatiatlas.provider.SpatiAtlasContract.SystemInfoColumns.PROJ4_VERSION;
import static eu.arateb.spatiatlas.provider.SpatiAtlasContract.SystemInfoColumns.SPATIALITE_VERSION;


public class SpatiAtlasProvider extends ContentProvider {

    private static final String DB_FILENAME = "geodb.sqlite";
    private static final int SYSTEM_INFO = 1;
    private static final int SPATIAL_REF_SYS = 20;
    private static final int VECTOR_LAYERS = 21;
    private static final int VECTOR_LAYERS_STATS = 22;
    private static final int SPATIAL_INDEX = 400;
    private static final int VECTOR_LAYER = 500;
    private static final int TOWNS = 1000;
    private static final int TOWNS_ID = 1001;
    private static final int HIGHWAYS = 2000;
    private static final int HIGHWAYS_ID = 2001;
    private static final int MUNICIPALITIES = 3000;
    private static final int MUNICIPALITIES_ID = 3001;
    private static final int COUNTIES = 4000;
    private static final int COUNTIES_ID = 4001;
    private static final int REGIONS = 5000;
    private static final int REGIONS_ID = 5001;
    private static final int COUNTRIES = 6000;
    private static final int COUNTRIES_ID = 6001;

    // a content URI pattern matches content URIs using wildcard characters:
    // *: Matches a string of any valid characters of any length.
    // #: Matches a string of numeric characters of any length.
    private static final UriMatcher sUriMatcher = buildUriMatcher();
    // Map DB alias -> attached DB file path
    private final Map<String, String> mAttachedDatabases = new HashMap<String, String>();
    private SpatialiteFileDbHelper mDbHelper;

    private static UriMatcher buildUriMatcher() {
        final UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);
        final String authority = SpatiAtlasContract.CONTENT_AUTHORITY;

        matcher.addURI(authority, "sysinfo", SYSTEM_INFO);

        matcher.addURI(authority, "srs", SPATIAL_REF_SYS);
        matcher.addURI(authority, "vectorlayers", VECTOR_LAYERS);

        matcher.addURI(authority, "spatialindex", SPATIAL_INDEX);

        matcher.addURI(authority, "geomtable/*", VECTOR_LAYER);

        return matcher;
    }

    // system calls onCreate() when it starts up the provider.
    @Override
    public boolean onCreate() {
        // get access to the database helper
        final Context context = getContext();

        SQLiteDatabase.loadLibs(context);

        Uri dbUri = Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.geodb);
        mDbHelper = new SpatialiteFileDbHelper(context, dbUri, DB_FILENAME);

        try {
            mDbHelper.createDataBase(false);
            //dbHelper.getWritableDatabase();
        } catch (IOException ioEx) {
            throw new Error("Unable to open database", ioEx);
        } catch (SQLException sqlEx) {
            throw sqlEx;
        }

        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (method.equalsIgnoreCase(SpatiAtlasContract.Methods.AttachDb.METHOD)) {
            final String dbAlias = arg;
            final String dbFilePath = extras.getString(SpatiAtlasContract.Methods.AttachDb.PARAM_FILEPATH);

            if (mAttachedDatabases.containsKey(dbAlias))
                return null;

            mDbHelper.attach(dbFilePath, dbAlias);
            mAttachedDatabases.put(dbAlias, dbFilePath);

            return new Bundle();
        } else if (method.equalsIgnoreCase(SpatiAtlasContract.Methods.DetachDb.METHOD)) {
            final String dbAlias = arg;

            if (!mAttachedDatabases.containsKey(dbAlias))
                return null;

            mDbHelper.detach(dbAlias);
            mAttachedDatabases.remove(dbAlias);

            return new Bundle();
        } else {
            //throw new IllegalArgumentException("Invalid call method " + method);
        }

        return null;
    }

    // Return the MIME type corresponding to a content URI
    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case VECTOR_LAYER:
                return SpatiAtlasContract.GeometryTable.CONTENT_TYPE;
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
    }

    /* The insert() method adds a new row to the appropriate table, using the
     values in the ContentValues argument. If a column name is not in the
     ContentValues argument, you may want to provide a default value for it
     either in your provider code or in your database schema. */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        ContentResolver cr = getContext().getContentResolver();
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        switch (sUriMatcher.match(uri)) {
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
    }

    /* The query() method must return a Cursor object, or if it fails,
     throw an Exception. If you are using an SQLite database as your data
     storage, you can simply return the Cursor returned by one of the
     query() methods of the SQLiteDatabase class. If the query does
     not match any rows, you should return a Cursor instance whose
     getCount() method returns 0. You should return null only
     if an internal error occurred during the query process. */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Log.v(TAG, "query(uri=" + uri + ", proj=" + Arrays.toString(projection) + ")");

        final ContentResolver cr = getContext().getContentResolver();
        final SQLiteDatabase db = mDbHelper.getReadableDatabase();
        final SelectionBuilder builder = new SelectionBuilder();
        Cursor cursor;

        final String limit = extractInt(uri, "limit", false);
        final String groupBy = extractString(uri, "groupBy", false);
        final String having = extractString(uri, "having", false);

        final int match = sUriMatcher.match(uri);
        switch (match) {
            /*case SPATIAL_REF_SYS: {
                cursor = builder
						.table(Tables.SPATIAL_REF_SYS)
						.where(selection, selectionArgs)
						.query(db, projection, groupBy, having, sortOrder, limit);
				cursor.setNotificationUri(cr, uri);
				return cursor;
			}
			case VECTOR_LAYERS: {
				cursor = builder
						.table(Tables.VECTOR_LAYERS)
						.where(selection, selectionArgs)
						.query(db, projection, groupBy, having, sortOrder, limit);
				cursor.setNotificationUri(cr, uri);
				return cursor;
			}
			case VECTOR_LAYERS_STATS: {
				cursor = builder
						.table(Tables.VECTOR_LAYERS_STATS)
						.where(selection, selectionArgs)
						.query(db, projection, groupBy, having, sortOrder, limit);
				cursor.setNotificationUri(cr, uri);
				return cursor;
			}*/
            case SPATIAL_INDEX: {
                final String vectorLayer = extractString(uri, "table", true);

                final VectorLayerMetadata layer = queryVectorLayerMetadata(db, vectorLayer);
                if (!layer.spatialIndexEnabled)
                    throw new IllegalArgumentException("Spatial index is not yet created for table: " + vectorLayer);

                final String xmin = extractDouble(uri, "xmin", true);
                final String ymin = extractDouble(uri, "ymin", true);
                final String xmax = extractDouble(uri, "xmax", true);
                final String ymax = extractDouble(uri, "ymax", true);
                final String srid = extractInt(uri, "srid", true);

                final String buildMbr = sqlFunc("BuildMbr", xmin, ymin, xmax, ymax, srid);
                //final String selectTableSrid = "(select srid from geometry_columns where f_table_name='" + vectorLayer + "')";
                final String searchFrame = sqlFunc("ST_Transform", buildMbr, String.valueOf(layer.srid));

                cursor = builder
                        .table(SpatiAtlasDatabase.Tables.SPATIAL_INDEX)
                        .where("f_table_name='DB=" + layer.dbAlias + "." + layer.table + "' AND search_frame=" + searchFrame, (String[]) null)
                        .where(selection, selectionArgs)
                        .query(db, projection, groupBy, having, sortOrder, limit);
                cursor.setNotificationUri(cr, uri);
                return cursor;
            }
            case VECTOR_LAYER: {
                final String layerName = uri.getLastPathSegment();

                final VectorLayerMetadata layer = queryVectorLayerMetadata(db, layerName);

                //if (!layer.spatialIndexEnabled)
                //	throw new IllegalArgumentException("Spatial index is not yet created for table: " + layerName);

                final boolean notPoints = !(layer.geomType.equals(SpatiAtlasContract.GeometryType.POINT) || layer.geomType.equals(SpatiAtlasContract.GeometryType.MULTIPOINT));

                final String tol = extractDouble(uri, "simplify", false);
                final String viewSrid = extractInt(uri, "srid", false);

                final String rawGeom = layer.geomColumn;
                final String simplified = (tol != null && notPoints) ? sqlFunc("SimplifyPreserveTopology", rawGeom, tol) : rawGeom;
                final String transformed = (viewSrid != null) ? sqlFunc("ST_Transform", simplified, viewSrid) : simplified;
                final String resultGeom = (transformed != null) ? transformed : rawGeom;
                final String wkbGeom = sqlFunc("AsBinary", resultGeom);

                cursor = builder
                        .table(layerName)
                        .map(layer.geomColumn, wkbGeom)
                        .where(selection, selectionArgs)
                        .query(db, projection, groupBy, having, sortOrder, limit);
                cursor.setNotificationUri(cr, uri);
                return cursor;
            }
            /*case TOWNS: {
				final String table = uri.getLastPathSegment();
				final String srid = extractInt(uri, "srid", false);
				final String rawGeom = getGeometryColumn(db, table);
				final String transformed = sqlFunc("ST_Transform", rawGeom, srid);
				final String resultGeom = (transformed != null) ? transformed : rawGeom;
				final String wkbGeom = sqlFunc("AsBinary", resultGeom);

				cursor = builder
						.table(table)
						.map(GeometryColumn.GEOMETRY, wkbGeom)
						.where(selection, selectionArgs)
						.query(db, projection, groupBy, having, sortOrder, limit);
				cursor.setNotificationUri(cr, uri);
				return cursor;
			}
			case HIGHWAYS:
			case MUNICIPALITIES:
			case COUNTIES:
			case REGIONS:
			case COUNTRIES: {
				final String table = uri.getLastPathSegment();		// TODO: sanitize
				final String srid = extractInt(uri, "srid", false);
				final String tol = extractDouble(uri, "simplify", false);

				final String rawGeom = getGeometryColumn(db, table);
				final String simplified = (tol != null) ? sqlFunc("SimplifyPreserveTopology", rawGeom, tol) : rawGeom;
				final String transformed = (srid != null) ? sqlFunc("ST_Transform", simplified, srid) : simplified;
				final String wkbGeom = sqlFunc("AsBinary", transformed);

				cursor = builder
						.table(table)
						.map(GeometryColumn.GEOMETRY, wkbGeom)
						.where(selection, selectionArgs)
						.query(db, projection, groupBy, having, sortOrder, limit);
				cursor.setNotificationUri(cr, uri);
				return cursor;
			}*/
            case SYSTEM_INFO: {
                String proj = "";
                proj += SPATIALITE_VERSION + "() AS " + SPATIALITE_VERSION + ", ";
                proj += PROJ4_VERSION + "() AS " + PROJ4_VERSION + ", ";
                proj += GEOS_VERSION + "() AS " + GEOS_VERSION + ", ";
                proj += HAS_PROJ + "() AS " + HAS_PROJ + ", ";
                proj += HAS_GEOS + "() AS " + HAS_GEOS + ", ";
                proj += HAS_GEOS_ADVANCED + "() AS " + HAS_GEOS_ADVANCED + ", ";
                proj += HAS_GEOS_TRUNK + "() AS " + HAS_GEOS_TRUNK + ", ";
                proj += HAS_LWGEOM + "() AS " + HAS_LWGEOM + ", ";
                proj += HAS_GEOCALLBACKS + "() AS " + HAS_GEOCALLBACKS + ", ";
                proj += HAS_MATHSQL + "() AS " + HAS_MATHSQL + ", ";
                proj += HAS_EPSG + "() AS " + HAS_EPSG + ", ";
                proj += HAS_ICONV + "() AS " + HAS_ICONV + ", ";
                proj += HAS_FREEXL + "() AS " + HAS_FREEXL + ", ";
                proj += HAS_LIBXML2 + "() AS " + HAS_LIBXML2;

                return db.rawQuery("SELECT " + proj, selectionArgs);
            }
            default: {
                throw new IllegalArgumentException("Unsupported URI: " + uri);
            }
        }
    }

    // The delete() method deletes rows based on the seletion or if an id is
    // provided then it deleted a single row. The methods returns the numbers
    // of records delete from the database. If you choose not to delete the data
    // physically then just update a flag here.
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        int deleteCount = 0;

        return deleteCount;
    }

    // The update method() is same as delete() which updates multiple rows
    // based on the selection or a single row if the row id is provided. The
    // update method returns the number of updated rows.
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        int updateCount = 0;

        return updateCount;
    }

    private void notifyChange(Uri uri, boolean syncToNetwork) {
        Context context = getContext();
        context.getContentResolver().notifyChange(uri, null, syncToNetwork);
    }

    /**
     * Apply the given set of {@link ContentProviderOperation}, executing inside
     * a {@link SQLiteDatabase} transaction. All changes will be rolled back if
     * any single one fails.
     */
    @Override
    public ContentProviderResult[] applyBatch(ArrayList<ContentProviderOperation> operations)
            throws OperationApplicationException {
        final SQLiteDatabase db = mDbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            final int numOperations = operations.size();
            final ContentProviderResult[] results = new ContentProviderResult[numOperations];
            for (int i = 0; i < numOperations; i++) {
                results[i] = operations.get(i).apply(this, results, i);
            }
            db.setTransactionSuccessful();
            return results;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Build a simple {@link SelectionBuilder} to match the requested
     * {@link Uri}. This is usually enough to support {@link #insert},
     * {@link #update}, and {@link #delete} operations.
     */
    private SelectionBuilder buildSimpleSelection(Uri uri) {
        final SelectionBuilder builder = new SelectionBuilder();
        final int match = sUriMatcher.match(uri);
        switch (match) {
            case TOWNS: {
                return builder.table(SpatiAtlasDatabase.Tables.TOWNS);
            }
            case TOWNS_ID: {
                final String townId = SpatiAtlasContract.Towns.getTownId(uri);
                return builder.table(SpatiAtlasDatabase.Tables.TOWNS)
                        .where(BaseColumns._ID + "=?", townId);
            }
            default: {
                throw new UnsupportedOperationException("Unknown uri for " + match + ": " + uri);
            }
        }
    }

    /**
     * Build an advanced {@link SelectionBuilder} to match the requested
     * {@link Uri}. This is usually only used by {@link #query}, since it
     * performs table joins useful for {@link Cursor} data.
     */
    private SelectionBuilder buildExpandedSelection(Uri uri, int match) {
        final SelectionBuilder builder = new SelectionBuilder();
        final Set<String> params = getQueryParameterNames(uri);
        switch (match) {
            default: {
                throw new UnsupportedOperationException("Unknown uri: " + uri);
            }
        }
    }

    /**
     * Returns a set of the unique names of all query parameters. Iterating
     * over the set will return the names in order of their first occurrence.
     *
     * @return a set of decoded names
     * @throws UnsupportedOperationException if this isn't a hierarchical URI
     */
    public Set<String> getQueryParameterNames(Uri uri) {
        if (uri.isOpaque()) {
            throw new UnsupportedOperationException("NOT_HIERARCHICAL");
        }

        String query = uri.getEncodedQuery();
        if (query == null) {
            return Collections.emptySet();
        }

        Set<String> names = new LinkedHashSet<String>();
        int start = 0;
        do {
            int next = query.indexOf('&', start);
            int end = (next == -1) ? query.length() : next;

            int separator = query.indexOf('=', start);
            if (separator > end || separator == -1) {
                separator = end;
            }

            String name = query.substring(start, separator);
            names.add(Uri.decode(name));

            // Move start to end of name.
            start = end + 1;
        } while (start < query.length());

        return Collections.unmodifiableSet(names);
    }

    private VectorLayerMetadata queryVectorLayerMetadata(SQLiteDatabase db, String vectorLayer) {
        Cursor cursor = null;
        try {
            final String[] qual = vectorLayer.split("\\.");
            final String attachedDb = (qual.length == 2) ? qual[0] : null;
            final String table = (qual.length == 2) ? qual[1] : vectorLayer;
            if (TextUtils.isEmpty(table))
                throw new IllegalArgumentException("Malformed vector layer name: " + vectorLayer);

            final String dbTable = (!TextUtils.isEmpty(attachedDb) ? attachedDb + "." : "") + SpatiAtlasDatabase.Tables.VECTOR_LAYERS;
            cursor = new SelectionBuilder()
                    .table(dbTable)
                    .where("table_name=?", new String[]{table})
                    .query(db, null, null, null, null, null);
            if (!cursor.moveToFirst())
                throw new IllegalArgumentException("Vector layer not found: " + vectorLayer);

            final int colLayerType = cursor.getColumnIndexOrThrow(SpatiAtlasContract.VectorLayersMetadataColumns.LAYER_TYPE);
            final int colGeomColumn = cursor.getColumnIndexOrThrow(SpatiAtlasContract.VectorLayersMetadataColumns.GEOMETRY_COLUMN);
            final int colGeomType = cursor.getColumnIndexOrThrow(SpatiAtlasContract.VectorLayersMetadataColumns.GEOMETRY_TYPE);
            final int colCoordDim = cursor.getColumnIndexOrThrow(SpatiAtlasContract.VectorLayersMetadataColumns.COORD_DIMENSION);
            final int colSrid = cursor.getColumnIndexOrThrow(SpatiAtlasContract.VectorLayersMetadataColumns.SRID);
            final int colSpIndexEnabled = cursor.getColumnIndexOrThrow(SpatiAtlasContract.VectorLayersMetadataColumns.SPATIAL_INDEX_ENABLED);

            VectorLayerMetadata layer = new VectorLayerMetadata();
            layer.dbAlias = (!TextUtils.isEmpty(attachedDb)) ? attachedDb : "main";
            layer.table = table;
            layer.layerType = cursor.getString(colLayerType);
            layer.geomColumn = cursor.getString(colGeomColumn);
            layer.geomType = SpatiAtlasContract.GeometryType.fromInt(cursor.getInt(colGeomType));
            layer.coordDim = cursor.getInt(colCoordDim);
            layer.srid = cursor.getInt(colSrid);
            layer.spatialIndexEnabled = cursor.getInt(colSpIndexEnabled) != 0;

            return layer;
        } finally {
            Utils.closeSilently(cursor);
        }
    }

    private String extractString(Uri uri, String paramName, boolean isRequired) {
        String param = uri.getQueryParameter(paramName);
        // TODO: Sanitize string against SQL injection
        if (param == null) {
            if (isRequired)
                throw new IllegalArgumentException("Missing String parameter " + paramName + " in query Uri: " + uri);
            return null;
        }

        return param;
    }

    private String extractInt(Uri uri, String paramName, boolean isRequired) {
        String param = uri.getQueryParameter(paramName);
        if (param == null) {
            if (isRequired)
                throw new IllegalArgumentException("Missing Integer parameter " + paramName + " in query Uri: " + uri);
            return null;
        }
        param = String.valueOf(Integer.valueOf(param));

        return param;
    }

    private String extractDouble(Uri uri, String paramName, boolean isRequired) {
        String param = uri.getQueryParameter(paramName);
        if (param == null) {
            if (isRequired)
                throw new IllegalArgumentException("Missing Double parameter " + paramName + " in query Uri: " + uri);
            return null;
        }
        param = String.valueOf(Double.valueOf(param));

        return param;
    }

    private String sqlFunc(String funcName, String... params) {
        final StringBuilder b = new StringBuilder(funcName);
        b.append("(");
        boolean first = true;
        for (String p : params) {
            if (p == null)
                return null;

            if (!first)
                b.append(", ");
            else
                first = false;
            b.append(p);
        }
        b.append(")");
        return b.toString();
    }

    public interface Subquery {

        /**
         * @param ?1 point x
         * @param ?2 point y
         * @param ?3 srcSRID
         * @param ?4 destSRID
         * @return p:(x, y)
         */
        String TRANSFORM_POINT_XY =
                "(select X(ST_Transform(MakePoint(?1, ?2, ?3), ?4)) as x, " +
                        "Y(ST_Transform(MakePoint(?1, ?2, ?3), ?4)) as y) as p";

        /**
         * @param ?1 rect x1
         * @param ?2 rect y1
         * @param ?3 rect x2
         * @param ?4 rect y2
         * @param ?5 srcSRID
         * @param ?6 destSRID
         * @return bounds:(xmin, ymin, xmax, ymax)
         */
        String TRANSFORM_RECT_XY =
                "(select Min(X(p1), X(p2)) as xmin, " +
                        "Min(Y(p1), Y(p2)) as ymin, " +
                        "Max(X(p1), X(p2)) as xmax, " +
                        "Max(Y(p1), Y(p2)) as ymax " +
                        "from (select ST_Transform(MakePoint(?, ?, viewSRID), srid) as p1, " +
                        "ST_Transform(MakePoint(?, ?, viewSRID), srid) as p2" +
                        "from (select srid from geometry_columns where f_table_name='?')," +
                        "(select ? as viewSRID)))";

        /**
         * @param mbr rect1
         * @param clip rect2
         * @return 1 if mbr and clip has a common area, 0 otherwise
         */
        String CLIP_RECT_SELECTION =
                "NOT(mbr.xmin > clip.xmax OR mbr.ymin > clip.ymax OR mbr.xmax < clip.xmin OR mbr.ymax < clip.ymin)";
    }

    /**
     * {@link SpatiAtlasContract} fields that are fully qualified with a specific
     * parent {@link SpatiAtlasDatabase.Tables}. Used when needed to work around SQL ambiguity.
     */
    private interface Qualified {
    }

    class VectorLayerMetadata {
        String dbAlias;
        String table;
        String layerType;
        String geomColumn;
        SpatiAtlasContract.GeometryType geomType;
        int coordDim;
        int srid;
        boolean spatialIndexEnabled;
    }
}