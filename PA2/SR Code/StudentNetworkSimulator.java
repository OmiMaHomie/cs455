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
    private int base;                  // Oldest unACKed seq# (Logical)
    private int nextseqnum;            // Next seq# to send (Logical)
    private Packet[] snd_buf;          // Sender window buffer
    private ArrayList<Message> pendingMessages; // Queue for msgs waiting for window
    private int dupAckCount = 0;       // Counter for dup ACK
    
    // Entity B
    private int expectedseqnum;        // Next packet expected in order (Logical)
    private Packet[] rcv_buf;          // Receiver buffer for out-of-order packets
    
    // Statistical (Debugging)
    private int numOriginalPackets;
    private int numRetransmissions;
    private int numDeliveredToLayer5;
    private int numACKsSent;
    private int numCorruptedPackets;
    private int totalPacketsSent;

    // RTT, Comm. Time (Debugging)
    private double[] sendTime;         // Time when packet was first sent
    private boolean[] hasRetransmitted;// Whether packet has been retransmitted
    private double totalRTT = 0.0;
    private int rttCount = 0;
    private double totalCommTime = 0.0;
    private int commTimeCount = 0;


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

        // Init. the debug vars
        numOriginalPackets = 0;
        numRetransmissions = 0;
        numDeliveredToLayer5 = 0;
        numACKsSent = 0;
        numCorruptedPackets = 0;
        totalPacketsSent = 0;
        sendTime = new double[LimitSeqNo];
        hasRetransmitted = new boolean[LimitSeqNo];
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
                - Update debug
                    - Stats
                    - Tracing (RTT)
                - Start the timer:
                    - ONLY if the current packet is the FIRST packet being sent (as in, it's base).
                    - If we're sending a prev. packet, its timer would alr be running.
                - Update next seq# num
            */
            snd_buf[nextseqnum % WindowSize] = pkt;
            
            toLayer3(A, pkt);
            
            numOriginalPackets++;
            totalPacketsSent++;
            sendTime[seq] = getTime();
            hasRetransmitted[seq] = false; // new pkt
            
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
                int oldBase = base;
                base = logicalAcked + 1;    // Move base forward

                // Calc. RTT & Comm. time
                for (int i = oldBase; i <= logicalAcked; i++) {
                    int idx = i % LimitSeqNo;
                    if (sendTime[idx] != 0.0) { // If there's a recorded send time
                        double ackTime = getTime();
                        double commTime = ackTime - sendTime[idx];
                        
                        totalCommTime += commTime;
                        commTimeCount++;
                        
                        if (!hasRetransmitted[idx]) {
                            totalRTT += commTime;
                            rttCount++;
                        }

                        // Reset to avoid double counting, incase the logic loops
                        // (Shouldn't happen, but init. tests had this issue)
                        sendTime[idx] = 0.0; 
                    }
                }
                
                // For trace == 2
                if ((base - oldBase) > 1) {
                    System.out.println("TRACE_CASE_2: Cumulative ACK " + ack + 
                                       " moved window by " + (base - oldBase) + " (from " + oldBase + " to " + base + ")");
                }
                
                // If the window is now empty, stop timer.
                if (base == nextseqnum) {
                    stopTimer(A);
                } 
                // Else, still got unACKed pkts --> Stop the old timer (for old base) and start new timer (for new base).
                else {
                    stopTimer(A);
                    startTimer(A, RxmtInterval);
                    dupAckCount = 0;
                }
                
                sendPackets();
            }
        } 
        else {
            int expectedDup = (base - 1 + LimitSeqNo) % LimitSeqNo;
            if (ack == expectedDup) {
                dupAckCount++;
                if (dupAckCount >= 3) { // Fast Retransmit
                    Packet p = snd_buf[base % WindowSize];
                    if (p != null) {
                        toLayer3(A, p);
                        numRetransmissions++;
                        totalPacketsSent++;
                        hasRetransmitted[base % LimitSeqNo] = true;
                    }
                }
            } else {
                dupAckCount = 0; // Reset on valid/new ACK
            }
        }
    }
    
    // A's packet expired
    // Just rtransmit the oldest unACKed packet [base]
    protected void aTimerInterrupt()
    {
        // For trace == 3
        System.out.println("TRACE_CASE_3: Timeout. Retransmitting packet " + base);
        
        // Retransmit
        Packet p = snd_buf[base % WindowSize];
        if (p != null) {
            toLayer3(A, p);
            numRetransmissions++;
            totalPacketsSent++;
            hasRetransmitted[base % LimitSeqNo] = true; // Mark as retransmitted
            
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
        // Pkt corruption check
        if (isCorrupted(packet)) {
            numCorruptedPackets++;
            return;
        }

        int seq = packet.getSeqnum();

        /* Are we getting the expected seq# pkt?
            - If so --> Check the buffer for consecutive pkts to send out, and send cum. ACK for the last sent pkt
            - Else, check if it's:
                - o-f-o
                - a dup
        */
        int expectedMod = expectedseqnum % LimitSeqNo;
        int diff = (seq - expectedMod + LimitSeqNo) % LimitSeqNo;

        if (diff == 0) {
            toLayer5(packet.getPayload());
            numDeliveredToLayer5++;
            expectedseqnum++;
            
            // trace == 5
            boolean deliveredBuffered = false;
            while (true) {
                int idx = expectedseqnum % WindowSize;
                if (rcv_buf[idx] != null) {
                    if (rcv_buf[idx].getSeqnum() == expectedseqnum % LimitSeqNo) {
                        toLayer5(rcv_buf[idx].getPayload());
                        numDeliveredToLayer5++;
                        rcv_buf[idx] = null; // Clear slot
                        expectedseqnum++;
                        deliveredBuffered = true;
                    } else {
                        break; // Gap found
                    }
                } else {
                    break;
                }
            }
            
            sendACK(expectedseqnum - 1);
            
            // trace == 5
            if (deliveredBuffered) {
                 System.out.println("TRACE_CASE_5: Delivered buffered packets. Cumulative ACK sent for " + (expectedseqnum - 1));
            }
        } 
        // o-of-o pkt
        else if (diff < WindowSize) {
            // Store in rcv buf
            rcv_buf[seq % WindowSize] = packet;
            
            // Send cum. ACK for last in-order packet
            sendACK(expectedseqnum - 1);
        }
        // dup/old pkt
        else {
            sendACK(expectedseqnum - 1);
        }
    }

    // Helper method for bInput(): Sends ACK
    private void sendACK(int ackNum) {
        // Since ACK# is logical --> send MOD LimitSeq#
        int ackToSend = ackNum % LimitSeqNo;

        // In case Ack# is -1 (neg. MOD value)
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
        System.out.println("\n\n===============STATISTICS=======================");
        System.out.println("Number of original packets transmitted by A: " + numOriginalPackets);
        System.out.println("Number of retransmissions by A: " + numRetransmissions);
        System.out.println("Number of data packets delivered to layer 5 at B: " + numDeliveredToLayer5);
        System.out.println("Number of ACK packets sent by B: " + numACKsSent);
        System.out.println("Number of corrupted packets: " + numCorruptedPackets);
        
        int totalSent = numOriginalPackets + numRetransmissions + numACKsSent;
        double lostRatio = 0.0;
        if (totalSent > 0) {
            lostRatio = (double)(numRetransmissions - numCorruptedPackets) / totalSent;
            if (lostRatio < 0) lostRatio = 0.0;
        }
        
        double corruptRatio = 0.0;
        double denom = totalSent - (numRetransmissions - numCorruptedPackets);
        if (denom > 0) {
            corruptRatio = (double)numCorruptedPackets / denom;
        }
        
        System.out.println("Ratio of lost packets: " + lostRatio);
        System.out.println("Ratio of corrupted packets: " + corruptRatio);
        
        double avgRTT = rttCount > 0 ? totalRTT / rttCount : 0.0;
        double avgCommTime = commTimeCount > 0 ? totalCommTime / commTimeCount : 0.0;
        
        System.out.println("Average RTT: " + avgRTT);
        System.out.println("Average communication time: " + avgCommTime);
        System.out.println("==================================================");
    }	
}