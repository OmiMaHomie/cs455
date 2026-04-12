/* Combined the following files: 
    - Event.java
    - EventList.java
    - EventListlmpl.java
    - Message.java
    - NetworkSimulator.java
    - OSIRandom.java
    - Packet.java
    - Project.java
*/

//
// Event.java
//
public class Event
{
    private double time;
    private int type;
    private int entity;
    private Packet packet;
    
    public Event(double t, int ty, int ent)
    {
        time = t;
        type = ty;
        entity = ent;
        packet = null;
    }
    
    public Event(double t, int ty, int ent, Packet p)
    {
        time = t;
        type = ty;
        entity = ent;
        packet = new Packet(p);
    }
            
    public boolean setTime(double t)
    {
        time = t;
        return true;
    }
    
    public boolean setType(int n)
    {
        if ((n != NetworkSimulator.TIMERINTERRUPT) &&
            (n != NetworkSimulator.FROMLAYER5) &&
            (n != NetworkSimulator.FROMLAYER3))
        {
            type = -1;
            return false;
        }
        
        type = n;
        return true;
    }
    
    public boolean setEntity(int n)
    {
        if ((n != NetworkSimulator.A) &&
            (n != NetworkSimulator.B))
        {
            entity = -1;
            return false;
        }
        
        entity = n;
        return true;
    }
    
    public boolean setPacket(Packet p)
    {
        if (p == null)
        {
            packet = null;
        }        
        else
        {
            packet = new Packet(p.getSeqnum(), p.getAcknum(),
                                p.getChecksum(), p.getPayload());
        }
        
        return true;
    }
    
    public double getTime()
    {
        return time;
    }
    
    public int getType()
    {
        return type;
    }
    
    public int getEntity()
    {
        return entity;
    }
    
    public Packet getPacket()
    {
        return packet;
    }
    
    public String toString()
    {
        return("time: " + time + "  type: " + type + "  entity: " + entity +
               "packet: " + packet);
    }
        
}

//
// EventList.java
//
public interface EventList
{
    public boolean add(Event e);
    public Event removeNext();
    public String toString();
    public Event removeTimer(int entity);
    public double getLastPacketTime(int entityTo);
}

//
// EventListlmpl.java
//
import java.util.Vector;

public class EventListImpl implements EventList
{
    private Vector<Event> data;
    
    public EventListImpl()
    {
        data = new Vector<Event>();
    }
    
    public boolean add(Event e)
    {
	data.addElement(e);
        return true;
    }
    
    public Event removeNext()
    {
        if (data.isEmpty())
        {
            return null;
        }
    
        int firstIndex = 0;
        double first = ((Event)data.elementAt(firstIndex)).getTime();
        for (int i = 0; i < data.size(); i++)
        {
            if (((Event)data.elementAt(i)).getTime() < first)
            {
                first = ((Event)data.elementAt(i)).getTime();
                firstIndex = i;
            }
        }
        
        Event next = (Event)data.elementAt(firstIndex);
        data.removeElement(next);
    
        return next;
    }
    
    public String toString()
    {
        return data.toString();
    }

    public Event removeTimer(int entity)
    {
        int timerIndex = -1;
        Event timer = null;
        
        for (int i = 0; i < data.size(); i++)
        {
            if ((((Event)(data.elementAt(i))).getType() == 
                                           NetworkSimulator.TIMERINTERRUPT) &&
                (((Event)(data.elementAt(i))).getEntity() == entity))
            {
                timerIndex = i;
                break;
            }
        }
        
        if (timerIndex != -1)
        {
            timer = (Event)(data.elementAt(timerIndex));
            data.removeElement(timer);
        }
        
        return timer;
            
    }
    
    public double getLastPacketTime(int entityTo)
    {
        double time = 0;
        for (int i = 0; i < data.size(); i++)
        {
            if ((((Event)(data.elementAt(i))).getType() == 
                                           NetworkSimulator.FROMLAYER3) &&
                (((Event)(data.elementAt(i))).getEntity() == entityTo))
            {
                time = ((Event)(data.elementAt(i))).getTime();
            }
        }
    
        return time;
    }
}

//
// Message.java
//
public class Message
{
    private String data;
    
    public Message(String inputData)
    {
        if (inputData == null)
        {
            data = "";
        }
        else if (inputData.length() > NetworkSimulator.MAXDATASIZE)
        {
            data = "";
        }
        else
        {
            data = new String(inputData);
        }
    }
           
    public boolean setData(String inputData)
    {
        if (inputData == null)
        {
            data = "";
            return false;
        }
        else if (inputData.length() > NetworkSimulator.MAXDATASIZE)
        {
            data = "";
            return false;
        }
        else
        {
            data = new String(inputData);
            return true;
        }
    }
    
    public String getData()
    {
        return data;
    }
}

//
// NetworkSimulator.java
//
import java.util.Vector;
import java.util.Enumeration;
import java.io.*;

public abstract class NetworkSimulator
{
    // This constant controls the maximum size of the buffer in a Message
    // and in a Packet
    public static final int MAXDATASIZE = 20;
    
    // These constants are possible events
    public static final int TIMERINTERRUPT = 0;
    public static final int FROMLAYER5 = 1;
    public static final int FROMLAYER3 = 2;
    
    // These constants represent our sender and receiver 
    public static final int A = 0;
    public static final int B = 1;

    private int maxMessages;
    private double lossProb;
    private double corruptProb;
    private double avgMessageDelay;
    protected int traceLevel;
    private EventList eventList;
    private FileWriter outFile;

    private OSIRandom rand;

    private int nSim;
    private int nToLayer3;
    private int nLost;
    private int nCorrupt;
    private double time;
    
    
    protected abstract void aOutput(Message message);
    protected abstract void aInput(Packet packet);
    protected abstract void aTimerInterrupt();
    protected abstract void aInit();

    protected abstract void bInput(Packet packet);
    protected abstract void bInit();
    protected abstract void Simulation_done();
    
    public NetworkSimulator(int numMessages,
                            double loss,
                            double corrupt,
                            double avgDelay,
                            int trace,
                            int seed)
    {
        maxMessages = numMessages;
        lossProb = loss;
        corruptProb = corrupt;
        avgMessageDelay = avgDelay;
        traceLevel = trace;
        eventList = new EventListImpl();
        rand = new OSIRandom(seed);
	try{
	    outFile = new FileWriter("OutputFile");
	}catch (Exception e) {e.printStackTrace();}

        nSim = 0;
        nToLayer3 = 0;
        nLost = 0;
        nCorrupt = 0;
        time = 0;
    }
    
    public void runSimulator()
    {
        Event next;
        
        // Perform any student-required initialization
        aInit();
        bInit();
        
        // Start the whole thing off by scheduling some data arrival
        // from layer 5
        generateNextArrival();
        
        // Begin the main loop
        while (true)
        {
            // Get our next event
            next = eventList.removeNext();
            if (next == null)
            {
                break;
            }
            
            if (traceLevel >= 2)
            {
                System.out.println();
                System.out.print("EVENT time: " + next.getTime());
                System.out.print("  type: " + next.getType());
                System.out.println("  entity: " + next.getEntity());
            }
            
            // Advance the simulator's time
            time = next.getTime();
            
            // Perform the appropriate action based on the event 
            switch (next.getType())
            {
                case TIMERINTERRUPT:
                    if (next.getEntity() == A)
                    {
                        aTimerInterrupt();
                    }
                    else
                    {
                        System.out.println("INTERNAL PANIC: Timeout for " +
                                           "invalid entity");
                    }
                    break;
                    
                case FROMLAYER3:
                    if (next.getEntity() == A)
                    {
                        aInput(next.getPacket());
                    }
                    else if (next.getEntity() == B)
                    {
                        bInput(next.getPacket());
                    }
                    else
                    {
                        System.out.println("INTERNAL PANIC: Packet has " +
                                           "arrived for unknown entity");
                    }
                    
                    break;
                    
                case FROMLAYER5:
                    
                    // If a message has arrived from layer 5, we need to
                    // schedule the arrival of the next message
                    generateNextArrival();
                    
                    char[] nextMessage = new char[MAXDATASIZE];
                    
                    // Now, let's generate the contents of this message
                    char j = (char)((nSim % 26) + 97);
                    for (int i = 0; i < MAXDATASIZE; i++)
                    {
                        nextMessage[i] = j;
                    }
                    
                    // Increment the message counter
                    nSim++;

                    // If we've reached the maximum message count, exit the main loop
		    if (nSim == maxMessages+1)
			break;
                    
                    // Let the student handle the new message
                    aOutput(new Message(new String(nextMessage)));
                    break;
                    
                default:
                    System.out.println("INTERNAL PANIC: Unknown event type");
            }
	    if (nSim == maxMessages+1)
		break;
        }
        System.out.println("Simulator terminated at time "+getTime());
        Simulation_done();
	try{
	    outFile.flush();
	    outFile.close();
	}catch (Exception e) {e.printStackTrace();}
    }
    
    /* Generate the next arrival and add it to the event list */
    private void generateNextArrival()
    {
        if (traceLevel > 2)
        {
            System.out.println("generateNextArrival(): called");
        }
        
        // arrival time 'x' is uniform on [0, 2*avgMessageDelay]
        // having mean of avgMessageDelay.  Should this be made
        // into a Gaussian distribution? 
        double x = 2 * avgMessageDelay * rand.nextDouble(0);
        Event next = new Event(time + x, FROMLAYER5, A);
                
        eventList.add(next);
        if (traceLevel > 2)
        {
            System.out.println("generateNextArrival(): time is " + time);
            System.out.println("generateNextArrival(): future time for " +
                               "event " + next.getType() + " at entity " +
                               next.getEntity() + " will be " +
                               next.getTime());
        }
        
    }
    
    protected void stopTimer(int entity)
    {
        if (traceLevel > 2)
        {
            System.out.println("stopTimer: stopping timer at " + time);
        }

        Event timer = eventList.removeTimer(entity);

        // Let the student know they are attempting to cancel a non-existant 
        // timer
        if (timer == null)
        {
            System.out.println("stopTimer: Warning: Unable to cancel your " +
                               "timer");
        }        
    }
    
    protected void startTimer(int entity, double increment)
    {
        if (traceLevel > 2)
        {
            System.out.println("startTimer: starting timer at " + time);
        }

        Event t = eventList.removeTimer(entity);        

        if (t != null)
        {
            System.out.println("startTimer: Warning: Attempting to start a " +
                               "timer that is already running");
            eventList.add(t);
            return;
        }
        else
        {
            Event timer = new Event(time + increment, TIMERINTERRUPT, entity);
            eventList.add(timer);
        }
    }    
    
    protected void toLayer3(int callingEntity, Packet p)
    {
        nToLayer3++;
        
        int destination;
        double arrivalTime;
        Packet packet = new Packet(p);
    
        if (traceLevel > 2)
        {
            System.out.println("toLayer3: " + packet);
        }

        // Set our destination
        if (callingEntity == A)
        {
            destination = B;
        }
        else if (callingEntity == B)
        {
            destination = A;
        }
        else
        {
            System.out.println("toLayer3: Warning: invalid packet sender");
            return;
        }

        // Simulate losses
        if (rand.nextDouble(1) < lossProb)
        {
            nLost++;
            
            if (traceLevel > 0)
            {
                System.out.println("toLayer3: packet being lost");
            }
            
            return;
        }
        
        // Decide when the packet will arrive.  Since the medium cannot
        // reorder, the packet will arrive 1 to 10 time units after the
        // last packet sent by this sender
        arrivalTime = eventList.getLastPacketTime(destination);
        
        if (arrivalTime <= 0.0)
        {
            arrivalTime = time;
        }
        
        arrivalTime = arrivalTime + 1 + (rand.nextDouble(2) * 9);

        // Simulate corruption
        if (rand.nextDouble(3) < corruptProb)
        {
            nCorrupt++;
            
            if (traceLevel > 0)
            {
                System.out.println("toLayer3: packet being corrupted");
            }
            
            double x = rand.nextDouble(4);
            if (x < 0.75)
            {
                String payload = packet.getPayload();
                
		if (payload.length()>0)
                
		    payload = "?" + payload.substring(1);
		
		else payload = "?";
                
                packet.setPayload(payload);
            }
            else if (x < 0.875)
            {
                packet.setSeqnum(999999);
            }
            else
            {
                packet.setAcknum(999999);
            }
        }
        

        // Finally, create and schedule this event
        if (traceLevel > 2)
        {
            System.out.println("toLayer3: scheduling arrival on other side");
        }
        Event arrival = new Event(arrivalTime, FROMLAYER3, destination, packet);
        eventList.add(arrival);
    }
    
    protected void toLayer5(String dataSent)
    {
	try{
	    outFile.write(dataSent,0,MAXDATASIZE);
	    outFile.write('\n');
	}catch (Exception e) {e.printStackTrace();}
    }
    
    protected double getTime()
    {
        return time;
    }
    
    protected void printEventList()
    {
        System.out.println(eventList.toString());
    }
    
}

//
// OSIRandom.java
//
public class OSIRandom
{
    private long seed[] = new long[5];

    public OSIRandom(int s)
    {
	for (int i=0;i<5;i++)
	  seed[i] = (s+i) & 0xFFFFFFFFL;
    }

    public int nextInt(int i)
    {
	seed[i] = ((seed[i]&0xFFFFFFFFL)*(1103515245&0xFFFFFFFFL)+12345)&0xFFFFFFFFL;
	return (int)(seed[i]/65536)%32768;
    }

    public double nextDouble(int i)
    {
	return (double)nextInt(i)/32767;
    }
}

//
// Packet.java
//
public class Packet
{
    private int seqnum;
    private int acknum;
    private int checksum;
    private String payload;
    
    public Packet(Packet p)
    {
        seqnum = p.getSeqnum();
        acknum = p.getAcknum();
        checksum = p.getChecksum();
        payload = new String(p.getPayload());
    }
    
    public Packet(int seq, int ack, int check, String newPayload)
    {
        seqnum = seq;
        acknum = ack;
        checksum = check;
        if (newPayload == null)
        {
            payload = "";
        }        
        else if (newPayload.length() > NetworkSimulator.MAXDATASIZE)
        {
            payload = null;
        }
        else
        {
            payload = new String(newPayload);
        }
    }
    
    public Packet(int seq, int ack, int check)
    {
        seqnum = seq;
        acknum = ack;
        checksum = check;
        payload = "";
    }    
        

    public boolean setSeqnum(int n)
    {
        seqnum = n;
        return true;
    }
    
    public boolean setAcknum(int n)
    {
        acknum = n;
        return true;
    }
    
    public boolean setChecksum(int n)
    {
        checksum = n;
        return true;
    }
    
    public boolean setPayload(String newPayload)
    {
        if (newPayload == null)
        {
            payload = "";
            return false;
        }        
        else if (newPayload.length() > NetworkSimulator.MAXDATASIZE)
        {
            payload = "";
            return false;
        }
        else
        {
            payload = new String(newPayload);
            return true;
        }
    }
    
    public int getSeqnum()
    {
        return seqnum;
    }
    
    public int getAcknum()
    {
        return acknum;
    }
    
    public int getChecksum()
    {
        return checksum;
    }
    
    public String getPayload()
    {
        return payload;
    }
    
    public String toString()
    {
        return("seqnum: " + seqnum + "  acknum: " + acknum + "  checksum: " +
               checksum + "  payload: " + payload);
    }
    
}

//
// StudentNetworkSimulator.java
//
import java.util.*;
import java.io.*;

public class StudentNetworkSimulator extends NetworkSimulator
{
    /*
     * Predefined Constants (static member variables):
     *
     *   int MAXDATASIZE : the maximum size of the Message data and
     *                     Packet payload
     *
     *   int A           : a predefined integer that represents entity A
     *   int B           : a predefined integer that represents entity B 
     *
     * Predefined Member Methods:
     *
     *  void stopTimer(int entity): 
     *       Stops the timer running at "entity" [A or B]
     *  void startTimer(int entity, double increment): 
     *       Starts a timer running at "entity" [A or B], which will expire in
     *       "increment" time units, causing the interrupt handler to be
     *       called.  You should only call this with A.
     *  void toLayer3(int callingEntity, Packet p)
     *       Puts the packet "p" into the network from "callingEntity" [A or B]
     *  void toLayer5(String dataSent)
     *       Passes "dataSent" up to layer 5
     *  double getTime()
     *       Returns the current time in the simulator.  Might be useful for
     *       debugging.
     *  int getTraceLevel()
     *       Returns TraceLevel
     *  void printEventList()
     *       Prints the current event list to stdout.  Might be useful for
     *       debugging, but probably not.
     *
     *
     *  Predefined Classes:
     *
     *  Message: Used to encapsulate a message coming from layer 5
     *    Constructor:
     *      Message(String inputData): 
     *          creates a new Message containing "inputData"
     *    Methods:
     *      boolean setData(String inputData):
     *          sets an existing Message's data to "inputData"
     *          returns true on success, false otherwise
     *      String getData():
     *          returns the data contained in the message
     *  Packet: Used to encapsulate a packet
     *    Constructors:
     *      Packet (Packet p):
     *          creates a new Packet that is a copy of "p"
     *      Packet (int seq, int ack, int check, String newPayload)
     *          creates a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and a
     *          payload of "newPayload"
     *      Packet (int seq, int ack, int check)
     *          chreate a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and
     *          an empty payload
     *    Methods:
     *      boolean setSeqnum(int n)
     *          sets the Packet's sequence field to "n"
     *          returns true on success, false otherwise
     *      boolean setAcknum(int n)
     *          sets the Packet's ack field to "n"
     *          returns true on success, false otherwise
     *      boolean setChecksum(int n)
     *          sets the Packet's checksum to "n"
     *          returns true on success, false otherwise
     *      boolean setPayload(String newPayload)
     *          sets the Packet's payload to "newPayload"
     *          returns true on success, false otherwise
     *      int getSeqnum()
     *          returns the contents of the Packet's sequence field
     *      int getAcknum()
     *          returns the contents of the Packet's ack field
     *      int getChecksum()
     *          returns the checksum of the Packet
     *      int getPayload()
     *          returns the Packet's payload
     *
     */

    /*   Please use the following variables in your routines.
     *   int WindowSize  : the window size
     *   double RxmtInterval   : the retransmission timeout
     *   int LimitSeqNo  : when sequence number reaches this value, it wraps around
     */

    public static final int FirstSeqNo = 0;
    private int WindowSize;
    private double RxmtInterval;
    private int LimitSeqNo;
    
    // Add any necessary class variables here.  Remember, you cannot use
    // these variables to send messages error free!  They can only hold
    // state information for A or B.
    // Also add any necessary methods (e.g. checksum of a String)

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
	LimitSeqNo = winsize*2; // set appropriately; assumes SR here!
	RxmtInterval = delay;
    }

    
    // This routine will be called whenever the upper layer at the sender [A]
    // has a message to send.  It is the job of your protocol to insure that
    // the data in such a message is delivered in-order, and correctly, to
    // the receiving upper layer.
    protected void aOutput(Message message)
    {

    }
    
    // This routine will be called whenever a packet sent from the B-side 
    // (i.e. as a result of a toLayer3() being done by a B-side procedure)
    // arrives at the A-side.  "packet" is the (possibly corrupted) packet
    // sent from the B-side.
    protected void aInput(Packet packet)
    {

    }
    
    // This routine will be called when A's timer expires (thus generating a 
    // timer interrupt). You'll probably want to use this routine to control 
    // the retransmission of packets. See startTimer() and stopTimer(), above,
    // for how the timer is started and stopped. 
    protected void aTimerInterrupt()
    {

    }
    
    // This routine will be called once, before any of your other A-side 
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity A).
    protected void aInit()
    {

    }
    
    // This routine will be called whenever a packet sent from the B-side 
    // (i.e. as a result of a toLayer3() being done by an A-side procedure)
    // arrives at the B-side.  "packet" is the (possibly corrupted) packet
    // sent from the A-side.
    protected void bInput(Packet packet)
    {

    }
    
    // This routine will be called once, before any of your other B-side 
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity B).
    protected void bInit()
    {

    }

    // Use to print final statistics
    protected void Simulation_done()
    {
    	// TO PRINT THE STATISTICS, FILL IN THE DETAILS BY PUTTING VARIBALE NAMES. DO NOT CHANGE THE FORMAT OF PRINTED OUTPUT
    	System.out.println("\n\n===============STATISTICS=======================");
    	System.out.println("Number of original packets transmitted by A:" + "<YourVariableHere>");
    	System.out.println("Number of retransmissions by A:" + "<YourVariableHere>");
    	System.out.println("Number of data packets delivered to layer 5 at B:" + "<YourVariableHere>");
    	System.out.println("Number of ACK packets sent by B:" + "<YourVariableHere>");
    	System.out.println("Number of corrupted packets:" + "<YourVariableHere>");
    	System.out.println("Ratio of lost packets:" + "<YourVariableHere>" );
    	System.out.println("Ratio of corrupted packets:" + "<YourVariableHere>");
    	System.out.println("Average RTT:" + "<YourVariableHere>");
    	System.out.println("Average communication time:" + "<YourVariableHere>");
    	System.out.println("==================================================");

    	// PRINT YOUR OWN STATISTIC HERE TO CHECK THE CORRECTNESS OF YOUR PROGRAM
    	System.out.println("\nEXTRA:");
    	// EXAMPLE GIVEN BELOW
    	//System.out.println("Example statistic you want to check e.g. number of ACK packets received by A :" + "<YourVariableHere>"); 
    }	

}

// Project.java
import java.io.*;

public class Project
{
    public final static void main(String[] argv)
    {
        StudentNetworkSimulator simulator;
        
        int nsim = -1;
        double loss = -1;
        double corrupt = -1;
        double delay = -1;
        int trace = -1;
        int seed = -1;
        int windowsize = -1;
        double timeout = -1;
        String buffer = "";
        File outputfile = new File("OutputFile");
        BufferedReader stdIn = new BufferedReader(
                                   new InputStreamReader(System.in));
                                   
        System.out.println("-- * Network Simulator v1.0 * --");
        
        while (nsim < 1)
        {
            System.out.print("Enter number of messages to simulate (> 0): " +
                             "[10] ");
            try
            {
                buffer = stdIn.readLine();
            }
            catch (IOException ioe)
            {
                System.out.println("IOError reading your input!");
                System.exit(1);
            }
            
            if (buffer.equals(""))
            {
                nsim = 10;
            }
            else
            {            
                try
                {
                    nsim = Integer.parseInt(buffer);
                }
                catch (NumberFormatException nfe)
                {
                    nsim = -1;
                }
            }
        }
        
        while (loss < 0)
        {
            System.out.print("Enter packet loss probability (0.0 for no " +
                             "loss): [0.0] ");
            try
            {
                buffer = stdIn.readLine();
            }
            catch (IOException ioe)
            {
                System.out.println("IOError reading your input!");
                System.exit(1);
            }
            
            if (buffer.equals(""))
            {
                loss = 0;
            }
            else
            {            
                try
                {
                    loss = (Double.valueOf(buffer)).doubleValue();
                }
                catch (NumberFormatException nfe)
                {
                    loss = -1;
                }
            }
        }            

        while (corrupt < 0)
        {
            System.out.print("Enter packet corruption probability (0.0 " +
                             "for no corruption): [0.0] ");
            try
            {
                buffer = stdIn.readLine();
            }
            catch (IOException ioe)
            {
                System.out.println("IOError reading your input!");
                System.exit(1);
            }
            
            if (buffer.equals(""))
            {
                corrupt = 0;
            }
            else
            {            
                try
                {
                    corrupt = (Double.valueOf(buffer)).doubleValue();
                }
                catch (NumberFormatException nfe)
                {
                    corrupt = -1;
                }
            }
        }            

        while (delay <= 0)
        {
            System.out.print("Enter average time between messages from " +
                             "sender's layer 5 (> 0.0): [1000] ");
            try
            {
                buffer = stdIn.readLine();
            }
            catch (IOException ioe)
            {
                System.out.println("IOError reading your input!");
                System.exit(1);
            }
            
            if (buffer.equals(""))
            {
                delay = 1000;
            }
            else
            {            
                try
                {
                    delay = (Double.valueOf(buffer)).doubleValue();
                }
                catch (NumberFormatException nfe)
                {
                    delay = -1;
                }
            }
        }            

         while (windowsize < 1)
        {
            System.out.print("Enter window size (> 0): [8] ");
            try
            {
                buffer = stdIn.readLine();
            }
            catch (IOException ioe)
            {
                System.out.println("IOError reading your input!");
                System.exit(1);
            }
            
            if (buffer.equals(""))
            {
                windowsize = 8;
            }
            else
            {            
                try
                {
                    windowsize = Integer.parseInt(buffer);
                }
                catch (NumberFormatException nfe)
                {
                    windowsize = -1;
                }
            }
        }

	while (timeout <= 0)
        {
            System.out.print("Enter retransmission timeout (>0.0) [15.0] ");
            try
            {
                buffer = stdIn.readLine();
            }
            catch (IOException ioe)
            {
                System.out.println("IOError reading your input!");
                System.exit(1);
            }
            
            if (buffer.equals(""))
            {
                timeout = 15.0;
            }
            else
            {            
                try
                {
                    timeout = (Double.valueOf(buffer)).doubleValue();
                }
                catch (NumberFormatException nfe)
                {
                    timeout = -1;
                }
            }
        }            

        while (trace < 0)
        {
            System.out.print("Enter trace level (>= 0): [0] ");
            try
            {
                buffer = stdIn.readLine();
            }
            catch (IOException ioe)
            {
                System.out.println("IOError reading your input!");
                System.exit(1);
            }
            
            if (buffer.equals(""))
            {
                trace = 0;
            }
            else
            {            
                try
                {
                    trace = Integer.parseInt(buffer);
                }
                catch (NumberFormatException nfe)
                {
                    trace = -1;
                }
            }
        }

        while (seed < 1)
        {
            System.out.print("Enter random seed: [0] ");
            try
            {
                buffer = stdIn.readLine();
            }
            catch (IOException ioe)
            {
                System.out.println("IOError reading your input!");
                System.exit(1);
            }
            
            if (buffer.equals(""))
            {
		seed = 0;
            }
            else
            {            
                try
                {
                    seed = (Integer.valueOf(buffer)).intValue();
                }
                catch (NumberFormatException nfe)
                {
                    seed = -1;
                }
            }
        }
         
        simulator = new StudentNetworkSimulator(nsim, loss, corrupt, delay,
                                                trace, seed, windowsize, timeout);
                                                
        simulator.runSimulator();
    }
}