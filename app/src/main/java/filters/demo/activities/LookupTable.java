package filters.demo.activities;

/** Simple Java class to hold some of the lookup table properties. */
public final class LookupTable {

  /** Name representing the filter. */
  private final String name;
  /** Indicates that this filter is grayscale. */
  private final boolean isGrayscale;
  /** Ids to connect to the correct filter in Google Photos. */
  private final int id;

  public static LookupTable create(String name, boolean isGrayscale, int id) {
    return new LookupTable(name, isGrayscale, id);
  }

  private LookupTable(String name, boolean isGrayscale, int id) {
    this.name = name;
    this.isGrayscale = isGrayscale;
    this.id = id;
  }

  // TODO(suhongjin): Display names along with the filters.
  public String getName() {
    return name;
  }

  public boolean isGrayscale() {
    return isGrayscale;
  }

  public int getId() {
    return id;
  }
}
