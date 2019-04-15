package filters.demo.activities;

import android.content.Context;
import android.content.Intent;
import android.media.Image;
import android.net.Uri;
import android.util.Log;
import com.google.common.io.Files;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.UUID;

/** Handles saving a jpeg image into a file in DCIM directory. */
public final class ImageSaver implements Runnable {

  private static final String TAG = ImageSaver.class.getSimpleName();

  private final Context context;
  private final Image image;
  private final int filterId;
  private final PartnerContentProviderCaller caller;

  ImageSaver(Context context, Image image, int filterId) {
    this.context = context;
    this.image = image;
    this.filterId = filterId;
    caller = new PartnerContentProviderCaller(context);
  }

  @Override
  public void run() {
    if (image == null) {
      return;
    }
    // Extract data from image into a byte array.
    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
    byte[] bytes = new byte[buffer.remaining()];
    buffer.get(bytes);
    image.close();
    String uniqueId = createUniqueId();
    boolean requiresFilter = filterId != FilterGLRenderer.NO_FILTER_ID;
    File directory = FileUtil.getProperDirectory(context, requiresFilter);
    // If not using a filter, we are done as we do not have to apply filters.
    File unfilteredImageFile =
        FileUtil.writeBytesToFile(
            directory,
            uniqueId,
            bytes);
    // We are done if no filter is applied as we have already saved to DCIM.
    if (!requiresFilter) {
      notifyMediaStoreOfNewFile(context, Uri.fromFile(unfilteredImageFile));
      return;
    }
    if (unfilteredImageFile == null) {
      Log.e(TAG, "Unable to write unfiltered image file.");
      return;
    }

    Uri photosFilteredUri = applyFilter(unfilteredImageFile);
    if (photosFilteredUri == null) {
      Log.e(TAG, "Uri returned from Google Photos was null.");
      return;
    }
    // For version 2 and above Photos will save to the output uri specified by the partner app.
    // The only action needed after apply filters is to notify media store of the new media.
    notifyMediaStoreOfNewFile(context, photosFilteredUri);
  }

  /** Call the applyFilter method in the PartnerContentProvider. */
  private Uri applyFilter(File unfilteredFile) {
    // Version 1 of the API was deprecated in Photos 3.27. Please use versions 2 and above.
    if (DemoActivity.version >= 2) {
      File outputFile = FileUtil
          .createFilteredFile(Files.getNameWithoutExtension(unfilteredFile.getName()));
      return caller.applyFilter(unfilteredFile.getName(), filterId, Uri.fromFile(outputFile));
    } else {
      throw new
          UnsupportedOperationException("API " + DemoActivity.version + " is no longer supported");
    }
  }

  /** Notify MediaStore of the new file. */
  private void notifyMediaStoreOfNewFile(Context context, Uri uri) {
    context.sendBroadcast(
        new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri));
  }

  private String createUniqueId() {
    return UUID.randomUUID().toString();
  }
}
