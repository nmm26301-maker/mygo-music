package com.mygo.music;

import android.content.Context;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Offline Babel transform on demand. Unused test/tool sources are never compiled. */
final class PluginTypeScript implements Closeable {
    interface Compiler {void compile(String name);}
    private final LinkedHashMap<String,File> sources=new LinkedHashMap<>();
    private final Map<String,String> names=new HashMap<>();
    private final ConcurrentHashMap<String,Pending> pending=new ConcurrentHashMap<>();
    private final File cache;private volatile boolean closed;private long sourceBytes,outputBytes;private Compiler compiler;
    private static final class Pending {final CountDownLatch done=new CountDownLatch(1);volatile File file;volatile String error="";}
    PluginTypeScript(Context context,File root)throws Exception{scan(root,root,0);cache=File.createTempFile("plugin-typescript-","",context.getCacheDir());cache.delete();if(!cache.mkdir())throw new IOException("无法创建插件编译缓存");}
    private void scan(File root,File directory,int depth)throws Exception{
        if(depth>20)throw new IOException("插件目录层级过深");File[] files=directory.listFiles();if(files==null)return;Arrays.sort(files,Comparator.comparing(File::getName));
        for(File file:files){if(file.isDirectory()){if(!file.getName().startsWith(".")&&!file.getName().equals("node_modules"))scan(root,file,depth+1);continue;}
            if(!file.getName().endsWith(".ts")||file.getName().endsWith(".d.ts"))continue;
            
            String relative=root.toURI().relativize(file.toURI()).getPath();sources.put(relative,file);names.put(file.getCanonicalPath(),relative);
        }
    }
    boolean needed(){return !sources.isEmpty();}
    void compiler(Compiler compiler){this.compiler=compiler;}
    synchronized String source(String name)throws Exception{if(closed||!sources.containsKey(name)||!pending.containsKey(name))throw new IOException("无效的插件编译请求");return PluginIdentity.read(sources.get(name));}
    synchronized void complete(String name,String code,String error){
        Pending task=pending.get(name);if(task==null||task.done.getCount()==0)return;
        try{
            if(closed)throw new IOException("插件已关闭");if(!error.isEmpty())throw new IOException(error);
            byte[] bytes=code.getBytes("UTF-8");if(bytes.length>16*1024*1024||(outputBytes+=bytes.length)>32*1024*1024)throw new IOException("插件编译结果过大");
            File output=File.createTempFile("module-",".js",cache);try(FileOutputStream stream=new FileOutputStream(output)){stream.write(bytes);}task.file=output;
        }catch(Exception failure){task.error=failure.getMessage();}finally{task.done.countDown();}
    }
    File module(File file)throws IOException{
        if(!file.getName().endsWith(".ts"))return file;String name=names.get(file.getCanonicalPath());if(closed||name==null||compiler==null)throw new IOException("TypeScript 模块无法编译");
        Pending candidate=new Pending(),task=pending.putIfAbsent(name,candidate);if(task==null){task=candidate;synchronized(this){sourceBytes+=file.length();if(pending.size()>256||sourceBytes>16*1024*1024){task.error="TypeScript 插件超过编译大小限制";task.done.countDown();}else compiler.compile(name);}}
        try{if(!task.done.await(30,TimeUnit.SECONDS))throw new IOException("TypeScript 模块编译超时");}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IOException("插件编译已取消");}
        if(task.file==null||closed)throw new IOException("TypeScript 编译失败："+task.error);return task.file;
    }
    public synchronized void close(){closed=true;for(Pending task:pending.values()){task.error="插件已关闭";task.done.countDown();}PluginArchive.delete(cache);}
}
