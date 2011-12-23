// WMISensor.cpp : Defines the entry point for the DLL application.
//

#include "stdafx.h"
#include "WMIService.h"
#include "WMIObjectSink.h"
#include "WMIUtils.h"

#define FIELD_NAME_SERVICE_HANDLE ("serviceHandle")

class WMIThreadInfo {
public:
	DWORD nThreadId;
	JNIEnv* pThreadEnv;
	ObjectSinkVector sinks;
};

typedef std::vector< WMIThreadInfo* > ThreadInfoVector;

static CComCriticalSection csSinkThreads;
JavaVM* WMIService::pJavaVM = NULL;
//static ThreadInfoVector threadInfos;

WMIService::WMIService(JNIEnv* pJavaEnv, jobject javaObject) :
	pWbemLocator(NULL),
	pWbemServices(NULL),
	jniMeta(pJavaEnv)
{
	serviceJavaObject = pJavaEnv->NewGlobalRef(javaObject);
	if (!pJavaEnv->ExceptionCheck()) {
		pJavaEnv->SetLongField(serviceJavaObject, jniMeta.wmiServiceHandleField, (jlong)this);
	}

	{
		CComCritSecLock<CComCriticalSection> guard(csSinkThreads);
		if (pJavaVM == NULL) {
			pJavaEnv->GetJavaVM(&pJavaVM);
			_ASSERT(pJavaVM != NULL);
		}

	}
}

WMIService::~WMIService()
{
}

void WMIService::Close(JNIEnv* pJavaEnv)
{
	if (pWbemServices != NULL) {
		pWbemServices->Release();
		pWbemServices = NULL;
	}
	if (pWbemLocator != NULL) {
		pWbemLocator->Release();
		pWbemLocator = NULL;
	}
	WriteLog(pJavaEnv, LT_INFO, L"WMI Service closed");

	if (serviceJavaObject != NULL) {
		pJavaEnv->SetLongField(serviceJavaObject, jniMeta.wmiServiceHandleField, 0);
		pJavaEnv->DeleteGlobalRef(serviceJavaObject);
		serviceJavaObject = NULL;
	}
}

WMIService* WMIService::GetFromObject(JNIEnv* pJavaEnv, jobject javaObject)
{
	jclass objectClass = pJavaEnv->GetObjectClass(javaObject);
	jfieldID fid = pJavaEnv->GetFieldID(objectClass, "serviceHandle", "J");
	DeleteLocalRef(pJavaEnv, objectClass);
	_ASSERT(fid != NULL);
	if (fid == NULL) {
		return NULL;
	}
	return (WMIService*)pJavaEnv->GetLongField(javaObject, fid);
}

void WMIService::WriteLog(JNIEnv* pLocalEnv, LogType logType, LPCWSTR wcMessage, HRESULT hr)
{
#ifdef DEBUG
	_RPTW1(_CRT_WARN, L"%s\n", wcMessage);
#endif

	// Get log field
	const char* cLogMethodName = "debug";
	switch (logType) {
		case LT_TRACE: cLogMethodName = "trace"; break;
		case LT_DEBUG: cLogMethodName = "debug"; break;
		case LT_INFO: cLogMethodName = "info"; break;
		case LT_ERROR: cLogMethodName = "error"; break;
		case LT_FATAL: cLogMethodName = "fatal"; break;
		default: 
			// Unsuported log type
			return;
	}

	jobject logObject = pLocalEnv->GetObjectField(serviceJavaObject, jniMeta.wmiServiceLogField);
	_ASSERT(logObject != NULL);
	if (logObject != NULL) {
		// Get log method
		jclass logClass = pLocalEnv->GetObjectClass(logObject);
		jmethodID logMethodID = pLocalEnv->GetMethodID(logClass, cLogMethodName, "(Ljava/lang/Object;)V");
		DeleteLocalRef(pLocalEnv, logClass);
		_ASSERT(logMethodID != NULL);
		if (logMethodID != NULL) {
			CComBSTR errorMessage;
			if (FAILED(hr)) {
				FormatErrorMessage(wcMessage, hr, &errorMessage);
				wcMessage = errorMessage;
			}
			jstring jMessage = MakeJavaString(pLocalEnv, wcMessage);
			pLocalEnv->CallVoidMethod(logObject, logMethodID, jMessage);
			DeleteLocalRef(pLocalEnv, jMessage);
		}
	}

	// Remove any exceptions occured in this method
	if (pLocalEnv->ExceptionCheck()) {
		pLocalEnv->ExceptionClear();
	}
}

/*
 * Class:     com_symantec_cas_ucf_sensors_wmi_service_WMIService
 * Method:    connect
 * Signature: (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V
 */
void WMIService::Connect(
	JNIEnv* pJavaEnv,
	LPWSTR domain, 
	LPWSTR host, 
	LPWSTR user, 
	LPWSTR password,
	LPWSTR locale)
{
    if (this->pWbemLocator != NULL) {
		THROW_COMMON_EXCEPTION(L"WMI Locator was already initialized");
		return;
	}

	HRESULT hres =  ::CoInitializeSecurity(
        NULL, 
        -1,                          // COM authentication
        NULL,                        // Authentication services
        NULL,                        // Reserved
        RPC_C_AUTHN_LEVEL_DEFAULT,   // Default authentication 
        RPC_C_IMP_LEVEL_IMPERSONATE, // Default Impersonation  
        NULL,                        // Authentication info
        EOAC_NONE,                   // Additional capabilities 
        NULL                         // Reserved
        );
    if (FAILED(hres)) {
		THROW_COMMON_ERROR(L"Failed to initialize security", hres);
        return;
    }

    // Step 3: ---------------------------------------------------
    // Obtain the initial locator to WMI -------------------------

    hres = CoCreateInstance(
        CLSID_WbemLocator,             
        0, 
        CLSCTX_INPROC_SERVER, 
        IID_IWbemLocator, 
		(LPVOID *) &pWbemLocator);
    if (FAILED(hres)) {
		THROW_COMMON_ERROR(L"Failed to create IWbemLocator object", hres);
		return;
    }

	// Connect to server
	CComBSTR resource = L"\\\\";
	resource.Append(host);
	resource.Append(L"\\root\\cimv2");
	CComBSTR resourceDomain;
	if (domain != NULL) {
		resourceDomain.Append(L"NTLMDOMAIN:");
		resourceDomain.Append(domain);
	}
	hres = pWbemLocator->ConnectServer(
        resource,
		user,				// User name
		password,		// User password
		locale == NULL ? L"MS_409" : locale,	// Locale
        NULL,                           // Security flags
        resourceDomain,					// Authority
        0,                              // Context object
        &pWbemServices                  // IWbemServices proxy
        );
    if (FAILED(hres)) {
        THROW_COMMON_ERROR(L"Failed to connect to WMI Service", hres);
		return;
    }

    // Set security levels on a WMI connection ------------------
    hres = CoSetProxyBlanket(
		pWbemServices,					// Indicates the proxy to set
		RPC_C_AUTHN_WINNT,           // RPC_C_AUTHN_xxx
		RPC_C_AUTHZ_NONE,            // RPC_C_AUTHZ_xxx
		NULL,                        // Server principal name 
		RPC_C_AUTHN_LEVEL_CALL,      // RPC_C_AUTHN_LEVEL_xxx 
		RPC_C_IMP_LEVEL_IMPERSONATE, // RPC_C_IMP_LEVEL_xxx
		NULL,                        // client identity
		EOAC_NONE                    // proxy capabilities 
		);
    if (FAILED(hres)) {
        THROW_COMMON_ERROR(L"Could not set proxy blanket", hres);
		return;
    }

	WriteLog(pJavaEnv, LT_INFO, bstr_t("WMI Service connected to ") + (LPCWSTR)resource);
}

jobjectArray WMIService::ExecuteQuery(JNIEnv* pJavaEnv, LPWSTR queryString, bool sync)
{
	if (queryString == NULL) {
		THROW_COMMON_EXCEPTION(L"Empty query specified");
		return NULL;
	}
	if (pWbemServices == NULL) {
		THROW_COMMON_EXCEPTION(L"WMI Service is not initialized");
		return NULL;
	}

    // Use the IWbemServices pointer to make requests of WMI ----
	this->WriteLog(pJavaEnv, LT_DEBUG, bstr_t(L"WQL: ") + queryString);
	jlong startTime = ::GetCurrentJavaTime();
    IEnumWbemClassObject* pEnumerator = NULL;
	long lFlags = WBEM_FLAG_FORWARD_ONLY | WBEM_FLAG_DIRECT_READ;
	if (!sync) lFlags |= WBEM_FLAG_RETURN_IMMEDIATELY;
    HRESULT hres = pWbemServices->ExecQuery(
        L"WQL",
        queryString,
        lFlags, 
        NULL,
        &pEnumerator);
    if (FAILED(hres)) {
        THROW_COMMON_ERROR(L"Could not execute query", hres);
        return NULL;
    }

	jlong parseTime = ::GetCurrentJavaTime();
	{
		bstr_t msg(L"Query finished in ");
		msg += (long)(parseTime - startTime);
		msg += L"ms";
		this->WriteLog(pJavaEnv, LT_DEBUG, msg);
	}

	JavaObjectVector rows;

	int objectsCount = 0;
	while (pEnumerator != NULL) {
		IWbemClassObject *pClassObject = NULL;
		ULONG uReturn = 0;
        hres = pEnumerator->Next(WBEM_INFINITE, 1, &pClassObject, &uReturn);
        if (uReturn == 0) {
            break;
        }
		if (FAILED(hres)) {
			this->WriteLog(pJavaEnv, LT_ERROR, L"Could not obtain next class object", hres);
			continue;
		}
		objectsCount++;
		jobject rowObject = MakeWMIObject(pJavaEnv, pClassObject);
		if (pClassObject != NULL) {
			pClassObject->Release();
		}

		CHECK_JAVA_EXCEPTION_NULL();

		if (rowObject != NULL) {
			rows.push_back(rowObject);
		}
	}
	if (pEnumerator != NULL) {
		pEnumerator->Release();
	}
	{
		jlong endTime = ::GetCurrentJavaTime();
		bstr_t msg(L"Query returned [");
		msg += (long)rows.size();
		msg += L"] object(s), parse time: ";
		msg += (long)(endTime - parseTime);
		msg += L"ms";

		this->WriteLog(pJavaEnv, LT_DEBUG, msg);
	}

	// Make object array from rows vector
	return ::MakeJavaArrayFromVector(pJavaEnv, jniMeta.wmiObjectClass, rows);
}

void WMIService::ExecuteQueryAsync(JNIEnv* pJavaEnv, LPWSTR queryString, jobject javaSinkObject, bool sendStatus)
{
	if (queryString == NULL) {
		THROW_COMMON_EXCEPTION(L"Empty query specified");
		return;
	}
	if (pWbemServices == NULL) {
		THROW_COMMON_EXCEPTION(L"WMI Service is not initialized");
		return;
	}

	CComPtr<WMIObjectSink> pSink = new CComObject<WMIObjectSink>();
	pSink->InitSink(this, pJavaEnv, javaSinkObject);

	CComPtr<IWbemObjectSink> pSecuredSink;

	// Make unsecured appartments for sink
	{
		CComPtr<IUnsecuredApartment> pUnsecApp;
		HRESULT hr = CoCreateInstance(CLSID_UnsecuredApartment, NULL, 
			CLSCTX_LOCAL_SERVER, IID_IUnsecuredApartment, 
			(void**)&pUnsecApp);
		if (pUnsecApp != NULL) {
			CComPtr<IUnknown> pStubUnk;
			pUnsecApp->CreateObjectStub(
			   pSink,
			   &pStubUnk);
			if (pStubUnk != NULL) {
				pStubUnk.QueryInterface(&pSecuredSink);
				if (pSecuredSink != NULL) {
					this->WriteLog(pJavaEnv, LT_DEBUG, L"Using unsecured appartments for async queries");
				}
			}
		}
	}

    // Use the IWbemServices pointer to make requests of WMI ----
	this->WriteLog(pJavaEnv, LT_DEBUG, bstr_t(L"Async WQL: ") + queryString);
    IEnumWbemClassObject* pEnumerator = NULL;
	long lFlags = WBEM_FLAG_DIRECT_READ;
	if (sendStatus) lFlags |= WBEM_FLAG_SEND_STATUS;
	IWbemObjectSink* pActiveSink;
	if (pSecuredSink != NULL) {
		pActiveSink = pSecuredSink;
	} else {
		pActiveSink = pSink;
	}
	HRESULT hres = pWbemServices->ExecQueryAsync(
        L"WQL",
        queryString,
        lFlags, 
        NULL,
		pActiveSink);
    if (FAILED(hres)) {
        THROW_COMMON_ERROR(L"Could not execute query", hres);
		return;
    }

	sinkList.push_back(pSink);
}

void WMIService::CancelAsyncOperation(JNIEnv* pJavaEnv, jobject javaSinkObject)
{
	_ASSERT(javaSinkObject != NULL);
	if (javaSinkObject == NULL) {
		THROW_COMMON_EXCEPTION(L"NULL sink object specified");
		return;
	}
	if (pWbemServices == NULL) {
		THROW_COMMON_EXCEPTION(L"WMI Service is not initialized");
		return;
	}
	WriteLog(pJavaEnv, LT_DEBUG, L"Cancel async call");

	WMIObjectSink* pSink = NULL;
	for (ObjectSinkVector::iterator i = sinkList.begin(); i != sinkList.end(); i++) {
		jboolean bEquals = pJavaEnv->CallBooleanMethod(
			javaSinkObject, 
			jniMeta.javaLangObjectEqualsMethod, 
			(*i)->GetJavaSinkObject());
		if (bEquals == JNI_TRUE) {
			pSink = (*i);
		}
	}
	if (pSink == NULL) {
		THROW_COMMON_EXCEPTION(L"Could not find internal sink for specified object");
	}

	HRESULT hres = pWbemServices->CancelAsyncCall(pSink);
	if (FAILED(hres)) {
		THROW_COMMON_ERROR(L"Could not cancel async call", hres);
	}
}

bool WMIService::RemoveObjectSink(JNIEnv* pJavaEnv, WMIObjectSink* pSink)
{
	ObjectSinkVector::iterator i = std::find(sinkList.begin(), sinkList.end(), pSink);
	if (i != sinkList.end()) {
		sinkList.erase(i);
		return true;
	}
	return false;
}

jobject WMIService::MakeWMIObject(JNIEnv* pJavaEnv, IWbemClassObject *pClassObject)
{
	// Fill class object properties
	HRESULT hres = pClassObject->BeginEnumeration(0);
	if (FAILED(hres)) {
		this->WriteLog(pJavaEnv, LT_ERROR, L"Could not start class object properties enumeration", hres);
		return NULL;
	}

	// Create instance
	jobject pWmiObject = pJavaEnv->NewObject(jniMeta.wmiObjectClass, jniMeta.wmiObjectConstructor);
	if (pWmiObject == NULL) {
		this->WriteLog(pJavaEnv, LT_ERROR, L"Can't instantiate WMI java object", hres);
		return NULL;
	}

	for (;;) {
		CComBSTR propName;
		CComVariant propValue;
		CIMTYPE cimType; // CIMTYPE_ENUMERATION
		LONG flavor;
		hres = pClassObject->Next(0, &propName, &propValue, &cimType, &flavor);
		if (FAILED(hres)) {
			this->WriteLog(pJavaEnv, LT_ERROR, L"Could not obtain next class object from enumeration", hres);
			break;
		}
		if (hres == WBEM_S_NO_MORE_DATA) {
			break;
		}
		wchar_t* propNameBSTR = propName;
		jstring javaPropName = MakeJavaString(pJavaEnv, propName);
		_ASSERT(javaPropName != NULL);
		if (javaPropName == NULL) {
			continue;
		}
		jobject javaPropValue = MakeJavaFromVariant(pJavaEnv, propValue, cimType);
		if (!pJavaEnv->ExceptionCheck()) {
			pJavaEnv->CallVoidMethod(pWmiObject, jniMeta.wmiObjectAddPropertyMethod, javaPropName, javaPropValue);
		}
		DeleteLocalRef(pJavaEnv, javaPropName);
		DeleteLocalRef(pJavaEnv, javaPropValue);
		if (pJavaEnv->ExceptionCheck()) {
			break;
		}
	}

	hres = pClassObject->EndEnumeration();
	if (FAILED(hres)) {
		this->WriteLog(pJavaEnv, LT_ERROR, L"Could not finish class object enumeration", hres);
	}

	if (pJavaEnv->ExceptionCheck()) {
		DeleteLocalRef(pJavaEnv, pWmiObject);
		return NULL;
	} else {
		return pWmiObject;
	}
}

jobject WMIService::MakeJavaFromVariant(JNIEnv* pJavaEnv, CComVariant& var, CIMTYPE cimType)
{
	JavaType javaType;

	VARTYPE varType = var.vt;
	bool isArray = (varType & VT_ARRAY) != 0;
	if (isArray) {
		varType &= ~VT_ARRAY;
	}

	switch (varType) {
	case VT_EMPTY:
	case VT_NULL:
	case VT_VOID:
		// Null value
		return NULL;

	case VT_I1:
	case VT_UI1:
		javaType = JT_BYTE;
		break;
	case VT_I2:
	case VT_UI2:
		javaType = JT_SHORT;
		break;
	case VT_I4: 
	case VT_UI4:
	case VT_INT:
	case VT_UINT:
		javaType = JT_INT;
		break;
	case VT_I8:
	case VT_UI8:
		javaType = JT_LONG;
		break;
	case VT_R4: 
		javaType = JT_FLOAT;
		break;
	case VT_R8: 
		javaType = JT_DOUBLE;
		break;
	case VT_DATE: 
		javaType = JT_DATE;
		break;
	case VT_BOOL: 
		javaType = JT_BOOL;
		break;
	case VT_BSTR: 
		javaType = JT_STRING;
		break;

	case VT_ARRAY:
	case VT_SAFEARRAY:
	case VT_DECIMAL:
	case VT_VARIANT:
	default:
		// Unsupported type
		// TODO: warning
		this->WriteLog(pJavaEnv, LT_WARN, L"Unsupported VARIANT type");
		return NULL;
	}

	if (isArray) {
		// Array
		// There two kinds of arrays - primitive and object
		switch (javaType) {
		case JT_BYTE:
			return MakeJavaByteArrayFromSafeArray(pJavaEnv, var.parray);
		case JT_BOOL:
			return MakeJavaBoolArrayFromSafeArray(pJavaEnv, var.parray);
		case JT_SHORT:
			return MakeJavaShortArrayFromSafeArray(pJavaEnv, var.parray);
		case JT_INT:
			return MakeJavaIntArrayFromSafeArray(pJavaEnv, var.parray);
		case JT_LONG:
			return MakeJavaLongArrayFromSafeArray(pJavaEnv, var.parray);
		case JT_FLOAT:
			return MakeJavaFloatArrayFromSafeArray(pJavaEnv, var.parray);
		case JT_DOUBLE:
			return MakeJavaDoubleArrayFromSafeArray(pJavaEnv, var.parray);
		default:
			{
				jclass elementClass;
				switch (javaType) {
					case JT_STRING: elementClass = jniMeta.javaLangStringClass; break;
					default: elementClass = jniMeta.javaLangObjectClass; break;
				}
				return MakeJavaObjectArrayFromSafeVector(pJavaEnv, var.parray, javaType, elementClass);
			}
		}
	} else {
		// Single value
		switch (javaType) {
		case JT_CHAR:
			return pJavaEnv->NewObject(jniMeta.javaLangCharClass, jniMeta.javaLangCharConstructor, var.cVal);
		case JT_BYTE:
			return pJavaEnv->NewObject(jniMeta.javaLangByteClass, jniMeta.javaLangByteConstructor, var.cVal);
		case JT_BOOL:
			return pJavaEnv->NewObject(jniMeta.javaLangBooleanClass, jniMeta.javaLangBooleanConstructor, var.boolVal == VARIANT_TRUE ? JNI_TRUE : JNI_FALSE);
		case JT_SHORT:
			return pJavaEnv->NewObject(jniMeta.javaLangShortClass, jniMeta.javaLangShortConstructor, var.iVal);
		case JT_INT:
			return pJavaEnv->NewObject(jniMeta.javaLangIntegerClass, jniMeta.javaLangIntegerConstructor, var.lVal);
		case JT_LONG:
			return pJavaEnv->NewObject(jniMeta.javaLangLongClass, jniMeta.javaLangLongConstructor, var.llVal);
		case JT_FLOAT:
			return pJavaEnv->NewObject(jniMeta.javaLangFloatClass, jniMeta.javaLangFloatConstructor, var.fltVal);
		case JT_DOUBLE:
			return pJavaEnv->NewObject(jniMeta.javaLangDoubleClass, jniMeta.javaLangDoubleConstructor, var.dblVal);
		case JT_DATE:
			// TODO: correct date value
			return pJavaEnv->NewObject(jniMeta.javaUtilDateClass, jniMeta.javaUtilDateConstructor, (jlong)var.date);
		case JT_STRING:
			if (cimType == CIM_DATETIME) {
				javaType = JT_DATE;
				return pJavaEnv->NewObject(jniMeta.javaUtilDateClass, jniMeta.javaUtilDateConstructor, ConvertCIMTimeToJavaTime(var.bstrVal));
			} else {
				return MakeJavaString(pJavaEnv, var.bstrVal);
			}
		default:
			// Unsupported type
			this->WriteLog(pJavaEnv, LT_WARN, L"Unsupported Java type");
			return NULL;
		}
	}
}

jbyteArray WMIService::MakeJavaByteArrayFromSafeArray(JNIEnv* pJavaEnv, SAFEARRAY* pSafeArray)
{
	jsize arraySize = ::GetSafeArraySize(pSafeArray);
	jbyteArray result = pJavaEnv->NewByteArray(arraySize);
	jbyte HUGEP * byteArray;
	HRESULT hr = ::SafeArrayAccessData(pSafeArray, (void HUGEP* FAR*)&byteArray);
	if (FAILED(hr)) {
		this->WriteLog(pJavaEnv, LT_ERROR, L"Can't access safe array byte data", hr);
		return NULL;
	}
	pJavaEnv->SetByteArrayRegion(result, 0, arraySize, byteArray);
	::SafeArrayUnaccessData(pSafeArray);
	return result;
}

jbooleanArray WMIService::MakeJavaBoolArrayFromSafeArray(JNIEnv* pJavaEnv, SAFEARRAY* pSafeArray)
{
	this->WriteLog(pJavaEnv, LT_ERROR, L"Boolean arrays not implemented");
	return NULL;
}

jshortArray WMIService::MakeJavaShortArrayFromSafeArray(JNIEnv* pJavaEnv, SAFEARRAY* pSafeArray)
{
	this->WriteLog(pJavaEnv, LT_ERROR, L"Short arrays not implemented");
	return NULL;
}

jintArray WMIService::MakeJavaIntArrayFromSafeArray(JNIEnv* pJavaEnv, SAFEARRAY* pSafeArray)
{
	this->WriteLog(pJavaEnv, LT_ERROR, L"Integer arrays not implemented");
	return NULL;
}

jlongArray WMIService::MakeJavaLongArrayFromSafeArray(JNIEnv* pJavaEnv, SAFEARRAY* pSafeArray)
{
	this->WriteLog(pJavaEnv, LT_ERROR, L"Long arrays not implemented");
	return NULL;
}

jfloatArray WMIService::MakeJavaFloatArrayFromSafeArray(JNIEnv* pJavaEnv, SAFEARRAY* pSafeArray)
{
	this->WriteLog(pJavaEnv, LT_ERROR, L"Float arrays not implemented");
	return NULL;
}

jdoubleArray WMIService::MakeJavaDoubleArrayFromSafeArray(JNIEnv* pJavaEnv, SAFEARRAY* pSafeArray)
{
	this->WriteLog(pJavaEnv, LT_ERROR, L"Double arrays not implemented");
	return NULL;
}

jobjectArray WMIService::MakeJavaObjectArrayFromSafeVector(JNIEnv* pJavaEnv, SAFEARRAY* pSafeArray, JavaType elementType, jclass arrayClass)
{
	jsize arraySize = ::GetSafeArraySize(pSafeArray);
	jobjectArray result = pJavaEnv->NewObjectArray(arraySize, arrayClass, NULL);

	if (elementType == JT_STRING) {
		BSTR HUGEP * pStrings;
		HRESULT hr = ::SafeArrayAccessData(pSafeArray, (void HUGEP* FAR*)&pStrings);
		if (FAILED(hr)) {
			this->WriteLog(pJavaEnv, LT_ERROR, L"Can't access safe array strings data", hr);
			return NULL;
		}
		for (int i = 0; i < arraySize; i++) {
			jstring arrString = MakeJavaString(pJavaEnv, pStrings[i]);
			pJavaEnv->SetObjectArrayElement(result, i, arrString);
			DeleteLocalRef(pJavaEnv, arrString);
		}
		::SafeArrayUnaccessData(pSafeArray);
	} else {
		// Unsupported type
		this->WriteLog(pJavaEnv, LT_ERROR, L"Unsupported object type of safe array");
	}

	return result;
}

/*
JNIEnv* WMIService::AcquireSinkEnv(WMIObjectSink* pSink)
{
	_ASSERT(pJavaVM != NULL);
	if (pJavaVM == NULL) {
		return NULL;
	}
	CComCritSecLock<CComCriticalSection> guard(csSinkThreads);

	DWORD curThreadId = ::GetCurrentThreadId();
	WMIThreadInfo* pCurThread = NULL;
	for (size_t i = 0; i < threadInfos.size(); i++) {
		if (threadInfos[i]->nThreadId == curThreadId) {
			pCurThread = threadInfos[i];
			break;
		}
	}
	if (pCurThread != NULL) {
		ObjectSinkVector::iterator i = std::find(pCurThread->sinks.begin(), pCurThread->sinks.end(), pSink);
		if (i == pCurThread->sinks.end()) {
			pCurThread->sinks.push_back(pSink);
		}
	} else {
		pCurThread = new WMIThreadInfo();
		pCurThread->nThreadId = curThreadId;
		pCurThread->sinks.push_back(pSink);
		pJavaVM->AttachCurrentThread((void**)&pCurThread->pThreadEnv, NULL);
		threadInfos.push_back(pCurThread);
	}

	return pCurThread->pThreadEnv;
}

void WMIService::ReleaseSinkEnv(WMIObjectSink* pSink)
{
	_ASSERT(pJavaVM != NULL);
	if (pJavaVM == NULL) {
		return;
	}
	CComCritSecLock<CComCriticalSection> guard(csSinkThreads);
	// Remove this sink from all threads
	// Detach for current thread only - other ones a dead :(
	DWORD curThreadId = ::GetCurrentThreadId();
	WMIThreadInfo* pCurThread = NULL;


	ThreadInfoVector removeThreads;
	for (ThreadInfoVector::iterator i = threadInfos.begin(); i != threadInfos.end(); i++) {
		ObjectSinkVector::iterator curSink = std::find((*i)->sinks.begin(), (*i)->sinks.end(), pSink);
		if (curSink != (*i)->sinks.end()) {
			(*i)->sinks.erase(curSink);
		}
		if ((*i)->sinks.empty()) {
			if ((*i)->nThreadId == curThreadId) {
				// Detach thread
				pJavaVM->DetachCurrentThread();
			} else {
				// Dunno what to do - we have some thread which is not current
				// Need a way to kill it
				_RPTW0(_CRT_ERROR, L"Can't detach sink thread\n");
			}
			removeThreads.push_back(pCurThread);
		}
	}
	for (ThreadInfoVector::iterator i = removeThreads.begin(); i != removeThreads.end(); i++) {
		removeThreads.erase(
			std::find(threadInfos.begin(), threadInfos.end(), *i));
		delete *i;
	}
}*/

void WMIService::InitStaticState()
{
	csSinkThreads.Init();
}

void WMIService::TermStaticState()
{
	csSinkThreads.Term();
}
