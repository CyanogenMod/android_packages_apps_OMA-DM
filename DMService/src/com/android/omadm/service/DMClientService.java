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

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.android.omadm.plugin.impl.DmtPluginManager;

import net.jcip.annotations.GuardedBy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This is the OMA DM client service as an IntentService.
 * FIXME: this should be rewritten as a regular Service with an associated StateMachine.
 */
public class DMClientService extends IntentService {
    private static final String TAG = "DMClientService";
    static final boolean DBG = true;    // STOPSHIP: change to false

    // flag "DM session in progress" used from DMIntentReceiver
    public static boolean sIsDMSessionInProgress;

    private boolean mInitGood;
    private WakeLock mWakeLock;

    /** Lock object for {@link #mSession} and {@link #mServiceID}. */
    private final Object mSessionLock = new Object();

    @GuardedBy("mSessionLock")
    private DMSession mSession;

    @GuardedBy("mSessionLock")
    private long mServiceID;

    @GuardedBy("mSessionTimeOutHandler")
    private final Handler mSessionTimeOutHandler = new Handler();

    /** AsyncTask to manage the settings SQLite database. */
    private DMConfigureTask mDMConfigureTask;

    private final AtomicBoolean mIsDmtLocked = new AtomicBoolean();

    /**
     * Helper class for DM session packages.
     */
    static final class DMSessionPkg {
        public DMSessionPkg(int type, long gId) {
            mType = type;
            mGlobalSID = gId;
            mobj = null;
        }

        public final int mType;
        public final long mGlobalSID;
        public Object mobj;
        public Object mobj2;
        public boolean mbvalue;
    }

    // Class for clients to access. Because we know this service always runs
    // in the same process as its clients, we don't need to deal with IPC.
    public class LocalBinder extends Binder {
        DMClientService getService() {
            return DMClientService.this;
        }
    }

    /**
     * Create the IntentService, naming the worker thread DMClientService.
     */
    public DMClientService() {
        super(TAG);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (DBG) logd("Enter onCreate tid=" + Thread.currentThread().getId());

        copyFilesFromAssets();      // wait for completion before continuing

        mInitGood = (NativeDM.initialize() == DMResult.SYNCML_DM_SUCCESS);
        DmtPluginManager.setContext(this);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        WakeLock lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
        lock.setReferenceCounted(false);
        lock.acquire();
        logd("XXXXX mWakeLock.acquire() in DMClientService.onCreate() XXXXX");
        mWakeLock = lock;

        mDMConfigureTask = new DMConfigureTask();
        mDMConfigureTask.execute(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (DBG) logd("Enter onDestroy tid=" + Thread.currentThread().getId());

        mAbortSession = null;

        if (mInitGood) NativeDM.destroy();

        getConfigDB().closeDatabase();

        logd("XXXXX mWakeLock.release() in DMClientService.onDestroy() XXXXX");
        mWakeLock.release();

        mSessionTimeOutHandler.removeCallbacks(mAbortSession);

        if (DBG) logd("leave onDestroy");
    }

    /**
     * AsyncTask to create the DMConfigureDB object on a helper thread.
     */
    private static class DMConfigureTask extends
            AsyncTask<DMClientService, Void, DMConfigureDB> {
        DMConfigureTask() {}

        @Override
        protected DMConfigureDB doInBackground(DMClientService... params) {
            if (DBG) logd("creating new DMConfigureDB() on tid "
                    + Thread.currentThread().getId());
            return new DMConfigureDB(params[0]);
        }
    }

    /**
     * Process message on IntentService worker thread.
     * @param pkg the parameters to pass from the Intent
     */
    private void processMsg(DMSessionPkg pkg) {
        // wait for up to 70 seconds for config DB to initialize.
        if (getConfigDB() == null) {
            Log.e(TAG, "processMsg: getConfigDB() failed. Aborting session");
            return;
        }
        if (DBG) logd("processMsg: getConfigDB() succeeded");

        sIsDMSessionInProgress = true;

        // check if DMT locked by DMSettingsProvider and wait. If DMT is
        // locked more then 1 minute (error case, means that something
        // wrong with DMSettingsProvider) we are continuing execution

        try {
            // check if DMT still locked and lock DMT and protect access from
            // DMSettingsProvider
            if (mIsDmtLocked.get()) {
                Log.e(TAG, "WARNING! Time expired but DMT still locked.");
            } else {
                lockDmt();
            }

            synchronized (mSessionLock) {
                mSession = new DMSession(this);
                mServiceID = pkg.mGlobalSID;
            }

            int timeOutSecond = 600 * 1000; /* 10 minutes */
            int ret = DMResult.SYNCML_DM_SESSION_PARAM_ERR;

            switch (pkg.mType) {
                case DMIntent.TYPE_PKG0_NOTIFICATION:
                    if (DBG) {
                        Log.d(TAG, "Start pkg0 alert session");
                    }
                    startTimeOutTick(timeOutSecond);
                    synchronized (mSessionLock) {
                        ret = mSession.startPkg0AlertSession((byte[]) pkg.mobj);
                    }
                    break;

                case DMIntent.TYPE_BOOTSTRAP:
                    if (DBG) {
                        Log.d(TAG, "process bootstrap message");
                    }
                    String serverId;
                    startTimeOutTick(timeOutSecond);
                    serverId = parseBootstrapServerId((byte[]) pkg.mobj, pkg.mbvalue);
                    if (!TextUtils.isEmpty(serverId)) {
                        ret = processBootstrapScript((byte[]) pkg.mobj, pkg.mbvalue,
                                serverId);
                        Intent intent = new Intent(DMIntent.DM_SERVICE_RESULT_INTENT);
                        intent.putExtra(DMIntent.FIELD_DM_UNITEST_RESULT, serverId);
                        intent.putExtra(DMIntent.FIELD_REQUEST_ID, pkg.mGlobalSID);
                        sendBroadcast(intent);
                    } else {
                        ret = 1;
                    }
                    break;

                case DMIntent.TYPE_FOTA_CLIENT_SESSION_REQUEST:
                    if (DBG) {
                        Log.d(TAG, "Start fota client initialized session");
                    }
                    startTimeOutTick(timeOutSecond);
                    synchronized (mSessionLock) {
                        ret = mSession.startFotaClientSession(
                                (String) pkg.mobj, (String) pkg.mobj2);
                    }
                    break;

                case DMIntent.TYPE_FOTA_NOTIFY_SERVER:
                    if (DBG) {
                        Log.d(TAG, "Start FOTA notify session");
                    }
                    startTimeOutTick(timeOutSecond);
                    synchronized (mSessionLock) {
                        ret = mSession.fotaNotifyDMServer((FotaNotifyContext) pkg.mobj);
                    }
                    break;

                case DMIntent.TYPE_CLIENT_SESSION_REQUEST:
                    if (DBG) {
                        Log.d(TAG, "Start client initialized session:");
                    }
                    if (pkg.mobj != null) {
                        startTimeOutTick(timeOutSecond);
                        synchronized (mSessionLock) {
                            ret = mSession.startClientSession((String) pkg.mobj);
                        }
                    }
                    break;

                case DMIntent.TYPE_LAWMO_NOTIFY_SESSION:
                    if (DBG) {
                        Log.d(TAG, "Start LAWMO notify session");
                    }
                    startTimeOutTick(timeOutSecond);
                    synchronized (mSessionLock) {
                        ret = mSession
                                .startLawmoNotifySession((FotaNotifyContext) pkg.mobj);
                    }
                    break;

                case DMIntent.TYPE_UNITEST_GET_STRING_NODE:
                    String path = (String) pkg.mobj;
                    if (path != null && !path.isEmpty()) {
                        String retStr = getNodeInfo(path);
                        if (DBG) {
                            Log.d(TAG, retStr);
                        }
                        Intent intent = new Intent(DMIntent.DM_SERVICE_RESULT_INTENT);
                        intent.putExtra(DMIntent.FIELD_DM_UNITEST_RESULT, retStr);
                        intent.putExtra(DMIntent.FIELD_REQUEST_ID, pkg.mGlobalSID);
                        sendBroadcast(intent);
                    }
                    break;

                case DMIntent.TYPE_UNITEST_SET_STRING_NODE:
                    Pair<String, String> vp = (Pair<String, String>) pkg.mobj;
                    if (!TextUtils.isEmpty(vp.first)) {
                        String retStr = setStringNode(vp.first, vp.second);
                        if (DBG) {
                            Log.d(TAG, retStr);
                        }
                        Intent intent = new Intent(DMIntent.DM_SERVICE_RESULT_INTENT);
                        intent.putExtra(DMIntent.FIELD_DM_UNITEST_RESULT, retStr);
                        intent.putExtra(DMIntent.FIELD_REQUEST_ID, pkg.mGlobalSID);
                        sendBroadcast(intent);
                    }
                    break;

                case DMIntent.TYPE_UNITEST_PROCESS_SCRIPT:
                    String fileName = (String) pkg.mobj;
                    boolean isBinary = pkg.mbvalue;
                    byte[] dmResult;
                    synchronized (mSessionLock) {
                        dmResult = processScript(fileName, isBinary, mSession);
                    }
                    if (dmResult != null) {
                        Intent intent = new Intent(DMIntent.DM_SERVICE_RESULT_INTENT);
                        if (!isBinary || dmResult.length == 5) {
                            String retStr = new String(dmResult);
                            if (DBG) {
                                Log.d(TAG, retStr);
                            }
                            intent.putExtra(DMIntent.FIELD_DM_UNITEST_RESULT, retStr);
                        } else {
                            byte[] xml = NativeDM.nativeWbxmlToXml(dmResult);
                            String strXml = new String(xml);
                            if (DBG) {
                                Log.d(TAG, strXml);
                            }
                            intent.putExtra(DMIntent.FIELD_DM_UNITEST_RESULT, strXml);
                        }
                        intent.putExtra(DMIntent.FIELD_REQUEST_ID, pkg.mGlobalSID);
                        sendBroadcast(intent);
                    }
                    //ret = 0;
                    break;

                case DMIntent.TYPE_UNITEST_DUMP_TREE:
                    String nodePath = (String) pkg.mobj;
                    if (nodePath != null && !nodePath.isEmpty()) {
                        String retStr = dumpTree(nodePath);
                        if (DBG) {
                            Log.d(TAG, retStr);
                        }
                        Intent intent = new Intent(
                                DMIntent.DM_SERVICE_RESULT_INTENT);
                        intent.putExtra(DMIntent.FIELD_DM_UNITEST_RESULT, retStr);
                        intent.putExtra(DMIntent.FIELD_REQUEST_ID, pkg.mGlobalSID);
                        sendBroadcast(intent);
                    }
                    break;
            }

            if (DBG) Log.d(TAG, "DM Session result code=" + ret);

            synchronized (mSessionLock) {
                mSession = null;
            }

            Intent intent = new Intent(DMIntent.DM_SERVICE_RESULT_INTENT);
            intent.putExtra(DMIntent.FIELD_DMRESULT, ret);
            intent.putExtra(DMIntent.FIELD_REQUEST_ID, pkg.mGlobalSID);
            sendBroadcast(intent);
        } finally {
            // unlock DMT to give access from DMSettingsProvider
            unlockDmt();

            //set static flag "DM session in progress" to false. Used from DMIntentReceiver
            sIsDMSessionInProgress = false;
        }
    }

    void cancelSession(long requestID) {
        synchronized (mSessionLock) {
            if (requestID == 0 || mServiceID == requestID) {
                if (mSession != null) {
                    Log.e(TAG, "Cancel session with serviceID: " + mServiceID);
                    mSession.cancelSession();
                }
            }
        }
    }

    /**
     * Called on worker thread with the Intent to handle. Calls DMSession directly.
     * @param intent The intent to handle
     */
    @Override
    protected void onHandleIntent(Intent intent) {
        long requestID = intent.getLongExtra(DMIntent.FIELD_REQUEST_ID, 0);
        int intentType = intent.getIntExtra(DMIntent.FIELD_TYPE, DMIntent.TYPE_UNKNOWN);

        if (DBG) Log.d(TAG, "onStart intentType: " + intentType + " requestID: "
                + requestID);

        // wait for up to 70 seconds for config DB to initialize.
        if (getConfigDB() == null) {
            Log.e(TAG, "WARNING! getConfigDB() failed. Aborting session");
            return;
        }
        if (DBG) Log.d(TAG, "getConfigDB() succeeded");

        // check if DMT still locked and lock DMT and protect access from
        // DMSettingsProvider
        if (mIsDmtLocked.get()) {
            Log.e(TAG, "WARNING! Time expired but DMT still locked.");
        } else {
            lockDmt();
        }

        switch (intentType) {
            case DMIntent.TYPE_PKG0_NOTIFICATION: {
                if (DBG) Log.d(TAG, "Pkg0 provision received.");

                byte[] pkg0data = intent.getByteArrayExtra(DMIntent.FIELD_PKG0);
                if (pkg0data == null) {
                    if (DBG) Log.d(TAG, "Pkg0 provision received, but no pkg0 data.");
                    return;
                }
                DMSessionPkg pkg = new DMSessionPkg(intentType, requestID);
                pkg.mobj = intent.getByteArrayExtra(DMIntent.FIELD_PKG0);
                processMsg(pkg);
                break;
            }
            case DMIntent.TYPE_BOOTSTRAP: {
                if (DBG) Log.d(TAG, "bootstrap received.");

                byte[] bsData = intent.getByteArrayExtra(DMIntent.FIELD_BOOTSTRAP_MSG);
                if (bsData == null) {
                    if (DBG) Log.d(TAG, "bootstrap received, but no bootstrap data.");
                    return;
                }
                // assume binary
                boolean isBinary = intent.getBooleanExtra(DMIntent.FIELD_IS_BINARY, true);

                DMSessionPkg pkg = new DMSessionPkg(intentType, requestID);
                pkg.mobj = intent.getByteArrayExtra(DMIntent.FIELD_BOOTSTRAP_MSG);
                pkg.mbvalue = isBinary;
                processMsg(pkg);
                break;
            }
            case DMIntent.TYPE_FOTA_CLIENT_SESSION_REQUEST: {
                if (DBG) Log.d(TAG, "Client initiated dm session was received.");

                DMSessionPkg pkg = new DMSessionPkg(intentType, requestID);
                String serverID = intent.getStringExtra(DMIntent.FIELD_SERVERID);
                String alertStr = intent.getStringExtra(DMIntent.FIELD_ALERT_STR);

                if (TextUtils.isEmpty(serverID)) {
                    Log.e(TAG, "missing server ID, returning");
                    return;
                }

                if (TextUtils.isEmpty(alertStr)) {
                    Log.e(TAG, "missing alert string, returning");
                    return;
                }

                pkg.mobj = serverID;
                pkg.mobj2 = alertStr;
                processMsg(pkg);
                break;
            }
            case DMIntent.TYPE_FOTA_NOTIFY_SERVER: {
                String result = intent.getStringExtra(DMIntent.FIELD_FOTA_RESULT);
                String pkgURI = intent.getStringExtra(DMIntent.FIELD_PKGURI);
                String alertType = intent.getStringExtra(DMIntent.FIELD_ALERTTYPE);
                String serverID = intent.getStringExtra(DMIntent.FIELD_SERVERID);
                String correlator = intent.getStringExtra(DMIntent.FIELD_CORR);

                if (DBG) Log.d(TAG, "FOTA_NOTIFY_SERVER_SESSION Input==>\n" + " Result="
                        + result + '\n' + " pkgURI=" + pkgURI + '\n'
                        + " alertType=" + alertType + '\n' + " serverID="
                        + serverID + '\n' + " correlator=" + correlator);

                DMSessionPkg pkg = new DMSessionPkg(intentType, requestID);
                pkg.mobj = new FotaNotifyContext(result, pkgURI, alertType,
                        serverID, correlator);
                processMsg(pkg);
                break;
            }
            case DMIntent.TYPE_CLIENT_SESSION_REQUEST: {
                if (DBG) Log.d(TAG, "Client initiated dm session was received.");

                DMSessionPkg pkg = new DMSessionPkg(intentType, requestID);
                String serverID = intent.getStringExtra(DMIntent.FIELD_SERVERID);
                int timer = intent.getIntExtra(DMIntent.FIELD_TIMER, 0);

                // XXXXX FIXME this should not be here!
                synchronized (this) {
                    try {
                        if (DBG) Log.d(TAG, "Timeout: " + timer);
                        if (timer > 0) {
                            wait(timer * 1000);
                        }
                    } catch (InterruptedException e) {
                        if (DBG) Log.d(TAG, "Waiting has been interrupted.");
                    }
                }
                if (DBG) Log.d(TAG, "Starting session.");

                if (serverID != null && !serverID.isEmpty()) {
                    pkg.mobj = serverID;
                    processMsg(pkg);
                }
                break;
            }
            case DMIntent.TYPE_CANCEL_DM_SESSION: {
                cancelSession(requestID);
                processMsg(new DMSessionPkg(DMIntent.TYPE_DO_NOTHING, requestID));
                break;
            }
            case DMIntent.TYPE_LAWMO_NOTIFY_SESSION: {
                if (DBG) Log.d(TAG, "LAWMO Notify DM Session was received");

                DMSessionPkg pkg = new DMSessionPkg(intentType, requestID);

                String result = intent.getStringExtra(DMIntent.FIELD_LAWMO_RESULT);
                String pkgURI = intent.getStringExtra(DMIntent.FIELD_PKGURI);
                String alertType = intent.getStringExtra(DMIntent.FIELD_ALERTTYPE);
                String correlator = intent.getStringExtra(DMIntent.FIELD_CORR);

                pkg.mobj = new FotaNotifyContext(result, pkgURI, alertType, null, correlator);
                processMsg(pkg);
                break;
            }
            case DMIntent.TYPE_UNITEST_SET_STRING_NODE: {
                String path = intent.getStringExtra("NodePath");
                String value = intent.getStringExtra("NodeValue");

                DMSessionPkg pkg = new DMSessionPkg(intentType, requestID);
                pkg.mobj = new Pair<String, String>(path, value);
                processMsg(pkg);
                break;
            }
            case DMIntent.TYPE_UNITEST_GET_STRING_NODE: {
                String path = intent.getStringExtra("NodePath");

                DMSessionPkg pkg = new DMSessionPkg(intentType, requestID);

                pkg.mobj = path;
                processMsg(pkg);
                break;
            }
            case DMIntent.TYPE_UNITEST_DUMP_TREE: {
                String path = intent.getStringExtra("NodePath");

                DMSessionPkg pkg = new DMSessionPkg(intentType, requestID);

                pkg.mobj = path;
                processMsg(pkg);
                break;
            }
            case DMIntent.TYPE_UNITEST_PROCESS_SCRIPT: {
                String fileName = intent.getStringExtra(DMIntent.FIELD_FILENAME);
                boolean isBinary = intent.getBooleanExtra(DMIntent.FIELD_IS_BINARY, false);

                DMSessionPkg pkg = new DMSessionPkg(intentType, requestID);

                pkg.mobj = fileName;
                pkg.mbvalue = isBinary;
                processMsg(pkg);
                break;
            }
        }
    }

    // private final IDmClientService.Stub mBinder = new IDmClientService.Stub()
    // {

    String setStringNode(String node, String value) {
        if (mInitGood) {
            return NativeDM.setStringNode(node, value);
        }
        return "DM Engine initialization failed";
    }

    String dumpTree(String node) {
        if (mInitGood) {
            return NativeDM.dumpTree(node);
        }
        return "DM Engine initialization failed";
    }

    // FIXME: this must be called from native code, right?
    // FIXME: can native code ever throw RemoteException??
    public String executePlugin(String node, String data) {
        if (mInitGood) {
            return NativeDM.executePlugin(node, data);
        }
        return "DM Engine initialization failed";
    }

    String getNodeInfo(String node) {
        if (mInitGood) {
            return NativeDM.getNodeInfo(node);
        }
        return "DM Engine initialization failed";
    }

    public int deleteNode(String node) {
        if (mInitGood) {
            return NativeDM.deleteNode(node);
        }
        return DMResult.SYNCML_DM_FAIL;
    }

    public int createInterior(String node) {
        if (mInitGood) {
            return NativeDM.createInterior(node);
        }
        return DMResult.SYNCML_DM_FAIL;
    }

    public int createLeaf(String node, String value) {
        if (mInitGood) {
            return NativeDM.createLeaf(node, value);
        }
        return DMResult.SYNCML_DM_FAIL;
    }

    public String getNodeInfoSP(String node) {
        if (mInitGood) {
            return NativeDM.getNodeInfo(node);
        }
        return null;
    }

    byte[] processScript(String fileName, boolean isBinary, DMSession session) {
        if (DBG) logd("processScript");

        DMConfigureDB configDB = getConfigDB();

        if (session != null && configDB != null) {
            boolean isDmAlertEnabled = configDB.isDmAlertEnabled();
            session.getDMAlert().setUIMode(isDmAlertEnabled);
            if (DBG) logd("isDmAlertEnabled=" + session.getDMAlert().getUIMode());
        }

        byte[] bResult = null;
        if (mInitGood) {
            int dmResult = 0;
            bResult = NativeDM.processScript("localhost", fileName, isBinary, dmResult, session);
            if (DBG) Log.d(TAG, "dmResult=" + dmResult);
            if (bResult != null) {
                if (DBG) Log.d(TAG, "result buf size=" + bResult.length);

            }
        }
        return bResult;
    }
    //};

    // This is the object that receives interactions from clients. See
    // RemoteService for a more complete example.
    private final IBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent arg0) {
        if (DBG) Log.d(TAG, "entering onBind()");
        DMConfigureDB db = getConfigDB();   // wait for configure DB to initialize
        if (DBG) Log.d(TAG, "returning mBinder");
        return mBinder;
    }

    @Override
    public void onLowMemory() {
    }

    /**
     * Get the {@code DMConfigureDB} object from the AsyncTask, waiting up to 70 seconds.
     * @return the {@code DMConfigureDB} object, or null if the AsyncTask failed
     */
    public DMConfigureDB getConfigDB() {
        try {
            return mDMConfigureTask.get(70, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "onBind() got InterruptedException waiting for config DB", e);
        } catch (ExecutionException e) {
            Log.e(TAG, "onBind() got ExecutionException waiting for config DB", e);
        } catch (TimeoutException e) {
            Log.e(TAG, "onBind() got TimeoutException waiting for config DB", e);
        }
        return null;
    }

    // DMT locking mechanism. Used by DMSettingsProvider
    public void lockDmt() {
        mIsDmtLocked.set(true);
    }

    public void unlockDmt() {
        mIsDmtLocked.set(false);
    }

    public boolean isDmtLocked() {
        return mIsDmtLocked.get();
    }

    public String parseBootstrapServerId(byte[] data, boolean isWbxml) {
        String retServerId = NativeDM.parseBootstrapServerId(data, isWbxml);
        if (DBG) Log.d(TAG, "parseBootstrapServerId retServerId=" + retServerId);

        if (DBG) {  // dump data for debug
            int logLevel = getConfigDB().getSyncMLLogLevel();
            if (logLevel > 0) {
                try {
                    // FIXME SECURITY: don't open file as world writeable, WTF!
                    FileOutputStream os = openFileOutput("syncml_" + System.currentTimeMillis()
                            + ".dump", MODE_WORLD_WRITEABLE);
                    os.write(data);
                    os.close();
                    Log.d(TAG, "xml/wbxml file saved to "
                            + getApplication().getFilesDir().getAbsolutePath());

                    if (isWbxml && logLevel == 2) {
                        byte[] xml = NativeDM.nativeWbxmlToXml(data);
                        if (xml != null) {
                            // FIXME SECURITY: don't open file as world writeable, WTF!
                            FileOutputStream xmlos = openFileOutput("syncml_"
                                    + System.currentTimeMillis() + ".xml", MODE_WORLD_WRITEABLE);
                            xmlos.write(xml);
                            xmlos.close();
                            Log.d(TAG, "wbxml2xml converted successful and saved to file");
                        }
                    }
                }
                catch (FileNotFoundException e) {
                    Log.d(TAG, "unable to open file for wbxml, e=" + e.toString());
                }
                catch (IOException e) {
                    Log.d(TAG, "unable to write to wbxml file, e=" + e.toString());
                }
                catch(Exception e) {
                    Log.e(TAG, "Unexpected exception converting wbxml to xml, e=" + e.toString());
                }
            }
        }
        return retServerId;
    }

    public static int processBootstrapScript(byte[] data, boolean isWbxml, String serverId) {
        int retcode = NativeDM.processBootstrapScript(data, isWbxml, serverId);
        if (DBG) Log.d(TAG, "processBootstrapScript retcode=" + retcode);
        return retcode;
    }

    private Runnable mAbortSession = new Runnable() {
        @Override
        public void run() {
            cancelSession(0);
        }
    };

    // FIXME: only used from SessionThread inner class
    private void startTimeOutTick(long delayTime) {
        synchronized (mSessionTimeOutHandler) {
            mSessionTimeOutHandler.removeCallbacks(mAbortSession);
            mSessionTimeOutHandler.postDelayed(mAbortSession, delayTime);
        }
    }

    private static boolean copyFile(InputStream in, File to) {
        try {
            if (!to.exists()) {
                to.createNewFile();
            }
            OutputStream out = new FileOutputStream(to);
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            out.close();
        } catch (IOException e) {
            Log.e(TAG, "Error: copyFile exception", e);
            return false;
        }
        return true;
    }

    /**
     * Copy files from assets folder.
     * @return true on success; false on any failure
     */
    private boolean copyFilesFromAssets() {
        // Check files in assets folder
        String strDes = getFilesDir().getAbsolutePath() + "/dm";
        if (DBG) Log.d(TAG, "w21034: directory is: " + strDes);
        File dirDes = new File(strDes);
        if (dirDes.exists() && dirDes.isDirectory()) {
            if (DBG) Log.d(TAG, "W21034:Predefined files already created: " + strDes);
            return true;
        }
        if (DBG) Log.d(TAG, "Predefined files not created: " + strDes);
        if (!dirDes.mkdir()) {
            if (DBG) Log.d(TAG, "Failed to create dir: " + dirDes.getAbsolutePath());
            return false;
        }
        // Create log directory.
        File dirLog = new File(dirDes, "log");
        // FIXME: don't ignore return value
        dirLog.mkdir();
        if (DBG) Log.d(TAG, "w21034: read assets");
        try {
            AssetManager am = getAssets();
            String[] arrRoot = am.list("dm");
            int cnt = arrRoot.length;
            if (DBG) Log.d(TAG, "w21034: assets count: " + cnt);
            for (int i = 0; i < cnt; i++) {
                if (DBG) Log.d(TAG, "Root No. " + i + ':' + arrRoot[i]);
                File dir2 = new File(dirDes, arrRoot[i]);
                if (!dir2.mkdir()) {
                    // FIXME: don't ignore return value
                    dirDes.delete();
                    return false;
                }
                String[] arrSub = am.list("dm/" + arrRoot[i]);
                int cntSub = arrSub.length;
                if (DBG) Log.d(TAG, arrRoot[i] + " has " + cntSub + " items");
                if (cntSub > 0) {
                    for (int j = 0; j < cntSub; j++) {
                        if (DBG) Log.d(TAG, "Sub No. " + j + ':' + arrSub[j]);
                        File to2 = new File(dir2, arrSub[j]);
                        String strFrom = "dm/" + arrRoot[i] + '/' + arrSub[j];
                        InputStream in2 = am.open(strFrom);
                        if (!copyFile(in2, to2)) {
                            // FIXME: don't ignore return value
                            dirDes.delete();
                            return false;
                        }
                    }
                }
            }
        } catch (IOException e) {
            loge("error copying file from assets", e);
            return false;
        }
        return true;
    }

    private static void logd(String msg) {
        Log.d(TAG, msg);
    }

    private static void loge(String msg, Throwable tr) {
        Log.e(TAG, msg, tr);
    }
}
