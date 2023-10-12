using System.Net;
using System.Net.Sockets;
using System.Text;
using InfluxDB.LineProtocol.Client;
using InfluxDB.LineProtocol.Payload;

namespace MechRain;

public class MechRainServer
{
    private const string MECH_RAIN_SOIL_HUMIDITY_MEASUREMENT = "SoilHumidity";

    private static readonly LineProtocolClient Client =
        new LineProtocolClient(new("http://192.168.0.67:8086"), "MechRain", "MechRain", "MechRain");

    private bool runServer = true;

    public static string GetLocalIPAddress()
    {
        var host = Dns.GetHostEntry(Dns.GetHostName());
        foreach (var ip in host.AddressList)
        {
            if (ip.AddressFamily == AddressFamily.InterNetwork)
            {
                Console.WriteLine("Got local IP: " + ip);
                return ip.ToString();
            }
        }

        InfluxLogger.error("No network adapters with an IPv4 address in the system!");
        throw new Exception("No network adapters with an IPv4 address in the system!");
    }

    public async void RunServer()
    {
        IPAddress ipAddr = IPAddress.Parse("192.168.0.67");
        IPEndPoint ipEndPoint = new(ipAddr, 7777);

        Console.WriteLine("Starting server");
        Socket listener = new(AddressFamily.InterNetwork, SocketType.Stream, ProtocolType.Tcp);
        listener.Bind(ipEndPoint);
        listener.Listen(100);

        Console.WriteLine("Waiting for connection");

        while (runServer)
        {
            Socket handler = listener.Accept();
            byte[] buffer = new byte[1_024];
            int idx = 0;
            NetworkStream ns = new(handler);

            Console.WriteLine("Got connection");

            while (handler.Connected)
            {
                int current = ns.ReadByte();
                if (current == -1)
                {
                    break;
                }

                if (current == '#')
                {
                    try
                    {
                        var str = Encoding.UTF8.GetString(buffer, 0, idx);
                        HandleMessage(str);
                    }
                    catch (Exception e)
                    {
                        Console.WriteLine(e.ToString());
                    }

                    idx = 0;
                }
                else
                {
                    buffer[idx++] = (byte)current;
                }
            }
        }

        Console.WriteLine("Handler disconnected");
    }

    private void HandleMessage(string message)
    {
        var msgParts = message.Split(';');
        if (!int.TryParse(msgParts[1], out int val))
        {
            Console.WriteLine("Could not parse " + message);
            InfluxLogger.warn("Could not parse " + message);
            ;
        }

        LineProtocolPayload payload = new();

        switch (msgParts[0])
        {
            case "M_HumPercent":
                payload.Add(new(MECH_RAIN_SOIL_HUMIDITY_MEASUREMENT, new Dictionary<string, object>()
                {
                    { "Humid%", val }
                }));
                break;
            case "M_HumExact":
                payload.Add(new(MECH_RAIN_SOIL_HUMIDITY_MEASUREMENT, new Dictionary<string, object>()
                {
                    { "HumidExact", val }
                }));
                break;
            default:
                Console.WriteLine("Could not route message " + message);
                InfluxLogger.warn("Could not route message " + message);
                return;
        }

        var result = Client.WriteAsync(payload);

        if (!result.Result.Success)
        {
            Console.WriteLine(result.Result.ErrorMessage);
        }
    }
}