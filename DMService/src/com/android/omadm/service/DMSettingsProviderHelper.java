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

import java.util.HashMap;
import java.util.Map;

final class DMSettingsProviderHelper {

    public static final String TAG = "DMSettingsProviderHelper";

    public static final String APPID_VAL = "w7";

    public static final String APPID = "APPID";

    // DM versions
    public static final int DM_VERSION_ERR = -1;

    public static final int DM_1_1_2 = 0;

    public static final int DM_1_2 = 1;

    // constants mapping node info result from C++; values and variables

    public static final String NODE_INFO_VALUE_PREFIX = "value=";

    public static final String NODE_INFO_CHILDREN_PREFIX = "children:";

    public static final String NODE_INFO_CHILDREN_DELIM = "/";

    public static final String NODE_INFO_FAIL_START = "can't";

    public static final String TRUE = "true";

    public static final String FALSE = "false";

    public static final String NULL = "null";

    // DMT paths

    public static final String DM_VERSION_NODE_PATH = "./DevInfo/DmV";

    public static final String DM_1_1_2_ROOT = "./SyncML/DMAcc";

    public static final String DM_1_2_ROOT = "./DMAcc";

    // XML tags
    public static final String DM_1_1_2_SERVER_ID = "ServerId";

    public static final String DM_1_2_SERVER_ID = "ServerID";

    public static final String XML_UNIQUE_TAG = "PROVIDER-ID";

    public static final String XML_TAG_DELIM = "/";

    // field for auto generated unique name during preprocess

    public static final String DM_UNIQUE_ROOT_TAG = "DMUNIQUENAME";

    public static final Map<String, String> DM_1_2_PATH_MAP = new HashMap<String, String>();

    static {
        // map xml tags to the path for DM 1.2
        DM_1_2_PATH_MAP.put(APPID, "AppID");
        DM_1_2_PATH_MAP.put(XML_UNIQUE_TAG, DM_1_2_SERVER_ID);
        DM_1_2_PATH_MAP.put("NAME", "Name");
        DM_1_2_PATH_MAP.put("APPADDR", "AppAddr");
        DM_1_2_PATH_MAP.put("ADDR", "Addr");
        DM_1_2_PATH_MAP.put("ADDRTYPE", "AddrType");
        DM_1_2_PATH_MAP.put("PORT", "Port");
        DM_1_2_PATH_MAP.put("PORTNBR", "PortNbr");
        DM_1_2_PATH_MAP.put("APPAUTH", "AppAuth");
        DM_1_2_PATH_MAP.put("AAUTHLEVEL", "AAuthLevel");
        DM_1_2_PATH_MAP.put("AAUTHTYPE", "AAuthType");
        DM_1_2_PATH_MAP.put("AAUTHNAME", "AAuthName");
        DM_1_2_PATH_MAP.put("AAUTHSECRET", "AAuthSecret");
        DM_1_2_PATH_MAP.put("AAUTHDATA", "AAuthData");
    }

    private DMSettingsProviderHelper() {
    }
}
