package com.foxsite.womentrust.app_utills;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

/**
 * Small wrapper around {@link LocationManager} that checks permission/state and
 * resolves a single location fix without pulling in Play Services.
 */
public class LocationHelper {

    private static final long FIX_TIMEOUT_MS = 20_000L;

    public interface Callback {
        void onLocation(Location location);

        void onFailure(String reason);
    }

    private final Context context;
    private final LocationManager locationManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public LocationHelper(Context context) {
        this.context = context.getApplicationContext();
        this.locationManager = (LocationManager) this.context.getSystemService(Context.LOCATION_SERVICE);
    }

    public boolean hasPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public boolean isLocationEnabled() {
        if (locationManager == null) {
            return false;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return locationManager.isLocationEnabled();
            }
            return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) {
            return false;
        }
    }

    public void requestSingleLocation(final Callback callback) {
        if (!hasPermission()) {
            callback.onFailure("permission");
            return;
        }
        if (locationManager == null || !isLocationEnabled()) {
            callback.onFailure("disabled");
            return;
        }

        final String provider = bestProvider();
        final boolean[] finished = {false};

        final Runnable timeout = () -> {
            if (finished[0]) {
                return;
            }
            finished[0] = true;
            Location last = lastKnownLocation(provider);
            if (last != null) {
                callback.onLocation(last);
            } else {
                callback.onFailure("timeout");
            }
        };
        handler.postDelayed(timeout, FIX_TIMEOUT_MS);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            final CancellationSignal cancellationSignal = new CancellationSignal();
            try {
                locationManager.getCurrentLocation(provider, cancellationSignal,
                        context.getMainExecutor(), location -> {
                            handler.removeCallbacks(timeout);
                            if (finished[0]) {
                                return;
                            }
                            finished[0] = true;
                            if (location != null) {
                                callback.onLocation(location);
                                return;
                            }
                            Location last = lastKnownLocation(provider);
                            if (last != null) {
                                callback.onLocation(last);
                            } else {
                                callback.onFailure("unavailable");
                            }
                        });
            } catch (Exception e) {
                handler.removeCallbacks(timeout);
                if (!finished[0]) {
                    finished[0] = true;
                    Location last = lastKnownLocation(provider);
                    if (last != null) {
                        callback.onLocation(last);
                    } else {
                        callback.onFailure("unavailable");
                    }
                }
            }
            return;
        }

        requestSingleUpdateLegacy(provider, callback, timeout, finished);
    }

    @SuppressWarnings("deprecation")
    private void requestSingleUpdateLegacy(String provider, Callback callback, Runnable timeout, boolean[] finished) {
        LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                handler.removeCallbacks(timeout);
                if (finished[0]) {
                    return;
                }
                finished[0] = true;
                try {
                    locationManager.removeUpdates(this);
                } catch (Exception ignored) {
                    // Provider may already be gone.
                }
                callback.onLocation(location);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }
        };

        try {
            locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper());
        } catch (Exception e) {
            handler.removeCallbacks(timeout);
            if (!finished[0]) {
                finished[0] = true;
                Location last = lastKnownLocation(provider);
                if (last != null) {
                    callback.onLocation(last);
                } else {
                    callback.onFailure("unavailable");
                }
            }
        }
    }

    private String bestProvider() {
        try {
            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            criteria.setPowerRequirement(Criteria.POWER_LOW);
            String provider = locationManager.getBestProvider(criteria, true);
            if (provider != null) {
                return provider;
            }
        } catch (Exception ignored) {
            // Fall through to the explicit checks below.
        }
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            return LocationManager.GPS_PROVIDER;
        }
        return LocationManager.NETWORK_PROVIDER;
    }

    private Location lastKnownLocation(String provider) {
        try {
            Location best = null;
            for (String candidate : new String[]{provider, LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                Location location = locationManager.getLastKnownLocation(candidate);
                if (location != null && (best == null || location.getTime() > best.getTime())) {
                    best = location;
                }
            }
            return best;
        } catch (Exception e) {
            return null;
        }
    }
}
