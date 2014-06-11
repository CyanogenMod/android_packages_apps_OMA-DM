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

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.SocketAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

public class DMHttpConnector {
    private static final String TAG = "DMHttpConnector";
    private static final boolean DBG = DMClientService.DBG;

    private HttpURLConnection mConnection;

    private boolean mIsFotaChannelAvailable;

    private static final String USER_AGENT = "User-Agent";

    private static final String CACHE_CONTROL = "Cache-Control";

    private static final String ACCEPT = "Accept";

    private static final String ACCEPT_LANGUAGE = "Accept-Language";

    private static final String ACCEPT_CHARSET = "Accept-Charset";

    private static final String CONTENT_TYPE = "Content-Type";

    public static final String CONTENT_LENGTH = "Content-Length";

    private static final String X_SYNCML_HMAC = "x-syncml-hmac";

    // Sprint DM-Sess-29: User-Agent: <make>/<model> <DM-vendor>/<DM-version>
    private static final String ANDROID_OMA_DM_CLIENT = "Google/Nexus Google/1";

    private static final String LANGUAGE_EN = "en";

    private static final String CHARSET_UTF8 = "utf-8";

    private static final String MIME_TYPE_SYNCML_DM = "application/vnd.syncml.dm";

    private static final String MIME_TYPE_SYNCML_DM_WBXML = MIME_TYPE_SYNCML_DM + "+wbxml";

    //private static final String MIME_TYPE_SYNCML_DM_XML = MIME_TYPE_SYNCML_DM + "+xml";

    private static final String CACHE_CONTROL_PRIVATE = "private";

    private String mContentType;

    private final DMSession mSession;

    private final DMClientService mContext;

    private Proxy mProxy;

    public DMHttpConnector(DMSession session) {
        mSession = session;
        mContext = session.getServiceContext();
        setHostProxy();
    }

    /**
     * Enable an APN by name.
     * Called from JNI code.
     *
     * @param apnName
     */
    public void enableApnByName(String apnName) {
        /* Apn setting DB is defined in
         * frameworks/base/core/java/android/provider/Telephony.java.
         * In ApnSetting DB, New DM apn item is always let Apn type equal to Apn name.
         */
        String apnType = apnName;

        if (DBG) logd("Enable Apn name=" + apnName + " type=" + apnType);

        //ConnectivityManager cm = (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        //invoke chain:
        //1)ConnectivityManager ==> MobileDataStateTracker.startUsingNetworkFeature()
        //2)startUsingNetworkFeature()==>PhoneService.enableApnType
        //cm.startUsingNetworkFeature(ConnectivityManager.TYPE_MOBILE, mApnType);
    }

    /**
     * Send an HTTP request.
     * Called from JNI code.
     *
     * @param url the URL to request
     * @param requestData the SyncML package to send
     * @param hmacValue the HMAC value to send as a request header
     * @return
     */
    public int sendRequest(String url, byte[] requestData, String hmacValue) {
        if (!mIsFotaChannelAvailable) {
            if (isDataNetworkLteOrCdma() && !isWifiConnected() && isPhoneTypeLTE()) {
                requestRouteAvailable(url);
                mIsFotaChannelAvailable = true;
            } else {
                logd("NW type is not LTE/eHRPD or not a 4G device");
            }
        } else {
            requestRouteAvailable(url);
            logd("fota data channel already enabled");
        }

        if (mContentType == null) {
            mContentType = MIME_TYPE_SYNCML_DM_WBXML;
        }
        return executePOST(url, requestData, hmacValue);
    }

    private boolean isWifiConnected() {
        Log.d(TAG, "Inside isWifiConnected");
        ConnectivityManager cm = (ConnectivityManager) mContext
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        if (cm.getActiveNetworkInfo() == null) {
            return false;
        }

        // return true only when WiFi is connected
        return cm.getActiveNetworkInfo().isConnected() &&
                (cm.getActiveNetworkInfo().getType() == ConnectivityManager.TYPE_WIFI);

        /*we have one more way to find out , we can use either of them
        if (cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected()) {
            return true;
        }*/
    }

    private static boolean isPhoneTypeLTE() {
        return DMSettingsHelper.isPhoneTypeLTE();
    }

    private boolean requestRouteAvailable(String url) {
        ConnectivityManager mConnMgr = (ConnectivityManager) mContext
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        SharedPreferences p = mContext.getSharedPreferences(DMHelper.FOTA_APN_PREFERENCE_KEY, 0);
        String apnInterfaceName = p.getString(DMHelper.APN_INTERFACE_NAME_KEY, null);
        Log.d(TAG, "Interface Name = " + apnInterfaceName);
        Uri uri = Uri.parse(url);
        Log.d(TAG, "uri.getHost = " + uri.getHost());

        InetAddress ipAddr;
        try {
            ipAddr = InetAddress.getByName(uri.getHost());
            Log.d(TAG, "ipAddr = " + ipAddr);
            Log.d(TAG, "getHostAddress, ipAddr = " + ipAddr.getHostAddress());
        } catch (UnknownHostException e) {
            Log.d(TAG, "requestRouteAvailable: unknown host", e);
            ipAddr = null;
        }

        boolean result = false;
        if (ipAddr != null) {
            result = mConnMgr
                    .requestRouteToHostAddress(ConnectivityManager.TYPE_MOBILE_FOTA, ipAddr);
        } else {
            Log.d(TAG, "requestRouteToHostAddress = " + result);
        }
        Log.d(TAG, "requestRouteToHostAddress = " + result);
        return result;
    }

    private boolean isDataNetworkLteOrCdma() {
        TelephonyManager tm = (TelephonyManager) mContext
                .getSystemService(Context.TELEPHONY_SERVICE);

        int networkType = tm.getDataNetworkType();

        switch (networkType) {
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_1xRTT:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
            case TelephonyManager.NETWORK_TYPE_EHRPD:
            case TelephonyManager.NETWORK_TYPE_LTE:
                if (DBG) logd("NW type is LTE or CDMA: " + networkType);
                return true;

            default:
                if (DBG) logd("NW type neither LTE nor CDMA: " + networkType);
                return false;
        }
    }

    private int executePOST(String urlString, byte[] requestData, String hmacValue) {
        if (DBG) logd("Post url=" + urlString + " HMAC value=" + hmacValue);

        if (urlString.isEmpty()) {
            return DMResult.SYNCML_DM_INVALID_URI;
        }

        HttpURLConnection connection = mConnection;
        URL url;
        try {
            if (connection != null) {
                Log.e(TAG, "overwriting old mConnection!");
                mConnection.disconnect();
            }

            url = new URL(urlString);
            if (url.getHost().contains("sprint")) {
                String serverUrl = DMHelper.getServerUrl(mContext);
                if (!TextUtils.isEmpty(serverUrl)) {
                    if (DBG) logd("replacing URL with Sprint URL: " + serverUrl);
                    url = new URL(serverUrl);
                    if (DBG) logd("new URL is " + url);
                }
            }

            if (mProxy != null) {
                if (DBG) logd("opening connection with proxy: " + mProxy);
                connection = (HttpURLConnection) url.openConnection(mProxy);
            } else {
                if (DBG) logd("opening direct connection");
                connection = (HttpURLConnection) url.openConnection();
            }
            mConnection = connection;

        } catch (Exception e) {
            Log.e(TAG, "bad URL", e);
            return DMResult.SYNCML_DM_INVALID_URI;
        }

        try {
            connection.setRequestMethod("POST");
            connection.addRequestProperty(ACCEPT, MIME_TYPE_SYNCML_DM_WBXML);
            connection.addRequestProperty(ACCEPT_LANGUAGE, LANGUAGE_EN);
            connection.addRequestProperty(ACCEPT_CHARSET, CHARSET_UTF8);
            connection.addRequestProperty(USER_AGENT, ANDROID_OMA_DM_CLIENT);
            connection.addRequestProperty(CACHE_CONTROL, CACHE_CONTROL_PRIVATE);
            connection.addRequestProperty(CONTENT_TYPE, mContentType);
            connection.addRequestProperty("Connection", "Close");
            if (!TextUtils.isEmpty(hmacValue)) {
                connection.addRequestProperty(X_SYNCML_HMAC, hmacValue);
            }
        } catch (ProtocolException e) {
            Log.e(TAG, "error setting headers", e);
            return DMResult.SYNCML_DM_IO_FAILURE;
        }

        // Log outgoing headers and content
        HttpLog log = new HttpLog(mContext, mSession.getLogFileName());
        log.logHeaders(connection.getRequestProperties());
        log.logContent(mContentType, requestData);
        log.closeLogFile();

        try {
            // Send request data
            OutputStream stream = connection.getOutputStream();
            stream.write(requestData);
            stream.flush();

            int retcode = connection.getResponseCode();
            if (DBG) Log.d(TAG, urlString + " code: " + retcode + " status: "
                    + connection.getResponseMessage());
            return retcode;
        } catch (UnknownHostException ignored) {
            Log.e(TAG, url + " - Unknown host exception");
            return DMResult.SYNCML_DM_UNKNOWN_HOST;
        } catch (IOException e) {
            Log.e(TAG, url + " - IOException error: ", e);
            return DMResult.SYNCML_DM_SOCKET_CONNECT_ERR;
        }
    }

    /**
     * Get the response length.
     * Called from JNI code.
     *
     * @return
     */
    long getResponseLength() {
        if (mConnection != null) {
            return mConnection.getContentLength();
        }
        return -1;
    }

    /**
     * Get the response data.
     * Called from JNI code.
     *
     * @return
     */
    public byte[] getResponseData() {
        if (mConnection == null) {
            return null;
        }

        int dataSize = (int) getResponseLength();
        if (dataSize <= 0) {
            return null;
        }

        byte[] data = new byte[dataSize];
        if (DBG) Log.d(TAG, "response dataSize=" + dataSize);

        String contentType = mConnection.getContentType();
        if (contentType == null) {
            loge("getResponseData: contentType is null");
            return null;
        }

        if (DBG) Log.d(TAG, "content type = " + contentType);

        try {
            InputStream resInput = mConnection.getInputStream();
            if (resInput != null) {
                if (DBG) logd("inputstream type = " + resInput.getClass().getName());
                int readTotal = 0;
                synchronized (this) {
                    int read;
                    while ((read = resInput.read(data, readTotal, dataSize - readTotal)) != -1) {
                        readTotal += read;
                    }
                }
                if (DBG) Log.d(TAG, "InputStream read len = " + readTotal);
            }
        } catch (IOException e) {
            loge("IOException reading response", e);
        }

        // log incoming headers and content
        HttpLog log = new HttpLog(mContext, mSession.getLogFileName());
        log.logHeaders(mConnection.getHeaderFields());
        log.logContent(contentType, data);
        log.closeLogFile();

        return data;
    }

    /**
     * Get the response header.
     * Called from JNI code.
     *
     * @param fieldName
     * @return
     */
    public String getResponseHeader(String fieldName) {
        if (mConnection != null) {
            return mConnection.getHeaderField(fieldName);
        }
        return null;
    }

    /**
     * Set the content type.
     * Called from JNI code.
     *
     * @param type
     */
    public void setContentType(String type) {
        mContentType = type;
    }

    public void cancelSession() {
        Log.e(TAG, "Abort the session");
        if (mConnection != null) {
            mConnection.disconnect();
        }
    }

    public int closeSession() {
        if (mConnection != null) {
            mConnection.disconnect();
        }

        //if(mApnType!=null){
        //	ConnectivityManager cm = (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        //	cm.stopUsingNetworkFeature(ConnectivityManager.TYPE_MOBILE, mApnType);
        //}
        return 1;
    }

    private void setHostProxy() {
        String hostname = DMHelper.getProxyHostname(mContext);
        if (TextUtils.isEmpty(hostname)) {
            loge("Using default proxy hostname!!");
            hostname = "oma.ssprov.sprint.com";
        }

        SocketAddress sa = InetSocketAddress.createUnresolved(hostname, 80);
        if (DBG) Log.d(TAG, "unresolved socket address created");

        mProxy = new Proxy(Proxy.Type.HTTP, sa);
        if (DBG) Log.d(TAG, "Set Proxy: " + mProxy);
    }

    static final class HttpLog {

        final int mLogLevel;

        FileOutputStream mOut;

        public HttpLog(DMClientService clientService, String logFileName) {
            // TODO: make into a property or preference
            mLogLevel = clientService.getConfigDB().getSyncMLLogLevel();
            try {
                if (mLogLevel > 0) {
                    mOut = new FileOutputStream(logFileName, true);
                    Log.d(TAG, "XXXXX creating log file " + logFileName + " XXXXX");
                }
            } catch (Exception ex) {
                Log.e(TAG, "Exception opening syncml log file=" + logFileName, ex);
            }
        }

        public void logHeaders(Map<String, List<String>> headers) {
            FileOutputStream out = mOut;
            if (out == null) {
                return;
            }
            StringBuilder builder = new StringBuilder(256);
            for (Map.Entry<String, List<String>> header : headers.entrySet()) {
                for (String value : header.getValue()) {
                    builder.append(header.getKey()).append(':').append(value).append("\r\n");
                }
            }
            try {
                out.write(builder.toString().getBytes());
            } catch (IOException ex) {
                Log.e(TAG, "Exception writing syncml headers log", ex);
            }
        }

        public void logContent(String contentType, byte[] body) {
            FileOutputStream out = mOut;
            if (out == null) {
                return;
            }
            try {
                out.write("===================================".getBytes());
                if (body == null || body.length == 0) {
                    out.write("empty body".getBytes());
                    return;
                }
                byte[] xml = null;
                if (contentType.equalsIgnoreCase(MIME_TYPE_SYNCML_DM_WBXML)) {
                    if (mLogLevel == 2) {
                        xml = NativeDM.nativeWbxmlToXml(body);
                        if (xml != null) {
                            out.write(xml);
                        }
                    }
                }
                if (xml == null) {
                    out.write(body);
                }
                out.write("===================================\n".getBytes());
            } catch (Exception ex) {  // catch all in case JNI throws exception
                Log.e(TAG, "Exception writing syncml content log", ex);
            }

        }

        public void closeLogFile() {
            FileOutputStream out = mOut;
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ex) {
                    Log.e(TAG, "Exception writing syncml headers log", ex);
                }
                mOut = null;
            }
        }
    }

    private static void logd(String msg) {
        Log.d(TAG, msg);
    }

    private static void loge(String msg) {
        Log.e(TAG, msg);
    }

    private static void loge(String msg, Throwable tr) {
        Log.e(TAG, msg, tr);
    }
}
