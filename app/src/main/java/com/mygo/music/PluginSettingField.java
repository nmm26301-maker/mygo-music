package com.mygo.music;

import android.content.Context;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.util.*;

final class PluginSettingField {
    final View view;
    private final ArrayList<String> values=new ArrayList<>();
    PluginSettingField(Context context,JSONObject specification,String current){
        JSONArray options=specification.optJSONArray("options");
        if(options==null||options.length()==0){EditText field=new EditText(context);field.setText(current);field.setHint(specification.optString("help"));view=field;return;}
        ArrayList<String> labels=new ArrayList<>();values.add("");labels.add("使用插件默认值");
        for(int i=0;i<options.length();i++){JSONObject item=options.optJSONObject(i);String value=item==null?options.optString(i):item.optString("value");values.add(value);labels.add(item==null?value:item.optString("label",value));}
        if(!values.contains(current)){values.add(current);labels.add(current);}
        Spinner spinner=new Spinner(context);spinner.setAdapter(new ArrayAdapter<String>(context,android.R.layout.simple_spinner_dropdown_item,labels));spinner.setSelection(values.indexOf(current));view=spinner;
    }
    String value(){return view instanceof EditText?((EditText)view).getText().toString():values.get(((Spinner)view).getSelectedItemPosition());}
}
