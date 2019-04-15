package filters.demo.activities;

import android.content.Context;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Util class to help perform file operations. */
public final class FileUtil {

  public static final String UNFILTERED_FILE_PREFIX = "tempImage";

  private static final String TAG = FileUtil.class.getSimpleName();
  private static final String JPEG_FILE_EXTENSION = ".jpg";
  private static final String FILTERED_FILE_PREFIX = "filteredImage";
  private static final String UNFILTERED_DIRECTORY = "unfilteredImages";

  /** Copies byte array data to a file that is associated with a unique ID . */
  @Nullable
  public static File writeBytesToFile(File directory, String uniqueId, byte[] data) {
    File unfilteredImageFile = createUnfilteredFile(directory, uniqueId);
    FileOutputStream output = null;
    try {
      output = new FileOutputStream(unfilteredImageFile);
      output.write(data);
      output.close();
    } catch (IOException e) {
      Log.e(TAG, "Could not write bytes to designated file.", e);
      unfilteredImageFile.delete();
      return null;
    } finally {
      if (output != null) {
        try {
          output.close();
        } catch (IOException e) {
          // Safely ignore because we would have already handled a partial data write in our catch
          // block.
        }
      }
    }
    return unfilteredImageFile;
  }

  /** Retrieves the right directory depending on whether the image requires a filter or not. */
  public static File getProperDirectory(Context context, boolean requiresFilter) {
    if (requiresFilter) {
      return getOrCreateUnfilteredDirectory(context);
    }
    return getDCIMDirectory();
  }

  /** Fetch/create the directory where the unfiltered image should be kept. */
  public static File getOrCreateUnfilteredDirectory(Context context) {
    File directory = new File(context.getCacheDir(), UNFILTERED_DIRECTORY);
    if (!directory.exists()) {
      directory.mkdir();
    }
    return directory;
  }

  /** Create a file that looks like "filteredImage[uniqueId].jpg" in the DCIM folder. */
  public static File createFilteredFile(String uniqueId) {
    return new File(
        getDCIMDirectory(),
        FILTERED_FILE_PREFIX + uniqueId + JPEG_FILE_EXTENSION);
  }

  private static File getDCIMDirectory() {
    return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
  }

  /** Create a file that looks like "tempImage[uniqueId].jpg" in a specified directory. */
  private static File createUnfilteredFile(File directory, String uniqueId) {
    return new File(directory, UNFILTERED_FILE_PREFIX + uniqueId + JPEG_FILE_EXTENSION);
  }
}
