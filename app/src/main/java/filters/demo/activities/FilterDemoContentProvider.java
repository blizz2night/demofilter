package filters.demo.activities;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import java.io.File;
import java.io.FileNotFoundException;
import filters.demo.libraries.TrustedPartners;
import filters.demo.libraries.TrustedPartnersUtil;

/** Provide Google Photos access to the internally-saved unfiltered photo. */
public final class FilterDemoContentProvider extends ContentProvider {

  private static final String TAG = FilterDemoContentProvider.class.getSimpleName();
  private static final String READ_MODE = "r";

  private TrustedPartners trustedPartners;

  @Override
  public final boolean onCreate() {
    trustedPartners =
        new TrustedPartners(getContext(), TrustedPartnersUtil.getTrustedPartnerCertificateHashes());
    return true;
  }

  @Nullable
  @Override
  public Cursor query(
      @NonNull Uri uri,
      @Nullable String[] projection,
      @Nullable String selection,
      @Nullable String[] selectionArgs,
      @Nullable String sortOrder) {
    throw new UnsupportedOperationException("query not supported");
  }

  @Nullable
  @Override
  public String getType(@NonNull Uri uri) {
    return null;
  }

  @Nullable
  @Override
  public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
    throw new UnsupportedOperationException("insert not supported");
  }

  @Override
  public int delete(
      @NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
    throw new UnsupportedOperationException("delete not supported");
  }

  @Override
  public int update(
      @NonNull Uri uri,
      @Nullable ContentValues values,
      @Nullable String selection,
      @Nullable String[] selectionArgs) {
    throw new UnsupportedOperationException("update not supported");
  }

  @Nullable
  @Override
  public ParcelFileDescriptor openFile(Uri uri, String mode) {
    validateCallingPackage();
    String filename = uri.getLastPathSegment();
    if (filename.startsWith(FileUtil.UNFILTERED_FILE_PREFIX)) {
      if (!mode.equals(READ_MODE)) {
        throw new UnsupportedOperationException(mode + " is not supported. Only use read mode.");
      }
      String directoryPath = FileUtil.getOrCreateUnfilteredDirectory(getContext()).getPath();
      File file = new File(directoryPath, filename);
      try {
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
      } catch (FileNotFoundException e) {
        Log.e(TAG, "Unable to open file");
      }
    }
    return null;
  }

  /** Validate calling package has a trusted signing certificate. */
  private void validateCallingPackage() {
    String callingPackageName = getCallingPackage();
    if (callingPackageName.isEmpty() || !trustedPartners.isTrustedApplication(callingPackageName)) {
      throw new SecurityException(callingPackageName + " is not authorized to access the content");
    }
  }
}
