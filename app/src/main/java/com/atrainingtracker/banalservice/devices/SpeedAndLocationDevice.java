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

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.util.Log;

import java.util.ArrayDeque;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.sensor.MyDoubleAccumulatorSensor;
import com.atrainingtracker.banalservice.sensor.MySensor;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.sensor.SensorType;


public abstract class SpeedAndLocationDevice extends MyDevice {
    /**
     * Reject location fixes worse than this (meters) for all processing — matches legacy tolerance
     * so we do not drop fixes that the app previously accepted.
     */
    public static final double ACCURACY_THRESHOLD = 200;
    /**
     * Only accumulate distance when reported accuracy is at least this good; stops most drift
     * from being summed when the fix is still loose but within {@link #ACCURACY_THRESHOLD}.
     */
    private static final float ACCURACY_FOR_DISTANCE_M = 40f;
    /**
     * Ignore GPS jitter below this horizontal movement (meters) so standing still does not add distance.
     * Segment length is measured from the last accepted distance anchor, not every raw sample.
     */
    private static final float MIN_MOVEMENT_FOR_DISTANCE_M = 7f;
    /**
     * Shorter path window so rolling average can catch up quickly; paired with last-segment speed
     * so the readout still updates on every fix (not only after 3+ seconds).
     */
    private static final long SPEED_MIN_WINDOW_MS = 850;
    private static final long SPEED_MAX_WINDOW_MS = 8000;
    private static final int SPEED_RING_MAX_SAMPLES = 24;
    /** How much the newest segment (prev fix → this fix) contributes vs the path window average. */
    private static final double SPEED_SEGMENT_BLEND = 0.58;
    /**
     * Display smoothing: higher = more real-time; segment+window already damp GPS spikes.
     */
    private static final double SPEED_DISPLAY_EMA_ALPHA = 0.52;
    private static final long SEGMENT_MIN_DT_MS = 45;
    private static final long SEGMENT_MAX_DT_MS = 12000;
    /** Cap absurd spikes (m/s). */
    private static final double MAX_SPEED_MPS = 25.0;
    private static final double MIN_SPEED_FOR_PACE_MPS = 0.25;
    /** Minimum path length (m) before we trust a non-zero window speed; keeps slow jogs but kills drift. */
    private static final double STATIONARY_PATH_MIN_M = 2.6;

    protected static final int SAMPLING_TIME = 400;
    protected static final int MIN_DISTANCE = 0;
    private static final String TAG = "GPSSpeedAndLocationDevice";
    private static final boolean DEBUG = BANALService.DEBUG & false;
    protected boolean LocationAvailable = false;
    protected MySensor<Double> mLongitudeSensor;
    protected MySensor<Double> mLatitudeSensor;
    protected MySensor<Double> mAccuracySensor;
    protected MySensor<Number> mBearingSensor;
    protected MySensor<Double> mAltitudeSensor;
    protected MySensor<Double> mSpeedSensor;
    protected MySensor<Double> mLineDistanceSensor;
    protected MySensor<Double> mPaceSensor;
    protected MyDoubleAccumulatorSensor mDistanceSensor;
    protected MyDoubleAccumulatorSensor mLapDistanceSensor;
    /** First fix for straight-line distance from start. */
    protected Location mStartLocation = null;
    /** Recent fixes for windowed path speed (newest at tail). */
    private final ArrayDeque<Location> mSpeedRing = new ArrayDeque<>();
    /** Last position committed into {@link #mDistance}; small moves do not move this anchor. */
    private Location mLastAnchorForDistance = null;
    private double mSmoothedSpeedMps = 0.0;
    private boolean mHaveSmoothedSpeed = false;
    double mDistance, mSpeed;


    public SpeedAndLocationDevice(Context context, MySensorManager mySensorManager, DeviceType deviceType) {
        super(context, mySensorManager, deviceType);
        if (DEBUG) {
            Log.d(TAG, "constructor");
        }
    }

    protected void LocationAvailable() {
        if (DEBUG) Log.d(TAG, "LocationAvailable()");

        if (!LocationAvailable) {
            LocationAvailable = true;
            registerSensors();
            mContext.sendBroadcast(new Intent(BANALService.LOCATION_AVAILABLE_INTENT));
        }
    }

    protected void LocationUnavailable() {
        if (DEBUG) Log.d(TAG, "LocationUnavailable()");

        if (LocationAvailable) {
            LocationAvailable = false;
            unregisterSensors();
            mContext.sendBroadcast(new Intent(BANALService.LOCATION_UNAVAILABLE_INTENT));
        }
    }

    @Override
    protected void addSensors() {
        mLongitudeSensor = new MySensor<Double>(this, SensorType.LONGITUDE);
        mLatitudeSensor = new MySensor<Double>(this, SensorType.LATITUDE);
        mAccuracySensor = new MySensor<Double>(this, SensorType.ACCURACY);
        mBearingSensor = new MySensor<Number>(this, SensorType.BEARING);
        mAltitudeSensor = new MySensor<Double>(this, SensorType.ALTITUDE);
        mSpeedSensor = new MySensor<Double>(this, SensorType.SPEED_mps);
        mPaceSensor = new MySensor<Double>(this, SensorType.PACE_spm);
        mLineDistanceSensor = new MyDoubleAccumulatorSensor(this, SensorType.LINE_DISTANCE_m);
        mDistanceSensor = new MyDoubleAccumulatorSensor(this, SensorType.DISTANCE_m);
        mLapDistanceSensor = new MyDoubleAccumulatorSensor(this, SensorType.DISTANCE_m_LAP);

        addSensor(mLongitudeSensor);
        addSensor(mLatitudeSensor);
        addSensor(mAltitudeSensor);
        addSensor(mAccuracySensor);
        addSensor(mAltitudeSensor);
        addSensor(mSpeedSensor);
        addSensor(mLineDistanceSensor);
        addSensor(mPaceSensor);
        addSensor(mDistanceSensor);
        addSensor(mLapDistanceSensor);

        mSpeedSensor.newValue(0.0);
        mPaceSensor.newValue(null);
    }

    @Override
    protected void newLap() {
        mLapDistanceSensor.reset();
    }


    public void onNewLocation(Location location) {
        if (DEBUG) Log.i(TAG, "onNewLocation()");

        if (location != null) {
            if (DEBUG)
                Log.d(TAG, "new location, provider: " + location.getProvider() + ", accuracy=" + location.getAccuracy() + ", threshold=" + ACCURACY_THRESHOLD);
            if (location.getAccuracy() <= ACCURACY_THRESHOLD) {
                LocationAvailable();

                if (mStartLocation == null) {
                    mStartLocation = location;
                }

                mLongitudeSensor.newValue(location.getLongitude());
                mLatitudeSensor.newValue(location.getLatitude());
                mAccuracySensor.newValue(location.getAccuracy() + 0.0);
                mBearingSensor.newValue(location.getBearing());
                mAltitudeSensor.newValue(location.getAltitude());
                mLineDistanceSensor.newValue(location.distanceTo(mStartLocation) + 0.0);

                // --- Speed: last-segment (per-fix) + short path window + fused getSpeed(), with gating and EMA.
                updateSpeedStable(location);

                // --- Distance: only add movement when accuracy is good and displacement exceeds noise floor.
                updateDistanceWithDriftFilter(location);

                mDistanceSensor.newValue(mDistance);
                mLapDistanceSensor.newValue(mDistance);

                Intent intent = new Intent(BANALService.NEW_LOCATION_INTENT);
                intent.putExtra(BANALService.LATITUDE, location.getLatitude());
                intent.putExtra(BANALService.LONGITUDE, location.getLongitude());
                intent.putExtra(BANALService.LOCATION_PROVIDER, location.getProvider());
                mContext.sendBroadcast(intent);

            }
        }
    }

    /**
     * Ring buffer of fixes; each update combines (1) gated speed from the previous fix to this one for immediacy,
     * (2) average speed along the path over ~0.85–8 s to limit spikes, (3) fused {@link Location#getSpeed()} when sane.
     */
    private void updateSpeedStable(Location location) {
        Location snap = new Location(location);
        if (!mSpeedRing.isEmpty() && mSpeedRing.peekLast().getTime() == snap.getTime()) {
            mSpeedRing.removeLast();
        }
        mSpeedRing.addLast(snap);
        while (!mSpeedRing.isEmpty() && snap.getTime() - mSpeedRing.peekFirst().getTime() > SPEED_MAX_WINDOW_MS) {
            mSpeedRing.pollFirst();
        }
        while (mSpeedRing.size() > SPEED_RING_MAX_SAMPLES) {
            mSpeedRing.pollFirst();
        }

        double segmentMps = computeLastSegmentSpeedMps();
        double windowMps = computeWindowPathSpeedMps();
        double providerMps = fusedSpeedMpsIfTrustworthy(location);
        double blended = combineSegmentWindowAndProvider(segmentMps, windowMps, providerMps, location);

        if (blended < 0) {
            if (mHaveSmoothedSpeed) {
                mSmoothedSpeedMps *= 0.9;
                if (mSmoothedSpeedMps < 0.12) {
                    mSmoothedSpeedMps = 0.0;
                    mHaveSmoothedSpeed = false;
                }
            }
        } else {
            blended = Math.min(blended, MAX_SPEED_MPS);
            if (!mHaveSmoothedSpeed) {
                mSmoothedSpeedMps = blended;
                mHaveSmoothedSpeed = true;
            } else {
                mSmoothedSpeedMps = SPEED_DISPLAY_EMA_ALPHA * blended
                        + (1.0 - SPEED_DISPLAY_EMA_ALPHA) * mSmoothedSpeedMps;
            }
        }

        mSpeed = mSmoothedSpeedMps;
        mSpeedSensor.newValue(mSpeed);
        if (mSpeed > MIN_SPEED_FOR_PACE_MPS) {
            mPaceSensor.newValue(1.0 / mSpeed);
        } else {
            mPaceSensor.newValue(null);
        }
    }

    /**
     * Speed from the two newest fixes only. Movement below a noise floor yields 0 (stationary); bad dt returns NaN.
     */
    private double computeLastSegmentSpeedMps() {
        if (mSpeedRing.size() < 2) {
            return Double.NaN;
        }
        Location prev = null;
        Location last = null;
        for (Location p : mSpeedRing) {
            prev = last;
            last = p;
        }
        if (prev == null) {
            return Double.NaN;
        }
        long dtMs = last.getTime() - prev.getTime();
        if (dtMs < SEGMENT_MIN_DT_MS || dtMs > SEGMENT_MAX_DT_MS) {
            return Double.NaN;
        }
        float segM = prev.distanceTo(last);
        double noiseM = Math.max(1.15, 0.32 * (prev.getAccuracy() + last.getAccuracy()));
        if (segM <= noiseM) {
            return 0.0;
        }
        double v = segM / (dtMs / 1000.0);
        if (Double.isNaN(v) || Double.isInfinite(v) || v > MAX_SPEED_MPS) {
            return Double.NaN;
        }
        return v;
    }

    /**
     * @return speed in m/s, or {@link Double#NaN} if the buffer does not yet span {@link #SPEED_MIN_WINDOW_MS}.
     */
    private double computeWindowPathSpeedMps() {
        if (mSpeedRing.size() < 2) {
            return Double.NaN;
        }
        Location first = mSpeedRing.peekFirst();
        Location last = mSpeedRing.peekLast();
        long dtMs = last.getTime() - first.getTime();
        if (dtMs < SPEED_MIN_WINDOW_MS) {
            return Double.NaN;
        }

        double pathM = 0.0;
        Location prev = null;
        for (Location p : mSpeedRing) {
            if (prev != null) {
                pathM += prev.distanceTo(p);
            }
            prev = p;
        }

        double noiseM = Math.max(STATIONARY_PATH_MIN_M,
                0.38 * (first.getAccuracy() + last.getAccuracy()));
        if (pathM <= noiseM) {
            return 0.0;
        }

        double dtS = dtMs / 1000.0;
        double v = pathM / dtS;
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return Double.NaN;
        }
        return v;
    }

    private static double fusedSpeedMpsIfTrustworthy(Location location) {
        if (!location.hasSpeed()) {
            return Double.NaN;
        }
        float v = location.getSpeed();
        if (Float.isNaN(v) || v < 0f || v > MAX_SPEED_MPS) {
            return Double.NaN;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasSpeedAccuracy()) {
            float sa = location.getSpeedAccuracyMetersPerSecond();
            if (!Float.isNaN(sa) && sa > 3.5f) {
                return Double.NaN;
            }
        }
        return v;
    }

    /**
     * Merges segment (real-time), window (stability), and fused speed. Returns negative if nothing is usable.
     */
    private static double combineSegmentWindowAndProvider(double segmentMps, double windowMps, double providerMps,
                                                          Location location) {
        boolean haveS = !Double.isNaN(segmentMps);
        boolean haveW = !Double.isNaN(windowMps);
        boolean haveP = !Double.isNaN(providerMps);

        if (haveW && windowMps <= 0.08) {
            return 0.0;
        }

        double core;
        if (haveS && haveW) {
            if (segmentMps <= 0.06 && windowMps > 0.55) {
                core = 0.20 * segmentMps + 0.80 * windowMps;
            } else {
                core = SPEED_SEGMENT_BLEND * segmentMps + (1.0 - SPEED_SEGMENT_BLEND) * windowMps;
            }
        } else if (haveS) {
            core = segmentMps;
        } else if (haveW) {
            core = windowMps;
        } else {
            core = Double.NaN;
        }

        if (Double.isNaN(core)) {
            return haveP ? providerMps : -1.0;
        }

        // No rolling window yet: a noise-gated stationary segment should not be overridden by fused speed creep.
        if (haveS && segmentMps <= 0.06 && !haveW) {
            return 0.0;
        }

        if (haveP) {
            double wProv = providerBlendWeight(location);
            if (haveW && windowMps < 0.4 && providerMps > 2.0) {
                return (1.0 - wProv) * core + wProv * Math.min(providerMps, core * 1.15 + 0.5);
            }
            return (1.0 - wProv) * core + wProv * providerMps;
        }
        return core;
    }

    /** How much to trust {@link Location#getSpeed()} on top of segment+window core. */
    private static double providerBlendWeight(Location location) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasSpeedAccuracy()) {
            float sa = location.getSpeedAccuracyMetersPerSecond();
            if (!Float.isNaN(sa) && sa >= 0f) {
                if (sa < 0.55f) {
                    return 0.38;
                }
                if (sa > 2.2f) {
                    return 0.20;
                }
            }
        }
        return 0.32;
    }

    /**
     * Advances total distance only when horizontal movement from the last committed anchor exceeds
     * {@link #MIN_MOVEMENT_FOR_DISTANCE_M} and the fix accuracy supports trusting that delta.
     */
    private void updateDistanceWithDriftFilter(Location location) {
        if (location.getAccuracy() > ACCURACY_FOR_DISTANCE_M) {
            return;
        }
        if (mLastAnchorForDistance == null) {
            mLastAnchorForDistance = location;
            return;
        }

        float deltaM = mLastAnchorForDistance.distanceTo(location);
        if (deltaM >= MIN_MOVEMENT_FOR_DISTANCE_M) {
            mDistance += deltaM;
            mLastAnchorForDistance = location;
        }
    }
}
