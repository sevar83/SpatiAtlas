package eu.arateb.spatiatlas;

import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.view.Menu;
import android.view.MenuItem;

import eu.arateb.spatiatlas.provider.SpatiAtlasContract;
import eu.arateb.spatiatlas.util.Utils;

import static eu.arateb.spatiatlas.provider.SpatiAtlasContract.SystemInfoColumns.GEOS_VERSION;
import static eu.arateb.spatiatlas.provider.SpatiAtlasContract.SystemInfoColumns.HAS_EPSG;
import static eu.arateb.spatiatlas.provider.SpatiAtlasContract.SystemInfoColumns.HAS_FREEXL;
import static eu.arateb.spatiatlas.provider.SpatiAtlasContract.SystemInfoColumns.HAS_GEOCALLBACKS;
import static eu.arateb.spatiatlas.provider.SpatiAtlasContract.SystemInfoColumns.HAS_GEOS_ADVANCED;
import static eu.arateb.spatiatlas.provider.SpatiAtlasContract.SystemInfoColumns.HAS_GEOS_TRUNK;
import static eu.arateb.spatiatlas.provider.SpatiAtlasContract.SystemInfoColumns.HAS_ICONV;
import static eu.arateb.spatiatlas.provider.SpatiAtlasContract.SystemInfoColumns.HAS_LIBXML2;
import static eu.arateb.spatiatlas.provider.SpatiAtlasContract.SystemInfoColumns.HAS_LWGEOM;
import static eu.arateb.spatiatlas.provider.SpatiAtlasContract.SystemInfoColumns.HAS_MATHSQL;
import static eu.arateb.spatiatlas.provider.SpatiAtlasContract.SystemInfoColumns.PROJ4_VERSION;
import static eu.arateb.spatiatlas.provider.SpatiAtlasContract.SystemInfoColumns.SPATIALITE_VERSION;

public class MainActivity extends SinglePaneActivity {

    @Override
    protected Fragment onCreatePane() {
        return new MapFragment();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.system_info) {
            Uri uri = SpatiAtlasContract.SystemInfo.CONTENT_URI;
            Cursor c = getContentResolver().query(uri, null, null, null, null);
            if (c == null || !c.moveToFirst()) {
                new AlertDialog.Builder(this)
                        .setTitle("Error")
                        .setMessage("Cannot get system info!")
                        .setPositiveButton(android.R.string.ok, null)
                        .create()
                        .show();
                Utils.closeSilently(c);
                return true;
            }

            String spliteVer = "Spatialite ver: " + c.getString(c.getColumnIndexOrThrow(SPATIALITE_VERSION));
            String proj4Ver = "PROJ.4 ver: " + c.getString(c.getColumnIndexOrThrow(PROJ4_VERSION));
            String geosVer = "GEOS ver: " + c.getString(c.getColumnIndexOrThrow(GEOS_VERSION));
            String hasGeosAdv = "HasGeosAdvanced: " + c.getString(c.getColumnIndexOrThrow(HAS_GEOS_ADVANCED));
            String hasGeosTrunk = "HasGeosTrunk: " + c.getString(c.getColumnIndexOrThrow(HAS_GEOS_TRUNK));
            String hasLwgeom = "HasLwgeom: " + c.getString(c.getColumnIndexOrThrow(HAS_LWGEOM));
            String hasGeoCallbacks = "HasGeoCallbacks: " + c.getString(c.getColumnIndexOrThrow(HAS_GEOCALLBACKS));
            String hasMathSql = "HasMathSQL: " + c.getString(c.getColumnIndexOrThrow(HAS_MATHSQL));
            String hasLibXml = "HasLibXML2: " + c.getString(c.getColumnIndexOrThrow(HAS_LIBXML2));
            String hasEpsg = "HasEPSG: " + c.getString(c.getColumnIndexOrThrow(HAS_EPSG));
            String hasIconv = "HasIconv: " + c.getString(c.getColumnIndexOrThrow(HAS_ICONV));
            String hasFreexl = "HasFreexl: " + c.getString(c.getColumnIndexOrThrow(HAS_FREEXL));

            CharSequence[] items = new CharSequence[]{spliteVer, proj4Ver, geosVer, hasGeosAdv,
                    hasGeosTrunk, hasLwgeom, hasGeoCallbacks, hasMathSql, hasLibXml,
                    hasEpsg, hasIconv, hasFreexl};

            new AlertDialog.Builder(this)
                    .setTitle("System Info")
                    .setItems(items, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    })
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    })
                    .create()
                    .show();

            Utils.closeSilently(c);

            return true;
        }

        return false;
    }
}
