package com.mygo.music;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Extracts a complete plugin without allowing archives to escape the staging directory. */
final class PluginArchive {
    static final long MAX_DOWNLOAD=32L*1024*1024, MAX_EXPANDED=128L*1024*1024;
    static File extract(InputStream source,File destination)throws IOException {
        if(!destination.exists()&&!destination.mkdirs())throw new IOException("无法创建插件暂存目录");
        String prefix=destination.getCanonicalPath()+File.separator;
        Set<String> seen=new HashSet<>();long total=0;int count=0;
        try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(source))){ZipEntry entry;byte[] buffer=new byte[8192];
            while((entry=zip.getNextEntry())!=null){
                String name=entry.getName();if(++count>4096)throw new IOException("插件文件数量超过限制");
                if(name.startsWith("/")||name.contains("\\")||name.indexOf('\0')>=0)throw new IOException("插件压缩包包含无效路径");
                File file=new File(destination,name);String path=file.getCanonicalPath();
                if(!path.startsWith(prefix)||!seen.add(path))throw new IOException("插件压缩包路径越界或重复");
                if(entry.isDirectory()){if(!file.isDirectory()&&!file.mkdirs())throw new IOException("无法创建插件子目录");continue;}
                File parent=file.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("无法创建插件子目录");
                long size=0;try(OutputStream out=new FileOutputStream(file)){int n;while((n=zip.read(buffer))!=-1){size+=n;total+=n;if(size>16L*1024*1024||total>MAX_EXPANDED)throw new IOException("插件解压大小超过限制");out.write(buffer,0,n);}}
                zip.closeEntry();
            }
        }
        if(count==0)throw new IOException("文件不是有效的 ZIP 插件包");
        File root=findRoot(destination,0);if(root==null)throw new IOException("插件包中没有 plugin.json");return root;
    }
    private static File findRoot(File dir,int depth)throws IOException {
        if(new File(dir,"plugin.json").isFile())return dir;if(depth>=3)return null;
        File found=null;File[] children=dir.listFiles();if(children!=null)for(File child:children)if(child.isDirectory()&&!child.getName().startsWith(".")){
            File candidate=findRoot(child,depth+1);if(candidate!=null){if(found!=null)throw new IOException("压缩包中有多个插件，请分别打包安装");found=candidate;}
        }return found;
    }
    static void copy(File source,File destination)throws IOException {
        if(source.isDirectory()){if(!destination.isDirectory()&&!destination.mkdirs())throw new IOException("无法创建插件目录");File[] files=source.listFiles();if(files!=null)for(File file:files)copy(file,new File(destination,file.getName()));}
        else try(InputStream in=new FileInputStream(source);OutputStream out=new FileOutputStream(destination)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
    }
    static void delete(File file){if(file==null)return;if(file.isDirectory()){File[] files=file.listFiles();if(files!=null)for(File child:files)delete(child);}file.delete();}
}
