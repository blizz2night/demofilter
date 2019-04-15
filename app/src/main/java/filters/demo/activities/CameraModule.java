package filters.demo.activities;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.widget.Toast;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Class that contains the variables required for camera usage. */
public final class CameraModule {

  private static final String TAG = CameraModule.class.getSimpleName();
  private static final String CAMERA_THREAD_NAME = "CameraBackground";
  private static final int SEMAPHORE_MAX_WAIT_TIME_MILLIS = 2500;

  private final Context context;
  private final CameraConfigurationManager cameraConfigurationManager;
  /** A {@link Semaphore} to prevent the app from exiting before closing the camera. */
  private final Semaphore cameraLock = new Semaphore(1);
  private final CameraOpenListener cameraOpenListener;
  private final ImageReader.OnImageAvailableListener onImageAvailableListener =
      this::onAvailableImage;
  /**
   * A {@link CameraConfigurationManager.ConfiguredOrientationCallback} that indicates that
   * preview/image sizes have been determined and we can proceed to open the camera.
   */
  private final CameraConfigurationManager.ConfiguredOrientationCallback configurationCallback =
      new CameraConfigurationManager.ConfiguredOrientationCallback() {
        @Override
        public void onConfiguredOrientation(Size imageSize, Size cameraPreviewSize) {
          previewSize = cameraPreviewSize;
          imageReader =
              ImageReader.newInstance(
                  imageSize.getWidth(),
                  imageSize.getHeight(),
                  ImageFormat.JPEG,
                  /* maxImages= */ 2);
          imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);
          surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
          cameraOpenListener.onPreviewSizeDetermined(previewSize);
          openCameraDevice();
        }
      };
  /** {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state. */
  private final CameraDevice.StateCallback deviceStateCallback =
      new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice device) {
          cameraLock.release();
          cameraDevice = device;
          createCaptureSession();
        }

        @Override
        public void onDisconnected(CameraDevice device) {
          if (cameraDevice != null && cameraDevice != device) {
            return;
          }
          close();
        }

        @Override
        public void onError(CameraDevice device, int errorCode) {
          if (cameraDevice != null && cameraDevice != device) {
            return;
          }
          close();
        }
      };
  /**
   * {@link CameraCaptureSession.StateCallback} is called when {@link CameraCaptureSession} is
   * created.
   */
  private final CameraCaptureSession.StateCallback sessionStateCallback =
      new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(CameraCaptureSession session) {
          if (cameraDevice == null || session.getDevice() != cameraDevice) {
            return;
          }
          captureSession = session;
          createPreviewRequest();
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
          Log.e(TAG, "Could not configure CameraCaptureSession.");
        }
      };

  private HandlerThread backgroundThread;
  private Handler backgroundHandler;
  private SurfaceTexture surfaceTexture;
  private ImageReader imageReader;
  private CameraCaptureSession captureSession;
  private CameraDevice cameraDevice;
  private CaptureRequest.Builder previewRequestBuilder;
  private CaptureRequest previewRequest;
  private Size previewSize;
  private int filterId = FilterGLRenderer.NO_FILTER_ID;

  /** Callback indicating when camera has started a CameraCaptureSession and a CaptureRequest. */
  public interface CameraOpenListener {
    void onPreviewSizeDetermined(Size previewSize);
    void onCameraReady();
  }

  public CameraModule(
      Context context, CameraOpenListener cameraOpenListener) {
    this.cameraOpenListener = cameraOpenListener;
    this.context = context;
    cameraConfigurationManager = new CameraConfigurationManager(context, configurationCallback);
  }

  /** Start the background thread. */
  public void start() {
    startBackgroundThread();
  }

  /** Closes the camera and background thread. */
  public void close() {
    closeCameraDevice();
    stopBackgroundThread();
  }

  public void setSurfaceTexture(SurfaceTexture surfaceTexture) {
    this.surfaceTexture = surfaceTexture;
  }

  /** Start setting up the back camera. */
  public void configureCamera(int availableWidth, int availableHeight) {
    cameraConfigurationManager.setCameraOrientation(
        CameraConfigurationManager.CameraDirection.BACK,
        availableWidth,
        availableHeight);
  }

  /** Send a capture request to take a still picture. */
  public void captureStillPicture(int filterId) {
    this.filterId = filterId;
    try {
      final CaptureRequest.Builder captureBuilder =
          cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
      captureBuilder.addTarget(imageReader.getSurface());
      captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
          CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
      captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getCameraRotationAngle());

      final CameraCaptureSession.CaptureCallback captureCallback =
          new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                           @NonNull CaptureRequest request,
                                           @NonNull TotalCaptureResult result) {
              Toast.makeText(context, "Picture taken!", Toast.LENGTH_SHORT).show();
            }
      };
      captureSession.capture(captureBuilder.build(), captureCallback, backgroundHandler);
    } catch (CameraAccessException e) {
      Log.e(TAG, "Could not access camera when trying to capture an image", e);
    }
  }

  /** Retrieves the necessary rotation angle to rotate the camera image upright. */
  public Integer getCameraRotationAngle() {
    return cameraConfigurationManager.getRotationForCurrentCamera();
  }

  private void openCameraDevice() {
    try {
      if (!cameraLock.tryAcquire(SEMAPHORE_MAX_WAIT_TIME_MILLIS, TimeUnit.MILLISECONDS)) {
        throw new RuntimeException("Time out waiting to lock camera opening.");
      }
      cameraConfigurationManager.openCamera(deviceStateCallback, backgroundHandler);
      cameraOpenListener.onCameraReady();
    } catch (CameraAccessException | InterruptedException e) {
      Log.e(TAG, e.toString());
    }
  }

  private void closeCameraDevice() {
    try {
      cameraLock.acquire();
      if (captureSession != null) {
        closeCaptureSession();
      }
      if (cameraDevice != null) {
        cameraDevice.close();
        cameraDevice = null;
      }
      if (imageReader != null) {
        imageReader.close();
        imageReader = null;
      }
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
    } finally {
      cameraLock.release();
    }
  }

  private void createCaptureSession() {
    try {
      Surface surface = new Surface(surfaceTexture);
      surfaceTexture = null;
      previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
      previewRequestBuilder.addTarget(surface);
      cameraDevice.createCaptureSession(
          Arrays.asList(surface, imageReader.getSurface()), sessionStateCallback, null);
    } catch (CameraAccessException e) {
      Log.e(TAG, "Trouble accessing camera while trying to create a capture session.");
    }
  }

  private void closeCaptureSession() {
    try {
      captureSession.stopRepeating();
    } catch (CameraAccessException e) {
      Log.e(TAG, "Trouble accessing camera when trying to close the capture session.");
    }
    captureSession.close();
    captureSession = null;
  }

  /** Set autofocus capability and set preview request to the camera session. */
  private void createPreviewRequest() {
    if (cameraDevice == null || captureSession == null || previewRequestBuilder == null) {
      return;
    }
    try {
      previewRequestBuilder.set(
          CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
      previewRequest = previewRequestBuilder.build();
      captureSession.setRepeatingRequest(previewRequest, /* listener= */ null, backgroundHandler);
    } catch (CameraAccessException e) {
      Log.e(TAG, "Trouble accessing camera when trying to create preview request.");
    }
  }

  private void startBackgroundThread() {
    backgroundThread = new HandlerThread(CAMERA_THREAD_NAME);
    backgroundThread.start();
    backgroundHandler = new Handler(backgroundThread.getLooper());
  }

  private void onAvailableImage(ImageReader reader) {
    backgroundHandler.post(new ImageSaver(context, reader.acquireLatestImage(), filterId));
  }

  private void stopBackgroundThread() {
    backgroundThread.quitSafely();
    try {
      backgroundThread.join();
      backgroundThread = null;
      backgroundHandler = null;
    } catch (InterruptedException e) {
      Log.e(TAG, e.toString());
    }
  }
}
