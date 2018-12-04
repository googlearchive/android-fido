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
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
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

import com.fido.example.fidoapiexample.utils.SecurityTokenAdapter;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.OptionalPendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.fido.Fido;
import com.google.android.gms.fido.u2f.U2fApiClient;
import com.google.android.gms.fido.u2f.U2fPendingIntent;
import com.google.android.gms.fido.u2f.api.common.ErrorResponseData;
import com.google.android.gms.fido.u2f.api.common.RegisterRequestParams;
import com.google.android.gms.fido.u2f.api.common.RegisterResponseData;
import com.google.android.gms.fido.u2f.api.common.ResponseData;
import com.google.android.gms.fido.u2f.api.common.SignRequestParams;
import com.google.android.gms.fido.u2f.api.common.SignResponseData;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import com.google.android.gms.tasks.Tasks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class U2FDemoActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener,
        GoogleApiClient.OnConnectionFailedListener, View.OnClickListener {
    private final static String TAG = "U2FDemoActivity";
    private static final String KEY_PUB_KEY = "public_key";

    private static final int RC_SIGN_IN = 9001;
    private static final int REQUEST_CODE_REGISTER = 0;
    private static final int REQUEST_CODE_SIGN = 1;
    private static final int GET_ACCOUNTS_PERMISSIONS_REQUEST_REGISTER = 0x11;
    private static final int GET_ACCOUNTS_PERMISSIONS_REQUEST_SIGN = 0x13;
    private static final int GET_ACCOUNTS_PERMISSIONS_ALL_TOKENS = 0x15;

    // Create a new ThreadPoolExecutor with 2 threads for each processor on the
    // device and a 60 second keep-alive time.
    private static final int NUM_CORES = Runtime.getRuntime().availableProcessors();
    private static final ThreadPoolExecutor THREAD_POOL_EXECUTOR =
        new ThreadPoolExecutor(NUM_CORES * 2, NUM_CORES * 2, 60L, TimeUnit.SECONDS,
            new LinkedBlockingDeque<Runnable>(
            ));

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
    private GoogleSignInAccount mGoogleSignInAccount;

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
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mProgressBar = findViewById(R.id.progressBar);

        mSwipeRefreshLayout = findViewById(R.id.swipe_container);
        mSwipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.colorAccent));
        mSwipeRefreshLayout.setRefreshing(true);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                updateAndDisplayRegisteredKeys();
            }
        });

        mRecyclerView = findViewById(R.id.list);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new SecurityTokenAdapter(new ArrayList<Map<String, String>>(),
                R.layout.row_token, U2FDemoActivity.this);
        // END prepare main layout

        // START prepare drawer layout
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this,
                drawer,
                toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();
        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setItemIconTintList(null);
        View header = navigationView.getHeaderView(0);
        mUserEmailTextView = header.findViewById(R.id.userEmail);
        mDisplayNameTextView = header.findViewById(R.id.displayName);
        Menu menu = navigationView.getMenu();
        mU2fOperationMenuItem = menu.findItem(R.id.nav_u2fOperations);
        mSignInMenuItem = menu.findItem(R.id.nav_signin);
        mSignOutMenuItem = menu.findItem(R.id.nav_signout);
        mSignInButton = findViewById(R.id.sign_in_button);
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
        // We check a boolean value in SharedPreferences to determine whether the user has been
        // signed in. This value is false by default. It would be set to true after signing in and
        // would be reset to false after user clicks "Sign out".
        // After the users clicks "Sign out", we couldn't use
        // GoogleSignInApi#silentSignIn(GoogleApiClient), because it silently signs in the user
        // again. Thus, we rely on this boolean value in SharedPreferences.
        if (!getAccountSignInStatus()) {
            displayAccountNotSignedIn();
            return;
        }

        OptionalPendingResult<GoogleSignInResult> pendingResult =
            Auth.GoogleSignInApi.silentSignIn(mGoogleApiClient);
        if (pendingResult.isDone()) {
            // If the user's cached credentials are valid, the OptionalPendingResult will be "done"
            // and the GoogleSignInResult will be available instantly.
            if (pendingResult.get().isSuccess()) {
                mGoogleSignInAccount = pendingResult.get().getSignInAccount();
                displayAccountSignedIn(pendingResult.get().getSignInAccount().getEmail(),
                    pendingResult.get().getSignInAccount().getDisplayName());
            } else {
                displayAccountNotSignedIn();
            }
        } else {
            // If the user has not previously signed in on this device or the sign-in has expired,
            // this asynchronous branch will attempt to sign in the user silently.  Cross-device
            // single sign-on will occur in this branch.
            displayAccountNotSignedIn();

            pendingResult.setResultCallback(new ResultCallback<GoogleSignInResult>() {
                @Override
                public void onResult(@NonNull GoogleSignInResult result) {
                    if (result.isSuccess()) {
                        mGoogleSignInAccount = result.getSignInAccount();
                        displayAccountSignedIn(result.getSignInAccount().getEmail(),
                            result.getSignInAccount().getDisplayName());
                    } else {
                        displayAccountNotSignedIn();
                    }
                }
            });
        }
    }

    private void displayAccountSignedIn(String email, String displayName) {
        mSwipeRefreshLayout.setVisibility(View.VISIBLE);
        mUserEmailTextView.setText(email);
        mDisplayNameTextView.setText(displayName);
        mU2fOperationMenuItem.setVisible(true);
        mSignInMenuItem.setVisible(false);
        mSignOutMenuItem.setVisible(true);
        updateAndDisplayRegisteredKeys();
        mSignInButton.setVisibility(View.GONE);
    }

    private void displayAccountNotSignedIn() {
        mSignInButton.setVisibility(View.VISIBLE);
        mUserEmailTextView.setText("");
        mDisplayNameTextView.setText("");
        mU2fOperationMenuItem.setVisible(false);
        mSignInMenuItem.setVisible(true);
        mSignOutMenuItem.setVisible(false);
        mSwipeRefreshLayout.setVisibility(View.GONE);
        mProgressBar.setVisibility(View.GONE);
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
            Task<RegisterRequestParams> getRegisterRequestTask = asyncGetRegisterRequest(false);
            getRegisterRequestTask.addOnCompleteListener(
                new OnCompleteListener<RegisterRequestParams>() {
                    @Override
                    public void onComplete(@NonNull Task<RegisterRequestParams> task) {
                        RegisterRequestParams registerRequest = task.getResult();
                        if (registerRequest == null) {
                            Log.d(TAG, "registerRequest is null");
                            return;
                        }
                        sendRegisterRequestToClient(registerRequest);
                    }
                });
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
        Task<String> updateRegisterResponseToServerTask =
            asyncUpdateRegisterResponseToServer(registerResponseData);
        updateRegisterResponseToServerTask.addOnCompleteListener(new OnCompleteListener<String>() {
            @Override
            public void onComplete(@NonNull Task<String> task) {
                String securityKeyToken = task.getResult();
                if (securityKeyToken == null) {
                    Toast.makeText(
                        U2FDemoActivity.this,
                        "security key registration failed",
                        Toast.LENGTH_SHORT)
                        .show();
                    return;
                }
                updateAndDisplayRegisteredKeys();
                Log.i(TAG, "Update register response to server with securityKeyToken: "
                    + securityKeyToken);
            }
        });
    }

    private void getSignRequest() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.GET_ACCOUNTS)
                == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "getSignRequest permission is granted");
            Task<SignRequestParams> getSignRequestTask = asyncGetSignRequest();
            getSignRequestTask.addOnCompleteListener(new OnCompleteListener<SignRequestParams>() {
                @Override
                public void onComplete(@NonNull Task<SignRequestParams> task) {
                    SignRequestParams signRequest = task.getResult();
                    if (signRequest == null) {
                        Log.i(TAG, "signRequest is null");
                        return;
                    }
                    sendSignRequestToClient(signRequest);
                }
            });
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
        Task<String> updateSignResponseToServerTask =
            asyncUpdateSignResponseToServer(signResponseData);
        updateSignResponseToServerTask.addOnCompleteListener(new OnCompleteListener<String>() {
            @Override
            public void onComplete(@NonNull Task<String> task) {
                String pubKey = task.getResult();
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
        });
    }

    private void updateAndDisplayRegisteredKeys() {
        mProgressBar.setVisibility(View.VISIBLE);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.GET_ACCOUNTS)
                == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "updateAndDisplayRegisteredKeys permission is granted");
            Task<List<Map<String, String>>> refreshSecurityKeyTask = asyncRefreshSecurityKey();
            refreshSecurityKeyTask.addOnCompleteListener(
                new OnCompleteListener<List<Map<String, String>>>() {
                    @Override
                    public void onComplete(@NonNull Task<List<Map<String, String>>> task) {
                        List<Map<String, String>> tokens = task.getResult();
                        securityTokens = tokens;
                        mAdapter.clearSecurityTokens();
                        mAdapter.addSecurityToken(securityTokens);
                        displayRegisteredKeys();
                    }
                });
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
        Task<String> removeSecurityKeyTask = asyncRemoveSecurityKey(whichToken);
        removeSecurityKeyTask.addOnCompleteListener(new OnCompleteListener<String>() {
            @Override
            public void onComplete(@NonNull Task<String> task) {
                updateAndDisplayRegisteredKeys();
            }
        });
    }

    private Task<RegisterRequestParams> asyncGetRegisterRequest(final Boolean allowReregistration) {
        return Tasks.call(THREAD_POOL_EXECUTOR, new Callable<RegisterRequestParams>() {
            @Override
            public RegisterRequestParams call() throws Exception {
                gaeService = GAEService.getInstance(U2FDemoActivity.this, mGoogleSignInAccount);
                return gaeService.getRegistrationRequest(allowReregistration);
            }
        });
    }

    private Task<String> asyncUpdateRegisterResponseToServer(
        final RegisterResponseData registerResponseData) {
        return Tasks.call(THREAD_POOL_EXECUTOR, new Callable<String>() {
            @Override
            public String call() throws Exception {
                gaeService = GAEService.getInstance(U2FDemoActivity.this, mGoogleSignInAccount);
                return gaeService.getRegisterResponseFromServer(registerResponseData);
            }
        });
    }

    private Task<SignRequestParams> asyncGetSignRequest() {
        return Tasks.call(THREAD_POOL_EXECUTOR, new Callable<SignRequestParams>() {
            @Override
            public SignRequestParams call() throws Exception {
                gaeService = GAEService.getInstance(U2FDemoActivity.this, mGoogleSignInAccount);
                return gaeService.getSignRequest();
            }
        });
    }

    private Task<String> asyncUpdateSignResponseToServer(final SignResponseData signResponseData) {
        return Tasks.call(THREAD_POOL_EXECUTOR, new Callable<String>() {
            @Override
            public String call() throws Exception {
                gaeService = GAEService.getInstance(U2FDemoActivity.this, mGoogleSignInAccount);
                return gaeService.getSignResponseFromServer(signResponseData);
            }
        });
    }

    private void highlightAuthenticatedToken(String pubKey) {
        int whichToken = -1;
        Log.i(TAG, "Successfully authenticated public_key: " + pubKey);
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

    private Task<List<Map<String, String>>> asyncRefreshSecurityKey() {
        return Tasks.call(THREAD_POOL_EXECUTOR, new Callable<List<Map<String, String>>>() {
            @Override
            public List<Map<String, String>> call() {
                gaeService = GAEService.getInstance(U2FDemoActivity.this, mGoogleSignInAccount);
                return gaeService.getAllSecurityTokens();
            }
        });
    }

    private Task<String> asyncRemoveSecurityKey(final int tokenPositionInList) {
        return Tasks.call(THREAD_POOL_EXECUTOR, new Callable<String>() {
            @Override
            public String call() {
                gaeService = GAEService.getInstance(U2FDemoActivity.this, mGoogleSignInAccount);
                return gaeService.removeSecurityKey(securityTokens.get(tokenPositionInList)
                    .get(KEY_PUB_KEY));
            }
        });
    }

    private void signIn() {
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    private void signOut() {
        Auth.GoogleSignInApi.signOut(mGoogleApiClient).setResultCallback(
                new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {
                        Auth.GoogleSignInApi.signOut(mGoogleApiClient);
                        clearAccountSignInStatus();
                        updateUI();
                        gaeService = null;
                    }
                });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (RC_SIGN_IN == requestCode) {
            GoogleSignInResult signInResult =
                    Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            handleSignInResult(signInResult);
            return;
        }

        switch (resultCode) {
            case RESULT_OK:
                ResponseData responseData = data.getParcelableExtra(Fido.KEY_RESPONSE_EXTRA);

                if (responseData instanceof ErrorResponseData) {
                    Toast.makeText(
                            U2FDemoActivity.this,
                            "Operation failed\n" + responseData,
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
    }

    private void handleSignInResult(GoogleSignInResult result) {
        Log.d(TAG, "handleSignInResult:" + result.isSuccess());
        if (result.isSuccess()) {
            GoogleSignInAccount acct = result.getSignInAccount();
            saveAccountSignInStatus();
            Log.d(TAG, "account email" + acct.getEmail());
            Log.d(TAG, "account displayName" + acct.getDisplayName());
            Log.d(TAG, "account id" + acct.getId());
            Log.d(TAG, "account idToken" + acct.getIdToken());
            Log.d(TAG, "account scopes" + acct.getGrantedScopes());
        } else {
            clearAccountSignInStatus();
        }
        updateUI();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
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
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
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
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
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

    private void saveAccountSignInStatus() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(Constants.PREF_SIGNED_IN_STATUS, true);
        Log.d(TAG, "Save account sign in status: true");
        editor.apply();
    }

    private void clearAccountSignInStatus() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(Constants.PREF_SIGNED_IN_STATUS, false);
        Log.d(TAG, "Clear account sign in status");
        editor.apply();
    }

    private boolean getAccountSignInStatus() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        return settings.getBoolean(Constants.PREF_SIGNED_IN_STATUS, false);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }
}
