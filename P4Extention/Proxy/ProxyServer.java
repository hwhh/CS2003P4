package Proxy;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * ProxyServer, spans off requests.
 */
public class ProxyServer extends Thread {

    /**
     * Constructor.
     */
    private ProxyServer() {
        super("ProxyServer Thread");
    }


    public static void main(String[] args) {
        (new ProxyServer()).run();
    }

    @Override
    public void run() {
        //Create new socket.
        try (ServerSocket serverSocket = new ServerSocket(7538)) {
            Socket socket;
            while ((socket = serverSocket.accept()) != null) {
                //Create new threads per request.
                (new ProxyThread(socket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
