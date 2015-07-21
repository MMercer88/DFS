package distributedrw;

/**
 *
 * @author Eric Gagnon, Kevin Achenbach, Yan Liang, Mike Mercer
 * @date Friday December 12, 2014
 * @assignment CSCI 3601 Distributed File Reading and Writing
 * 
 * The controller is used for starting and ending simulation.
 */
import java.net.*;
import java.io.*;
import java.util.*;
public class Controller extends Thread {
   
    public Controller(Socket accept) {
     } 
    public static void main(String[] args) {
        ServerSocket socket = null;
        int num_clients = 5;
        ArrayList<Socket> client_list = new ArrayList();
        ArrayList<BufferedWriter> writers = new ArrayList<>();
        ArrayList<BufferedReader> readers = new ArrayList<>();
        int client_counter = 0;
        String message;
        boolean connected = false;


        try {
            socket = new ServerSocket(10007);
        } catch(IOException e) {
            System.err.println("Error establishing controller port" + e.getMessage());
        }
         System.out.println ("Waiting for connection....."); 
         
       while(client_counter < num_clients) {
            try {
                Socket clientSocket = socket.accept();
                client_list.add(clientSocket);
                System.out.println("Client " + clientSocket.getPort() + " to Controller " + socket.getLocalPort());
                writers.add(new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream())));
                readers.add(new BufferedReader(new InputStreamReader(clientSocket.getInputStream())));
                client_counter++;
                
            } catch(IOException e) {
                 System.err.println("Trouble setting up in/out streams " + e.getMessage());
             } catch(Exception e) {
                System.err.println("Error connecting to clients " + e.getMessage());
            }
        }
            connected = true;
            System.out.println("Connection Successful");
            
            String addresses = "2000,2001,2002,2003,2004";
            for(int i = 0; i < num_clients; i++) {

                    message = i + " " + addresses.split(",")[i] + " " + addresses + " " + client_list.get(i).getInetAddress().getHostAddress() + "\r";
                    sendMessage(message, writers.get(i));

            }
            
            System.out.println("Waiting for listeners to be set up...");
            
            int received_ready = 0;
            String ready_msg = null;
            //Wait for ready listener message
            while(received_ready < num_clients) {
              try {
                ready_msg = readers.get(received_ready).readLine();
                System.out.println(ready_msg);
                received_ready++;
              }catch(IOException e) {
                System.err.println("Error receiving listener ready messages " + e.getMessage());
              } 
            }
            //send out start client to client connection
            for(int j = 0; j < num_clients; j++) {
              sendMessage("Okay establish connection", writers.get(j));
            }
            
            System.out.println("Waiting for connections to be set up...");
            //Wait for client to client connections are established
            received_ready = 0;
            ready_msg = null; 
            while(received_ready < num_clients) {
              try {
                 ready_msg = readers.get(received_ready).readLine();
                 System.out.println(ready_msg);
                 received_ready++;
              }catch(IOException e) {
                System.out.println("Error receiving PC ready messages " + e.getMessage() );
              }
            }
            
            //Send Start Sim
            for(int k = 0; k < num_clients; k++) {
              sendMessage("Start Sim", writers.get(k));
            }
            
            System.out.println("Waiting for the end...");
            //Wait for end simulation
            received_ready = 1;
            ready_msg = null;
            while(received_ready < num_clients) {
              try {
                ready_msg = readers.get(received_ready).readLine();
                System.out.println(ready_msg);
                received_ready++;
              }catch(IOException e) {
                System.out.println("Error receiving finished" + e.getMessage());
              }
            }
            
            //Send End
            for(int l = 1; l < num_clients; l++) {
              sendMessage("End Sim", writers.get(l));
            }
            
            
            
    }
    static void sendMessage(String message, BufferedWriter destination) {
        try {
            destination.write(message);
            destination.newLine();
            System.out.println("sending the message " + message);
            destination.flush();
        } catch (IOException ex) {
            System.err.println("Error in sendMessage " + ex);
        }
        
    }
    
}
