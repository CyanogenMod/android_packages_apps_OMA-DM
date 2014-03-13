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

import android.app.IntentService;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class ConnmoBackupService extends IntentService {
    private static final String TAG = "ConnmoBackupService";

    public ConnmoBackupService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String action = intent.getAction();
        if (ConnmoPlugin.CONNMO_LOGD)
            Log.i(TAG, "Requested action: " + action);
        if (ConnmoConstants.INTENT_DISABLE_APN2.equals(action)) {
            if (updateDisableAPN2() > 0)
                Log.i(TAG, "APN2 is disabled");
            else
                Log.i(TAG, "Error in disabling APN2");
        }
    }

    private int updateDisableAPN2() {
        ContentValues values = new ContentValues();
        values.put("carrier_enabled", false);
        String where = "name='Verizon FOTA'";
        return getContentResolver().update(ConnmoPlugin.CONTENT_URI, values, where, null);
    }
}
