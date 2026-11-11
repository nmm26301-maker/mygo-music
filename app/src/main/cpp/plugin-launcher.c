#include <errno.h>
#include <stdio.h>
#include <unistd.h>

/* Packaged Android launcher: isolate a permitted helper in its own session
 * so plugin shutdown can terminate its complete process group. */
int main(int argc, char **argv) {
    if (argc < 2) return 126;
    if (setsid() == -1) { perror("plugin setsid"); return 126; }
    execv(argv[1], argv + 1);
    int error = errno;
    perror("plugin exec");
    return error == ENOENT ? 127 : 126;
}
