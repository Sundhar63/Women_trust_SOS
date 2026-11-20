package com.foxsite.womentrust.app_utills;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.foxsite.womentrust.R;

import java.util.Locale;

public class LanguageHelper {

    public static String getLanguageCode(Context context) {
        if (context == null) return "en";

        // 1. Check AppSettings SharedPreferences
        SharedPreferences preferences =
                context.getSharedPreferences(
                        "AppSettings",
                        Context.MODE_PRIVATE
                );

        String code = preferences.getString("language_code", null);
        if (code != null && !code.trim().isEmpty()) {
            return code.trim();
        }

        // 2. Check WomenTrust_Shared
        try {
            SharedPreferences sharedPref = context.getSharedPreferences("WomenTrust_Shared", Context.MODE_PRIVATE);
            code = sharedPref.getString("language_code", null);
            if (code != null && !code.trim().isEmpty()) {
                return code.trim();
            }
        } catch (Exception ignored) {}

        // 3. Check AppCompatDelegate locales
        try {
            LocaleListCompat appLocales = AppCompatDelegate.getApplicationLocales();
            if (appLocales != null && !appLocales.isEmpty()) {
                String tag = appLocales.get(0).getLanguage();
                if ("ta".equalsIgnoreCase(tag)) {
                    return "ta";
                }
            }
        } catch (Exception ignored) {}

        // 4. Check configuration locales
        try {
            String confLang = context.getResources().getConfiguration().getLocales().get(0).getLanguage();
            if ("ta".equalsIgnoreCase(confLang)) {
                return "ta";
            }
        } catch (Exception ignored) {}

        // 5. Check if current active UI resources are in Tamil
        try {
            String submitText = context.getString(R.string.submit);
            if (submitText != null && submitText.contains("சமர்ப்பிக்க")) {
                return "ta";
            }
            String titleText = context.getString(R.string.domestic_violence);
            if (titleText != null && titleText.contains("குடும்ப")) {
                return "ta";
            }
        } catch (Exception ignored) {}

        return "en";
    }

    public static void setLocale(Context context, String languageCode) {
        if (context == null) return;
        if (languageCode == null || languageCode.trim().isEmpty()) {
            languageCode = "en";
        }

        SharedPreferences preferences =
                context.getSharedPreferences(
                        "AppSettings",
                        Context.MODE_PRIVATE
                );

        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("language_code", languageCode);

        if ("ta".equalsIgnoreCase(languageCode)) {
            editor.putString("language_name", "Tamil");
        } else {
            editor.putString("language_name", "English");
        }
        editor.apply();

        try {
            SharedPreferences sharedPref = context.getSharedPreferences("WomenTrust_Shared", Context.MODE_PRIVATE);
            sharedPref.edit().putString("language_code", languageCode).apply();
        } catch (Exception ignored) {}

        LocaleListCompat appLocale = LocaleListCompat.forLanguageTags(languageCode);
        AppCompatDelegate.setApplicationLocales(appLocale);

        try {
            Locale locale = new Locale(languageCode);
            Locale.setDefault(locale);
            Configuration config = new Configuration(context.getResources().getConfiguration());
            config.setLocale(locale);
            context.getResources().updateConfiguration(config, context.getResources().getDisplayMetrics());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}