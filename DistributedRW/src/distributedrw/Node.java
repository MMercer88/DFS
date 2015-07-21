package distributedrw;

/**
 *
 * @author Eric Gagnon, Kevin Achenbach, Yan Liang, Mike Mercer
 * @date Friday December 12, 2014
 * @assignment CSCI 3601 Distributed File Reading and Writing
 * 
 * This class contains all the methods for sending messages between the clients,
 * file server, and receiving and sending to the controller.  It also handles
 * logging messages as they are sent for the file server and client logs. This
 * class allows for partially consistent reading and writing.
 */


import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.*;

public class Node extends Thread {
    Socket echoSocket = null;                                 //Socket to the Controller
    int id = -1;
    int num_clients = -1;
    int num_events = 100;                                       //number of events to be executed
    String hostname = null;                                     //localhost
    String timetableMsg = null;                                 //The vetor timestamp as a String
    int[] timetable = {0, 0, 0, 0, 0};                          //Most current vector timestamp for this client
    int[] sendTimetable = {0, 0, 0, 0, 0};                      //The vector timestamp that is sent with a request
    int[] tempTimetable = {0, 0, 0, 0, 0};
    HashMap<Integer,Socket> socket_list = new HashMap<>();      //All the sockets connecting the clients to each other including the file server
    Listener listener = null;               
    String[] ports = {};                              //List of ports the clients and the file server are on
    int messageWaiting = 0;                           
    int finished_counter = 0;                         //Number of finished nodes
    LinkedBlockingQueue<String> messages = null;      //List of messages waiting to be sent
    Queue<Integer> request_list = null;     //Waiting line of pending requests
    BufferedWriter client_log = null;       //This client's log file
    BufferedReader read_file = null;        //File being read from
    BufferedWriter write_file = null;       //File being written to
    BufferedWriter controller_out = null;   //Stream to controller
    BufferedReader controller_in = null;    //Stream from controller
    boolean wanting_to_write = false;
    boolean inCS = false;                   //Is this client in the CS?
    boolean wanting_to_read = false;        
    int ok_counter = 0;                     //number of oks received
    boolean running = true;
    boolean file_server_cs = false;         //Is any client in the CS at all?
    private HashMap<Integer,BufferedWriter> client_writers = new HashMap<>();

  public Node() {

  }
  
  //Generates 80% partially consistent reading and 20% writing 
 public String readOrWrite() {
   Random rand = new Random();
   int roll = rand.nextInt(10);
   if(roll <= 7) {
        return "READ";
    } else { 
        return "WRITE";
    }  
 }

 //Connection to the Controller
  public void connect() {
      try {
         echoSocket = new Socket("127.0.0.1", 10007);

      } catch(Exception e) {
          System.err.println("Error Connecting to the Controller " + e.getMessage());
      }
  }

    @Override
 public void run() {
        connect();
          try {
            controller_out = new BufferedWriter(new OutputStreamWriter((echoSocket.getOutputStream())));
            controller_in = new BufferedReader(new InputStreamReader(echoSocket.getInputStream()));
            messages = new LinkedBlockingQueue<>();
            request_list = new LinkedList<>();
            String message = null;
                
                //parse id and list of ports from controller
                message = controller_in.readLine();
                System.out.println("Got the messsage!");
                id = Integer.parseInt(message.split(" ")[0]);
                
                System.out.println("Creating listener " + id);
                //Create a listner to 
                hostname = message.split(" ")[3];
                listener = new Listener(this, Integer.parseInt(message.split(" ")[1]), hostname, messages, request_list);
                listener.start();
                System.out.println("Started thread " + id);
                
                
                String addresses = message.split(" ")[2];
                ports = addresses.split(",");     //"2000,2001,2002,2003,2004"
                num_clients = ports.length;
                
               
            
            System.out.println("PC " + id + " is READY!");
            //Wait for response from listener thread
            //Send message to controller that listener is ready
              messages.take();
              sendMessage("Listener Ready", controller_out);
              
              //wait for controller response to start connections
           if((message = controller_in.readLine()) != null) {
             System.out.println(message);
          
             
             //Connect the server sockets in the listener to the sockets in the listen and setup streams
             for(int i = 0; i < num_clients; i++) {
               if(i != id) {
                  System.out.println("ID " + id + " sets up writer for client " + i);
                  Socket temp = new Socket(hostname, Integer.parseInt(ports[i]));
                  socket_list.put(i, temp);
                  client_writers.put(i, new BufferedWriter(new OutputStreamWriter(socket_list.get(i).getOutputStream())));
               }
              }
              System.out.println("Connection to other PCs complete! Sending ready message to controller.");
              sendMessage("Setup complete", controller_out);
                
              
              //Received start simulation from Controller
              if((message = controller_in.readLine()) != null) {
                System.out.println(message);
                if(id == 0) {
                  runFileServer();
                } else {
                  runSim();
                }
              }
           }
     } catch(IOException e) {
         System.out.println("Error creating writer and/or reader " + e.getMessage());
     } catch(InterruptedException e) {
         System.out.println("Something is not right... " + e.getMessage());
     }

 }
    //Send message to a specific node
     public void sendMessage(String message, BufferedWriter destination) {
        try {
            destination.write(message);
            destination.newLine();
            destination.flush();
        } catch (IOException ex) {
            System.err.println("Error in sendMessage " + ex);
        } 
     }
     
     //Send messages to all clients (not including the file server)
     public void sendToAll(String message) {
       //J is the client it is going to to
       for(int j = 1; j < client_writers.size() + 1 ;j++) {
          if(client_writers.containsKey(j))
            sendMessage(message, client_writers.get(j));
        }
     }
     
     public void runFileServer() {
       try {
        client_log = new BufferedWriter(new FileWriter("fs_log-" + id + ".txt"));  //File Server log
        read_file = new BufferedReader(new FileReader("read_file.txt"));          
        write_file = new BufferedWriter(new FileWriter("write_file.txt",true));
      } catch (FileNotFoundException ex) {
        Logger.getLogger(Node.class.getName()).log(Level.SEVERE, null, ex);
      } catch (IOException ex) {
        Logger.getLogger(Node.class.getName()).log(Level.SEVERE, null, ex);
      }
      int line = -1;
      while(running) {
           handleFileServerMessages(); 

           if(!file_server_cs && (!request_list.isEmpty())) {  //If no one is in the CS and there is a client waiting let them write
               try {
                   line = request_list.remove();
                   client_log.append("Sending OK to " + line + "\n");
                   client_log.flush();
                   sendMessage("OK from File Server", client_writers.get(line));
                    
                   file_server_cs = true;
               }catch (IOException ex) {
                   Logger.getLogger(Node.class.getName()).log(Level.SEVERE, null, ex);
               } 
           }
      } 
      
       
     }
     
     //Handle the file servers messages waiting in the list
     public void handleFileServerMessages() {
       String nextMessage = null;
       while(messages.peek() != null) {
        try {
          nextMessage = messages.take();
          String[] this_msg = nextMessage.split(" ");
          //Get the contents from the read file and send them to the client that wants them
          if(nextMessage.startsWith("READ")) {
            //send file
            read_file.close();
            read_file = new BufferedReader(new FileReader("read_file.txt"));
            String file_contents = "";
            String temp= "";
            read_file.readLine();
            while((temp = read_file.readLine())!=null) {
                
                file_contents += (temp + "\n"); 
              
            }
            file_contents = "File Begin\n" + file_contents + "File End";
            String[] fileArray = file_contents.split("\n");
            if(fileArray.length >= 3) {   //Send File Begin, the first line, the second to the last line if it's there, and File End
                
                file_contents = fileArray[0] + ",[";
                file_contents += fileArray[1] + "],";                
                if(fileArray.length >= 4) {
                    file_contents += ("[" + fileArray[fileArray.length - 2] + "],");
                }
                file_contents += fileArray[fileArray.length - 1];
            } 
            else {
                file_contents = "File Begin ,Empty Read File,File End";     //If nothing let them know it's empty
            } 
            sendMessage(file_contents, client_writers.get(Integer.parseInt(this_msg[1])));
          }
          
          else if(nextMessage.startsWith("ADD")) {
            
            //add to write file
            String tempStore = "";
            write_file.append(this_msg[1] + "\t" + this_msg[2] + "\t" + this_msg[3] + "\n");
            write_file.flush();
            Thread.sleep(20);
            //copy write file to read file
            System.out.println("I am adding message into server: " + this_msg[1] + " " + this_msg[2] + " " + this_msg[3]);
            
            BufferedWriter writer = new BufferedWriter(new FileWriter("read_file.txt",false));
            BufferedReader reader = new BufferedReader(new FileReader("write_file.txt"));
            
            //After writing update the read file
            while(true) {
              String temp = reader.readLine();
              if(temp==null) {
                break;
              }
              tempStore += (temp + "\n");            
            }
           writer.append(tempStore);
           
           writer.flush();
           reader.close();
           writer.close();
            
           file_server_cs = false;
           
           
           //When the simulation finishes send out a disconnect message to the clients
          } else if(nextMessage.startsWith("FINISHED")) {
            finished_counter++;
            //Send disconnect message to clients to disconnect from each other
            if(finished_counter == 4) {
              client_log.append("RECEIVED FINISHES");
              client_log.newLine();
              client_log.flush();
              System.out.println("SENT DISCONNECT");
              client_log.append("SENT DISCONNECT");
              client_log.flush();
              sendToAll("DISCONNECT");
            }
          }
        } catch(InterruptedException e) {
          System.err.println("Error handling messages " + e.getMessage());
        } catch(IOException e) {
          System.err.println("Error reading from read file " + e.getMessage());
        }
     }
     }

     //This is the simulation that the clients run
     //Handle messages is called many times to ensure no client waits too long
     public void runSim() {
       System.out.println("in runSim " + id);
      try {
        
        client_log = new BufferedWriter(new FileWriter("client_log-" + id + ".txt"));  //The client's log
      } catch (FileNotFoundException ex) {
        Logger.getLogger(Node.class.getName()).log(Level.SEVERE, null, ex);
      } catch (IOException ex) {
        Logger.getLogger(Node.class.getName()).log(Level.SEVERE, null, ex);
      }
      
       int event_counter = 0;  //Number of events completed
       String line = null;
       while(event_counter < num_events) {
         line = readOrWrite();          //Get the next command ie read or write
         
           System.out.println("next command " + line + " id: " + id);
         if(line.equals("WRITE")) {
            //set send timetable and convert to string
            wanting_to_write = true;
            timetable[id]++;
            System.arraycopy( timetable, 0, sendTimetable, 0, timetable.length );
            sendToAll(line + " " + id + " " + timetableToString(sendTimetable));
            
            //wait for client oks
            while(ok_counter < num_clients - 2) {
              handleMessages();
              //Thread.sleep(50);
            }
            
            //Tell FS we want to write
            sendMessage("WRITE " + id, client_writers.get(0));
            
            //Wait for last okay from FS
            while(ok_counter < num_clients - 1) {
              handleMessages();
            }
             
            //After you get all OKs enter the CS
            criticalSection();
            while(!request_list.isEmpty()) {
              messages.add(Integer.toString(request_list.remove()));
            }
            handleMessages();
            
            ok_counter = 0;         //Reset the counter
            
           //A read request
         } else if (line.equals("READ")){
           sendMessage(line + " "+ id + " " + timetableToString(timetable),client_writers.get(0));
           
           wanting_to_read = true;
           while (wanting_to_read) {
             handleMessages();
           }
         } 
         event_counter++;         //Event complete
       }
       
       //Send message to FS to say that events are finished
       try {
         Thread.sleep(200);
         client_log.append("SENDING FINISHED TO FS");
         client_log.newLine();
         client_log.flush();
       }catch(IOException e) {
         System.err.println("Error logging Client to FS FINISHED MESSAGE " + e.getMessage());
       }catch(InterruptedException e) {
         System.err.println("Error waiting for others to catch up to finished " + e.getMessage());
       }
      
       //Send the finished message to the file server
       sendMessage("FINISHED " + id, client_writers.get(0));
       
       //Handle any message left over
       while(true) {
       try{
         Thread.sleep(200);
       }catch(InterruptedException e) {
         System.err.println("Error sleeping before end " + e.getMessage());
       }  
        handleMessages();
       }
       
     }
     
    //returns "0,1,2,3,4" for message sending
     public String timetableToString(int[] convertTable) {
        timetableMsg="";
        for(int i = 0; i<convertTable.length; i++) {
            timetableMsg += convertTable[i];
            //only add a comma if not at end of array
            if(i!=convertTable.length-1) {
                timetableMsg+= ",";
            }  
        }
        return timetableMsg;
    }
     //Takes the sent vector timestamp and makes it an integer array
    public int[] stringToTimetable(String convert) {
        String[] temp = convert.split(",");
        for(int i = 0; i<temp.length; i++) {
            tempTimetable[i] = Integer.parseInt(temp[i]);
         
        }
        
        return tempTimetable;
    }
    
    //Update the vector timestamp
    public int[] updateTimetable(int[] nodesTimetable, int[] receivedTimetable) {
        for(int i = 0; i<nodesTimetable.length; i++) {
             if(nodesTimetable[i]<receivedTimetable[i]) {
                 nodesTimetable[i]=receivedTimetable[i];
             }
        }
        return nodesTimetable;
    }
    
    //checks if nodestimetable is logically before revieved
    //node's must be <= received and one must be less than
    public boolean happensBefore(int[] nodesTimetable, int[] receivedTimetable) {
        boolean lessThan = false; 
        for(int i = 0; i<nodesTimetable.length; i++) {
             if(nodesTimetable[i]<receivedTimetable[i]) {
                lessThan = true;
             }
             else if(nodesTimetable[i] > receivedTimetable[i]) {
                 return false;
             }
        }
        return lessThan;
    }
    
    //Handings messages for the clients
    public void handleMessages() {
      String line = null;
      while(messages.peek() != null) {
        try {
          line = messages.take();
          if(line.startsWith("File Begin")) {            
            client_log.append("Reading from file");
            client_log.append(line);
            client_log.flush();
            Thread.sleep(50);   //Read the file
            wanting_to_read = false;
          } else if(line.startsWith("DISCONNECT")) {  //If received message from FS send FINISHED to Controller
            System.err.println("GETS DISCONNECT FROM FS");
            client_log.append("SENT FINISHED TO CONTROLLER");
            client_log.newLine();
            client_log.flush();
            Thread.sleep(50);  
            sendMessage("FINISHED", controller_out);
            System.err.println("SENT FINISHED");
            //Receive message from Controller and shutdown
            if((line = controller_in.readLine()) != null) {
              client_log.append("RECEIVED CONTROLLER FINAL MESSAGE");
              client_log.newLine();
              client_log.flush();
              System.out.println(line);
              Thread.sleep(200);
              System.exit(0);
            }
          } else {
            //For sending OKs to other clients
            client_log.append("Sending OK to " + line);
            sendMessage("OK from" + id, client_writers.get(Integer.parseInt(line)));
          }
          client_log.append("\n");
          client_log.flush();
        } catch(InterruptedException e) {
          System.err.println("Error handling messages " + e.getMessage());
        }catch(IOException e) {
          System.err.println("Error handling messages " + e.getMessage());
        }
      }
    }
    
    public void criticalSection() {
      inCS = true;              //Entered CS
      Random rand = new Random();
      System.out.println("Sending write message");   //Write message send to the file server to be added
      String messagewrite = "ADD "+id + " " + timetableToString(timetable) + " " + rand.nextInt(100);
      sendMessage(messagewrite,client_writers.get(0));
      wanting_to_write = false;       //Cannot do both read and write at the same time
      inCS = false;             //Exit CS
    }
     
}
