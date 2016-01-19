import java.io.IOException;
import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PRFLR {
	/**
	 * prflr://${key}@${host}:${port}
	 */
	public static final Pattern API_KEY = Pattern.compile("prflr://([\\w]+)@([\\w.]+):(\\d+)");

	public static String source = null;
	public static String key = null;
	public static int port;
	public static int overflowCount = 100;

	private static InetAddress IPAddress;
	private static DatagramSocket socket;
	private static Map<String, Long> timers;
	private static AtomicInteger counter = new AtomicInteger(0);

	private PRFLR() {

	}

	/**
	 * @param source Source of events
	 * @param apiKey API key for account in format {@code prflr://${key}@${host}:${port}}
	 * @throws Exception
	 */
	public static void init(String source, String apiKey) throws Exception {
		if (apiKey == null) {
			throw new Exception("Unknown apikey.");
		}
		Matcher apiKeyMatcher = API_KEY.matcher(apiKey);
		if (!apiKeyMatcher.matches()) {
			throw new Exception("Unknown API key format: " + apiKey);
		}
		PRFLR.key = apiKeyMatcher.group(1);
		String host = apiKeyMatcher.group(2);
		PRFLR.port = Integer.parseInt(apiKeyMatcher.group(3));

		try {
			IPAddress = InetAddress.getByName(host);
		} catch (UnknownHostException e) {
			throw new Exception("Host unknown: " + host);
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
		PRFLR.timers = new ConcurrentHashMap<String, Long>();
	}

	private static void cleanTimers() {
		PRFLR.timers.clear();
	}

	private static String threadId() {
		return Long.toString(Thread.currentThread().getId());
	}

	private static String timerId(String timerName) {
		return threadId() + timerName;
	}

	public static boolean begin(String timerName) {
		int val = counter.incrementAndGet();
		if (val > overflowCount) {
			cleanTimers();
			counter.set(0);
		}
		timers.put(timerId(timerName), System.nanoTime());
		return true;
	}

	public static boolean end(String timerName, String info) throws Exception {
		Long startTime = timers.remove(timerId(timerName));
		if (startTime == null) {
			return false;
		}
		String thread = threadId();
		counter.decrementAndGet();
		long now = System.nanoTime();
		long precision = (long) Math.pow(10, 3);
		double diffTime = (double) Math.round((double) (now - startTime) / 1000000 * precision) / precision;
		send(timerName, diffTime, thread, info);
		return true;
	}

	private static String cut(String s, int maxLength) {
		if (s.length() < maxLength) {
			return s;
		} else {
			return s.substring(0, maxLength);
		}
	}

	private static void send(String timerName, double time, String thread, String info) throws Exception {
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
