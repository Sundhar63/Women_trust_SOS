package com.foxsite.womentrust.login;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.foxsite.womentrust.R;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.foxsite.womentrust.MainActivity;
import com.foxsite.womentrust.R;
import com.foxsite.womentrust.api_services.ApiService;
import com.foxsite.womentrust.api_services.RetrofitClient;
import com.foxsite.womentrust.app_utills.AppUtils;
import com.foxsite.womentrust.app_utills.LoadingDialog;
import com.foxsite.womentrust.app_utills.SessionManager;
import com.foxsite.womentrust.register.RegisterActivity;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonArray;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    TextInputLayout input_layout_password, input_layout_username;
    EditText editusername;
    EditText editpassword;
    Button loginBT;
    TextView signUpTextView;
    ProgressBar progressBarLogin;

    ProgressDialog progressDialog;

    boolean loginStatus;
    String message;
    String data;
    String usertype;
    String user_id;

    int backpress = 0;
    AppUtils appUtils = new AppUtils(this);
    LoadingDialog loadingDialog;
    SharedPreferences sharedPreferences;
    String LOGINSTATUS;
    String USERTYPE;

    boolean connStatus = false;
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        sharedPreferences = getSharedPreferences(getString(R.string.sharedpref_name), Context.MODE_PRIVATE);
        boolean isLoggedIn = sharedPreferences.getBoolean(getString(R.string.login_status), false)
                || SessionManager.get(this).hasSession();
        if (isLoggedIn) {
            Intent mainIntent = new Intent(LoginActivity.this, MainActivity.class);
            finish();
            startActivity(mainIntent);
            return;
        }
        loadingDialog = new LoadingDialog(this);

        editusername = findViewById(R.id.username_edittext);
        editpassword = findViewById(R.id.password_edittext);
        signUpTextView = findViewById(R.id.signUpTextView);
        progressBarLogin = findViewById(R.id.progressBarLogin);

        input_layout_username = findViewById(R.id.username_layout);
        input_layout_password = findViewById(R.id.password_layout);
        loginBT = findViewById(R.id.LoginButton);

        this.setTitle("Login");
        progressDialog = new ProgressDialog(LoginActivity.this);
        progressDialog.setMessage("Loading..");
        progressDialog.setCancelable(false);

        // Pre-fill registered credentials if arriving from RegisterActivity
        if (getIntent() != null && getIntent().hasExtra("registered_username")) {
            String regUser = getIntent().getStringExtra("registered_username");
            String regPass = getIntent().getStringExtra("registered_password");
            if (regUser != null) editusername.setText(regUser);
            if (regPass != null) editpassword.setText(regPass);
            appUtils.showToast("Account created! Tap Sign In to continue.");
        }

        editusername.addTextChangedListener(new MyTextWatcher(editusername));
        editpassword.addTextChangedListener(new MyTextWatcher(editpassword));

        loginBT.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (isNetworkAvailable()) {
                    String str_username = editusername.getText().toString().trim();
                    String str_password = editpassword.getText().toString().trim();
                    submitForm(str_username, str_password);
                } else {
                    infoAlert(getString(R.string.check_network_connection));
                }

            }
        });

        signUpTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Intent loginintent = new Intent(LoginActivity.this, RegisterActivity.class);
                startActivity(loginintent);

            }
        });

    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.hasExtra("registered_username")) {
            String regUser = intent.getStringExtra("registered_username");
            String regPass = intent.getStringExtra("registered_password");
            if (regUser != null) editusername.setText(regUser);
            if (regPass != null) editpassword.setText(regPass);
            appUtils.showToast("Account created! Tap Sign In to continue.");
        }
    }

    private void submitForm(String str_username, String str_password) {
        if (!validateUsername()) {
            return;
        }
        if (!validatePassword()) {
            return;
        }

        loginProcess(str_username, str_password,"ta");

//        if ("Admin".equals(str_username)) {
//
//            Intent loginintent = new Intent(LoginActivity.this, MainActivity.class);
//            finish();
//            startActivity(loginintent);
//
//            SharedPreferences.Editor editor = sharedPreferences.edit();
//            editor.putBoolean("login_status", true);
//            editor.putString("user_id", "");
//            editor.commit();
//
//            Toast.makeText(LoginActivity.this, "Login Successfully", Toast.LENGTH_SHORT).show();
//        } else {
//            Toast.makeText(LoginActivity.this, "Invalid Credential!", Toast.LENGTH_SHORT).show();
//        }

    }

//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//        getMenuInflater().inflate(R.menu.setting_menu, menu);
//        return true;
//    }

//    @Override
//    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
//
//        if (item.getItemId() == R.id.settings_item) {
//            Intent intent = new Intent(LoginActivity.this, IpSettingsActivity.class);
//            startActivity(intent);
//        }
//        return super.onOptionsItemSelected(item);
//    }


    private void setLoginLoading(boolean isLoading) {
        if (isLoading) {
            loginBT.setEnabled(false);
            loginBT.setText("Signing In...");
            if (progressBarLogin != null) progressBarLogin.setVisibility(View.VISIBLE);
        } else {
            loginBT.setEnabled(true);
            loginBT.setText(getString(R.string.sign_in));
            if (progressBarLogin != null) progressBarLogin.setVisibility(View.GONE);
        }
    }

    public void loginProcess(
            String userName,
            String password,
            String ln
    ) {
        setLoginLoading(true);
        ApiService apiService = RetrofitClient.getApiService("");
        SessionManager sessionManager = SessionManager.get(this);
        LoginRequest loginObject = new LoginRequest(
                "CheckLogin", userName, password, sessionManager.getDeviceId(), ln);
        Call<JsonArray> call = apiService.login(loginObject);
        call.enqueue(new Callback<JsonArray>() {
            @Override
            public void onResponse(Call<JsonArray> call, Response<JsonArray> response) {
                setLoginLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    JsonArray data = response.body();

                    try {
                        JSONArray jsonArray = new JSONArray(data.toString());
                        JSONObject jsonObject = jsonArray.getJSONObject(0);
                        String status = jsonObject.optString("Status", "");

                        if ("Success".equalsIgnoreCase(status)) {
                            appUtils.showToast(getString(R.string.login_successful));

                            // Access + refresh token pair from the backend.
                            String token = jsonObject.optString("Token", "");
                            String refreshToken = jsonObject.optString("RefreshToken", "");
                            long expiresIn = jsonObject.optLong("ExpiresIn", 1800L);
                            if (token.isEmpty()) {
                                token = jsonObject.optString("access_token", "");
                            }
                            if (refreshToken.isEmpty()) {
                                refreshToken = jsonObject.optString("refresh_token", "");
                            }

                            JSONArray loginDataJSONArray = jsonObject.optJSONArray("LOGIN_MASTERs");
                            if (loginDataJSONArray != null && loginDataJSONArray.length() > 0) {
                                JSONObject loginDataJSONObject = loginDataJSONArray.getJSONObject(0);

                                String _userId = loginDataJSONObject.optString("ID", "");
                                String _userCode = loginDataJSONObject.optString("CODE", "");
                                String _name = loginDataJSONObject.optString("NAME", "");
                                String _username = loginDataJSONObject.optString("USERNAMES", "");
                                String _mobile = loginDataJSONObject.optString("MOBILE", "");
                                String _altermobile = loginDataJSONObject.optString("ALTERNATIVE_MOBILE", "");
                                String _email = loginDataJSONObject.optString("EMAIL", "");
                                String _role = loginDataJSONObject.optString("ROLE", "");
                                String _status = loginDataJSONObject.optString("MASTER_STATUS", "");
                                String _dateCreated = loginDataJSONObject.optString("UPDATETIME", "");
                                String _dateUpdated = loginDataJSONObject.optString("UPDATED", "");
                                String _updatedBy = loginDataJSONObject.optString("USERNAME", "");

                                SharedPreferences.Editor editor = sharedPreferences.edit();

                                editor.putString(getString(R.string.userId), _userId);
                                editor.putString(getString(R.string.userCode), _userCode);
                                editor.putString(getString(R.string.name), _name);
                                editor.putString(getString(R.string.username), _username);
                                editor.putString(getString(R.string.mobile), _mobile);
                                editor.putString(getString(R.string.email), _email);
                                editor.putString(getString(R.string.role), _role);
                                editor.putString(getString(R.string.status), _status);
                                editor.putString(getString(R.string.dateCreated), _dateCreated);
                                editor.putString(getString(R.string.dateUpdated), _dateUpdated);
                                editor.putString(getString(R.string.updatedBy), _updatedBy);

                                // Never store the password locally.
                                editor.remove(getString(R.string.password));

                                editor.putBoolean(getString(R.string.login_status), true);
                                if (!token.isEmpty()) {
                                    editor.putString("jwt_token", token);
                                }
                                if (!refreshToken.isEmpty()) {
                                    editor.putString("refresh_token", refreshToken);
                                }
                                editor.apply();

                                if (!token.isEmpty() && !refreshToken.isEmpty()) {
                                    sessionManager.saveSession(token, refreshToken, expiresIn);
                                }

                                Intent mainIntent = new Intent(LoginActivity.this, MainActivity.class);
                                finish();
                                startActivity(mainIntent);
                            } else {
                                appUtils.showToast("User details not found.");
                            }

                        } else {
                            appUtils.showToast("Invalid credentials. If you are a new user, please Sign Up first!");
                        }

                    } catch (Exception e) {
                        Log.e("json Login_Error", "Login parsing error", e);
                        appUtils.showToast("Login error: " + e.getMessage());
                    }

                } else {
                    Log.e("Login_Response_Error", "HTTP " + response.code() + ": " + response.message());
                    appUtils.showToast("Server error (HTTP " + response.code() + "). Please try again.");
                }
            }

            @Override
            public void onFailure(Call<JsonArray> call, Throwable t) {
                setLoginLoading(false);
                Log.e("Login_Error", "Network error", t);
                String err = (t != null && t.getMessage() != null) ? t.getMessage() : "Unable to reach server";
                appUtils.showToast("Network error: " + err);
            }
        });

    }

    private boolean validateUsername() {
        if (editusername.getText().toString().trim().isEmpty()) {
            input_layout_username.setError(getString(R.string.invalid_username));
            requestFocus(editusername);
            return false;
        } else {
            input_layout_username.setErrorEnabled(false);
        }

        return true;
    }

    private boolean validatePassword() {
        if (editpassword.getText().toString().trim().isEmpty()) {
            input_layout_password.setError(getString(R.string.invalid_password));
            requestFocus(editpassword);
            return false;
        } else {
            input_layout_password.setErrorEnabled(false);
        }

        return true;
    }

    private void requestFocus(View view) {
        if (view.requestFocus()) {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
    }

    private class MyTextWatcher implements TextWatcher {

        private View view;

        private MyTextWatcher(View view) {
            this.view = view;
        }

        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        public void afterTextChanged(Editable editable) {
            int i = view.getId();
            if (i == R.id.username_edittext) {
                validateUsername();
            }

            if (i == R.id.password_edittext) {
                validatePassword();
            }
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        backpress = (backpress + 1);
        Toast.makeText(getApplicationContext(), getString(R.string.exit_info), Toast.LENGTH_SHORT).show();

        if (backpress > 1) {
            this.finish();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            moveTaskToBack(true);
        }
        return super.onKeyDown(keyCode, event);


    }

    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager == null) {
                return true;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network network = connectivityManager.getActiveNetwork();
                if (network != null) {
                    NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
                    if (capabilities != null) {
                        if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                                || capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                            return true;
                        }
                    }
                }
            }
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            if (networkInfo != null) {
                return networkInfo.isConnectedOrConnecting();
            }
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    private void infoAlert(String info) {
        AlertDialog alertDialog = new AlertDialog.Builder(LoginActivity.this).create();
        alertDialog.setMessage(info);
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.ok_text),
                new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

        alertDialog.show();
    }

}

