package filters.demo.libraries;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

/**
 * Activity to launch permissions request dialog if permission is not granted.
 *
 * <p>This activity is taken from the purple puppies codebase and will not be altered.
 */
public final class PermissionsRequestActivity extends Activity {

  public static Intent getIntent(Context context, String[] permissions) {
    Intent intent = new Intent(context, PermissionsRequestActivity.class);
    intent.putExtra(PermissionsRequestActivity.EXTRA_PERMISSIONS, permissions);
    return intent;
  }

  private static final String EXTRA_PERMISSIONS = "extra_permission";
  private static final int REQUEST_CODE = 5;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    String[] permissions = getIntent().getStringArrayExtra(EXTRA_PERMISSIONS);
    if (areRuntimePermissionsGranted(this, permissions)) {
      setResult(RESULT_OK);
      finish();
    } else {
      ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE);
    }
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    if (requestCode != REQUEST_CODE) {
      return;
    }
    // If request is cancelled, the result arrays are empty.
    if (grantResults.length <= 0) {
      setResult(RESULT_CANCELED);
      finish();
    }
    for (int i = 0; i < permissions.length; i++) {
      if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
        setResult(RESULT_CANCELED);
        finish();
      }
    }
    setResult(RESULT_OK);
    finish();
  }

  private boolean areRuntimePermissionsGranted(Context context, String[] permissions) {
    for (String requiredPermission : permissions) {
      int permissionCheck = ContextCompat.checkSelfPermission(context, requiredPermission);
      if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
        return false;
      }
    }
    return true;
  }
}
