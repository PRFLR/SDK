using System;
using System.Threading;
using System.Collections.Generic;
using System.Collections.Concurrent;
using System.Text;
using System.Threading.Tasks;
using System.Net.Sockets;
using System.Net;
static class PRFLR
{
    private static String source;
    private static String apikey;
    private static ConcurrentDictionary<String, long> timers;
    private static UdpClient udp;
    private static Int32 overflowCounter = 100;
    private static Int32 counter = 0;
    public static void init(String source, String apikey)
    {
        if (source == "")
        {
            throw new Exception("Unknown source");
        }
        PRFLR.source = cut(source, 32);
        if (apikey == "")
        {
            throw new Exception("Unknown apikey");
        }
        PRFLR.apikey = cut(apikey, 32);
        PRFLR.timers = new ConcurrentDictionary<string, long>();
        try
        {
            PRFLR.udp = new UdpClient("prflr.org", 4000);
        }
        catch (SocketException e)
        {
            throw new Exception("Can't open socket: " + e.ToString());
        }
    }
    public static void setOverflowCounter(int value)
    {
        PRFLR.overflowCounter = value;
    }
    public static void begin(String timerName)
    {
        PRFLR.counter++;
        long thread = Thread.CurrentThread.ManagedThreadId;
        if (PRFLR.counter > PRFLR.overflowCounter)
        {
            PRFLR.timers.Clear();
            PRFLR.counter = 0;
        }
        PRFLR.timers[thread.ToString() + timerName] = DateTime.Now.Ticks / TimeSpan.TicksPerMillisecond;
    }
    public static Boolean end(String timerName, String info)
    {
        long prevTime;
        long thread = Thread.CurrentThread.ManagedThreadId;
        if (!PRFLR.timers.TryGetValue(thread.ToString() + timerName, out prevTime))
        {
            return false;
        }
        PRFLR.counter--;
        double diffTime = Math.Round((double)(DateTime.Now.Ticks / TimeSpan.TicksPerMillisecond - prevTime), 3);
        PRFLR.send(timerName, diffTime, thread, info);
        return true;
    }
    private static String cut(String s, Int32 maxLength)
    {
        if (s.Length < maxLength)
            return s;
        else
            return s.Substring(0, maxLength);
    }
    private static void send(String timerName, double diffTime, long thread, String info)
    {
        String[] toSend = {
                             cut(thread.ToString(), 32),
                             PRFLR.source,
                             cut(timerName, 48),
                             diffTime.ToString(System.Globalization.CultureInfo.InvariantCulture),
                             cut(info, 32),
                             PRFLR.apikey
                             };
        String sendString = String.Join("|", toSend);
        byte[] tempArr = System.Text.Encoding.UTF8.GetBytes(sendString);
        byte[] t = { 250 };
        PRFLR.udp.Send(tempArr, tempArr.Length);
    }
}