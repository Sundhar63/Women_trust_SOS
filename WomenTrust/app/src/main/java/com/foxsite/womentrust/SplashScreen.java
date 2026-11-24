package com.foxsite.womentrust;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.foxsite.womentrust.api_services.ApiService;
import com.foxsite.womentrust.api_services.AuthRefreshRequest;
import com.foxsite.womentrust.api_services.RetrofitClient;
import com.foxsite.womentrust.app_utills.SessionManager;
import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SplashScreen extends AppCompatActivity {

    private static final int SPLASH_DISPLAY_LENGTH = 2000;

    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        sessionManager = SessionManager.get(this);

        new Handler(Looper.getMainLooper()).postDelayed(this::route, SPLASH_DISPLAY_LENGTH);
    }

    private void route() {
        if (isFinishing()) {
            return;
        }

        android.content.SharedPreferences sharedPreferences =
                getSharedPreferences(getString(R.string.sharedpref_name), MODE_PRIVATE);
        boolean isLoggedIn = sharedPreferences.getBoolean(getString(R.string.login_status), false)
                || sessionManager.hasSession();

        if (isLoggedIn) {
            if (sessionManager.isAccessTokenValid()) {
                goToMain();
            } else {
                refreshSession();
            }
            return;
        }

        android.content.SharedPreferences appSettings = getSharedPreferences("AppSettings", MODE_PRIVATE);
        String lang = appSettings.getString("language_code", "");
        if (lang != null && !lang.isEmpty()) {
            goToLogin();
        } else {
            goToLanguageSelect();
        }
    }

    private void refreshSession() {
        String refreshToken = sessionManager.getRefreshToken();
        if (refreshToken == null || refreshToken.isEmpty()) {
            enterOfflineMode();
            return;
        }

        AuthRefreshRequest request = new AuthRefreshRequest(refreshToken, sessionManager.getDeviceId());
        RetrofitClient.getApiService("").refreshSession(request).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (isFinishing()) {
                    return;
                }
                if (response.isSuccessful() && response.body() != null) {
                    JsonObject body = response.body();
                    String access = RetrofitClient.optString(body, "access_token", "Token");
                    String refresh = RetrofitClient.optString(body, "refresh_token", "RefreshToken");
                    long expiresIn = body.has("expires_in") ? body.get("expires_in").getAsLong() : 1800L;

                    if (access != null && refresh != null) {
                        sessionManager.saveSession(access, refresh, expiresIn);
                        goToMain();
                        return;
                    }
                }

                // If token refresh fails, do not lock out the user: enter offline mode to ensure safety
                enterOfflineMode();
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                if (isFinishing()) {
                    return;
                }
                Log.w("SplashScreen", "Session refresh failed", t);
                enterOfflineMode();
            }
        });
    }

    /**
     * A women's safety app must stay usable without a network connection. The
     * stored refresh token is kept so the next launch can retry the refresh.
     */
    private void enterOfflineMode() {
        android.content.SharedPreferences sp =
                getSharedPreferences(getString(R.string.sharedpref_name), MODE_PRIVATE);
        boolean isLoggedIn = sp.getBoolean(getString(R.string.login_status), false)
                || sessionManager.hasSession();
        if (!isLoggedIn) {
            goToLogin();
            return;
        }
        goToMain();
    }

    private void goToMain() {
        Intent intent = new Intent(SplashScreen.this, MainActivity.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private void goToLogin() {
        Intent intent = new Intent(SplashScreen.this, com.foxsite.womentrust.login.LoginActivity.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private void goToLanguageSelect() {
        Intent intent = new Intent(SplashScreen.this, LanguageSelectActivity.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
