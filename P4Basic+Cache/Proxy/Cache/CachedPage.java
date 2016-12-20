package Proxy.Cache;

/**
 * Represents website stored in cache.
 */
class CachedPage {

    /**
     * The time the page was last accessed and the data for the page.
     */
    long lastAccessed = System.currentTimeMillis();
    byte[] page;

    /**
     * Constructor.
     *
     * @param page the web page to be saved.
     */
    CachedPage(byte[] page) {
        this.page = page;
    }


}
