package com.mygo.music;

import android.app.Application;
import java.net.*;
import java.util.*;

/** Source sessions are explicit; native media must not create a shared automatic cookie jar. */
public final class MyGoApplication extends Application {
    @Override public void onCreate(){
        super.onCreate();
        // WebView sign-in has its own android.webkit.CookieManager. This affects only java.net.
        CookieHandler.setDefault(new CookieHandler(){
            @Override public Map<String,List<String>> get(URI uri,Map<String,List<String>> headers){return Collections.emptyMap();}
            @Override public void put(URI uri,Map<String,List<String>> headers){}
        });
    }
}
