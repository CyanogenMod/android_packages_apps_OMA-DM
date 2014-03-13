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

#ifndef DMSERVICE_ALERT_H
#define DMSERVICE_ALERT_H

#ifndef __cplusplus
#error "This is a C++ header file; it requires C++ to compile."
#endif

#include <DMServiceMain.h>
#include "xpl_dm_ServerAlert.h"

// DMServiceAlert Response Code
#define  DM_SERVICE_ALERT_RESP_FAIL        -1
#define  DM_SERVICE_ALERT_RESP_NONE         0
#define  DM_SERVICE_ALERT_RESP_NO           1
#define  DM_SERVICE_ALERT_RESP_YES          2
#define  DM_SERVICE_ALERT_RESP_CANCEL       3
#define  DM_SERVICE_ALERT_RESP_TIMEOUT      4

#ifdef DM_SDMSERVICES
#define  DM_SERVICE_ALERT_MAX_MSG_SIZE      250
#endif

// DMServiceAlert Input Type
#define  DM_SERVICE_ALERT_INPUT_ALPHA       0
#define  DM_SERVICE_ALERT_INPUT_NUMERIC     1
#define  DM_SERVICE_ALERT_INPUT_DATE        2
#define  DM_SERVICE_ALERT_INPUT_TIME        3
#define  DM_SERVICE_ALERT_INPUT_PHONE_NUM   4
#define  DM_SERVICE_ALERT_INPUT_IP_ADDR     5

// DMServiceAlert Echo Type
#define  DM_SERVICE_ALERT_ECHO_TEXT         0
#define  DM_SERVICE_ALERT_ECHO_PASSWD       1

// DMServiceAlert Icon Type
#define DM_SERVICE_ALERT_ICON_GENERIC      0
#define DM_SERVICE_ALERT_ICON_PROGRESS     1
#define DM_SERVICE_ALERT_ICON_OK           2
#define DM_SERVICE_ALERT_ICON_ERROR        3
#define DM_SERVICE_ALERT_ICON_CONFIRM      4
#define DM_SERVICE_ALERT_ICON_ACTION       5
#define DM_SERVICE_ALERT_ICON_INFO         6

// DMServiceAlert Title Type
#define DM_SERVICE_ALERT_TITLE_NULL                                      0
#define PMF_RESOURCE_ID_TITLE_SYSTEM_UPDATE                              1
#define PMF_RESOURCE_ID_TITLE_NEED_AUTHENTICATION                        2
#define PMF_RESOURCE_ID_TITLE_UPDATE_COMPLETE                            3
#define PMF_RESOURCE_ID_TITLE_SYSTEM_MESSAGE                             4
#define PMF_RESOURCE_ID_TITLE_AUTHENTICATION_FAILED                      5
#define PMF_RESOURCE_ID_TITLE_UPDATE_ERROR                               6
#define PMF_RESOURCE_ID_TITLE_UPDATE_CANCELLED                           7
#define PMF_RESOURCE_ID_TITLE_PROFILE_FOR_BROWSER                        8
#define PMF_RESOURCE_ID_TITLE_PROFILE_FOR_MMS                            9
#define PMF_RESOURCE_ID_TITLE_CONNECTION_FAILED                          10
#define PMF_RESOURCE_ID_TITLE_CONNECTION_FAILURE                         11
#define PMF_RESOURCE_ID_TITLE_SW_UPDATE                                  12
#define PMF_RESOURCE_ID_14674_TITLE_REGISTRATION                         13
#define PMF_RESOURCE_ID_CONTEXT_SYSTEM_UPDATE_SEVERAL_MINUTES_NO_PROCEED 14
#define PMF_RESOURCE_ID_CONTEXT_SYSTEM_UPDATE_NOTICE                     15
#define PMF_RESOURCE_ID_CONTEXT_SYSTEM_UPDATE                            16
#define PMF_RESOURCE_ID_CONTEXT_ENTER_PIN_CARRIER                        17
#define PMF_RESOURCE_ID_CONTEXT_SYSTEM_UPDATE_IN_PROGRESS                18
#define PMF_RESOURCE_ID_CONTEXT_DO_YOU_WANT_TO_ACCEPT_UPDATE             19
#define PMF_RESOURCE_ID_CONNECTING_TO_UPDATE_SERVICE                     20
#define PMF_RESOURCE_ID_CONTEXT_SYSTEM_UPDATE_COMPLETED                  21
#define PMF_RESOURCE_ID_CONTEXT_CONNECTION_SUCCEEDED                     22
#define PMF_RESOURCE_ID_CONTEXT_PIN_FAILED_TRY_AGAIN                     23
#define PMF_RESOURCE_ID_CONTEXT_PIN_FAILED_CONTACT_CARRIER               24
#define PMF_RESOURCE_ID_CONTEXT_SYSTEM_UPDATE_FAILED_CONTACT_CARRIER     25
#define PMF_RESOURCE_ID_CONTEXT_CONNECTION_FAILED                        26
#define PMF_RESOURCE_ID_CONTEXT_SYSTEM_UPDATE_CANCELLED                  27
#define PMF_RESOURCE_ID_CONTEXT_PLEASE_SELECT_BROWSER_PROFILE            28
#define PMF_RESOURCE_ID_CONTEXT_PLEASE_SELECT_MMS_PROFILE                29
#define PMF_RESOURCE_ID_CONTEXT_AUTH_FAILED_CONTACT_CARRIER              30
#define PMF_RESOURCE_ID_CONTEXT_NWNOT_AVAILABLE                          31
#define PMF_RESOURCE_ID_CONTEXT_CONNECT_ERROR                            32
#define PMF_RESOURCE_ID_CONTEXT_SW_UPDATE                                33
#define PMF_RESOURCE_ID_CONTEXT_CONNECTING                               34

class DMServiceAlert {

  public:
      /**
       * Constructor
       *
       **/
     DMServiceAlert();

      /**
       * De-Constructor
       *
       **/
     ~DMServiceAlert();

      /**
       * Display a text messages
       *
       * @param minDisplayTime minimum display time, in seconds.
       * @param msg messages to display
       * @return Upon successful completion, the SYNCML_DM_SUCCESS is returned, otherwise
       * SYNCML_DM_FAIL or other more specific error codes.
       **/
      SYNCML_DM_RET_STATUS_T
      showDisplayAlert( INT32 minDisplayTime,
                        CPCHAR msg,
                        INT32 title = DM_SERVICE_ALERT_TITLE_NULL,
                        INT32 icon = DM_SERVICE_ALERT_ICON_GENERIC );

      /**
       * Display a confirm alert message box, user can confirm or cancel the action
       *
       * @param maxDisplayTime maximum display time (for timeout), in seconds.
       * @param msg messages to display
       * @param defaultResponse default user action when timeout
       * @param responseCode user's action will be returned here.
       * @return Upon successful completion, the SYNCML_DM_SUCCESS is returned, otherwise
       * SYNCML_DM_FAIL or other more specific error codes.
       **/
      SYNCML_DM_RET_STATUS_T
      showConfirmAlert( INT32 maxDisplayTime,
                        CPCHAR msg,
                        XPL_DM_ALERT_RES_T defaultResponse,
                        XPL_DM_ALERT_RES_T *response,
                        INT32 title = DM_SERVICE_ALERT_TITLE_NULL,
                        INT32 icon = DM_SERVICE_ALERT_ICON_GENERIC );

      /**
       * Display a text input message box for user to enter input.
       *
       * @param maxDisplayTime maximum display time (for timeout), in seconds.
       * @param msg messages to display
       * @param defaultResponse default user action when timeout
       * @param maxLength length allowed in user input
       * @param inputType data format as specified in DM_ALERT_INPUT_T
       * @param echoType whether to echo user input (hidden for password ) as specified in DM_ALERT_ECHO_T
       * @param response hold user's response action and input data.
       * @return Upon successful completion, the SYNCML_DM_SUCCESS is returned, otherwise
       * SYNCML_DM_FAIL or other more specific error codes.
       **/
      SYNCML_DM_RET_STATUS_T
      showTextInputAlert( INT32 maxDisplayTime,
                          CPCHAR msg,
                          CPCHAR defaultResponse,
                          INT32 maxLength,
                          XPL_DM_ALERT_INPUT_T inputType,
                          XPL_DM_ALERT_ECHO_T echoType,
                          XPL_DM_ALERT_TEXTINPUT_RES_T * response,
                          INT32 title = DM_SERVICE_ALERT_TITLE_NULL,
                          INT32 icon = DM_SERVICE_ALERT_ICON_GENERIC );

      /**
       * Display a single choice message box for user to pick up one entry.
       *
       * @param maxDisplayTime maximum display time (for timeout), in seconds.
       * @param msg messages to display
       * @param choices a string vector to hold text for each choice
       * @param defaultResponse default user action when timeout
       * @param response hold user's response action and selected choice.
       * @return Upon successful completion, the SYNCML_DM_SUCCESS is returned, otherwise
       * SYNCML_DM_FAIL or other more specific error codes.
       **/
      SYNCML_DM_RET_STATUS_T
      showSingleChoiceAlert( INT32 maxDisplayTime,
                             CPCHAR msg,
                             DMStringVector & choices,
                             INT32 defaultResponse,
                             XPL_DM_ALERT_SCHOICE_RES_T * response,
                             INT32 title = DM_SERVICE_ALERT_TITLE_NULL,
                             INT32 icon = DM_SERVICE_ALERT_ICON_GENERIC );

      /**
       * Display a multiple choice message box for user to pick up zero to many entry.
       *
       * @param maxDisplayTime maximum display time (for timeout), in seconds.
       * @param msg messages to display
       * @param choices a string vector to hold text for each choice
       * @param defaultResponse default user action when timeout
       * @param defaultResponses holds default response in an array of string representation of
       * selected indexes (starting from 1)
       * @param response hold user's response action and selected choice.
       * @return Upon successful completion, the SYNCML_DM_SUCCESS is returned, otherwise
       * SYNCML_DM_FAIL or other more specific error codes.
       **/
      SYNCML_DM_RET_STATUS_T
      showMultipleChoiceAlert( INT32 maxDisplayTime,
                               CPCHAR msg,
                               DMStringVector & choices,
                               DMStringVector & defaultResponses,
                               XPL_DM_ALERT_MCHOICE_RES_T * response,
                               INT32 title = DM_SERVICE_ALERT_TITLE_NULL,
                               INT32 icon = DM_SERVICE_ALERT_ICON_GENERIC );

  private:
      JNIEnv*    m_jDmEnv;
      jclass     m_jDmAlertCls;
      jobject    m_jDmAlertObj;
      jmethodID  m_jDmAlertMID;

      inline bool isJvmNull() { return NULL == m_jDmEnv || NULL == m_jDmAlertCls || NULL == m_jDmAlertObj; };
};

#endif /* DMSERVICEALERT_H */
