package com.foxsite.womentrust.app_utills;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Stores the session tokens encrypted with a key that never leaves the Android Keystore.
 *
 * Only the access token, refresh token and device id live here. Passwords, Aadhaar
 * numbers and other personal data must never be written to this store.
 */
public final class SessionManager {

    private static final String TAG = "SessionManager";
    private static final String PREFS_NAME = "auth_secure";
    private static final String KEY_ALIAS = "womentrust_session_key";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;

    private static final String P_ACCESS = "access_token";
    private static final String P_REFRESH = "refresh_token";
    private static final String P_EXPIRES_AT = "access_expires_at";

    private static final String DEVICE_PREFS = "device_info";
    private static final String P_DEVICE_ID = "device_id";

    private static volatile SessionManager instance;

    private final Context appContext;
    private final SharedPreferences prefs;

    private SessionManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void init(Context context) {
        if (instance == null) {
            synchronized (SessionManager.class) {
                if (instance == null) {
                    instance = new SessionManager(context);
                }
            }
        }
    }

    public static SessionManager get(Context context) {
        init(context);
        return instance;
    }

    public static SessionManager get() {
        if (instance == null) {
            throw new IllegalStateException("SessionManager.init(context) was not called");
        }
        return instance;
    }

    // ---------------------------------------------------------------- session

    public void saveSession(String accessToken, String refreshToken, long expiresInSeconds) {
        long expiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000L);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(P_ACCESS, encrypt(accessToken));
        editor.putString(P_REFRESH, encrypt(refreshToken));
        editor.putLong(P_EXPIRES_AT, expiresAt);
        editor.apply();
    }

    public String getAccessToken() {
        return decrypt(prefs.getString(P_ACCESS, null));
    }

    public String getRefreshToken() {
        return decrypt(prefs.getString(P_REFRESH, null));
    }

    public long getAccessTokenExpiresAt() {
        return prefs.getLong(P_EXPIRES_AT, 0L);
    }

    /** True while the access token is still usable (60 second safety margin). */
    public boolean isAccessTokenValid() {
        String token = getAccessToken();
        return token != null && !token.isEmpty()
                && System.currentTimeMillis() < (getAccessTokenExpiresAt() - 60_000L);
    }

    /** True when a refresh token exists, i.e. the user should not see the login screen. */
    public boolean hasSession() {
        String refresh = getRefreshToken();
        return refresh != null && !refresh.isEmpty();
    }

    public void clear() {
        prefs.edit().clear().apply();
    }

    // --------------------------------------------------------------- device id

    public String getDeviceId() {
        SharedPreferences devicePrefs = appContext.getSharedPreferences(DEVICE_PREFS, Context.MODE_PRIVATE);
        String id = devicePrefs.getString(P_DEVICE_ID, null);
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
            devicePrefs.edit().putString(P_DEVICE_ID, id).apply();
        }
        return id;
    }

    // ----------------------------------------------------------------- crypto

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);

        if (keyStore.containsAlias(KEY_ALIAS)) {
            KeyStore.Entry entry = keyStore.getEntry(KEY_ALIAS, null);
            if (entry instanceof KeyStore.SecretKeyEntry) {
                return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
            }
        }

        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }

    private String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] iv = cipher.getIV();
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(iv, Base64.NO_WRAP)
                    + ":"
                    + Base64.encodeToString(cipherText, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "Unable to encrypt session value", e);
            return null;
        }
    }

    private String decrypt(String stored) {
        if (stored == null || stored.isEmpty()) {
            return null;
        }
        try {
            String[] parts = stored.split(":", 2);
            if (parts.length != 2) {
                return null;
            }
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] cipherText = Base64.decode(parts[1], Base64.NO_WRAP);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Key was invalidated (for example after a restore on another device).
            Log.e(TAG, "Unable to decrypt session value; clearing session", e);
            prefs.edit().clear().apply();
            return null;
        }
    }
}
