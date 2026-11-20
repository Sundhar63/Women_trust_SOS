package com.foxsite.womentrust;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.os.LocaleListCompat;

import com.foxsite.womentrust.abuse.AbuseActivity;
import com.foxsite.womentrust.api_services.ApiService;
import com.foxsite.womentrust.api_services.RetrofitClient;
import com.foxsite.womentrust.app_utills.AppUtils;
import com.foxsite.womentrust.app_utills.LanguageHelper;
import com.foxsite.womentrust.app_utills.LoadingDialog;
import com.foxsite.womentrust.databinding.ActivityMainBinding;
import com.foxsite.womentrust.login.LoginActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.ArrayList;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    ActivityMainBinding activityMainBinding;
    AppUtils appUtils;
    LoadingDialog loadingDialog;
    SharedPreferences sharedPreferences;

    String userID = "";
    String username = "";
    String name = "";
    String mobile = "";
    String email = "";
    String role = "";
    String roleId = "";

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private MediaRecorder mediaRecorder;
    private String audioFilePath;
    private boolean isRecording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityMainBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(activityMainBinding.getRoot());

        sharedPreferences = getSharedPreferences(getString(R.string.sharedpref_name), Context.MODE_PRIVATE);

        this.setTitle(getString(R.string.app_name));
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        if (toolbar != null) {
            toolbar.setTitle(getString(R.string.app_name));
            setSupportActionBar(toolbar);
            toolbar.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.menu_language) {
                    showLanguageDialog();
                    return true;
                }
                return false;
            });
        }

        String lang = LanguageHelper.getLanguageCode(this);
        LanguageHelper.setLocale(this, lang);

        loadingDialog = new LoadingDialog(MainActivity.this);
        appUtils = new AppUtils(this);

        userID = sharedPreferences.getString(getString(R.string.userId), "");
        username = sharedPreferences.getString(getString(R.string.username), "");
        name = sharedPreferences.getString(getString(R.string.name), "");
        mobile = sharedPreferences.getString(getString(R.string.mobile), "");
        email = sharedPreferences.getString(getString(R.string.email), "");
        roleId = sharedPreferences.getString(getString(R.string.role), "");

        activityMainBinding.userNameTV.setText(name);
        activityMainBinding.emailTV.setText(mobile);


        activityMainBinding.domesticViolenceCardLayout.setOnClickListener(this);
        activityMainBinding.violenceWrkPCardLayout.setOnClickListener(this);
        activityMainBinding.violenceBublicPCardLayoutCardLayout.setOnClickListener(this);
        activityMainBinding.childAbuseLayout.setOnClickListener(this);
        activityMainBinding.logoutText.setOnClickListener(this);

        activityMainBinding.btnMicRecord.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
            } else {
                if (!isRecording) {
                    startAudioRecording();
                } else {
                    stopAudioRecordingAndProcess();
                }
            }
        });

        activityMainBinding.btnMicRecord.setOnLongClickListener(v -> {
            File sample = new File(getCacheDir(), "test_voice.mp4");
            if (sample.exists()) {
                appUtils.showToast(getString(R.string.voice_demo_testing));
                uploadAudioToBackend(sample);
                return true;
            }
            return false;
        });
    }

    public void getCategoryActivity(String categoryId, String categoryName) {
        Intent intent = new Intent(MainActivity.this, AbuseActivity.class);
        intent.putExtra("categoryId", categoryId);
        intent.putExtra("categoryName", categoryName);
        startActivity(intent);
    }

    @Override
    public void onClick(View v) {


        if (v.getId() == activityMainBinding.domesticViolenceCardLayout.getId()) {
            getCategoryActivity("1", getString(R.string.domestic_violence).trim());
        } else if (v.getId() == activityMainBinding.violenceWrkPCardLayout.getId()) {
            getCategoryActivity("2", getString(R.string.violence_work_place).trim());
        } else if (v.getId() == activityMainBinding.violenceBublicPCardLayoutCardLayout.getId()) {
            getCategoryActivity("3", getString(R.string.violence_public_place).trim());
        } else if (v.getId() == activityMainBinding.childAbuseLayout.getId()) {
            getCategoryActivity("4", getString(R.string.child_sexual_abuse).trim());
        } else if (v.getId() == activityMainBinding.logoutText.getId()) {
            logoutInfoAlert();
        }

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        if (item.getItemId() == R.id.menu_language) {

            showLanguageDialog();

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showLanguageDialog() {

        String[] languageList = {
                "English",
                "தமிழ் (Tamil)"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle(getString(R.string.change_language));

        builder.setItems(languageList, (dialog, which) -> {

            if (which == 0) {

                setLocale("en");

            } else {

                setLocale("ta");
            }

        });

        builder.show();
    }

    private void setLocale1(String languageCode) {

        LocaleListCompat appLocale = LocaleListCompat.forLanguageTags(languageCode);
        AppCompatDelegate.setApplicationLocales(appLocale);

        Intent intent = new Intent(MainActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void setLocale(String languageCode) {
        LanguageHelper.setLocale(this, languageCode);

        Intent intent = new Intent(MainActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    public void logoutInfoAlert() {
        AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle(getString(R.string.logout));
        alertDialog.setMessage(getString(R.string.logout_alert));
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.yes_text),
                new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.clear();
                        editor.commit();

                        Intent mainIntent = new Intent(MainActivity.this, LoginActivity.class);
                        finish();
                        startActivity(mainIntent);
                    }
                });
        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.no_text),
                new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        alertDialog.cancel();
                    }
                });

        alertDialog.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startAudioRecording();
            } else {
                appUtils.showToast(getString(R.string.voice_permission_required));
            }
        }
    }

    private void startAudioRecording() {
        audioFilePath = getCacheDir().getAbsolutePath() + "/voice_record_" + System.currentTimeMillis() + ".mp4";
        mediaRecorder = new MediaRecorder();
        try {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioSamplingRate(16000);
            mediaRecorder.setAudioEncodingBitRate(64000);
            mediaRecorder.setOutputFile(audioFilePath);
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;

            activityMainBinding.btnMicRecord.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CAF50")));
            activityMainBinding.tvVoiceStatus.setText(getString(R.string.voice_recording));
            activityMainBinding.tvVoiceStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            activityMainBinding.tvVoiceTranscription.setVisibility(View.GONE);
            appUtils.showToast(getString(R.string.voice_recording_started));
        } catch (Exception e) {
            Log.e("AudioRecord", "prepare() failed", e);
            String err = e.getMessage() != null ? e.getMessage() : "";
            appUtils.showToast(getString(R.string.voice_failed_start, err));
        }
    }

    private void stopAudioRecordingAndProcess() {
        if (mediaRecorder != null && isRecording) {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
                isRecording = false;

                activityMainBinding.btnMicRecord.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E91E63")));
                activityMainBinding.tvVoiceStatus.setText(getString(R.string.voice_processing));
                activityMainBinding.tvVoiceStatus.setTextColor(ContextCompat.getColor(this, android.R.color.black));
                appUtils.showToast(getString(R.string.voice_recording_stopped));

                File audioFile = new File(audioFilePath);
                if (audioFile.exists() && audioFile.length() > 3500) {
                    uploadAudioToBackend(audioFile);
                } else {
                    String warn = getString(R.string.voice_no_audio);
                    activityMainBinding.tvVoiceStatus.setText(warn);
                    activityMainBinding.tvVoiceStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
                    appUtils.showToast(warn);
                }
            } catch (Exception e) {
                Log.e("AudioRecord", "stop() failed", e);
                isRecording = false;
                mediaRecorder = null;
                activityMainBinding.btnMicRecord.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E91E63")));
                String err = e.getMessage() != null ? e.getMessage() : "";
                appUtils.showToast(getString(R.string.voice_failed_stop, err));
            }
        }
    }

    private void uploadAudioToBackend(File audioFile) {
        activityMainBinding.progressBarVoice.setVisibility(View.VISIBLE);
        activityMainBinding.tvVoiceStatus.setText(getString(R.string.voice_analyzing));
        activityMainBinding.tvVoiceStatus.setTextColor(ContextCompat.getColor(this, android.R.color.black));

        RequestBody requestFile = RequestBody.create(MediaType.parse("audio/*"), audioFile);
        MultipartBody.Part body = MultipartBody.Part.createFormData("audio_file", audioFile.getName(), requestFile);

        ApiService apiService = RetrofitClient.getApiService("");
        Call<JsonObject> call = apiService.processAudio(body);
        call.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                activityMainBinding.progressBarVoice.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    JsonObject responseBody = response.body();
                    Log.d("VoiceAI_Response", responseBody.toString());

                    if (responseBody.has("success") && !responseBody.get("success").getAsBoolean()) {
                        String msg = getString(R.string.voice_feature_unavailable);
                        activityMainBinding.tvVoiceStatus.setText(msg);
                        activityMainBinding.tvVoiceStatus.setTextColor(ContextCompat.getColor(MainActivity.this, android.R.color.holo_red_dark));
                        appUtils.showToast(msg);
                        return;
                    }

                    if (responseBody.has("transcription") && !responseBody.get("transcription").isJsonNull()) {
                        String transcription = responseBody.get("transcription").getAsString();
                        activityMainBinding.tvVoiceTranscription.setVisibility(View.VISIBLE);
                        activityMainBinding.tvVoiceTranscription.setText(getString(R.string.voice_transcript_prefix, transcription));
                    }

                    if (responseBody.has("selected_checkbox") && !responseBody.get("selected_checkbox").isJsonNull()) {
                        JsonObject selectedObj = responseBody.getAsJsonObject("selected_checkbox");
                        JsonObject classification = null;
                        String matchedSubViolence = "Intimate Partner Violence";
                        String matchedCategoryViolence = "Physical Abuse";
                        String matchedViolence = "Domestic Violence";

                        if (selectedObj.has("classification") && !selectedObj.get("classification").isJsonNull()) {
                            classification = selectedObj.getAsJsonObject("classification");
                            if (classification.has("sub_violence") && !classification.get("sub_violence").isJsonNull()) {
                                matchedSubViolence = classification.get("sub_violence").getAsString();
                            }
                            if (classification.has("category_violence") && !classification.get("category_violence").isJsonNull()) {
                                matchedCategoryViolence = classification.get("category_violence").getAsString();
                            }
                            if (classification.has("violence") && !classification.get("violence").isJsonNull()) {
                                matchedViolence = classification.get("violence").getAsString();
                            } else if (classification.has("voilence") && !classification.get("voilence").isJsonNull()) {
                                matchedViolence = classification.get("voilence").getAsString();
                            }
                        }

                        com.google.gson.JsonArray violationsArray = null;
                        if (selectedObj.has("selected_violations") && selectedObj.get("selected_violations").isJsonArray()) {
                            violationsArray = selectedObj.getAsJsonArray("selected_violations");
                        } else if (selectedObj.has("matched_sub_category_violences") && selectedObj.get("matched_sub_category_violences").isJsonArray()) {
                            violationsArray = selectedObj.getAsJsonArray("matched_sub_category_violences");
                        } else if (classification != null && classification.has("matched_sub_category_violences") && classification.get("matched_sub_category_violences").isJsonArray()) {
                            violationsArray = classification.getAsJsonArray("matched_sub_category_violences");
                        }

                        ArrayList<String> voiceViolations = new ArrayList<>();
                        if (violationsArray != null && violationsArray.size() > 0) {
                            String lang = LanguageHelper.getLanguageCode(MainActivity.this);
                            for (int i = 0; i < violationsArray.size(); i++) {
                                String v = violationsArray.get(i).getAsString();
                                if (v != null && !v.isEmpty() && !"ta".equalsIgnoreCase(lang)) {
                                    v = v.substring(0, 1).toUpperCase() + v.substring(1);
                                }
                                voiceViolations.add(v);
                            }
                        }

                        String detectedLevel = "";
                        if (selectedObj.has("abuse_level") && !selectedObj.get("abuse_level").isJsonNull()) {
                            detectedLevel = selectedObj.get("abuse_level").getAsString();
                        }

                        // Severity-based rating computed by the backend (a single rape / child abuse label is already severe).
                        // Fall back to the number of matched labels for older backends.
                        int serverRating = voiceViolations.size();
                        if (selectedObj.has("violence_rating") && !selectedObj.get("violence_rating").isJsonNull()) {
                            serverRating = Math.max(serverRating, selectedObj.get("violence_rating").getAsInt());
                        }

                        // The backend returns the real violence code, which is the app's categoryId.
                        String serverCategoryId = null;
                        if (selectedObj.has("codes") && selectedObj.get("codes").isJsonObject()) {
                            JsonObject codes = selectedObj.getAsJsonObject("codes");
                            if (codes.has("violence_code") && !codes.get("violence_code").isJsonNull()) {
                                serverCategoryId = codes.get("violence_code").getAsString();
                            }
                        }

                        if (!voiceViolations.isEmpty()) {
                            String categoryId = "1";
                            String localizedViolence = getString(R.string.domestic_violence).trim();
                            if (matchedViolence != null) {
                                String lower = matchedViolence.toLowerCase();
                                if (lower.contains("work")) {
                                    categoryId = "2";
                                    localizedViolence = getString(R.string.violence_work_place).trim();
                                } else if (lower.contains("public")) {
                                    categoryId = "3";
                                    localizedViolence = getString(R.string.violence_public_place).trim();
                                } else if (lower.contains("child")) {
                                    categoryId = "4";
                                    localizedViolence = getString(R.string.child_sexual_abuse).trim();
                                } else {
                                    categoryId = "1";
                                    localizedViolence = getString(R.string.domestic_violence).trim();
                                }
                            }
                            if (serverCategoryId != null && !serverCategoryId.isEmpty()) {
                                categoryId = serverCategoryId;
                                if (matchedViolence != null && !matchedViolence.trim().isEmpty()) {
                                    localizedViolence = matchedViolence.trim();
                                }
                            }
                            openAbuseReport(categoryId, localizedViolence, matchedSubViolence, matchedCategoryViolence, voiceViolations, detectedLevel, serverRating);
                        } else {
                            String noViolationsMsg = getString(R.string.voice_no_violations);
                            activityMainBinding.tvVoiceStatus.setText(noViolationsMsg);
                            activityMainBinding.tvVoiceStatus.setTextColor(ContextCompat.getColor(MainActivity.this, android.R.color.holo_red_dark));
                            appUtils.showToast(noViolationsMsg);
                        }
                    }
                } else {
                    String msg = getString(R.string.voice_speech_not_detected);
                    activityMainBinding.tvVoiceStatus.setText(msg);
                    activityMainBinding.tvVoiceStatus.setTextColor(ContextCompat.getColor(MainActivity.this, android.R.color.holo_red_dark));
                    appUtils.showToast(msg);
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                activityMainBinding.progressBarVoice.setVisibility(View.GONE);
                Log.e("VoiceAI_Error", t.toString());
                String netError = (t.getMessage() != null && !t.getMessage().isEmpty()) ? t.getMessage() : getString(R.string.voice_network_default);
                String msg = getString(R.string.voice_network_error, netError);
                activityMainBinding.tvVoiceStatus.setText(msg);
                activityMainBinding.tvVoiceStatus.setTextColor(ContextCompat.getColor(MainActivity.this, android.R.color.holo_red_dark));
                appUtils.showToast(msg);
            }
        });
    }

    private String localizeAbuseLevel(String level) {
        if (level == null || level.trim().isEmpty()) return "";
        String lower = level.trim().toLowerCase();
        if (lower.contains("high")) {
            return getString(R.string.abuse_level_high);
        } else if (lower.contains("medium")) {
            return getString(R.string.abuse_level_medium);
        } else if (lower.contains("low")) {
            return getString(R.string.abuse_level_low);
        }
        return level;
    }

    private void openAbuseReport(String categoryId, String violence, String subViolence, String categoryViolence, ArrayList<String> violations, String abuseLevel, int rating) {
        String displayLevel = localizeAbuseLevel(abuseLevel);
        String statusMsg;
        if (!displayLevel.isEmpty()) {
            statusMsg = getString(R.string.voice_ai_detected_level, displayLevel, rating);
        } else {
            statusMsg = getString(R.string.voice_ai_detected_count, rating);
        }
        activityMainBinding.tvVoiceStatus.setText(statusMsg);
        activityMainBinding.tvVoiceStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
        appUtils.showToast(getString(R.string.voice_opening_report, rating));

        Intent intent = new Intent(MainActivity.this, AbuseActivity.class);
        intent.putExtra("categoryId", categoryId);
        intent.putExtra("categoryName", violence);
        intent.putExtra("voiceSubViolence", subViolence);
        intent.putExtra("voiceCategoryViolence", categoryViolence);
        intent.putExtra("voiceViolations", violations);
        intent.putExtra("voiceAbuseLevel", abuseLevel);
        intent.putExtra("voiceRating", rating);
        startActivity(intent);
    }
}