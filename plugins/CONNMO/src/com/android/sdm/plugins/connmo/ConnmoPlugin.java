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

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import com.android.omadm.plugin.DmtBasePlugin;
import com.android.omadm.plugin.DmtData;
import com.android.omadm.plugin.DmtException;
import com.android.omadm.plugin.DmtPathUtils;
import com.android.omadm.plugin.DmtPluginNode;
import com.android.omadm.plugin.ErrorCodes;
import com.android.omadm.service.DMSettingsHelper;

import java.util.HashMap;
import java.util.Map;

public class ConnmoPlugin extends DmtBasePlugin {
    static final String TAG = "DM_ConnmoPlugin";
    protected static final boolean CONNMO_LOGD = DmtBasePlugin.DEBUG;
    private String mRootPath;
    private final Context mContext;
    private static boolean mTargetQC = true;    // FIXME: why was this only for QC targets?
    // private Phone mPhone = null;
    private Map<String, DmtData> mNodeValues;
    public static final String CONNMO_ROOT = DmtPathUtils.ROOTNODE;
    public static final String CONNMO_PATH = "./ManagedObjects/ConnMO";

    /* 33886 and 34471 related global variable */
    public static final Uri CONTENT_URI = Uri.parse("content://telephony/carriers/");
    public String mWhere;
    /* end */

    //--- Keys used for storing reset status ---//
    public static final String RESETBP_PREFERENCE_KEY = "resetbp";
    public static final String RESET_VALUE_KEY = "reset";

    public ConnmoPlugin(Context ctx) {
        mContext = ctx;
        // mPhone = PhoneFactory.getDefaultPhone();
    }

    @Override
    public boolean init(String rootPath, Map parameters) {
        if (CONNMO_LOGD) Log.i(TAG, "Enter ConnmoPlugin.init");
        mRootPath = rootPath;
        mNodeValues = new HashMap<String, DmtData>();

        if (isPhoneTypeLTE()) {
            String tmpPath = CONNMO_ROOT;
            DmtData tmpNodeData = new DmtData("LTE|IMS|ext|VZW800|IPV6Enable", DmtData.NODE);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE";
            tmpNodeData = new DmtData("APN", DmtData.NODE);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "IMS";
            tmpNodeData = new DmtData("Setting", DmtData.NODE);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "ext";
            tmpNodeData = new DmtData("Settings", DmtData.NODE);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "VZW800";
            tmpNodeData = new DmtData("APN", DmtData.NODE);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "IPV6Enable";
            tmpNodeData = new DmtData("Setting", DmtData.NODE);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE/APN";
            tmpNodeData = new DmtData("1|2|3|4", DmtData.NODE);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "IMS/Setting";
            tmpNodeData = new DmtData(
                    "Domain|SIPT1|SIPTf|SIPT2|smsformat|sms_over_IP_network_indication",
                    DmtData.NODE);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "ext/Settings";
            tmpNodeData = new DmtData("t_mpsr|tbsr_cdma|t_1xRTT", DmtData.NODE);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "VZW800/APN";
            tmpNodeData = new DmtData("5", DmtData.NODE);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "IPV6Enable/Setting";
            tmpNodeData = new DmtData("Enabled|Operations", DmtData.NODE);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "IPV6Enable/Setting/Operations";
            tmpNodeData = new DmtData("Enable|Disable", DmtData.NODE);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "IPV6Enable/Setting/Enabled";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "IPV6Enable/Setting/Operations/Enable";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "IPV6Enable/Setting/Operations/Disable";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE/APN/1";
            tmpNodeData = new DmtData("Setting", DmtData.NODE);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE/APN/2";
            tmpNodeData = new DmtData("Setting", DmtData.NODE);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE/APN/3";
            tmpNodeData = new DmtData("Setting", DmtData.NODE);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE/APN/4";
            tmpNodeData = new DmtData("Setting", DmtData.NODE);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "VZW800/APN/5";
            tmpNodeData = new DmtData("Setting", DmtData.NODE);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "IMS/Setting/Domain";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "IMS/Setting/SIPT1";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "IMS/Setting/SIPTf";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "IMS/Setting/SIPT2";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "IMS/Setting/smsformat";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "IMS/Setting/sms_over_IP_network_indication";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "ext/Settings/t_mpsr";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "ext/Settings/tbsr_cdma";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "ext/Settings/t_1xRTT";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE/APN/1/Setting";
            tmpNodeData = new DmtData("Id|Name|IP|Enabled|Operations", DmtData.NODE);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE/APN/2/Setting";
            tmpNodeData = new DmtData("Id|Name|IP|Enabled|Operations", DmtData.NODE);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE/APN/3/Setting";
            tmpNodeData = new DmtData("Id|Name|IP|Enabled|Operations", DmtData.NODE);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE/APN/4/Setting";
            tmpNodeData = new DmtData("Id|Name|IP|Enabled|Operations", DmtData.NODE);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "VZW800/APN/5/Setting";
            tmpNodeData = new DmtData("Id|Name|IP|Enabled|Operations", DmtData.NODE);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE/APN/1/Setting/Operations";
            tmpNodeData = new DmtData("Disable", DmtData.NODE);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE/APN/2/Setting/Operations";
            tmpNodeData = new DmtData("Disable", DmtData.NODE);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE/APN/3/Setting/Operations";
            tmpNodeData = new DmtData("Enable|Disable", DmtData.NODE);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE/APN/4/Setting/Operations";
            tmpNodeData = new DmtData("Enable|Disable", DmtData.NODE);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "VZW800/APN/5/Setting/Operations";
            tmpNodeData = new DmtData("Enable|Disable", DmtData.NODE);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE/APN/1/Setting/Id";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE/APN/1/Setting/Name";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE/APN/1/Setting/IP";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE/APN/1/Setting/Enabled";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE/APN/1/Setting/Operations/Disable";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE/APN/2/Setting/Id";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE/APN/2/Setting/Name";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE/APN/2/Setting/IP";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE/APN/2/Setting/Enabled";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE/APN/2/Setting/Operations/Disable";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE/APN/3/Setting/Id";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE/APN/3/Setting/Name";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE/APN/3/Setting/IP";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE/APN/3/Setting/Enabled";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE/APN/3/Setting/Operations/Enable";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE/APN/3/Setting/Operations/Disable";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE/APN/4/Setting/Id";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE/APN/4/Setting/Name";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE/APN/4/Setting/IP";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE/APN/4/Setting/Enabled";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE/APN/4/Setting/Operations/Enable";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "LTE/APN/4/Setting/Operations/Disable";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "VZW800/APN/5/Setting/Id";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "VZW800/APN/5/Setting/Name";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "VZW800/APN/5/Setting/IP";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "VZW800/APN/5/Setting/Enabled";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "VZW800/APN/5/Setting/Operations/Enable";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);

            tmpPath = "VZW800/APN/5/Setting/Operations/Disable";
            tmpNodeData = new DmtData(null, DmtData.STRING);
            mNodeValues.put(tmpPath, tmpNodeData);
        }

        return true;
    }

    @Override
    public DmtPluginNode getNode(String path) {
        DmtPluginNode node = new DmtPluginNode("", new DmtData("abc"));
        setOperationResult(node == null ?
                ErrorCodes.SYNCML_DM_FAIL :
                ErrorCodes.SYNCML_DM_SUCCESS);
        return node;
    }

    @Override
    public DmtData getNodeValue(String path) {
        if (CONNMO_LOGD) Log.i(TAG, "rootPath=" + mRootPath);
        if (CONNMO_LOGD) Log.i(TAG, "path=" + path);
        //mContext.enforceCallingPermission("com.android.permission.READ_OMADM_SETTINGS", "Insufficient Permissions");

        DmtData data = null;

        if (mTargetQC) {
            if (CONNMO_LOGD) Log.d(TAG, "Target device is QC");
            if (path.equals("./ManagedObjects/ConnMO/LTE/APN/1/Setting/Id")||
                    path.equals("./ManagedObjects/ConnMO/LTE/APN/1/Setting/Name")||
                    path.equals("./ManagedObjects/ConnMO/LTE/APN/1/Setting/IP")||
                    path.equals("./ManagedObjects/ConnMO/LTE/APN/1/Setting/Enabled")||

                    path.equals("./ManagedObjects/ConnMO/LTE/APN/3/Setting/Id")||
                    path.equals("./ManagedObjects/ConnMO/LTE/APN/3/Setting/Name")||
                    path.equals("./ManagedObjects/ConnMO/LTE/APN/3/Setting/IP")||
                    path.equals("./ManagedObjects/ConnMO/LTE/APN/3/Setting/Enabled")||

                    path.equals("./ManagedObjects/ConnMO/LTE/APN/2/Setting/Id")||
                    path.equals("./ManagedObjects/ConnMO/LTE/APN/2/Setting/Name")||
                    path.equals("./ManagedObjects/ConnMO/LTE/APN/2/Setting/IP")||
                    //path.equals("./ManagedObjects/ConnMO/LTE/APN/2/Setting/Enabled")||

                    path.equals("./ManagedObjects/ConnMO/LTE/APN/4/Setting/Id")||
                    path.equals("./ManagedObjects/ConnMO/LTE/APN/4/Setting/Name")||
                    path.equals("./ManagedObjects/ConnMO/LTE/APN/4/Setting/IP")||
                    path.equals("./ManagedObjects/ConnMO/LTE/APN/4/Setting/Enabled")||

                    path.equals("./ManagedObjects/ConnMO/VZW800/APN/5/Setting/Id")||
                    path.equals("./ManagedObjects/ConnMO/VZW800/APN/5/Setting/Name")||
                    path.equals("./ManagedObjects/ConnMO/VZW800/APN/5/Setting/IP")||
                    path.equals("./ManagedObjects/ConnMO/VZW800/APN/5/Setting/Enabled"))

            {
                if (CONNMO_LOGD) Log.d(TAG, "Entered getNodeValue for path: " + path);
                try {
                    data = getValue(path);
                } catch (SQLiteException e) {
                    if (CONNMO_LOGD) Log.d(TAG,
                            "SQLiteException trying to get non-existant path", e);
                    data = new DmtData("Value Unknown");
                }
                setOperationResult(ErrorCodes.SYNCML_DM_SUCCESS);
            } else if(path.equals("./ManagedObjects/ConnMO/LTE/APN/2/Setting/Enabled")) {
                if(ConnmoReceiver.getAPN2DisableStatus(mContext).equalsIgnoreCase("yes")) {
                    data = new DmtData(false);
                    if (CONNMO_LOGD) Log.d(TAG,"APN 2 Disable Flag set. Reading from flag instead of db");
                } else {
                    if (CONNMO_LOGD) Log.d(TAG, "Entered getNodeValue for path : " + path);
                    try {
                        data = getValue(path);
                    } catch (SQLiteException e) {
                        if (CONNMO_LOGD) Log.d(TAG,
                                "SQLiteException trying to get non-existant path", e);
                        data = new DmtData("Value Unknown");
                    }
                }
                setOperationResult(ErrorCodes.SYNCML_DM_SUCCESS);
            } else if (path.equals("./ManagedObjects/ConnMO/IPV6Enable/Setting/Enabled")) {
                if (CONNMO_LOGD) Log.d(TAG, "Entered getNodeValue for path : " + path);
                data = new DmtData("Value Unknown");
                setOperationResult(ErrorCodes.SYNCML_DM_SUCCESS);
            } else if (path.equals("./ManagedObjects/ConnMO/IMS/Setting/Domain")) {
                if (CONNMO_LOGD) Log.d(TAG, "Entered into IMS Domain Value");
                data = new DmtData("Value Unknown");
                setOperationResult(ErrorCodes.SYNCML_DM_SUCCESS);
            } else if (path.equals("./ManagedObjects/ConnMO/IMS/Setting/SIPT1")) {
                if (CONNMO_LOGD) Log.d(TAG, "Entered into IMS SIPT1 Value");
                data = new DmtData("Value Unknown");
                setOperationResult(ErrorCodes.SYNCML_DM_SUCCESS);
            } else if (path.equals("./ManagedObjects/ConnMO/IMS/Setting/SIPTf")) {
                if (CONNMO_LOGD) Log.d(TAG, "Entered into IMS SIPTF Value");
                data = new DmtData("Value Unknown");
                setOperationResult(ErrorCodes.SYNCML_DM_SUCCESS);
            } else if (path.equals("./ManagedObjects/ConnMO/IMS/Setting/SIPT2")) {
                if (CONNMO_LOGD) Log.d(TAG, "Entered into IMS SIPT2 Value");
                data = new DmtData("Value Unknown");
                setOperationResult(ErrorCodes.SYNCML_DM_SUCCESS);
            } else if (path.equals("./ManagedObjects/ConnMO/IMS/Setting/smsformat")) {
                if (CONNMO_LOGD) Log.d(TAG, "Entered into IMS smsformat Value");
                data = new DmtData("Value Unknown");
                setOperationResult(ErrorCodes.SYNCML_DM_SUCCESS);
            } else if (path.equals("./ManagedObjects/ConnMO/IMS/Setting/sms_over_IP_network_indication")) {
                if (CONNMO_LOGD) Log.d(TAG,
                        "Entered into IMS sms_over_IP_network_indication Value");
                data = new DmtData("Value Unknown");
                setOperationResult(ErrorCodes.SYNCML_DM_SUCCESS);
            } else if (path.equals("./ManagedObjects/ConnMO/ext/Settings/t_mpsr")) {
                if (CONNMO_LOGD) Log.d(TAG, "Entered into ext/Settings/t_mpsr Value");
                data = new DmtData("Value Unknown");
                setOperationResult(ErrorCodes.SYNCML_DM_SUCCESS);
            } else if (path.equals("./ManagedObjects/ConnMO/ext/Settings/tbsr_cdma")) {
                if (CONNMO_LOGD) Log.d(TAG, "Entered into ext/Settings/tbsr_cdma Value");
                data = new DmtData("Value Unknown");
                setOperationResult(ErrorCodes.SYNCML_DM_SUCCESS);
            } else if (path.equals("./ManagedObjects/ConnMO/ext/Settings/t_1xRTT")) {
                if (CONNMO_LOGD) Log.d(TAG, "Entered into ext/Settings/t_1xRTT Value");
                data = new DmtData("Value Unknown");
                setOperationResult(ErrorCodes.SYNCML_DM_SUCCESS);
            } else if (path.equals("./ManagedObjects/ConnMO/LTE/APN/1/Setting/Operations/Disable")||
                    path.equals("./ManagedObjects/ConnMO/LTE/APN/2/Setting/Operations/Disable") ||
                    path.equals("./ManagedObjects/ConnMO/LTE/APN/3/Setting/Operations/Enable") ||
                    path.equals("./ManagedObjects/ConnMO/LTE/APN/3/Setting/Operations/Disable") ||
                    path.equals("./ManagedObjects/ConnMO/LTE/APN/4/Setting/Operations/Enable") ||
                    path.equals("./ManagedObjects/ConnMO/LTE/APN/4/Setting/Operations/Disable") ||
                    path.equals("./ManagedObjects/ConnMO/VZW800/APN/5/Setting/Operations/Enable") ||
                    path.equals("./ManagedObjects/ConnMO/VZW800/APN/5/Setting/Operations/Disable")||
                    path.equals("./ManagedObjects/ConnMO/IPV6Enable/Setting/Operations/Enable") ||
                    path.equals("./ManagedObjects/ConnMO/IPV6Enable/Setting/Operations/Disable"))
            {
                if (CONNMO_LOGD) Log.d(TAG, "Entered get for Operations/Enable or Disable Value");
                String mEnable = "Not Allowed";
                data = new DmtData(mEnable);
                setOperationResult(ErrorCodes.SYNCML_DM_SUCCESS);
            } else {
                if (CONNMO_LOGD) Log.d(TAG, "Get Operation not supported");
                setOperationResult(ErrorCodes.SYNCML_DM_UNSUPPORTED_OPERATION);
            }
        }
        return data;
    }

    @Override
    public int updateLeafNode(String path, DmtData newValue) throws RemoteException {
        if (CONNMO_LOGD) Log.i(TAG, "rootPath=" + mRootPath);
        if (CONNMO_LOGD) Log.i(TAG, "path=" + path);
        //mContext.enforceCallingPermission("com.android.permission.WRITE_OMADM_SETTINGS", "Insufficient Permissions");

        if (mTargetQC) {
            if (CONNMO_LOGD) Log.d(TAG, "Target device is QC");
            if (path.equals("./ManagedObjects/ConnMO/LTE/APN/3/Setting/IP") ||
                    path.equals("./ManagedObjects/ConnMO/LTE/APN/2/Setting/IP")||
                    path.equals("./ManagedObjects/ConnMO/LTE/APN/4/Setting/IP")||
                    path.equals("./ManagedObjects/ConnMO/VZW800/APN/5/Setting/IP")||

                    path.equals("./ManagedObjects/ConnMO/LTE/APN/3/Setting/Name")||
                    path.equals("./ManagedObjects/ConnMO/LTE/APN/2/Setting/Name")||
                    path.equals("./ManagedObjects/ConnMO/LTE/APN/4/Setting/Name")||
                    path.equals("./ManagedObjects/ConnMO/VZW800/APN/5/Setting/Name")) {

                if (CONNMO_LOGD) Log.d(TAG, "Entered updateLeafNode for path : " + path);
                return updateValue(path,newValue);
            } else if(path.equals("./ManagedObjects/ConnMO/LTE/APN/1/Setting/IP")||
                    path.equals("./ManagedObjects/ConnMO/LTE/APN/1/Setting/Name")) {

                if (CONNMO_LOGD) Log.d(TAG, "Entered updateLeafNode for path : " + path);
                if (CONNMO_LOGD) Log.d(TAG, "set reset BP status in shared_pref for Verizon IMS");
                setResetBPValue();
                return updateValue(path,newValue);
            } else if (path.equals("./ManagedObjects/ConnMO/IMS/Setting/Domain")) {
                if (CONNMO_LOGD) Log.d(TAG, "Entered Replace for IMS Domain Value");
                return setOperationResult(ErrorCodes.SYNCML_DM_UNSUPPORTED_OPERATION);
            } else if (path.equals("./ManagedObjects/ConnMO/IMS/Setting/SIPT1")) {
                if (CONNMO_LOGD) Log.d(TAG, "Entered Replace for IMS SIPT1 Value");
                return setOperationResult(ErrorCodes.SYNCML_DM_UNSUPPORTED_OPERATION);
            } else if (path.equals("./ManagedObjects/ConnMO/IMS/Setting/SIPTf")) {
                if (CONNMO_LOGD) Log.d(TAG, "Entered Replace for IMS SIPTF Value");
                return setOperationResult(ErrorCodes.SYNCML_DM_UNSUPPORTED_OPERATION);
            } else if (path.equals("./ManagedObjects/ConnMO/IMS/Setting/SIPT2")) {
                if (CONNMO_LOGD) Log.d(TAG, "Entered Replace for IMS SIPT2 Value");
                return setOperationResult(ErrorCodes.SYNCML_DM_UNSUPPORTED_OPERATION);
            } else if (path.equals("./ManagedObjects/ConnMO/IMS/Setting/smsformat")) {
                if (CONNMO_LOGD) Log.d(TAG, "Entered Replace for IMS smsformat Value");
                return setOperationResult(ErrorCodes.SYNCML_DM_UNSUPPORTED_OPERATION);
            } else if (path.equals("./ManagedObjects/ConnMO/IMS/Setting/sms_over_IP_network_indication")) {
                if (CONNMO_LOGD) Log.d(TAG, "Entered Replace for IMS sms_over_IP Value");
                return setOperationResult(ErrorCodes.SYNCML_DM_UNSUPPORTED_OPERATION);
            } else if (path.equals("./ManagedObjects/ConnMO/ext/Settings/t_mpsr")) {
                if (CONNMO_LOGD) Log.d(TAG, "Entered Replace for ext/Settings/t_mpsr Value");
                return setOperationResult(ErrorCodes.SYNCML_DM_UNSUPPORTED_OPERATION);
            } else if (path.equals("./ManagedObjects/ConnMO/ext/Settings/tbsr_cdma")) {
                if (CONNMO_LOGD) Log.d(TAG, "Entered Replace for ext/Settings/tbsr_cdma Value");
                return setOperationResult(ErrorCodes.SYNCML_DM_UNSUPPORTED_OPERATION);
            } else if (path.equals("./ManagedObjects/ConnMO/ext/Settings/t_1xRTT")) {
                if (CONNMO_LOGD) Log.d(TAG, "Entered Replace for ext/Settings/t_1xRTT Value");
                return setOperationResult(ErrorCodes.SYNCML_DM_UNSUPPORTED_OPERATION);
            }
        }
        return setOperationResult(ErrorCodes.SYNCML_DM_UNSUPPORTED_OPERATION);
    }

    @Override
    public int exec(String path, String args, String correlator) throws RemoteException {
        if (CONNMO_LOGD) Log.i(TAG, "rootPath=" + mRootPath);
        if (CONNMO_LOGD) Log.i(TAG, "path=" + path);
        if (CONNMO_LOGD) Log.i(TAG, "args=" + args);
        if (CONNMO_LOGD) Log.i(TAG, "correlator=" + correlator);
        //mContext.enforceCallingPermission("com.android.permission.WRITE_OMADM_SETTINGS", "Insufficient Permissions");

        if(mTargetQC){
            if (CONNMO_LOGD) Log.d(TAG, "Target device is QC");
            if (path.equals("./ManagedObjects/ConnMO/LTE/APN/3/Setting/Operations/Enable") ||
                    path.equals("./ManagedObjects/ConnMO/LTE/APN/4/Setting/Operations/Enable") ||
                    path.equals("./ManagedObjects/ConnMO/VZW800/APN/5/Setting/Operations/Enable")){

                if (CONNMO_LOGD) Log.d(TAG, "Entered execute for path : " + path);
                DmtData newValue = null;
                newValue = new DmtData(true);
                if(newValue == null){
                    Log.d(TAG,"Unacceptable value");
                    return setOperationResult(ErrorCodes.SYNCML_DM_FAIL);
                }
                return updateValue(path,newValue);
            } else if(path.equals("./ManagedObjects/ConnMO/IPV6Enable/Setting/Operations/Enable")){
                if (CONNMO_LOGD) Log.d(TAG, "Entered execute for path : " + path);
                return setOperationResult(ErrorCodes.SYNCML_DM_UNSUPPORTED_OPERATION);
            } else if(path.equals("./ManagedObjects/ConnMO/IPV6Enable/Setting/Operations/Disable")){
                if (CONNMO_LOGD) Log.d(TAG, "Entered execute for path : " + path);
                return setOperationResult(ErrorCodes.SYNCML_DM_UNSUPPORTED_OPERATION);
            } else if(path.equals("./ManagedObjects/ConnMO/LTE/APN/1/Setting/Operations/Disable") ||
                    //path.equals("./ManagedObjects/ConnMO/LTE/APN/2/Setting/Operations/Disable") ||
                    path.equals("./ManagedObjects/ConnMO/LTE/APN/3/Setting/Operations/Disable") ||
                    path.equals("./ManagedObjects/ConnMO/LTE/APN/4/Setting/Operations/Disable") ||
                    path.equals("./ManagedObjects/ConnMO/VZW800/APN/5/Setting/Operations/Disable")){

                if (CONNMO_LOGD) Log.d(TAG, "Entered execute for path : " + path);
                DmtData newValue = null;
                newValue = new DmtData(false);
                if(newValue == null){
                    Log.d(TAG,"Unacceptable value");
                    return setOperationResult(ErrorCodes.SYNCML_DM_FAIL);
                }
                return updateValue(path,newValue);
            } else if(path.equals("./ManagedObjects/ConnMO/LTE/APN/2/Setting/Operations/Disable")){
                setAPN2DisableValue();
                return setOperationResult(ErrorCodes.SYNCML_DM_SUCCESS);
            }
        }
        return setOperationResult(ErrorCodes.SYNCML_DM_UNSUPPORTED_OPERATION);
    }

    private void setResetBPValue() {
        Log.d(TAG, "Inside setResetBPValue()");
        SharedPreferences p = mContext.getSharedPreferences(RESETBP_PREFERENCE_KEY, 0);
        SharedPreferences.Editor ed = p.edit();
        ed.clear();
        ed.putString(RESET_VALUE_KEY, "yes");
        ed.apply();
    }

    private void setAPN2DisableValue() {
        Log.d(TAG, "Inside setAPN2DisableValue()");
        SharedPreferences p = mContext.getSharedPreferences(ConnmoConstants.APN2_PREFERENCE_NAME, 0);
        SharedPreferences.Editor ed = p.edit();
        ed.clear();
        ed.putString(ConnmoConstants.APN2_DISABLE_KEY, "yes");
        ed.apply();
    }

    @Override
    public Map getNodes(String rootPath) {
        if (CONNMO_LOGD) Log.d(TAG, "Enter ConnmoPlugin::getNodes()");
        Map<String, DmtPluginNode> hMap = new HashMap<String, DmtPluginNode>();
        DmtPluginNode node;

        Map<String, DmtData> nodes = getNodeMap(DmtPathUtils.toRelativePath(CONNMO_PATH, mRootPath));
        for (String connPath : nodes.keySet()) {
            if (connPath.equals(CONNMO_ROOT)) {
                connPath = "";
            }
            if (ConnmoPlugin.CONNMO_LOGD) {
                Log.i(TAG, "put node = '" + connPath + "'");
            }
            node = new DmtPluginNode(connPath, nodes.get(connPath));
            hMap.put(connPath, node);
        }

        if (CONNMO_LOGD) Log.i(TAG, "created the nodes.");
        if (CONNMO_LOGD) Log.d(TAG, "Leave ConnmoPlugin::getNodes()");
        return hMap;
    }

    public Map<String, DmtData> getNodeMap(String rootPath) {

        Map<String, DmtData> nodeMap = new HashMap<String, DmtData>();
        if (rootPath.equals(CONNMO_ROOT)) {
            nodeMap.putAll(mNodeValues);
        } else {
            for (String key : mNodeValues.keySet()) {
                if (key.startsWith(rootPath)) {
                    if ((key.substring(rootPath.length(), (rootPath.length() + 1))).equals("/")) {
                        nodeMap.put(key, mNodeValues.get(key));
                    }
                }
            }
        }
        return nodeMap;
    }

    @Override
    public boolean release() {
        return true;
    }

    private DmtData getValue(String path) throws SQLiteException {
        if (CONNMO_LOGD) Log.d(TAG,"Entered getValue function");
        DmtData value = null;
        String [] splitPath = null;
        String getColumn = null;
        String delimiter = "/";
        Cursor c = null;
        if (CONNMO_LOGD) Log.d(TAG,"CONTENT_URI path in telephonydb is : "+ CONTENT_URI);
        String relativePath = DmtPathUtils.toRelativePath(CONNMO_PATH, path);
        if (CONNMO_LOGD) Log.d(TAG,"relativePath for the node is :" + relativePath);

        try {
            splitPath = relativePath.split(delimiter);
            /*after split it will be like LTE APN 1 Setting Id
              for(int i=0; i<splitPath.length;i++){
                  if (CONNMO_LOGD) Log.d(TAG,"names in splitPath is: " + splitPath[i]);
              } */
            if(splitPath.length > 0){
                if (CONNMO_LOGD) Log.d(TAG,"splitPath.length > 0");
                if("1".equals(splitPath[2])) {
                    mWhere = "name" + "=" + "'" + "Verizon IMS" + "'";
                }
                else if("3".equals(splitPath[2])) {
                    mWhere = "name" + "=" + "'" + "Verizon Internet" + "'";
                }
                else if("2".equals(splitPath[2])) {
                    mWhere = "name" + "=" + "'" + "Verizon FOTA" + "'";
                }
                else if("4".equals(splitPath[2])) {
                    mWhere = "name" + "=" + "'" + "Verizon CBS" + "'";
                }
                else if("5".equals(splitPath[2])) {
                    mWhere = "name" + "=" + "'" + "Verizon 800" + "'";
                }

                if ("Id".equals(splitPath[4])) {
                    getColumn = "class";
                } else if ("Name".equals(splitPath[4])) {
                    getColumn = "apn";
                } else if ("IP".equals(splitPath[4])) {
                    getColumn = "protocol";
                } else if ("Enabled".equals(splitPath[4])) {
                    getColumn = "carrier_enabled";
                }
                if (ConnmoPlugin.CONNMO_LOGD) {
                    Log.d(TAG, "column we are trying to get is: " + getColumn);
                }

                c = mContext.getContentResolver()
                        .query(CONTENT_URI, new String[]{getColumn}, mWhere, null, null);
                if (ConnmoPlugin.CONNMO_LOGD) {
                    Log.i(TAG, "c value is :" + c + " getcount is = " + c.getCount());
                }
                if ((c != null) && (c.getCount() > 0)) {
                    c.moveToFirst();
                    value = new DmtData(c.getString(0));
                    if ("carrier_enabled".equals(getColumn)) {
                        if (ConnmoPlugin.CONNMO_LOGD) {
                            Log.d(TAG, "enabled value is :" + c.getString(0));
                        }
                        if ("1".equals(c.getString(0))) {
                            value = new DmtData(true);
                        } else if ("0".equals(c.getString(0))) {
                            value = new DmtData(false);
                        }
                    }
                    if (ConnmoPlugin.CONNMO_LOGD) {
                        Log.d(TAG, getColumn + "from telephonydb is :" + value);
                    }
                }
            }
        } catch (Exception e) {
            if (ConnmoPlugin.CONNMO_LOGD) {
                Log.d(TAG, "Exception occurred");
            }
            e.printStackTrace();
            value = new DmtData("Value Unknown");
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return value;
    }

    private int updateValue(String path, DmtData newValue) {
        if (ConnmoPlugin.CONNMO_LOGD) Log.d(TAG, "Entered updateValue function");
        String delimiter = "/";
        int result = ErrorCodes.SYNCML_DM_FAIL;
        ContentValues values = new ContentValues();
        if (ConnmoPlugin.CONNMO_LOGD) Log.d(TAG, "CONTENT_URI path in telephonydb is : "
                + CONTENT_URI);
        String relativePath = DmtPathUtils.toRelativePath(CONNMO_PATH, path);
        if (ConnmoPlugin.CONNMO_LOGD) Log.d(TAG, "relativePath for the node is :" + relativePath);

        String[] splitPath;
        try {
            splitPath = relativePath.split(delimiter);
            /*after split it will be like LTE APN 1 Setting Id */
            for (String name : splitPath) {
                if (ConnmoPlugin.CONNMO_LOGD) {
                    Log.d(TAG, "names in splitPath is: " + name);
                }
            }
            if (splitPath.length > 0) {
                if (ConnmoPlugin.CONNMO_LOGD) Log.d(TAG,"Inside splitPath.length > 0");
                if("1".equals(splitPath[2])) {
                    mWhere = "name" + "=" + "'" + "Verizon IMS" + "'";
                }
                else if("3".equals(splitPath[2])) {
                    mWhere = "name" + "=" + "'" + "Verizon Internet" + "'";
                }
                else if("2".equals(splitPath[2])) {
                    mWhere = "name" + "=" + "'" + "Verizon FOTA" + "'";
                }
                else if("4".equals(splitPath[2])) {
                    mWhere = "name" + "=" + "'" + "Verizon CBS" + "'";
                }
                else if("5".equals(splitPath[2])) {
                    mWhere = "name" + "=" + "'" + "Verizon 800" + "'";
                }

                if ("Name".equals(splitPath[4])) {
                    if (!newValue.getString().isEmpty()) {
                        values.put("apn", newValue.getString());
                        if (ConnmoPlugin.CONNMO_LOGD) {
                            Log.d(TAG, "column we are trying to update is :" + splitPath[4]);
                        }
                    } else {
                        Log.d(TAG, "Null value not accepted");
                        return ErrorCodes.SYNCML_DM_FAIL;
                    }
                    //return ErrorCodes.SYNCML_DM_COMMAND_NOT_ALLOWED;;

                } else if ("IP".equals(splitPath[4])) {
                    values.put("protocol", parseIP(newValue.getString()));
                    if (ConnmoPlugin.CONNMO_LOGD) {
                        Log.d(TAG, "column we are trying to update is: " + splitPath[4]);
                    }
                }
                /*else if("Enabled".equals(splitPath[4])){
                                          values.put("enabled", newValue.getBoolean());
                                  }*/
                else if ("Enable".equals(splitPath[5])) {
                    values.put("carrier_enabled", newValue.getBoolean());
                    if (ConnmoPlugin.CONNMO_LOGD) {
                        Log.d(TAG, "column we are trying to update is: " + splitPath[5]);
                    }
                } else if ("Disable".equals(splitPath[5])) {
                    values.put("carrier_enabled", newValue.getBoolean());
                    if (ConnmoPlugin.CONNMO_LOGD) {
                        Log.d(TAG, "column we are trying to update is: " + splitPath[5]);
                    }
                }
                result = mContext.getContentResolver().update(CONTENT_URI, values, mWhere, null);
                if (result > 0) {
                    result = ErrorCodes.SYNCML_DM_SUCCESS;
                }
            }
        } catch (DmtException e) {
            if (ConnmoPlugin.CONNMO_LOGD) {
                Log.d(TAG, "Exception occurred");
            }
            e.printStackTrace();
        } catch (SQLiteException e) {
            if (ConnmoPlugin.CONNMO_LOGD) {
                Log.d(TAG, "SQLiteException occurred");
            }
            e.printStackTrace();
        }
        return result;
    }

    private int updateVZWInternetIP(String newValue, int bearerValue) {
        if (ConnmoPlugin.CONNMO_LOGD) {
            Log.d(TAG, "Entered updateIPValue function");
        }
        int result = ErrorCodes.SYNCML_DM_FAIL;
        ContentValues values = new ContentValues();
        if (ConnmoPlugin.CONNMO_LOGD) {
            Log.d(TAG, "CONTENT_URI path in telephonydb is : " + CONTENT_URI);
        }
        try {
            mWhere = "name='Verizon Internet' and bearer='" + bearerValue + '\'';
            values.put("protocol", newValue);
            if (ConnmoPlugin.CONNMO_LOGD) {
                Log.d(TAG, "column we are trying to update is VZW Internet IP");
            }
            result = mContext.getContentResolver().update(CONTENT_URI, values, mWhere, null);
            if (result > 0) {
                result = ErrorCodes.SYNCML_DM_SUCCESS;
            }
        } catch (SQLiteException e) {
            if (ConnmoPlugin.CONNMO_LOGD) {
                Log.d(TAG, "SQLiteException thrown", e);
            }
        } catch (Exception e) {
            if (ConnmoPlugin.CONNMO_LOGD) {
                Log.d(TAG, "Exception thrown", e);
            }
        }
        return result;
    }

    private String getVZWInternetIP(int bearerValue) {
        if (ConnmoPlugin.CONNMO_LOGD) {
            Log.d(TAG, "Entered getVZWInternetIP function");
        }
        String value = null;
        String getColumn = null;
        Cursor c = null;
        ContentValues values = new ContentValues();
        if (ConnmoPlugin.CONNMO_LOGD) {
            Log.d(TAG, "CONTENT_URI path in telephonydb is : " + CONTENT_URI);
        }
        try {
            mWhere = "name='Verizon Internet' and bearer='" + bearerValue + '\'';
            getColumn = "protocol";
            if (ConnmoPlugin.CONNMO_LOGD) {
                Log.d(TAG, "column we are trying to update is VZW Internet IP");
            }
            c = mContext.getContentResolver()
                    .query(CONTENT_URI, new String[]{getColumn}, mWhere, null, null);
            if (ConnmoPlugin.CONNMO_LOGD) {
                Log.i(TAG, "c value is :" + c + " getcount is = " + c.getCount());
            }
            if ((c != null) && (c.getCount() > 0)) {
                c.moveToFirst();
                value = c.getString(0);
            }
        } catch (SQLiteException e) {
            if (ConnmoPlugin.CONNMO_LOGD) {
                Log.d(TAG, "SQLiteException thrown", e);
            }
        } catch (Exception e) {
            if (ConnmoPlugin.CONNMO_LOGD) {
                Log.d(TAG, "Exception thrown", e);
            }
        }
        return value;
    }

    private boolean isPhoneTypeLTE() {
        return DMSettingsHelper.isPhoneTypeLTE();
    }

    private String parseIP(String rawIpValue) {
        if (CONNMO_LOGD) Log.d(TAG, "Inside parseIP");
        String mapIPValue = "";
        if (!TextUtils.isEmpty(rawIpValue)) {
            String ipvalue = rawIpValue.replaceAll("\\s","");
            if (CONNMO_LOGD) Log.d(TAG, "Trimed ipvalue : " + ipvalue);
            if (ipvalue.equalsIgnoreCase("IPV4")){
                mapIPValue = "IPV4";
            } else if (ipvalue.equalsIgnoreCase("IPV6")) {
                mapIPValue = "IPV6";
            } else if (ipvalue.equalsIgnoreCase("IPV4V6")
                    || ipvalue.equalsIgnoreCase("IPV6V4")
                    || ipvalue.equalsIgnoreCase("IPV4ANDIPV6")
                    || ipvalue.equalsIgnoreCase("IPV6ANDIPV4")) {
                mapIPValue = "IPV4V6";
            }
        }
        return mapIPValue;
    }
}
