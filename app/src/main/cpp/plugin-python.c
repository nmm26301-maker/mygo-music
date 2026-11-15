#include <stdio.h>
#include <unistd.h>
#include <string.h>
#include <dlfcn.h>
#include <limits.h>
int main(int argc,char **argv){
 char path[PATH_MAX];ssize_t n=readlink("/proc/self/exe",path,sizeof(path)-32);
 if(n<=0)return 126;path[n]=0;char *slash=strrchr(path,'/');if(!slash)return 126;
 strcpy(slash+1,"libpython3.12.so");void *library=dlopen(path,RTLD_NOW|RTLD_GLOBAL);
 if(!library){fprintf(stderr,"Python runtime: %s\n",dlerror());return 126;}
 int (*run)(int,char **)=dlsym(library,"Py_BytesMain");if(!run)return 126;return run(argc,argv);
}
