#define PRFLR_TEST

/*
 * linux:
		g++ -Wall -std=c++0x test.cpp -lpthread -lrt && ./a.out
	 mingw:
		g++ -Wall -std=c++0x test.cpp -lpthread -lws2_32 && a.exe
*/

#include <vector>
#include <thread>
#include <sstream>
#include <unistd.h>
#include <atomic>
#include "prflr.hpp"

using std::string;
using std::ostringstream;

int nn;

void main2()
{
	PRFLR::init("testApp", "PRFLR-CPP-TEST");
	PRFLR::begin("mongoDB.save total");
	std::vector<std::thread> threads;
	for(int i = 0; i < 10; i++)
	{
		threads.push_back(std::thread([i]()
		{
			ostringstream	timerName,info;		
			timerName << "mongoDB.save step-" << i;
			PRFLR::begin(timerName.str());
#if 1
#ifdef _WIN32
			Sleep(1234);
#else
			//sleep(1);
			struct timespec t;
			t.tv_sec = 1;
			t.tv_nsec = 1000000*234;			
			nanosleep(&t,NULL);
#endif
#else
			for(int n=0;n<1000000;n++) nn++;
#endif
			info << "step " << i;
			PRFLR::end(timerName.str(), info.str());
		}));
	}
	for(auto& thread : threads)
	{
		thread.join();
	}
	PRFLR::end("mongoDB.save total","Done");
}

int main()
{
#ifdef _WIN32
	WSADATA wsaData;
	(void)WSAStartup(MAKEWORD(2,0), &wsaData);
#endif
	try
	{
		main2();
	}
	catch(PRFLRException x)
	{
		printf("PRFLRException: %s\n",x.what());
		return 1;
	}
	return 0;
}
