# Reliable-UDP-file-transfer-system
Reliable UDP file transfer system

a. Details on development environment
I use eclipse and java 1.6 to develop my code.(JavaSE-1.6)

b. Instructions on how to run your code
The receiver will be compiled by javac receiver.java, it will be involked as 
receiver <filename> <listening_port> <sender_IP> <sender_port> <log_filename>

The sender will be compiled by javac sender.java, it will be involked as 
sender <filename> <remote_IP> <remote_port> <ack_port_num> <log_filename> <window_size>

c. Sample commands to invoke your code
1. Invoke receiver so that it is ready to receive file:
file.txt 127.0.0.1 20000 20001 logfile.txt 1152

2. Then we can invoke sender so that it can start to send file
java sender file.txt 127.0.0.1 20000 20001 logfile.txt 1152 

The information of sending procedure will be displayed in command line, and some specific information will be entered in the logfile.txt of both ends.

If some exception occurs, there would be information showed in command line such as ACK timeout, and resending packet.


d. Specific function
I did not do some really big extra function, there’s only some little work in extra I did.
1. The sequence number of the packets that are sent and received will be listed on the command line;
2. Together with the header, log will also include the corresponding sequence number of the packet.
3. I start a timer that will record the transportation time, in that case together with my record of the Bytes that is transmitted, I can calculate the average throughput and listed in the command line of sender side.
4. According to the example in Assignment example, I record total segments sent and segments that are retransmitted.

