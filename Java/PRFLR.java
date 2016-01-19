import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class PRFLR {
	public static String source = null;
	public static String key = null;
	public static Integer port;
	public static Integer overflowCount = 100;

	private static InetAddress IPAddress;
	private static DatagramSocket socket;
	private static ConcurrentHashMap<String, Long> timers;
	private static AtomicInteger counter = new AtomicInteger(0);

	private PRFLR() {

	}

	public static void init(String source, String apiKey) throws Exception {
		if (apiKey == null) {
			throw new Exception("Unknown apikey.");
		}

		String[] parts = apiKey.split("@");
		PRFLR.key = parts[0];
		parts = apiKey.split(":");
		String host = parts[0];
		PRFLR.port = Integer.parseInt(parts[1]);

		try {
			IPAddress = InetAddress.getByName(host);
		} catch (UnknownHostException e) {
			throw new Exception("Host unknown.");
		}
		try {
			socket = new DatagramSocket();
		} catch (SocketException e) {
			throw new Exception("Can't open socket.");
		}

		if (source == null) {
			throw new Exception("Unknown source.");
		} else {
			PRFLR.source = cut(source, 32);
		}
		PRFLR.timers = new ConcurrentHashMap<>();
	}

	private static void cleanTimers() {
		PRFLR.timers.clear();
	}

	public static Boolean begin(String timerName) {
		Integer val = counter.incrementAndGet();
		if (val > overflowCount) {
			cleanTimers();
			counter.set(0);
		}
		timers.put(Long.toString(Thread.currentThread().getId()) + timerName, System.nanoTime());
		return true;
	}

	public static Boolean end(String timerName, String info) throws Exception {
		String thread = Long.toString(Thread.currentThread().getId());
		Long startTime = timers.get(thread + timerName);
		if (startTime == null) {
			return false;
		}
		counter.decrementAndGet();
		timers.remove(timerName);
		Long now = System.nanoTime();
		Long precision = (long) Math.pow(10, 3);
		Double diffTime = (double) Math.round((double) (now - startTime) / 1000000 * precision) / precision;
		send(timerName, diffTime, thread, info);
		return true;
	}

	private static String cut(String s, Integer maxLength) {
		if (s.length() < maxLength) {
			return s;
		} else {
			return s.substring(0, maxLength);
		}
	}

	private static void send(String timerName, Double time, String thread, String info) throws Exception {
		String[] dataForSend = {
				cut(thread, 32),
				source,
				cut(timerName, 48),
				Double.toString(time),
				cut(info, 32),
				key
		};
		byte[] buffer = String.format(null, "%s|%s|%s|%s|%s|%s", (Object[]) dataForSend).getBytes();
		try {
			socket.send(new DatagramPacket(buffer, buffer.length, IPAddress, port));
		} catch (IOException e) {
			throw new Exception("IOException while sending.");
		}
	}
}
