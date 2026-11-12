package com.mygo.music;

import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.security.MessageDigest;

/** Keep an installed source's account separate from another repository with the same manifest id. */
final class PluginIdentity {
    static String instance(File directory, JSONObject manifest) throws Exception {
        File origin=new File(directory,".origin.json");
        if(origin.isFile()){
            JSONObject info=new JSONObject(read(origin));
            String value=info.optString("instanceId");
            if(value.matches("[A-Za-z0-9][A-Za-z0-9_.-]*"))return value;
        }
        return manifest.getString("id");
    }
    static String read(File file)throws Exception{try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);return out.toString("UTF-8");}}
    static String separate(String declared,String repository)throws Exception{
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(repository.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        StringBuilder suffix=new StringBuilder();for(int i=0;i<8;i++)suffix.append(String.format(java.util.Locale.ROOT,"%02x",hash[i]));
        return declared+"-"+suffix;
    }
}
