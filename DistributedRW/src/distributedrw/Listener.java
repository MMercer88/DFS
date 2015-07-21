package distributedrw;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

/**
 *
 * @author Eric Gagnon, Kevin Achenbach, Yan Liang, Mike Mercer
 * @date Friday December 12, 2014
 * @assignment CSCI 3601 Distributed File Reading and Writing
 * 
 * The Listener creates a server socket that connects to the listen that retrieves
 * messages.
 */

public class Listener extends Thread {
    Node current = null;        //This Listener's node
    int port;                   //The port the node is on
    String hostname;            //localhost
    ServerSocket server_socket = null; 
    Socket socket = null;
    LinkedBlockingQueue<String> messages;   
    Queue<Integer> request_list;
    boolean running = true;
    
    public Listener(Node current, int port, String hostname, LinkedBlockingQueue messages, Queue request_list) {
        this.current = current;
        this.port = port;
        this.hostname = hostname;
        this.messages = messages;
        this.request_list = request_list;
    }
    @Override
    public void run() {
        try {
            server_socket = new ServerSocket(port);
            messages.add("Listener Ready");               //Adds readys for to ensure that they are all set up before connecting to listen sockets
            
        } catch (IOException ex) {
            Logger.getLogger(Listener.class.getName()).log(Level.SEVERE, null, ex);
        }
        while(running) {
            try {

                socket = server_socket.accept();
                //Passes its client, socket, message list, pending request list, and if its running
                Listen listen = new Listen(current, socket, messages, request_list, running);
                listen.start();
                
                
            } catch (IOException ex) {
                System.err.println("Error trying to accept socket/create Buffered reader " + ex);
            }
        }
        try {
          //After not running shutdown
          server_socket.close();
        }catch(IOException e) {
          System.err.println("Error closing listener socket " + e.getMessage());
        }
    }
}
