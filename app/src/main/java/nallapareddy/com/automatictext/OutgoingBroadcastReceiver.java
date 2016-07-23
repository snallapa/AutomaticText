package nallapareddy.com.automatictext;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.telecom.TelecomManager;
import android.telephony.PhoneStateListener;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import static android.content.Context.LOCATION_SERVICE;

public class OutgoingBroadcastReceiver extends BroadcastReceiver implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private GoogleApiClient googleApiClient;
    private String number;
    private Context context;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        this.context = context;
        if (action.equals(Intent.ACTION_NEW_OUTGOING_CALL)) {
            number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            //CHANGE TO 911 IF YOU WANT TO USE
            if (number.contains("0126") || number.contains("471")) {
                if (googleApiClient == null) {
                    googleApiClient = new GoogleApiClient.Builder(context)
                            .addConnectionCallbacks(this)
                            .addOnConnectionFailedListener(this)
                            .addApi(LocationServices.API)
                            .build();
                    googleApiClient.connect();
                }
            }
        }
    }


    @Override
    public void onConnected(@Nullable Bundle bundle) {
        googleApiClient.disconnect();
        if (checkPermission()) {
            return;
        }

        Location lastLocation = LocationServices.FusedLocationApi.getLastLocation(
                googleApiClient);
        if (lastLocation == null) {
            lastLocation = getLastKnownLocation();
        }

        sendSmsMessage(lastLocation);
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        if (googleApiClient!= null) {
            googleApiClient.disconnect();
        }
    }

    private void sendSmsMessage(Location location) {
        if (location != null) {

            String message = "Latitude:" + String.valueOf(location.getLatitude()) + " Longitude: " + String.valueOf(location.getLongitude());
            Address addressFromLocation = getAddressFromLocation(location);
            if (addressFromLocation != null) {
                if (addressFromLocation.getLocality() == null) {
                    if (addressFromLocation.getSubLocality() == null) {
                        message += "\n" + addressFromLocation.getAddressLine(0) +  " " + addressFromLocation.getAdminArea() + " " + addressFromLocation.getPostalCode();
                    } else {
                        message += "\n" + addressFromLocation.getAddressLine(0) + " " + addressFromLocation.getSubLocality() + " " + addressFromLocation.getAdminArea() + " " + addressFromLocation.getPostalCode();
                    }
                } else {
                    message += "\n" + addressFromLocation.getAddressLine(0) + " " + addressFromLocation.getLocality() + " " + addressFromLocation.getAdminArea() + " " + addressFromLocation.getPostalCode();
                }

                if (addressFromLocation.getFeatureName() != null) {
                    message += "\n" + addressFromLocation.getFeatureName();
                }
            }
            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            telephonyManager.listen(new CustomPhoneListener(message),PhoneStateListener.LISTEN_CALL_STATE);
            googleApiClient.disconnect();
        }
    }

    private Location getLastKnownLocation() {
        if (checkPermission()) {
            return null;
        }
        LocationManager locationManager = (LocationManager) context.getSystemService(LOCATION_SERVICE);
        List<String> providers = locationManager.getProviders(true);
        Location bestLocation = null;
        for (String provider : providers) {
            Location l = locationManager.getLastKnownLocation(provider);
            if (l == null) {
                continue;
            }
            if (bestLocation == null || l.getAccuracy() < bestLocation.getAccuracy()) {
                // Found best last known location: %s", l);
                bestLocation = l;
            }
        }
        return bestLocation;
    }

    private boolean checkPermission() {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED;
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
    }

    private Address getAddressFromLocation(Location location) {
        Geocoder geocoder = new Geocoder(context, Locale.getDefault());
        try  {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            return addresses.get(0);
        } catch (IOException e) {
            return null;
        }
    }

    private class CustomPhoneListener extends PhoneStateListener {
        private String message;

        CustomPhoneListener(String message) {
            this.message = message;
        }

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            switch (state) {
                case TelephonyManager.CALL_STATE_IDLE:
                    SmsManager smsManager = SmsManager.getDefault();
                    smsManager.sendTextMessage(number, null, message, null, null);
            }
        }
    }
}
