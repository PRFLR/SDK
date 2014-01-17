using System;
using System.Collections.Generic;
using System.Text;
using System.Threading.Tasks;
using System.Threading;
class test
{
    static void Main(string[] args)
    {
        try
        {
            PRFLR.init("C#Example", "apiKey");
            PRFLR.setOverflowCounter(50);
            PRFLR.begin("mainTest");
            for (int i = 0; i < 10; i++)
            {
                PRFLR.begin("test" + i);
                Thread.Sleep(50);
                PRFLR.end("test" + i, "test " + i + " ended!");
            }
            PRFLR.end("mainTest", "good!");
        }
        catch (Exception e)
        {
            Console.WriteLine("{0} Exception caught.", e);
        }
    }
}
