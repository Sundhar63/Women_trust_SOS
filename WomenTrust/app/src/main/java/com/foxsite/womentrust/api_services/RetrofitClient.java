package com.foxsite.womentrust.api_services;


import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {
//    public static final String BASE_URL = "http://tmtechies3-001-site12.ltempurl.tmtechies.in/";
    public static final String BASE_URL = "https://python-backend-c8e63fol3-nayem11.vercel.app/";

    private static Retrofit retrofit;
    static String _token="";

    public static Retrofit getClient(OkHttpClient defaultHttpClient) {
        if (retrofit == null) {

            Gson gson = new GsonBuilder()
                    .setLenient()
                    .create();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(defaultHttpClient)
                    .addConverterFactory(GsonConverterFactory.create(gson))//Remove gson if not needed
                    .build();
        }
        return retrofit;
    }

    public static Retrofit createService(String authToken) {

        okhttp3.logging.HttpLoggingInterceptor loggingInterceptor = new okhttp3.logging.HttpLoggingInterceptor();
        loggingInterceptor.setLevel(okhttp3.logging.HttpLoggingInterceptor.Level.BODY);

        OkHttpClient defaultHttpClient = new OkHttpClient.Builder()
                .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)
                .addInterceptor(loggingInterceptor)
                .addInterceptor(
                        chain -> {
                            Request original = chain.request();
                            Request.Builder builder = original.newBuilder();

                            // Do not overwrite Content-Type if already set (e.g., by Multipart upload)
                            if (original.header("Content-Type") == null && (original.body() == null || original.body().contentType() == null)) {
                                builder.header("Content-Type", "application/json; charset=UTF-8");
                            }
                            if (_token != null && !_token.isEmpty()) {
                                Log.d("TokenStatus", _token);
                                builder.header("Authorization", "Bearer " + _token);
                            }
                            return chain.proceed(builder.build());
                        })
                .build();

        return getClient(defaultHttpClient);
    }

    public static ApiService getApiService(String token){
        _token=token;
        return RetrofitClient.createService(token).create(ApiService.class);
    }

}