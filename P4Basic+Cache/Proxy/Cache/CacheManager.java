package Proxy.Cache;


import org.apache.commons.collections4.MapIterator;
import org.apache.commons.collections4.map.LRUMap;

import java.util.ArrayList;

/**
 * Represents the cache storage system.
 */
public class CacheManager {

    /**
     * Stores the cached web pages.
     */
    private final LRUMap CacheMap;
    /**
     * The time to live for all the pages.
     */
    private long timeToLive;


    /**
     * Constructor.
     * @param timeToLive The time to live for all pages.
     * @param timerInterval The time between checking for expired items.
     * @param maxItems The max number of items allowed in the cache.
     */
    public CacheManager(long timeToLive, final long timerInterval, int maxItems) {
        this.timeToLive = timeToLive * 1000;
        CacheMap = new LRUMap(maxItems);
        if (timeToLive > 0 && timerInterval > 0) {
            //Create new thread and call the clean up method in the background.
            Thread t = new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(timerInterval * 1000);
                    } catch (InterruptedException ignored) {
                    }
                    cleanup();
                }
            });
            //Does not prevent the JVM from exiting when the program finishes
            t.setDaemon(true);
            //Start the thread
            t.start();
        }
    }

    /**
     * Store a web page in the cache.
     * @param pageURL The url of the page.
     * @param page The data for the page.
     */
    public void put(String pageURL, byte[] page) {
        synchronized (CacheMap) {
            //Store the page in the cache.
            CacheMap.put(pageURL, new CachedPage(page));
        }
    }


    /**
     * Get a page back from the cache.
     * @param pageURL the url of the page to be recived.
     * @return the data for the page.
     */
    public byte[] get(String pageURL) {
        synchronized (CacheMap) {
            //Try and find the page
            CachedPage c = (CachedPage) CacheMap.get(pageURL);
            if (c == null)
                return null;
            else {
                //Update the last accessed attribute for the page
                c.lastAccessed = System.currentTimeMillis();
                return c.page;
            }
        }
    }


    /**
     * Maintains the LRUMap.
     */
    private void cleanup() {
        //Get the current time
        long now = System.currentTimeMillis();
        ArrayList<String> deleteKey;
        synchronized (CacheMap) {
            //Make an iterator to loop over items stored int the LRUMap
            MapIterator itr = CacheMap.mapIterator();
            System.out.println("Preforming cleaning");
            deleteKey = new ArrayList<>();
            String pageURL;
            CachedPage c;
            while (itr.hasNext()) {
                //Get the current page and check if it should be removed
                pageURL = (String) itr.next();
                c = (CachedPage) itr.getValue();
                if (c != null && (now > (timeToLive + c.lastAccessed))) {
                    //Add expired items to the delete array list.
                    System.out.println("Removed: " + pageURL);
                    deleteKey.add(pageURL);
                }
            }
        }
        //Remove the items that are expired from the LRUMap
        for (String pageURL : deleteKey) {
            synchronized (CacheMap) {
                CacheMap.remove(pageURL);
            }
            Thread.yield();
        }
    }
}
