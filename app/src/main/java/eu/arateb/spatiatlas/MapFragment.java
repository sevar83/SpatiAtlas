package eu.arateb.spatiatlas;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.BaseColumns;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.io.WKBReader;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import eu.arateb.spatiatlas.provider.SpatiAtlasContract;
import eu.arateb.spatiatlas.util.Preconditions;
import eu.arateb.spatiatlas.util.Utils;

public class MapFragment extends SupportMapFragment implements
        LoaderCallbacks<Cursor>,
        GoogleMap.OnCameraChangeListener,
        GoogleMap.OnMarkerClickListener {
    private static final String TAG = MapFragment.class.getSimpleName();

    private static final LatLng PARMA = new LatLng(44.801485, 10.327904);
    private static final LatLng PESCARA = new LatLng(42.464079, 14.213932);

    private static final LatLng START_LOCATION = PESCARA;
    private static final float START_ZOOM = 10;

    private static final int SRID_WGS84 = 4326;

    private static final int MAX_MARKERS = 100;
    private static final int MAX_GEOMETRIES = 8192;

    private static final Bitmap BMP_MARKER = Bitmap.createBitmap(3, 3, Bitmap.Config.RGB_565);

    static {
        BMP_MARKER.eraseColor(Color.YELLOW);
    }

    private final ContentObserver mObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            /*if (!isAdded())
            {
				return;
			}

			setupMap(false);

			// reload data from loaders
			LoaderManager lm = getActivity().getSupportLoaderManager();

			Loader<Cursor> loader = lm.getLoader(ClipRegionsQuery._TOKEN);
			if (loader != null) {
				loader.forceLoad();
			}*/
        }
    };
    private GoogleMap mMap;
    private List<Layer> mLayers = new ArrayList<Layer>();
    //private GeometryCache mGeomCache;
    private HashSet<String> mGeomKeys;

    private static String createGeomKey(int prefix, long rowid) {
        return String.format("%X", prefix) + "-" + String.format("%X", rowid);
    }

    private static int randColor(Geometry geom) {
        final int hash = geom.hashCode();
        return Color.argb(128, Color.red(hash), Color.green(hash), Color.blue(hash));
    }

    @Override
    public void onCreate(Bundle bundle) {
        // TODO Auto-generated method stub
        super.onCreate(bundle);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        //attachExternalDB();

        if (mMap == null) {
            getMapAsync(new OnMapReadyCallback() {
                @Override
                public void onMapReady(GoogleMap googleMap) {
                    setupMap(googleMap, true);
                }
            });
        }
    }

    private void attachExternalDB() {
        File extStore = Environment.getExternalStorageDirectory();
        File extDbFile = new File(extStore.getAbsolutePath() + "/" + "parma-dxf.sqlite");
        if (!extDbFile.exists()) {
            throw new RuntimeException("Cannot find external DB " + extDbFile.getPath() + ". Probably external SD card is not mounted");
        }

        final Bundle params = new Bundle();
        params.putString(SpatiAtlasContract.Methods.AttachDb.PARAM_FILEPATH, extDbFile.getAbsolutePath());

        final ContentResolver cr = getActivity().getContentResolver();
        final Bundle result = cr.call(SpatiAtlasContract.BASE_CONTENT_URI,
                SpatiAtlasContract.Methods.AttachDb.METHOD,
                "Parma",
                params);

        if (result == null) {
            throw new RuntimeException("Cannot attach DB " + extDbFile.getAbsolutePath());
        }
    }

    public int addLayer(Layer layer) {
        if (mLayers.contains(layer))
            return -1;

        if (!mLayers.add(layer))
            return -1;

        final int id = mLayers.indexOf(layer);
        layer.setLoaderId(id);

        return id;
    }

    public boolean removeLayer(Layer layer) {
        final LoaderManager lm = getLoaderManager();
        final int loaderId = layer.getLoaderId();
        if (loaderId != -1) {
            lm.destroyLoader(loaderId);
            layer.setLoaderId(-1);
        }
        return mLayers.remove(layer);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        activity.getContentResolver().registerContentObserver(
                SpatiAtlasContract.Towns.CONTENT_URI, true, mObserver);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    private void setupMap(GoogleMap googleMap, boolean resetCamera) {
        mMap = googleMap;
        mMap.setMapType(GoogleMap.MAP_TYPE_NONE);
        mMap.setOnMarkerClickListener(this);
        mMap.setOnCameraChangeListener(this);

        if (resetCamera) {
            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.fromLatLngZoom(
                    START_LOCATION, START_ZOOM)));
        }

        //mGeomCache = GeometryCache.getInstance(getFragmentManager(), 0.3f);
        mGeomKeys = new HashSet<String>(MAX_GEOMETRIES);

        final Layer townsLayer = new LayerBuilder(TownsQuery.TABLE, SpatiAtlasContract.Towns._ID)
                .projection(TownsQuery.PROJECTION)
                .limit(2048)
                .build();

        final Layer highwaysLayer = new LayerBuilder(HighwaysQuery.TABLE, SpatiAtlasContract.Highways._ID)
                .projection(HighwaysQuery.PROJECTION)
                .limit(1024)
                .build();

        final Layer municipalitiesLayer = new LayerBuilder(MunicipalitiesQuery.TABLE, SpatiAtlasContract.Municipalities._ID)
                .projection(MunicipalitiesQuery.PROJECTION)
                .simplify(200.0)
                .limit(256)
                .build();

        final Layer countiesLayer = new LayerBuilder(CountiesQuery.TABLE, SpatiAtlasContract.Counties._ID)
                .projection(CountiesQuery.PROJECTION)
                .simplify(200.0)
                .build();

        final Layer regionsLayer = new LayerBuilder(RegionsQuery.TABLE, SpatiAtlasContract.Regions._ID)
                .projection(RegionsQuery.PROJECTION)
                .simplify(200.0)
                .build();

        final Layer parmaPointsLayer = new LayerBuilder(ParmaPointsQuery.TABLE, BaseColumns._ID)
                .projection(ParmaPointsQuery.PROJECTION)
                .geometryColumn("geometry")
                .build();

        final Layer parmaLinesLayer = new LayerBuilder(ParmaLinesQuery.TABLE, BaseColumns._ID)
                .projection(ParmaLinesQuery.PROJECTION)
                .geometryColumn("geometry")
                .build();

        final Layer parmaPolygonsLayer = new LayerBuilder(ParmaPolygonsQuery.TABLE, BaseColumns._ID)
                .projection(ParmaPolygonsQuery.PROJECTION)
                .geometryColumn("geometry")
                .build();

        addLayer(townsLayer);
        //addLayer(municipalitiesLayer);
        addLayer(highwaysLayer);
        addLayer(countiesLayer);
        //addLayer(regionsLayer);

        //addLayer(parmaPolygonsLayer);
        //addLayer(parmaLinesLayer);
        //addLayer(parmaPointsLayer);

        final LoaderManager loaderManager = getLoaderManager();
        for (Layer layer : mLayers) {
            final int loaderId = layer.getLoaderId();
            if (loaderId != -1)
                loaderManager.initLoader(loaderId, null, this);
        }

        updateProgress();

        Log.d(TAG, "Map setup complete.");
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle data) {
        Layer layer = mLayers.get(id);
        if (layer == null)
            return null;

        return layer.onCreateLoader(data);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        final int idLoader = cursorLoader.getId();
        final Layer layer = mLayers.get(idLoader);
        if (layer != null)
            layer.onLoadFinished(cursor);

        updateProgress();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        updateProgress();
    }

    private void updateProgress() {
        final boolean loading = getLoaderManager().hasRunningLoaders();
        getActivity().setProgressBarIndeterminateVisibility(loading);
    }

    @Override
    public void onCameraChange(CameraPosition cam) {
        Log.d(TAG, cam.toString());

        for (int id = 0; id < mLayers.size(); id++) {
            if (mLayers.get(id) == null)
                continue;

            getLoaderManager().restartLoader(id, null, this);
        }
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        // TODO Auto-generated method stub
        return false;
    }

    private void addGeometry(Geometry geom, String name) {
        final int color = randColor(geom);
        if (geom instanceof Point) {
            addPoint(name, (Point) geom);
        } else if (geom instanceof LineString) {
            addLineString(name, (LineString) geom);
        } else if (geom instanceof Polygon) {
            addPolygon(name, (Polygon) geom, color);
        } else if (geom instanceof MultiPolygon) {
            addMultiPolygon(name, (MultiPolygon) geom, color);
        }
    }

    private void addPoint(String name, Point pnt) {
        final LatLng latlon = new LatLng(pnt.getY(), pnt.getX());

        mMap.addMarker(new MarkerOptions()
                .icon(BitmapDescriptorFactory.fromBitmap(BMP_MARKER))
                .title(name)
                .position(latlon)
                .title(name));
    }

    private void addLineString(String name, LineString lineString) {
        final PolylineOptions polyOptions = new PolylineOptions();

        for (int iPnt = 0; iPnt < lineString.getNumPoints(); iPnt++) {
            final Point p = lineString.getPointN(iPnt);
            polyOptions.add(new LatLng(p.getY(), p.getX()));
        }

        mMap.addPolyline(polyOptions
                .width(1)
                .color(Color.YELLOW));
    }

    private void addPolygon(String name, Polygon poly, int color) {
        final PolygonOptions polyOptions = new PolygonOptions();
        final LineString exteriorRing = poly.getExteriorRing();
        for (int iPnt = 0; iPnt < exteriorRing.getNumPoints(); iPnt++) {
            final Point p = exteriorRing.getPointN(iPnt);
            polyOptions.add(new LatLng(p.getY(), p.getX()));
        }

        for (int iHole = 0; iHole < poly.getNumInteriorRing(); iHole++) {
            final LineString interiorRing = poly.getInteriorRingN(iHole);
            final List<LatLng> holePnts = new ArrayList<LatLng>();
            for (int iPnt = 0; iPnt < interiorRing.getNumPoints(); iPnt++) {
                final Point pnt = interiorRing.getPointN(iPnt);
                holePnts.add(new LatLng(pnt.getY(), pnt.getX()));
            }

            polyOptions.addHole(holePnts);
        }

        mMap.addPolygon(polyOptions
                .strokeWidth(1)
                .strokeColor(Color.DKGRAY)
                .fillColor(color));
    }

    private void addMultiPolygon(String name, MultiPolygon multiPoly, int color) {
        for (int iPoly = 0; iPoly < multiPoly.getNumGeometries(); iPoly++) {
            final Polygon poly = (Polygon) multiPoly.getGeometryN(iPoly);
            final int colorPoly = (color != -1) ? color : randColor(poly);
            addPolygon(name + "-" + iPoly, poly, colorPoly);
        }
    }

    private interface TownsQuery {
        //int _TOKEN = 0x1;

        String TABLE = SpatiAtlasContract.Towns.TABLE;

/*		int TOWN_ID = 0;
        int TOWN_NAME = 1;
		int TOWN_GEOM = 2;*/

        String[] PROJECTION = new String[]
                {
                        SpatiAtlasContract.Towns._ID,
                        SpatiAtlasContract.Towns.TOWN_NAME,
                        SpatiAtlasContract.Towns.GEOMETRY,
                };
    }


    private interface HighwaysQuery {
        //int _TOKEN = 0x2;

        String TABLE = SpatiAtlasContract.Highways.TABLE;

/*		int HIGHWAY_ID = 0;
		int HIGHWAY_NAME = 1;
		int HIGHWAY_GEOM = 2;*/

        String[] PROJECTION = new String[]
                {
                        SpatiAtlasContract.Highways._ID,
                        SpatiAtlasContract.Highways.HIGHWAY_NAME,
                        SpatiAtlasContract.Highways.GEOMETRY
                };
    }

    private interface MunicipalitiesQuery {
        //int _TOKEN = 0x3;

        String TABLE = SpatiAtlasContract.Municipalities.TABLE;

/*		int MUNICIPALITY_ID = 0;
		int MUNICIPALITY_NAME = 1;
		int MUNICIPALITY_GEOM = 2;*/

        String[] PROJECTION = new String[]
                {
                        SpatiAtlasContract.Municipalities._ID,
                        SpatiAtlasContract.Municipalities.MUNICIPALITY_NAME,
                        SpatiAtlasContract.Municipalities.GEOMETRY,
                };
    }

    private interface CountiesQuery {
        //int _TOKEN = 0x4;

        String TABLE = SpatiAtlasContract.Counties.TABLE;

/*		int COUNTY_ID = 0;
		int COUNTY_NAME = 1;
		int COUNTY_GEOM = 2;*/

        String[] PROJECTION = new String[]
                {
                        SpatiAtlasContract.Counties._ID,
                        SpatiAtlasContract.Counties.COUNTY_NAME,
                        SpatiAtlasContract.Counties.GEOMETRY
                };
    }

    private interface RegionsQuery {
        //int _TOKEN = 0x5;

        String TABLE = SpatiAtlasContract.Regions.TABLE;

		/*int REGION_ID = 0;
		int REGION_NAME = 1;
		int REGION_GEOM = 2;*/

        String[] PROJECTION = new String[]
                {
                        SpatiAtlasContract.Regions._ID,
                        SpatiAtlasContract.Regions.REGION_NAME,
                        SpatiAtlasContract.Regions.GEOMETRY
                };
    }

    private interface ParmaPointsQuery {
        String TABLE = "Parma.parma_point_layer_2d";

        String[] PROJECTION = new String[]
                {
                        "feature_id AS _id",
                        "geometry"
                };
    }

    private interface ParmaLinesQuery {
        String TABLE = "Parma.parma_line_layer_2d";

        String[] PROJECTION = new String[]
                {
                        "feature_id AS _id",
                        "geometry"
                };
    }

    private interface ParmaPolygonsQuery {
        String TABLE = "Parma.parma_polyg_layer_2d";

        String[] PROJECTION = new String[]
                {
                        "feature_id AS _id",
                        "geometry"
                };
    }

    private interface ParmaTextQuery {
        String TABLE = "Parma.parma_text_layer_2d";

        String[] PROJECTION = new String[]
                {
                        "feature_id AS _id",
                        "geometry",
                        "label"
                };
    }

    private static abstract class MultiPhaseLoader extends CursorLoader {
        private int mPhase = -1;
        private Bundle mPhaseArguments;

        public MultiPhaseLoader(Context context) {
            super(context);
        }

        protected void gotoPhase(int phase, Bundle phaseArguments) {
            final boolean wasStarted = isStarted();
            if (wasStarted)
                reset();
            mPhaseArguments = phaseArguments;
            setPhase(phase);
        }

        public int getPhase() {
            return mPhase;
        }

        protected void setPhase(int phase) {
            if (isStarted())
                throw new IllegalStateException("Phase can be changed after load has finished/stopped");

            mPhase = phase;
        }

        @Override
        protected void onStartLoading() {
            if (mPhase == -1)
                throw new IllegalStateException("Not set initial phase");

            super.onStartLoading();
        }

        protected Bundle getPhaseArguments() {
            return mPhaseArguments;
        }

        public abstract int getPhaseCount();
    }

    private static class GeomLoader extends MultiPhaseLoader {
        public static final int PHASE_CLIP_BOUNDS = 0;
        public static final int PHASE_LOAD_GEOM = 1;

        public static final String PARAM_SIMPLIFICATION_TOLERANCE = "simplify";
        public static final String PARAM_LIMIT = "limit";
        private final Parameters mParams = new Parameters();

        public GeomLoader(Context context,
                          String table,
                          LatLngBounds bounds,
                          String[] projection,
                          String selection,
                          String[] selectionArgs,
                          String sortOrder,
                          Bundle parameters) {
            super(context);

            mParams.table = Preconditions.checkNotNull(table);
            mParams.xmin = bounds.southwest.longitude;
            mParams.ymin = bounds.southwest.latitude;
            mParams.xmax = bounds.northeast.longitude;
            mParams.ymax = bounds.northeast.latitude;
            mParams.projection = projection;
            mParams.selection = selection;
            mParams.selectionArgs = selectionArgs;
            mParams.sortOrder = sortOrder;

            if (parameters == null)
                parameters = new Bundle();
            mParams.simplificationTolerance = parameters.getDouble(PARAM_SIMPLIFICATION_TOLERANCE, 0);
            mParams.limit = parameters.getInt(PARAM_LIMIT, MAX_GEOMETRIES);

            gotoPhase(0, null);
        }

        @Override
        protected void setPhase(int phase) {
            if (phase == PHASE_CLIP_BOUNDS) {
                final Uri uri = buildClipBoundsQueryUri(mParams);
                setUri(uri);
                setProjection(new String[]{SpatiAtlasContract.SpatialIndex.ROWID});
                setSelection(null);
                setSelectionArgs(null);
                setSortOrder(null);
            } else if (phase == PHASE_LOAD_GEOM) {
                final Uri uri = buildLoadGeomQueryUri(mParams);
                setUri(uri);
                setProjection(mParams.projection);
                setSelection(buildIdListFilter(mParams.selection, getPhaseArguments().getLongArray("nonCachedGeoms")));
                setSelectionArgs(mParams.selectionArgs);
                setSortOrder(mParams.sortOrder);
            }

            super.setPhase(phase);
        }

        @Override
        public int getPhaseCount() {
            return 2;
        }

        private Uri buildClipBoundsQueryUri(Parameters params) {
            return SpatiAtlasContract.SpatialIndex
                    .buildQueryUri(params.table, params.xmin, params.ymin, params.xmax, params.ymax, params.srid)
                    .buildUpon()
                    .appendQueryParameter("limit", String.valueOf(params.limit))
                    .build();
        }

        private Uri buildLoadGeomQueryUri(Parameters params) {
            return SpatiAtlasContract.GeometryTable.buildUri(mParams.table, mParams.srid, mParams.simplificationTolerance, mParams.limit);
        }

        private String buildIdListFilter(String selection, long[] idList) {
            final StringBuilder where = new StringBuilder();

            where.append("ROWID IN ");
            where.append("(");
            if (idList != null && idList.length > 0) {
                boolean first = true;
                for (long id : idList) {
                    if (!first)
                        where.append(",");
                    else
                        first = false;
                    where.append(id);
                }
            }
            where.append(")");

            if (!TextUtils.isEmpty(selection)) {
                if (where.length() > 0)
                    where.append(" AND ");
                where.append(selection);
            }

            return (where.length() != 0) ? where.toString() : null;
        }

        private class Parameters {
            String table;
            double xmin;
            double ymin;
            double xmax;
            double ymax;
            int srid = SRID_WGS84;
            String[] projection;
            String selection;
            String[] selectionArgs;
            String sortOrder;

            double simplificationTolerance;
            int limit = MAX_GEOMETRIES;
        }
    }

    public class Layer {
        protected final String mTable;

        protected int mLoaderId = -1;
        protected String mIdColumn = BaseColumns._ID;
        protected String mGeomColumn = SpatiAtlasContract.GeometryColumn.GEOMETRY;
        protected String mNameColumn = "name";
        protected String[] mProjection;
        protected String mSelection;
        protected String[] mSelectionArgs;
        protected String mSortOrder;

        protected double mSimplificationTolerance = -1;
        protected int mLimit = -1;

        private Layer(String table, String idColumn) {
            mTable = table;
            mIdColumn = idColumn;
        }

        public int getLoaderId() {
            return mLoaderId;
        }

        void setLoaderId(int id) {
            if (mLoaderId != -1 && id != -1)
                throw new IllegalStateException("Loader id is already set");

            mLoaderId = id;
        }

        public GeomLoader getLoader() {
            final Loader<Cursor> loader = (getLoaderManager().getLoader(mLoaderId));
            return (GeomLoader) loader;
        }

        public GeomLoader onCreateLoader(Bundle args) {
            final LatLngBounds bounds = mMap.getProjection().getVisibleRegion().latLngBounds;

            final Bundle params = new Bundle();
            if (mSimplificationTolerance != -1)
                params.putDouble(GeomLoader.PARAM_SIMPLIFICATION_TOLERANCE, mSimplificationTolerance);
            if (mLimit != -1)
                params.putInt(GeomLoader.PARAM_LIMIT, mLimit);

            final GeomLoader loader = new GeomLoader(
                    getActivity().getApplicationContext(),
                    mTable,
                    bounds,
                    mProjection,
                    mSelection,
                    mSelectionArgs,
                    mSortOrder,
                    params);

            return loader;
        }

        public void onLoadFinished(Cursor cursor) {
            final GeomLoader loader = getLoader();
            final int phase = loader.getPhase();

            if (phase == GeomLoader.PHASE_CLIP_BOUNDS) {
                onClipBoundsFinished(cursor);
            } else {
                onGeometryLoadingFinished(cursor);
            }

            final boolean loading = getLoaderManager().hasRunningLoaders();
            getActivity().setProgressBarIndeterminateVisibility(loading);
        }

        protected void onClipBoundsFinished(Cursor cursor) {
            final int colRowid = cursor.getColumnIndex(SpatiAtlasContract.SpatialIndex.ROWID);
            final long[] nonCachedGeomsFull = new long[cursor.getCount()];
            final long[] cachedGeomsFull = new long[cursor.getCount()];

            int e = 0, m = 0;
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                final long rowid = cursor.getLong(colRowid);
                final String key = createGeomKey(mLoaderId, rowid);
                if (mGeomKeys.contains(key))
                    cachedGeomsFull[e++] = rowid;
                else
                    nonCachedGeomsFull[m++] = rowid;
                cursor.moveToNext();
            }

            final long[] cachedGeoms = new long[e];
            final long[] missingIds = new long[m];

            System.arraycopy(cachedGeomsFull, 0, cachedGeoms, 0, e);
            System.arraycopy(nonCachedGeomsFull, 0, missingIds, 0, m);

            final Bundle phaseArgs = new Bundle();
            phaseArgs.putLongArray("cachedGeoms", cachedGeoms);
            phaseArgs.putLongArray("nonCachedGeoms", missingIds);

            final GeomLoader loader = getLoader();
            loader.gotoPhase(GeomLoader.PHASE_LOAD_GEOM, phaseArgs);
            loader.startLoading();
        }

        protected void onGeometryLoadingFinished(Cursor cursor) {
            try {
                if (cursor == null || cursor.getCount() == 0)
                    return;

                Log.v(TAG, "Geometries loaded: " + cursor.getCount());

                final int colId = cursor.getColumnIndexOrThrow(mIdColumn);
                final int colGeom = cursor.getColumnIndexOrThrow(mGeomColumn);
                final int colName = cursor.getColumnIndex(mNameColumn);
                final WKBReader geomReader = new WKBReader();

                cursor.moveToFirst();
                while (!cursor.isAfterLast()) {
                    final long id = cursor.getLong(colId);
                    final String name = (colName != -1) ? cursor.getString(colName) : null;
                    final String key = createGeomKey(mLoaderId, id);
                    if (mGeomKeys.contains(key))
                        continue;

                    Geometry geom;
                    //Geometry geom = mGeomCache.getGeometry(key);
                    //if (geom == null)
                    //{
                    try {
                        final byte[] geomBlob = cursor.getBlob(colGeom);
                        if (geomBlob == null) {
                            Log.e(TAG, "Malformed or null geometry with id=" + id + ", name=" + name + " in table " + mTable);
                            continue;
                        }
                        geom = geomReader.read(geomBlob);
                        //mGeomCache.putGeometry(key, geom);
                        addGeometry(geom, name);
                    } catch (Exception ex) {
                        Log.e(TAG, "Failed to parse geometry blob with id=" + id + ", name=" + name + " in table " + mTable);
                        ex.printStackTrace();
                    } finally {
                        // Add the key even if geometry is broken just to prevent perpetual attempts to load it
                        mGeomKeys.add(key);

                        cursor.moveToNext();
                    }
                    //}
                }
            } catch (SQLiteException sqlEx) {
                Log.e(TAG, sqlEx.getMessage());
            } finally {
                Utils.closeSilently(cursor);
                final GeomLoader loader = getLoader();
                loader.gotoPhase(0, null);
            }
        }

        //protected abstract int getMainPropertyColumn();

        // Styling
        /*protected abstract Integer getStrokeColor(Geometry geometry);
		protected abstract Float getStrokeWidth(Geometry geometry);
		protected abstract Integer getFillColor(Geometry geometry);
		protected abstract String getText(Geometry geometry);*/
    }

    public class LayerBuilder {
		/*private final int mId;
		private final String mTable;
		private final String mIdColumn;
		private String[] mProjection;
		private String mSelection;
		private String[] mSelectionArgs;
		private String mSortOrder;*/

        private final Layer mLayer;

        public LayerBuilder(String table, String idColumn) {
			/*mId = idLayer;
			mTable = table;
			mIdColumn = idColumn;*/

            mLayer = new Layer(table, idColumn);
        }

        public LayerBuilder projection(String[] projection) {
            mLayer.mProjection = projection;
            return this;
        }

        public LayerBuilder selection(String selection) {
            mLayer.mSelection = selection;
            return this;
        }

        public LayerBuilder selectionArgs(String[] selectionArgs) {
            mLayer.mSelectionArgs = selectionArgs;
            return this;
        }

        public LayerBuilder sortOrder(String sortOrder) {
            mLayer.mSortOrder = sortOrder;
            return this;
        }

        public LayerBuilder geometryColumn(String column) {
            mLayer.mGeomColumn = column;
            return this;
        }

        public LayerBuilder simplify(double simplificationTolerance) {
            mLayer.mSimplificationTolerance = simplificationTolerance;
            return this;
        }

        public LayerBuilder limit(int limit) {
            mLayer.mLimit = limit;
            return this;
        }

        // TODO: Styles

        public Layer build() {
            return mLayer;
        }
    }
}
