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

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;

import java.util.Timer;
import java.util.TimerTask;

/**
 * This service waits for the FOTA APN to come up, then notifies {@link DMIntentReceiver}.
 * FIXME: this class should be rewritten for readability and simplicity.
 */
public class ApnStateMonitoringService extends Service {
    private static final String TAG = "ApnStateMonitoringService";
    private static final boolean DBG = DMClientService.DBG;

    private static final int APN_CHECK_TIMEOUT = 120 * 1000; // 2 min
    private static final int MAX_RETRY_COUNT = 1;

    private ConnectivityManager mConnMgr;
    private NetworkInfo mNetworkInfo;
    private BroadcastReceiver mConnectivityReceiver;
    // private static boolean mIsFotaApnEnabled = false;
    private static Timer sTimer;
    private static int sRetryCount;
    private TelephonyManager mTelephonyManager;
    private boolean isDataRoaming;
    private RFPhoneStateListener mRFPhoneStateListener;

    /**
     * The state of a data connection.
     * <ul>
     * <li>CONNECTED = IP traffic should be available</li>
     * <li>CONNECTING = Currently setting up data connection</li>
     * <li>DISCONNECTED = IP not available</li>
     * <li>SUSPENDED = connection is created but IP traffic is
     *                 temporarily not available. i.e. voice call is in place
     *                 in 2G network</li>
     * </ul>
     */
    enum DataState {
        CONNECTED, CONNECTING, DISCONNECTED, SUSPENDED
    }

    @Override
    public void onCreate() {
        mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        mRFPhoneStateListener = new RFPhoneStateListener();
        mTelephonyManager.listen(mRFPhoneStateListener, PhoneStateListener.LISTEN_SERVICE_STATE);
        checkApnState();
        registerNetworkReceiver();
    }

    private void checkApnState() {
        if (DBG) logd("checkApnState: retries left = " + (MAX_RETRY_COUNT - sRetryCount));
        int fotaApnState = getFotaApnState();

        if (sRetryCount < MAX_RETRY_COUNT) {
            mConnMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            int result = mConnMgr.startUsingNetworkFeature(ConnectivityManager.TYPE_MOBILE,
                    Phone.FEATURE_ENABLE_FOTA);
            switch (result) {
                case PhoneConstants.APN_ALREADY_ACTIVE:
                    if (DBG) logd("FOTA APN enabled");
                    informApnActiveState();
                    break;
                case PhoneConstants.APN_REQUEST_STARTED:
                    if (DBG) logd("Waiting to enable FOTA APN");
                    startTimer();   // FIXME change to use StateMachine
                    break;
                default:
                    if (DBG) logd("Failed to enable Fota apn: errno=" + result);
                    startTimer();   // FIXME: change to use StateMachine
                    break;
            }
            sRetryCount++;
        } else {
            if (DBG) logd("Couldn't enable fota apn after trying " + sRetryCount + " times");
            stopUsingFotaApn();
            stopTimer();
            resetVariables();

            if (fotaApnState == DMHelper.FOTA_APN_STATE_START_DM_SESSION) {
                if (DBG) logd("Request for START_DM_SESSION apn switch failed");
                DMHelper.cleanAllResources(this);
                DMHelper.cleanFotaApnResources(this);
            } else if (fotaApnState == DMHelper.FOTA_APN_STATE_REPORT_DM_SESSION) {
                // TODO
                // TBD if its ok not doing FDM
                if (DBG) logd("Request for REPORTING_DM_SESSION apn switch failed");
                DMHelper.cleanFotaApnResources(this);
            }
            stopSelf();
        }
        if (DBG) logd("leaving checkApnState()");
    }

    private void informApnActiveState() {
        if (DBG) logd("Inside informApnActiveState()");

        if (isDataRoaming) {
            if (DBG) logd("VZW 4G Data Roaming: don't allow DMSession.");
            stopUsingFotaApn();
            if (getFotaApnState() == DMHelper.FOTA_APN_STATE_START_DM_SESSION) {
                DMHelper.cleanAllResources(this);
                DMHelper.cleanFotaApnResources(this);
            } else if (getFotaApnState() == DMHelper.FOTA_APN_STATE_REPORT_DM_SESSION) {
                DMHelper.cleanFotaApnResources(this);
            }
        } else {
            if (DBG) logd("send APN active broadcast to DMIntentReceiver");
            Intent intent = new Intent(this, DMIntentReceiver.class);
            intent.setAction(DMIntent.ACTION_APN_STATE_ACTIVE_READY);
            sendBroadcast(intent);
            if (DBG) logd("sent APN active broadcast intent");
            unregisterNetworkReceiver();
        }
        stopTimer();
        resetVariables();
        stopSelf();
    }

    private void stopUsingFotaApn() {
        if (DBG) logd("stopUsingFotaApn");

        unregisterNetworkReceiver();
        mConnMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        int result = mConnMgr.stopUsingNetworkFeature(ConnectivityManager.TYPE_MOBILE,
                Phone.FEATURE_ENABLE_FOTA);
        if (result != -1) {
            if (DBG) logd("stopUsingNetworkFeature result=" + result);
        }
    }

    private int startTimer() {
        if (DBG) logd("startTimer, current:" + System.currentTimeMillis());
        synchronized (this) {
            try {
                if (sTimer == null) {
                    if (DBG) logd("startTimer, create, schedule");
                    ApnCheckTask task = new ApnCheckTask();
                    sTimer = new Timer();
                    sTimer.schedule(task, APN_CHECK_TIMEOUT);
                    //sTimer.schedule(task, APN_CHECK_TIMEOUT,APN_CHECK_PERIOD);
                } else {
                    if (DBG) logd("startTimer has been scheduled, do nothing");
                }
            } catch (IllegalArgumentException e) {
                loge("IllegalArgumentException, ignore it.", e);
            } catch (IllegalStateException e) {
                loge("IllegalStateException: maybe canceled, ignore it.", e);
            }
            return 0;
        }
    }

    private void stopTimer() {
        synchronized (this) {
            if (sTimer != null) {
                if (DBG) logd("stopTimer, cancel()");
                sTimer.cancel();
                sTimer = null;
            } else {
                if (DBG) logd("stopTimer, have stopped, do nothing");
            }
        }
    }

    private class ApnCheckTask extends TimerTask {
        ApnCheckTask() {
        }

        @Override
        public void run() {
            if (DBG) logd("ApnCheckTask Timeout, to stop service, current:"
                    + System.currentTimeMillis());
            checkApnState();
        }
    }

    private void resetVariables() {
        if (DBG) logd("inside resetVariables()");
        sRetryCount = 0;
        mTelephonyManager.listen(mRFPhoneStateListener, PhoneStateListener.LISTEN_NONE);
    }

    // get current state.
    private int getFotaApnState() {
        SharedPreferences p = getSharedPreferences(DMHelper.FOTA_APN_PREFERENCE_KEY, 0);
        return p.getInt(DMHelper.FOTA_APN_STATE_KEY, 0);
    }

    @Override
    public void onDestroy() {
        if (DBG) logd("inside onDestroy()");
        stopTimer();
        resetVariables();
        unregisterNetworkReceiver();
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DBG) logd("inside onStartCommand()");
        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    // used inside ConnectivityBroadcastReceiver
    static DataState getMobileDataState(Intent intent) {
        String str = intent.getStringExtra(PhoneConstants.STATE_KEY);
        if (str != null) {
            return Enum.valueOf(DataState.class, str);
            //return DataState.CONNECTED;
        } else {
            return DataState.DISCONNECTED;
        }
    }

    private class ConnectivityBroadcastReceiver extends BroadcastReceiver {

        ConnectivityBroadcastReceiver() {
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION) ) {
                if (DBG) logd("onReceive() called with intent: " + intent);

                boolean noConnectivity =
                        intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);

                // FIXME: javadoc for EXTRA_NETWORK_INFO says to use getActiveNetworkInfo() / getAllNetworkInfo()
                mNetworkInfo = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);

                if (DBG) logd("onReceive(): mNetworkInfo=" + mNetworkInfo
                        + " noConnectivity=" + noConnectivity);


                if (mNetworkInfo != null) {
                    if (mNetworkInfo.isConnected()
                            && mNetworkInfo.getType() == ConnectivityManager.TYPE_MOBILE_FOTA) {
                        if (DBG) logd("FOTA APN active, start the process");
                        //queryApnStatus();
                        informApnActiveState();
                    }
                }
            }	else if (action.equals(TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED)) {
                DataState state = getMobileDataState(intent);
                String apnName = intent.getStringExtra(PhoneConstants.DATA_APN_KEY);
                if (apnName == null) {
                    if (DBG) logd("apnName name should be non-null");
                    return;
                }
                // TODO: allow client plugins to specify the required APN name
                if (DBG) logd("APN = " + apnName);
                boolean unavailable = intent.getBooleanExtra(
                        PhoneConstants.NETWORK_UNAVAILABLE_KEY, false);
                if (DBG) logd("Received " + intent.getAction() + " broadcast - state = " + state
                        + ", unavailable = " + unavailable);

                switch (state) {
                    case CONNECTED:
                        String ifaceName = intent.getStringExtra(PhoneConstants.DATA_IFACE_NAME_KEY);
                        if (DBG) logd("interface = " + ifaceName);
                        if (ifaceName == null) {
                            //cant come here
                            loge("CONNECTED event did not supply interface name!", new Throwable());
                        }
                        setApnInterfaceName(ifaceName);
                        //queryApnStatus();
                        break;

                    case CONNECTING:
                        if (DBG) logd("CONNECTING");
                        break;

                    case DISCONNECTED:
                        if (DBG) logd("DISCONNECTED");
                        break;

                    case SUSPENDED:
                        if (DBG) logd("SUSPENDED");
                        break;
                }
            }
        } //onReceive
    }

    private final class RFPhoneStateListener extends PhoneStateListener {

        RFPhoneStateListener() {
        }

        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            isDataRoaming = getServiceStateDataRoaming(serviceState);
            if (DBG) logd("Service State intent received, Data Roaming State: " + isDataRoaming);
        }

        private boolean getServiceStateDataRoaming(ServiceState serviceState) {
            // FIXME: removed old code that tried to call getDataRoaming() through reflection.
            return serviceState.getRoaming();   // FIXME: is there a better method to use?
        }
    }

    private void registerNetworkReceiver() {
        if (DBG) logd("inside registerNetworkReceiver()");
        if (mConnectivityReceiver == null) {
            if (DBG) logd("registering new ConnectivityBroadcastReceiver");
            BroadcastReceiver connectivityReceiver = new ConnectivityBroadcastReceiver();
            mConnectivityReceiver = connectivityReceiver;
            IntentFilter filter = new IntentFilter();
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            filter.addAction(TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED);
            registerReceiver(connectivityReceiver, filter);
        }
    }

    void setApnInterfaceName(String intfName) {
        SharedPreferences p = getSharedPreferences(DMHelper.FOTA_APN_PREFERENCE_KEY, 0);
        SharedPreferences.Editor ed = p.edit();

        ed.putString(DMHelper.APN_INTERFACE_NAME_KEY, intfName);
        ed.apply();
    }

    private void unregisterNetworkReceiver() {
        if (DBG) logd("inside unregisterNetworkReceiver()");

        BroadcastReceiver oldConnectivityListener = mConnectivityReceiver;
        if (oldConnectivityListener != null) {
            mConnectivityReceiver = null;
            unregisterReceiver(oldConnectivityListener);
        }

        mNetworkInfo = null;
    }

    private static void logd(String msg) {
        Log.d(TAG, msg);
    }

    private static void loge(String msg, Throwable tr) {
        Log.e(TAG, msg, tr);
    }
}
