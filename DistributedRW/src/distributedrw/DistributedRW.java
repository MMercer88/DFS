package distributedrw;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
/**
 *
 * @author Eric Gagnon, Kevin Achenbach, Yan Liang, Mike Mercer
 * @date Friday December 12, 2014
 * @assignment CSCI 3601 Distributed File Reading and Writing
 * 
 * THe main for the creating and executing the clients and file server.
 */
public class DistributedRW {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        cleanLogFiles(5);           //Empty log files before simulation
        
        for(int i = 0; i < 5; i++) {
            Node client = new Node();
            client.start();
        }
        
    }
    
    private static void cleanLogFiles(int number_of_clients){
        try{
                BufferedWriter log = new BufferedWriter(new FileWriter("fs_log-0.txt"));
                log.write("");
                log.close();

                log = new BufferedWriter(new FileWriter("read_file.txt"));
                log.write("ID   VectorTimestamp     RandomNumber (Client 0 is the File Server, so index 0 of the Vector Timestamp will not increase, VT with same values indicates concurrency)\n");
                log.close();

                log = new BufferedWriter(new FileWriter("write_file.txt"));
                log.write("ID   VectorTimestamp     RandomNumber (Client 0 is the File Server, so index 0 of the Vector Timestamp will not increase, VT with same values indicates concurrency)\n");
                log.close();

                log = new BufferedWriter(new FileWriter("fs_log-0.txt"));
                log.write("");
                log.close();
                
                for(int i = 1; i < number_of_clients; i++){
                    log = new BufferedWriter(new FileWriter("client_log-" + i +".txt"));
                    log.write("");
                    log.close();
                }    
        } catch(IOException e){
            System.err.println("Error reading from read file " + e.getMessage());
        }        
    }
}
