package com.mygo.music;

import org.json.JSONObject;
import java.io.IOException;

/** Upstream protocol versions are separate from the Android release version. */
final class PluginProtocol {
    static final String VERSION="0.1.24";
    static void validate(JSONObject manifest)throws IOException{
        String minimum=manifest.optString("minAppVersion","");if(minimum.isEmpty())return;
        if(!minimum.matches("v?\\d+\\.\\d+\\.\\d+"))throw new IOException("插件最低协议版本格式无效："+minimum);
        String[] requested=minimum.replaceFirst("^v","").split("\\."),supported=VERSION.split("\\.");
        for(int i=0;i<3;i++){long left;try{left=Long.parseLong(requested[i]);}catch(NumberFormatException error){throw new IOException("插件最低协议版本过高");}long right=Long.parseLong(supported[i]);if(left>right)throw new IOException("插件需要原版协议 "+minimum+"，当前 Android 兼容协议为 "+VERSION+"，请先更新应用");if(left<right)return;}
    }
    static String image(String address,int pixels){if(address==null)return "";String size=String.valueOf(Math.max(40,Math.min(1200,pixels)));return address.replace("{size}",size).replaceAll("(?i)%7bsize%7d",size);}
}
