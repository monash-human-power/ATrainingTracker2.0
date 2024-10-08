/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (C) 2011 - 2019 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.banalservice.devices;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.sensor.MySensorManager;


public class SpeedAndLocationDevice_GPS extends SpeedAndLocationDevice
        implements LocationListener {
    private static final String TAG = "SAND_GPS";
    private static final boolean DEBUG = BANALService.DEBUG & false;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private Context context;


    LocationManager mLocationManager;

    public SpeedAndLocationDevice_GPS(Context context, MySensorManager mySensorManager) {
        super(context, mySensorManager, DeviceType.SPEED_AND_LOCATION_GPS);
        this.context = context;
        if (DEBUG) {
            Log.d(TAG, "constructor");
        }

        mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        // Permissions are granted, request location updates
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, SAMPLING_TIME, MIN_DISTANCE, this);
    }

    @Override
    public String getName() {
        return "gps";   // here, we do not use R.string to be compatible with the old (pre 3.8) way
    }

    @Override
    public void shutDown() {
        mLocationManager.removeUpdates(this);

        super.shutDown();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        if (DEBUG) Log.d(TAG, "onStatusChanged(" + provider + ", " + status + ")");
    }

    @Override
    public void onProviderEnabled(String provider) {
        if (DEBUG) Log.d(TAG, "onProviderEnabled: " + provider);
        if (provider.equals(LocationManager.GPS_PROVIDER)) {
            if (DEBUG) Log.d(TAG, "GPS location provider enabled");
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, SAMPLING_TIME, MIN_DISTANCE, this);
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        if (provider.equals(LocationManager.GPS_PROVIDER)) {
            if (DEBUG) Log.d(TAG, "GPS location provider disabled");
            mLocationManager.removeUpdates(this);
            LocationUnavailable();
        }
    }


    @Override
    public void onLocationChanged(Location location) {
        if (DEBUG) Log.i(TAG, "onLocationChanged");

        onNewLocation(location);

    }

}
