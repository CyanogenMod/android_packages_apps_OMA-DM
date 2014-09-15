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

package com.android.sdm.plugins.connmo;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Process;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.omadm.service.DMSettingsHelper;

import java.util.Calendar;

public class ConnmoReceiver extends BroadcastReceiver {

    static final String TAG = "DM_ConnmoPlugin";

    private Phone mPhone;

    private static boolean sfirstTriggerReceived = false;

    /**
     * OEM CDMA Telephony Manager
     */
    private static final String ACTION_NOTIFY_START_UP_DMSERVICE
            = "com.android.omadm.service.start_up";

    //--- Keys used for storing reset status ---//
    public static final String RESETBP_PREFERENCE_KEY = "resetbp";

    public static final String RESET_VALUE_KEY = "reset";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (disableIfSecondaryUser(context)) {
            return;
        }
        if (isVerizon(context) == false) {
            return;
        }
        logd("Received intent: " + intent.getAction());
        try {
            mPhone = PhoneFactory.getDefaultPhone();
        } catch (Exception e) {
            mPhone = null;
            Log.e(TAG, "Exception for Phone instance ready", e);
        }

        String action = intent.getAction();
        if (action == null) {
            Log.e(TAG, "intent action is null");
            return;
        }
        logd("XXX ConnmoReceiver intent.getAction(): " + action
                + " context.getFilesDir().getPath(): " + context.getFilesDir().getPath());
        if (action.equals("com.android.omadm.service.wait_timer_alert")) {
            if (mPhone == null) {
                handleConnmoRepeatDelayIntent(context, 2); //Repeat 2 sec delay
            } else {
                handleConnmoWaitTimerAlert(context);
            }
        } else if (action.equals("com.android.omadm.service.Result")) {
            handledmServiceResult(context);
        }
    }

    private void handleConnmoWaitTimerAlert(Context context) {
        logd("Inside handleConnmoWaitTimerAlert");
        if (isPhoneTypeLTE()) {
            String strIMEI = mPhone.getImei();
            logd("strIMEI = " + strIMEI);

            wakeUpDMService(context, strIMEI, "4g");
        }
    }

    private void handledmServiceResult(Context context) {
        logd("Inside handledmServiceResult");
        if (getAPN2DisableStatus(context).equalsIgnoreCase("yes")) {
            logd("Diasble APN2 Silently");
            startDisableAPN2Service(context);
            clearAPN2DisableStatus(context);
            if (getResetBPStatus(context).equalsIgnoreCase("yes")) {
                clearBPResetStatus(context);
            }
            return;
        }
        logd("Non APN2 disable session ,No APN2 Disable required");

        if (getResetBPStatus(context).equalsIgnoreCase("yes")) {
            logd("Reset BP Silently");
            clearBPResetStatus(context);
        } else {
            logd("Non eHRPD enable/disable session ,No Reset BP required");
        }
    }

    private boolean isPhoneTypeLTE() {
        return DMSettingsHelper.isPhoneTypeLTE();
    }

    private String getResetBPStatus(Context mContext) {
        SharedPreferences p = mContext.getSharedPreferences(RESETBP_PREFERENCE_KEY, 0);
        String getResetBPStatus = p.getString(RESET_VALUE_KEY, null);
        if (getResetBPStatus == null) {
            getResetBPStatus = "no";
        }
        logd("getResetBPStatus() = " + getResetBPStatus);
        return getResetBPStatus;
    }

    private void clearBPResetStatus(Context mContext) {
        logd("Inside clearBPResetStatus");
        SharedPreferences p = mContext.getSharedPreferences(RESETBP_PREFERENCE_KEY, 0);
        SharedPreferences.Editor ed = p.edit();
        ed.clear();
        ed.apply();
    }

    public static String getAPN2DisableStatus(Context mContext) {
        SharedPreferences p = mContext
                .getSharedPreferences(ConnmoConstants.APN2_PREFERENCE_NAME, 0);
        String getAPN2DisableStatus = p.getString(ConnmoConstants.APN2_DISABLE_KEY, null);
        if (getAPN2DisableStatus == null) {
            getAPN2DisableStatus = "no";
        }
        logd("getAPN2DisableStatus() = " + getAPN2DisableStatus);
        return getAPN2DisableStatus;
    }

    private void clearAPN2DisableStatus(Context mContext) {
        logd("Inside clearAPN2DisableStatus");
        SharedPreferences p = mContext
                .getSharedPreferences(ConnmoConstants.APN2_PREFERENCE_NAME, 0);
        SharedPreferences.Editor ed = p.edit();
        ed.clear();
        ed.apply();
    }

    private void startDisableAPN2Service(Context context) {
        Intent backupServiceIntent = new Intent();
        backupServiceIntent.setClass(context, ConnmoBackupService.class);
        backupServiceIntent.setAction(ConnmoConstants.INTENT_DISABLE_APN2);
        context.startService(backupServiceIntent);
    }

    private void wakeUpDMService(Context context, String result, String phoneType) {
        Intent intent = new Intent(ACTION_NOTIFY_START_UP_DMSERVICE);
        if (phoneType.equalsIgnoreCase("4g")) {
            intent.putExtra("gsmimei", result);
        } else if (phoneType.equalsIgnoreCase("3g")) {
            intent.putExtra("akey", result);
        }

        context.sendBroadcast(intent);
    }

    private void handleConnmoRepeatDelayIntent(Context context, int seconds) {

        logd("handleConnmoRepeatDelayIntent ...");
        Intent intent = new Intent("com.android.omadm.service.wait_timer_alert");
        PendingIntent sender = PendingIntent.getBroadcast(context, 0, intent, 0);

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.add(Calendar.SECOND, seconds);

        // Schedule the alarm
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.set(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), sender);
        logd("handleConnmoRepeatDelayIntent for " + seconds + " second done!");
    }

    public static boolean isRunningAsOwner() {
        return Process.myUserHandle().isOwner();
    }

    public static boolean disableIfSecondaryUser(Context context) {
        if (sfirstTriggerReceived == false) {
            sfirstTriggerReceived = true;
            if (!isRunningAsOwner()) {
                PackageManager pm = context.getPackageManager();
                logd("Disabling com.android.sdm.plugins.connmo for secondary user");
                pm.setApplicationEnabledSetting("com.android.sdm.plugins.connmo",
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0 );
                return true;
            }
        }
        return false;
    }

    private boolean isVerizon(Context context) {
        TelephonyManager tm = TelephonyManager.from(context);
        String simOperator = tm.getSimOperator();
        String imsi = tm.getSubscriberId();
        logd("simOperator: " + simOperator + " IMSI: " + imsi);
        if (!simOperator.isEmpty() || !imsi.isEmpty()) {
            for (String s : context.getResources().getStringArray(R.array.supported_plmns)) {
                if ((!simOperator.isEmpty() && s.equals(simOperator)) ||
                        (!imsi.isEmpty() && imsi.startsWith(s))) {
                    logd("We have a Verizon UICC");
                    return true;
                }
            }
        } else {
            /* If simOperator is not available, assume Verizon */
            return true;
        }

        return false;
    }

    private static void logd(String msg) {
        Log.d(TAG, msg);
    }

} // end of ConnmoReceiver class
