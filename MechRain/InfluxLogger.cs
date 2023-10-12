using InfluxDB.LineProtocol.Client;
using InfluxDB.LineProtocol.Payload;

namespace MechRain;

public class InfluxLogger
{
    private static readonly LineProtocolClient Client =
        new LineProtocolClient(new("http://192.168.0.67:8086"), "MechRainLog", "MechRain", "MechRain");


    public enum LogLvl
    {
        TRACE = 100,
        
        DEBUG = 300,
        
        INFO = 500,
        
        WARN = 700,
        
        ERROR = 900
    }

    public static void trace(String logMsg)
    {
        doLog(logMsg, 100);
    }

    public static void debug(String logMsg)
    {
        doLog(logMsg, 300);
    }

    public static void info(String logMsg)
    {
        doLog(logMsg, 500);
    }

    public static void warn(String logMsg)
    {    
        doLog(logMsg, 700); 
    }

    public static void error(String logMsg)
    {
        doLog(logMsg, 900);
    }

    private static void doLog(String logMsg, int severity)
    {
        LineProtocolPayload payload = new();
        LineProtocolPoint point = new("LogEntry", new Dictionary<string, object>()
        {
            { "logMsg", logMsg },
            { "severity", severity }
        });
        payload.Add(point);
        var result = Client.WriteAsync(payload);

        if (!result.Result.Success)
        {
            Console.WriteLine(result.Result.ErrorMessage);
        }
    }
}