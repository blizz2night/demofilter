package filters.demo.activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Configures the connection to camera hardware. Contains the logic for selecting the correct camera
 * and capture size, and also containing the {@link CameraManager}.
 */
final class CameraConfigurationManager {

  private static final String TAG = CameraConfigurationManager.class.getSimpleName();
  /** Max preview width guaranteed by Camera2 API. */
  private static final int MAX_PREVIEW_WIDTH = 1920;
  /** Max preview height guaranteed by Camera2 API. */
  private static final int MAX_PREVIEW_HEIGHT = 1080;
  private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

  /** Conversions needed for proper screen rotation to camera image orientation. */
  static {
    ORIENTATIONS.append(Surface.ROTATION_0, 90);
    ORIENTATIONS.append(Surface.ROTATION_90, 0);
    ORIENTATIONS.append(Surface.ROTATION_180, 270);
    ORIENTATIONS.append(Surface.ROTATION_270, 180);
  }

  private final CameraManager cameraManager;
  private final Context context;
  private final ConfiguredOrientationCallback configuredOrientationCallback;
  private StreamConfigurationMap streamConfigurationMap;
  private String cameraId;
  private Size previewSize;

  /** Specifies which camera on the phone we want to use. */
  enum CameraDirection {
    FRONT,
    BACK,
  }

  /** Callback for when configuring orientation of camera preview is finished. */
  public interface ConfiguredOrientationCallback {
    void onConfiguredOrientation(Size imageSize, Size cameraPreviewSize);
  }

  public CameraConfigurationManager(
      Context context, ConfiguredOrientationCallback configuredOrientationCallback) {
    this.context = context;
    this.configuredOrientationCallback = configuredOrientationCallback;
    cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
  }

  /**
   * Gets the proper orientation based on which camera is being used.
   *
   * <p>We check the camera sensor orientation as well as the current rotation of the phone and set
   * the width and height accordingly.
   *
   * <p>To have the proper resolution, this should be called after {@code FilterGLRenderer} is
   * initialized and its surfaceTexture is ready. If this function is called before that, width and
   * height will be 0 and {@code chooseOptimalSize} will return a very small Size.
   *
   * @param direction Specifies which camera we are using.
   * @param width The width of available size for camera preview.
   * @param height The height of available size for camera preview.
   */
  public void setCameraOrientation(CameraDirection direction, int width, int height) {
    int requestedDirection;
    switch (direction) {
      case FRONT:
        requestedDirection = CameraCharacteristics.LENS_FACING_FRONT;
        break;
      case BACK:
        requestedDirection = CameraCharacteristics.LENS_FACING_BACK;
        break;
      default:
        throw new IllegalArgumentException(
            "Camera direction unrecognized when trying to set camera orientation.");
    }
    try {
      String[] cameraIds = cameraManager.getCameraIdList();
      CameraCharacteristics cameraCharacteristics = null;
      for (String cameraId : cameraIds) {
        this.cameraId = cameraId;
        cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
        Integer current = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
        if (current != null && current.equals(requestedDirection)) {
          break;
        }
      }
      if (cameraId == null) {
        Log.e(TAG, "Did not find a camera direction that was requested.");
        return;
      }

      streamConfigurationMap =
          cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

      // For still image captures, we want the largest available size.
      Size largestImageSize =
          Collections.max(
              Arrays.asList(streamConfigurationMap.getOutputSizes(ImageFormat.JPEG)),
              new CompareSizesByArea());

      // Swap width and height depending on display rotation and sensor orientation.
      int displayRotation = getDefaultDisplay().getRotation();
      int sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
      boolean swappedDimensions = false;
      switch (displayRotation) {
        case Surface.ROTATION_0:
        case Surface.ROTATION_180:
          if (sensorOrientation == 90 || sensorOrientation == 270) {
            swappedDimensions = true;
          }
          break;
        case Surface.ROTATION_90:
        case Surface.ROTATION_270:
          if (sensorOrientation == 0 || sensorOrientation == 180) {
            swappedDimensions = true;
          }
          break;
        default:
          Log.e(TAG, "Display rotation is invalid: " + displayRotation);
      }

      Point displaySize = new Point();
      getDefaultDisplay().getSize(displaySize);
      int rotatedPreviewWidth = width;
      int rotatedPreviewHeight = height;
      int maxPreviewWidth = displaySize.x;
      int maxPreviewHeight = displaySize.y;

      if (swappedDimensions) {
        rotatedPreviewWidth = height;
        rotatedPreviewHeight = width;
        maxPreviewWidth = displaySize.y;
        maxPreviewHeight = displaySize.x;
      }

      if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
        maxPreviewWidth = MAX_PREVIEW_WIDTH;
      }
      if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
        maxPreviewHeight = MAX_PREVIEW_HEIGHT;
      }

      // Attempting to use too large a preview size could exceed the camera bus' bandwidth
      // limitation, resulting in storage of garbage capture data.
      previewSize =
          chooseOptimalSize(
              streamConfigurationMap.getOutputSizes(SurfaceTexture.class),
              rotatedPreviewWidth,
              rotatedPreviewHeight,
              maxPreviewWidth,
              maxPreviewHeight,
              largestImageSize);
      configuredOrientationCallback.onConfiguredOrientation(largestImageSize, previewSize);
    } catch (CameraAccessException e) {
      Log.e(TAG, "Could not retrieve camera ID list.");
    }
  }

  /** Opens the camera specified by {@link CameraConfigurationManager#cameraId}. */
  @SuppressLint("MissingPermission") // check permissions in DemoActivity.
  public void openCamera(CameraDevice.StateCallback deviceStateCallback, Handler backgroundHandler)
      throws CameraAccessException {
    cameraManager.openCamera(cameraId, deviceStateCallback, backgroundHandler);
  }

  /**
   * Retrieves the sensor orientation and display rotation to compute the necessary rotation angle
   * to make the camera preview image upright.
   *
   * @return The angle needed to rotate counter-clockwise
   */
  @Nullable
  public Integer getRotationForCurrentCamera() {
    if (cameraManager == null || cameraId == null) {
      Log.d(TAG, "cameraManager or cameraId null");
      return null;
    }
    try {
      // Sensor orientation is 90 degrees for most devices, but 270 for some.
      // For devices with sensor orientation of 90, we simply return the mapping from ORIENTATIONS.
      // For devices with sensor orientation of 270, we rotate 180 degrees.
      CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
      int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
      int orientation = ORIENTATIONS.get(getDefaultDisplay().getRotation());
      return (orientation + sensorOrientation + 270) % 360;
    } catch (CameraAccessException e) {
      Log.e(TAG, "Could not access camera when getting JPEG orientation.");
      return null;
    }
  }

  /**
   * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that is
   * at least as large as the respective texture view size, and that is at most as large as the
   * respective max size, and whose aspect ratio matches with the specified value. If such size
   * doesn't exist, choose the largest one that is at most as large as the respective max size, and
   * whose aspect ratio matches with the specified value.
   *
   * @param choices The list of sizes that the camera supports for the intended output class
   * @param textureViewWidth The width of the texture view relative to sensor coordinate
   * @param textureViewHeight The height of the texture view relative to sensor coordinate
   * @param maxWidth The maximum width that can be chosen
   * @param maxHeight The maximum height that can be chosen
   * @param aspectRatio The aspect ratio
   * @return The optimal {@code Size}, or an arbitrary one if none were big enough
   */
  private Size chooseOptimalSize(
      Size[] choices,
      int textureViewWidth,
      int textureViewHeight,
      int maxWidth,
      int maxHeight,
      Size aspectRatio) {

    // Collect the supported resolutions that are at least as big as the preview Surface
    List<Size> resolutionsGreaterThanOrEqualToPreviewSurface = new ArrayList<>();
    // Collect the supported resolutions that are smaller than the preview Surface
    List<Size> resolutionsSmallerThanPreviewSurface = new ArrayList<>();
    int width = aspectRatio.getWidth();
    int height = aspectRatio.getHeight();
    for (Size option : choices) {
      if (option.getWidth() <= maxWidth
          && option.getHeight() <= maxHeight
          && option.getHeight() == option.getWidth() * height / width) {
        if (option.getWidth() >= textureViewWidth && option.getHeight() >= textureViewHeight) {
          resolutionsGreaterThanOrEqualToPreviewSurface.add(option);
        } else {
          resolutionsSmallerThanPreviewSurface.add(option);
        }
      }
    }

    // Pick the smallest of those big enough. If there is no one big enough, pick the
    // largest of those not big enough.
    if (!resolutionsGreaterThanOrEqualToPreviewSurface.isEmpty()) {
      return Collections.min(
          resolutionsGreaterThanOrEqualToPreviewSurface, new CompareSizesByArea());
    } else if (!resolutionsSmallerThanPreviewSurface.isEmpty()) {
      return Collections.max(
          resolutionsSmallerThanPreviewSurface, new CompareSizesByArea());
    } else {
      return choices[0];
    }
  }

  private Display getDefaultDisplay() {
    return ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
  }

  /** Compare two {@link Size}s based on their areas. */
  private static class CompareSizesByArea implements Comparator<Size> {
    @Override
    public int compare(Size lhs, Size rhs) {
      return Long.signum(
          (long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
    }
  }
}
