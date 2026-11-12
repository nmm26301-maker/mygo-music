package com.mygo.music;

import android.app.Activity;
import android.os.*;
import android.text.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Debounced provider suggestions never survive a query, source, or Activity change. */
final class SourceSuggestions implements TextWatcher {
    interface Host {PluginManager.PluginInfo source();void search(String query);}
    private final Activity activity;private final PluginManager manager;private final AutoCompleteTextView input;private final Host host;private final Handler handler=new Handler(Looper.getMainLooper());private long generation;private boolean closed,selecting;
    SourceSuggestions(Activity activity,PluginManager manager,AutoCompleteTextView input,Host host){this.activity=activity;this.manager=manager;this.input=input;this.host=host;input.setThreshold(1);input.addTextChangedListener(this);input.setOnItemClickListener((parent,view,position,id)->{selecting=true;String value=String.valueOf(parent.getItemAtPosition(position));changed();input.setText(value,false);input.setSelection(value.length());selecting=false;host.search(value);});}
    void changed(){generation++;handler.removeCallbacksAndMessages(null);input.dismissDropDown();input.setAdapter(new ArrayAdapter<String>(activity,android.R.layout.simple_dropdown_item_1line,new ArrayList<>()));}
    void close(){closed=true;changed();input.removeTextChangedListener(this);}
    public void beforeTextChanged(CharSequence text,int start,int count,int after){}
    public void onTextChanged(CharSequence text,int start,int before,int count){}
    public void afterTextChanged(Editable text){if(selecting||closed)return;changed();String query=text.toString().trim();PluginManager.PluginInfo source=host.source();if(query.isEmpty()||source==null||!source.enabled||!source.supports("suggest")||!manager.networkAllowed(source.id))return;long ticket=generation;
        handler.postDelayed(()->manager.export(source.id,"suggest",SourceBrowser.arg("query",query),(ok,raw)->{PluginManager.PluginInfo selected=host.source();if(closed||activity.isDestroyed()||ticket!=generation||selected==null||!source.id.equals(selected.id)||!query.equals(input.getText().toString().trim()))return;if(!ok)return;try{JSONArray items=new JSONArray(raw);LinkedHashSet<String> values=new LinkedHashSet<>();for(int i=0;i<items.length()&&values.size()<12;i++){String value=items.optString(i).trim();if(!value.isEmpty()&&value.length()<=256)values.add(value);}input.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_dropdown_item_1line,new ArrayList<>(values)));if(!values.isEmpty()&&input.hasFocus())input.showDropDown();}catch(Exception ignored){}}),300);
    }
}
