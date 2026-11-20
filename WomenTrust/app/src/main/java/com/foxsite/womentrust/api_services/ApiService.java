package com.foxsite.womentrust.api_services;

import com.foxsite.womentrust.abuse.AbuseMasterRequest;
import com.foxsite.womentrust.abuse.CategoryViolenceRequest;
import com.foxsite.womentrust.abuse.SubCategoryRequest;
import com.foxsite.womentrust.abuse.SubCategoryViolenceRequest;
import com.foxsite.womentrust.login.LoginRequest;
import com.foxsite.womentrust.abuse.AddAbuseDetailRequest;
import com.foxsite.womentrust.register.UserRegisterModel;
import com.foxsite.womentrust.register.UserRegisterModelRequest;
import com.foxsite.womentrust.view_contact.ViewAbuseModelRequest;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Query;

public interface ApiService {

    @Multipart
    @POST("api/API/ProcessAudio")
    Call<JsonObject> processAudio(
            @Part MultipartBody.Part audio_file
    );

    @POST("api/API/")
    Call<JsonArray> login(
            @Body LoginRequest loginUser
    );

    @POST("api/API/")
    Call<JsonArray> getSubCategories(@Body SubCategoryRequest subCategoryRequest);

    @POST("api/API/")
    Call<JsonArray> getCategoriesViolence(@Body CategoryViolenceRequest categoryViolenceRequest);

    @POST("api/API/")
    Call<JsonArray> getSubCategoriesViolence(@Body SubCategoryViolenceRequest subCategoryViolenceRequest);

    @POST("api/API/")
    Call<JsonArray> addAbuseDetails(@Body AbuseMasterRequest abuseMasterRequest);

    @POST("api/API/")
    Call<JsonArray> userRegDetails(@Body UserRegisterModelRequest userRegisterModel);

    @POST("api/API/")
    Call<JsonArray> getAbuseContacts(@Body ViewAbuseModelRequest viewAbuseModelRequest);

    @GET("api/API/")
    Call<JsonObject> getExpenditureCategories();

    @GET("api/getDashboardDetails.php")
    Call<JsonObject> getDashboardDetails();


    @POST("/api/logout")
    Call<JsonObject> logout();

    @GET("api/getProducts.php")
    Call<JsonObject> getProductList(
            @Query("categoryId") String categoryId
    );

    @GET("api/getProductReturnStatus.php")
    Call<JsonObject> getProductReturnStatus(
            @Query("productId") String productId,
            @Query("invoiceId") String invoiceId);

}
