#ifdef PLATFORM_ANDROID

#include "dm_tpt_connection.H"
#include "DMServiceMain.h"
#include <android_runtime/AndroidRuntime.h>

SYNCML_DM_OTAConnection::SYNCML_DM_OTAConnection()
{
    m_maxAcptSize = 0;
    m_szURL = "";

    JNIEnv* jEnv = NULL;
    if (android::AndroidRuntime::getJavaVM()) {
        jEnv = android::AndroidRuntime::getJNIEnv();
        m_jNetConnObj = getNetConnector();
    } else {
        return;
    }

    jclass jNetConnCls = jEnv->GetObjectClass(m_jNetConnObj);
    if (jNetConnCls == NULL) {
        LOGD(("FindClass return Error"));
        goto end;
    }

    m_jSendRequest = jEnv->GetMethodID(jNetConnCls, "sendRequest",
            "(Ljava/lang/String;[BLjava/lang/String;)I");
    if (m_jSendRequest == NULL) {
        LOGD(("GetMethod 'sendRequest' return Error"));
        goto end;
    }

    m_jGetRespLength = jEnv->GetMethodID(jNetConnCls, "getResponseLength", "()J");
    if (m_jGetRespLength == NULL) {
        LOGD(("GetMethod 'getResponseLength' return Error"));
        goto end;
    }

    m_jGetRespData = jEnv->GetMethodID(jNetConnCls, "getResponseData", "()[B");
    if (m_jGetRespData == NULL) {
        LOGD(("GetMethod 'getResponseData' return Error"));
        goto end;
    }

    m_jSetContentType = jEnv->GetMethodID(jNetConnCls, "setContentType", "(Ljava/lang/String;)V");
    if (m_jSetContentType == NULL) {
        LOGD(("GetMethod 'setContentType' return Error"));
        goto end;
    }

    m_jEnbleApnByName = jEnv->GetMethodID(jNetConnCls, "enableApnByName", "(Ljava/lang/String;)V");
    if (m_jEnbleApnByName == NULL) {
        LOGD(("GetMethod 'enableApnByName' return Error"));
        goto end;
    }

    LOGD("constructed successfully");
end:
    return;
}

SYNCML_DM_OTAConnection::~SYNCML_DM_OTAConnection()
{
    LOGD("~");
}

SYNCML_DM_RET_STATUS_T SYNCML_DM_OTAConnection::Init(UINT32 dwMaxAcptSize,
        XPL_ADDR_TYPE_T AddressType,
        CPCHAR ConRef)
{
    LOGD("dwMaxAcptSize=%d, AddressType=%d\n", dwMaxAcptSize, AddressType);

    if (ConRef) {
        LOGD("ConRef=%s\n", ConRef);
        JNIEnv* jEnv = android::AndroidRuntime::getJNIEnv();
        jstring jConRef = jEnv->NewStringUTF((const char*)ConRef);
        jEnv->CallVoidMethod(m_jNetConnObj, m_jEnbleApnByName, jConRef);
    }

    m_maxAcptSize = dwMaxAcptSize;

    return SYNCML_DM_SUCCESS;
}

SYNCML_DM_RET_STATUS_T SYNCML_DM_OTAConnection::Send(
        const SYNCML_DM_INDIRECT_BUFFER_T *psSendSyncMLDocument,
        SYNCML_DM_INDIRECT_BUFFER_T *psRecvSyncMLDocument,
        const UINT8 *pbContType,
        const DMCredHeaders * psCredHdr)
{
    LOGD("Send=%d", psSendSyncMLDocument->dataSize);

    if ((psSendSyncMLDocument == NULL) ||
            (psSendSyncMLDocument->pData == NULL) ||
            (psSendSyncMLDocument->dataSize == 0 ))
    {
        return SYNCML_DM_FAIL;
    }

    if ((psRecvSyncMLDocument == NULL) ||
            (psRecvSyncMLDocument->pData == NULL))
    {
        return SYNCML_DM_FAIL;
    }

    if ((pbContType == NULL) || (pbContType[0] == '\0'))
    {
        return SYNCML_DM_FAIL;
    }

    // Check whether psCredHdr is valid
    if ( psCredHdr->isCorrect() == FALSE )
        return SYNCML_DM_FAIL;

    JNIEnv* jEnv = android::AndroidRuntime::getJNIEnv();

    jstring jContentType = jEnv->NewStringUTF((const char*)pbContType);
    jEnv->CallVoidMethod(m_jNetConnObj, m_jSetContentType, jContentType);

    CPCHAR strUrl = m_szURL.GetBuffer();
    LOGD("url=%s", strUrl);
    jstring jurl = jEnv->NewStringUTF(strUrl);
    jbyteArray jDataArray = jEnv->NewByteArray(psSendSyncMLDocument->dataSize);
    jEnv->SetByteArrayRegion(jDataArray,
            0, psSendSyncMLDocument->dataSize,
            (const jbyte*)psSendSyncMLDocument->pData);

    jint jResult;
    int wNumRetries = 0;

    jstring jstrMac = NULL;
    if (psCredHdr->empty() == FALSE) {
        DMString strHMAC = "";
        strHMAC+=("algorithm=MD5,username=\"");
        strHMAC+=((CPCHAR)psCredHdr->m_oUserName.getBuffer());
        strHMAC+=("\",mac=");
        strHMAC+=((CPCHAR)psCredHdr->m_oMac.getBuffer());
        LOGD("mac length:%d\n", psCredHdr->m_oMac.getSize());
        //LOGD("mac value in hex:%x\n", psCredHdr->m_oMac.getBuffer());
        LOGD("hmac value =%s\n", strHMAC.GetBuffer());
        jstrMac = jEnv->NewStringUTF(strHMAC.GetBuffer());
    }

    while (wNumRetries < DMTPT_MAX_RETRIES) {
        jResult = jEnv->CallIntMethod(m_jNetConnObj, m_jSendRequest, jurl, jDataArray, jstrMac /*hmac*/);
        LOGD("Send result=%d", jResult);

        // retry for timeout or general connection errors
        if(jResult == SYNCML_DM_SOCKET_TIMEOUT || jResult == SYNCML_DM_SOCKET_CONNECT_ERR || jResult == SYNCML_DM_NO_HTTP_RESPONSE
           || jResult == SYNCML_DM_REQUEST_TIMEOUT || jResult == SYNCML_DM_INTERRUPTED
           || jResult == SYNCML_DM_SERVICE_UNAVAILABLE || jResult == SYNCML_DM_GATEWAY_TIMEOUT ){
            wNumRetries++;
            sleep(10);  // sleep a little bit before trying again
            continue;
        }

        if (jResult == 200) {
            jlong jResponseLen = jEnv->CallLongMethod(m_jNetConnObj, m_jGetRespLength);
            LOGD("response length=%lld", jResponseLen);
            if(jResponseLen > 0 && jResponseLen <= m_maxAcptSize){
                jbyteArray jData = (jbyteArray)jEnv->CallObjectMethod(m_jNetConnObj, m_jGetRespData);
                jEnv->GetByteArrayRegion(jData, 0, jResponseLen, (jbyte*)psRecvSyncMLDocument->pData);
                psRecvSyncMLDocument->dataSize = jResponseLen;
                //Get header:x-syncml-hmac
                m_pCredHeaders = (DMCredHeaders*)psCredHdr;
                m_pCredHeaders->clear();
                jstring jstrHMAC = jEnv->NewStringUTF("x-syncml-hmac");
                jobject jobjHMACValue = NULL;
                jclass jNetConnCls = jEnv->GetObjectClass(m_jNetConnObj);
                jmethodID jmethodGetHeader = jEnv->GetMethodID(jNetConnCls, "getResponseHeader","(Ljava/lang/String;)Ljava/lang/String;");
                jobjHMACValue = jEnv->CallObjectMethod(m_jNetConnObj, jmethodGetHeader, jstrHMAC);
                if(jobjHMACValue != NULL)
                {
                    LOGD("Get hmac header successfully!\n");
                    const char * strHMACValue = jEnv->GetStringUTFChars(static_cast<jstring>(jobjHMACValue), NULL);
                    LOGD("hmac value=%s\n", strHMACValue);
                    if(strHMACValue != NULL && strlen(strHMACValue) >0)
                    {
                        ProcessCredHeaders(strHMACValue);
                    }
                    LOGD("Finish process hmac header!\n");
                    LOGD("m_pCredHeaders: algorithm:%s\n", (CPCHAR)(m_pCredHeaders->m_oAlgorithm.getBuffer()));
                    LOGD("m_pCredHeaders: username:%s\n", (CPCHAR)(m_pCredHeaders->m_oUserName.getBuffer()));
                    LOGD("m_oRecvCredHeaders: mac:%s\n", (CPCHAR)(m_pCredHeaders->m_oMac.getBuffer()));

                    jEnv->ReleaseStringUTFChars(static_cast<jstring>(jobjHMACValue), strHMACValue);
                }
                LOGD("Return OK");
                return SYNCML_DM_SUCCESS;
            }
            LOGD("Too much data was received!");
            return SYNCML_DM_FAIL;
         } else {
            LOGD("Not retryable network error!");
            break;
        }
    }

    LOGD("Server or Net issue. return code=%d", jResult);
    return jResult;
}

SYNCML_DM_RET_STATUS_T SYNCML_DM_OTAConnection::SetURI(CPCHAR szURL)
{
    LOGD("szURL=%s", szURL);
    m_szURL = szURL;

    return SYNCML_DM_SUCCESS;
}

//==============================================================================
// FUNCTION: SYNCML_DM_OTAConnection::ProcessCredHeaders
//
// DESCRIPTION: This method extracts the Credential headers from the
//               Response headers
//
// ARGUMENTS PASSED:
//          INPUT : Pointer to the Response header
//
//
//          OUTPUT: None
//
// RETURN VALUE:    BOOLEAN
//                  TRUE - If handled successfully
//                  FALSE - If there is some failure
//
//
// IMPORTANT NOTES: The HandleOTARedirect method calls this method.
//==============================================================================
SYNCML_DM_RET_STATUS_T SYNCML_DM_OTAConnection::ProcessCredHeaders(CPCHAR pbOrigHmacStr)
{

    UINT8 *pbHmacString = NULL;
    UINT8 *pbInitialHmacString = NULL;
    UINT8 *pbParam = NULL;
    UINT8 *pbValue = NULL;
    char  *pbAlgo = NULL;
    char  *pbUname = NULL;
    char  *pbMAC = NULL;

    LOGD(("Enter SYNCML_DM_OTAConnection::ProcessCredHeaders\n"));
    if(pbOrigHmacStr == NULL)
        return TRUE;

    //Trim the blank space and tabs
    pbHmacString = DMTPTTrimString((UINT8*)pbOrigHmacStr);
    if (pbHmacString == NULL)
    {
        return SYNCML_DM_FAIL;
    }

    pbInitialHmacString = pbHmacString;

    pbHmacString = (UINT8*)DmStrstr((CPCHAR)pbHmacString, "algorithm");

    if (pbHmacString == NULL)
        pbHmacString = (UINT8*)DmStrstr((CPCHAR)pbInitialHmacString,"username");

        //Extract the algorithm, Username and mac from
        //the x-syncml-hmac header
     while (pbHmacString != NULL)
     {
         pbHmacString = DM_TPT_splitParamValue(pbHmacString,&pbParam,&pbValue);

         if ((pbParam != NULL) && (pbParam [0] != '\0'))
         {
             if (!DmStrcmp ((CPCHAR)pbParam, "algorithm"))
             {
                 pbAlgo = (char*)pbValue;
             }
             else
                if (!DmStrcmp ((CPCHAR)pbParam, "username"))
                {
                    pbUname = (char*)pbValue;
                }
                else
                    if (!DmStrcmp ((CPCHAR)pbParam, "mac"))
                    {
                        pbMAC = (char*)pbValue;
                    }
         }
    }

        // Allocate memory to hold username, mac, algorithm
    if (pbUname == NULL || pbMAC == NULL)
    {
        DmFreeMem(pbInitialHmacString);
        return SYNCML_DM_FAIL;
    }

    if (pbAlgo != NULL)
        m_pCredHeaders->m_oAlgorithm.assign(pbAlgo);
    else
        m_pCredHeaders->m_oAlgorithm.assign("MD5");

    if ( m_pCredHeaders->m_oAlgorithm.getBuffer() == NULL )
    {
        DmFreeMem(pbInitialHmacString);
        return SYNCML_DM_DEVICE_FULL;
    }

    m_pCredHeaders->m_oUserName.assign(pbUname);
    m_pCredHeaders->m_oMac.assign(pbMAC);

    if ( m_pCredHeaders->m_oMac.getBuffer() == NULL )
    {
        DmFreeMem(pbInitialHmacString);
        return SYNCML_DM_DEVICE_FULL;
    }
    DmFreeMem(pbInitialHmacString);
    LOGD(("Leave SYNCML_DM_OTAConnection::ProcessCredHeaders\n"));
    return SYNCML_DM_SUCCESS;
}

#endif

