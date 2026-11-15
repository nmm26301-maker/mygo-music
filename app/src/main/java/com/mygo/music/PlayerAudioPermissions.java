package com.mygo.music;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.widget.Toast;

/** Standard system consent. The capture configuration never uses a microphone source. */
final class PlayerAudioPermissions {
    static final int AUDIO=3060,PROJECTION=3061;
    static void request(Activity activity){if(Build.VERSION.SDK_INT<29){Toast.makeText(activity,"真实双声道分析需要 Android 10 或更新系统",Toast.LENGTH_LONG).show();return;}if(activity.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){activity.requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},AUDIO);return;}MediaProjectionManager manager=(MediaProjectionManager)activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);activity.startActivityForResult(manager.createScreenCaptureIntent(),PROJECTION);}
    static boolean permission(Activity activity,int request,int[] grants){if(request!=AUDIO)return false;if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)PlayerAudioPermissions.request(activity);else AudioAnalysis.clear("音频分析未授权，播放不受影响");return true;}
    static boolean result(Activity activity,int request,int result,Intent data){if(request!=PROJECTION)return false;if(result==Activity.RESULT_OK&&data!=null){Intent intent=new Intent(activity,PlayerAudioCaptureService.class).putExtra("result",result).putExtra("data",data);if(Build.VERSION.SDK_INT>=26)activity.startForegroundService(intent);else activity.startService(intent);}else AudioAnalysis.clear("音频分析授权已取消");return true;}
}
