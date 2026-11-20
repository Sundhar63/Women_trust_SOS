package com.foxsite.womentrust;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.cardview.widget.CardView;
import androidx.core.os.LocaleListCompat;

import com.foxsite.womentrust.login.LoginActivity;

public class LanguageSelectActivity extends AppCompatActivity {

    CardView cardEnglish, cardTamil;
    TextView txtEnglish, txtTamil;

    String selectedLanguage = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_language_select);

        cardEnglish = findViewById(R.id.cardEnglish);
        cardTamil = findViewById(R.id.cardTamil);

        txtEnglish = findViewById(R.id.txtEnglish);
        txtTamil = findViewById(R.id.txtTamil);

        /// ENGLISH CLICK
        cardEnglish.setOnClickListener(v -> {
            selectedLanguage = "en";
            saveLanguage(selectedLanguage);

            Intent intent = new Intent(
                    LanguageSelectActivity.this,
                    LoginActivity.class
            );
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });

        /// TAMIL CLICK
        cardTamil.setOnClickListener(v -> {
            selectedLanguage = "ta";
            saveLanguage(selectedLanguage);

            Intent intent = new Intent(
                    LanguageSelectActivity.this,
                    LoginActivity.class
            );
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });
    }

    /// SAVE LANGUAGE
    private void saveLanguage(String languageCode) {

        SharedPreferences preferences =
                getSharedPreferences("AppSettings", MODE_PRIVATE);

        SharedPreferences.Editor editor = preferences.edit();

        editor.putString("language_code", languageCode);

        if (languageCode.equals("en")) {
            editor.putString("language_name", "English");
        } else {
            editor.putString("language_name", "Tamil");
        }

        editor.apply();

        // Create a LocaleListCompat with the specified language
        LocaleListCompat appLocale = LocaleListCompat.forLanguageTags(languageCode);

        // Update the app's locales
        AppCompatDelegate.setApplicationLocales(appLocale);
    }
}