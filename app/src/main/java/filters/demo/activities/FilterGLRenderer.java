package filters.demo.activities;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.support.annotation.Nullable;
import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * OpenGL renderer that shows the launcher icon image on the screen.
 */
public final class FilterGLRenderer implements GLSurfaceView.Renderer {

  public static final int NO_FILTER_ID = -1;
  private static final String TAG = FilterGLRenderer.class.getSimpleName();
  private static final int BYTES_PER_FLOAT = 4;
  private static final int VERTEX_COUNT = 4;
  private static final int POSITION_OFFSET = 0;
  private static final int POSITION_COUNT = 2;
  private static final int TEXTURE_COORDINATE_COUNT = 2;
  private static final int TEXTURE_COORDINATE_OFFSET = 2;
  private static final int STRIDE_BYTES =
      (TEXTURE_COORDINATE_COUNT + TEXTURE_COORDINATE_OFFSET) * BYTES_PER_FLOAT;
  private static final String VERTEX_SHADER_CODE_FILENAME = "photo_vsh.vsh";
  private static final String FRAGMENT_SHADER_CODE_FILENAME = "photo_fsh.fsh";

  private final Context context;
  private final FloatBuffer verticesBuffer;
  private final SurfaceReadyCallback surfaceReadyCallback;
  private final float[] mvpMatrix = new float[16];
  /** List that maps which filters are treated as grayscale. */
  private final float[] isGrayscaleList = new float[9];

  // Handles for shader attributes and uniforms.
  private int positionHandle;
  private int mvpMatrixHandle;
  private int textureCoordinateHandle;
  private int textureHandle;
  private int isGrayscaleHandle;
  private int showNineTilesHandle;
  private int lutIndexHandle;
  private int lutsCountHandle;
  private int lutHandle;
  private int[] textureNames;

  private String vertexShaderCode;
  private String fragmentShaderCode;
  private int angle;
  private int lutsCount;
  /** Index of the filter we want to show (single tile view only). */
  private int lutIndex = NO_FILTER_ID;
  private boolean showNineTiles;
  @Nullable
  private Bitmap lutBitmap;
  @Nullable
  private ArrayList<LookupTable> luts;
  @Nullable
  private SurfaceTexture surfaceTexture;

  /** Callback to pass {@link SurfaceTexture} with proper texture ID to {@link CameraModule}.*/
  public interface SurfaceReadyCallback {
    void onSurfaceReady(SurfaceTexture surfaceTexture);
  }

  public FilterGLRenderer(Context context, SurfaceReadyCallback surfaceReadyCallback) {
    this.context = context;
    this.surfaceReadyCallback = surfaceReadyCallback;
    this.angle = 0;
    this.lutsCountHandle = 0;
    this.luts = null;
    verticesBuffer =
        ByteBuffer.allocateDirect(VERTEX_COUNT * STRIDE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer();
    loadShaderFromAssets();
  }

  @Override
  public void onSurfaceCreated(GL10 glUnused, EGLConfig config) {
    // Compile vertex shader code.
    int vertexShaderHandle = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
    if (vertexShaderHandle != 0) {
      GLES20.glShaderSource(vertexShaderHandle, vertexShaderCode);
      GLES20.glCompileShader(vertexShaderHandle);
      final int[] compileStatus = new int[1];
      GLES20.glGetShaderiv(
          vertexShaderHandle, GLES20.GL_COMPILE_STATUS, compileStatus, /* offset= */ 0);
      if (compileStatus[0] == 0) {
        GLES20.glDeleteShader(vertexShaderHandle);
        vertexShaderHandle = 0;
      }
    }
    if (vertexShaderHandle == 0) {
      throw new RuntimeException("Error creating vertex shader.");
    }

    // Compile fragment shader code.
    int fragmentShaderHandle = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
    if (fragmentShaderHandle != 0) {
      GLES20.glShaderSource(fragmentShaderHandle, fragmentShaderCode);
      GLES20.glCompileShader(fragmentShaderHandle);
      final int[] compileStatus = new int[1];
      GLES20.glGetShaderiv(
          fragmentShaderHandle, GLES20.GL_COMPILE_STATUS, compileStatus, /* offset= */ 0);
      if (compileStatus[0] == 0) {
        GLES20.glDeleteShader(fragmentShaderHandle);
        fragmentShaderHandle = 0;
      }
    }
    if (fragmentShaderHandle == 0) {
      throw new RuntimeException("Error creating fragment shader.");
    }

    // Setup program handle and attach vertex/fragment shader code.
    int programHandle = GLES20.glCreateProgram();
    if (programHandle != 0) {
      GLES20.glAttachShader(programHandle, vertexShaderHandle);
      GLES20.glAttachShader(programHandle, fragmentShaderHandle);
      GLES20.glLinkProgram(programHandle);
      final int[] linkStatus = new int[1];
      GLES20.glGetProgramiv(programHandle, GLES20.GL_LINK_STATUS, linkStatus, /* offset= */ 0);
      if (linkStatus[0] == 0) {
        GLES20.glDeleteProgram(programHandle);
        programHandle = 0;
      }
    }
    if (programHandle == 0) {
      throw new RuntimeException("Error creating program.");
    }

    // Link handles to variables in shader code.
    positionHandle = GLES20.glGetAttribLocation(programHandle, "a_Position");
    mvpMatrixHandle = GLES20.glGetUniformLocation(programHandle, "u_MVPMatrix");
    textureCoordinateHandle = GLES20.glGetAttribLocation(programHandle, "a_TexCoord");
    textureHandle = GLES20.glGetUniformLocation(programHandle, "u_Texture");
    isGrayscaleHandle = GLES20.glGetUniformLocation(programHandle, "u_LookIsGrayscale");
    showNineTilesHandle = GLES20.glGetUniformLocation(programHandle, "u_ShowNineTiles");
    lutIndexHandle = GLES20.glGetUniformLocation(programHandle, "u_LookIndex");
    lutsCountHandle = GLES20.glGetUniformLocation(programHandle, "u_LooksCount");
    lutHandle = GLES20.glGetUniformLocation(programHandle, "u_TextureLookupTable");

    GLES20.glUseProgram(programHandle);

    textureNames = new int[2];
    GLES20.glGenTextures(/* n= */ 2, textureNames, /* offset= */ 0);

    // Setup camera preview texture.
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureNames[0]);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

    // Pass vertex/texture coordinates to the FloatBuffer.
    final float[] verticesBufferData = {
      // Vertex X, Y
      // Texture coordinates U, V
      -1f, -1f,
      1f, 0f,
      1f, -1f,
      0f, 0f,
      -1f, 1f,
      1f, 1f,
      1f, 1f,
      0f, 1f,
    };
    verticesBuffer.position(0);
    verticesBuffer.put(verticesBufferData).position(0);

    surfaceTexture = new SurfaceTexture(textureNames[0]);
    surfaceReadyCallback.onSurfaceReady(surfaceTexture);
  }

  @Override
  public void onSurfaceChanged(GL10 glUnused, int width, int height) {
    GLES20.glViewport(/* x= */ 0, /* y= */ 0, width, height);
  }

  @Override
  public void onDrawFrame(GL10 glUnused) {
    GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

    // Load texture from the LUT bitmap if it exists.
    if (lutBitmap != null) {
      loadLutTexture();
    }

    if (surfaceTexture == null) {
      return;
    }

    // Update the texture image to the most recent frame from the image stream.
    surfaceTexture.updateTexImage();

    // Pass in the vertex coordinates.
    verticesBuffer.position(POSITION_OFFSET);
    GLES20.glVertexAttribPointer(
      positionHandle,
      POSITION_COUNT,
      GLES20.GL_FLOAT,
      /* normalized= */ false,
      STRIDE_BYTES,
      verticesBuffer);
    GLES20.glEnableVertexAttribArray(positionHandle);

    // Pass in the texture coordinates.
    verticesBuffer.position(TEXTURE_COORDINATE_COUNT);
    GLES20.glVertexAttribPointer(
      textureCoordinateHandle,
      TEXTURE_COORDINATE_COUNT,
      GLES20.GL_FLOAT,
      /* normalized= */ false,
      STRIDE_BYTES,
      verticesBuffer);
    GLES20.glEnableVertexAttribArray(textureCoordinateHandle);

    // Set rotation matrix to orient the image properly.
    Matrix.setIdentityM(mvpMatrix, /* smOffset= */ 0);
    Matrix.rotateM(mvpMatrix, /* mOffset= */ 0, angle, /* x= */ 0f, /* y= */ 0f, /* z= */ 1f);
    GLES20.glUniformMatrix4fv(
        mvpMatrixHandle, /* count= */ 1, /* transpose= */ false, mvpMatrix, /* offset= */ 0);

    // Pass grayscale list.
    GLES20.glUniform1fv(
        isGrayscaleHandle,
        /* count= */ isGrayscaleList.length,
        isGrayscaleList,
        /* offset= */ 0);

    // Pass the texture
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    GLES20.glUniform1i(textureHandle, /* x= */ textureNames[0]);
    GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
    GLES20.glUniform1i(lutHandle, /* x= */ textureNames[1]);
    GLES20.glUniform1f(lutsCountHandle, lutsCount);
    GLES20.glUniform1f(lutIndexHandle, lutIndex);
    GLES20.glUniform1f(showNineTilesHandle, showNineTiles ? 1.0f : 0.0f);
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* offset= */ VERTEX_COUNT);
  }

  /** Build our list of LookupTables. */
  public void createLutObjects(
      ArrayList<String> filterNames,
      boolean[] filterIsGrayscaleList,
      ArrayList<Integer> filterIds) {
    luts = new ArrayList<>();
    for (int i = 0; i < filterNames.size(); i++) {
      LookupTable lut =
          LookupTable.create(filterNames.get(i), filterIsGrayscaleList[i], filterIds.get(i));
      luts.add(lut);
    }
  }

  /** Update the grayscale list based on our list of LookupTables. */
  public void updateGrayscaleList() {
    for (int i = 0; i < luts.size() && i < isGrayscaleList.length; i++) {
      isGrayscaleList[i] = luts.get(i).isGrayscale() ? 1.0f : 0.0f;
    }
  }

  public void shouldShowNineTiles(boolean showNineTiles) {
    this.showNineTiles = showNineTiles;
  }

  public void setLutIndex(int lutIndex) {
    this.lutIndex = lutIndex;
  }

  public void setRotationAngle(int angle) {
    this.angle = angle;
  }

  public void setLutBitmap(Bitmap lutBitmap) {
    this.lutBitmap = lutBitmap;
  }

  public void setLutsCount(int lutsCount) {
    this.lutsCount = lutsCount;
  }

  public int getFilterId() {
    if (lutIndex == NO_FILTER_ID) {
      return NO_FILTER_ID;
    }
    return luts.get(lutIndex).getId();
  }

  public boolean showNineTiles() {
    return showNineTiles;
  }

  /** Load texture from LUT bitmap to the proper texture id. */
  private void loadLutTexture() {
    GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, lutBitmap, 0);
    lutBitmap.recycle();
    lutBitmap = null;
  }

  private void loadShaderFromAssets() {
    vertexShaderCode = getStringFromFileInAssets(context, VERTEX_SHADER_CODE_FILENAME);
    fragmentShaderCode = getStringFromFileInAssets(context, FRAGMENT_SHADER_CODE_FILENAME);
  }

  /**
   * Parse file in /assets/ folder and return file as a string.
   *
   * <p>Used in this demo for shaders.
   *
   * @param context Context to use.
   * @param filename name of the file, including any folders, within /assets/ folder.
   * @return String of contents of file, lines are separated by {@code \n}.
   */
  private String getStringFromFileInAssets(Context context, String filename) {
    StringBuilder builder = new StringBuilder();
    try (InputStream is = context.getAssets().open(filename)) {
      BufferedReader reader = new BufferedReader(new InputStreamReader(is));
      String line;
      while ((line = reader.readLine()) != null) {
        builder.append(line).append("\n");
      }
    } catch (IOException e) {
      Log.e(TAG, "File not found when trying to parse file: " + filename);
    }
    return builder.toString();
  }
}
