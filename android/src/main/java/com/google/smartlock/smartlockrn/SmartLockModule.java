package com.google.smartlock.smartlockrn;


import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import androidx.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.google.android.gms.auth.api.credentials.Credential;
import com.google.android.gms.auth.api.credentials.CredentialRequest;
import com.google.android.gms.auth.api.credentials.CredentialRequestResponse;
import com.google.android.gms.auth.api.credentials.Credentials;
import com.google.android.gms.auth.api.credentials.CredentialsClient;
import com.google.android.gms.auth.api.credentials.IdToken;
import com.google.android.gms.auth.api.credentials.IdentityProviders;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class SmartLockModule extends ReactContextBaseJavaModule {

    private ReactApplicationContext mContext;
    private Application application;
    private Promise sLPromise;
    private static final int RC_READ = 4;
    private static final int SUCCESS_CODE = -1;
    private static final int CANCEL_CODE = 1001;



    public SmartLockModule(ReactApplicationContext reactContext, Application application) {
        super(reactContext);
        this.mContext = reactContext;
        this.application = application;
    }

    @Override
    public String getName() {
        return "SmartLockRN";
    }


    private final ActivityEventListener mActivityEventListener = new BaseActivityEventListener() {

        @Override
        public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent intent) {
            super.onActivityResult(requestCode, resultCode, intent);

            System.out.println("----------RESULT CODE : "+resultCode+"-----------");

            if (resultCode == SUCCESS_CODE) {
                Credential credential = intent.getParcelableExtra(Credential.EXTRA_KEY);
                if (credential == null) {
                    return;
                }

                String accountType = credential.getAccountType();
                if (requestCode == RC_READ) {
                    handleSuccess(credential);
                } else if (accountType == null) {
                    Log.e("OnActivityResult", "Credential Read: NOT OK");
                    Toast.makeText(getCurrentActivity(), "Credential Read Failed", Toast.LENGTH_SHORT).show();
                    sLPromise.reject("Unable to login","Unable to login");
                }
            } else if (resultCode == CANCEL_CODE) {
                sLPromise.reject("Login cancelled", "Login cancelled");
            } else {
                return;
            }
        }
    };


    @ReactMethod
    public void getCredentials(final Promise promise) {
        this.sLPromise = promise;
        CredentialsClient mCredentialsClient;
        mCredentialsClient = Credentials.getClient(this.mContext);

        CredentialRequest mCredentialRequest = new CredentialRequest.Builder()
                .setPasswordLoginSupported(true)
                .setAccountTypes(IdentityProviders.GOOGLE)
                .build();

        System.out.println(mCredentialsClient.toString());

        mCredentialsClient.request(mCredentialRequest).addOnCompleteListener(
                new OnCompleteListener<CredentialRequestResponse>() {
                    @Override
                    public void onComplete(@NonNull Task<CredentialRequestResponse> task) {
                        if (task.isSuccessful()) {
                            Credential credential = task.getResult().getCredential();
                            handleSuccess(credential);
                            return;
                        }

                        Exception e = task.getException();
                        if (e instanceof ResolvableApiException) {
                            // This is most likely the case where the user has multiple saved
                            // credentials and needs to pick one. This requires showing UI to
                            // resolve the read request.
                            ResolvableApiException rae = (ResolvableApiException) e;
                            Activity activity = getCurrentActivity();
                            if (activity == null) {
                                sLPromise.reject("Activity is null", "Activity is null");
                                return;
                            }
                            resolveResult(rae, RC_READ, activity);
                        } else if (e instanceof ApiException) {
                            // The user must create an account or sign in manually.
                            Log.e("SmartLockModule", "Unsuccessful credential request.", e);
                            System.out.println("The user must create an account or sign in manually.");

                            ApiException ae = (ApiException) e;
                            int code = ae.getStatusCode();
                            sLPromise.reject("API EXCEPTION", "The user must create an account or sign in manually.");
                        }
                    }
                }
        );
    }

    private void resolveResult(ResolvableApiException rae, int requestCode, Activity activity) {
        try {
            rae.startResolutionForResult(activity, requestCode);
            this.mContext.addActivityEventListener(mActivityEventListener);
        } catch (IntentSender.SendIntentException e) {
            Log.e("SMARTLOCKMODULE", "Failed to send resolution.", e);
            sLPromise.reject("INTENT EXCEPTION", "Failed to send resolution.");
        }
    }

    private void handleSuccess(Credential credential) {
        try {
            JSONObject obj = new JSONObject();

            String name = credential.getName();
            String id = credential.getId();
            List<IdToken> tokens = credential.getIdTokens();


            obj.put("name", name);
            obj.put("id", id);
            obj.put("tokens", tokens.toString());

            sLPromise.resolve(obj.toString());
        } catch (JSONException e) {
            e.printStackTrace();
            sLPromise.reject("err",e.getMessage());
        }

    }
}
