package filters.demo.activities;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import filters.demo.libraries.TrustedPartners;
import filters.demo.libraries.TrustedPartnersUtil;

/** Handles calls to the PartnerContentProvider. */
public final class PartnerContentProviderCaller {

  private static final String TAG = PartnerContentProviderCaller.class.getSimpleName();
  private static final String PHOTOS_AUTHORITY =
      "com.google.android.apps.photos.partnercontentprovider";
  private static final Uri PHOTOS_AUTHORITY_URI = Uri.parse("content://" + PHOTOS_AUTHORITY);
  private static final String PARTNER_AUTHORITY =
      "filters.demo.activities.filterdemocontentprovider";

  /** getVersion method name */
  public static final String GET_VERSION_METHOD_NAME = "getVersion";
  /** getVersion result bundle key names */
  public static final String VERSION_KEY_NAME = "version";

  /** getFilter method name */
  public static final String GET_FILTER_METHOD_NAME = "getFilters";
  /** getFilter result bundle key names */
  public static final String FILTER_BITMAPS_LIST_KEY_NAME = "filter_bitmaps";
  public static final String FILTER_IDS_KEY_NAME = "filter_ids";
  public static final String FILTER_NAMES_LIST_KEY_NAME = "filter_names";
  public static final String FILTER_IS_GRAYSCALE_BOOL_LIST_KEY_NAME = "filter_isGrayscale";

  /** applyFilter method name and extra param names */
  private static final String APPLY_FILTER_METHOD_NAME = "applyFilter";
  private static final String FILTER_ID_KEY_NAME = "filter_id";
  private static final String PARTNER_FILE_KEY_NAME = "file_name";
  private static final String PARTNER_AUTHORITY_KEY_NAME = "partner_authority";
  private static final String OUTPUT_URI_KEY_NAME = "output_uri";

  /** deleteFilteredPhoto method name */
  private static final String DELETE_FILTERED_PHOTO_METHOD_NAME = "deleteFilteredPhoto";
  /** deleteFilteredPhoto result bundle key names */
  private static final String IS_DELETE_SUCCESS_KEY_NAME = "is_delete_success";

  private final Context context;
  private final TrustedPartners trustedPartners;

  public PartnerContentProviderCaller(Context context) {
    this.context = context;
    this.trustedPartners =
        new TrustedPartners(context, TrustedPartnersUtil.getTrustedPartnerCertificateHashes());
    validateAuthority();
  }

  public int getVersionFromPhotos() {
    Bundle bundle = context
        .getContentResolver()
        .call(PHOTOS_AUTHORITY_URI, GET_VERSION_METHOD_NAME, /* arg= */ null, /* extras= */ null);
    return bundle.getInt(PartnerContentProviderCaller.VERSION_KEY_NAME);
  }

  /** Returns the bundle retrieved from PartnerContentProvider getFilters method. */
  @Nullable
  public Bundle getFilters() {
    return context
        .getContentResolver()
        .call(PHOTOS_AUTHORITY_URI, GET_FILTER_METHOD_NAME, /* arg= */ null, /* extras= */ null);
  }

  /**
   * Calls the applyFilter method in PartnerContentProvider.
   *
   * @param filename Filename of the unfiltered image we want to apply the filter on.
   * @param filterId Indicates which filter we want to use. Uses the filter id from getFilters
   * Content Provider call.
   * @param outputUri The uri to which Photos will save the filtered image to.
   * @return The output Uri if successful, null if there was an error.
   */
  @Nullable
  public Uri applyFilter(String filename, int filterId, Uri outputUri) {
    Bundle paramsBundle = new Bundle();
    paramsBundle.putInt(FILTER_ID_KEY_NAME, filterId);
    paramsBundle.putString(PARTNER_FILE_KEY_NAME, filename);
    paramsBundle.putString(PARTNER_AUTHORITY_KEY_NAME, PARTNER_AUTHORITY);
    paramsBundle.putParcelable(OUTPUT_URI_KEY_NAME, outputUri);

    Bundle returnedBundle =
        context
            .getContentResolver()
            .call(PHOTOS_AUTHORITY_URI, APPLY_FILTER_METHOD_NAME, /* arg= */ null, paramsBundle);
    // A null returnedBundle indicates the Photos was not able to successfully apply and save the
    // filtered photo.
    if (returnedBundle == null) {
      Log.e(TAG, "Bundle returned from PartnerContentProvider was null.");
      return null;
    }
    return outputUri;
  }

  /** Alert Photos we are done using the filtered file stored in the Photos app. */
  @Nullable
  public Boolean deleteFilteredPhoto(String filename) {
    Bundle returnedBundle = context.getContentResolver().call(
        PHOTOS_AUTHORITY_URI,
        DELETE_FILTERED_PHOTO_METHOD_NAME,
        filename,
        /* extras= */ null);
    if (returnedBundle == null) {
      return null;
    }
    return returnedBundle.getBoolean(IS_DELETE_SUCCESS_KEY_NAME);
  }

  /** Validate given authority before calling the content provider methods. */
  private void validateAuthority() {
    if (!trustedPartners.isTrustedAuthority(PHOTOS_AUTHORITY)) {
      throw new SecurityException(PHOTOS_AUTHORITY + " is not authorized to access the content");
    }
  }
}
