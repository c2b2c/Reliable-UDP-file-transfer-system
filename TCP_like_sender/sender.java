
import java.io.*;
import java.net.*;
import java.util.Vector;  
import java.util.Calendar;
import java.util.GregorianCalendar;

public class sender {

	static int windowSize;
	static int ACKport;
	static int headerLength=20;
	
	public static void main(String args[]) throws Exception {
        // get information from input command line
        
        final String fileName = args[0];
        final String remote_IP=args[1];
        final int port = Integer.parseInt(args[2]);
        ACKport = Integer.parseInt(args[3]);
        final String LogfileName = args[4];
        windowSize = Integer.parseInt(args[5]);
        
        sendProcess(remote_IP, port, fileName, LogfileName);
    }
	
	public static class Timer {
		//all the timer is in ms
        int pastTime;
        int startTime;
        int currentTime;
        int timeoutTime;

        public Timer(int timeout) {
            // get time from GregorianCalendar
            Calendar cal = new GregorianCalendar();
            int sec = cal.get(Calendar.SECOND);  
            int min = cal.get(Calendar.MINUTE);           
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            int milliSec = cal.get(Calendar.MILLISECOND);
            startTime = milliSec + (sec*1000) + (min *60000) + (hour*3600000);
            timeoutTime = (timeout);
        }

        int timeUsed() {
            Calendar cal = new GregorianCalendar();
            int secUsed = cal.get(Calendar.SECOND);
            int minUsed = cal.get(Calendar.MINUTE);
            int hourUsed = cal.get(Calendar.HOUR_OF_DAY);
            int msecUsed = cal.get(Calendar.MILLISECOND);
            currentTime = msecUsed + (secUsed*1000) + (minUsed *60000) + (hourUsed * 3600000);
            pastTime = currentTime - startTime;
            return pastTime;
        }

        boolean timeout() {
            timeUsed();
            if (pastTime >= timeoutTime) {
                return true;
            } else {
                return false;
            }
        }
    }

    public static void sendProcess(String remote_IP, int port, String fileName, String LogfileName) throws IOException {
        System.out.println("Sending the file");

        // create the socket 
        DatagramSocket socket = new DatagramSocket();
        InetAddress address = InetAddress.getByName(remote_IP);
        File file = new File(fileName);	
        
        BufferedWriter LogOutput = new BufferedWriter(new FileWriter(LogfileName));

        // create bytearray for sending data
        InputStream readFile = new FileInputStream(file);
        byte[] fileByteArray = new byte[(int)file.length()];
        readFile.read(fileByteArray);

        // timer for calculating throughput
        Timer timer = new Timer(0);

        // sequence number and that of last received
        int sequenceNumber = 0;
        boolean LastReceived = false;

        // parameters about acknowledgements
        int ackSequenceNumber = 0;
        int lastAckedSequenceNumber = 0;
        boolean lastAcknowledgedFlag = false;

        // retransmission counter
        int retransCounter = 0;

        // message that is sent
        Vector <byte[]> sentMessageList = new Vector <byte[]>();

        // For as each message we will create
        for (int i=0; i < fileByteArray.length; i = i+1024-headerLength ) {

            // Increment sequence number
            sequenceNumber += 1;

            // Create new byte array for message
            byte[] data = new byte[1024];
            byte[] header = new byte[headerLength];

            // sequence number added in header
            data[0] = (byte)(sequenceNumber >> 8);
            data[1] = (byte)(sequenceNumber);

            // if it is the last packet,stored in the third bit
            if ((i+1024-headerLength) >= fileByteArray.length) {
                LastReceived = true;
                data[2] = (byte)(1);
            } else { 
                LastReceived = false;
                data[2] = (byte)(0);
            }

            // form the message
            if (!LastReceived) {
                for (int j=0; j != 1024-headerLength; j++) {
                    data[j+headerLength] = fileByteArray[i+j];
                }
            }
            else if (LastReceived) { 
                for (int j=0;  j < (fileByteArray.length - i); j++) {
                    data[j+headerLength] = fileByteArray[i+j];
                }
            }
            
            for (int j=0; j < headerLength ; j++) {
                header[j] = data[j];
            }

            // send the packet
            DatagramPacket sendPacket = new DatagramPacket(data, data.length, address, port);

            // sent list showed
            sentMessageList.add(data);

            while (true) {
                // while the next sequence number is outside  window
                if ((sequenceNumber - windowSize) > lastAckedSequenceNumber) {

                    boolean ackRecievedCorrect = false;
                    boolean ackPacketReceived = false;

                    while (!ackRecievedCorrect) {
                        // Check for an ack
                        byte[] ack = new byte[2];
                        DatagramPacket ackpack = new DatagramPacket(ack, ack.length);

                        try {
                            socket.setSoTimeout(50);
                            socket.receive(ackpack);
                            ackSequenceNumber = ((ack[0] & 0xff) << 8) + (ack[1] & 0xff);
                            ackPacketReceived = true;
                        } catch (SocketTimeoutException e) {
                            ackPacketReceived = false;
                            //System.out.println("Socket timed out while waiting for an acknowledgement");
                            //e.printStackTrace();
                        }

                        if (ackPacketReceived) {
                            if (ackSequenceNumber >= (lastAckedSequenceNumber + 1)) {
                                lastAckedSequenceNumber = ackSequenceNumber;
                            }
                            ackRecievedCorrect = true;
                            System.out.println("Ack recieved: Sequence Number = " + ackSequenceNumber);
                            break; 	// Break if there is an ack so the next packet can be sent
                        } else { // Resend the packet
                            System.out.println("Resending: Sequence Number = " + sequenceNumber);
                            // Resend the packet following the last acknowledged packet and all following that (cumulative acknowledgement)
                            for (int y=0; y != (sequenceNumber - lastAckedSequenceNumber); y++) {
                                byte[] resendMessage = new byte[1024];
                                resendMessage = sentMessageList.get(y + lastAckedSequenceNumber);

                                DatagramPacket resendPacket = new DatagramPacket(resendMessage, resendMessage.length, address, port);
                                socket.send(resendPacket);
                                retransCounter += 1;
                            }
                        }
                    }
                } else { // Else pipeline is not full, break so we can send the message
                    break;
                }
            }

            // send message
            socket.send(sendPacket);
            LogOutput.write("Sent: Sequence number = " + sequenceNumber + ", header is " + header + ".\r\n");
            System.out.println("Sent: Sequence number = " + sequenceNumber);
            
            // Check acknowledgements
            while (true) {
                boolean ackPacketReceived = false;
                byte[] ack = new byte[2];
                DatagramPacket ackpack = new DatagramPacket(ack, ack.length);

                try {
                    socket.setSoTimeout(10);
                    socket.receive(ackpack);
                    ackSequenceNumber = ((ack[0] & 0xff) << 8) + (ack[1] & 0xff);
                    ackPacketReceived = true;
                } catch (SocketTimeoutException e) {
                    ackPacketReceived = false;
                    break;
                }

                // Note acknowledgements and move window 
                if (ackPacketReceived) {
                    if (ackSequenceNumber >= (lastAckedSequenceNumber + 1)) {
                        lastAckedSequenceNumber = ackSequenceNumber;
                        //System.out.println("Ack recieved: Sequence number = " + ackSequenceNumber);
                    }
                }
            }
        }

        // check whether ack
        while (!lastAcknowledgedFlag) {

            boolean ackRecievedCorrect = false;
            boolean ackPacketReceived = false;

            while (!ackRecievedCorrect) {
                // Check for an acknowledgement
                byte[] ack = new byte[2];
                DatagramPacket ackpack = new DatagramPacket(ack, ack.length);

                try {
                    socket.setSoTimeout(50);
                    socket.receive(ackpack);
                    ackSequenceNumber = ((ack[0] & 0xff) << 8) + (ack[1] & 0xff);
                    ackPacketReceived = true;
                } catch (SocketTimeoutException e) {
                    System.out.println("Ack timeout, though sent, receiver may not receive file!!!");                 
                    ackPacketReceived = false;
                }

                // last packet received
                if (LastReceived) {
                    lastAcknowledgedFlag = true;
                    break;
                }	
                // if received, break and try for next packet
                    if (ackPacketReceived) {		
                    //System.out.println("Ack recieved: Sequence number = " + ackSequenceNumber);
                    if (ackSequenceNumber >= (lastAckedSequenceNumber + 1)) {
                        lastAckedSequenceNumber = ackSequenceNumber;
                    }
                    ackRecievedCorrect = true;
                    break; 
                } else { 
                    for (int j=0; j != (sequenceNumber-lastAckedSequenceNumber); j++) {
                        byte[] resendMessage = new byte[1024];
                        resendMessage = sentMessageList.get(j + lastAckedSequenceNumber);
                        DatagramPacket resendPacket = new DatagramPacket(resendMessage, resendMessage.length, address, port);
                        socket.send(resendPacket);
                        System.out.println("Resending: Sequence Number = " + lastAckedSequenceNumber);

                        retransCounter += 1;
                    }
                }
            }
        }

        socket.close();
        System.out.println("File " + fileName + " has been sent");

        // parameters showed in command line 
        int bytesSent = (fileByteArray.length) ;
        int transferTime = timer.timeUsed() / 1000;
        int totalSegments = retransCounter + sequenceNumber;
        double throughput = (double) bytesSent / transferTime;
        System.out.println("Total bytes sent = " + bytesSent + " Bytes");
        System.out.println("Total segments sent = " + totalSegments );
        System.out.println("Segments retransmitted = " + retransCounter);	
        System.out.println("Transfer time: " + transferTime + " seconds");
        System.out.println("Throughput: " + throughput + " Bps");
        
        LogOutput.flush();
        LogOutput.close();
    }
    
}