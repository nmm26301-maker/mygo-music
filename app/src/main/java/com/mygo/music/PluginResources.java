package com.mygo.music;

import android.net.Uri;
import android.webkit.WebResourceResponse;
import java.io.*;
import java.util.*;

/** Same-origin ES modules, including relative imports, cycles and import.meta.url. */
final class PluginResources {
    static final String ORIGIN="https://plugin.invalid", PREFIX="/plugin/";
    private final File root;private final PluginAdapters adapters;private android.content.Context context;private PluginTypeScript typescript;
    private volatile String document="";
    void document(String html){document=html;}
    PluginResources(File root){this.root=root;this.adapters=new PluginAdapters(root);}
    PluginResources(File root,android.content.Context context,PluginTypeScript typescript){this.root=root;this.adapters=new PluginAdapters(root);this.context=context;this.typescript=typescript;}
    String moduleURL(String main)throws IOException {file(Uri.parse(ORIGIN+PREFIX+main));return ORIGIN+PREFIX+main;}
    boolean owns(Uri uri){return "https".equals(uri.getScheme())&&"plugin.invalid".equals(uri.getHost())&&uri.getPort()==-1;}
    File file(Uri uri)throws IOException {
        if(!owns(uri)||uri.getPath()==null||!uri.getPath().startsWith(PREFIX))throw new IOException("无效插件资源地址");
        String path=uri.getPath().substring(PREFIX.length());if(path.contains("\\")||path.indexOf('\0')>=0)throw new IOException("无效插件资源路径");
        File file=new File(root,path);if(!file.getCanonicalPath().startsWith(root.getCanonicalPath()+File.separator))throw new IOException("插件资源路径越界");
        if(!file.isFile()){
            String[] candidates=path.endsWith(".js")?new String[]{path.substring(0,path.length()-3)+".ts"}:new String[]{path+".js",path+".ts",path+"/index.js",path+"/index.ts"};
            for(String candidate:candidates){File alternative=new File(root,candidate);if(alternative.getCanonicalPath().startsWith(root.getCanonicalPath()+File.separator)&&alternative.isFile()){file=alternative;break;}}
        }
        if(!file.isFile())throw new FileNotFoundException("插件文件不存在："+path);return file;
    }
    String mime(File file){String name=file.getName().toLowerCase(Locale.ROOT);if(name.endsWith(".js")||name.endsWith(".mjs"))return "application/javascript";if(name.endsWith(".json"))return "application/json";if(name.endsWith(".wasm"))return "application/wasm";if(name.endsWith(".svg"))return "image/svg+xml";if(name.endsWith(".png"))return "image/png";if(name.endsWith(".jpg")||name.endsWith(".jpeg"))return "image/jpeg";return "application/octet-stream";}
    WebResourceResponse intercept(Uri uri){try{if(context!=null&&owns(uri)&&"/.host/babel.js".equals(uri.getPath()))return new WebResourceResponse("application/javascript","UTF-8",context.getAssets().open("vendor/babel.min.js"));if(owns(uri)&&"/".equals(uri.getPath()))return new WebResourceResponse("text/html","UTF-8",200,"OK",Collections.singletonMap("Cache-Control","no-store"),new ByteArrayInputStream(document.getBytes("UTF-8")));File file=file(uri);if(typescript!=null)file=typescript.module(file);Map<String,String> headers=new HashMap<>();headers.put("Cache-Control","no-store");headers.put("Access-Control-Allow-Origin",ORIGIN);headers.put("X-Content-Type-Options","nosniff");return new WebResourceResponse(mime(file),"UTF-8",200,"OK",headers,adapters.open(file));}catch(IOException e){return new WebResourceResponse("text/plain","UTF-8",404,"Not Found",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}}
}
