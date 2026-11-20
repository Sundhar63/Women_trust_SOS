 package com.foxsite.womentrust;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;

import androidx.appcompat.app.AppCompatActivity;

import com.foxsite.womentrust.login.LoginActivity;

 public class SplashScreen extends AppCompatActivity {

     private final int SPLASH_DISPLAY_LENGTH = 2000;

     SharedPreferences sharedPreferences;
     Boolean LOGINSTATUS;
     String user_id;
     String usertype;

     @Override
     protected void onCreate(Bundle savedInstanceState) {
         super.onCreate(savedInstanceState);
         setContentView(R.layout.activity_splash_screen);
         setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

         sharedPreferences = getSharedPreferences(getString(R.string.sharedpref_name), Context.MODE_PRIVATE);
         LOGINSTATUS = sharedPreferences.getBoolean(getString(R.string.login_status), false);
         user_id = sharedPreferences.getString(getString(R.string.userId), null);
         usertype = sharedPreferences.getString(getString(R.string.role), null);


         new Handler().postDelayed(new Runnable() {
             @Override
             public void run() {

                 if (LOGINSTATUS) {
                     Intent mainIntent = new Intent(SplashScreen.this, MainActivity.class);
                     startActivity(mainIntent);
                     overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                     finish();
                 } else {
                     Intent mainIntent = new Intent(SplashScreen.this, LanguageSelectActivity.class);
                     startActivity(mainIntent);
                     overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                     finish();
                 }
             }
         }, SPLASH_DISPLAY_LENGTH);

     }
 }
