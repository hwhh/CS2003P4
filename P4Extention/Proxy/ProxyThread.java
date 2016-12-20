package Proxy;

import java.io.*;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Class that handles individual requests.
 */
class ProxyThread extends Thread {

    /**
     * Max buffer size.
     */
    private static final int BUFFER_SIZE = 8192;
    /**
     * HTTPS pattern.
     */
    private static final Pattern HTTPS_PATTERN = Pattern.compile("CONNECT (.+):(.+) HTTP/(1\\.[01])", Pattern.CASE_INSENSITIVE);
    /**
     * HTTP pattern.
     */
    private static final Pattern HTTP_WWW_PATTERN = Pattern.compile("(.+) http://www\\.(.+) HTTP/(1\\.[01])", Pattern.CASE_INSENSITIVE);

    /**
     * Client socket.
     */
    private Socket clientSocket;


    /**
     * Constructor.
     *
     * @param clientSocket client socket to proxy.
     */
    ProxyThread(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }


    /**
     * Rnu method called when new thread started.
     */
    @Override
    public void run() {
        try {
            //Read the input header
            String request = readLine(clientSocket);
            //Create matcher's
            Matcher httpsMatcher = HTTPS_PATTERN.matcher(request);
            Matcher httpWWWMatcher = HTTP_WWW_PATTERN.matcher(request);
            System.out.println(request);
            //If request HTTP
            if (httpWWWMatcher.find()) {
                String header;
                //Print out the entire header
                while (!(header = readLine(clientSocket)).equals("")) {
                    System.out.println(header);
                }
                //Send a re-direct to the browser with a HTTPS URL
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(clientSocket.getOutputStream(), "ISO-8859-1");
                outputStreamWriter.write(" HTTP/1.1 302 Redirect\r\n");
                outputStreamWriter.write("Location: https://www." + httpWWWMatcher.group(2) + "\n\r");
                outputStreamWriter.write("\r\n");
                outputStreamWriter.flush();
                //Close the client socket
                outputStreamWriter.close();
            }
            //If request HTTPS
            if (httpsMatcher.find()) {
                String header;
                //Print out the entire header
                while (!(header = readLine(clientSocket)).equals("")) {
                    System.out.println(header);
                }
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(clientSocket.getOutputStream(), "ISO-8859-1");
                //Open up socket to target machine with try with resources
                try (Socket forwardSocket = new Socket(httpsMatcher.group(1), Integer.parseInt(httpsMatcher.group(2)))) {
                    //Send connection established to browser
                    outputStreamWriter.write("HTTP/" + httpsMatcher.group(3) + " 200 Connection established\r\n");
                    outputStreamWriter.write("ProxyServer-agent: Simple/0.1\r\n");
                    outputStreamWriter.write("\r\n");
                    outputStreamWriter.flush();
                    //Start data transfer
                    forwardData(forwardSocket);
                }
            }
        } catch (IOException ignored) {

        } finally {
            //Close the client socket when two way transfer complete
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Read each line in the header
     * @param socket the clients socket.
     * @return A string repressing a line of the header.
     * @throws IOException if the socket is closed.
     */
    private String readLine(Socket socket) throws IOException {
        //Store each line as bytes
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        int next;
        //Reads up to a set number of bytes from the input stream into the byte array
        //And returns the total number of bytes read into the buffer, or -1 if there is no more data
        while ((next = socket.getInputStream().read()) != -1) {
            //If a new line can break
            if (next == '\n') {
                break;
            } else {
                //Write bytes to the byteArrayOutputStream
                byteArrayOutputStream.write(next);
            }
        }
        //Return the line as a string encoded in ISO-8859-1
        return byteArrayOutputStream.toString("ISO-8859-1").replaceAll("(\\r|\\n)", "");
    }

    /**
     * Tunnel data from the target socket to the client socket on one thread and
     * simultaneously on another thread have data tunneled client socket back the target socket
     * @param forwardSocket the target socket.
     */
    private void forwardData(Socket forwardSocket) {
        //Create new thread to tunnel data from the target to the client
        Thread remoteToClient = new Thread() {
            public void run() {
                writeData(forwardSocket, clientSocket);
            }
        };
        //Start the thread
        remoteToClient.start();
        try {
            //Tunnel data from the client to the target
            readData(forwardSocket);
        } catch (IOException ignored) {
        } finally {
            try {
                //Wait for the remoteToClient to finish before starting a new one
                remoteToClient.join();
            } catch (InterruptedException ignored) {

            }
        }
    }

    /**
     * Read the data from a socket.
     * @param forwardSocket the target
     * @throws IOException if a socket is broken
     */
    private void readData(Socket forwardSocket) throws IOException {
        //Reads in the next byte from the client’s socket input stream
        int read = clientSocket.getInputStream().read();
        if (read != -1) {
            //If there is more data available
            if (read != '\n') {
                //If the data doesnt represent a new lien
                forwardSocket.getOutputStream().write(read);
            }
            //Write the data from the client to the target
            writeData(clientSocket, forwardSocket);
        } else {
            //Close the sockets when the transfer is complete
            if (!forwardSocket.isOutputShutdown())
                forwardSocket.shutdownOutput();
            if (!clientSocket.isInputShutdown())
                clientSocket.shutdownInput();
        }
    }

    /**
     * Called to set up Input and Output streams with resources.
     * @param inputSocket the socket data is being read from
     * @param outputSocket the socket data is being output to
     */
    private void writeData(Socket inputSocket, Socket outputSocket) {
        //Try with resources
        try (InputStream inputStream = inputSocket.getInputStream();
             OutputStream outputStream = outputSocket.getOutputStream()
        ) {
            writeData(inputStream, outputStream);
        } catch (IOException ignored) {
            ignored.printStackTrace();
        }
    }


    /**
     *  Write the response.
     * @param inputStream the socket data is being read from
     * @param outputStream the socket data is being output to
     * @throws IOException if a stream is broken
     */
    private void writeData(InputStream inputStream, OutputStream outputStream) throws IOException {
        try {
            //Create buffer
            byte by[] = new byte[BUFFER_SIZE];
            //Reads up to a set number of bytes from the input stream into the byte array
            //And returns the total number of bytes read into the buffer, or -1 if there is no more data
            int index = inputStream.read(by, 0, BUFFER_SIZE);
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            //While loop which breaks whilst there's more data in the input stream
            while (index != -1) {
                //writes the contents of the byte array
                outputStream.write(by, 0, index);
                byteArrayOutputStream.write(by, 0, index);
                //Reads next chunk of data into the buffer
                index = inputStream.read(by, 0, BUFFER_SIZE);
                outputStream.flush();
            }
        } catch (IOException ignored) {
        }
    }


}


