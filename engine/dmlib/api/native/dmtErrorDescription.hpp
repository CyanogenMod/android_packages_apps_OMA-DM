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

#ifndef __DMT_ERROR_SESC_H__
#define __DMT_ERROR_SESC_H__

#ifndef __cplusplus
#error "This is a C++ header file; it requires C++ to compile."
#endif


/**  
\file dmtErrorDescription.hpp
\brief  The dmtErrorDescription.hpp header file contains PDmtErrorDescription class definition. \n
          This class encapsulates Device Management  Tree error retrieving for backward compatibility. \n 
          <b>Warning:</b>  All functions, structures, and classes from this header file are for internal usage only!!!
*/

#include <string.h>
#include "dmtError.h"
#include "dmstring.h"

/**
 * Error codes definition - please see file dmerr.h for full list of errors.
 * List below is deprecated and provided only for compatability with previous versions.
 */
enum {
/** Success */
  enumDmtResult_Success = SYNCML_DM_SUCCESS, 
/** Unspecified error */
  enumDmtResult_UnspecifiedError = SYNCML_DM_FAIL, 
/** Type mismatch, usually with DmtData class */ 
  enumDmtResult_TypeMismatch = SYNCML_DM_INVALID_PARAMETER,  
/** Only one session is supported by current engine, and previous tree or node was not released */
  enumDmtResult_MultipleSessionsNotSupported = SYNCML_DM_FAIL, 
/** Internal syncml engine error */  
  enumDmtResult_UnableStartSession = SYNCML_DM_FAIL, 
/** Requested functionality is not supported */  
  enumDmtResult_NotSupported = SYNCML_DM_FEATURE_NOT_SUPPORTED, 
/** Some error trying to get node data or attribute */  
  enumDmtResult_UnableGetNode = SYNCML_DM_FAIL, 
/** Some error trying to set node data or attribute */  
  enumDmtResult_UnableSetNode = SYNCML_DM_FAIL, 
/** Attempt to get children for leaf node */  
  enumDmtResult_NoChildrenForLeafNode = SYNCML_DM_FAIL, 
/** Script operation failed */  
  enumDmtResult_ScriptFailed = SYNCML_DM_FAIL, 
/** Delete node failed */  
  enumDmtResult_UnableDeleteNode = SYNCML_DM_FAIL, 
/** Create node failed */  
  enumDmtResult_UnableCreateNode = SYNCML_DM_FAIL, 
/** Acquire lock operation failed */  
  enumDmtResult_UnableAcquireLock = SYNCML_DM_FAIL, 
/** Read only lock was acquired and write is not permitted */  
  enumDmtResult_TreeIsReadOnly = SYNCML_DM_TREE_READONLY, 
 /** Delete all leaf children failed */  
  enumDmtResult_UnableDeleteChildren = SYNCML_DM_FAIL,
/** Failed constraint */  
  enumDmtResult_ConstraintFailed = SYNCML_DM_CONSTRAINT_FAIL
  
};


/**
  * This class is deprecated and kept here for backward compatibility and for encapsulation Device Management  Tree error retrieving. \n 
* \par Category: General
* \par Persistence: Transient
* \par Security: Non-Secure
* \par Migration State: FINAL
 */

class PDmtErrorDescription 
{ 
public:
  /**
  * Default constructor. The memory for the size of "Success." will be allocated.
  */
  PDmtErrorDescription() 
  {
    m_nErrorCode = 0;
    m_strError = "Success.";
  }
  
  /**
   * Constructor with error code only; list of parameters is empty.The memory will be allocated.
   *  \param nError [in] - error code
   */
  PDmtErrorDescription( int nError )
  {
    m_nErrorCode = nError;
    m_strError = "Refer error code for more information.";
  }

   /**
   * Constructor with error message only.  The memory for the size of parameter "msg" will be allocated.
   *  \param msg [in] - error message
   */
  PDmtErrorDescription( DMString msg )
  {
    m_strError = msg;
  }


/**
  * Member selection via pointer operator.
  * \par Sync (or) Async:
  * This is a Synchronous function.
  * \par Secure (or) Non-Secure (or) N/A:
  * This is a Non-Secure function.
  * \return pointer to an element
  * \par Prospective Clients:
  * All potential applications that require configuration settings and Internal Classes.
  */
 PDmtErrorDescription* operator->() 
 { 
  return this; 
 }

 /**
  * Sets error code
  * \par Sync (or) Async:
  * This is a Synchronous function.
  * \par Secure (or) Non-Secure (or) N/A:
  * This is a Non-Secure function.
  * \param nError [in] - error code
  * \par Prospective Clients:
  * All potential applications that require configuration settings and Internal Classes.
  */
  inline void SetErrorCode( int nError )
  {
    m_nErrorCode = nError;
    m_strError = "Refer error code for more information.";
  }

  /**
   * Retrieves error code as an integer.
  * \par Sync (or) Async:
  * This is a Synchronous function.
  * \par Secure (or) Non-Secure (or) N/A:
  * This is a Non-Secure function.
  * \return integer error code
  * \par Prospective Clients:
  * All potential applications that require configuration settings and Internal Classes.
  */
  inline int  GetErrorCode() const 
  { 
      return m_nErrorCode; 
  }
  
  /**
   * Retrieves error code as an string 
  * \par Sync (or) Async:
  * This is a Synchronous function.
  * \par Secure (or) Non-Secure (or) N/A:
  * This is a Non-Secure function.
  * \return human readable error text
  * \par Prospective Clients:
  * All potential applications that require configuration settings and Internal Classes.
  */
  inline const DMString&  GetErrorText() 
  {
      return m_strError;
  }
  
 /**
  * Comparison operator (equally)
  * \par Sync (or) Async:
  * This is a Synchronous function.
  * \par Secure (or) Non-Secure (or) N/A:
  * This is a Non-Secure function.
  * \param p [in] - void pointer
  * \return boolean result of comparison error codes
  * \par Prospective Clients:
  * All potential applications that require configuration settings and Internal Classes.
  */
  inline BOOLEAN operator==(void* p) const 
  { 
      return m_nErrorCode == (intptr_t)p; 
  }
  
 /**
  * Comparison operator (unequally)
  * \par Sync (or) Async:
  * This is a Synchronous function.
  * \par Secure (or) Non-Secure (or) N/A:
  * This is a Non-Secure function.
  * \param p [in] - void pointer
  * \return boolean result of comparison error codes
  * \par Prospective Clients:
  * All potential applications that require configuration settings and Internal Classes.
  */
  inline BOOLEAN operator!=(void* p) const 
  { 
      return m_nErrorCode != (intptr_t)p;
  }

 /**
  * Assign  operator
  * \par Sync (or) Async:
  * This is a Synchronous function.
  * \par Secure (or) Non-Secure (or) N/A:
  * This is a Non-Secure function.
  * \param s [in] - error code
  * \return pointer to an element or NULL
  * \par Prospective Clients:
  * All potential applications that require configuration settings and Internal Classes.
  */
  inline PDmtErrorDescription* operator=(int s) 
  { 
      if ( s!=0 )
      {
         SetErrorCode(s);
         return this; 
      }
      else
      {
          SetErrorCode(0);
          return NULL;
      
      } 
  };

private:
  // data
  int   m_nErrorCode;
  DMString m_strError;
};

#endif
