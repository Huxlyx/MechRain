// See https://aka.ms/new-console-template for more information

using MechRain;

Console.WriteLine("Hello, World!");

MechRainServer mrs = new();

try
{
    mrs.RunServer();
    Console.WriteLine("Terminated");
}
catch (Exception e)
{
    Console.WriteLine(e.Message);
}