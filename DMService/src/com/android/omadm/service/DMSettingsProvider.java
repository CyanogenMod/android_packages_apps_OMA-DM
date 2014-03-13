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

import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;

public class DMSettingsProvider extends ContentProvider implements ServiceConnection {
    private static final String TAG = "DMSettingsProvider";

    // max waiting time for starting DM service (binding) in milliseconds
    private static final int MAX_WAIT_TIME_BIND_SERVICE = 1000 * 60; // 60 sec

    // max waiting time for locking DM tree in milliseconds
    private static final int MAX_WAIT_TIME_DMT_LOCKED = 1000 * 60 * 5;// 5 min

    // waiting time for start service
    private static final int WAIT_TIME_BIND_SERVICE = 1000;

    // waiting time if DMT is locked
    private static final int WAIT_TIME_DMT_LOCKED = 1000;

    // authority and URIs
    //public static final String AUTHORITY = "com.android.omadm.service";
    //public static final String DM_TREE = "dmtree";
    //public static final String DM_TREE_STATUS = "dmtreestatus";

    //public static final Uri DM_TREE_URI = Uri.parse("content://" + AUTHORITY + "/" + DM_TREE);
    //public static final Uri DM_TREE_STATUS_URI = Uri.parse("content://" + AUTHORITY + "/" + DM_TREE_STATUS);

    //this uri will be returned during DM bootstrapping in case if success
    private static final String DM_BLOB_BOOTSTRAP_URI_STR = "bootstrap://dmt";
    // columns
    //public static final String COL_ROOT_NODE = "rootnode";
    //public static final String COL_SERVER_ID = "serverid";

    private static final String[] NODE_INFO_FIELDS = {
            DMSettingsHelper.COL_ROOT_NODE,
            DMSettingsHelper.COL_SERVER_ID };

    private static final String NODE_VALUE = "NODE_VALUE";
    private static final String NODE_CHILDREN = "NODE_CHILDREN";

    // Variables used to work with DM service
    private DMClientService mBoundDMService;
    private static final Object mLock = new Object();

    private SQLiteDatabase mdb;
    private DatabaseHelper mDbHelper;

    // project map
    private static final HashMap<String, String> sNodesProjectionMap;

    private int mDMVersion = DMSettingsProviderHelper.DM_VERSION_ERR;
    private String mDMAccRoot;
    private String mServerIdNode;

    // DMT status. Fields for the cursor and values
    private static final String[] TREE_STATUS_FIELDS = {
            DMSettingsHelper.COL_DM_AVAILABILITY };

    // two URIs: access DMT and check DMT status
    private static final UriMatcher uriMatcher;
    private static final int DM_TREE_CODE = 1;
    private static final int DM_TREE_STATUS_CODE = 2;
    private static final int DM_DMFLEXS_CODE = 3;

    static {
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(DMSettingsHelper.AUTHORITY, DMSettingsHelper.DM_TREE, DM_TREE_CODE);
        uriMatcher.addURI(DMSettingsHelper.AUTHORITY,
                DMSettingsHelper.DM_TREE_STATUS, DM_TREE_STATUS_CODE);
        uriMatcher.addURI(DMSettingsHelper.AUTHORITY,
                DMSettingsHelper.DM_DMFLEXS, DM_DMFLEXS_CODE);

        sNodesProjectionMap = new HashMap<String, String>();
        sNodesProjectionMap.put("_id", "_id");
        sNodesProjectionMap.put("name", "name");
        sNodesProjectionMap.put("value", "value");
    }

    /**
     * This class helps open, create, and upgrade the database file.
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            super(context, DMSettingsHelper.DATABASE_NAME, null, 10);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.i(TAG, "In Upgrade");
        }

        //@Override
        @Override
        public void onCreate(SQLiteDatabase db) {
            Log.i(TAG, "In create");
        }
    }

    @Override
    // This function used only for the testing DMSettingsProvider
    public int delete(Uri uri, String selection, String[] selectionArgs) {

        if (uri == null) {
            Log.e(TAG, "Error! Uri cannot be null.");
            return 0;
        }

        switch (uriMatcher.match(uri)) {
            case DM_TREE_CODE :
                // valid case for the delete();
                return dmDelete(selection, selectionArgs);
            case DM_TREE_STATUS_CODE:
                Log.e(TAG, "Error! The method delete() doesn't support URI: " + uri.getPath());
                return 0;
            default:
                Log.e(TAG, "!!====***delete**====!!" + "Error! Unknown URI: " + uri.getPath());
                return 0;
        }
    }

    int dmDelete(String selection, String[] selectionArgs) {

        if (selectionArgs != null) {
            Log.e(TAG, "Error during deleting node: selectionArgs are not supported (should be null).");
            return 0;
        }
        if (selection == null) {
            Log.e(TAG, "Error during deleting node: selection cannot be null.");
            return 0;
        }

        // Extract DMT node path from the parameter selection.
        String rootNode = extractRootNodeFromSelection(selection);

        if (rootNode == null)
            return 0;

        if (rootNode.isEmpty()) {
            Log.e(TAG, "Error during deleting node: node name empty.");
            return 0;
        }

        // acquire and lock DMT
        if (!acquireDMT()) {
            Log.e(TAG, "Error! Cannot acquire DMT.");
            return 0;
        }
        // set root DMT and PROVIDER-ID
        if (!setInitVariables()) {
            return 0;
        }

        String path = mDMAccRoot + '/' + rootNode;
        int result = mBoundDMService.deleteNode(path);

        // release DMT
        releaseDMT();

        if (result != DMResult.SYNCML_DM_SUCCESS) {
            Log.e(TAG, "Error deleting node: " + path + ", return code is: " + result);
            return 0;
        } else {
            return 1;
        }
    }

    @Override
    // The values contain all XML tags and their values
    // sample APPAUTH_AAUTHDATA=XYZ
    public Uri insert(Uri uri, ContentValues values) {

        if (uri == null) {
            Log.e(TAG, "Error! Uri cannot be null.");
            return null;
        }

        switch (uriMatcher.match(uri)) {
            case DM_TREE_CODE :
                // valid case for the insert();
                return dmInsert(values);

            case DM_TREE_STATUS_CODE:
                Log.e(TAG, "Error! The method insert() doesn't support URI: " + uri.getPath());
                return null;

            case DM_DMFLEXS_CODE:
                Log.d(TAG, "Entered insert for dmflexs");
                mdb = getContext().openOrCreateDatabase(DMSettingsHelper.DATABASE_NAME, 0, null);
                mdb.insert("dmFlexs", null, values);
                mdb.close();
                return uri;

            default:
                Log.e(TAG, "!!====***insert**====!!" + "Error! Unknown URI: " + uri.getPath());
                return null;
        }
    }

    Uri dmInsert(ContentValues values) {

        if (values == null || values.size() == 0) {
            Log.e(TAG, "Error during inserting: values is null or empty.");
            return null;
        }

        // find unique value, to choose delete existing or not (by Server ID).
        String uniqueServerId = values
                .getAsString(DMSettingsProviderHelper.XML_UNIQUE_TAG);

        if (TextUtils.isEmpty(uniqueServerId)) {
            Log
                    .e(TAG,
                            "Error during inserting node: unique node value is null or empty.");
            return null;
        }

        // acquire and lock DMT
        if (!acquireDMT()) {
            Log.e(TAG, "Error! Cannot acquire DMT.");
            return null;
        }

        // set root DMT and PROVIDER-ID
        if (!setInitVariables()) {
            releaseDMT();
            Log.e(TAG, "Error! Cannot setInitVariables.");
            return null;
        }

        // check if it is bootstrap and all settings come as a blob (hex string
        // presents the binary settings).
        String blob = values.getAsString(DMSettingsHelper.BLOB);

        if (blob != null) {
            return processSettingsAsBlob(blob);
        }

        // converts XML tags to paths. For leaf nodes values not null
        Map<String, String> mapPathAndValues;
        if (mDMVersion == DMSettingsProviderHelper.DM_1_2) {
            mapPathAndValues = convertTagsToPath_DM_1_2(values);
        } else {
            // mapPathAndValues = convertTagsToPath_DM_1_1_2(values);
            Log.e(TAG, "Error. DM 1.1.2 currently not supported");
            releaseDMT();
            return null;
        }

        if (mapPathAndValues == null || mapPathAndValues.isEmpty()) {
            Log.e(TAG, "Error! Map with paths and values is null or empty.");
            releaseDMT();
            return null;
        }

        // delete subtree if already exists. Match by
        // DMSettingsProviderHelper.XML_UNIQUE_TAG (Server ID)
        if (!removeDuplicatedNodeIfExists(mDMAccRoot, mServerIdNode,
                uniqueServerId)) {
            Log.e(TAG, "Error removing duplicated node.");
            releaseDMT();
            return null;
        }

        // sort all paths by length. It means that ./aaa becomes before
        // ./aaa/b/c
        String[] nodes = mapPathAndValues.keySet().toArray(new String[0]);
        nodes = sortNodePaths(nodes);

        // Insert nodes one by one. Path is a key. Value is null - for the
        // interior node

        for (String node : nodes) {
            String value = mapPathAndValues.get(node);
            int res;
            if (value == null) {
                res = mBoundDMService.createInterior(node);
            } else {
                res = mBoundDMService.createLeaf(node, value);
            }
            // Log.d("AAAAAA", "node=" +node+" value=" + ((value==null) ?
            // "<null>" : value) + " res=" + res);
            if (res != DMResult.SYNCML_DM_SUCCESS) {
                Log.e(TAG, "Error during creating node: " + node
                        + ". Error code: " + res);
                Log.e(TAG, "Try to remove newly created subtree.");
                mBoundDMService.deleteNode(nodes[0]);
                releaseDMT();
                return null;
            }
        }

        releaseDMT();

        return Uri.parse(nodes[0] + "?serverId=" + uniqueServerId);
    }

    @Override
    // queries DMT. The selection contains profile name or empty string
    // for all profiles.
    // Returns Cursor with result or null in an error case. The cursor ALWAYS
    // contains 2 columns: COL_ROOT_NODE, COL_SERVER_ID.
    // The parameters 'projection' and sortOrder are irrelevant.
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {

        if (uri == null) {
            Log.e(TAG, "Error! Uri cannot be null.");
            return null;
        }

        switch (uriMatcher.match(uri)) {
            case DM_TREE_CODE :
                // execute DM query
                return dmQuery(projection, selection, selectionArgs, sortOrder);

            case DM_TREE_STATUS_CODE:
                // return DMT status (can bind service and tree is blocked or not)
                return dmStatus();

            case DM_DMFLEXS_CODE:
                SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
                qb.setTables("dmFlexs");
                qb.setProjectionMap(sNodesProjectionMap);
                Log.d(TAG, "Query for dmFlexs");

                mdb = mDbHelper.getReadableDatabase();
                return qb.query(mdb, projection, selection, selectionArgs, null, null, sortOrder);

            default:
                Log.e(TAG,  "!!====***query**====!!" + "Error! Unknown URI (uri.getPath()): "
                        + uri.getPath());
                Log.e(TAG,  "!!====***query**====!!" + "Error! Unknown URI (uri): " + uri);
                return null;
        }
    }

    Cursor dmQuery(String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {

        if (projection != null || selectionArgs != null || sortOrder != null) {
            Log.e(TAG, "Error during querying: projection, selection, selectionArgs and sortOrder should be null.");
            return null;
        }

        // extract DMT Node path from the parameter "selection".
        String nodeName = extractRootNodeFromSelection(selection);

        if (nodeName == null)
            return null;

        // acquire and lock DMT
        if (!acquireDMT()) {
            Log.e(TAG, "Error! Cannot acquire DMT.");
            return null;
        }

        // set root DMT and PROVIDER-ID
        if (!setInitVariables()) {
            releaseDMT();
            return null;
        }

        MatrixCursor cursor = new MatrixCursor(NODE_INFO_FIELDS);
        Map<String, String> mapNodeInfo = null;

        // request for the serverId and name for a specific node
        if (!nodeName.isEmpty()) {
            mapNodeInfo = getAndParseNodeInfo(mDMAccRoot + '/' + nodeName + '/'
                    + mServerIdNode);

            Object[] rowObj = getRowFromInfo(mapNodeInfo, nodeName);
            if (rowObj == null) {
                releaseDMT();
                return null;
            }
            cursor.addRow(rowObj);
        }
        // request for the all profile names and ServerIds
        else {
            mapNodeInfo = getAndParseNodeInfo(mDMAccRoot);
            // error during parsing
            if (mapNodeInfo == null || mapNodeInfo.isEmpty()) {
                Log.e(TAG, "Error: map with node info is empty or null");
                releaseDMT();
                return null;
            }
            String delimChildren = mapNodeInfo.get(NODE_CHILDREN);

            if (!TextUtils.isEmpty(delimChildren)) {
                String[] childrens = parseChildren(delimChildren);

                // get serverId for each child
                for (String children : childrens) {
                    mapNodeInfo = getAndParseNodeInfo(mDMAccRoot + '/'
                            + children + '/' + mServerIdNode);
                    Object[] rowObj = getRowFromInfo(mapNodeInfo, children);
                    if (rowObj == null) {
                        releaseDMT();
                        return null;
                    }
                    cursor.addRow(rowObj);
                }
            }
        }

        releaseDMT();

        return cursor;
    }

    // this function check if DMT has been created and not blocked
    private Cursor dmStatus() {
        Log.d(TAG, "!!====****====!!" + "asking for dmStatus() !!" );
        MatrixCursor cursor = new MatrixCursor(TREE_STATUS_FIELDS);
        Object[] rowObj = new Object[1];
        Context context = getContext();

        // Check if not first time (file have been created).
        String rootWbxmlPath = context.getFilesDir().getAbsolutePath() + "/dm/dmt_data/root.wbxml";
        File rootWbxmlFile = new File(rootWbxmlPath);

        if (!rootWbxmlFile.exists()) {
            Log.d(TAG, "!!====****====!!" +  "First time. The root.wbxml file has not been created:" + rootWbxmlPath);
            rowObj[0] = DMSettingsHelper.LOCKED;
            cursor.addRow(rowObj);

            //start DMClientService service
            Intent intent = new Intent(DMIntent.LAUNCH_INTENT);
            intent.putExtra(DMIntent.FIELD_TYPE, DMIntent.TYPE_DO_NOTHING);
            intent.setClass(context, DMClientService.class);
            context.startService(intent);

            return cursor;
        }
        Log.d (TAG, "!!====****====!!" +  "File exists: " + rootWbxmlPath);
        //check if can bind service and tree is not locked.
        boolean isDMAvailable = checkIsDMTAvailable();
        rowObj[0] = (isDMAvailable) ? DMSettingsHelper.AVAILABLE : DMSettingsHelper.LOCKED;
        Log.d (TAG, "!!====****====!!" +  "isDMAvailable:  " + rowObj[0].toString());
        cursor.addRow(rowObj);
        return cursor;
    }

    // check if can bind and DMT not blocked
    private boolean checkIsDMTAvailable(){

        // Establish a connection with the service.
        getContext().bindService(
                new Intent(getContext(), DMClientService.class), this,
                Context.BIND_AUTO_CREATE);

        // Waiting for starting (binding) DMClientService

        Log.d (TAG, "!!====****====!!" +  "Start binding...");
        int numAttempts = 0;
        int timeWait = 100;   // 0.1 second
        int maxTimeWait = MAX_WAIT_TIME_BIND_SERVICE; // 60 seconds max waiting time
        do {
            synchronized (mLock) {

                try {
                    mLock.wait(timeWait);
                    Log.d (TAG, "!!====****====!!" +  "numAttempts: " + numAttempts);

                    if (mBoundDMService != null) {
                        Log.d(TAG, "Done binding from checkIsDMTCanBeUsed().");
                        boolean isDmtLocked = mBoundDMService.isDmtLocked();
                        getContext().unbindService(this);
                        mBoundDMService = null;
                        Log.d(TAG, "!!====****====!!" +  "DMT is " + ((isDmtLocked) ? "locked" : "unlocked"));
                        return !isDmtLocked;
                    }

                } catch (InterruptedException e) {
                    Log.e(TAG, "Error! Interrupted exception during binding from checkIsDMTCanBeUsed().");
                    return false;
                }
            }
            numAttempts++;

        } while (timeWait * numAttempts <= maxTimeWait);

        // check if start service success and it is not exceeded max time
        Log.d(TAG, "!!====****====!!" +  "Error! Cannot start (bind) DMClientService from checkIsDMTCanBeUsed().");
        return false;
    }


    // create row object for cursor from node info
    private static Object[] getRowFromInfo(Map<String, String> mapNodeInfo,
            String nodeName) {
        // error during parsing
        if (mapNodeInfo == null || mapNodeInfo.isEmpty()) {
            Log.e(TAG, "Error: map with node info is empty or null");
            return null;
        }

        String serverId = mapNodeInfo.get(NODE_VALUE);
        Object[] rowObj = new Object[2];
        rowObj[0] = nodeName;
        rowObj[1] = serverId;

        return rowObj;
    }

    // This is called when the connection with the service has been
    // established, giving us the service object we can use to
    // interact with the service.
    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
        mBoundDMService = ((DMClientService.LocalBinder) service).getService();

        synchronized (mLock) {
            mLock.notifyAll();
        }
        Log.d(TAG, "DMClientService has been bounded");
    }

    // This is called when the connection with the service has been
    // unexpectedly disconnected -- that is, its process crashed.
    // Because it is running in our same process, we should never
    // see this happen.
    @Override
    public void onServiceDisconnected(ComponentName className) {
        mBoundDMService = null;
        Log.d(TAG, "DMClientService has been disconnected.");
    }

    @Override
    // Update is not supported.
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        Log.e(TAG, "Update not supported.");
        return 0;
    }

    // ~~~~~~~~~~~~~ private functions ~~~~~~~~~~~~~~~~~~~~~~//

    // set tree root and serverId based on the DM version
    private boolean setInitVariables() {
        int dmVersion = getDMVersion();
        mDMVersion = dmVersion;

        if (dmVersion == DMSettingsProviderHelper.DM_VERSION_ERR) {
            return false; // error getting DM version
        } else if (dmVersion == DMSettingsProviderHelper.DM_1_2) {
            mDMAccRoot = DMSettingsProviderHelper.DM_1_2_ROOT;
            mServerIdNode = DMSettingsProviderHelper.DM_1_2_SERVER_ID;
            return true;
        } else {
            mDMAccRoot = DMSettingsProviderHelper.DM_1_1_2_ROOT;
            mServerIdNode = DMSettingsProviderHelper.DM_1_1_2_SERVER_ID;
            return true;
        }
    }

    // starts/connects to DM service (first time it will populate DMT);
    // locks DMT.
    private boolean acquireDMT() {
        // Establish a connection with the service.
        getContext().bindService(
                new Intent(getContext(), DMClientService.class), this,
                Context.BIND_AUTO_CREATE);

        // Waiting for starting (binding) DMClientService
        int numAttempts = 0;

        do {
            synchronized (mLock) {

                try {
                    mLock.wait(WAIT_TIME_BIND_SERVICE);

                    if (mBoundDMService != null) {
                        Log.d(TAG, "Done binding with DMClientService.");
                        break;
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "Error! Interrupted exception during binding"
                            + " with DMClientService.");
                    return false;
                }
            }
            numAttempts++;
        } while (WAIT_TIME_BIND_SERVICE * numAttempts <= MAX_WAIT_TIME_BIND_SERVICE);

        // check if start service success and it is not exceeded max time
        if (mBoundDMService == null) {
            Log.d(TAG, "Error! Cannot start (bind) DMClientService.");
            return false;
        }

        // check if DMT is not locked and wait for unlocking otherwise
        numAttempts = 1;

        synchronized (mLock) {
            while (mBoundDMService.isDmtLocked()
                    && WAIT_TIME_DMT_LOCKED * numAttempts <= MAX_WAIT_TIME_DMT_LOCKED) {

                try {
                    mLock.wait(WAIT_TIME_DMT_LOCKED);

                } catch (InterruptedException e) {
                    Log.e(TAG,
                            "Interrupted exception during waiting unlocking DMT.");
                    releaseDMT();
                    return false;
                }
                numAttempts++;
            }
        }

        // check if DMT still locked
        if (mBoundDMService.isDmtLocked()) {
            Log.e(TAG, "Error! DMT is locked.");
            releaseDMT();
            return false;
        }

        // Success (binding and locking DMT
        mBoundDMService.lockDmt();
        return true;
    }

    // release DMT
    private void releaseDMT() {
        if (mBoundDMService != null) {
            mBoundDMService.unlockDmt();
            getContext().unbindService(this);
            mBoundDMService = null;
        }
    }

    // validates and extracts DMT root node from the parameter 'selection'
    private static String extractRootNodeFromSelection(String selection) {

        if (selection == null) {
            return "";
        }

        // validate selection format: NODE_PATH='./DMAcc/...'
        if (!(selection.startsWith(DMSettingsHelper.COL_ROOT_NODE + "='")
                && selection.endsWith("'") && selection.length() > (DMSettingsHelper.COL_ROOT_NODE
                + "='" + '\'').length())) {
            Log.e(TAG, "Error! The format 'selection' is wrong. " + selection);
            return null;
        }

        // the selection should look like NODE_PATH='./xxx/abc/../...'
        // remove leading "rootnode='ABC'"
        int start = (DMSettingsHelper.COL_ROOT_NODE + "='").length();
        String rootNode = selection.substring(start, selection.length() - 1);

        return rootNode;
    }

    // parse return for getNodeInfo() for the root (".")
    // if the node SyncML presents - it is DM 1.1.2, otherwise DM 1.2
    private int getDMVersion() {

        Map<String, String> mapNodeInfo = getAndParseNodeInfo(
                DMSettingsProviderHelper.DM_VERSION_NODE_PATH);

        // error during parsing
        if (mapNodeInfo == null) {
            return DMSettingsProviderHelper.DM_VERSION_ERR;
        }

        String nodeValue = mapNodeInfo.get(NODE_VALUE);

        if ("1.2".equals(nodeValue)) {
            return DMSettingsProviderHelper.DM_1_2;
        } else if ("1.1.2".equals(nodeValue)) {
            return DMSettingsProviderHelper.DM_1_1_2;
        } else {
            Log.e(TAG, "Unsupported DM version: " + nodeValue);
            return DMSettingsProviderHelper.DM_VERSION_ERR;
        }
    }

    // The keys for values is XML tags. converts keys to the DMT path
    // for the DM 1.2.
    private static Map<String, String> convertTagsToPath_DM_1_2(ContentValues values) {
        Map<String, String> result = new HashMap<String, String>();

        // get unique profile root name generated during preprocess
        String profName = values
                .getAsString(DMSettingsProviderHelper.DM_UNIQUE_ROOT_TAG);
        if (TextUtils.isEmpty(profName)) {
            Log.e(TAG, "Error converting tags to path. The unique profile root name"
                    + " cannot be empty value or null");
            return null;
        }

        // add appId w7 for the DM 1.2 only
        values.put(DMSettingsProviderHelper.APPID,
                DMSettingsProviderHelper.APPID_VAL);

        // get keys/values (entry) from the ContentValues
        Set<Entry<String, Object>> setEntries = values.valueSet();
        Iterator<Entry<String, Object>> iterator = setEntries.iterator();

        // Add root for a profile under ./DMAcc
        String profileRoot = DMSettingsProviderHelper.DM_1_2_ROOT + '/' + profName;
        result.put(profileRoot, null);

        // parse key and create corresponded DMT paths

        while (iterator.hasNext()) {
            Entry<String, Object> entry = iterator.next();
            String xmlTagPath = entry.getKey();
            String value = (String) entry.getValue();

            // skip root node
            if (DMSettingsProviderHelper.DM_UNIQUE_ROOT_TAG.equals(xmlTagPath))
                continue;

            // value cannot be empty
            if (TextUtils.isEmpty(value)) {
                Log.e(TAG, "Error converting tags to path. The " + xmlTagPath
                        + " contains empty value or null");
                return null;
            }

            String path = profileRoot;

            String[] tags = xmlTagPath.split(DMSettingsProviderHelper.XML_TAG_DELIM);

            // create all required interior nodes. last node is a leaf.
            String tmp;
            for (int i = 0; i < tags.length - 1; i++) {
                tmp = tagToPath(tags[i],
                        DMSettingsProviderHelper.DM_1_2_PATH_MAP);
                path = path + '/' + tmp;
                result.put(path, null);
            }

            // create leaf node
            tmp = tagToPath(tags[tags.length - 1],
                    DMSettingsProviderHelper.DM_1_2_PATH_MAP);
            path = path + '/' + tmp;
            result.put(path, value);
        }
        return result;
    }

    // sorts paths in the array by path's length
    private static String[] sortNodePaths(String[] nodes) {
        for (int i = 1; i < nodes.length; i++) {
            for (int j = 0; j < nodes.length - i; j++) {
                if (nodes[j].length() > nodes[j + 1].length()) {
                    String str = nodes[j];
                    nodes[j] = nodes[j + 1];
                    nodes[j + 1] = str;
                }
            }
        }
        return nodes;
    }

    // parses result from getNodeInfo() and returns map with values
    // the format is "children:aaa/bbb/ccc/ddd\n or value=ABC\n"
    // returns null or map with data
    private Map<String, String> getAndParseNodeInfo(String nodePath) {

        // get node info
        String strNodeInfo = mBoundDMService.getNodeInfoSP(nodePath);

        Log.d(TAG, "Node info for " + nodePath + '\n' + strNodeInfo);

        if (TextUtils.isEmpty(strNodeInfo)) {
            Log.e(TAG, "Native GetNodeInfoSP() returns null or an empty string.");
            return null;
        }
        if (strNodeInfo.startsWith(DMSettingsProviderHelper.NODE_INFO_FAIL_START)) {
            Log.e(TAG, "Native GetNodeInfoSP() returns error: " + strNodeInfo);
            return null;
        }

        // parse info into the map
        StringTokenizer st = new StringTokenizer(strNodeInfo, "\n");
        Map<String, String> result = new HashMap<String, String>();

        while (st.hasMoreTokens()) {
            String line = st.nextToken();

            if (TextUtils.isEmpty(line))
                continue;

            if (line.startsWith(DMSettingsProviderHelper.NODE_INFO_VALUE_PREFIX)) {
                line = line.replaceFirst(
                        DMSettingsProviderHelper.NODE_INFO_VALUE_PREFIX, "");
                if (DMSettingsProviderHelper.NULL.equals(line) || line.isEmpty()) {
                    line = null;
                }
                result.put(NODE_VALUE, line);
            } else if (line.startsWith(DMSettingsProviderHelper.NODE_INFO_CHILDREN_PREFIX)) {
                line = line.replaceFirst(
                        DMSettingsProviderHelper.NODE_INFO_CHILDREN_PREFIX, "");
                if (DMSettingsProviderHelper.NULL.equals(line) || line.isEmpty()) {
                    line = null;
                }
                result.put(NODE_CHILDREN, line);
            }
        }

        return result;
    }

    // parses children names from bbb/ccc/ddd into array
    private static String[] parseChildren(String delimChildren) {
        return delimChildren
                .split(DMSettingsProviderHelper.NODE_INFO_CHILDREN_DELIM);
    }

    // remove profile (subtree) in case if already exists.
    // returns true - if success (node not exists or has been removed or false
    private boolean removeDuplicatedNodeIfExists(String dmAccRoot,
            String serverIdNode, String uniqueValue) {

        Map<String, String> mapNodeInfo = getAndParseNodeInfo(dmAccRoot);

        // check error during getting info or parsing.
        if (mapNodeInfo == null) {
            return false;
        }

        String delimChildren = mapNodeInfo.get(NODE_CHILDREN);

        // node does not contain any children. don't need remove
        if (delimChildren == null) {
            return true;
        }

        String[] profileNames = parseChildren(delimChildren);

        for (String profileName : profileNames) {
            String tmpProfileNode = dmAccRoot + '/' + profileName;
            String tmpServerIdNode = tmpProfileNode + '/' + serverIdNode;
            Map<String, String> tmpNodeInfo = getAndParseNodeInfo(tmpServerIdNode);

            // check error during getting info or parsing.
            if (tmpNodeInfo == null) {
                return false;
            }
            String tmpValue = tmpNodeInfo.get(NODE_VALUE);

            if (TextUtils.isEmpty(tmpValue)) {
                Log.d(TAG, "Warning. The tree is corrupted. The node "
                        + tmpServerIdNode + " cannot be empty or null.");
                return true;
            }

            // check if SERVER_ID match and then remove whole profile.
            if (tmpValue.equals(uniqueValue)) {
                int res = mBoundDMService.deleteNode(tmpProfileNode);

                if (res == DMResult.SYNCML_DM_SUCCESS) {
                    return true;
                } else {
                    Log.e(TAG, "Error deleting " + tmpServerIdNode
                            + " error code: " + res);
                    return false;
                }
            }
        }
        // node doesn't exist
        return true;
    }

    // returns corresponded DMT path from the map or tag itself
    private static String tagToPath(String tag, Map<String, String> map) {
        String path = map.get(tag);
        return (path != null) ? path : tag;
    }

    // process DM bootstrap settings coming as a blob
    private Uri processSettingsAsBlob(String hexBlobSettings) {

        byte[] byteSettings = hexToBytes(hexBlobSettings);

        if(byteSettings == null){
            Log.e(TAG, "Error converting HEX to byte array.");
            releaseDMT();
            return null;
        }

        String uniqueServerId = mBoundDMService.parseBootstrapServerId(byteSettings, true);

        if(TextUtils.isEmpty(uniqueServerId)){
            Log.e(TAG, "Error. The serverId from parseBootstrapServerId() is null.");
            releaseDMT();
            return null;
        }

        if (!removeDuplicatedNodeIfExists(mDMAccRoot, mServerIdNode,
                uniqueServerId)) {
            Log.e(TAG, "Error removing duplicated node.");
            releaseDMT();
            return null;
        }

        int result = DMClientService.processBootstrapScript(byteSettings, true, uniqueServerId);

        releaseDMT();

        return (result == DMResult.SYNCML_DM_SUCCESS)
                ? (Uri.parse(DM_BLOB_BOOTSTRAP_URI_STR + "?serverId=" + uniqueServerId))
                : null;
    }

    // convert hex settings to the array of bytes.
    private static byte[] hexToBytes(String hexBlobSettings) {
        if ((hexBlobSettings.length() % 2) != 0) {
            Log.e(TAG, "Hex blob settings not correct");
            return null;
        }

        int blobLength = hexBlobSettings.length();
        byte[] bytesSettings = new byte[blobLength / 2];

        for (int i = 0; i < blobLength; i += 2) {
            bytesSettings[i / 2] = (byte) ((Character.digit(hexBlobSettings
                    .charAt(i), 16) << 4) + Character.digit(hexBlobSettings
                    .charAt(i + 1), 16));
        }

        return bytesSettings;
    }
    // /~~~~~~~~~ Not used and not implemented ~~~~~~~~~~~~//
    @Override
    public boolean onCreate() {
        mDbHelper = new DatabaseHelper(getContext());
        return true;
    }

    @Override
    public String getType(Uri uri) {
        Log.e(TAG, "DM content provider doesn't support method 'getType()'");
        return null;
    }
    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~//
}
