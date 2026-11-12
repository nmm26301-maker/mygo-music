package com.mygo.music;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Test APK only: an external, portrait-only scanner window. No credentials or QR image are read. */
public class PhoneScannerFixtureActivity extends Activity {
    @Override public void onCreate(Bundle saved){super.onCreate(saved);TextView label=new TextView(this);label.setText("模拟本机竖屏扫码应用");label.setTextSize(20);setContentView(label);}
}
