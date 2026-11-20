package com.foxsite.womentrust.abuse;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import com.foxsite.womentrust.R;
import com.foxsite.womentrust.app_utills.LanguageHelper;
import com.foxsite.womentrust.app_utills.SegmentedProgressBar;
import com.foxsite.womentrust.view_contact.ViewContactInfoActivity;
import com.foxsite.womentrust.api_services.ApiService;
import com.foxsite.womentrust.api_services.RetrofitClient;
import com.foxsite.womentrust.app_utills.AppUtils;
import com.foxsite.womentrust.app_utills.LoadingDialog;
import com.foxsite.womentrust.databinding.ActivityAbuseBinding;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.JsonArray;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AbuseActivity extends AppCompatActivity {

    ActivityAbuseBinding activityAbuseBinding;
    AppUtils appUtils;
    LoadingDialog loadingDialog;

    SubCategoryModel categoryModel;
    CategoryViolenceModel categoryViolenceModel;
    SubCategoryViolenceModel subCategoryViolenceModel;
    String getSubCategoryId = "";
    String getSubCategoryName = "";
    String getCategoryId = "";
    String getCategoryName = "";
    String getCategoryViolenceName = "";
    String getCategoryViolenceId = "";
    String getSubCategoryViolenceName = "";
    String getSelectedSubCategoryViolence = "";
    ArrayList<String> getCategoryViolences;
    ArrayList<SubCategoryModel> categoryModelArrayList;
    ArrayList<CategoryViolenceModel> categoryViolenceModelArrayList;
    ArrayList<SubCategoryViolenceModel> subCategoryViolenceModelArrayList;
    SubCategoryAdapter subCategoryAdapter;
    CategoryViolenceAdapter categoryViolenceAdapter;
    SubCategoryViolenceAdapter violenceAdapter;

    private String pendingAutoSubViolence = "";
    private String pendingAutoCategoryViolence = "";

    SharedPreferences sharedPreferences;

    String userID = "";
    String username = "";
    String name = "";
    String mobile = "";
    String email = "";
    String role = "";
    String roleId = "";
    int violenceRating =0;
    int voiceRatingFloor = 0;   // severity rating from the voice AI; the label count must not lower it

    String lang ="";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityAbuseBinding = ActivityAbuseBinding.inflate(getLayoutInflater());
        setContentView(activityAbuseBinding.getRoot());

        sharedPreferences = getSharedPreferences(getString(R.string.sharedpref_name), Context.MODE_PRIVATE);

        this.setTitle(getString(R.string.abuse_screen_title));
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        lang = LanguageHelper.getLanguageCode(this);
        LanguageHelper.setLocale(this, lang);

        getCategoryId = getIntent().getStringExtra("categoryId");
        getCategoryName = getIntent().getStringExtra("categoryName");
        if (getCategoryId == null || getCategoryId.trim().isEmpty()) {
            getCategoryId = "1";
        }
        if ("ta".equalsIgnoreCase(lang)) {
            if ("1".equals(getCategoryId) || getCategoryName == null || "Domestic Violence".equalsIgnoreCase(getCategoryName)) {
                getCategoryName = getString(R.string.domestic_violence).trim();
            } else if ("2".equals(getCategoryId) || "Violence At Work Place".equalsIgnoreCase(getCategoryName)) {
                getCategoryName = getString(R.string.violence_work_place).trim();
            } else if ("3".equals(getCategoryId) || "Violence At Public Place".equalsIgnoreCase(getCategoryName)) {
                getCategoryName = getString(R.string.violence_public_place).trim();
            } else if ("4".equals(getCategoryId) || "Child Sexual Abuse".equalsIgnoreCase(getCategoryName)) {
                getCategoryName = getString(R.string.child_sexual_abuse).trim();
            }
        } else {
            if (getCategoryName == null || getCategoryName.trim().isEmpty()) {
                getCategoryName = "Domestic Violence";
            }
        }
        activityAbuseBinding.categoryName.setText(getCategoryName);

        userID = sharedPreferences.getString(getString(R.string.userId), "");
        username = sharedPreferences.getString(getString(R.string.username), "");
        name = sharedPreferences.getString(getString(R.string.name), "");
        mobile = sharedPreferences.getString(getString(R.string.mobile), "");
        email = sharedPreferences.getString(getString(R.string.email), "");
        roleId = sharedPreferences.getString(getString(R.string.role), "");

        loadingDialog = new LoadingDialog(AbuseActivity.this);
        appUtils = new AppUtils(this);

        getCategoryViolences = new ArrayList<String>();
        categoryModelArrayList = new ArrayList<SubCategoryModel>();
        categoryViolenceModelArrayList = new ArrayList<CategoryViolenceModel>();
        subCategoryViolenceModelArrayList = new ArrayList<SubCategoryViolenceModel>();

        populateInitialSpinners();

        if (appUtils.isNetworkAvailable()) {
            getSubCategory(getCategoryId, lang);
        } else {
            appUtils.infoAlert(getString(R.string.check_network_connection));
        }

        handleVoiceAgentExtras(getIntent());

        View.OnClickListener subCatClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String catId = (getCategoryId != null && !getCategoryId.isEmpty()) ? getCategoryId : "1";
                String subCatId = (getSubCategoryId != null && !getSubCategoryId.isEmpty()) ? getSubCategoryId : "1";
                String catViolId = (getCategoryViolenceId != null && !getCategoryViolenceId.isEmpty()) ? getCategoryViolenceId : "1";
                getSubCategoryViolance(catId, subCatId, catViolId, lang);
            }
        };

        activityAbuseBinding.categoryViolenceLayout.setOnClickListener(subCatClickListener);
        activityAbuseBinding.categoryViolenceTextV.setOnClickListener(subCatClickListener);

        activityAbuseBinding.submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getCategoryViolences == null || getCategoryViolences.isEmpty()) {
                    appUtils.infoAlert("Please select Sub-Category Violence before submitting.");
                    return;
                }

                if (appUtils.isNetworkAvailable()) {
                    try {
                        String updatdate = appUtils.getCurrentDateOnly().toString();
                        String updateDateTime = appUtils.getCurrentLocalDateTime().toString();

                        if (getCategoryName == null || getCategoryName.trim().isEmpty()) {
                            getCategoryName = "Domestic Violence";
                        }
                        if (getSubCategoryName == null || getSubCategoryName.trim().isEmpty()) {
                            getSubCategoryName = "Intimate Partner Violence";
                        }
                        if (getCategoryViolenceName == null || getCategoryViolenceName.trim().isEmpty()) {
                            getCategoryViolenceName = "Physical Abuse";
                        }
                        if (getSelectedSubCategoryViolence == null || getSelectedSubCategoryViolence.trim().isEmpty()) {
                            getSelectedSubCategoryViolence = TextUtils.join(", ", getCategoryViolences);
                        }
                        if (getSubCategoryViolenceName == null || getSubCategoryViolenceName.trim().isEmpty()) {
                            getSubCategoryViolenceName = getCategoryViolences.get(0);
                        }

                        violenceRating = Math.min(17, Math.max(getCategoryViolences.size(), voiceRatingFloor));

                        String ratingColor;
                        String ratingNumber = String.valueOf(violenceRating);
                        if (violenceRating >= 10) {
                            ratingColor = "Red";
                        } else if (violenceRating >= 5) {
                            ratingColor = "Mild Yellow";
                        } else {
                            ratingColor = "Mild";
                        }

                        Log.d("RatingColor: ", ratingColor + " " + violenceRating);
                        abuseRegister(
                                "Abuse",
                                username,
                                getCategoryName,
                                getSubCategoryName,
                                getCategoryViolenceName,
                                getCategoryViolences,
                                ratingColor,
                                ratingNumber,
                                "Active",
                                "Admin",
                                "Manager",
                                updatdate,
                                updateDateTime,
                                lang
                        );

                    } catch (ParseException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    appUtils.infoAlert(getString(R.string.check_network_connection));
                }

            }
        });

        activityAbuseBinding.segmentedProgress.setProgress(0);
        updateRatingDisplay();


    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    private void getSubCategory(String categoryId, String ln) {
        if (categoryId == null || categoryId.trim().isEmpty()) {
            categoryId = "1";
        }
        loadingDialog.show();
        SubCategoryRequest subCategoryRequest = new SubCategoryRequest("SubViolence", categoryId, ln);
        ApiService apiService = RetrofitClient.getApiService("");
        Call<JsonArray> call = apiService.getSubCategories(subCategoryRequest);
        call.enqueue(new Callback<JsonArray>() {
            @Override
            public void onResponse(Call<JsonArray> call, Response<JsonArray> response) {
                loadingDialog.cancel();
                Log.d("VCategory_Response", response != null ? response.toString() : "null");
                if (response != null && response.isSuccessful() && response.body() != null) {
                    try {
                        JsonArray data = response.body();
                        JSONArray jsonArray = new JSONArray(data.toString());
                        JSONObject jsonObject = jsonArray.getJSONObject(0);
                        String status = jsonObject.optString("Status", "");

                        if ("Success".equalsIgnoreCase(status)) {
                            JSONArray jsonDataArray = jsonObject.optJSONArray("Sub_Violences");

                            if (jsonDataArray != null && jsonDataArray.length() > 0) {
                                ArrayList<SubCategoryModel> tempList = new ArrayList<>();
                                for (int i = 0; i < jsonDataArray.length(); i++) {

                                    JSONObject responseData = jsonDataArray.getJSONObject(i);

                                    String _subCategoryId = responseData.optString("ID", "");
                                    String _subCategoryCode = responseData.optString("CODE", "");
                                    String _categoryName = responseData.optString("VIOLENCE", "");
                                    String _subCategoryName = responseData.optString("SUB_VIOLENCE", "");

                                    if ("ta".equalsIgnoreCase(lang)) {
                                        if (responseData.has("TAMIL_VIOLENCE") && !responseData.optString("TAMIL_VIOLENCE", "").trim().isEmpty()) {
                                            _categoryName = responseData.optString("TAMIL_VIOLENCE", "");
                                        }
                                        if (responseData.has("TAMIL_SUB_VIOLENCE") && !responseData.optString("TAMIL_SUB_VIOLENCE", "").trim().isEmpty()) {
                                            _subCategoryName = responseData.optString("TAMIL_SUB_VIOLENCE", "");
                                        }
                                    }

                                    if (i == 0 && _categoryName != null && !_categoryName.trim().isEmpty()) {
                                        getCategoryName = _categoryName;
                                        activityAbuseBinding.categoryName.setText(_categoryName);
                                    }

                                    SubCategoryModel model = new SubCategoryModel(
                                            _subCategoryId,
                                            _subCategoryCode,
                                            _categoryName,
                                            _subCategoryName
                                    );
                                    tempList.add(model);
                                }
                                categoryModelArrayList.clear();
                                categoryModelArrayList.addAll(tempList);
                            }
                        }
                    } catch (JSONException e) {
                        Log.d("JCategory_Error", e.toString());
                    }
                }

                if (categoryModelArrayList.isEmpty()) {
                    populateFallbackSubViolence();
                } else {
                    subCategoryAdapter = new SubCategoryAdapter(AbuseActivity.this, categoryModelArrayList);
                    activityAbuseBinding.spinnerSubCategory.setAdapter(subCategoryAdapter);

                    activityAbuseBinding.spinnerSubCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                            categoryModel = categoryModelArrayList.get(position);

                            getSubCategoryId = categoryModel.getSubCategoryId();
                            getSubCategoryName = categoryModel.getSubCategoryName();
                            appUtils.showToast(getSubCategoryName + " is selected");

                            getCategoryViolance(getCategoryId, getSubCategoryId, lang);
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {

                        }
                    });

                    if (pendingAutoSubViolence != null && !pendingAutoSubViolence.isEmpty()) {
                        autoSelectSubViolence(pendingAutoSubViolence, pendingAutoCategoryViolence);
                    } else if (!categoryModelArrayList.isEmpty()) {
                        activityAbuseBinding.spinnerSubCategory.setSelection(0);
                    }
                }
            }

            @Override
            public void onFailure(Call<JsonArray> call, Throwable t) {
                loadingDialog.cancel();
                Log.d("Category_Error", t.toString());
                populateFallbackSubViolence();
            }
        });

    }

    private void getCategoryViolance(String categoryId, String subCategoryId, String ln) {
        if (categoryId == null || categoryId.trim().isEmpty()) {
            categoryId = "1";
        }
        if (subCategoryId == null || subCategoryId.trim().isEmpty()) {
            subCategoryId = "1";
        }
        loadingDialog.show();
        CategoryViolenceRequest categoryViolenceRequest = new CategoryViolenceRequest("CategoryViolence", categoryId, subCategoryId, ln);
        ApiService apiService = RetrofitClient.getApiService("");
        Call<JsonArray> call = apiService.getCategoriesViolence(categoryViolenceRequest);
        call.enqueue(new Callback<JsonArray>() {
            @Override
            public void onResponse(Call<JsonArray> call, Response<JsonArray> response) {
                loadingDialog.cancel();
                Log.d("CategoryViolence_Response", response != null ? response.toString() : "null");
                if (response != null && response.isSuccessful() && response.body() != null) {
                    try {
                        JsonArray data = response.body();
                        JSONArray jsonArray = new JSONArray(data.toString());
                        JSONObject jsonObject = jsonArray.getJSONObject(0);
                        String status = jsonObject.optString("Status", "");

                        if ("Success".equalsIgnoreCase(status)) {
                            JSONArray jsonDataArray = jsonObject.optJSONArray("cATEGORY_VIOLENCE_MASTERs");

                            if (jsonDataArray != null && jsonDataArray.length() > 0) {
                                ArrayList<CategoryViolenceModel> tempList = new ArrayList<>();
                                for (int i = 0; i < jsonDataArray.length(); i++) {

                                    JSONObject responseData = jsonDataArray.getJSONObject(i);

                                    String _violenceId = responseData.optString("ID", "");
                                    String _violenceCode = responseData.optString("CODE", "");
                                    String _violenceTypeCode = responseData.optString("VIOLENCE_CODE", "");
                                    String _subViolenceTypeCode = responseData.optString("SUB_VIOLENCE_CODE", "");
                                    String _violenceName = responseData.optString("CATEGORY", "");
                                    String _violenceStatus = responseData.optString("MASTER_STATUS", "");

                                    if ("ta".equalsIgnoreCase(lang)) {
                                        if (responseData.has("TAMIL_CATEGORY") && !responseData.optString("TAMIL_CATEGORY", "").trim().isEmpty()) {
                                            _violenceName = responseData.optString("TAMIL_CATEGORY", "");
                                        }
                                    }

                                    CategoryViolenceModel model = new CategoryViolenceModel(
                                            _violenceId,
                                            _violenceCode,
                                            _violenceTypeCode,
                                            _subViolenceTypeCode,
                                            _violenceName,
                                            _violenceStatus
                                    );
                                    tempList.add(model);
                                }
                                categoryViolenceModelArrayList.clear();
                                categoryViolenceModelArrayList.addAll(tempList);
                            }
                        }
                    } catch (JSONException e) {
                        Log.d("JCategory_Error", e.toString());
                    }
                }

                if (categoryViolenceModelArrayList.isEmpty()) {
                    populateFallbackCategoryViolence();
                } else {
                    categoryViolenceAdapter = new CategoryViolenceAdapter(AbuseActivity.this, categoryViolenceModelArrayList);
                    activityAbuseBinding.spinnerCategoryViolence.setAdapter(categoryViolenceAdapter);

                    activityAbuseBinding.spinnerCategoryViolence.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                            categoryViolenceModel = categoryViolenceModelArrayList.get(position);

                            getCategoryViolenceId = categoryViolenceModel.getViolenceId();
                            getCategoryViolenceName = categoryViolenceModel.getViolenceName();
                            appUtils.showToast(getCategoryViolenceName + " is selected");
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {

                        }
                    });

                    if (pendingAutoCategoryViolence != null && !pendingAutoCategoryViolence.isEmpty()) {
                        autoSelectCategoryViolence(pendingAutoCategoryViolence);
                    } else if (!categoryViolenceModelArrayList.isEmpty()) {
                        activityAbuseBinding.spinnerCategoryViolence.setSelection(0);
                    }
                }
            }

            @Override
            public void onFailure(Call<JsonArray> call, Throwable t) {
                loadingDialog.cancel();
                Log.d("Category_Error", t.toString());
                populateFallbackCategoryViolence();
            }
        });

    }

    private void getSubCategoryViolance(String categoryId, String subCategoryId, String categoryViolenceId, String ln) {
        subCategoryViolenceModelArrayList.clear();
        loadingDialog.show();
        SubCategoryViolenceRequest subCategoryViolenceRequest = new SubCategoryViolenceRequest("SubCategoryViolence", categoryId, subCategoryId, categoryViolenceId, ln);
        ApiService apiService = RetrofitClient.getApiService("");
        Call<JsonArray> call = apiService.getSubCategoriesViolence(subCategoryViolenceRequest);
        call.enqueue(new Callback<JsonArray>() {
            @Override
            public void onResponse(Call<JsonArray> call, Response<JsonArray> response) {
                loadingDialog.cancel();
                Log.d("CategoryViolence_Response", response != null ? response.toString() : "null");
                if (response != null && response.isSuccessful() && response.body() != null) {
                    try {
                        JSONArray jsonArray = new JSONArray(response.body().toString());
                        JSONObject jsonObject = jsonArray.getJSONObject(0);
                        String status = jsonObject.optString("Status", "");

                        if ("Success".equalsIgnoreCase(status)) {
                            JSONArray jsonDataArray = jsonObject.optJSONArray("sUB_CATEGORY_VIOLENCE_MASTERs");

                            if (jsonDataArray != null && jsonDataArray.length() > 0) {
                                for (int i = 0; i < jsonDataArray.length(); i++) {
                                    JSONObject responseData = jsonDataArray.getJSONObject(i);

                                    String _id = responseData.optString("ID", String.valueOf(i + 1));
                                    String _violence_code = responseData.optString("VIOLENCE_CODE", "1");
                                    String _code = responseData.optString("CODE", String.valueOf(i + 1));
                                    String _sub_violence_code = responseData.optString("SUB_VIOLENCE_CODE", "1");
                                    String _category_code = responseData.optString("CATEGORY_CODE", "1");
                                    String _sub_category = responseData.optString("SUB_CATEGORY", "");
                                    if ("ta".equalsIgnoreCase(lang)) {
                                        if (responseData.has("TAMIL_SUB_CATEGORY") && !responseData.optString("TAMIL_SUB_CATEGORY", "").trim().isEmpty()) {
                                            _sub_category = responseData.optString("TAMIL_SUB_CATEGORY", "");
                                        }
                                    } else {
                                        if (_sub_category.isEmpty()) {
                                            _sub_category = responseData.optString("TAMIL_SUB_CATEGORY", "");
                                        }
                                    }
                                    String _master_status = responseData.optString("MASTER_STATUS", "Active");
                                    String _username = responseData.optString("USERNAME", "Admin");
                                    String _role = responseData.optString("ROLE", "Manager");
                                    String _updated = responseData.optString("UPDATED", "");
                                    String _updatetime = responseData.optString("UPDATETIME", "");

                                    SubCategoryViolenceModel model = new SubCategoryViolenceModel(
                                            _id,
                                            _violence_code,
                                            _code,
                                            _sub_violence_code,
                                            _category_code,
                                            _sub_category,
                                            _master_status,
                                            _username,
                                            _role,
                                            _updated,
                                            _updatetime
                                    );
                                    subCategoryViolenceModelArrayList.add(model);
                                }
                            }
                        }

                    } catch (Exception e) {
                        Log.e("JCategory_Error", "SubCategoryViolence parsing error", e);
                    }
                }

                if (subCategoryViolenceModelArrayList.isEmpty()) {
                    populateFallbackSubCategories();
                }

                showViolanceCategoryDialog(subCategoryViolenceModelArrayList);
            }

            @Override
            public void onFailure(Call<JsonArray> call, Throwable t) {
                loadingDialog.cancel();
                Log.e("Category_Error", "Network error in getSubCategoryViolance", t);
                populateFallbackSubCategories();
                showViolanceCategoryDialog(subCategoryViolenceModelArrayList);
            }
        });

    }

    private void populateFallbackSubCategories() {
        subCategoryViolenceModelArrayList.clear();
        String[] defaultSubs;
        if ("ta".equalsIgnoreCase(lang)) {
            defaultSubs = new String[]{
                    "அடி கொடுத்தல்", "தள்ளுதல்", "கால் அடித்தல்", "முடி இழுத்தல்", "கைதட்டுதல்",
                    "அதிகாரம் மற்றும் அவமானம்", "கடுமையான நடத்தல்", "சுட்டல் மற்றும் எரிதல்",
                    "உடல் தண்டனைகள்", "அசாதாரண அசௌகரிய உணர்வு",
                    "வலியுறுத்தப்பட்ட தனிமை", "மருந்தின் தவறான பயன்பாடு",
                    "செயலை வற்புறுத்தல்", "உணவை மறுத்தல்", "இயக்கத்தை கட்டுப்படுத்தல்",
                    "அனுமதியில்லாத கட்டளையை கடைபிடிக்க வற்புறுத்தல்", "மற்றவை"
            };
        } else {
            defaultSubs = new String[]{
                    "Hitting", "Pushing", "Kicking", "Hair Pulling", "Slapping",
                    "Assault & Ashame", "Rough Handling", "Scalding & Burning",
                    "Physical Punishments", "Inappropriate Uncomfortable Feeling",
                    "Involuntary Isolation Of Confinement", "Misuse Of Medication",
                    "Forcing An Action", "With Hold Food", "Restricting Movement",
                    "Unauthorised Command To Obey", "Others"
            };
        }
        for (int i = 0; i < defaultSubs.length; i++) {
            subCategoryViolenceModelArrayList.add(new SubCategoryViolenceModel(
                    String.valueOf(i + 1), "1", String.valueOf(i + 1), "1", "1",
                    defaultSubs[i], "Active", "Admin", "Manager", "", ""
            ));
        }
    }

    public void showViolanceCategoryDialog(ArrayList<SubCategoryViolenceModel> subCategoryViolenceModelArrayList) {
        final Dialog dialog = new Dialog(AbuseActivity.this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setContentView(R.layout.dialog_list_view);
        ListView listView = dialog.findViewById(R.id.violanceListview);
        FloatingActionButton fab = dialog.findViewById(R.id.fab);
        View btnCancel = dialog.findViewById(R.id.btnCancelDialog);
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        violenceAdapter = new SubCategoryViolenceAdapter(AbuseActivity.this, subCategoryViolenceModelArrayList);
        if (getCategoryViolences != null && !getCategoryViolences.isEmpty()) {
            violenceAdapter.setSelectedViolations(getCategoryViolences);
        }
        listView.setAdapter(violenceAdapter);
        violenceAdapter.notifyDataSetChanged();

        if (dialog.getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.95);
            int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.85);
            dialog.getWindow().setLayout(width, height);
        }

        fab.setOnClickListener(v -> {
            List<SubCategoryViolenceModel> selectedItems = violenceAdapter.getCheckedItems();

            if (selectedItems != null && !selectedItems.isEmpty()) {
                ArrayList<String> selectedIds = new ArrayList<>();

                for (SubCategoryViolenceModel model : selectedItems) {
                    Log.d("SelectedSubViolence: ", model.getSub_category());
                    selectedIds.add(model.getSub_category());
                }
                getCategoryViolences = selectedIds;

                String joined = TextUtils.join(", ", selectedIds);

                getSubCategoryViolenceName = selectedIds.get(0);
                getSelectedSubCategoryViolence = joined;
                activityAbuseBinding.categoryViolenceTextV.setText(joined);

                violenceRating = Math.min(17, Math.max(getCategoryViolences.size(), voiceRatingFloor));

                activityAbuseBinding.segmentedProgress.setProgress(violenceRating);
                updateRatingDisplay();
                dialog.dismiss();
            } else {
                appUtils.infoAlert("Please select at least one type of violence");
            }
        });

        dialog.show();
    }

    public void abuseRegister(
            String actions,
            String username,
            String violencename,
            String subviolencename,
            String categoryname,
            ArrayList<String> subCategoryname,
            String colour,
            String colour_rating,
            String master_status,
            String registerUser,
            String role,
            String updated,
            String updatetime,
            String ln
    ) {
        loadingDialog.show();
        ApiService apiService = RetrofitClient.getApiService("");
        AddAbuseDetailRequest abuseDetailRequest = new AddAbuseDetailRequest(
                username,
                violencename,
                subviolencename,
                categoryname,
                subCategoryname,
                colour,
                colour_rating,
                master_status,
                username,
                role,
                "",
                updatetime
        );


        List<AddAbuseDetailRequest> abuseDetailRequestArrayList = new ArrayList<>();
        abuseDetailRequestArrayList.add(abuseDetailRequest);

        AbuseMasterRequest abuseMasterRequest = new AbuseMasterRequest(
                actions,
                ln,
                abuseDetailRequestArrayList
        );

        Gson gson = new Gson();
        String json = gson.toJson(abuseMasterRequest);
        Log.d("String_abusedetails: ", json);

        Call<JsonArray> call = apiService.addAbuseDetails(abuseMasterRequest);
        call.enqueue(new Callback<JsonArray>() {
            @Override
            public void onResponse(Call<JsonArray> call, Response<JsonArray> response) {

                Log.d("AbuseData_Response", response.toString());
                if (response.isSuccessful() && response.body() != null) {
                    JsonArray data = response.body();
                    Log.d("AbuseDataData_Response", data.toString());

                    try {
                        JSONArray jsonArray = new JSONArray(data.toString());
                        JSONObject jsonObject = jsonArray.getJSONObject(0);
                        String status = jsonObject.optString("Status", "");

                        if ("Success".equalsIgnoreCase(status)) {
                            appUtils.showToast(getString(R.string.data_insert_success));

                            Intent mainIntent = new Intent(AbuseActivity.this, ViewContactInfoActivity.class);
                            JSONArray abuseArray = jsonObject.optJSONArray("aBUSE_CONTACT_DETAILS_s");
                            mainIntent.putExtra("CategoryName", getCategoryName);
                            mainIntent.putExtra("SubCategoryName", getSubCategoryName);
                            mainIntent.putExtra("CategoryViolenceName", getCategoryViolenceName);
                            mainIntent.putExtra("SubCategoryViolenceName", getSubCategoryViolenceName);
                            mainIntent.putExtra("CategoryViolences", getSelectedSubCategoryViolence);
                            mainIntent.putExtra("violenceRating", violenceRating);
                            mainIntent.putExtra("ratingColor", colour);

                            if (abuseArray == null || abuseArray.length() == 0 || !abuseArray.getJSONObject(0).has("IMAGE_NAME") || abuseArray.getJSONObject(0).optJSONArray("IMAGE_NAME") == null || abuseArray.getJSONObject(0).optJSONArray("IMAGE_NAME").length() == 0) {
                                abuseArray = createContactDetailsJson(violenceRating, getCategoryName, getSubCategoryName, getCategoryViolenceName);
                            }
                            mainIntent.putExtra("jsonAbuseDetails", abuseArray.toString());
                            startActivity(mainIntent);

                        } else {
                            appUtils.showToast(getString(R.string.data_insert_unsuccess));
                        }

                    } catch (Exception e) {
                        loadingDialog.cancel();
                        Log.e("json AbuseData_Error", "Abuse data parsing error", e);
                        appUtils.showToast("Error processing report: " + e.getMessage());
                    }

                    loadingDialog.cancel();

                } else {
                    loadingDialog.cancel();
                    Log.d("json AbuseData_Error", "");
                    appUtils.showToast("Input Error.");
                }
            }

            @Override
            public void onFailure(Call<JsonArray> call, Throwable t) {
                Log.d("AbuseData_Error", t.toString());
                loadingDialog.cancel();

                appUtils.showToast("Network error.");
            }
        });

    }

    private void handleVoiceAgentExtras(Intent intent) {
        if (intent == null) {
            return;
        }
        String voiceSubViolence = intent.getStringExtra("voiceSubViolence");
        String voiceCategoryViolence = intent.getStringExtra("voiceCategoryViolence");
        String voiceAbuseLevel = intent.getStringExtra("voiceAbuseLevel");
        ArrayList<String> voiceViolations = intent.getStringArrayListExtra("voiceViolations");

        if (voiceViolations == null || voiceViolations.isEmpty()) {
            return;
        }

        if (voiceSubViolence != null && !voiceSubViolence.trim().isEmpty()) {
            pendingAutoSubViolence = voiceSubViolence.trim();
            getSubCategoryName = voiceSubViolence.trim();
        }
        if (voiceCategoryViolence != null && !voiceCategoryViolence.trim().isEmpty()) {
            pendingAutoCategoryViolence = voiceCategoryViolence.trim();
            getCategoryViolenceName = voiceCategoryViolence.trim();
        }

        getCategoryViolences = voiceViolations;
        getSelectedSubCategoryViolence = TextUtils.join(", ", voiceViolations);
        getSubCategoryViolenceName = voiceViolations.get(0);
        // use the backend's severity rating (never lower than the number of matched labels)
        voiceRatingFloor = Math.min(17, intent.getIntExtra("voiceRating", 0));
        violenceRating = Math.min(17, Math.max(voiceViolations.size(), voiceRatingFloor));
        activityAbuseBinding.categoryViolenceTextV.setText(getSelectedSubCategoryViolence);
        activityAbuseBinding.segmentedProgress.setProgress(violenceRating);
        updateRatingDisplay();

        String displayLevel = voiceAbuseLevel;
        if (voiceAbuseLevel != null && !voiceAbuseLevel.trim().isEmpty()) {
            String lower = voiceAbuseLevel.toLowerCase();
            if (lower.contains("high")) {
                displayLevel = getString(R.string.abuse_level_high);
            } else if (lower.contains("medium")) {
                displayLevel = getString(R.string.abuse_level_medium);
            } else if (lower.contains("low")) {
                displayLevel = getString(R.string.abuse_level_low);
            }
        }
        String msg;
        if (voiceAbuseLevel != null && !voiceAbuseLevel.trim().isEmpty()) {
            msg = getString(R.string.voice_ai_detected_level_review, displayLevel, violenceRating);
        } else {
            msg = getString(R.string.voice_ai_detected_count_review, violenceRating);
        }
        appUtils.showToast(msg);

        if (pendingAutoSubViolence != null && !pendingAutoSubViolence.isEmpty() && categoryModelArrayList != null && !categoryModelArrayList.isEmpty()) {
            autoSelectSubViolence(pendingAutoSubViolence, pendingAutoCategoryViolence);
        }
    }

    private void autoSelectSubViolence(String targetSubViolence, String targetCategoryViolence) {
        if (targetSubViolence != null && !targetSubViolence.trim().isEmpty()) {
            pendingAutoSubViolence = targetSubViolence.trim();
        }
        if (targetCategoryViolence != null && !targetCategoryViolence.trim().isEmpty()) {
            pendingAutoCategoryViolence = targetCategoryViolence.trim();
        }

        if (categoryModelArrayList == null || categoryModelArrayList.isEmpty()) {
            populateFallbackSubViolence();
        }

        int targetIndex = -1;
        for (int i = 0; i < categoryModelArrayList.size(); i++) {
            SubCategoryModel model = categoryModelArrayList.get(i);
            String name = model.getSubCategoryName();
            if (name != null && (name.equalsIgnoreCase(pendingAutoSubViolence) ||
                name.toLowerCase().contains(pendingAutoSubViolence.toLowerCase()) ||
                pendingAutoSubViolence.toLowerCase().contains(name.toLowerCase()))) {
                targetIndex = i;
                break;
            }
        }

        if (targetIndex != -1) {
            activityAbuseBinding.spinnerSubCategory.setSelection(targetIndex);
            SubCategoryModel selected = categoryModelArrayList.get(targetIndex);
            getSubCategoryId = selected.getSubCategoryId();
            getSubCategoryName = selected.getSubCategoryName();
        } else if (!categoryModelArrayList.isEmpty()) {
            activityAbuseBinding.spinnerSubCategory.setSelection(0);
            SubCategoryModel selected = categoryModelArrayList.get(0);
            getSubCategoryId = selected.getSubCategoryId();
            getSubCategoryName = selected.getSubCategoryName();
        }

        autoSelectCategoryViolence(pendingAutoCategoryViolence);
    }

    private void autoSelectCategoryViolence(String targetCategoryViolence) {
        if (targetCategoryViolence != null && !targetCategoryViolence.trim().isEmpty()) {
            pendingAutoCategoryViolence = targetCategoryViolence.trim();
        }

        if (categoryViolenceModelArrayList == null || categoryViolenceModelArrayList.isEmpty()) {
            populateFallbackCategoryViolence();
        }

        int targetIndex = -1;
        for (int j = 0; j < categoryViolenceModelArrayList.size(); j++) {
            CategoryViolenceModel model = categoryViolenceModelArrayList.get(j);
            String name = model.getViolenceName();
            if (name != null && (name.equalsIgnoreCase(pendingAutoCategoryViolence) ||
                name.toLowerCase().contains(pendingAutoCategoryViolence.toLowerCase()) ||
                pendingAutoCategoryViolence.toLowerCase().contains(name.toLowerCase()))) {
                targetIndex = j;
                break;
            }
        }

        if (targetIndex != -1) {
            activityAbuseBinding.spinnerCategoryViolence.setSelection(targetIndex);
            CategoryViolenceModel selected = categoryViolenceModelArrayList.get(targetIndex);
            getCategoryViolenceId = selected.getViolenceId();
            getCategoryViolenceName = selected.getViolenceName();
        } else if (!categoryViolenceModelArrayList.isEmpty()) {
            activityAbuseBinding.spinnerCategoryViolence.setSelection(0);
            CategoryViolenceModel selected = categoryViolenceModelArrayList.get(0);
            getCategoryViolenceId = selected.getViolenceId();
            getCategoryViolenceName = selected.getViolenceName();
        }
    }

    private void populateInitialSpinners() {
        populateFallbackSubViolence();
        populateFallbackCategoryViolence();
    }

    private void populateFallbackSubViolence() {
        if (categoryModelArrayList == null) {
            categoryModelArrayList = new ArrayList<>();
        }
        if (categoryModelArrayList.isEmpty()) {
            categoryModelArrayList.add(new SubCategoryModel("1", "1", "Domestic Violence", "Intimate Partner Violence"));
            categoryModelArrayList.add(new SubCategoryModel("2", "2", "Domestic Violence", "Family Violence"));
            categoryModelArrayList.add(new SubCategoryModel("3", "3", "Domestic Violence", "Substance Use Disorder"));
        }
        if (subCategoryAdapter == null) {
            subCategoryAdapter = new SubCategoryAdapter(AbuseActivity.this, categoryModelArrayList);
            activityAbuseBinding.spinnerSubCategory.setAdapter(subCategoryAdapter);

            activityAbuseBinding.spinnerSubCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (position >= 0 && position < categoryModelArrayList.size()) {
                        categoryModel = categoryModelArrayList.get(position);
                        getSubCategoryId = categoryModel.getSubCategoryId();
                        getSubCategoryName = categoryModel.getSubCategoryName();
                        getCategoryViolance(getCategoryId, getSubCategoryId, lang);
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        } else {
            subCategoryAdapter.notifyDataSetChanged();
        }
        if (!categoryModelArrayList.isEmpty()) {
            activityAbuseBinding.spinnerSubCategory.setSelection(0);
            getSubCategoryId = categoryModelArrayList.get(0).getSubCategoryId();
            getSubCategoryName = categoryModelArrayList.get(0).getSubCategoryName();
        }
    }

    private void populateFallbackCategoryViolence() {
        if (categoryViolenceModelArrayList == null) {
            categoryViolenceModelArrayList = new ArrayList<>();
        }
        if (categoryViolenceModelArrayList.isEmpty()) {
            categoryViolenceModelArrayList.add(new CategoryViolenceModel("1", "1", "1", "1", "Physical Abuse", "Active"));
            categoryViolenceModelArrayList.add(new CategoryViolenceModel("2", "2", "1", "1", "Emotional Abuse", "Active"));
            categoryViolenceModelArrayList.add(new CategoryViolenceModel("3", "3", "1", "1", "Finacial Abuse", "Active"));
            categoryViolenceModelArrayList.add(new CategoryViolenceModel("4", "4", "1", "1", "Sexual Abuse", "Active"));
            categoryViolenceModelArrayList.add(new CategoryViolenceModel("5", "5", "1", "1", "Social Abuse", "Active"));
            categoryViolenceModelArrayList.add(new CategoryViolenceModel("6", "6", "1", "1", "Varbal Abuse", "Active"));
            categoryViolenceModelArrayList.add(new CategoryViolenceModel("7", "7", "1", "1", "Cultural Abuse", "Active"));
            categoryViolenceModelArrayList.add(new CategoryViolenceModel("8", "8", "1", "1", "Patenal Parental Abuse", "Active"));
        }
        if (categoryViolenceAdapter == null) {
            categoryViolenceAdapter = new CategoryViolenceAdapter(AbuseActivity.this, categoryViolenceModelArrayList);
            activityAbuseBinding.spinnerCategoryViolence.setAdapter(categoryViolenceAdapter);

            activityAbuseBinding.spinnerCategoryViolence.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (position >= 0 && position < categoryViolenceModelArrayList.size()) {
                        categoryViolenceModel = categoryViolenceModelArrayList.get(position);
                        getCategoryViolenceId = categoryViolenceModel.getViolenceId();
                        getCategoryViolenceName = categoryViolenceModel.getViolenceName();
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        } else {
            categoryViolenceAdapter.notifyDataSetChanged();
        }
        if (!categoryViolenceModelArrayList.isEmpty()) {
            activityAbuseBinding.spinnerCategoryViolence.setSelection(0);
            getCategoryViolenceId = categoryViolenceModelArrayList.get(0).getViolenceId();
            getCategoryViolenceName = categoryViolenceModelArrayList.get(0).getViolenceName();
        }
    }

    private void updateRatingDisplay() {
        int rating = violenceRating;
        int color;
        String text;
        if (rating == 0) {
            color = 0xFF666666;
            text = "Rating: 0 / 17 (Tap Sub-Category Violence above)";
        } else if (rating < 5) {
            color = 0xFF2E7D32; // Mild Green
            text = "Violence Rating: " + rating + " / 17 - Mild (கணிவானது)";
        } else if (rating < 10) {
            color = 0xFFF57F17; // Mild Yellow / Amber
            text = "Violence Rating: " + rating + " / 17 - Moderate (மிதமானது - Mild Yellow)";
        } else {
            color = 0xFFD32F2F; // Severe Red
            text = "Violence Rating: " + rating + " / 17 - Severe (தீவிரமானது)";
        }
        activityAbuseBinding.tvViolenceRatingText.setText(text);
        activityAbuseBinding.tvViolenceRatingText.setTextColor(color);
    }

    private JSONArray createContactDetailsJson(int rating, String catName, String subCatName, String catViolName) {
        JSONArray array = new JSONArray();
        try {
            JSONObject obj = new JSONObject();
            String abuseLevel;
            String badge;
            JSONArray parts = new JSONArray();

            if (rating < 5) {
                abuseLevel = "Mild";
                badge = "save_logo.png";
                parts.put("1 - Talk to trusted Peers, Mentors & Family Member");
                parts.put("2 - Strengthen yourself to Ignore & divert");
                parts.put("3 - Assert to Say 'No'");
                parts.put("4 - Use it as a learning Opportunity to cope");
                parts.put("5 - Do not cry, depressed or Shows Signs of Victimisation");
            } else if (rating < 10) {
                abuseLevel = "Moderate";
                badge = "telemanas_logo.png";
                parts.put("1 - Warn the harassar (Husband / in law family to Stop");
                parts.put("2 - Take some chance to record the Harassment");
                parts.put("3 - Have a detailed log of Date of the Harassment, Time, location, witness");
                parts.put("4 - Identify Early & report within family");
                parts.put("5 - 'No' Means 'No'");
                parts.put("6 - Be cautious & aware of external and legal remedy");
                parts.put("7 - https://telemanas.mohf.gov.in/home");
            } else {
                abuseLevel = "Severe";
                badge = "tamilnadu_police_kaaval_uthavi.png";
                parts.put("1 - Consult A Legal Advisor");
                parts.put("2 - Talk To a family therapist Dr. Saranya Sundaraju 9500185855");
                parts.put("3 - Save Counselling 9842868900");
                parts.put("4 - Telemanas Nimhans");
                parts.put("5 - KAVALAN UDANI APP");
                parts.put("6 - NCW Women Helpline 181");
            }

            obj.put("ABUSE_LEVEL", abuseLevel);
            obj.put("VIOLENCE_RATING", rating);
            JSONArray imgArr = new JSONArray();
            imgArr.put(badge);
            obj.put("IMAGE_NAME", imgArr);
            obj.put("PARTICULAR", parts);
            obj.put("VIOLENCE", catName);
            obj.put("SUB_VIOLENCE", subCatName);
            obj.put("CATEGORY", catViolName);
            obj.put("CONTACT_NO", "100");
            obj.put("CONTACT_MAIL_ID", "support@womentrust.org");
            obj.put("CONTACT_WEBSITE", "https://www.womentrust.org/help");

            array.put(obj);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return array;
    }

}