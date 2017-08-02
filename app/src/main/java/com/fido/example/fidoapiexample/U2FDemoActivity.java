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

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.NavigationView;
import android.support.multidex.MultiDex;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.fido.example.fidoapiexample.R;
import com.fido.example.fidoapiexample.utils.SecurityTokenAdapter;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.fido.Fido;
import com.google.android.gms.fido.u2f.U2fApiClient;
import com.google.android.gms.fido.u2f.U2fPendingIntent;
import com.google.android.gms.fido.u2f.api.common.RegisterRequestParams;
import com.google.android.gms.fido.u2f.api.common.ResponseData;
import com.google.android.gms.fido.u2f.api.common.SignRequestParams;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.fido.u2f.api.common.ErrorResponseData;
import com.google.android.gms.fido.u2f.api.common.RegisterResponseData;
import com.google.android.gms.fido.u2f.api.common.SignResponseData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class U2FDemoActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener,
        GoogleApiClient.OnConnectionFailedListener, View.OnClickListener {
    private final static String TAG = "U2FDemoActivity";
    private static final String KEY_RESULT_DATA = "resultData";
    private static final String KEY_PUB_KEY = "public_key";

    private static final int RC_SIGN_IN = 9001;
    private static final int REQUEST_CODE_REGISTER = 0;
    private static final int REQUEST_CODE_SIGN = 1;
    private static final int GET_ACCOUNTS_PERMISSIONS_REQUEST_REGISTER = 0x11;
    private static final int GET_ACCOUNTS_PERMISSIONS_REQUEST_SIGN = 0x13;
    private static final int GET_ACCOUNTS_PERMISSIONS_ALL_TOKENS = 0x15;

    private ProgressBar mProgressBar;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView mRecyclerView = null;
    private SecurityTokenAdapter mAdapter = null;
    private List<Map<String, String>> securityTokens = null;
    private SignInButton mSignInButton;

    private TextView mUserEmailTextView;
    private TextView mDisplayNameTextView;
    private MenuItem mU2fOperationMenuItem;
    private MenuItem mSignInMenuItem;
    private MenuItem mSignOutMenuItem;

    private GoogleApiClient mGoogleApiClient;
    private GAEService gaeService = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigation);

        // START Google sign in API client
        // configure sign-in to request user info
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken(Constants.SERVER_CLIENT_ID)
                .requestServerAuthCode(Constants.SERVER_CLIENT_ID)
                .build();

        // build client with access to Google Sign-In API and the options specified by gso
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this /* FragmentActivity */, this /* OnConnectionFailedListener */)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();
        // END Google sign in API client

        // START prepare main layout
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_container);
        mSwipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.colorAccent));
        mSwipeRefreshLayout.setRefreshing(true);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                updateAndDisplayRegisteredKeys();
            }
        });

        mRecyclerView = (RecyclerView) findViewById(R.id.list);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new SecurityTokenAdapter(new ArrayList<Map<String, String>>(),
                R.layout.row_token, U2FDemoActivity.this);
        // END prepare main layout

        // START prepare drawer layout
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this,
                drawer,
                toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();
        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setItemIconTintList(null);
        View header = navigationView.getHeaderView(0);
        mUserEmailTextView = (TextView) header.findViewById(R.id.userEmail);
        mDisplayNameTextView = (TextView) header.findViewById(R.id.displayName);
        Menu menu = navigationView.getMenu();
        mU2fOperationMenuItem = menu.findItem(R.id.nav_u2fOperations);
        mSignInMenuItem = menu.findItem(R.id.nav_signin);
        mSignOutMenuItem = menu.findItem(R.id.nav_signout);
        mSignInButton = (SignInButton) findViewById(R.id.sign_in_button);
        mSignInButton.setSize(SignInButton.SIZE_WIDE);
        mSignInButton.setScopes(gso.getScopeArray());
        mSignInButton.setOnClickListener(this);
        // END prepare drawer layout

        // request SignIn or load registered tokens
        updateUI();
    }

    /**
     * Show SignIn button to request user sign in or display all registered security tokens
     */
    private void updateUI() {
        // temporarily depend on the availability of account name in shared preference
        // TODO checking auth token status to decide login status
        String signedInAccount = getAccountEmailFromPref();
        if (signedInAccount == null || signedInAccount.isEmpty()) {
            mSignInButton.setVisibility(View.VISIBLE);
            mUserEmailTextView.setText("");
            mDisplayNameTextView.setText("");
            mU2fOperationMenuItem.setVisible(false);
            mSignInMenuItem.setVisible(true);
            mSignOutMenuItem.setVisible(false);
            mSwipeRefreshLayout.setVisibility(View.GONE);
            mProgressBar.setVisibility(View.GONE);
        } else {
            mSwipeRefreshLayout.setVisibility(View.VISIBLE);
            mUserEmailTextView.setText(getAccountEmailFromPref());
            mDisplayNameTextView.setText(getDisplayNameFromPref());
            mU2fOperationMenuItem.setVisible(true);
            mSignInMenuItem.setVisible(false);
            mSignOutMenuItem.setVisible(true);
            updateAndDisplayRegisteredKeys();
            mSignInButton.setVisibility(View.GONE);
        }
    }

    private void displayRegisteredKeys() {
        mAdapter.clearSecurityTokens();
        mAdapter.addSecurityToken(securityTokens);
        mRecyclerView.setAdapter(mAdapter);
        mSwipeRefreshLayout.setRefreshing(false);
        mProgressBar.setVisibility(View.GONE);
    }

    private void getRegisterRequest() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.GET_ACCOUNTS)
                == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "getRegisterRequest permission is granted");
            new GetRegisterRequestAsyncTask().execute(false);
        } else {
            Log.i(TAG, "getRegisterRequest permission is requested");
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.GET_ACCOUNTS},
                    GET_ACCOUNTS_PERMISSIONS_REQUEST_REGISTER);
        }
    }

    private void sendRegisterRequestToClient(RegisterRequestParams registerRequestParams) {
        U2fApiClient u2fApiClient = Fido.getU2fApiClient(this.getApplicationContext());

        Task<U2fPendingIntent> result = u2fApiClient.getRegisterIntent(registerRequestParams);

        result.addOnSuccessListener(new OnSuccessListener<U2fPendingIntent>() {
            @Override
            public void onSuccess(U2fPendingIntent u2fPendingIntent) {
                if (u2fPendingIntent.hasPendingIntent()) {
                    try {
                        u2fPendingIntent
                                .launchPendingIntent(U2FDemoActivity.this, REQUEST_CODE_REGISTER);
                        Log.i(TAG, "register_request is sent out");
                    } catch (IntentSender.SendIntentException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void updateRegisterResponseToServer(RegisterResponseData registerResponseData) {
        /* assume this operation can only happen within short time after getRegisterRequest,
           which has already checked permission
         */
        new UpdateRegisterResponseToServerAsyncTask().execute(registerResponseData);
    }

    private void getSignRequest() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.GET_ACCOUNTS)
                == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "getSignRequest permission is granted");
            new GetSignRequestAsyncTask().execute();
        } else {
            Log.i(TAG, "getSignRequest permission is requested");
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.GET_ACCOUNTS},
                    GET_ACCOUNTS_PERMISSIONS_REQUEST_SIGN);
        }
    }

    private void sendSignRequestToClient(SignRequestParams signRequestParams) {
        U2fApiClient u2fApiClient = Fido.getU2fApiClient(this.getApplicationContext());

        Task<U2fPendingIntent> result = u2fApiClient.getSignIntent(signRequestParams);

        result.addOnSuccessListener(new OnSuccessListener<U2fPendingIntent>() {
            @Override
            public void onSuccess(U2fPendingIntent u2fPendingIntent) {
                if (u2fPendingIntent.hasPendingIntent()) {
                    try {
                        u2fPendingIntent
                                .launchPendingIntent(U2FDemoActivity.this, REQUEST_CODE_SIGN);
                    } catch (IntentSender.SendIntentException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void updateSignResponseToServer(SignResponseData signResponseData) {
        /* assume this operation can only happen within short time after getSignRequest,
           which has already checked permission
         */
        new UpdateSignResponseToServerAsyncTask().execute(signResponseData);
    }

    private void updateAndDisplayRegisteredKeys() {
        mProgressBar.setVisibility(View.VISIBLE);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.GET_ACCOUNTS)
                == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "updateAndDisplayRegisteredKeys permission is granted");
            new RefreshSecurityKeysAsyncTask().execute();
        } else {
            Log.i(TAG, "updateAndDisplayRegisteredKeys permission is requested");
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.GET_ACCOUNTS},
                    GET_ACCOUNTS_PERMISSIONS_ALL_TOKENS);
        }
    }

    public void removeTokenByIndexInList(int whichToken) {
        /* assume this operation can only happen within short time after
           updateAndDisplayRegisteredKeys, which has already checked permission
         */
        new RemoveSecurityKeyAsyncTask().execute(whichToken);
    }

    class GetRegisterRequestAsyncTask extends AsyncTask<Boolean, Void, RegisterRequestParams> {
        @Override
        protected RegisterRequestParams doInBackground(Boolean... params) {
            gaeService = GAEService.getInstance(U2FDemoActivity.this);
            return gaeService.getRegistrationRequest(params[0]);
        }

        @Override
        protected void onPostExecute(RegisterRequestParams registerRequest) {
            if (registerRequest == null) {
                Log.d(TAG, "registerRequest is null");
                return;
            }
            sendRegisterRequestToClient(registerRequest);
        }
    }

    class UpdateRegisterResponseToServerAsyncTask
            extends AsyncTask<RegisterResponseData, Void, String> {

        @Override
        protected String doInBackground(RegisterResponseData... params) {
            gaeService = GAEService.getInstance(U2FDemoActivity.this);
            return gaeService.getRegisterResponseFromServer(params[0]);
        }

        @Override
        protected void onPostExecute(String securityKeyToken) {
            if (securityKeyToken == null) {
                Toast.makeText(
                        U2FDemoActivity.this,
                        "security key registration failed",
                        Toast.LENGTH_SHORT)
                        .show();
                return;
            }
            updateAndDisplayRegisteredKeys();
            Log.i(TAG, "UpdateRegisterResponseToServerAsyncTask securityKeyToken: " + securityKeyToken);
        }
    }

    class GetSignRequestAsyncTask extends AsyncTask<Void, Void, SignRequestParams> {
        @Override
        protected SignRequestParams doInBackground(Void... params) {
            gaeService = GAEService.getInstance(U2FDemoActivity.this);
            return gaeService.getSignRequest();
        }

        @Override
        protected void onPostExecute(SignRequestParams signRequest) {
            if (signRequest == null) {
                Log.i(TAG, "signRequest is null");
                return;
            }
            sendSignRequestToClient(signRequest);
        }
    }

    class UpdateSignResponseToServerAsyncTask extends AsyncTask<SignResponseData, Void, String> {
        @Override
        protected String doInBackground(SignResponseData... params) {
            gaeService = GAEService.getInstance(U2FDemoActivity.this);
            return gaeService.getSignResponseFromServer(params[0]);
        }

        @Override
        protected void onPostExecute(String pubKey) {
            if (pubKey == null) {
                Toast.makeText(
                        U2FDemoActivity.this,
                        "this security key has not been registered!",
                        Toast.LENGTH_SHORT).
                        show();
                return;
            }
            Log.i(TAG, "authenticated key's pub_key is " + pubKey);
            highlightAuthenticatedToken(pubKey);
        }
    }

    private void highlightAuthenticatedToken(String pubKey) {
        int whichToken = -1;
        Log.i(TAG, "UpdateSignResponseToServerAsyncTask authenticated public_key: " + pubKey);
        for (int position = 0; position < securityTokens.size(); position++) {
            Map<String, String> tokenMap = securityTokens.get(position);
            Log.i(TAG, "highlightAuthenticatedToken registered public_key: "
                    + tokenMap.get(KEY_PUB_KEY));
            if (pubKey.equals(tokenMap.get(KEY_PUB_KEY))) {
                whichToken = position;
                break;
            }
        }
        if (whichToken >= 0) {
            Log.i(TAG, "highlightAuthenticatedToken whichToken: " + whichToken);
            View card = mRecyclerView.getLayoutManager().findViewByPosition(whichToken)
                    .findViewById(R.id.tokenValue);
            card.setPressed(true);
            card.setPressed(false);
        }
    }

    class RefreshSecurityKeysAsyncTask extends AsyncTask<Void, Void, List<Map<String, String>>> {
        @Override
        protected List<Map<String, String>> doInBackground(Void... params) {
            gaeService = GAEService.getInstance(U2FDemoActivity.this);
            return gaeService.getAllSecurityTokens();
        }

        @Override
        protected void onPostExecute(List<Map<String, String>> tokens) {
            securityTokens = tokens;
            mAdapter.clearSecurityTokens();
            mAdapter.addSecurityToken(securityTokens);
            displayRegisteredKeys();
        }
    }

    class RemoveSecurityKeyAsyncTask extends AsyncTask<Integer, Void, String> {
        @Override
        protected String doInBackground(Integer... params) {
            gaeService = GAEService.getInstance(U2FDemoActivity.this);
            int tokenPositionInList = params[0];
            return gaeService.removeSecurityKey(securityTokens.get(tokenPositionInList)
                    .get(KEY_PUB_KEY));
        }

        @Override
        protected void onPostExecute(String removeKeyResponse) {
            updateAndDisplayRegisteredKeys();
        }
    }

    private void signIn() {
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    private void signOut() {
        Auth.GoogleSignInApi.signOut(mGoogleApiClient).setResultCallback(
                new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        clearAccountInfoFromPref();
                        updateUI();
                        gaeService = null;
                    }
                });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (RC_SIGN_IN == requestCode) {
            GoogleSignInResult siginInResult =
                    Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            handleSignInResult(siginInResult);
            return;
        }

        switch(resultCode) {
            case RESULT_OK:
                ResponseData responseData = data.getParcelableExtra(Fido.KEY_RESPONSE_EXTRA);

                if (responseData instanceof ErrorResponseData) {
                    Toast.makeText(
                            U2FDemoActivity.this,
                            "Operation failed\n"+ responseData,
                            Toast.LENGTH_SHORT)
                            .show();
                    break;
                }

                if (requestCode == REQUEST_CODE_REGISTER) {
                    Toast.makeText(
                            U2FDemoActivity.this,
                            "Registration result:\n" + responseData.toJsonObject(),
                            Toast.LENGTH_SHORT)
                            .show();
                    updateRegisterResponseToServer((RegisterResponseData) responseData);
                } else if (requestCode == REQUEST_CODE_SIGN) {
                    Toast.makeText(
                            U2FDemoActivity.this,
                            "Sign result:\n" + responseData.toJsonObject(),
                            Toast.LENGTH_SHORT)
                            .show();
                    updateSignResponseToServer((SignResponseData) responseData);
                }
                break;

            case RESULT_CANCELED:
                Toast.makeText(
                        U2FDemoActivity.this,
                        "Operation is cancelled",
                        Toast.LENGTH_SHORT)
                        .show();
                break;

            default:
                Toast.makeText(
                        U2FDemoActivity.this,
                        "Operation failed, with resultCode " + resultCode,
                        Toast.LENGTH_SHORT)
                        .show();
                break;
        }

        /*
        String result = "resultCode: " + resultCode;
        String u2fResultData = null;
        if (data.hasExtra(KEY_RESULT_DATA)) {
            u2fResultData = data.getStringExtra(KEY_RESULT_DATA);
            result += "; "
                    + KEY_RESULT_DATA + ":  " + u2fResultData;
            Log.d(TAG, result);
        }
        switch (requestCode) {
            case RC_SIGN_IN:
                GoogleSignInResult siginInResult =
                        Auth.GoogleSignInApi.getSignInResultFromIntent(data);
                handleSignInResult(siginInResult);
                break;
            case REQUEST_CODE_REGISTER:
                if (u2fResultData == null) {
                    Toast.makeText(
                            U2FDemoActivity.this,
                            "Registration is cancelled",
                            Toast.LENGTH_SHORT)
                            .show();
                    break;
                }
                updateRegisterResponseToServer(u2fResultData);
                break;
            case REQUEST_CODE_SIGN:
                if (u2fResultData == null) {
                    Toast.makeText(
                            U2FDemoActivity.this,
                            "Authentication is cancelled",
                            Toast.LENGTH_SHORT)
                            .show();
                    break;
                }
                updateSignResponseToServer(u2fResultData);
                break;
        }*/
    }

    private void handleSignInResult(GoogleSignInResult result) {
        Log.d(TAG, "handleSignInResult:" + result.isSuccess());
        if (result.isSuccess()) {
            GoogleSignInAccount acct = result.getSignInAccount();
            saveAccountInfoToPref(acct.getEmail(), acct.getDisplayName());
            Log.d(TAG, "account email" + acct.getEmail());
            Log.d(TAG, "account displayName" + acct.getDisplayName());
            Log.d(TAG, "account id" + acct.getId());
            Log.d(TAG, "account idToken" + acct.getIdToken());
            Log.d(TAG, "account scopes" + acct.getGrantedScopes());
        } else {
            clearAccountInfoFromPref();
        }
        updateUI();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case GET_ACCOUNTS_PERMISSIONS_REQUEST_REGISTER:
                Log.d(TAG, "onRequestPermissionsResult");
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getRegisterRequest();
                }
                return;
            case GET_ACCOUNTS_PERMISSIONS_REQUEST_SIGN:
                getSignRequest();
                return;
            case GET_ACCOUNTS_PERMISSIONS_ALL_TOKENS:
                updateAndDisplayRegisteredKeys();
                return;
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.sign_in_button:
                signIn();
                break;
        }
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        switch (item.getItemId()) {
            case R.id.nav_signin:
                signIn();
                break;
            case R.id.nav_signout:
                signOut();
                break;
            case R.id.nav_register:
                getRegisterRequest();
                break;
            case R.id.nav_auth:
                getSignRequest();
                break;
            case R.id.nav_gitbuh:
                Intent browser = new Intent(Intent.ACTION_VIEW,
                        Uri.parse(getString(R.string.github_location)));
                this.startActivity(browser);
                break;
            case R.id.nav_allowReregistration:
                boolean newStatus = !item.isChecked();
                item.setChecked(newStatus);
                return true;
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(TAG, "onConnectionFailed:" + connectionResult);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    private String getAccountEmailFromPref() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        return settings.getString(Constants.PREF_ACCOUNT_NAME, "");
    }

    private String getDisplayNameFromPref() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        return settings.getString(Constants.PREF_DISPLAY_NAME, "");
    }

    private void saveAccountInfoToPref(String email, String displayName) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(Constants.PREF_ACCOUNT_NAME, email);
        Log.d(TAG, "saveAccountInfoToPref email: " + email);
        editor.putString(Constants.PREF_DISPLAY_NAME, displayName);
        Log.d(TAG, "saveAccountInfoToPref displayName: " + displayName);
        editor.commit();
    }

    private void clearAccountInfoFromPref() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = settings.edit();
        editor.remove(Constants.PREF_ACCOUNT_NAME);
        editor.remove(Constants.PREF_DISPLAY_NAME);
        Log.d(TAG, "clearAccountInfoFromPref");
        editor.commit();
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }
}
