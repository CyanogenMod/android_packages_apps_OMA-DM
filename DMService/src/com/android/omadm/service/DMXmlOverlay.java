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

import android.util.Log;

import com.android.internal.util.XmlUtils;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Represents a single xml "flex" file.  This should contain a
 * table like structure in xml.  All parsing is done by XmlUtils.
 */
public class DMXmlOverlay {
    private static final String TAG = "OverlayRecord";

    private final File   mFile;
    private final String mTableName;

    private DMXmlOverlay(String path, String tableName) {
        mFile = new File(path);
        mTableName = tableName;
    }

    public String toString() {
        return getPath();
    }

    public String getPath() {
        return mFile.getAbsolutePath();
    }

    public String getTableName() {
        return mTableName;
    }

    /**
     * Returns a list of files to read in for flexing.
     *
     * @param dirPath path to the directory
     * @param suffix
     * @return null if no overlays
     */
    public static DMXmlOverlay[] getOverlays(String dirPath, final String suffix) {
        File dir = new File(dirPath);

        // we only want xml files
        FilenameFilter filter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.toLowerCase(Locale.US).endsWith(suffix);
            }
        };

        String[] children = dir.list(filter);
        DMXmlOverlay[] records = null;

        if (children == null) {
            // Either dir does not exist or is not a directory
        } else {
            records = new DMXmlOverlay[children.length];
            for (int i = 0; i < children.length; i++) {
                String filePath = dir.getPath() + File.separator + children[i];
                String tableName = children[i].substring(0,
                        children[i].length() - suffix.length());
                records[i] = new DMXmlOverlay(filePath, tableName);
            }
        }
        return records;
    }

    /**
     * read/parse the xml file.
     *
     * @return A list of (hopefully) maps
     *
     */
    public List read() {
        Log.d(TAG, String.format("OverlayHelper.readListXml %s", mFile.getAbsolutePath()));
        ArrayList list = null;
        if (mFile.exists()) {
            FileInputStream stream = null;
            try {
                stream = new FileInputStream(mFile);
                list = XmlUtils.readListXml(stream);
            } catch (FileNotFoundException e) {
                Log.e(TAG, String.format("File %s unexpectedly missing!", mFile.toString()));
                list = null;
            } catch (XmlPullParserException e) {
                Log.e(TAG, String.format("Invalid format for file %s error is %s",
                         mFile.toString(), e.getMessage()));
                list = null;
            } catch (IOException e) {
                Log.e(TAG, String.format("IOException reading file %s error is %s",
                         mFile.toString(), e.getMessage()));
                list = null;
            } finally {
                 // clean up the file output stream
                if (stream != null) {
                    try { stream.close(); } catch (Exception e) {}
                }
            }
        }
        return list;
    }
}
