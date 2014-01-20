#ifndef PRFLR_HPP
#define PRFLR_HPP

/*
 *  HOW TO USE
 *
 * // configure profiler
 * // set  profiler server:port  and  set source for timers
 * PRFLR::init("192.168.1.45-testApp", "yourApiKey");
 *
 *
 * //start timer
 * PRFLR::begin("mongoDB.save");
 *
 * //some code
 * sleep(1);
 *
 * //stop timer
 * PRFLR::end("mongoDB.save");
 *
*/

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifdef __unix__
#include <unistd.h>
#include <sys/socket.h>
#include <netdb.h>
#ifndef SOCKET_ERROR
#define SOCKET_ERROR -1
#endif
#endif

#ifdef _WIN32
#include <winsock2.h>
#include <windows.h>
#endif

#include <sys/timeb.h>

#include <string>
#include <map>
#include <exception>
#include <sstream>
#include <mutex>

class PRFLRException : public std::exception
{
	std::string reason_;
	public:
	PRFLRException(const std::string &reason):reason_(reason)
	{
	}
	virtual ~PRFLRException() throw()
	{
	}
	virtual const char* what() const throw()
	{
		return reason_.c_str();
	}
};

#ifdef __linux
#include <syscall.h>
#else
#include <pthread.h>
#endif

struct PRFLRMisc
{
#ifdef _WIN32
	static double ms() // millisec
	{
		//return GetTickCount();
		SYSTEMTIME t;
		GetSystemTime(&t);
		//return ...+1000.*t.wSecond+t.wMilliseconds;
		FILETIME f;
		SystemTimeToFileTime(&t,&f);
		ULARGE_INTEGER d;
		d.LowPart = f.dwLowDateTime;
		d.HighPart = f.dwHighDateTime;
		return (double)d.QuadPart/1.e4;
	}
#elif defined(__linux)
	static double ms()
	{
		struct timespec t;
		//clock_gettime(_POSIX_THREAD_CPUTIME,&t);
		clock_gettime(_POSIX_MONOTONIC_CLOCK,&t);
		return (double)t.tv_nsec/1000000.+1000.*t.tv_sec;
	}
#else
	static double ms()
	{
#if 0
		time_t t;
		time(&t);
		return 1000.*t;
#endif
		struct timeb b;
		ftime(&b);
		return 1.e3*b.time + b.millitm;
	}
#endif
	static inline std::string threadid()
	{
		std::ostringstream ret;
#ifdef __linux
		pid_t x = (pid_t)syscall(SYS_gettid);
		ret << x;
#else
		unsigned long x = (unsigned long)pthread_self();
		ret << x;
#endif
		return ret.str();
	}
};

class PRFLRSender // static
{
	typedef std::string string;
  std::mutex mutex;
	std::map<string,double> timers;
	int delayedSend;
	sockaddr ip;
	public:
	int socket;
	string source;
	string apikey;
	public:
	PRFLRSender(const char *server,int port):delayedSend(false),socket(-1)
	{
		ip = iresolve(server,port);
	}
	bool bad()
	{
		if(ip.sa_family==0) return true; // can't resolve
		return false;
	}
	virtual ~PRFLRSender()
	{
		if(socket!=-1) close(socket);
	}
	void Begin(const string& timer)
	{
		string tid(PRFLRMisc::threadid());
		string id(tid + "-" + timer);
		double t1 = PRFLRMisc::ms();
		mutex.lock();
		timers[id] = t1;
		mutex.unlock();
	}
	bool End(const string& timer,const string& info="")
	{
		double t2 = PRFLRMisc::ms();
		string tid(PRFLRMisc::threadid());
		string id(tid + "-" + timer);
		double t1 = 0;
		mutex.lock();
		std::map<string,double>::iterator i = timers.find(id);
		if(i!=timers.end())
		{
			t1 = i->second;
			timers.erase(i);
		}
		mutex.unlock();
		if(t1==0) return false;
		double tt = t2 - t1;
		send(tid, timer, tt, info); // atomic
		return true;
	}
	private:
	void send(const string &tid,const string& timer,double time,const string& info = "")
	{
		char ntime[100];
		snprintf(ntime,sizeof(ntime),"%.3f",time);
		string message = 
			substr(tid,32)
			+ substr(source,32)
			+ substr(timer,48)
			+ substr(ntime,16)
			+ substr(info,32)
			+ substr(apikey,32,0);
		if(socket==-1) throw PRFLRException("Socket not exist");
#ifdef PRFLR_TEST
		printf("[%s]\n",message.c_str());
#else
		(void)::sendto(socket,message.c_str(),message.size(),0,&ip,sizeof(ip));
#endif
	}
	static string substr(const string &a,size_t n,char c='|')
	{
		string ret;
		string::const_iterator end = a.end();
		for(string::const_iterator i = a.begin();i!=end;++i)
		{
			if(*i!='|') ret += *i;
		}
		ret = ret.substr(0,n);
		if(c) ret += '|';
		return ret;
	}
	static struct sockaddr iresolve(const char* host,int port)
	{
		sockaddr ret;
		memset(&ret,0,sizeof(ret));
		if(1==1)
		{
			sockaddr_in t;
			int a,b,c,d;
			if(isdigit(host[0]) && sscanf(host, "%d.%d.%d.%d", &a,&b,&c,&d)==4)
			{
				t.sin_addr.s_addr = htonl((a<<24)+(b<<16)+(c<<8)+d);
			}
			else
			{
				struct hostent *h = ::gethostbyname(host);
				if(h==NULL) 
				{
					return ret; // sin_family=0
				}
				t.sin_addr.s_addr = *(u_long*) h->h_addr;
			}
			t.sin_family = AF_INET;
			t.sin_port = htons(port);
			ret = *(sockaddr*)&t;
		}
		// TODO: add INET6
		return ret;
	}
	friend class PRFLR;
};

class PRFLR
{
	typedef std::string string;
	static inline PRFLRSender* &sender()
	{
		static PRFLRSender *ptrfl_org = 0;
		return ptrfl_org;
	}
	public:
	static void init(const char* source,const char* apikey)
	{
		if(sender()!=0)
			throw PRFLRException("PRFLR::init already called.");
		if(!apikey||apikey[0]==0)
			throw PRFLRException("Unknown apikey.");
		if(sender()==0)
			sender() = new PRFLRSender("prflr.org",4000); ///
		if(sender()->bad())
			throw PRFLRException("Unknown host prflr.org.");
		sender()->socket = ::socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
		if(sender()->socket == SOCKET_ERROR) throw PRFLRException("socket");
		sender()->apikey = apikey;
		sender()->source = source;
	}
	static inline void begin(const string& timer)
	{
		if(sender()) sender()->Begin(timer);
	}
	static inline bool end(const string& timer, const string& info="")
	{
		if(sender()) return sender()->End(timer,info);
		return false;
	}
};

#endif
