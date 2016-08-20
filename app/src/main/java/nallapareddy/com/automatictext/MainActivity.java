package nallapareddy.com.automatictext;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnCheckedChanged;


public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST = 0;
    public static final String PREFS = "prefs_automatictext";
    public static final String PREFS_ENABLED = "prefs_enabled";
    public static final String PREFS_NUMBER = "prefs_number";
    public static final String PREFS_DETAILED = "prefs_detailed";

    @BindView(R.id.permissions_text)
    TextView permissionsText;
    @BindView(R.id.app_enabled)
    Switch enabled;
    @BindView(R.id.number_input)
    EditText numberText;
    @BindView(R.id.detailed_text)
    Switch detailedText;

    private boolean appEnabled;
    private boolean sendDetailedText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        permissionsText.setText(checkAllPermissions() ? R.string.permissions_granted : R.string.permissions_not_granted);

        SharedPreferences preferences = getSharedPreferences(PREFS, 0);
        appEnabled = preferences.getBoolean(PREFS_ENABLED, true);
        sendDetailedText = preferences.getBoolean(PREFS_DETAILED, true);
        String number = preferences.getString(PREFS_NUMBER, "0126");
        numberText.setText(number.trim());
        enabled.setChecked(appEnabled);
        detailedText.setChecked(sendDetailedText);
    }

    @Override
    protected void onStop() {
        super.onStop();

        SharedPreferences.Editor editor = getSharedPreferences(PREFS, 0).edit();
        if (!numberText.getText().toString().isEmpty()) {
            editor.putString(PREFS_NUMBER, numberText.getText().toString().trim());
        }
        editor.putBoolean(PREFS_ENABLED, appEnabled);
        editor.putBoolean(PREFS_DETAILED, sendDetailedText);
        editor.commit();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    permissionsText.setText(R.string.permissions_granted);
                } else {
                    permissionsText.setText(R.string.permissions_not_granted);
                }
            }
        }
    }

    private boolean checkPermission(String[] permission) {
        int result;
        List<String> needPermissions = new ArrayList<>();
        for (String currentPermission: permission) {
            result = ContextCompat.checkSelfPermission(this, currentPermission);
            if (result !=PackageManager.PERMISSION_GRANTED) {
                needPermissions.add(currentPermission);
            }
        }
        if (!needPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, needPermissions.toArray(new String[needPermissions.size()]), PERMISSION_REQUEST);
            return false;
        }
        return true;
    }

    private boolean checkAllPermissions() {
        return checkPermission(new String[] {Manifest.permission.PROCESS_OUTGOING_CALLS, Manifest.permission.SEND_SMS, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.INTERNET});
    }

    @OnCheckedChanged(R.id.app_enabled)
    public void enabledChanged() {
        appEnabled = enabled.isChecked();
    }

    @OnCheckedChanged(R.id.detailed_text)
    public void detailedTextChanged() {
        sendDetailedText = detailedText.isChecked();
    }

}
