package filters.demo.activities;

import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.opengl.GLSurfaceView;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import filters.demo.R;
import filters.demo.libraries.PermissionsRequestActivity;
import java.util.ArrayList;

/**
 * The critical pieces for integration with the OEM Filter's API includes:
 * <ul>
 *   <li>Retrieve LUTs from Photos via a content provider.
 *   <li>Apply the LUT filters inside the shader code.
 *   <li>Pass the captured image to Photos for processing.
 * </ul>
 *
 * <p><b>Note:</b> the camera implementation is to support the demonstration of the features listed
 * above and is not unique to this OEM Filters API implementation. This demo does not currently
 * support landscape orientation.
 */
public class DemoActivity extends AppCompatActivity {
  public static int version = -1;

  private static final String TAG = DemoActivity.class.getSimpleName();
  private static final String[] REQUIRED_PERMISSIONS = {
      Manifest.permission.CAMERA,
      Manifest.permission.WRITE_EXTERNAL_STORAGE,
  };
  private static final int ALL_PERMISSION_REQUEST_CODE = 3;
  /** This is just a sample test for checking if our version is currently up to date with Photos. */
  private static final int MINIMIMUM_VALID_PROVIDER_VERSION = 1;

  /** Pass {@link SurfaceTexture} to the {@link CameraModule} for configuration. */
  private final FilterGLRenderer.SurfaceReadyCallback surfaceReadyCallback =
      new FilterGLRenderer.SurfaceReadyCallback() {
        @Override
        public void onSurfaceReady(SurfaceTexture surfaceTexture) {
          surfaceTexture.setOnFrameAvailableListener(
              (unusedTexture) -> {
                autoFitGLSurfaceView.requestRender();
              });
          if (cameraModule != null) {
            cameraModule.setSurfaceTexture(surfaceTexture);
            cameraModule.configureCamera(
                autoFitGLSurfaceView.getWidth(), autoFitGLSurfaceView.getHeight());
          }
        }
      };
  /** Callback for when the camera is fully initialized and we can adjust camera angle. */
  private final CameraModule.CameraOpenListener cameraOpenListener =
      new CameraModule.CameraOpenListener() {
        @Override
        public void onPreviewSizeDetermined(Size previewSize) {
          runOnUiThread(() -> {
            // Preview width and height need to be switched for the GLSurfaceView dimensions in
            // portrait mode.
            autoFitGLSurfaceView.setAspectRatio(
                /* width= */ previewSize.getHeight(), /* height= */ previewSize.getWidth());
          });
        }

        @Override
        public void onCameraReady() {
          Integer rotationAngle = cameraModule.getCameraRotationAngle();
          if (rotationAngle != null) {
            filterGLRenderer.setRotationAngle(rotationAngle);
          }

          // Don't allow changing filters until both the renderer and camera are ready.
          changeFilterButton.setOnClickListener(v -> filterGLRenderer.shouldShowNineTiles(true));
          enableAutoFitGLSurfaceViewTouchListener();
        }
      };

  private AutoFitGLSurfaceView autoFitGLSurfaceView;
  private FilterGLRenderer filterGLRenderer;
  private CameraModule cameraModule;
  private Button changeFilterButton;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == ALL_PERMISSION_REQUEST_CODE && resultCode == RESULT_OK) {
      setUpView();
    } else {
      Toast.makeText(this, "Need to grant permissions.", Toast.LENGTH_SHORT).show();
      finish();
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    if (cameraModule != null) {
      cameraModule.start();
    } else {
      startActivityForResult(
          PermissionsRequestActivity.getIntent(this, REQUIRED_PERMISSIONS),
          ALL_PERMISSION_REQUEST_CODE);
    }
  }

  @Override
  public void onPause() {
    if (cameraModule != null) {
      cameraModule.close();
      cameraModule = null;
    }
    super.onPause();
  }

  private void setUpView() {
    new LUTFilterRetrievalTask().execute(PartnerContentProviderCaller.GET_VERSION_METHOD_NAME);
    new LUTFilterRetrievalTask().execute(PartnerContentProviderCaller.GET_FILTER_METHOD_NAME);
    setContentView(R.layout.activity_demo);
    Button captureImageButton = findViewById(R.id.capture_photo);
    captureImageButton.setOnClickListener(v -> {
      captureImage();
    });
    changeFilterButton = findViewById(R.id.change_filter);
    autoFitGLSurfaceView = findViewById(R.id.camera_preview);
    autoFitGLSurfaceView.setEGLContextClientVersion(2);
    autoFitGLSurfaceView.setEGLConfigChooser(
        /* redSize= */ 8,
        /* greenSize= */ 8,
        /* blueSize= */ 8,
        /* alphaSize= */ 8,
        /* depthSize= */ 0,
        /* stencilSize= */ 0);
    SurfaceHolder surfaceHolder = autoFitGLSurfaceView.getHolder();
    if (surfaceHolder == null) {
      throw new IllegalStateException("Failed to get surface texture holder.");
    }
    surfaceHolder.setFormat(PixelFormat.TRANSLUCENT);
    filterGLRenderer = new FilterGLRenderer(this, surfaceReadyCallback);
    autoFitGLSurfaceView.setRenderer(filterGLRenderer);
    autoFitGLSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    cameraModule = new CameraModule(this, cameraOpenListener);
  }

  /**
   * Enable OnTouchListener for autoFitGLSurfaceView to select a filter in the nine-tile view.
   *
   * <p>We calculate the filter index based on touch coordinates and pass that to the renderer.
   */
  private void enableAutoFitGLSurfaceViewTouchListener() {
    // We get a ClickViewAccessibility warning here because autoFitGLSurfaceView does not override
    // performClick. The view may not handle accessibility actions properly.
    autoFitGLSurfaceView.setOnTouchListener(
        new View.OnTouchListener() {
          float startX;
          float startY;

          @Override
          public boolean onTouch(View view, MotionEvent event) {
            if (!filterGLRenderer.showNineTiles()) {
              return false;
            }
            switch (event.getAction()) {
              case MotionEvent.ACTION_DOWN:
                startX = event.getX();
                startY = event.getY();
                break;
              case MotionEvent.ACTION_UP:
                view.performClick();
                float endX = event.getX();
                float endY = event.getY();
                int startIndex = obtainLutIndexFromTouch(startX, startY);
                int endIndex = obtainLutIndexFromTouch(endX, endY);
                // Only select filter if both the touch down and up indices are the same.
                if (startIndex == endIndex) {
                  filterGLRenderer.setLutIndex(startIndex);
                  filterGLRenderer.shouldShowNineTiles(false);
                }
                break;
              default: // fall out
            }
            return true;
          }
        });
  }

  /**
   * Retrieve proper filter index based on touch coordinates from the nine tile view.
   *
   * <p>Touch coordinates' origin is in the upper right. Our filter indices are organized as such:
   * <p>6, 7, 8
   * <p>3, 4, 5
   * <p>0, 1, 2
   */
  private int obtainLutIndexFromTouch(float xCoordinate, float yCoordinate) {
    float xScaled = xCoordinate / autoFitGLSurfaceView.getWidth();
    float yScaled =
        (autoFitGLSurfaceView.getHeight() - yCoordinate) / autoFitGLSurfaceView.getHeight();
    int x = Math.min(2, (int) (xScaled * 3));
    int y = Math.min(2, (int) (yScaled * 3));
    return x + y * 3;
  }

  /** Stitch all the LUT bitmaps together side by side horizontally. */
  private void stitchBitmaps(ArrayList<Bitmap> lutBitmaps) {
    if (lutBitmaps.isEmpty()) {
      Log.e(TAG, "Bitmap list was empty.");
      return;
    }

    filterGLRenderer.setLutsCount(lutBitmaps.size());
    // Determine size of new Bitmap.
    int totalWidth = 0;
    int totalHeight = 0;
    for (Bitmap lut : lutBitmaps) {
      totalWidth += lut.getWidth();
      totalHeight = Math.max(lut.getHeight(), totalHeight);
    }

    // Draw LUT bitmaps onto new empty bitmap.
    Bitmap stitchedBitmap =
        Bitmap.createBitmap(totalWidth, totalHeight, lutBitmaps.get(0).getConfig());
    Canvas canvas = new Canvas(stitchedBitmap);
    int previousBitmapLeftCoordinate = 0;
    for (Bitmap lut : lutBitmaps) {
      canvas.drawBitmap(lut, previousBitmapLeftCoordinate, 0, null);
      previousBitmapLeftCoordinate += lut.getWidth();
    }
    filterGLRenderer.setLutBitmap(stitchedBitmap);
    // Allow changing of filters now.
    if (!isValidVersion(version)) {
      Toast.makeText(
          this, "PartnerContentProvider version is not valid", Toast.LENGTH_SHORT).show();
      return;
    }
    changeFilterButton.setEnabled(/* enabled= */ true);
  }

  private boolean isValidVersion(int version) {
    return version >= MINIMIMUM_VALID_PROVIDER_VERSION;
  }

  private void captureImage() {
    cameraModule.captureStillPicture(filterGLRenderer.getFilterId());
  }

  /** Call some of the PartnerContentProvider methods. */
  private final class LUTFilterRetrievalTask extends AsyncTask<String, Void, Void> {

    private PartnerContentProviderCaller caller;
    private Bundle returnedBundle = null;
    private String method;

    @Override
    protected Void doInBackground(String... methods) {
      method = methods[0];
      caller = new PartnerContentProviderCaller(DemoActivity.this);
      switch(method) {
        case PartnerContentProviderCaller.GET_VERSION_METHOD_NAME:
          version = caller.getVersionFromPhotos();
          break;
        case PartnerContentProviderCaller.GET_FILTER_METHOD_NAME:
          returnedBundle = caller.getFilters();
          break;
        default:
          throw new IllegalArgumentException("Unknown PartnerContentProvider call requested");
      }
      return null;
    }

    @Override
    protected void onPostExecute(Void v) {
      if (returnedBundle == null || method == null) {
        Log.e(TAG, "Error calling PartnerContentProvider");
        return;
      }
      switch(method) {
        case PartnerContentProviderCaller.GET_FILTER_METHOD_NAME:
          handleLookupTableRetrieval();
          break;
        default:
          throw new IllegalArgumentException("Unknown PartnerContentProvider call requested");
      }
    }

    private void handleLookupTableRetrieval() {
      ArrayList<String> filterNames =
          returnedBundle.getStringArrayList(
              PartnerContentProviderCaller.FILTER_NAMES_LIST_KEY_NAME);
      boolean[] filterIsGrayscaleList =
          returnedBundle.getBooleanArray(
              PartnerContentProviderCaller.FILTER_IS_GRAYSCALE_BOOL_LIST_KEY_NAME);
      ArrayList<Bitmap> lutBitmaps =
          returnedBundle.getParcelableArrayList(
              PartnerContentProviderCaller.FILTER_BITMAPS_LIST_KEY_NAME);
      ArrayList<Integer> filterIds =
          returnedBundle.getIntegerArrayList(
              PartnerContentProviderCaller.FILTER_IDS_KEY_NAME);
      if (filterNames == null
          || filterIsGrayscaleList == null
          || lutBitmaps == null
          || filterIds == null) {
        Log.e(TAG, "Bundle contained a null value.");
        return;
      }
      filterGLRenderer.createLutObjects(filterNames, filterIsGrayscaleList, filterIds);
      filterGLRenderer.updateGrayscaleList();
      stitchBitmaps(lutBitmaps);
    }
  }
}
