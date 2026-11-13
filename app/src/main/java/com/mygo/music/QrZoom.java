package com.mygo.music;

import android.app.*;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** A clean, locally regenerated QR display. It never confirms or completes authorization. */
final class QrZoom {
    final Dialog dialog;final ImageView image;
    QrZoom(Context context,String payload)throws Exception{
        dialog=new Dialog(context);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);FrameLayout root=new FrameLayout(context);root.setBackgroundColor(Color.WHITE);int pad=(int)(16*context.getResources().getDisplayMetrics().density);root.setPadding(pad,pad,pad,pad);image=new ImageView(context);image.setContentDescription("放大的登录二维码");image.setScaleType(ImageView.ScaleType.FIT_CENTER);root.addView(image,new FrameLayout.LayoutParams(-1,-1));Button close=new Button(context);close.setText("返回");close.setTextColor(Color.BLACK);close.setBackgroundColor(Color.WHITE);FrameLayout.LayoutParams button=new FrameLayout.LayoutParams(pad*4,pad*3,Gravity.TOP|Gravity.RIGHT);root.addView(close,button);close.setOnClickListener(view->dialog.dismiss());dialog.setContentView(root);update(payload);dialog.show();Window window=dialog.getWindow();window.setBackgroundDrawable(new ColorDrawable(Color.WHITE));window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);window.setLayout(-1,-1);
    }
    void update(String payload)throws Exception{BitmapDrawable drawable=new BitmapDrawable(image.getResources(),PluginQrImage.create(payload));drawable.setFilterBitmap(false);image.setImageDrawable(drawable);}
    void close(){dialog.dismiss();}
}
