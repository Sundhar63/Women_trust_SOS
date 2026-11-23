package com.foxsite.womentrust.api_services;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.foxsite.womentrust.app_utills.SessionManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Authenticator;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.Route;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {
    public static final String BASE_URL = "https://python-backend-c8e63fol3-nayem11.vercel.app/";

    private static final MediaType JSON = MediaType.parse("application/json; charset=UTF-8");

    private static Retrofit retrofit;
    private static ApiService apiService;
    private static OkHttpClient refreshClient;
    private static SessionManager sessionManager;

    private RetrofitClient() {
    }

    /** Called once from the Application class. */
    public static synchronized void init(Context context) {
        if (context == null) {
            return;
        }
        sessionManager = SessionManager.get(context);
        if (retrofit != null) {
            return;
        }

        Gson gson = new GsonBuilder().setLenient().create();

        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);
        // Never log bodies: they contain credentials and tokens.

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)
                .addInterceptor(new AuthHeaderInterceptor())
                .addInterceptor(loggingInterceptor)
                .authenticator(new TokenAuthenticator())
                .build();

        refreshClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        apiService = retrofit.create(ApiService.class);
    }

    public static synchronized Retrofit getRetrofit() {
        if (retrofit == null) {
            throw new IllegalStateException("RetrofitClient.init(context) was not called");
        }
        return retrofit;
    }

    /**
     * Kept for source compatibility with existing call sites. The Authorization
     * header is now supplied automatically from {@link SessionManager}.
     */
    public static ApiService getApiService(String ignoredToken) {
        if (apiService == null) {
            throw new IllegalStateException("RetrofitClient.init(context) was not called");
        }
        return apiService;
    }

    // --------------------------------------------------------------- internals

    private static SessionManager session() {
        return sessionManager != null ? sessionManager : SessionManager.get();
    }

    private static final class AuthHeaderInterceptor implements Interceptor {
        @NonNull
        @Override
        public Response intercept(@NonNull Chain chain) throws IOException {
            Request original = chain.request();
            Request.Builder builder = original.newBuilder();

            if (original.header("Content-Type") == null
                    && (original.body() == null || original.body().contentType() == null)) {
                builder.header("Content-Type", "application/json; charset=UTF-8");
            }

            String accessToken = session().getAccessToken();
            if (accessToken != null && !accessToken.isEmpty()
                    && original.header("Authorization") == null) {
                builder.header("Authorization", "Bearer " + accessToken);
            }
            return chain.proceed(builder.build());
        }
    }

    /**
     * Handles a 401 by refreshing the session once, then replaying the request.
     * The synchronized block guarantees a single refresh when several calls fail
     * at the same time.
     */
    private static final class TokenAuthenticator implements Authenticator {
        private static final Object LOCK = new Object();

        @Nullable
        @Override
        public Request authenticate(@Nullable Route route, @NonNull Response response) throws IOException {
            Request request = response.request();

            // Do not try to refresh the refresh call itself.
            if (request.url().encodedPath().endsWith("/api/auth/refresh")) {
                return null;
            }
            // A request without our header was unauthenticated on purpose.
            if (request.header("Authorization") == null) {
                return null;
            }
            // Give up after one retry.
            if (responseCount(response) >= 2) {
                return null;
            }

            synchronized (LOCK) {
                String currentToken = session().getAccessToken();
                String usedHeader = request.header("Authorization");

                if (currentToken != null && !currentToken.isEmpty()
                        && !("Bearer " + currentToken).equals(usedHeader)) {
                    // Another thread already refreshed the session.
                    return request.newBuilder()
                            .header("Authorization", "Bearer " + currentToken)
                            .build();
                }

                String refreshToken = session().getRefreshToken();
                if (refreshToken == null || refreshToken.isEmpty()) {
                    session().clear();
                    return null;
                }

                JsonObject refreshed = refreshSync(refreshToken);
                if (refreshed == null) {
                    // Network or server failure: keep the session so the user can retry.
                    return null;
                }

                String newAccess = optString(refreshed, "access_token", "Token");
                String newRefresh = optString(refreshed, "refresh_token", "RefreshToken");
                long expiresIn = refreshed.has("expires_in") ? refreshed.get("expires_in").getAsLong() : 1800L;

                if (newAccess == null || newAccess.isEmpty()
                        || newRefresh == null || newRefresh.isEmpty()) {
                    session().clear();
                    return null;
                }

                session().saveSession(newAccess, newRefresh, expiresIn);

                return request.newBuilder()
                        .header("Authorization", "Bearer " + newAccess)
                        .build();
            }
        }

        private int responseCount(Response response) {
            int result = 1;
            while ((response = response.priorResponse()) != null) {
                result++;
            }
            return result;
        }
    }

    /** Synchronous refresh used by the OkHttp authenticator. */
    @Nullable
    public static JsonObject refreshSync(String refreshToken) {
        if (refreshClient == null || sessionManager == null) {
            return null;
        }
        JsonObject body = new JsonObject();
        body.addProperty("refresh_token", refreshToken);
        body.addProperty("device_id", sessionManager.getDeviceId());

        Request request = new Request.Builder()
                .url(BASE_URL + "api/auth/refresh")
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response response = refreshClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }
            String payload = response.body().string();
            if (payload.isEmpty()) {
                return null;
            }
            return new JsonParser().parse(payload).getAsJsonObject();
        } catch (Exception e) {
            Log.e("RetrofitClient", "Session refresh failed", e);
            return null;
        }
    }

    @Nullable
    public static String optString(JsonObject object, String... keys) {
        for (String key : keys) {
            if (object.has(key) && !object.get(key).isJsonNull()) {
                String value = object.get(key).getAsString();
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }
}
