import java.util.*;
import java.io.*;

public class StudentNetworkSimulator extends NetworkSimulator {
    // === FIELDS ===
    // DONT TOUCH
    public static final int FirstSeqNo = 0;
    private int WindowSize;
    private double RxmtInterval;
    private int LimitSeqNo;

    // Entity A: Use ABSOLUTE counters. Modulo only for array indexing & packet fields.
    private int base;                  // Oldest unACKed seq.# (Absolute)
    private int nextseqnum;            // Next seq.# to send (Absolute)
    private boolean timerRunning = false;
    private Packet[] snd_buf;          // Sender window buffer
    private ArrayList<Message> pendingMessages = new ArrayList<>();
    
    // Entity B
    private int expectedseqnum;        // Next expected in-order seq.# (Absolute)
    private int[] sackHistory = new int[5]; // Tracks the last 5 received seq.# (for SACK)
    private int sackIdx = 0;           // Circular index for sackHistory
    
    // Statistics
    private int numOriginalPackets = 0;
    private int numRetransmissions = 0;
    private int numDeliveredToLayer5 = 0;
    private int numACKsSent = 0;
    private int numCorruptedPackets = 0;
    private int totalPacketsSent = 0;
    private double[] sendTime;         // Time when packet was first sent
    private boolean[] hasRetransmitted;// Whether packet has been retransmitted
    private double totalRTT = 0.0;
    private int rttCount = 0;
    private double totalCommTime = 0.0;
    private int commTimeCount = 0;

    // === CONSTRUCTOR ===
    public StudentNetworkSimulator(int numMessages, double loss, double corrupt, 
                                   double avgDelay, int trace, int seed, 
                                   int winsize, double delay) {
        super(numMessages, loss, corrupt, avgDelay, trace, seed);
        WindowSize = winsize;
        LimitSeqNo = winsize * 2;
        RxmtInterval = delay;
        snd_buf = new Packet[WindowSize];
        sendTime = new double[LimitSeqNo];
        hasRetransmitted = new boolean[LimitSeqNo];
        Arrays.fill(sackHistory, -1);
    }

    // === SENDER (A) ===
    protected void aOutput(Message message) {
        pendingMessages.add(message);
        sendPackets();
    }

    private void sendPackets() {
        // Window check uses absolute values
        while (!pendingMessages.isEmpty() && nextseqnum < base + WindowSize) {
            Message msg = pendingMessages.remove(0);
            
            Packet pkt = new Packet(nextseqnum % LimitSeqNo, -1, 0, msg.getData());
            pkt.setChecksum(calculateChecksum(pkt));
            snd_buf[nextseqnum % WindowSize] = pkt;
            toLayer3(A, pkt);
            
            System.out.println("[DEBUG A] Sent pkt " + (nextseqnum % LimitSeqNo) + " | base=" + base + " | next=" + nextseqnum);
            
            numOriginalPackets++;
            totalPacketsSent++;
            
            int seqMod = nextseqnum % LimitSeqNo;
            if (sendTime[seqMod] == 0.0) {
                sendTime[seqMod] = getTime();
                hasRetransmitted[seqMod] = false;
            }
            
            // Start timer ONLY for the first packet in window (base)
            if (nextseqnum == base && !timerRunning) {
                System.out.println("[DEBUG TIMER] Starting timer for base=" + base);
                startTimer(A, RxmtInterval);
                timerRunning = true;
            }
            nextseqnum++;
        }
    }

    protected void aInput(Packet packet) {
        if (isCorrupted(packet)) {
            numCorruptedPackets++;
            return;
        }

        int recvAck = packet.getAcknum(); // This is modulo'd by receiver
        
        // GBN Cumulative ACK Check:
        // Since LimitSeqNo > 2*WindowSize, we can safely check if ACK matches base modulo
        // or falls within the window ahead of base.
        int baseMod = base % LimitSeqNo;
        int ackMod = recvAck;
        int diff = (ackMod - baseMod + LimitSeqNo) % LimitSeqNo;

        if (diff >= 0 && diff < WindowSize) {
            int oldBase = base;
            base = base + diff + 1; // Advance base absolutely

            System.out.println("[DEBUG WINDOW] ACK=" + ackMod + " >= base=" + baseMod + " | Advancing base to " + base);

            // Update stats for newly acknowledged packets
            for (int i = oldBase; i < base; i++) {
                int idx = i % LimitSeqNo;
                if (sendTime[idx] != 0.0) {
                    double commTime = getTime() - sendTime[idx];
                    totalCommTime += commTime;
                    commTimeCount++;
                    if (!hasRetransmitted[idx]) {
                        totalRTT += commTime;
                        rttCount++;
                    }
                    sendTime[idx] = 0.0; // Reset to prevent double counting
                }
            }

            // Timer management
            if (base == nextseqnum) {
                if (timerRunning) { stopTimer(A); timerRunning = false; }
                System.out.println("[DEBUG TIMER] Window empty. Stopping timer.");
            } else {
                if (timerRunning) stopTimer(A);
                startTimer(A, RxmtInterval);
                System.out.println("[DEBUG TIMER] Restarting timer for new base=" + base);
            }
            sendPackets(); // Slide window & send pending
        } else {
            System.out.println("[DEBUG ACK] Duplicate/Invalid ACK (" + ackMod + "). Ignoring.");
        }
    }

    protected void aTimerInterrupt() {
        System.out.println("[DEBUG TIMEOUT] Timer expired! base=" + base + " | nextseqnum=" + nextseqnum + " | timerRunning=" + timerRunning);
        
        // GBN: Retransmit ALL unacked packets in window [base, nextseqnum)
        int seq = base;
        while (seq != nextseqnum) {
            Packet p = snd_buf[seq % WindowSize];
            if (p != null) {
                toLayer3(A, p);
                numRetransmissions++;
                totalPacketsSent++;
                hasRetransmitted[seq % LimitSeqNo] = true;
            }
            seq++;
        }
        
        // Restart timer
        if (timerRunning) stopTimer(A);
        startTimer(A, RxmtInterval);
        timerRunning = true;
        System.out.println("[DEBUG TIMEOUT] Retransmitted window. Timer restarted.");
    }

    protected void aInit() {
        base = 0; 
        nextseqnum = 0; 
        timerRunning = false; 
        pendingMessages.clear();
    }

    // === RECEIVER (B) ===
    protected void bInput(Packet packet) {
        if (isCorrupted(packet)) {
            numCorruptedPackets++;
            sendACK(expectedseqnum - 1);
            return;
        }

        int seq = packet.getSeqnum(); // Modulo'd
        recordInSACK(seq);

        // Check if in-order (using absolute expectedseqnum)
        if (seq == expectedseqnum % LimitSeqNo) {
            toLayer5(packet.getPayload());
            numDeliveredToLayer5++;
            expectedseqnum++;
            System.out.println("[DEBUG B] In-order delivery. expectedseqnum advanced to " + expectedseqnum);
        } else {
            System.out.println("[DEBUG B] Out-of-order/Duplicate pkt " + seq + ". Discarding.");
        }
        sendACK(expectedseqnum - 1);
    }

    protected void bInit() {
        expectedseqnum = 0;
        Arrays.fill(sackHistory, -1);
        sackIdx = 0;
    }

    private void sendACK(int ackNum) {
        int ackToSend = ackNum % LimitSeqNo;
        if (ackToSend < 0) ackToSend += LimitSeqNo;
        
        Packet ackPacket = new Packet(-1, ackToSend, 0, " ");
        // Fill SACK block with last 5 received seqnums
        for (int i = 0; i < 5; i++) {
            int val = sackHistory[(sackIdx + i) % 5];
            ackPacket.sack[i] = (val == -1) ? -1 : val;
        }
        ackPacket.setChecksum(calculateChecksum(ackPacket));
        toLayer3(B, ackPacket);
        numACKsSent++;
        System.out.println("[DEBUG ACK SENT] ACK=" + ackToSend + " | SACK=[" + 
            ackPacket.sack[0] + "," + ackPacket.sack[1] + "," + ackPacket.sack[2] + "," + ackPacket.sack[3] + "," + ackPacket.sack[4] + "]");
    }

    private void recordInSACK(int seq) {
        sackHistory[sackIdx] = seq;
        sackIdx = (sackIdx + 1) % 5;
    }

    // === UTILITIES ===
    private int calculateChecksum(Packet packet) {
        int checksum = 0;
        checksum += packet.getSeqnum();
        checksum += packet.getAcknum();
        if (packet.sack != null) {
            for (int i = 0; i < 5; i++) checksum += packet.sack[i];
        }
        String payload = packet.getPayload();
        if (payload != null) {
            for (int i = 0; i < payload.length(); i++) checksum += (int) payload.charAt(i);
        }
        return checksum;
    }

    private boolean isCorrupted(Packet packet) {
        return calculateChecksum(packet) != packet.getChecksum();
    }

    protected void Simulation_done() {
        System.out.println("\n\n===============STATISTICS=======================");
        System.out.println("Number of original packets transmitted by A: " + numOriginalPackets);
        System.out.println("Number of retransmissions by A: " + numRetransmissions);
        System.out.println("Number of data packets delivered to layer 5 at B: " + numDeliveredToLayer5);
        System.out.println("Number of ACK packets sent by B: " + numACKsSent);
        System.out.println("Number of corrupted packets: " + numCorruptedPackets);
        int totalSent = numOriginalPackets + numRetransmissions + numACKsSent;
        double lostRatio = totalSent > 0 ? (double)(numRetransmissions - numCorruptedPackets) / totalSent : 0.0;
        if (lostRatio < 0) lostRatio = 0.0;
        double corruptRatio = 0.0;
        double denom = totalSent - (numRetransmissions - numCorruptedPackets);
        if (denom > 0) corruptRatio = (double)numCorruptedPackets / denom;
        System.out.println("Ratio of lost packets: " + lostRatio);
        System.out.println("Ratio of corrupted packets: " + corruptRatio);
        System.out.println("Average RTT: " + (rttCount > 0 ? totalRTT / rttCount : 0.0));
        System.out.println("Average communication time: " + (commTimeCount > 0 ? totalCommTime / commTimeCount : 0.0));
        System.out.println("==================================================");
    }
}