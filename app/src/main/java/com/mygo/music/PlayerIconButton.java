package com.mygo.music;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.*;
import android.os.Build;
import android.view.MotionEvent;
import android.widget.*;

/** Native touch targets with the pinned, locally bundled Lucide vector controls. */
final class PlayerIconButton extends ImageButton implements Checkable {
    private boolean checked,checkable;private int icon;private final boolean prominent;boolean motion=true;
    PlayerIconButton(Context context,int resource,String description,boolean prominent){
        super(context);this.prominent=prominent;setScaleType(ScaleType.CENTER_INSIDE);int p=dp(prominent?17:12);setPadding(p,p,p,p);setMinimumWidth(dp(48));setMinimumHeight(dp(48));
        GradientDrawable shape=new GradientDrawable();shape.setShape(GradientDrawable.OVAL);shape.setColor(prominent?0xffddd0f3:0x002b2637);setBackground(new RippleDrawable(ColorStateList.valueOf(0x448f79b1),shape,null));icon(resource);describe(description);paintState();
    }
    int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
    void icon(int value){if(icon!=value){icon=value;setImageResource(value);}}
    void describe(String value){setContentDescription(value);if(Build.VERSION.SDK_INT>=26)setTooltipText(value);}
    private void paintState(){setImageTintList(ColorStateList.valueOf(prominent?0xff261c35:checked?0xffdcc1ff:0xffe4deed));setSelected(checked);setAlpha(isEnabled()?1:.38f);}
    @Override public void setEnabled(boolean value){super.setEnabled(value);paintState();}
    @Override public void setChecked(boolean value){checkable=true;if(checked==value)return;checked=value;paintState();}
    @Override public boolean isChecked(){return checked;}
    @Override public void toggle(){setChecked(!checked);}
    @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setCheckable(checkable);info.setChecked(checked);}
    @Override public boolean onTouchEvent(MotionEvent event){if(motion&&isEnabled()){int action=event.getActionMasked();if(action==MotionEvent.ACTION_DOWN)animate().scaleX(.92f).scaleY(.92f).setDuration(85).start();else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)animate().scaleX(1).scaleY(1).setDuration(140).start();}return super.onTouchEvent(event);}
}
