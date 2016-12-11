package eu.arateb.spatiatlas.provider;

import org.spatialite.database.SQLiteDatabase;

public class EvalScriptBuilder {

    private final StringBuilder mScript = new StringBuilder();

    public EvalScriptBuilder() {
    }

    public static String varSelect(String variableName) {
        return "(SELECT coalesce(RealValue, IntegerValue, TextValue, BlobValue, GeomValue FROM _Variables WHERE Name = '" + variableName + "' LIMIT 1)";
    }

    public void reset() {
        mScript.setLength(0);
    }

    private void createVarTableIfNotExists() {
        mScript.append(
                "PRAGMA temp_store = 2;\n" +
                        "CREATE TEMP TABLE IF NOT EXISTS _Variables(Name TEXT PRIMARY KEY, IntegerValue INTEGER, RealValue REAL, TextValue TEXT, BlobValue BLOB, GeomValue GEOMETRY);\n");
    }

    public void setInt(String variableName, String subquery) {
        createVarTableIfNotExists();
        mScript.append("INSERT OR REPLACE INTO _Variables (Name, IntegerValue, RealValue, TextValue, BlobValue, GeomValue) VALUES (" + variableName + ", " + subquery + ", NULL, NULL, NULL, NULL);\n");
    }

    public void setReal(String variableName, String subquery) {
        createVarTableIfNotExists();
        mScript.append("INSERT OR REPLACE INTO _Variables (Name, IntegerValue, RealValue, TextValue, BlobValue, GeomValue) VALUES (" + variableName + ", NULL, " + subquery + ", NULL, NULL, NULL);\n");
    }

    public void setText(String variableName, String subquery) {
        createVarTableIfNotExists();
        mScript.append("INSERT OR REPLACE INTO _Variables (Name, IntegerValue, RealValue, TextValue, BlobValue, GeomValue) VALUES (" + variableName + ", NULL, NULL, " + subquery + ", NULL, NULL);\n");
    }

    public void setBlob(String variableName, String subquery) {
        createVarTableIfNotExists();
        mScript.append("INSERT OR REPLACE INTO _Variables (Name, IntegerValue, RealValue, TextValue, BlobValue, GeomValue) VALUES (" + variableName + ", NULL, NULL, NULL, " + subquery + ", NULL);\n");
    }

    public void setGeometry(String variableName, String subquery) {
        createVarTableIfNotExists();
        mScript.append("INSERT OR REPLACE INTO _Variables (Name, IntegerValue, RealValue, TextValue, BlobValue, GeomValue) VALUES (" + variableName + ", NULL, NULL, NULL, NULL, " + subquery + ");\n");
    }

    public void execute(SQLiteDatabase db) {
        if (mScript.length() == 0)
            return;

        db.execSQL(mScript.toString());
    }

    public void cleanup(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS _Variables;");
    }
}
