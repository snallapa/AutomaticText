package nallapareddy.com.automatictext;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telephony.PhoneStateListener;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OutgoingBroadcastReceiver extends BroadcastReceiver implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {

    private static final int TWO_MINUTES = 1000 * 60 * 2;
    private static final int MAX_LOCATION = 3;

    private GoogleApiClient googleApiClient;
    private String number;
    private Context context;
    private LocationRequest locationRequest;
    private List<Location> locationList = new ArrayList<>();
    private boolean sendDetailedText;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        this.context = context;
        SharedPreferences defaultSharedPreferences = context.getSharedPreferences(MainActivity.PREFS, 0);
        boolean enabled = defaultSharedPreferences.getBoolean(MainActivity.PREFS_ENABLED, true);
        String numberCheck = defaultSharedPreferences.getString(MainActivity.PREFS_NUMBER, "0126");
        sendDetailedText = defaultSharedPreferences.getBoolean(MainActivity.PREFS_DETAILED, true);
        if (action.equals(Intent.ACTION_NEW_OUTGOING_CALL) && enabled) {
            number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);

            if (number.contains(numberCheck)) {
                if (googleApiClient == null) {
                    googleApiClient = new GoogleApiClient.Builder(context)
                            .addConnectionCallbacks(this)
                            .addOnConnectionFailedListener(this)
                            .addApi(LocationServices.API)
                            .build();
                    googleApiClient.connect();

                    locationRequest = new LocationRequest();
                    locationRequest.setInterval(1000);
                    locationRequest.setFastestInterval(500);
                    locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
                }
            }
        }
    }


    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (Utils.checkPermission(context)) {
            return;
        }

        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this);
    }

    @Override
    public void onLocationChanged(Location location) {
        locationList.add(location);
        if (locationList.size() == MAX_LOCATION) {
            LocationServices.FusedLocationApi.removeLocationUpdates(
                    googleApiClient, this);
            googleApiClient.disconnect();
            findBestLocation();
            locationList.clear();
        }
    }

    private void findBestLocation() {
        Location bestLocation = locationList.get(0);
        for (Location location : locationList) {
            long timeDelta = bestLocation.getTime() - location.getTime();
            boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
            boolean isNewer = timeDelta > 0;

            if (isSignificantlyNewer) {
                bestLocation = location;
                continue;
            }

            int accuracyDelta = (int) (location.getAccuracy() - bestLocation.getAccuracy());
            boolean isLessAccurate = accuracyDelta > 0;
            boolean isMoreAccurate = accuracyDelta < 0;
            boolean isSignificantlyLessAccurate = accuracyDelta > 200;
            boolean isFromSameProvider = Utils.isSameProvider(location.getProvider(),
                    bestLocation.getProvider());
            if (isMoreAccurate) {
                bestLocation = location;
            } else if (isNewer && !isLessAccurate) {
                bestLocation = location;
            } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
                bestLocation = location;
            }
        }
        sendSmsMessage(bestLocation);
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        if (googleApiClient != null) {
            googleApiClient.disconnect();
        }
    }

    private void sendSmsMessage(Location location) {
        if (location != null) {
            String message;
            if (sendDetailedText) {
                message = "Latitude:" + String.valueOf(location.getLatitude()) + " Longitude: " + String.valueOf(location.getLongitude()) + "\n";
                message += getAddressFromLocation(location);
            } else {
                message = context.getString(R.string.message_url, String.valueOf(location.getLatitude()), String.valueOf(location.getLongitude()));
            }

            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            telephonyManager.listen(new CustomPhoneListener(message), PhoneStateListener.LISTEN_CALL_STATE);
        }
    }

    private String getAddressFromLocation(Location location) {
        Geocoder geocoder = new Geocoder(context, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            return convertAddressToString(addresses.get(0));
        } catch (Exception e) {
            //sometimes we fail oh well
            getAddressFromLocationWithMaps(location);
            Log.i("Automatic Text", "No wifi forcing mobile data");
            return "";
        }
    }

    private void getAddressFromLocationWithMaps(Location location) {
        new ConnectToMapsApi().execute("http://maps.googleapis.com/maps/api/geocode/json?latlng=" +
                location.getLatitude() + "," + location.getLongitude() + "&sensor=true");
    }

    private String convertAddressToString(Address address) {
        String message = "";
        if (address != null) {
            message += address.getAddressLine(0) + " " + address.getSubLocality() + " " + address.getLocality() + " " + address.getAdminArea() + " " + address.getPostalCode();
            if (address.getFeatureName() != null) {
                message += "\n" + address.getFeatureName();
            }
        } else {
            message += "Street address is unknown";
        }
        return message.replace("null", "");
    }

    private StringBuilder inputStreamToString(InputStream is) {
        String rLine;
        StringBuilder answer = new StringBuilder();
        BufferedReader rd = new BufferedReader(new InputStreamReader(is));
        try {
            while ((rLine = rd.readLine()) != null) {
                answer.append(rLine);
            }
        } catch (IOException e) {
            // e.printStackTrace();
            Toast.makeText(context.getApplicationContext(),
                    "Error..." + e.toString(), Toast.LENGTH_LONG).show();
        }
        return answer;
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

    private class ConnectToMapsApi extends AsyncTask<String, String, String> {

        @Override
        protected String doInBackground(String... uri) {
            try {
                URL url = new URL(uri[0]);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setReadTimeout(10000);
                conn.setConnectTimeout(15000);
                conn.connect();

                String jsonResult = inputStreamToString(conn.getInputStream()).toString();
                JSONObject jsonResponse = new JSONObject(jsonResult);

                if ("OK".equalsIgnoreCase(jsonResponse.getString("status"))) {
                    JSONArray addressComponents = ((JSONArray) jsonResponse.get("results")).getJSONObject(0).getJSONArray("address_components");
                    String message = "";
                    for (int i = 0; i < addressComponents.length(); i++) {
                        String addressComponent = ((JSONArray) ((JSONObject) addressComponents.get(i)).get("types")).getString(0);
                        if (addressComponent.equals("street_number")) {
                            message += ((JSONObject) addressComponents.get(i)).getString("long_name") + " ";
                        }
                        if (addressComponent.equals("route")) {
                            message += ((JSONObject) addressComponents.get(i)).getString("long_name") + " ";
                        }
                        if (addressComponent.equals("locality")) {
                            message += ((JSONObject) addressComponents.get(i)).getString("long_name") + " ";
                        }
                        if (addressComponent.equals("administrative_area_level_2")) {
                            message += ((JSONObject) addressComponents.get(i)).getString("long_name") + " ";
                        }
                        if (addressComponent.equals("administrative_area_level_1")) {
                            message += ((JSONObject) addressComponents.get(i)).getString("long_name") + " ";
                        }
                        if (addressComponent.equals("postal_code")) {
                            message += ((JSONObject) addressComponents.get(i)).getString("short_name") + " ";
                        }
                    }
                    return message;
                }
                if (conn.getInputStream() != null) {
                    conn.getInputStream().close();
                }
            } catch (MalformedURLException e) {
                return "";
            } catch (IOException e) {
                return "";
            } catch (JSONException e) {
                return "";
            }

            return null;
        }

        @Override
        protected void onPostExecute(String message) {
            super.onPostExecute(message);
            SmsManager smsManager = SmsManager.getDefault();
            message = message.replace("null", "");
            if (message.isEmpty()) {
                message = null;
            }
            try {
                smsManager.sendTextMessage(number, null, message, null, null);
            } catch (Exception e) {
                smsManager.sendTextMessage(number, null, "Tried to get coarse address but failed. User might not be at a real street address", null, null);
            }

        }
    }
}
