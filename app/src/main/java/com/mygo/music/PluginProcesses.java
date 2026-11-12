package com.mygo.music;

import android.content.Context;
import android.os.Build;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

/** Real Android subprocesses, confined to the manifest grant and the app's own UID. */
final class PluginProcesses implements Closeable {
    private final Context context;
    private final File directory;
    private final String id;
    private final List<String> rules=new ArrayList<>();
    private final LinkedHashMap<Long,Child> children=new LinkedHashMap<>();
    private boolean closed;
    PluginProcesses(Context context,File directory,String id,Object declared){this.context=context;this.directory=directory;this.id=id;if(Boolean.TRUE.equals(declared))rules.add("*");if(declared instanceof JSONArray){JSONArray list=(JSONArray)declared;for(int i=0;i<list.length();i++)rules.add(list.optString(i));}}
    boolean declared(){return !rules.isEmpty();}
    synchronized JSONObject spawn(String command,JSONArray args)throws Exception {
        if(closed)throw new IOException("插件辅助程序环境已关闭");
        if(!context.getSharedPreferences("library",0).getBoolean("plugin_process_"+id,false))throw new IOException("请先在插件权限中允许启动辅助程序 (allow process)");
        if(command.isEmpty()||command.indexOf('\0')>=0||!allowed(command))throw new IOException("插件未声明此辅助程序："+command);
        int active=0;for(Child child:children.values())if(child.alive())active++;if(active>=4)throw new IOException("每个插件最多同时启动 4 个辅助程序");
        if(args.length()>256)throw new IOException("辅助程序参数过多");List<String> argv=new ArrayList<>();File launcher=new File(context.getApplicationInfo().nativeLibraryDir,"libmygo-launcher.so");if(!launcher.isFile())throw new IOException("当前安装包缺少 Android 辅助程序启动器");argv.add(launcher.getAbsolutePath());argv.add(resolve(command).getAbsolutePath());int length=0;for(int i=0;i<args.length();i++){String arg=args.getString(i);if(arg.indexOf('\0')>=0||((length+=arg.length())>65536))throw new IOException("无效的辅助程序参数");argv.add(arg);}
        Process process;try{process=new ProcessBuilder(argv).directory(directory).start();}catch(IOException e){throw new IOException("无法启动辅助程序，需要可在 Android 上运行的版本："+command+"；"+e.getMessage());}
        long pid;try{pid=pid(process);}catch(Exception e){process.destroy();throw e;}
        Child child=new Child(process);children.put(pid,child);Iterator<Map.Entry<Long,Child>> it=children.entrySet().iterator();while(children.size()>16&&it.hasNext()){Map.Entry<Long,Child> entry=it.next();if(!entry.getValue().alive()&&entry.getKey()!=pid){entry.getValue().close();it.remove();}}
        return new JSONObject().put("pid",pid);
    }
    private boolean allowed(String command)throws IOException {
        boolean path=command.contains("/");String requested=path?absolute(command):command;
        for(String rule:rules){if(rule.equals("*"))return true;boolean rulePath=rule.contains("/");if(path!=rulePath)continue;if(glob(requested,rulePath?absolute(rule):rule))return true;}return false;
    }
    private String absolute(String value)throws IOException {File file=new File(value);if(!file.isAbsolute()){file=new File(directory,value);if(!file.getCanonicalPath().startsWith(directory.getCanonicalPath()+File.separator))throw new IOException("辅助程序路径越界");}return file.getCanonicalPath();}
    private File resolve(String command)throws IOException {
        if(command.equals("osascript")||command.endsWith("/osascript"))throw new IOException("此插件通过 macOS Music.app 播放，当前播放方式不支持 Android；请改用提供 Android 播放方式的插件");
        if(command.toLowerCase(Locale.ROOT).endsWith(".exe"))throw new IOException("此插件请求启动 Windows 辅助程序，Android 无法运行："+command);
        if(command.contains("/")){File file=new File(absolute(command));if(file.isFile()&&file.getCanonicalPath().startsWith(directory.getCanonicalPath()+File.separator))file.setExecutable(true,true);return file;}
        File bundled=new File(context.getApplicationInfo().nativeLibraryDir,"lib"+command+".so");if(bundled.isFile())return bundled;
        for(String path:new String[]{"/system/bin","/system/xbin","/vendor/bin"}){File file=new File(path,command);if(file.isFile())return file;}
        throw new IOException("未找到 Android 辅助程序："+command);
    }
    static boolean glob(String value,String rule){StringBuilder regex=new StringBuilder("^");for(int i=0;i<rule.length();i++){char ch=rule.charAt(i);regex.append(ch=='*'?"[^/]*":Pattern.quote(String.valueOf(ch)));}return value.matches(regex.append('$').toString());}
    private static long pid(Process process)throws Exception {
        // Android's public Process.pid() is unavailable on older SDKs. AOSP's public
        // UNIXProcess.toString() includes the actual child PID, without hidden APIs.
        Matcher match=Pattern.compile("(?:pid=|pid\\s+)(\\d+)").matcher(process.toString());if(match.find())return Long.parseLong(match.group(1));
        try{return ((Number)Process.class.getMethod("pid").invoke(process)).longValue();}catch(Exception e){throw new IOException("当前系统无法取得辅助程序进程标识");}
    }
    private synchronized Child child(long pid)throws IOException {Child child=children.get(pid);if(closed||child==null)throw new IOException("辅助程序已关闭或不属于此插件");return child;}
    void write(long pid,String text)throws IOException {if(text.length()>65536)throw new IOException("单次辅助程序输入过长");child(pid).write(text);}
    void closeStdin(long pid)throws IOException {child(pid).closeStdin();}
    String readLine(long pid)throws IOException {return child(pid).readLine();}
    int waitFor(long pid)throws Exception {Child child=child(pid);int code=child.process.waitFor();child.stderrThread.join(1000);return code;}
    String errors(long pid)throws IOException {return child(pid).errors();}
    void kill(long pid)throws IOException {child(pid).close();}
    synchronized int activeCount(){int count=0;for(Child child:children.values())if(child.alive())count++;return count;}
    @Override public synchronized void close(){closed=true;for(Child child:children.values())child.close();children.clear();}
    private static final class Child implements Closeable {
        final Process process;final Reader stdout;final OutputStream stdin;final Object inputLock=new Object(),outputLock=new Object();final StringBuilder stderr=new StringBuilder();final Thread stderrThread;
        Child(Process process){this.process=process;stdout=new InputStreamReader(process.getInputStream(),java.nio.charset.StandardCharsets.UTF_8);stdin=process.getOutputStream();stderrThread=new Thread(()->{try(Reader reader=new InputStreamReader(process.getErrorStream(),java.nio.charset.StandardCharsets.UTF_8)){char[] block=new char[2048];int n;while((n=reader.read(block))!=-1)synchronized(stderr){stderr.append(block,0,n);if(stderr.length()>65536)stderr.delete(0,stderr.length()-65536);}}catch(IOException ignored){}},"mygo-plugin-stderr");stderrThread.setDaemon(true);stderrThread.start();}
        boolean alive(){try{process.exitValue();return false;}catch(IllegalThreadStateException e){return true;}}
        void write(String value)throws IOException {synchronized(inputLock){stdin.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));stdin.flush();}}
        void closeStdin()throws IOException {synchronized(inputLock){stdin.close();}}
        String readLine()throws IOException {synchronized(outputLock){StringBuilder line=new StringBuilder();int ch;while((ch=stdout.read())!=-1){if(ch=='\n')break;if(line.length()>=1024*1024){close();throw new IOException("辅助程序输出行超过 1 MB");}line.append((char)ch);}if(ch==-1&&line.length()==0)return null;if(line.length()>0&&line.charAt(line.length()-1)=='\r')line.setLength(line.length()-1);return line.toString();}}
        String errors(){synchronized(stderr){return stderr.toString();}}
        @Override public void close(){try{android.system.Os.kill(-(int)pid(process),android.system.OsConstants.SIGKILL);}catch(Exception ignored){}if(Build.VERSION.SDK_INT>=26)process.destroyForcibly();else process.destroy();Thread cleanup=new Thread(()->{try{process.getInputStream().close();}catch(IOException ignored){}try{process.getOutputStream().close();}catch(IOException ignored){}try{process.getErrorStream().close();}catch(IOException ignored){}},"mygo-plugin-process-cleanup");cleanup.setDaemon(true);cleanup.start();}
    }
}
