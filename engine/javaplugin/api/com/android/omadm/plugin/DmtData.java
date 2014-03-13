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

package com.android.omadm.plugin;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * DmtData encapsulates various DMT node data formats.
 *
 * <pre>
 * Data formats includes:
 *     NULL
 *     String
 *     Integer
 *     Boolean
 *     Binary
 *     Date
 *     Time
 *     Float
 *     NODE
 * </pre>
 */
public class DmtData implements Parcelable {
    private static final String TAG = "DmtData";

    /**
     * NULL Data type.
     */
    public static final int NULL = 0;

    /**
     * Undefined.
     */
    public static final int UNDEFINED = NULL;

    /**
     * String Data
     */
    public static final int STRING = 1;

    /**
     * Integer Data
     */
    public static final int INT = 2;

    /**
     * Boolean Data
     */
    public static final int BOOL = 3;

    /**
     * Binary Data
     */
    public static final int BIN = 4;

    /**
     * Date data
     */
    public static final int DATE = 5;

    /**
     * Time data
     */
    public static final int TIME = 6;

    /**
     * Float data
     */
    public static final int FLOAT = 7;

    /**
     * Interior Node
     */
    public static final int NODE = 8;

    /*
     * Used to create a DmtData((String)null)
     */
    public static final String NULLVALUE = null;

    private int type;

    private String stringValue;

    private int intValue;

    private boolean boolValue;

    private byte[] binValue;

    private String dateValue;

    private String timeValue;

    private float floatValue;

    private ArrayList<String> nodeValue; // Child Node Names

    /**
     * Data represent a default value, it is only used for setting default value
     * to a node. if the node does not have a default value, a null value is
     * used instead.
     */
    public DmtData() {
    }

    public DmtData(String str, int dataType) {
        init(str, dataType);
    }

    private void init(String str, int dataType) {
        type = dataType;
        if (str == null) {
            return;
        }
        switch (dataType) {
            case NULL:
                break;
            case STRING:
                stringValue = str;
                break;
            case INT:
                try {
                    intValue = Integer.parseInt(str);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "can't parse init value as integer", e);
                }
                break;
            case BOOL:
                boolValue = Boolean.parseBoolean(str);
                break;
            case BIN:
                binValue = str.getBytes();
                break;
            case DATE:
                dateValue = str;
                break;
            case TIME:
                timeValue = str;
                break;
            case FLOAT:
                try {
                    floatValue = Float.parseFloat(str);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "can't parse init value as float", e);
                }
                break;
            case NODE:
                String[] strArray = str.split("\\|");
                int cnt = strArray.length;
                if (cnt > 0 && !strArray[0].isEmpty()) {
                    nodeValue = new ArrayList<String>();
                    Collections.addAll(nodeValue, strArray);
                }
                break;
            default:
                type = UNDEFINED;
                break;
        }
    }

    /**
     * Data represent an String type.
     *
     * @param str String type data. The size may be restricted by MDF.
     */
    public DmtData(String str) {
        stringValue = str;
        type = STRING;
    }

    /**
     * Data represent an integer type Data range may be restricted by MDF
     */
    public DmtData(int integer) {
        intValue = integer;
        type = INT;
    }

    /**
     * Data represent a boolean type
     */
    public DmtData(boolean bool) {
        boolValue = bool;
        type = BOOL;
    }

    /**
     * Data represent a binary type Data size may be restricted by MDF. Data may
     * be null.
     */
    public DmtData(byte[] bin) {
        binValue = bin;
        type = BIN;
    }

    public DmtData(ArrayList<String> value) {
        nodeValue = value;
        type = NODE;
    }

    public DmtData(float value) {
        floatValue = value;
        type = FLOAT;
    }

    @Override
    public String toString() {
        return getString();
    }

    /**
     * Get string representation of the value. It will automatically convert to
     * string value from other types. for NULL value, return empty String. for
     * Default value, return null.
     */
    public String getString() {
        switch (type) {
            case NULL:
                return "";
            case STRING:
                return stringValue;
            case INT:
                return String.valueOf(intValue);
            case BOOL:
                return String.valueOf(boolValue);
            case BIN:
                return (binValue == null) ? null : (new String(binValue));
            case DATE:
                return dateValue;
            case TIME:
                return timeValue;
            case FLOAT:
                return String.valueOf(floatValue);
            case NODE:
                StringBuffer tmpValue = new StringBuffer("");
                try {
                    if (nodeValue == null || nodeValue.isEmpty()) {
                        return tmpValue.toString();
                    }
                    for (String node : nodeValue) {
                        tmpValue.append(node).append('|');
                    }
                    tmpValue.deleteCharAt(tmpValue.length() - 1);
                } catch (Exception e) {
                    Log.e(TAG, "getString() failed for node object", e);
                    tmpValue = new StringBuffer("");
                }
                return tmpValue.toString();
            default:
                return null;
        }
    }

    /**
     * Get Boolean value.
     *
     * @return boolean value.
     */
    public boolean getBoolean() throws DmtException {
        if (type != BOOL) {
            throw new DmtException(ErrorCodes.SYNCML_DM_INVALID_PARAMETER,
                    "The value requested is not boolean");
        }
        return boolValue;
    }

    /**
     * Get Integer value.
     *
     * @return integer value
     */
    public int getInt() throws DmtException {
        if (type != INT) {
            throw new DmtException(ErrorCodes.SYNCML_DM_INVALID_PARAMETER,
                    "The value requested is not integer");
        }
        return intValue;
    }

    /**
     * Get binary value.
     *
     * @return binary value in byte[].
     */
    public byte[] getBinary() throws DmtException {
        if (type != BIN) {
            throw new DmtException(ErrorCodes.SYNCML_DM_INVALID_PARAMETER,
                    "The value requested is not binary");
        }
        return binValue;
    }

    public ArrayList<String> getNodeValue() throws DmtException {
        // if (type != NODE)
        // {
        // throw new DmtException(ErrorCodes.SYNCML_DM_INVALID_PARAMETER,
        // "The value requested is not interior node");
        // }
        return nodeValue;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof DmtData)) {
            return false;
        }
        DmtData data = (DmtData) obj;

        if (getType() != data.getType()) {
            return false;
        }

        try {
            switch (getType()) {
                case NULL:
                    return true;

                case DATE:
                case TIME:
                case FLOAT:
                case STRING:
                case NODE:
                    String str1 = getString();
                    String str2 = data.getString();
                    return TextUtils.equals(str1, str2);

                case INT:
                    return getInt() == data.getInt();

                case BOOL:
                    return getBoolean() == data.getBoolean();

                case BIN:
                    byte[] bytes1 = getBinary();
                    byte[] bytes2 = data.getBinary();
                    return Arrays.equals(bytes1, bytes2);

                default:
                    return false;
            }
        } catch (DmtException e) {
            return false;
        }
    }

    /**
     * Return the Type associated with the data This information is to be used
     * by the persistence layer. Persistence does NOT need to use meta info to
     * get the data
     */
    public int getType() {
        return type;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(type);
        out.writeString(getString());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<DmtData> CREATOR = new Creator<DmtData>() {
        @Override
        public DmtData createFromParcel(Parcel in) {
            return new DmtData(in);
        }

        @Override
        public DmtData[] newArray(int size) {
            return new DmtData[size];
        }
    };

    DmtData(Parcel in) {
        type = in.readInt();
        String tmpValue = in.readString();
        if (type <= NULL || type > NODE) {
            if (type != NULL) {
                type = UNDEFINED;
            }
            return;
        }
        init(tmpValue, type);
    }
}
