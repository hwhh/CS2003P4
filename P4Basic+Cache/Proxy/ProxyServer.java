package Proxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

/**
 * ProxyServer, spans off requests.
 */
public class ProxyServer {


    public static void main(String[] args) throws IOException {
        boolean listening = true;
        //Create new socket.
        try (ServerSocket serverSocket = new ServerSocket()) {
            //Bind socket to an address
            serverSocket.bind(new InetSocketAddress(7538));
            while (listening) {
                //Create new threads per request.
                new ProxyThread(serverSocket.accept()).start();
            }
        }

    }

}
