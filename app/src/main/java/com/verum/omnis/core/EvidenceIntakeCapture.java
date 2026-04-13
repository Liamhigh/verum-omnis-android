package com.verum.omnis.core;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;

import androidx.core.content.ContextCompat;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class EvidenceIntakeCapture {

    public static final class Snapshot {
        public final String capturedAtUtc;
        public final String localTime;
        public final String timezoneId;
        public final Double latitude;
        public final Double longitude;
        public final String locationStatus;

        public Snapshot(
                String capturedAtUtc,
                String localTime,
                String timezoneId,
                Double latitude,
                Double longitude,
                String locationStatus
        ) {
            this.capturedAtUtc = capturedAtUtc;
            this.localTime = localTime;
            this.timezoneId = timezoneId;
            this.latitude = latitude;
            this.longitude = longitude;
            this.locationStatus = locationStatus;
        }

        public String coordinatesLabel() {
            if (latitude == null || longitude == null) {
                return locationStatus;
            }
            return String.format(Locale.US, "%.6f, %.6f", latitude, longitude);
        }
    }

    private EvidenceIntakeCapture() {}

    public static Snapshot capture(Context context) {
        ZonedDateTime now = ZonedDateTime.now();
        String utc = Instant.now().toString();
        String local = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String timezone = now.getZone().getId();

        if (!hasLocationPermission(context)) {
            return new Snapshot(utc, local, timezone, null, null, "Location permission not granted");
        }

        try {
            LocationManager locationManager =
                    (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (locationManager == null) {
                return new Snapshot(utc, local, timezone, null, null, "Location service unavailable");
            }

            Location best = null;
            List<String> providers = locationManager.getProviders(true);
            for (String provider : providers) {
                Location location = locationManager.getLastKnownLocation(provider);
                if (location == null) {
                    continue;
                }
                if (best == null || location.getAccuracy() < best.getAccuracy()) {
                    best = location;
                }
            }

            if (best == null) {
                return new Snapshot(utc, local, timezone, null, null, "No last-known GPS fix available");
            }

            return new Snapshot(
                    utc,
                    local,
                    timezone,
                    best.getLatitude(),
                    best.getLongitude(),
                    "GPS captured"
            );
        } catch (SecurityException se) {
            return new Snapshot(utc, local, timezone, null, null, "Location access denied");
        } catch (Exception e) {
            return new Snapshot(utc, local, timezone, null, null, "Location error: " + e.getMessage());
        }
    }

    public static boolean hasLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }
}
