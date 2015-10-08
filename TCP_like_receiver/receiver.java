
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class receiver {

    static int ACKport;
    static String sender_IP;
    static int headerLength=20;
	
	public static void main(String args[]) throws Exception {
        System.out.println("Receiver is ready to receive file, wait for the sender.");

        // got information from input of command line
        final String fileName = args[0];
        final int port = Integer.parseInt(args[1]);
        sender_IP=args[2];
        ACKport = Integer.parseInt(args[3]);
        final String LogfileName = args[4];
        receiveProcess(port, fileName , LogfileName);
    }

    //function for sending acknowledgements
	public static void sendAck(int LastReceived, DatagramSocket socket, InetAddress address, int port) throws IOException {
        byte[] ackPacket = new byte[2];
        ackPacket[0] = (byte)(LastReceived >> 8);
        ackPacket[1] = (byte)(LastReceived);
        DatagramPacket ACKs = new  DatagramPacket(ackPacket, ackPacket.length, address, port);
        socket.send(ACKs);
    }

	
    public static void receiveProcess(int port, String fileName , String LogfileName) throws IOException {
        // Create UDP socket, receive the file.
    	InetAddress address;
    	DatagramSocket socket = new DatagramSocket(port);
        
        File file = new File(fileName);
        FileOutputStream output = new FileOutputStream(file);
        
        //File file1 = new File(LogfileName);
        //FileOutputStream output1 = new FileOutputStream(LogfileName);
        //FileWriter fileWriter = new FileWriter(LogfileName,true);
        BufferedWriter LogOutput = new BufferedWriter(new FileWriter(LogfileName));  
        DataOutputStream out =  new DataOutputStream(new BufferedOutputStream(new FileOutputStream(LogfileName)));

        
        boolean EngFlag = false;  // Show whether we receive the last message
		
        int sequenceNumber = 0;  // assign sequence number to every packet
        int LastReceived = 0;  //last received sequence number

        // receiving process
        while (!EngFlag) {
            // divide data into message and headers
            byte[] data = new byte[1024];
            byte[] message = new byte[1024-headerLength];
            byte[] header = new byte[headerLength];

            // get data from packet
            DatagramPacket receivedPacket = new DatagramPacket(data, data.length);
            socket.setSoTimeout(0);
            socket.receive(receivedPacket);
            data = receivedPacket.getData();

            // get address information of sender
            //address = receivedPacket.getAddress();
            address = InetAddress.getByName(sender_IP);
            port = ACKport;

            // sequence number retrieve
            sequenceNumber = ((data[0] & 0xff) << 8) + (data[1] & 0xff);

            // get the flag of last message
            if ((data[2] & 0xff) == 1) {
                EngFlag = true;
            } else {
                EngFlag = false;
            }

            //find out whether the packet is the next of last packet received
            if (sequenceNumber == (LastReceived + 1)) {

                LastReceived = sequenceNumber;

                // Retrieve message from data
                for (int i=headerLength; i < 1024 ; i++) {
                    message[i-headerLength] = data[i];
                }
                
                for (int i=0; i < headerLength ; i++) {
                    header[i] = data[i];
                }
                
                String headerString = header.toString(); 
                
               

                // output the message to the file
                output.write(message);            
                
                // enter in the log
                LogOutput.write("Received: Sequence number = " + sequenceNumber + ", header is " + headerString + ".\r\n");
                //output1.write(header);  
                System.out.println("Received: Sequence number = " + sequenceNumber);
                //System.out.println("Received: Sequence number = " + sequenceNumber + ", header is " + header + ".\r\n");
                
                // Send acknowledgements
                sendAck(LastReceived, socket, address, port);

                if (EngFlag) {
                    output.close();
                } 
            } else {
                // for duplicate
                if (sequenceNumber < (LastReceived + 1)) {
                    sendAck(sequenceNumber, socket, address, port);
                } else {
                    // resend the packet
                    sendAck(LastReceived, socket, address, port);
                }
            }
        }
        
        socket.close();
        LogOutput.flush();
        LogOutput.close();
        System.out.println("Delivery completed successfully! "+fileName + " has been received.");
	}
    
    /*public static int byteArrayToInt(byte[] b) {
        final ByteBuffer bb = ByteBuffer.wrap(b);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        return bb.getInt();
    }*/
}

