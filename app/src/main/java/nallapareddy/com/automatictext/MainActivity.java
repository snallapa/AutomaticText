package nallapareddy.com.automatictext;

import android.Manifest;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;


public class MainActivity extends AppCompatActivity {

    private final static int PERMISSION_REQUEST = 0;

    @BindView(R.id.permissions_text)
    TextView permissionsText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        permissionsText.setText(checkAllPermissions() ? R.string.permissions_granted : R.string.permissions_not_granted);
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
        return checkPermission(new String[] {Manifest.permission.PROCESS_OUTGOING_CALLS, Manifest.permission.SEND_SMS, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION});

    }

}
