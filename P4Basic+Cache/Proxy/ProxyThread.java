package Proxy;

import Proxy.Cache.CacheManager;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.util.StringTokenizer;

/**
 * Class that handles individual requests.
 */
class ProxyThread extends Thread {

    public static final int TIMEOUT = 1000;
    /**
     * src.Proxy.Cache manager with following properties: time to live 300 second, cleaning every 100 seconds, max number of pages 10000.
     */
    private static final CacheManager cache = new CacheManager(300, 100, 10000);
    /**
     * Max buffer size.
     */
    private static final int BUFFER_SIZE = 32768;
    /**
     * Client socket.
     */
    private Socket socket = null;

    /**
     * Constructor.
     *
     * @param socket client socket to proxy.
     */
    ProxyThread(Socket socket) {
        super("ProxyThread");
        this.socket = socket;
    }

    /**
     * Check if url is available.
     *
     * @param url url to be checked.
     * @return whether target is reachable with https.
     */
    private static boolean pingURL(String url) {
        try {
            //Create HTTPS connection
            HttpsURLConnection connection = (HttpsURLConnection) new URL(url).openConnection();
            //Set attributes
            connection.setConnectTimeout(TIMEOUT);
            connection.setReadTimeout(TIMEOUT);
            connection.setRequestMethod("HEAD");
            //Check response code
            int responseCode = connection.getResponseCode();
            return (200 <= responseCode && responseCode <= 399);
        } catch (IOException exception) {
            return false;
        }
    }

    /**
     * Rnu method called when new thread started.
     */
    @Override
    public void run() {
        try {
            //Set up input and output streams.
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            //Get the URL from the client header
            String urlToCall = getURL(in);
            try {
                //Replace http:// with https://
                urlToCall = urlToCall.replace("http://", "https://");
                //Look for either http or https version of the website in cache
                byte[] websiteHTTPS = cache.get(urlToCall);
                byte[] websiteHTTP = cache.get(urlToCall.replace("https://", "http://"));
                if (websiteHTTPS == null && websiteHTTP == null) {
                    HttpURLConnection conn;
                    //Check server supports https
                    if (pingURL(urlToCall)) {
                        //Make https connection
                        conn = createHTTPConnection(urlToCall);
                    } else {
                        //Otherwise make http connection
                        urlToCall = urlToCall.replace("https://", "http://");
                        conn = createHTTPConnection(urlToCall);
                    }
                    System.out.println(urlToCall);
                    //Write the output from target to client
                    writeData(urlToCall, conn, out);
                } else {
                    //If website stored in cache
                    InputStream inputStream = null;
                    //Get the website type
                    if (websiteHTTPS != null)
                        inputStream = new ByteArrayInputStream(websiteHTTPS);
                    if (websiteHTTP != null)
                        inputStream = new ByteArrayInputStream(websiteHTTP);
                    //Write data from cache to client.
                    writeData(urlToCall, inputStream, out, true);
                }
                //Close client socket
                socket.close();
            } catch (Exception e) {
                out.writeBytes("");
            }
        } catch (IOException ignored) {
        }
    }

    /**
     * Get the url from the header.
     * @param in the buffered reader containing the URL.
     * @return the url
     * @throws IOException if in invalid.
     */
    private String getURL(BufferedReader in) throws IOException {
        String inputLine;
        int count = 0;
        String urlToCall = "";
        //Read in the header line by line
        while ((inputLine = in.readLine()) != null) {
            //Split each line up into tokens
            try {
                //Parse the header
                StringTokenizer tok = new StringTokenizer(inputLine);
                tok.nextToken();
            } catch (Exception e) {
                break;
            }
            //First line contains target url
            if (count == 0) {
                //Split first line by spaces and get url
                String[] tokens = inputLine.split("\\s");
                urlToCall = tokens[1];
            }
            count++;
        }
        return urlToCall;
    }

    /**
     * Creates the connection object to the target.
     * @param url the url of the target machine
     * @return The connection to the target.
     * @throws IOException If url bad.
     */
    private HttpURLConnection createHTTPConnection(String url) throws IOException {
        //Create URL object
        URL urlToCall = new URL(url);
        //Open URL connection and cast to HttpURLConnection
        HttpURLConnection conn = (HttpURLConnection) urlToCall.openConnection();
        //Get the response code from the connection
        int status = conn.getResponseCode();
        //Check if target has moved location
        if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER) {
            String redirectUrl;
            //Check if URL is HTTPS or HTTP and update connection target URL
            if (url.startsWith("https://"))
                redirectUrl = conn.getHeaderField("Location").replace("http://", "https://");
            else if (url.startsWith("https://"))
                redirectUrl = conn.getHeaderField("Location").replace("https://", "http://");
            else
                redirectUrl = conn.getHeaderField("Location");
            urlToCall = new URL(redirectUrl);
            conn = (HttpURLConnection) urlToCall.openConnection();
        }
        //Return connection
        return conn;
    }

    /**
     * Called when site not in cache.
     * @param urlToCall the url of target machine
     * @param conn the connection to the target machine
     * @param out the output stream to the client.
     */
    private void writeData(String urlToCall, HttpURLConnection conn, DataOutputStream out) {
        //Try with resources
        try (InputStream inputStream = conn.getInputStream()) {
            writeData(urlToCall, inputStream, out, false);
        } catch (IOException ignored) {
        }

    }

    /**
     * Write the response.
     * @param urlToCall the url of target machine
     * @param is Stream containing data from sire
     * @param out the output stream to the client.
     * @param fromCache the output stream to the client.
     */
    private void writeData(String urlToCall, InputStream is, DataOutputStream out, boolean fromCache) {
        try {
            //Set up store to save web page data
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            //Create buffer
            byte by[] = new byte[BUFFER_SIZE];
            //Reads up to a set number of bytes from the input stream into the byte array
            //And returns the total number of bytes read into the buffer, or -1 if there is no more data
            int index = is.read(by, 0, BUFFER_SIZE);
            //While loop which breaks whilst there's more data in the input stream
            while (index != -1) {
                //writes the contents of the byte array to client
                out.write(by, 0, index);
                //writes the contents of the byte array to temporary store
                byteArrayOutputStream.write(by, 0, index);
                //Reads next chunk of data into the buffer
                index = is.read(by, 0, BUFFER_SIZE);
            }
            out.flush();
            byteArrayOutputStream.close();
            //Check if page being loaded from cache
            if (!fromCache && cache.get(urlToCall) == null) {
                //If page wasn't loaded from cache add page to cache
                cache.put(urlToCall, byteArrayOutputStream.toByteArray());
            } else if (fromCache) {
                System.out.println(urlToCall + ": was loaded from cache");
            }
        } catch (IOException ignored) {
        }
    }


}
