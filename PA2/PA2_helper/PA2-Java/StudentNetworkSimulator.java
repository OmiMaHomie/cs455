import java.util.*;
import java.io.*;

public class StudentNetworkSimulator extends NetworkSimulator
{
    //
    // FIELDS
    //

    // DONT TOUCH
    public static final int FirstSeqNo = 0;
    private int WindowSize;
    private double RxmtInterval;
    private int LimitSeqNo; 

    // Entity A
    private int base;   // Oldest unACKed sequence number (Logical)
    private int nextseqnum; // Next sequence number to send (Logical)
    private Packet[] snd_buf; // Sender window buffer
    private ArrayList<Message> pendingMessages; // Queue for messages waiting for window
    
    // Entity B
    private int expectedseqnum; // Next packet expected in order (Logical)
    private Packet[] rcv_buf; // Receiver buffer, for out-of-order packets
    
    // Debugging
    private int numOriginalPackets;
    private int numRetransmissions;
    private int numDeliveredToLayer5;
    private int numACKsSent;
    private int numCorruptedPackets;
    private int totalPacketsSent;

    //
    // CONSTRUCTOR(S)
    //

    // This is the constructor.  Don't touch!
    public StudentNetworkSimulator(int numMessages,
                                   double loss,
                                   double corrupt,
                                   double avgDelay,
                                   int trace,
                                   int seed,
                                   int winsize,
                                   double delay)
    {
        super(numMessages, loss, corrupt, avgDelay, trace, seed);
        WindowSize = winsize;
        LimitSeqNo = winsize*2; // Set to SR
        RxmtInterval = delay;

        // Custom init. (for the debugging)
        numOriginalPackets = 0;
        numRetransmissions = 0;
        numDeliveredToLayer5 = 0;
        numACKsSent = 0;
        numCorruptedPackets = 0;
        totalPacketsSent = 0;
    }

    //
    // METHODS
    //

    // Checksum calc.
    private int calculateChecksum(Packet packet) {
        int checksum = 0;
        checksum += packet.getSeqnum();
        checksum += packet.getAcknum();
        String payload = packet.getPayload();

        // This packet got a payload?
        // YES
        if (payload != null) {
            for (int i = 0; i < payload.length(); i++) {
                checksum += (int) payload.charAt(i);
            }
        }

        // NO --> do nothing

        return checksum;
    }
    
    // Corruption check (just checks sent checksum to packet checksum)
    private boolean isCorrupted(Packet packet) {
        return calculateChecksum(packet) != packet.getChecksum();
    }


    // A wants to send a msg (layer 5)
    protected void aOutput(Message message)
    {
        // Add msg to queue, and just send AMAP (as much as possible)
        pendingMessages.add(message);
        sendPackets();
    }

    // Helper for aOutput: Handles logic when the window isn't full (& there's pending msgs)
    private void sendPackets() {
        while (!pendingMessages.isEmpty() && (nextseqnum < base + WindowSize)) {
            Message msg = pendingMessages.remove(0);
            
            // Packet has seq# = MOD limitSeq#
            int seq = nextseqnum % LimitSeqNo;
            Packet pkt = new Packet(seq, -1, 0, msg.getData());
            pkt.setChecksum(calculateChecksum(pkt));
            
            /* Then do the following:
                - Store in snd win buf (MOD WindowSize as index)
                - Send to layer3
                - Debug update
                - Start the timer:
                    - ONLY if the current packet is the FIRST packet being sent (as in, it's base).
                    - If we're sending a prev. packet, its timer would alr be running.
                - Update next seq# num
            */
            snd_buf[nextseqnum % WindowSize] = pkt;
            
            toLayer3(A, pkt);
            
            numOriginalPackets++;
            totalPacketsSent++;
            
            if (nextseqnum == base) {
                startTimer(A, RxmtInterval);
            }
            
            nextseqnum++;
        }
    }
    
    // A gets a packet (its an ACK, based on assignment details)
    protected void aInput(Packet packet)
    {
        if (isCorrupted(packet)) {
            numCorruptedPackets++;
            return;
        }

        int ack = packet.getAcknum();

        /* Since this is SR, process ACK as a Cumulative ACK:
            - Map the ACK (mod value) to the logicalseq# RELATIVE to base
            - Check if ACK is within current window range
                - If so, it's a new ACK --> ACK everything up to logicalACKed
                - Else, it's prolly a dup --> resend missing packet (check if the dup is actually for base - 1 tho [missing base])
        */
        int baseMod = base % LimitSeqNo;
        int diff = (ack - baseMod + LimitSeqNo) % LimitSeqNo;
        
        if (diff < WindowSize) {
            int logicalAcked = base + diff;
            
            if (logicalAcked >= base && logicalAcked < nextseqnum) {
                base = logicalAcked + 1;    // Move base forward
                
                // Is Window empty?
                // YES --> stop timer
                if (base == nextseqnum) {
                    stopTimer(A);
                } 
                // NO --> Stop old timer, then start new one
                else {
                    stopTimer(A);
                    startTimer(A, RxmtInterval);
                }
                
                sendPackets();  // Try to send more pending messages now that window has moved
            }
        } 
        else {
            int expectedDup = (base - 1 + LimitSeqNo) % LimitSeqNo;
            if (ack == expectedDup) {
                // I dunno if I can just call this. Just gonna copy paste the code to hard-wire it.
                // aTimerInterrupt();
                Packet p = snd_buf[base % WindowSize];

                if (p != null) {
                    toLayer3(A, p);
                    numRetransmissions++;
                    totalPacketsSent++;

                    // Restart timer
                    stopTimer(A);
                    startTimer(A, RxmtInterval);
                }
            }
        }
    }
    
    // A's packet expired
    // Just rtransmit the oldest unACKed packet [base]
    protected void aTimerInterrupt()
    {
        Packet p = snd_buf[base % WindowSize];

        if (p != null) {
            toLayer3(A, p);
            numRetransmissions++;
            totalPacketsSent++;

            // Restart timer
            startTimer(A, RxmtInterval);
        }
    }
    
    // A (sender) init
    protected void aInit()
    {
        base = 0;
        nextseqnum = 0;
        snd_buf = new Packet[WindowSize];
        pendingMessages = new ArrayList<Message>();
    }
    
    // B gets a packet
    protected void bInput(Packet packet)
    {
        // Packet corruption check
        if (isCorrupted(packet)) {
            numCorruptedPackets++;
            return;
        }

        int seq = packet.getSeqnum();

        // Check if seq is the expected one. If so, map it to logical RELATIVE to expectedseqnum
        int expectedMod = expectedseqnum % LimitSeqNo;
        int diff = (seq - expectedMod + LimitSeqNo) % LimitSeqNo;

        // Are we getting diff [the expected packet]?
        // YES
        if (diff == 0) {
            toLayer5(packet.getPayload());
            numDeliveredToLayer5++;
            expectedseqnum++;
            
            // Check the buffer for consecutive packets to deliver
            while (true) {
                int idx = expectedseqnum % WindowSize;
                if (rcv_buf[idx] != null) {
                    if (rcv_buf[idx].getSeqnum() == expectedseqnum % LimitSeqNo) { // seq# MOD limitSeq# --> next expected buffered packet check
                        toLayer5(rcv_buf[idx].getPayload());
                        numDeliveredToLayer5++;
                        rcv_buf[idx] = null; // Clear slot
                        expectedseqnum++;
                    } 
                    else {
                        break; // Gap found
                    }
                } 
                else {
                    break;
                }
            }
            
            // Send Cumulative ACK for the last delivered packet
            sendACK(expectedseqnum - 1);
        } 
        // NO
        // Check if packet is in window, but out-of-order
        else if (diff < WindowSize) {
            // Store in rcv buffer
            rcv_buf[seq % WindowSize] = packet; // This is safe, since seq is within window
            
            // Send Cumulative ACK for last in-order packet
            sendACK(expectedseqnum - 1);
        }
        // This is just some DUP/old packet
        else {
            sendACK(expectedseqnum - 1);
        }
    }

    // Helper method for bInput(): Sends ACK
    private void sendACK(int ackNum) {
        /* Ok so this method does the following:
            - since ACK# is logical, set the newACK# %= limitSeq#
            - Check for neg. MOD (if ACK# = -1)
            - Then SEND that ACK to layer3
        */
        int ackToSend = ackNum % LimitSeqNo;
        if (ackToSend < 0) ackToSend += LimitSeqNo;
        
        Packet ackPacket = new Packet(-1, ackToSend, 0, "");
        ackPacket.setChecksum(calculateChecksum(ackPacket));
        toLayer3(B, ackPacket);
        numACKsSent++;
    }

    // B (reciever) init
    protected void bInit()
    {
        expectedseqnum = 0;
        rcv_buf = new Packet[WindowSize];
    }

    // Debug/Stats
    protected void Simulation_done()
    {
    	// TO PRINT THE STATISTICS, FILL IN THE DETAILS BY PUTTING VARIBALE NAMES. DO NOT CHANGE THE FORMAT OF PRINTED OUTPUT
        System.out.println("\n\n===============STATISTICS=======================");
        System.out.println("Number of original packets transmitted by A: " + numOriginalPackets);
        System.out.println("Number of retransmissions by A: " + numRetransmissions);
        System.out.println("Number of data packets delivered to layer 5 at B: " + numDeliveredToLayer5);
        System.out.println("Number of ACK packets sent by B: " + numACKsSent);
        System.out.println("Number of corrupted packets: " + numCorruptedPackets);
        
        int totalPackets = totalPacketsSent + numACKsSent;
        double lostRatio = 0.0;
        if (totalPackets > 0) {
            lostRatio = (double)(numRetransmissions) / totalPackets; 
        }
        
        double corruptRatio = totalPackets > 0 ? (double)numCorruptedPackets / totalPackets : 0.0;
        
        System.out.println("Ratio of lost packets: " + lostRatio);
        System.out.println("Ratio of corrupted packets: " + corruptRatio);
        System.out.println("==================================================");

    	// PRINT YOUR OWN STATISTIC HERE TO CHECK THE CORRECTNESS OF YOUR PROGRAM
    	System.out.println("\nEXTRA:");
        System.out.println("Total packets sent (original + retransmissions): " + totalPacketsSent);
        System.out.println("Window Size: " + WindowSize);
        System.out.println("Retransmission Timeout: " + RxmtInterval);
    }	
}