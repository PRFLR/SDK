import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class PRFLR {
	public static String source = null;
	public static String apiKey = null;
	public static Integer overflowCount = 100;
	
	private static InetAddress IPAddress;
	private static Integer port;
	private static DatagramSocket socket;
	private static ConcurrentHashMap<String, Long> timers;
	private static AtomicInteger counter = new AtomicInteger(0);
	private PRFLR() {
		
	}
	public static void init(String source, String apiKey) throws Exception {
		try {
			IPAddress = InetAddress.getByName("prflr.org");
		} catch (UnknownHostException e) {
			throw new Exception("Host unknown.");
		}
		PRFLR.port = 4000;
		try {
			socket = new DatagramSocket();
		} catch (SocketException e) {
			throw new Exception("Can't open socket.");
		}
		
		if(apiKey == null) {
			throw new Exception("Unknown apikey.");
		}
		else {
			PRFLR.apiKey = cut(apiKey, 32);
		}
		if(source == null) {
			throw new Exception("Unknown source.");
		}
		else {
			PRFLR.source = cut(source, 32);
		}
		PRFLR.timers = new ConcurrentHashMap<>();
	}
	private static void cleanTimers() {
		PRFLR.timers.clear();
	}
	public static Boolean begin(String timerName) {
		Integer val = counter.incrementAndGet();
		if(val > overflowCount) {
			cleanTimers();
			counter.set(0);
		}
		timers.put(Long.toString(Thread.currentThread().getId()) + timerName,System.nanoTime());
		return true;
	}
	
	public static Boolean end(String timerName, String info) throws Exception {
		String thread = Long.toString(Thread.currentThread().getId());
		Long startTime = timers.get(thread + timerName);
		if(startTime == null) {
			return false;
		}
		counter.decrementAndGet();
		timers.remove(timerName);
		Long now = System.nanoTime();
		Long precision = (long) Math.pow(10, 3);
		Double diffTime = (double)Math.round((double)(now - startTime) / 1000000 * precision) / precision;
		send(timerName, diffTime, thread, info);
		return true;
	}
	private static String cut(String s, Integer maxLength) {
		if(s.length() < maxLength)
			return s;
		else
			return s.substring(0, maxLength);
	}
	private static void send(String timerName, Double time, String thread, String info) throws Exception {
		byte[] buffer = (
                	  cut(thread, 32) + "|"
                        + source + "|"
                        + cut(timerName, 48) + "|"
                        + Double.toString(time) + "|"
                        + cut(info, 32) + "|"
                        + apiKey
        	).getBytes(Charset.forName("UTF-8"));
        
		try {
			socket.send( new DatagramPacket(buffer, buffer.length, IPAddress, port) );
		} catch (IOException e) {
			throw new Exception("IOException while sending.");
		}
	}
}
