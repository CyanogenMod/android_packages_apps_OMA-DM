#include <android_runtime/AndroidRuntime.h>
#include "utils/Log.h"
#include "DMServiceMain.h"
#include "dmt.hpp"
#include <stdarg.h>
#include <dmMemory.h>

extern "C" {
#include "xltdec.h"
}

#define RESULT_BUF_SIZE 8192 /*2048*/

static PDmtTree ptrTree=NULL;
static DMString s_strRootPath = "";
static PDmtErrorDescription e;
static DmtPrincipal principal("localhost");
static bool bShowTimestamp = false;


static void Open( const char * szNode);
static PDmtTree GetTree();
static void PrintNode( PDmtNode ptrNode );
static void DumpSubTree( PDmtNode ptrNode );

static char resultBuf[RESULT_BUF_SIZE];
static void strcatEx(const char * format, ...)
{
    if (!format){
        return;
    }

    int len = strlen(resultBuf);
    if(len < RESULT_BUF_SIZE - 1){
        va_list args;
        va_start (args, format);
        int ret = vsnprintf (&resultBuf[len], RESULT_BUF_SIZE - len - 1, format, args);
        if(ret == -1){
            resultBuf[RESULT_BUF_SIZE - 1] = 0x0;
        }
        va_end (args);
    }
}

static  PDmtNode GetNode( const char*  szNodeName )
{
    PDmtNode ptrNode;
    GetTree();

    if ( ptrTree != NULL ){
        if ( (e=ptrTree->GetNode( szNodeName, ptrNode)) != NULL ) {
            strcatEx("can't get node %s", szNodeName);
        }
    }

    return ptrNode;
}

JNIEXPORT jstring JNICALL setStringNode(JNIEnv *jenv,
        jclass jclz, jstring nodePath, jstring value)
{
    const char* szNode = jenv->GetStringUTFChars(nodePath, NULL);
    const char* szValue = jenv->GetStringUTFChars(value, NULL);

    resultBuf[0] = 0x0;
    PDmtNode ptrNode = GetNode(szNode);
    if (ptrNode == NULL) {
        goto end;
    }

    if ( (e=ptrNode->SetStringValue(szValue)) == NULL ) {
        strcatEx("set value of node %s to %s successfully\n", szNode, szValue);
        PrintNode(ptrNode);
    } else {
        strcatEx("can't set value of node %s to %s", szNode, szValue);
    }
end:
    jstring ret = jenv->NewStringUTF(resultBuf);
    return ret;
}

JNIEXPORT jstring JNICALL getNodeInfo(JNIEnv *jenv, jclass clz, jstring jszNode)
{
    resultBuf[0] = 0x0;
    const char* szNode = jenv->GetStringUTFChars(jszNode, NULL);
    PDmtNode ptrNode = GetNode( szNode );

    if ( ptrNode != NULL )
    {
        PrintNode(ptrNode);
    }

    jstring ret = jenv->NewStringUTF(resultBuf);
    return ret;
}

JNIEXPORT jstring JNICALL executePlugin(JNIEnv *jenv, jclass clz,
        jstring jszNode, jstring jszData)
{
    const char* szNode = jenv->GetStringUTFChars(jszNode, NULL);
    const char* szData = jenv->GetStringUTFChars(jszData, NULL);
    resultBuf[0] = 0x0;

    PDmtNode ptrNode = GetNode(szNode);

    if (ptrNode != NULL) {
        DMString strResult;
        if ( (e=ptrNode->Execute(szData, strResult)) == NULL ) {
            strcatEx("execute node %s successfully, result=%s\n",
                    szNode, strResult.c_str() );
        } else {
            strcatEx("can't execute node %s", szNode);
        }
    }
    jstring ret = jenv->NewStringUTF(resultBuf);

    return ret;
}

JNIEXPORT jstring JNICALL dumpTree(JNIEnv *jenv, jclass clz, jstring jszNode)
{
    const char* szNode = jenv->GetStringUTFChars(jszNode, NULL);
    resultBuf[0] = 0x0;

    PDmtNode ptrNode = GetNode( szNode );
    if ( ptrNode != NULL  ) {
        DumpSubTree( ptrNode );
    }

    jstring ret = jenv->NewStringUTF(resultBuf);
    return ret;
}

JNIEXPORT jint JNICALL createInterior(JNIEnv *jenv, jclass clz, jstring jszNode)
{
    const char* szNode = jenv->GetStringUTFChars(jszNode, NULL);
    PDmtNode ptrNode;
    GetTree();

    if ( ptrTree == NULL ) {
        return SYNCML_DM_FAIL;
    }

    if ( (e=ptrTree->CreateInteriorNode( szNode, ptrNode )) == NULL ) {
        LOGI( "node %s created successfully\n", szNode );
        jenv->ReleaseStringUTFChars(jszNode, szNode);
        return SYNCML_DM_SUCCESS;
    } else {
        LOGE("can't create a node %s", szNode);
        jenv->ReleaseStringUTFChars(jszNode, szNode);
        return SYNCML_DM_FAIL;
    }
}

JNIEXPORT jint JNICALL createLeaf( JNIEnv *jenv, jclass clz, jstring jszNode, jstring jszData )
{
    if(jszNode == NULL){
        return SYNCML_DM_FAIL;
    }

    const char *szNode = jenv->GetStringUTFChars(jszNode, NULL);

    const char *szData = "";
    if(jszData != NULL){
        szData = jenv->GetStringUTFChars(jszData, NULL);
    }

    //LOGE("node=%s, szData=0x%X, 0x%X\n", szNode, szData[0], szData[1]);

    PDmtNode ptrNode;
    GetTree();

    if ( ptrTree == NULL ) {
        return SYNCML_DM_FAIL;
    }

    if ((e=ptrTree->CreateLeafNode( szNode, ptrNode, DmtData( szData ))) == NULL ) {
        LOGI( "node %s (%s) created successfully\n", szNode, szData );
        jenv->ReleaseStringUTFChars(jszNode, szNode);
        if(jszData!=NULL){
            jenv->ReleaseStringUTFChars(jszData, szData);
        }
        return SYNCML_DM_SUCCESS;
    } else {
        LOGE("can't create a node %s", szNode);
        jenv->ReleaseStringUTFChars(jszNode, szNode);
        if(jszData!=NULL){
            jenv->ReleaseStringUTFChars(jszData, szData);
        }
        return SYNCML_DM_FAIL;
    }
}

JNIEXPORT jint JNICALL createLeafByte( JNIEnv *jenv, jclass clz, jstring jszNode, jbyteArray bDataArray)
{
    const char* szNode = jenv->GetStringUTFChars(jszNode, NULL);
    jbyte* jData = (jbyte*)jenv->GetByteArrayElements(bDataArray, NULL);
    jsize arraySize = jenv->GetArrayLength(bDataArray);

    char* pData = (char*)DmAllocMem(arraySize+1);
    memcpy(pData, jData, arraySize);
    pData[arraySize] = '\0';

    PDmtNode ptrNode;
    GetTree();

    jenv->ReleaseByteArrayElements(bDataArray, jData, 0);

    if ( ptrTree == NULL ) {
        DmFreeMem(pData);
        return SYNCML_DM_FAIL;
    }

    LOGI("NodePath=%s,Byte Data=0x%X,0x%X,0x%X,0x%X,0x%X,0x%X,0x%X\n", szNode, pData[0], pData[1], pData[2], pData[3], pData[4], pData[5], pData[6]);
    if ((e=ptrTree->CreateLeafNode( szNode, ptrNode, DmtData( pData ))) == NULL ) {
        DmFreeMem(pData);
        LOGI( "node %s created successfully\n", szNode);
        return SYNCML_DM_SUCCESS;
    } else {
        DmFreeMem(pData);
        LOGE("can't create a node %s", szNode);
        return SYNCML_DM_FAIL;
    }
}

JNIEXPORT jint JNICALL deleteNode(JNIEnv *jenv, jclass clz, jstring jszNode )
{
    const char* szNode = jenv->GetStringUTFChars(jszNode, NULL);
    GetTree();
    if ( ptrTree == NULL ) {
        return SYNCML_DM_FAIL;
    }

    if ( (e=ptrTree->DeleteNode( szNode )) == NULL ) {
        LOGI( "node %s deleted successfully\n", szNode );
        return SYNCML_DM_SUCCESS;
    } else {
        LOGE("can't delete node %s", szNode);
        return SYNCML_DM_FAIL;
    }
}


static void Open( const char * szNode)
{
    if ( strcmp(szNode, ".") == 0) {
        s_strRootPath = "";
    } else {
        s_strRootPath = szNode;
    }
    ptrTree = NULL;
    LOGV("Open tree: %s\n", s_strRootPath.c_str());
}

static PDmtTree GetTree()
{
    if (ptrTree != NULL) return ptrTree;

    if ( (e=DmtTreeFactory::GetSubtree(principal, s_strRootPath.c_str(), ptrTree)) != NULL ) {
        strcatEx("Can't get tree '%s'.", s_strRootPath.c_str());
    }

    return ptrTree;
}

static void DumpSubTree( PDmtNode ptrNode )
{
    PrintNode(ptrNode);
    strcatEx("\n");
    if ( e != NULL ) return;

    if ( !ptrNode->IsLeaf() ) {
        DMVector<PDmtNode> aChildren;
        if ( (e=ptrNode->GetChildNodes( aChildren )) != NULL ) {
            DMString path;
            ptrNode->GetPath(path);
            strcatEx("can't get child nodes of %s", path.c_str());
            return;
        }
        for (int i=0; i < aChildren.size(); i++) {
            DumpSubTree( aChildren[i] );
        }
    }
}

static void PrintNode(PDmtNode ptrNode )
{
    LOGD("Enter PrintNode\n");
    DmtAttributes oAttr;
    DMString path;

    if( (e=ptrNode->GetPath(path)) != NULL )
    {
        strcatEx("can't get attributes of node %d",  e.GetErrorCode());
    }
    LOGD("Get attrributes\n");
    if ( (e=ptrNode->GetAttributes( oAttr )) != NULL) {
        strcatEx("can't get attributes of node %s",  path.c_str());
        return;
    }
    LOGD("Checi storage mode...\n");
    DmtData oData;
    if (!ptrNode->IsExternalStorageNode())
    {
        LOGD("Enter get value...\n");
        SYNCML_DM_RET_STATUS_T ret1 = SYNCML_DM_SUCCESS;
        ret1 =ptrNode->GetValue(oData);
        if (ret1 != SYNCML_DM_SUCCESS) {
            LOGD("Value is null");
            strcatEx("can't get value of node %s", path.c_str());
            return;
        }
    }

    LOGD("Compose string begin...\n");
    strcatEx("path=%s\n", (const char*)path.c_str());
    strcatEx("isLeaf=%s\n", (ptrNode->IsLeaf()?"true":"false") );
    strcatEx("name=%s\n", (const char*)oAttr.GetName().c_str() );
    strcatEx("format=%s\n", (const char*)oAttr.GetFormat().c_str() );
    strcatEx("type=%s\n", (const char*)oAttr.GetType().c_str() );
    strcatEx("title=%s\n", (const char*)oAttr.GetTitle().c_str() );
    strcatEx("acl=%s\n", (const char*)oAttr.GetAcl().toString().c_str() );

    strcatEx("size=%d\n", (const char*)oAttr.GetSize() );
    if (bShowTimestamp ) {
        if ( oAttr.GetTimestamp() == 0 ) {
            strcatEx("timestamp=(Unknown)\n");
        } else {
            time_t timestamp = (time_t)(oAttr.GetTimestamp()/1000L);
            strcatEx("timestamp=%s", ctime(&timestamp) );
        }
    }

    strcatEx("version=%d\n", oAttr.GetVersion() );
    if ( !ptrNode->IsLeaf() ) {
        DMStringVector aChildren;
        oData.GetNodeValue( aChildren );
        strcatEx("children:");
        if ( aChildren.size() == 0 ) {
            strcatEx("null");
        }

        for (int i=0; i < aChildren.size(); i++) {
            strcatEx("%s/", aChildren[i].c_str());
        }
        strcatEx("\n");
    } else {
        if (ptrNode->IsExternalStorageNode())
        {
            strcatEx("value=\n");
            strcatEx("It is a ESN node, not supportted now");
            //displayESN(ptrNode);
        }
        else {
            if ( strcasecmp(oAttr.GetFormat(), "bin") == 0 ) {
                strcatEx("Binary value: [");
                for ( int i = 0 ; i < oData.GetBinaryValue().size(); i++ ){
                    strcatEx("%02x ", oData.GetBinaryValue().get_data()[i]);
                }
                strcatEx("]\n" );
            }
            else
            {
                DMString s;
                oData.GetString(s);
                strcatEx("value=%s\n", s.c_str());
            }
        }

    }
}

short wbxml2xml(unsigned char *bufIn, int bufInLen, unsigned char *bufOut, int * bufOutLen)
{
    short ret = 0;
#ifdef __SML_WBXML__
    ret = wbxml2xmlInternal(bufIn, bufInLen, bufOut,bufOutLen);
#endif
    return ret;
}

JNIEXPORT jbyteArray JNICALL ConvertWbxml2Xml( JNIEnv *env, jclass clz, jbyteArray bArray)
{
    unsigned char* xmlBuf = NULL;
    int xmlLen = 0;

    jbyte* wbxmlBuf = env->GetByteArrayElements(bArray, NULL);
    jsize  wbxmlLen = env->GetArrayLength(bArray);
    LOGD("ConvertWbxml2Xml: wbxml length = %d\n", wbxmlLen);

    if (wbxmlBuf == NULL || wbxmlLen <= 0)
    {
        LOGD("ConvertWbxml2Xml: nothing to convert\n");
        return NULL;
    }

    xmlLen = wbxmlLen * 6;
    xmlBuf = new unsigned char[xmlLen];
    if (xmlBuf == NULL)
    {
        LOGE("ConvertWbxml2Xml: failed to allocate memory\n");
        return NULL;
    }
    LOGD("ConvertWbxml2Xml: allocated xml length = %d\n", xmlLen);

#ifdef __SML_WBXML__
    short ret = wbxml2xmlInternal((unsigned char*)wbxmlBuf, wbxmlLen, xmlBuf, &xmlLen);
#else
    short ret = -1;
#endif

    if (ret != 0) {
        LOGE("ConvertWbxml2Xml: wbxml2xml failed: %d\n", ret);
        delete []xmlBuf;
        return NULL;
    }

    jbyteArray jb = env->NewByteArray(xmlLen);
    env->SetByteArrayRegion(jb, 0, xmlLen, (jbyte*)xmlBuf);
    LOGD("ConvertWbxml2Xml: result xml length = %d\n", xmlLen);
    delete []xmlBuf;
    return jb;
}

static JNINativeMethod gMethods[] = {
    {"setStringNode",
        "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
        (void*)setStringNode},
    {"getNodeInfo",
        "(Ljava/lang/String;)Ljava/lang/String;",
        (void*)getNodeInfo},
    {"executePlugin",
        "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
        (void*)executePlugin},
    {"dumpTree",
        "(Ljava/lang/String;)Ljava/lang/String;",
        (void*)dumpTree},
    {"createInterior",
        "(Ljava/lang/String;)I",
        (void*)createInterior},
    {"createLeaf",
        "(Ljava/lang/String;Ljava/lang/String;)I",
        (void*)createLeaf},
    {"createLeaf",
        "(Ljava/lang/String;[B)I",
        (void*)createLeafByte},
    {"deleteNode",
        "(Ljava/lang/String;)I",
        (void*)deleteNode},
    {"nativeWbxmlToXml",
        "([B)[B",
        (void*)ConvertWbxml2Xml},
};

int registerDMTreeNatives(JNIEnv *env)
{
    jclass clazz = env->FindClass(javaDMEnginePackage);
    if(clazz == NULL)
        return JNI_FALSE;

    if (env->RegisterNatives(clazz, gMethods, sizeof(gMethods)/sizeof(gMethods[0])) < 0)
    {
        LOGE("registerDMTreeNatives return ERROR");
        return JNI_FALSE;
    }

    return JNI_TRUE;
}
