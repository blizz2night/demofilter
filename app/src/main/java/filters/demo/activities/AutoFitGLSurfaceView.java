package filters.demo.activities;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;

/**
 * A {@link GLSurfaceView} that can be adjusted to a specified aspect ratio.
 */
public final class AutoFitGLSurfaceView extends GLSurfaceView {

  private int ratioWidth = 0;
  private int ratioHeight = 0;

  public AutoFitGLSurfaceView(Context context) {
    this(context, /* attrs= */ null);
  }

  public AutoFitGLSurfaceView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  /**
   * Sets the aspect ratio for this view. The size of the view will be measured based on the ratio
   * calculated from the parameters. Note that the actual sizes of parameters don't matter, that is,
   * calling setAspectRatio(2, 3) and setAspectRatio(4, 6) make the same result.
   */
  public void setAspectRatio(int width, int height) {
    if (width < 0 || height < 0) {
      throw new IllegalArgumentException("Size cannot be negative.");
    }
    ratioWidth = width;
    ratioHeight = height;
    requestLayout();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    int width = MeasureSpec.getSize(widthMeasureSpec);
    int height = MeasureSpec.getSize(heightMeasureSpec);
    if (ratioWidth == 0 || ratioHeight == 0) {
      setMeasuredDimension(width, height);
    } else {
      if (width < height * ratioWidth / ratioHeight) {
        setMeasuredDimension(width, width * ratioHeight / ratioWidth);
      } else {
        setMeasuredDimension(height * ratioWidth / ratioHeight, height);
      }
    }
  }
}
