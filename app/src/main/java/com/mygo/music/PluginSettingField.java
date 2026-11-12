package com.mygo.music;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.util.*;

final class PluginSettingField {
    interface Commit {void save(String value);}
    final View view;final boolean password;private final ArrayList<String> values=new ArrayList<>();private String saved;private boolean touched,binding;
    PluginSettingField(Context context,JSONObject specification,String current){
        saved=current;String type=specification.optString("type");password=type.equals("password");JSONArray options=specification.optJSONArray("options");
        boolean valid=options!=null&&options.length()>0&&(type.equals("select")||type.isEmpty());HashSet<String> labelsSeen=new HashSet<>(),valuesSeen=new HashSet<>();ArrayList<String> labels=new ArrayList<>();
        if(valid)for(int i=0;i<options.length();i++){JSONObject item=options.optJSONObject(i);String value=item==null?options.optString(i):item.optString("value"),label=item==null?value:item.optString("label",value);if(label.isEmpty()||!labelsSeen.add(label)||!valuesSeen.add(value)){valid=false;break;}values.add(value);labels.add(label);}
        if(valid){if(!values.contains("")){values.add(0,"");labels.add(0,"使用插件默认值");}if(!values.contains(current)){values.add(current);labels.add(current);}Spinner spinner=new Spinner(context);spinner.setAdapter(new ArrayAdapter<>(context,android.R.layout.simple_spinner_dropdown_item,labels));spinner.setSelection(values.indexOf(current));view=spinner;}
        else if(type.equals("boolean")&&(current.isEmpty()||current.equals("true")||current.equals("false"))){Switch control=new Switch(context);control.setChecked(current.equals("true"));control.setOnCheckedChangeListener((button,on)->{if(!binding)touched=true;});view=control;}
        else{values.clear();EditText field=new EditText(context);field.setSingleLine(true);field.setText(current);field.setHint(specification.optString("help"));if(password)field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);view=field;}
        view.setContentDescription(specification.optString("label",specification.optString("key")));
    }
    String value(){if(view instanceof EditText)return ((EditText)view).getText().toString();if(view instanceof Switch)return touched?(((Switch)view).isChecked()?"true":"false"):saved;return values.get(((Spinner)view).getSelectedItemPosition());}
    void saved(String value){saved=value;touched=false;}
    void restore(String value){binding=true;saved=value;touched=false;if(view instanceof EditText)((EditText)view).setText(value);else if(view instanceof Switch)((Switch)view).setChecked(value.equals("true"));else ((Spinner)view).setSelection(values.indexOf(value));binding=false;}
    void commits(Commit commit){
        if(view instanceof Switch)((Switch)view).setOnCheckedChangeListener((button,on)->{if(binding)return;touched=true;if(!value().equals(saved))commit.save(value());});
        else if(view instanceof Spinner)((Spinner)view).setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> parent,View selected,int at,long id){if(!binding&&!value().equals(saved))commit.save(value());}public void onNothingSelected(AdapterView<?> parent){}});
        else if(!password){EditText field=(EditText)view;field.setOnFocusChangeListener((control,focused)->{if(!focused&&!value().equals(saved))commit.save(value());});field.setOnEditorActionListener((control,action,event)->{if(!value().equals(saved))commit.save(value());return true;});}
    }
}
