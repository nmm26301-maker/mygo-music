package com.mygo.music;

import org.json.*;
import java.util.*;

/** All required groups must have one nonempty cookie. Names alone never complete a login. */
final class BrowserCookieGate {
    private final ArrayList<ArrayList<String>> groups=new ArrayList<>();
    BrowserCookieGate(JSONObject options)throws Exception{
        JSONArray all=options.optJSONArray("until"),any=options.optJSONArray("untilAny");
        if(all!=null)for(int i=0;i<all.length();i++){ArrayList<String> group=new ArrayList<>();group.add(name(all.getString(i)));groups.add(group);}
        if(any!=null)for(int i=0;i<any.length();i++){JSONArray aliases=any.getJSONArray(i);if(aliases.length()==0||aliases.length()>24)throw new IllegalArgumentException("登录Cookie别名分组无效");ArrayList<String> group=new ArrayList<>();for(int j=0;j<aliases.length();j++)group.add(name(aliases.getString(j)));groups.add(group);}
        if(groups.isEmpty()||groups.size()>32)throw new IllegalArgumentException("插件未指定有效的登录Cookie条件");
    }
    private static String name(String value){if(!value.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+"))throw new IllegalArgumentException("登录Cookie名称无效");return value;}
    boolean ready(String header){if(header==null)return false;HashSet<String> present=new HashSet<>();for(String pair:header.split(";")){int at=pair.indexOf('=');if(at>0){String value=pair.substring(at+1).trim();if(!value.isEmpty()&&!value.matches("(?i)null|undefined|deleted"))present.add(pair.substring(0,at).trim());}}
        for(ArrayList<String> group:groups){boolean found=false;for(String name:group)if(present.contains(name)){found=true;break;}if(!found)return false;}return true;
    }
}
