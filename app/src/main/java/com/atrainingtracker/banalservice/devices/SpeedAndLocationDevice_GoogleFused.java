package com.atrainingtracker.banalservice.devices;

import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import android.content.pm.PackageManager;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

public class SpeedAndLocationDevice_GoogleFused extends SpeedAndLocationDevice
        implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "SpeedAndLocationDevice_GoogleFused";
    private static final boolean DEBUG = BANALService.DEBUG & false;

    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Context context;

    public SpeedAndLocationDevice_GoogleFused(Context context, MySensorManager mySensorManager) {
        super(context, mySensorManager, DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED);
        this.context = context;
        if (DEBUG) {
            Log.d(TAG, "constructor");
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);

        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(SAMPLING_TIME);
        // Allow the fused provider to deliver fixes faster than setInterval() so speed = d/dt stays responsive.
        mLocationRequest.setFastestInterval(Math.max(100, SAMPLING_TIME / 2));

        // Initialize location callback to handle location updates
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult != null) {
                    for (Location location : locationResult.getLocations()) {
                        if (DEBUG) Log.d(TAG, "onLocationChanged");
                        onNewLocation(location);
                    }
                }
            }
        };

        // Connect the client
        mGoogleApiClient.connect();
    }

    @Override
    public String getName() {
        return "google_fused"; // Maintain compatibility with pre 3.8 way
    }

    @Override
    public void onConnected(Bundle dataBundle) {
        if (DEBUG) Log.d(TAG, "onConnected()");

        // Check for location permissions before requesting updates
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permissions are not granted.");
            // Request necessary permissions from the user or handle accordingly
            return;
        }

        // Request location updates using the modern FusedLocationProviderClient
        fusedLocationClient.requestLocationUpdates(mLocationRequest, locationCallback, Looper.getMainLooper());
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "GoogleApiClient connection has been suspended.");
        LocationUnavailable();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.e(TAG, "GoogleApiClient connection has failed: " + connectionResult.getErrorMessage());
        // Optional: Implement retry logic or notify the user that location services are unavailable
    }

    @Override
    public void shutDown() {
        if (fusedLocationClient != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }

        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }

        super.shutDown();
    }
}
