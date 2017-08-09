/*
 *  Copyright 2017 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.fido.example.fidoapiexample;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.fido.u2f.api.common.RegisterRequest;
import com.google.android.gms.fido.u2f.api.common.RegisterRequestParams;
import com.google.android.gms.fido.u2f.api.common.RegisteredKey;
import com.google.android.gms.fido.u2f.api.common.SignRequestParams;
import com.google.android.gms.fido.u2f.api.common.RegisterResponseData;
import com.google.android.gms.fido.u2f.api.common.SignResponseData;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.extensions.android.json.AndroidJsonFactory;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.google.api.client.googleapis.services.GoogleClientRequestInitializer;
import com.google.u2f.gaedemo.u2fRequestHandler.U2fRequestHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class GAEService {
    private static GAEService gaeService = null;
    private static final String TAG = "GAEService";
    private static final String KEY_KEY_HANDLE = "keyHandle";
    private static final String KEY_SESSION_ID = "sessionId";
    private static final String KEY_PUB_KEY = "public_key";

    private static final String KEY_REQUEST_APP_ID = "appId";
    private static final String KEY_REQUEST_ID = "requestId";
    private static final String KEY_REQUEST_TIMEOUT_SECONDS = "timeoutSeconds";
    private static final String KEY_REQUEST_REGISTER_REQUESTS = "registerRequests";
    private static final String KEY_REQUEST_REGISTERED_KEYS = "registeredKeys";
    private static final String KEY_REQUEST_CHALLENGE = "challenge";
    private static String signSessionId = null;

    private U2fRequestHandler service = null;
    private Context mContext = null;
    private GoogleSignInAccount mGoogleSignInAccount = null;
    private Map<String, String> sessionIds = null;
    private List<Map<String, String>> securityTokens = null;

    public static GAEService getInstance(Context context, GoogleSignInAccount googleSignInAccount) {
        if (gaeService == null) {
            gaeService = new GAEService(context, googleSignInAccount);
        }
        return gaeService;
    }

    private GAEService(Context context, GoogleSignInAccount googleSignInAccount) {
        mContext = context;
        mGoogleSignInAccount = googleSignInAccount;
        sessionIds = new HashMap<>();
        initGAEService();
    }

    public RegisterRequestParams getRegistrationRequest(Boolean allowReregistration) {
        try {
            if (service == null) {
                return null;
            }
            List<String> registerRequestContent =
                    service.getRegistrationRequest(allowReregistration).execute().getItems();
            if (registerRequestContent == null || registerRequestContent.isEmpty()) {
                Log.i(TAG, "registerRequestContent is null or empty");
                return null;
            } else {
                for (String value : registerRequestContent) {
                    Log.i(TAG, "registerRequestContent" + value);
                }
            }
            JSONObject registerRequestJson = new JSONObject(registerRequestContent.get(0));
            signSessionId = registerRequestJson.getString(KEY_SESSION_ID);

            Integer requestId = registerRequestJson.has(KEY_REQUEST_ID) ?
                    registerRequestJson.getInt(KEY_REQUEST_ID) : null;

            Double timeoutSeconds = registerRequestJson.has(KEY_REQUEST_TIMEOUT_SECONDS) ?
                    registerRequestJson.getDouble(KEY_REQUEST_TIMEOUT_SECONDS)
                    : Constants.DEFAULT_TIMEOUT_SECONDS;

            Uri appId = registerRequestJson.has(KEY_REQUEST_APP_ID) ?
                    Uri.parse(registerRequestJson.getString(KEY_REQUEST_APP_ID)) : null;

            if (!registerRequestJson.has(KEY_REQUEST_REGISTER_REQUESTS)) {
                throw new JSONException("Server provided no list of register requests");
            }
            List<RegisterRequest> registerRequests = parseRegisterRequests(
                    registerRequestJson.getJSONArray(KEY_REQUEST_REGISTER_REQUESTS));


            List<RegisteredKey> registeredKeys =
                    registerRequestJson.has(KEY_REQUEST_REGISTERED_KEYS) ?
                            parseRegisteredKeys(registerRequestJson
                                    .getJSONArray(KEY_REQUEST_REGISTERED_KEYS)) : null;

            RegisterRequestParams registerRequestParams = new RegisterRequestParams.Builder()
                    .setAppId(appId)
                    .setRegisterRequests(registerRequests)
                    .setRegisteredKeys(registeredKeys)
                    .setRequestId(requestId)
                    .setTimeoutSeconds(timeoutSeconds)
                    .build();
            return registerRequestParams;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getRegisterResponseFromServer(RegisterResponseData registerResponseData) {
        try {
            if (service == null) {
                return null;
            }

            JSONObject registerResponseJson = registerResponseData.toJsonObject();
            registerResponseJson.put(KEY_SESSION_ID, signSessionId);
            List<String> registerResponseContent = service.processRegistrationResponse(
                    registerResponseJson.toString())
                    .execute().getItems();
            if (registerResponseContent == null || registerResponseContent.isEmpty()) {
                Log.i(TAG, "registerResponseContent is null or empty");
            } else {
                Log.i(TAG, "registerResponseContent size is " + registerResponseContent.size());
                for (String value : registerResponseContent) {
                    Log.i(TAG, "registerResponseContent " + value);
                }

                JSONObject token = new JSONObject(registerResponseContent.get(0));
                // return public key value of the authenticated token
                return token.toString();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public SignRequestParams getSignRequest() {
        try {
            if (service == null) {
                return null;
            }
            List<String> signRequestContent = service.getSignRequest().execute().getItems();
            if (signRequestContent == null || signRequestContent.isEmpty()) {
                Log.i(TAG, "signRequestContent is empty");
                return null;
            } else {
                Log.i(TAG, "signRequestContent size is " + signRequestContent.size());
                for (String value : signRequestContent) {
                    Log.i(TAG, "signRequestContent " + value);
                }
            }
            JSONObject signRequestJson = new JSONObject(signRequestContent.get(0));

            Integer requestId = signRequestJson.has(KEY_REQUEST_ID) ?
                    signRequestJson.getInt(KEY_REQUEST_ID) : null;

            // from u2fdemo.js, if default timeout not available, assign 30
            Double timeoutSeconds = signRequestJson.has(KEY_REQUEST_TIMEOUT_SECONDS) ?
                    signRequestJson.getDouble(KEY_REQUEST_TIMEOUT_SECONDS)
                    : Constants.DEFAULT_TIMEOUT_SECONDS;

            Uri appId = signRequestJson.has(KEY_REQUEST_APP_ID) ?
                    Uri.parse(signRequestJson.getString(KEY_REQUEST_APP_ID)) : null;
            byte[] defaultChallenge = signRequestJson.has(KEY_REQUEST_CHALLENGE) ?
                    Base64.decode(signRequestJson.getString(KEY_REQUEST_CHALLENGE),
                            Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP) : null;

            // save sessionId for each KeyHandle, to be retrieved after authN
            // pass back to server
            JSONArray registeredKeys = signRequestJson.getJSONArray(KEY_REQUEST_REGISTERED_KEYS);
            for (int i = 0; i < registeredKeys.length(); i++) {
                JSONObject registeredKey = registeredKeys.getJSONObject(i);
                sessionIds.put(registeredKey.getString(KEY_KEY_HANDLE),
                        registeredKey.remove(KEY_SESSION_ID).toString());
            }

            List<RegisteredKey> registeredKeyList = parseRegisteredKeys(registeredKeys);

            SignRequestParams signRequestParams = new SignRequestParams.Builder()
                    .setAppId(appId)
                    .setDefaultSignChallenge(defaultChallenge)
                    .setRegisteredKeys(registeredKeyList)
                    .setTimeoutSeconds(timeoutSeconds)
                    .setRequestId(requestId)
                    .build();
            return signRequestParams;
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getSignResponseFromServer(SignResponseData signResponseData) {
        try {
            if (service == null) {
                return null;
            }


            Log.i(TAG, "SignResponseData\n" + signResponseData.toJsonObject());
            // insert sessionId for the authenticated KeyHandle into resultData json
            // pass back to server
            JSONObject signResponseJson = signResponseData.toJsonObject();
            String keyHandle = signResponseJson.getString(KEY_KEY_HANDLE);
            String sessionId = sessionIds.get(keyHandle);
            signResponseJson.put(KEY_SESSION_ID, sessionId);

            List<String> signResponseContent = service.processSignResponse(
                    signResponseJson.toString()).execute().getItems();
            if (signResponseContent == null || signResponseContent.isEmpty()) {
                Log.i(TAG, "signResponseContent is null or empty");
            } else {
                Log.i(TAG, "signResponseContent size is " + signResponseContent.size());
                for (String value : signResponseContent) {
                    Log.i(TAG, "signResponseContent " + value);
                }
                JSONObject token = new JSONObject(signResponseContent.get(0));
                // return public key value of the authenticated token
                return token.getString(KEY_PUB_KEY);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<Map<String, String>> getAllSecurityTokens() {
        try {
            if (service == null) {
                return new ArrayList<>();
            }
            List<String> securityKeyResponse = service.getAllSecurityKeys().execute().getItems();
            if (securityKeyResponse == null || securityKeyResponse.isEmpty()) {
                Log.i(TAG, "securityKeyResponse is null or empty");
            } else {
                Log.i(TAG, "securityKeyResponse size is " + securityKeyResponse.size());
                for (String value : securityKeyResponse) {
                    Log.i(TAG, "securityKeyResponse " + value);
                }
                securityTokens = new ArrayList<Map<String, String>>();
                JSONArray tokenJsonArray = new JSONArray(securityKeyResponse.get(0));
                for (int i = 0; i < tokenJsonArray.length(); i++) {
                    Map<String, String> tokenContent = new HashMap<String, String>();
                    JSONObject tokenJson = tokenJsonArray.getJSONObject(i);
                    Iterator<String> keys = tokenJson.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        tokenContent.put(key, tokenJson.getString(key));
                    }
                    securityTokens.add(tokenContent);
                }
                return securityTokens;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String removeSecurityKey(String publicKey) {
        try {
            if (service == null) {
                return null;
            }
            List<String> removeKeyResponse =
                    service.removeSecurityKey(publicKey).execute().getItems();
            if (removeKeyResponse == null || removeKeyResponse.isEmpty()) {
                Log.i(TAG, "removeKeyResponse is null or empty");
            } else {
                Log.i(TAG, "removeKeyResponse size is " + removeKeyResponse.size());
                for (String value : removeKeyResponse) {
                    Log.i(TAG, "removeKeyResponse " + value);
                }
                return removeKeyResponse.get(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void initGAEService() {
        if (service != null) {
            return;
        }
        if (mGoogleSignInAccount == null) {
            return;
        }
        GoogleAccountCredential credential = GoogleAccountCredential.usingAudience(mContext,
                "server:client_id:" + Constants.SERVER_CLIENT_ID);
        credential.setSelectedAccountName(mGoogleSignInAccount.getEmail());
        Log.d(TAG, "credential account name" + credential.getSelectedAccountName());
        U2fRequestHandler.Builder builder = new U2fRequestHandler.Builder(
                AndroidHttp.newCompatibleTransport(),
                new AndroidJsonFactory(), credential)
                .setGoogleClientRequestInitializer(new GoogleClientRequestInitializer() {
                    @Override
                    public void initialize(
                            AbstractGoogleClientRequest<?> abstractGoogleClientRequest)
                            throws IOException {
                        abstractGoogleClientRequest.setDisableGZipContent(true);
                    }
                });
        service = builder.build();
    }

    private static List<RegisterRequest> parseRegisterRequests(JSONArray registerRequestsJson)
            throws JSONException {
        ArrayList<RegisterRequest> registerRequests = new ArrayList<RegisterRequest>();
        for (int i = 0; i < registerRequestsJson.length(); i++) {
            JSONObject registerRequestJson = registerRequestsJson.getJSONObject(i);
            RegisterRequest registerRequest = RegisterRequest.parseFromJson(registerRequestJson);
            registerRequests.add(registerRequest);
        }
        return registerRequests;
    }

    private static List<RegisteredKey> parseRegisteredKeys(JSONArray registeredKeysJson)
            throws JSONException {
        List<RegisteredKey> registeredKeys = new ArrayList<RegisteredKey>();
        for (int i = 0; i < registeredKeysJson.length(); i++) {
            JSONObject registeredKeyJson = registeredKeysJson.getJSONObject(i);
            registeredKeys.add(RegisteredKey.parseFromJson(registeredKeyJson));
        }
        return registeredKeys;
    }
}
