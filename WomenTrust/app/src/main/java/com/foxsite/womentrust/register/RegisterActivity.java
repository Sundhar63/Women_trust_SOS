package com.foxsite.womentrust.register;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import com.foxsite.womentrust.R;
import com.foxsite.womentrust.api_services.ApiService;
import com.foxsite.womentrust.api_services.RetrofitClient;
import com.foxsite.womentrust.app_utills.AppUtils;
import com.foxsite.womentrust.app_utills.LanguageHelper;
import com.foxsite.womentrust.app_utills.LoadingDialog;
import com.foxsite.womentrust.databinding.ActivityRegisterBinding;
import com.foxsite.womentrust.login.LoginActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.gson.Gson;
import com.google.gson.JsonArray;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Array;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    ActivityRegisterBinding activityRegisterBinding;
    AppUtils appUtils;
    LoadingDialog loadingDialog;

    String lang = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityRegisterBinding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(activityRegisterBinding.getRoot());

        this.setTitle(getString(R.string.user_register));
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);

        lang = LanguageHelper.getLanguageCode(this);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        loadingDialog = new LoadingDialog(RegisterActivity.this);
        appUtils = new AppUtils(this);

        activityRegisterBinding.registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (!validateName()) return;
                if (!validateMobile()) return;
                if (!validateAlterMobile()) return;
                if (!validateEmail()) return;
                if (!validateAddress()) return;
                if (!validateAadhar()) return;
                if (!validateUsername()) return;
                if (!validatePassword()) return;

                try {
                    String updateDateTime;
                    try {
                        updateDateTime = appUtils.getCurrentLocalDateTime();
                    } catch (Exception e) {
                        updateDateTime = "";
                    }

                    String _registername = activityRegisterBinding.registernameEdittext.getText().toString().trim();
                    String _mobileNumber = activityRegisterBinding.mobileNumberEdittext.getText().toString().trim();
                    String _alterNumber = activityRegisterBinding.alterNumberEdittext.getText().toString().trim();
                    String _email = activityRegisterBinding.emailEdittext.getText().toString().trim();
                    String _address = activityRegisterBinding.addressEdittext.getText().toString().trim();
                    String _aadhar = activityRegisterBinding.aadharEdittext.getText().toString().trim();
                    String _username = activityRegisterBinding.usernameEdittext.getText().toString().trim();
                    String _password = activityRegisterBinding.passwordEdittext.getText().toString().trim();

                    UserRegister(
                            "LoginCreation",
                            _registername,
                            _mobileNumber,
                            _alterNumber,
                            _email,
                            _aadhar,
                            _address,
                            _username,
                            _password,
                            "000000",
                            "Active",
                            "Admin",
                            "Manager",
                            "",
                            updateDateTime,
                            lang
                    );

                } catch (Exception e) {
                    Log.e("RegisterActivity", "Registration error", e);
                    appUtils.showToast("Registration error: " + e.getMessage());
                }
            }
        });

        activityRegisterBinding.signInTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent loginintent = new Intent(RegisterActivity.this, LoginActivity.class);
                finish();
                startActivity(loginintent);

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

    private void setRegisterLoading(boolean isLoading) {
        if (isLoading) {
            activityRegisterBinding.registerButton.setEnabled(false);
            activityRegisterBinding.registerButton.setText("Registering...");
            activityRegisterBinding.progressBarRegister.setVisibility(View.VISIBLE);
        } else {
            activityRegisterBinding.registerButton.setEnabled(true);
            activityRegisterBinding.registerButton.setText(getString(R.string.submit));
            activityRegisterBinding.progressBarRegister.setVisibility(View.GONE);
        }
    }

    public void UserRegister(
            String actions,
            String name,
            String mobile,
            String alternative_mobile,
            String email,
            String aadhar_no,
            String address,
            String usernames,
            String passwords,
            String device_id,
            String master_status,
            String username,
            String role,
            String updated,
            String updatetime,
            String ln
    ) {
        setRegisterLoading(true);
        ApiService apiService = RetrofitClient.getApiService("");

        UserRegisterModel userRegisterModel = new UserRegisterModel(
                name,
                mobile,
                alternative_mobile,
                email,
                aadhar_no,
                address,
                usernames,
                passwords,
                device_id,
                master_status,
                username,
                role,
                updated,
                updatetime
        );
        List<UserRegisterModel> userRegisterModelArrayList = new ArrayList<>();
        userRegisterModelArrayList.add(userRegisterModel);

        UserRegisterModelRequest userRegisterModelRequest = new UserRegisterModelRequest(
                actions,
                ln,
                userRegisterModelArrayList
        );

        // Never log the registration payload: it contains the password and Aadhaar number.
        Call<JsonArray> call = apiService.userRegDetails(userRegisterModelRequest);
        call.enqueue(new Callback<JsonArray>() {
            @Override
            public void onResponse(Call<JsonArray> call, Response<JsonArray> response) {
                setRegisterLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    JsonArray data = response.body();

                    try {
                        JSONArray jsonArray = new JSONArray(data.toString());
                        JSONObject jsonObject = jsonArray.getJSONObject(0);
                        String status = jsonObject.optString("Status", "");

                        if ("Success".equalsIgnoreCase(status)) {
                            appUtils.showToast(getString(R.string.register_success));

                            Intent loginintent = new Intent(RegisterActivity.this, LoginActivity.class);
                            loginintent.putExtra("registered_username", usernames);
                            loginintent.putExtra("registered_password", passwords);
                            finish();
                            startActivity(loginintent);

                        } else {
                            appUtils.showToast(getString(R.string.register_unsuccess));
                        }

                    } catch (Exception e) {
                        Log.e("json UserRegData_Error", "User registration parsing error", e);
                        appUtils.showToast("Registration error: " + e.getMessage());
                    }

                } else {
                    Log.e("UserRegData_Error", "HTTP " + response.code() + ": " + response.message());
                    appUtils.showToast("Registration failed (HTTP " + response.code() + ")");
                }
            }

            @Override
            public void onFailure(Call<JsonArray> call, Throwable t) {
                setRegisterLoading(false);
                Log.e("UserRegData_Error", "Network error", t);
                String err = (t != null && t.getMessage() != null) ? t.getMessage() : "Unable to reach server";
                appUtils.showToast("Network error: " + err);
            }
        });

    }


    private boolean validateName() {

        String value = activityRegisterBinding
                .registernameEdittext
                .getText().toString().trim();

        if (value.isEmpty()) {
            activityRegisterBinding.registernameLayout
                    .setError(getString(R.string.invalid_name));
            appUtils.showToast("Please enter your Name");
            activityRegisterBinding.registerScrollView.smoothScrollTo(0, activityRegisterBinding.registernameLayout.getTop());
            requestFocus(activityRegisterBinding.registernameEdittext);
            return false;
        } else {
            activityRegisterBinding.registernameLayout
                    .setErrorEnabled(false);
            return true;
        }
    }

    private boolean validateMobile() {
        String value = activityRegisterBinding
                .mobileNumberEdittext
                .getText().toString().trim();

        if (value.isEmpty()) {
            activityRegisterBinding.mobileNumberLayout
                    .setError(getString(R.string.invalid_mobile_number));
            appUtils.showToast("Please enter your Mobile Number");
            activityRegisterBinding.registerScrollView.smoothScrollTo(0, activityRegisterBinding.mobileNumberLayout.getTop());
            requestFocus(activityRegisterBinding.mobileNumberEdittext);
            return false;
        } else if (value.length() != 10) {
            activityRegisterBinding.mobileNumberLayout
                    .setError(getString(R.string.invalid_mobile_length));
            appUtils.showToast("Mobile number must be 10 digits");
            activityRegisterBinding.registerScrollView.smoothScrollTo(0, activityRegisterBinding.mobileNumberLayout.getTop());
            requestFocus(activityRegisterBinding.mobileNumberEdittext);
            return false;
        } else {
            activityRegisterBinding.mobileNumberLayout
                    .setErrorEnabled(false);
            return true;
        }
    }

    private boolean validateAlterMobile() {
        String value = activityRegisterBinding
                .alterNumberEdittext
                .getText().toString().trim();

        // Alternate mobile is optional; if provided it must be 10 digits
        if (!value.isEmpty() && value.length() != 10) {
            activityRegisterBinding.alterNumberLayout
                    .setError(getString(R.string.invalid_mobile_length));
            appUtils.showToast("Alternate mobile must be 10 digits");
            activityRegisterBinding.registerScrollView.smoothScrollTo(0, activityRegisterBinding.alterNumberLayout.getTop());
            requestFocus(activityRegisterBinding.alterNumberEdittext);
            return false;
        } else {
            activityRegisterBinding.alterNumberLayout
                    .setErrorEnabled(false);
            return true;
        }
    }

    private boolean validateEmail() {
        String value = activityRegisterBinding
                .emailEdittext
                .getText().toString().trim();

        if (value.isEmpty()) {
            activityRegisterBinding.emailLayout
                    .setError(getString(R.string.invalid_email));
            appUtils.showToast("Please enter your Email");
            activityRegisterBinding.registerScrollView.smoothScrollTo(0, activityRegisterBinding.emailLayout.getTop());
            requestFocus(activityRegisterBinding.emailEdittext);
            return false;
        } else if (!android.util.Patterns.EMAIL_ADDRESS
                .matcher(value).matches()) {
            activityRegisterBinding.emailLayout
                    .setError(getString(R.string.invalid_email_format));
            appUtils.showToast("Please enter a valid Email address");
            activityRegisterBinding.registerScrollView.smoothScrollTo(0, activityRegisterBinding.emailLayout.getTop());
            requestFocus(activityRegisterBinding.emailEdittext);
            return false;
        } else {
            activityRegisterBinding.emailLayout
                    .setErrorEnabled(false);
            return true;
        }
    }

    private boolean validateAddress() {
        String value = activityRegisterBinding
                .addressEdittext
                .getText().toString().trim();

        if (value.isEmpty()) {
            activityRegisterBinding.addressLayout
                    .setError(getString(R.string.invalid_address));
            appUtils.showToast("Please enter your Address");
            activityRegisterBinding.registerScrollView.smoothScrollTo(0, activityRegisterBinding.addressLayout.getTop());
            requestFocus(activityRegisterBinding.addressEdittext);
            return false;
        } else {
            activityRegisterBinding.addressLayout
                    .setErrorEnabled(false);
            return true;
        }
    }

    private boolean validateAadhar() {
        String value = activityRegisterBinding
                .aadharEdittext
                .getText().toString().trim();

        if (value.isEmpty()) {
            activityRegisterBinding.aadharLayout
                    .setError(getString(R.string.invalid_aadhar));
            appUtils.showToast("Please enter Aadhar Number");
            activityRegisterBinding.registerScrollView.smoothScrollTo(0, activityRegisterBinding.aadharLayout.getTop());
            requestFocus(activityRegisterBinding.aadharEdittext);
            return false;
        } else if (value.length() < 12) {
            activityRegisterBinding.aadharLayout
                    .setError(getString(R.string.invalid_aadhar_length));
            appUtils.showToast("Aadhar Number must be at least 12 digits");
            activityRegisterBinding.registerScrollView.smoothScrollTo(0, activityRegisterBinding.aadharLayout.getTop());
            requestFocus(activityRegisterBinding.aadharEdittext);
            return false;
        } else {
            activityRegisterBinding.aadharLayout
                    .setErrorEnabled(false);
            return true;
        }
    }

    private boolean validateUsername() {
        String value = activityRegisterBinding
                .usernameEdittext
                .getText().toString().trim();

        if (value.isEmpty()) {
            activityRegisterBinding.usernameLayout
                    .setError(getString(R.string.enter_username));
            appUtils.showToast("Please enter a Username");
            activityRegisterBinding.registerScrollView.smoothScrollTo(0, activityRegisterBinding.usernameLayout.getTop());
            requestFocus(activityRegisterBinding.usernameEdittext);
            return false;
        } else {
            activityRegisterBinding.usernameLayout
                    .setErrorEnabled(false);
            return true;
        }
    }

    private boolean validatePassword() {
        String value = activityRegisterBinding
                .passwordEdittext
                .getText().toString().trim();

        if (value.isEmpty()) {
            activityRegisterBinding.passwordLayout
                    .setError(getString(R.string.enter_password));
            appUtils.showToast("Please enter a Password");
            activityRegisterBinding.registerScrollView.smoothScrollTo(0, activityRegisterBinding.passwordLayout.getTop());
            requestFocus(activityRegisterBinding.passwordEdittext);
            return false;
        } else if (value.length() < 6) {
            activityRegisterBinding.passwordLayout
                    .setError(getString(R.string.password_length));
            appUtils.showToast("Password must be at least 6 characters");
            activityRegisterBinding.registerScrollView.smoothScrollTo(0, activityRegisterBinding.passwordLayout.getTop());
            requestFocus(activityRegisterBinding.passwordEdittext);
            return false;
        } else {
            activityRegisterBinding.passwordLayout
                    .setErrorEnabled(false);
            return true;
        }
    }

    private void requestFocus(View view) {

        if (view.requestFocus()) {

            getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
            );
        }
    }

}