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

public interface ConnmoConstants {

    // SharedPreference name and key for APN 1-5's name
    String APN_PREFERENCE_NAME = "apn_preference";
    String APN1_NAME = "apn1_name";
    String APN2_NAME = "apn2_name";
    String APN3_NAME = "apn3_name";
    String APN4_NAME = "apn4_name";
    String APN5_NAME = "apn5_name";

    // SharedPreference name and key to disable APN 2
    String APN2_PREFERENCE_NAME = "apn2_disable_state";
    String APN2_DISABLE_KEY = "apn2_disable";

    // Intent to disable APN2
    String INTENT_DISABLE_APN2 = "com.android.sdm.plugins.connmo.DISABLE_APN2";
}
