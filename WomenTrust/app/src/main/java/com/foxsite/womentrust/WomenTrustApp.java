package com.foxsite.womentrust;

import android.app.Application;

import com.foxsite.womentrust.api_services.RetrofitClient;
import com.foxsite.womentrust.app_utills.SessionManager;

public class WomenTrustApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        SessionManager.init(this);
        RetrofitClient.init(this);
    }
}
