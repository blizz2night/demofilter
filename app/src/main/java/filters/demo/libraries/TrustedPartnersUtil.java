package filters.demo.libraries;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Contains the certificates of trusted partners (aka Google Photos).
 *
 * TODO(suhongjin): Use trusted_certificates.xml resource file when we migrate to Purple Puppies.
 */
public final class TrustedPartnersUtil {

  private static final String PHOTOS_DEV_CERTIFICATE =
      "4BA713DFECE93D47572DC5E845A7A82C4A891F2F";
  private static final String PHOTOS_DEBUG_CERTIFICATE =
      "24BB24C05E47E0AEFA68A58A766179D9B613A600";

  public static Set<String> getTrustedPartnerCertificateHashes() {
    return new HashSet<>(Arrays.asList(PHOTOS_DEV_CERTIFICATE, PHOTOS_DEBUG_CERTIFICATE));
  }
}
