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
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.Log;

public class DataAndCallStateMonitoringService extends Service {
    private static final String TAG = "DataAndCallStateMonitoringService";
    private static final boolean DBG = DMClientService.DBG;

    @Override
    public void onCreate() {
        super.onCreate();
        if (DBG) logd("onCreate()");
    }

    @Override
    public void onDestroy() {
        if (DBG) logd("onDestroy()");
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DBG) logd("inside onStartCommand(intent, " + flags + ", " + startId + ')');
        new DataAndCallStateListener(this, startId);
        return Service.START_STICKY;
    }

    private static class DataAndCallStateListener extends PhoneStateListener {
        private final int mStartID;
        private Service mService;
        private final TelephonyManager mTelephonyManager;

        DataAndCallStateListener(Service service, int startID) {
            mService = service;
            mStartID = startID;
            TelephonyManager telephonyManager = (TelephonyManager) service
                    .getSystemService(Context.TELEPHONY_SERVICE);
            mTelephonyManager = telephonyManager;
            telephonyManager.listen(this, PhoneStateListener.LISTEN_CALL_STATE
                    | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
                    | PhoneStateListener.LISTEN_SERVICE_STATE);
        }

        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            if (serviceState.getDataNetworkType() != TelephonyManager.NETWORK_TYPE_UNKNOWN) {
                Log.d(TAG, "data network is now available.");
                sendIntentAndStopListener();
            }
        }

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            if (TelephonyManager.CALL_STATE_IDLE == state) {
                int dataNetworkType = mTelephonyManager.getDataNetworkType();
                Log.d(TAG, "Data network type: " + dataNetworkType);

                if (dataNetworkType == TelephonyManager.NETWORK_TYPE_UNKNOWN) {
                    Log.d(TAG, "no data network, waiting...");
                    return;
                }

                //DMHelper.cancelTimeAlert(mContext, DmAlarmReceiver.class); ????????
                sendIntentAndStopListener();
            }
        }

        @Override
        public void onDataConnectionStateChanged(int state) {
            if (TelephonyManager.DATA_CONNECTED == state) {
                int callState = mTelephonyManager.getCallState();
                Log.d(TAG, "CallState: " + callState);

                if (callState != TelephonyManager.CALL_STATE_IDLE) {
                    Log.d(TAG, "CallState is busy. Waiting...");
                    return;
                }

                //DMHelper.cancelTimeAlert(mContext, DmAlarmReceiver.class); ??????
                sendIntentAndStopListener();
            }
        }

        private void sendIntentAndStopListener() {
            mTelephonyManager.listen(this, PhoneStateListener.LISTEN_NONE);
            Service service = mService;
            if (service != null) {
                Intent intent = new Intent(service, DMIntentReceiver.class);
                intent.setAction(DMIntent.ACTION_CALL_AND_DATA_STATE_READY);
                service.sendBroadcast(intent);
                if (service.stopSelfResult(mStartID)) {
                    Log.d(TAG, "stopping service for ID " + mStartID);
                    mService = null;
                }
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static void logd(String msg) {
        Log.d(TAG, msg);
    }
}
