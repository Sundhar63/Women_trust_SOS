package com.foxsite.womentrust.api_services;

public class SaveLocationRequest {
    public String Actions = "SaveUserLocation";
    public double Latitude;
    public double Longitude;
    public Float Accuracy;
    public String Provider;
    public String CapturedAt;
    public String DeviceId;

    public SaveLocationRequest(double latitude, double longitude, Float accuracy,
                               String provider, String capturedAt, String deviceId) {
        this.Latitude = latitude;
        this.Longitude = longitude;
        this.Accuracy = accuracy;
        this.Provider = provider;
        this.CapturedAt = capturedAt;
        this.DeviceId = deviceId;
    }
}
