/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.omadm.service;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

class DMOverlayHelper {
    private static final String TAG = "DMOverlayHelper";
    private static final boolean DBG = true;    // STOPSHIP: change to false

    private static final String BASE_PATH = Environment.getRootDirectory() + "/etc/omadm";
    private static final String OVL_SUFFIX = ".xml";
    private static final String OVL_PATH_FMT = BASE_PATH + "/%s/databases";
    private static final String MKITSO_SUFFIX = ".mkitso";

    private static final String OVERWRITE_ALL = "__overwrite_all__";  // special parameter to overwrite contents of the
                                                                      // table DANGEROUS!!!!

    // packageName is used to determine the path to the overlay files
    private final String mPackageName;
    private final String mOverlayPath;

    /**
     * Construct the Helper using the Context
     * @param ctx
     */
    DMOverlayHelper(Context ctx) {
        mPackageName = ctx.getPackageName();
        mOverlayPath = String.format(OVL_PATH_FMT, mPackageName);
    }

    String getOverlayPath() {
        return mOverlayPath;
    }

    /**
     * Overwrites the defaults with the values from the mkitso files.
     *
     * @param db  The open database that we are initializing
     * @param tableName The name of the table to update
     */
    public void applyMkitso(SQLiteDatabase db, String tableName) {
        Log.d(TAG, TAG + " applying overlays to " + tableName);
        File mkitso = new File(String.format("%s/%s/%s%s",
                BASE_PATH, mPackageName, tableName, MKITSO_SUFFIX));
        Log.d(TAG, TAG + "overlay from " + mkitso.toString());

        if (mkitso.exists()) {
            SQLiteStatement stmt = db.compileStatement(
                    String.format("INSERT OR REPLACE INTO %s(name,value) VALUES(?,?);", tableName));

            Properties p = new Properties();
            InputStream in = null;
            try {
                in = new FileInputStream(mkitso);
                p.load(in);
                // CAUTION: this will delete everything from the table
                // use the special property __overwrite_all__ to identify that
                // all of the developer defaults should be replaced.
                String overwrite = (String) p.get(OVERWRITE_ALL);
                if (overwrite != null && "true".equalsIgnoreCase(overwrite)) {
                    db.execSQL(String.format("DELETE FROM %s", tableName));
                }
                for (Map.Entry<Object, Object> m : p.entrySet()) {
                    String key = (String) m.getKey();
                    String value = (String) m.getValue();
                    stmt.bindString(1, key);
                    stmt.bindString(2, value);
                    stmt.execute();
                    if (DBG) Log.d(TAG, "OverlayHelper.apply set " + key + " to " + value);
                }
            } catch (FileNotFoundException e) {
                // The mkitso files are optional so this can be safely ignored
                Log.e(TAG, String.format("File %s unexpectedly missing!", mkitso.toString()));
            } catch (IOException e) {
                Log.e(TAG, String.format("error parsing %s: %s", mkitso.toString(), e.getMessage()));
            } catch (Exception e) {
                Log.e(TAG, String.format("Unexpected exception for %s: %s",
                    mkitso.toString(), e.getMessage()));
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        // failing to close a file is strange but we should continue
                        Log.e(TAG, String.format("failed to close %s", mkitso.toString()));
                    }
                }
            }
            stmt.close();
        }
    }

    /**
     * Apply all of the (xml) overlays from the "flex" source.
     * If you also want to apply mkitso files, you will need to call
     * apply(db, table) separately.
     *
     * @param db
     */
    public void applyXml(SQLiteDatabase db) {
        try {
            // get list of overlays for this
            DMXmlOverlay[] ovls = DMXmlOverlay.getOverlays(getOverlayPath(), OVL_SUFFIX);

            if (ovls != null) {
                Log.d(TAG, String.format("Found %d overlay(s)", ovls.length));

                // loop over the overlay files and apply each
                for (DMXmlOverlay ovl : ovls) {
                    Log.d(TAG, String.format(" - overlay:  '%s'", ovl.getPath()));
                    applyOverlay(db, ovl);
                }
            } else {
                Log.d(TAG, String.format("No overlays found at %s", getOverlayPath()));
            }
        } catch (Exception e) {
            Log.e(TAG, "Yikes! Runtime error in applyXml " + e.getMessage());
        }
    }

    /**
     * Apply a single (xml)overlay file
     *
     * @param db
     * @param ovl
     */
    private static void applyOverlay(SQLiteDatabase db, DMXmlOverlay ovl) {
        Log.d(TAG, "applyOverlay called!");
        if (ovl != null) {
            List list = ovl.read();
            if (list != null) {
                DMDatabaseTable tr = new DMDatabaseTable(db, ovl.getTableName());
                if (tr.isValid()) {
                    // loop over the rows
                    for (Object obj : list) {
                        // FIXME: chain of instanceof checks indicates abstraction failure!
                        if (obj instanceof HashMap) {
                            Log.d(TAG, " - Map -");
                            HashMap map = (HashMap)obj;
                            tr.insertRow(db, map);
                        } else if (obj instanceof String) {
                            Log.d(TAG, " - String in Map - ");
                            String string = (String)obj;

                            if (string.equalsIgnoreCase(OVERWRITE_ALL)) {
                                db.execSQL(String.format("DELETE FROM %s", ovl.getTableName()));
                            }
                        } else {
                            Log.d(TAG, obj.getClass().getName());
                        }
                    }
                }
                else {
                    Log.e(TAG, "Attempt to flex invalid table " + ovl.getTableName());
                }
             }
        }
        Log.d(TAG, "applyOverlay returning");
    }


}
