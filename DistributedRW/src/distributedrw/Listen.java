package distributedrw;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Queue;

/**
 *
 * @author Eric Gagnon, Kevin Achenbach, Yan Liang, Mike Mercer
 * @date Friday December 12, 2014
 * @assignment CSCI 3601 Distributed File Reading and Writing
 * 
 * The listen waits for messages reads them in and adds them to the message list
 * to be handled in the file server and client respective methods that handle
 * messages.
 */
public class Listen extends Thread {
    BufferedReader reader = null;       //This nodes message receiver
    Socket socket = null;         
    Node current = null;
    String message;
    boolean running = true;
    LinkedBlockingQueue<String> messages;
    Queue<Integer> request_list;
    
    public Listen(Node current, Socket socket, LinkedBlockingQueue messages, Queue request_list, boolean running) throws IOException {
        this.current = current;
        this.socket=socket;
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));    //Create a read stream for this node
        this.messages = messages; 
        this.request_list = request_list;
        this.running = running;
    }
    
    @Override
    public void run() {
        while(running) {
            try {
                    
              message = reader.readLine();  //Get the next message
                    
            } catch (IOException ex) {
                Logger.getLogger(Listen.class.getName()).log(Level.SEVERE, null, ex);
            }
            String[] info = {};
            if(message != null) {   //Ensure there is a message
              info = message.split(" ");
            }
            
            //The file server
            if(current.id == 0) {
              //Add write requests to the waiting list to ensure only one goes at a time
              if(message.startsWith("WRITE")) {
                  System.out.println("FILESERVER GOTS THE REQUERST " + message);
                  writeToLog("REQUEST: " + message);
                request_list.add(Integer.parseInt(info[1]));
              }
              //If read or add, put the message in the message list so OKs can be sent out
              else if(message.startsWith("READ") || message.startsWith("ADD")) {
                writeToLog(message);
                messages.add(message);
                
                //If finished at it to the  message list so shutdown can begin
              } else if(message.startsWith("FINISHED")) {
                writeToLog(message);
                messages.add(message);
              }
            }else{
              
              if(message.startsWith("WRITE")){
                current.updateTimetable(current.timetable, current.stringToTimetable(info[2]));
                writeToLog("REQUEST: " + message);
                if(current.inCS){           //If write request and in the CS add to the waiting line
                  request_list.add(Integer.parseInt(info[1]));
                } else if(current.wanting_to_write) {       //If you want to write and you come before the other client put the other client in the waiting list
                  if(current.happensBefore(current.sendTimetable, current.stringToTimetable(info[2]))){
                    request_list.add(Integer.parseInt(info[1]));
                  } else {  //Else put in message list to send out OK
                    messages.add(info[1]);
                  }
                } else {      //Else add to message list to send out OK
                messages.add(info[1]);
              }
              }else if(message.startsWith("OK")){   //If an OK is received add 1 to count
                writeToLog("Received: " + message);
                current.ok_counter++;
              }else if(message.startsWith("File Begin")){     //IF you receieve a file from a read request
                messages.add(message);
              }else if(message.startsWith("DISCONNECT")) {        //Received Disconnect from FS now send message to Controller
                writeToLog("Received: FROM FS OK TO " + message);
                messages.add(message);
              }
            
            }
        }
        //Close upon exiting
        try {
          reader.close();
        } catch(IOException e) {
          System.err.println("Error closing listen readers " + e.getMessage());
        }
    }
    
    //Writing information to respective logs
    void writeToLog(String message) {
        try {
            current.client_log.write(message + "\n");
            current.client_log.flush();
        } catch (IOException ex) {
            Logger.getLogger(Listen.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
