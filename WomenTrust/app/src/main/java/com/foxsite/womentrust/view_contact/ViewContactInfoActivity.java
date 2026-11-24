package com.foxsite.womentrust.view_contact;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.foxsite.womentrust.R;
import com.foxsite.womentrust.abuse.ImageAdapter;
import com.foxsite.womentrust.api_services.ApiService;
import com.foxsite.womentrust.api_services.RetrofitClient;
import com.foxsite.womentrust.app_utills.AppUtils;
import com.foxsite.womentrust.app_utills.LanguageHelper;
import com.foxsite.womentrust.app_utills.LoadingDialog;
import com.foxsite.womentrust.databinding.ActivityViewContactInfoBinding;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ViewContactInfoActivity extends AppCompatActivity {

    ActivityViewContactInfoBinding viewContactInfoBinding;
    AppUtils appUtils;
    LoadingDialog loadingDialog;

    String getSubCategoryName = "";
    String getCategoryId = "";
    String getCategoryName = "";
    String getCategoryViolenceName = "";
    String getSubCategoryViolenceName = "";
    String getCategoryViolences = "";

    SharedPreferences sharedPreferences;

    RecyclerView recyclerImages;
    ImageAdapter imageAdapter;
    List<String> imageList = new ArrayList<>();
    String lang = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewContactInfoBinding = ActivityViewContactInfoBinding.inflate(getLayoutInflater());
        setContentView(viewContactInfoBinding.getRoot());

        sharedPreferences = getSharedPreferences(getString(R.string.sharedpref_name), Context.MODE_PRIVATE);

        lang = LanguageHelper.getLanguageCode(this);

        loadingDialog = new LoadingDialog(ViewContactInfoActivity.this);
        appUtils = new AppUtils(this);

        this.setTitle(getString(R.string.details));
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        getCategoryId = getIntent().getStringExtra("categoryId");
        getCategoryName = getIntent().getStringExtra("CategoryName");
        getSubCategoryName = getIntent().getStringExtra("SubCategoryName");
        getCategoryViolenceName = getIntent().getStringExtra("CategoryViolenceName");
        getSubCategoryViolenceName = getIntent().getStringExtra("SubCategoryViolenceName");
        getCategoryViolences = getIntent().getStringExtra("CategoryViolences");

        viewContactInfoBinding.categoryName.setText(getCategoryName);
        viewContactInfoBinding.subCategoryName.setText(getSubCategoryName);
        viewContactInfoBinding.subCategoryViolenceName.setText(getCategoryViolenceName);
        viewContactInfoBinding.abuseCategoryText.setText(getCategoryViolences);


        String abuseString = getIntent().getStringExtra("jsonAbuseDetails");

        if (abuseString != null && !abuseString.trim().isEmpty() && !abuseString.equals("null")) {

            try {
                JSONArray abuseArray = new JSONArray(abuseString);
                JSONObject abuseObject = abuseArray.getJSONObject(0);

                int rating = getIntent().getIntExtra("violenceRating", 0);
                if (rating == 0) {
                    rating = abuseObject.optInt("VIOLENCE_RATING", 0);
                }

                String abuseLevel = abuseObject.optString("ABUSE_LEVEL", "");
                if (rating == 0) {
                    if ("Severe".equalsIgnoreCase(abuseLevel)) rating = 12;
                    else if ("Moderate".equalsIgnoreCase(abuseLevel)) rating = 7;
                    else rating = 3;
                }

                int levelColor;
                String ratingLabel;
                String defaultBadge;
                List<String> defaultParticulars = new ArrayList<>();

                if (rating < 5) {
                    abuseLevel = "Mild";
                    levelColor = 0xFF2E7D32; // Mild Green
                    ratingLabel = rating + " / 17 (Mild)";
                    defaultBadge = "save_logo.png";
                    defaultParticulars.add("1 - Talk to trusted Peers, Mentors & Family Member");
                    defaultParticulars.add("2 - Strengthen yourself to Ignore & divert");
                    defaultParticulars.add("3 - Assert to Say 'No'");
                    defaultParticulars.add("4 - Use it as a learning Opportunity to cope");
                    defaultParticulars.add("5 - Do not cry, depressed or Shows Signs of Victimisation");
                    defaultParticulars.add("6 - Tele-MANAS 24x7 Toll-Free Helpline: 14416 / Alternative: 1-800-891-4416 (Free & Confidential Support)");
                } else if (rating < 10) {
                    abuseLevel = "Moderate";
                    levelColor = 0xFFF57F17; // Mild Yellow / Amber
                    ratingLabel = rating + " / 17 (Moderate - Mild Yellow)";
                    defaultBadge = "telemanas_logo.png";
                    defaultParticulars.add("1 - Warn the harassar (Husband / in law family to Stop");
                    defaultParticulars.add("2 - Take some chance to record the Harassment");
                    defaultParticulars.add("3 - Have a detailed log of Date of the Harassment, Time, location, witness");
                    defaultParticulars.add("4 - Identify Early & report within family");
                    defaultParticulars.add("5 - 'No' Means 'No'");
                    defaultParticulars.add("6 - Be cautious & aware of external and legal remedy");
                    defaultParticulars.add("7 - Tele-MANAS 24x7 Toll-Free Helpline: 14416 / Alternative: 1-800-891-4416 (https://telemanas.mohf.gov.in/home)");
                } else {
                    abuseLevel = "Severe";
                    levelColor = 0xFFD32F2F; // Severe Red
                    ratingLabel = rating + " / 17 (Severe)";
                    defaultBadge = "tamilnadu_police_kaaval_uthavi.png";
                    defaultParticulars.add("1 - Consult A Legal Advisor");
                    defaultParticulars.add("2 - Talk To a family therapist Dr. Saranya Sundaraju 9500185855");
                    defaultParticulars.add("3 - Save Counselling 9842868900");
                    defaultParticulars.add("4 - Telemanas Nimhans 24x7 Toll-Free Helpline: 14416 / Alternative: 1-800-891-4416 (Free & Confidential Support)");
                    defaultParticulars.add("5 - KAVALAN UDANI APP");
                    defaultParticulars.add("6 - NCW Women Helpline 181");
                }

                viewContactInfoBinding.abuseLevelTextview.setText(abuseLevel);
                viewContactInfoBinding.abuseLevelTextview.setTextColor(levelColor);

                viewContactInfoBinding.violenceRatingTextview.setText(ratingLabel);
                viewContactInfoBinding.violenceRatingTextview.setTextColor(levelColor);

                JSONArray particularArray = abuseObject.optJSONArray("PARTICULAR");
                StringBuilder builder = new StringBuilder();

                if (particularArray != null && particularArray.length() > 0) {
                    for (int i = 0; i < particularArray.length(); i++) {
                        builder.append(particularArray.optString(i, "")).append("\n\n");
                    }
                } else {
                    for (String part : defaultParticulars) {
                        builder.append(part).append("\n\n");
                    }
                }

                viewContactInfoBinding.particularTextview.setText(builder.toString());

                recyclerImages = findViewById(R.id.recyclerImages);
                recyclerImages.setLayoutManager(
                        new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
                );

                JSONArray imageArray = abuseObject.optJSONArray("IMAGE_NAME");

                if (imageArray != null && imageArray.length() > 0) {
                    for (int i = 0; i < imageArray.length(); i++) {
                        if (!imageArray.isNull(i)) {
                            String imageUrl = imageArray.optString(i, "");

                            if (imageUrl != null && !imageUrl.trim().isEmpty()
                                    && !imageUrl.equalsIgnoreCase("null")) {
                                imageList.add(imageUrl);
                            }
                        }
                    }
                }

                if (imageList.isEmpty()) {
                    imageList.add(defaultBadge);
                }

                imageAdapter = new ImageAdapter(this, imageList);
                recyclerImages.setAdapter(imageAdapter);

                String contactNo = abuseObject.optString("CONTACT_NO", "");
                String contactMail = abuseObject.optString("CONTACT_MAIL_ID", "");
                String contactWeb = abuseObject.optString("CONTACT_WEBSITE", "");

                if (!contactNo.isEmpty() || !contactMail.isEmpty() || !contactWeb.isEmpty()) {
                    viewContactInfoBinding.contactNumberText.setText(contactNo);
                    viewContactInfoBinding.contactEmailText.setText(contactMail);
                    viewContactInfoBinding.contactWebAddres.setText(contactWeb);
                    viewContactInfoBinding.contactDetailsContainer.setVisibility(View.VISIBLE);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            if (appUtils.isNetworkAvailable()) {
                getAbuseContactDetails(getCategoryName, getSubCategoryName, getCategoryViolenceName, lang);
            } else {
                appUtils.infoAlert(getString(R.string.check_network_connection));
            }
        }

    }


    public void getAbuseContactDetails(
            String _CategoryName,
            String _SubCategoryName,
            String _CategoryViolenceName,
            String _ln
    ) {
        loadingDialog.show();
        ApiService apiService = RetrofitClient.getApiService("");
        ViewAbuseModelRequest viewAbuseObject = new ViewAbuseModelRequest("AbuseContact", _CategoryName, _SubCategoryName, _CategoryViolenceName, _ln);
        Gson gson = new Gson();
        String json = gson.toJson(viewAbuseObject);
        Log.d("json_abuseRequest", json);
        Call<JsonArray> call = apiService.getAbuseContacts(viewAbuseObject);
        call.enqueue(new Callback<JsonArray>() {
            @Override
            public void onResponse(Call<JsonArray> call, Response<JsonArray> response) {

                Log.d("Abuse_Response", response.toString());
                if (response.isSuccessful() && response.body() != null) {
                    JsonArray data = response.body();
                    Log.d("AbuseData_Response", data.toString());

                    try {
                        JSONArray jsonArray = new JSONArray(data.toString());
                        JSONObject jsonObject = jsonArray.getJSONObject(0);
                        String status = jsonObject.optString("Status", "");

                        if ("Success".equalsIgnoreCase(status)) {
                            JSONArray AbuseDataJSONArray = jsonObject.optJSONArray("aBUSE_CONTACT_DETAILS_s");
                            if (AbuseDataJSONArray != null && AbuseDataJSONArray.length() > 0) {
                                JSONObject AbuseDataJSONObject = AbuseDataJSONArray.getJSONObject(0);

                                String _violence = AbuseDataJSONObject.optString("VIOLENCE", "");
                                String _sub_violence = AbuseDataJSONObject.optString("SUB_VIOLENCE", "");
                                String _category = AbuseDataJSONObject.optString("CATEGORY", "");
                                String _contact_mail_id = AbuseDataJSONObject.optString("CONTACT_MAIL_ID", "");
                                String _contact_no = AbuseDataJSONObject.optString("CONTACT_NO", "");
                                String _contact_website = AbuseDataJSONObject.optString("CONTACT_WEBSITE", "");
                                String _abouseLevel = AbuseDataJSONObject.optString("ABUSE_LEVEL", "");
                                int rating = getIntent().getIntExtra("violenceRating", 0);
                                if (rating == 0) {
                                    if ("Severe".equalsIgnoreCase(_abouseLevel)) rating = 12;
                                    else if ("Moderate".equalsIgnoreCase(_abouseLevel)) rating = 7;
                                    else rating = 3;
                                }

                                int levelColor;
                                String ratingLabel;
                                String defaultBadge;
                                if (rating < 5) {
                                    _abouseLevel = "Mild";
                                    levelColor = 0xFF2E7D32;
                                    ratingLabel = rating + " / 17 (Mild)";
                                    defaultBadge = "save_logo.png";
                                } else if (rating < 10) {
                                    _abouseLevel = "Moderate";
                                    levelColor = 0xFFF57F17;
                                    ratingLabel = rating + " / 17 (Moderate - Mild Yellow)";
                                    defaultBadge = "telemanas_logo.png";
                                } else {
                                    _abouseLevel = "Severe";
                                    levelColor = 0xFFD32F2F;
                                    ratingLabel = rating + " / 17 (Severe)";
                                    defaultBadge = "tamilnadu_police_kaaval_uthavi.png";
                                }

                                viewContactInfoBinding.contactNumberText.setText(_contact_no);
                                viewContactInfoBinding.contactEmailText.setText(_contact_mail_id);
                                viewContactInfoBinding.contactWebAddres.setText(_contact_website);

                                if (!_contact_no.isEmpty() || !_contact_mail_id.isEmpty() || !_contact_website.isEmpty()) {
                                    viewContactInfoBinding.contactDetailsContainer.setVisibility(View.VISIBLE);
                                }

                                viewContactInfoBinding.abuseLevelTextview.setText(_abouseLevel);
                                viewContactInfoBinding.abuseLevelTextview.setTextColor(levelColor);

                                viewContactInfoBinding.violenceRatingTextview.setText(ratingLabel);
                                viewContactInfoBinding.violenceRatingTextview.setTextColor(levelColor);

                                recyclerImages = findViewById(R.id.recyclerImages);
                                recyclerImages.setLayoutManager(
                                        new LinearLayoutManager(ViewContactInfoActivity.this, LinearLayoutManager.HORIZONTAL, false)
                                );
                                imageList.clear();
                                JSONArray imgArray = AbuseDataJSONObject.optJSONArray("IMAGE_NAME");
                                if (imgArray != null && imgArray.length() > 0) {
                                    for (int k = 0; k < imgArray.length(); k++) {
                                        String url = imgArray.optString(k, "");
                                        if (!url.isEmpty() && !url.equalsIgnoreCase("null")) {
                                            imageList.add(url);
                                        }
                                    }
                                }
                                if (imageList.isEmpty()) {
                                    imageList.add(defaultBadge);
                                }
                                imageAdapter = new ImageAdapter(ViewContactInfoActivity.this, imageList);
                                recyclerImages.setAdapter(imageAdapter);

                                JSONArray particularArray = AbuseDataJSONObject.optJSONArray("PARTICULAR");
                                if (particularArray != null) {
                                    StringBuilder builder = new StringBuilder();
                                    for (int i = 0; i < particularArray.length(); i++) {
                                        builder.append(particularArray.optString(i, "")).append("\n\n");
                                    }
                                    viewContactInfoBinding.particularTextview.setText(builder.toString());
                                }
                            } else {
                                appUtils.showToast("Data not found");
                            }

                        } else {
                            appUtils.showToast("Data not found");
                        }

                    } catch (Exception e) {
                        loadingDialog.cancel();
                        Log.e("json Abuse_Error", "Abuse parsing error", e);
                    }

                    loadingDialog.cancel();

                } else {
                    loadingDialog.cancel();
                    Log.d("json Abuse_Error", "");
                    appUtils.showToast("Input Error.");
                }

            }

            @Override
            public void onFailure(Call<JsonArray> call, Throwable t) {
                Log.d("Abuse_Error", t.toString());
                loadingDialog.cancel();

                appUtils.showToast("Network error.");
            }
        });

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

}