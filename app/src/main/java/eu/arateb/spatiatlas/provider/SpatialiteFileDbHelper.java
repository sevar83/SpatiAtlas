package eu.arateb.spatiatlas.provider;

import android.content.Context;
import android.net.Uri;

import org.spatialite.database.SQLiteDatabase;
import org.spatialite.database.SQLiteOpenHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class SpatialiteFileDbHelper extends SQLiteOpenHelper {
    private static final int DB_VERSION = 1;

    private static final int SOURCE_NEW = 0;
    private static final int SOURCE_ASSET = 1;
    private static final int SOURCE_URI = 2;

    private final Context mContext;
    private final String mDbPath;
    private final String mAssetName;
    private final int mSourceType;
    private final Uri mDbUri;

    /**
     * Create database helper from asset source
     *
     * @param context
     * @param dbName
     */
    public SpatialiteFileDbHelper(Context context, String assetName, String dbName) {
        super(context, dbName, null, DB_VERSION);
        mContext = context;
        mDbPath = getDatabaseDir() + dbName;
        mAssetName = assetName;
        mDbUri = null;
        mSourceType = SOURCE_ASSET;
    }

    /**
     * Create database helper from file (file:// or content:// schema)
     *
     * @param context
     * @param dbName
     */
    public SpatialiteFileDbHelper(Context context, Uri dbUri, String dbName) {
        super(context, dbName, null, DB_VERSION);
        mContext = context;
        mDbPath = getDatabaseDir() + dbName;
        mAssetName = null;
        mDbUri = dbUri;
        mSourceType = SOURCE_URI;
    }

    /**
     * Create database helper with an empty new database
     *
     * @param context
     * @param dbName
     */
    public SpatialiteFileDbHelper(Context context, String dbName) {
        super(context, dbName, null, DB_VERSION);
        mContext = context;
        mDbPath = getDatabaseDir() + dbName;
        mAssetName = null;
        mDbUri = null;
        mSourceType = SOURCE_NEW;
    }

    /**
     * Check if the database already exist to avoid re-copying the file each time
     * you open the application.
     *
     * @return true if it exists, false if it doesn't
     */
    private static boolean checkDataBase(String dbPath) {
        /*SQLiteDatabase checkDB = null;

		try
		{
			checkDB = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY);
		}
		catch (SQLiteException e)
		{
			// database does't exist yet.
		}

		if (checkDB != null)
		{
			checkDB.close();
		}

		return checkDB != null ? true : false;*/

        return new File(dbPath).exists();
    }

    /**
     * Copies your database from your local assets-folder to the just created
     * empty database in the system folder, from where it can be accessed and
     * handled. This is done by transferring bytestream.
     **/
    private static void copyDataBase(String dbPath, InputStream input) throws IOException {
        // Open the empty db as the output stream
        File outputDir = new File(dbPath);
        outputDir.getParentFile().mkdirs();
        OutputStream output = new FileOutputStream(outputDir);

        // transfer bytes from the input file to the output file
        byte[] buffer = new byte[1024];
        int length;
        while ((length = input.read(buffer)) > 0) {
            output.write(buffer, 0, length);
        }

        // Close the streams
        output.flush();
        output.close();
    }

    private String getDatabaseDir() {
        if (android.os.Build.VERSION.SDK_INT >= 17) {
            return mContext.getApplicationInfo().dataDir + "/databases/";
        } else {
            return "/data/data/" + mContext.getPackageName() + "/databases/";
        }
    }

    public String getDbPath() {
        return mDbPath;
    }

    public void createDataBase(boolean forceRewrite) throws IOException {
        createDataBase(mDbPath, forceRewrite);
    }

    /**
     * Creates an empty database on the system and rewrites it with your own
     * database.
     */
    public void createDataBase(String dbPath, boolean forceRewrite) throws IOException {
        if (dbPath == null)
            throw new NullPointerException("dbPath null");

        boolean dbExist = checkDataBase(dbPath);

        if (dbExist && !forceRewrite) {
            // do nothing - database already exist
        } else {
            // By calling this method and empty database will be created into the
            // default system path
            // of your application so we are gonna be able to overwrite that database
            // with our database.

            //getReadableDatabase();

            InputStream input = null;
            try {
                // Open your local database as the input stream
                if (mSourceType == SOURCE_NEW) {
                    return;
                }
                if (mSourceType == SOURCE_ASSET) {
                    input = mContext.getAssets().open(mAssetName);
                } else if (mSourceType == SOURCE_URI) {
                    input = mContext.getContentResolver().openInputStream(mDbUri);
                } else {
                    throw new IllegalStateException("Unknown source type");
                }

                copyDataBase(dbPath, input);
            } finally {
                if (input != null)
                    input.close();
            }
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // TODO Auto-generated method stub

    }

    public void attach(String dbFilePath, String alias) {
        final SQLiteDatabase db = getReadableDatabase();

        db.execSQL("ATTACH DATABASE ? AS ?", new String[]{dbFilePath, alias});
    }

    public void detach(String alias) {
        final SQLiteDatabase db = getReadableDatabase();

        db.execSQL("DETACH DATABASE ?", new String[]{alias});
    }
}
