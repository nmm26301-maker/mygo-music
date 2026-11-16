package com.mygo.music;
import android.content.Context;
import android.os.Build;
import org.json.*;
import java.io.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.zip.*;
import javax.net.ssl.*;

/** Pinned native Python and JS helpers. Does not download executable code at playback time. */
final class PluginNativeTools {
 private static final Object LOCK=new Object();
 static boolean youtube(String command){return command.equals("yt-dlp")||command.equals("/usr/local/bin/yt-dlp")||command.equals("/opt/homebrew/bin/yt-dlp");}
 static final class Plan {File binary;final List<String> args=new ArrayList<>();final Map<String,String> env=new HashMap<>();}
 static Plan prepare(Context c,String id,List<String> original)throws Exception{
  if(Build.VERSION.SDK_INT<24)throw new IOException("此解析工具需要 Android 7.0 或更新版本");
  File lib=new File(c.getApplicationInfo().nativeLibraryDir);Plan plan=new Plan();plan.binary=new File(lib,"libmygo-python.so");
  if(!plan.binary.isFile()||!new File(lib,"libpython3.12.so").isFile())throw new IOException("当前设备架构缺少已验证的解析环境");
  boolean version=original.size()==1&&(original.get(0).equals("--version")||original.get(0).equals("--help"));
  if(!version&&!c.getSharedPreferences("library",0).getBoolean("plugin_network_"+id,false))throw new IOException("请先在插件网络权限中允许访问");
  File runtime=runtime(c,lib);plan.args.add(new File(runtime,"yt-dlp").getAbsolutePath());
  if(!has(original,"--js-runtimes")){plan.args.add("--js-runtimes");plan.args.add("quickjs:"+new File(lib,"libmygo-qjs.so").getAbsolutePath());}
  if(!has(original,"--ffmpeg-location")){plan.args.add("--ffmpeg-location");plan.args.add(new File(lib,"libffmpeg.so").getAbsolutePath());}
  plan.args.add("--compat-options");plan.args.add("no-certifi");plan.args.addAll(original);
  plan.env.put("PYTHONHOME",runtime.getAbsolutePath());plan.env.put("PYTHONPATH",runtime+":"+new File(runtime,"stdlib")+":"+new File(runtime,"modules"));
  plan.env.put("PYTHONNOUSERSITE","1");plan.env.put("PYTHONDONTWRITEBYTECODE","1");plan.env.put("LD_LIBRARY_PATH",lib.getAbsolutePath());plan.env.put("TMPDIR",c.getCacheDir().getAbsolutePath());
  plan.env.put("SSL_CERT_FILE",new File(runtime,"android-ca.pem").getAbsolutePath());plan.env.put("REQUESTS_CA_BUNDLE",new File(runtime,"android-ca.pem").getAbsolutePath());return plan;
 }
 private static boolean has(List<String> args,String key){for(String v:args)if(v.equals(key)||v.startsWith(key+"="))return true;return false;}
 static String sha(File f)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte b:d.digest())s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
 private static void copy(InputStream input,File f)throws IOException{try(InputStream in=input;OutputStream out=new FileOutputStream(f)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}}
 private static void remove(File f){File[] files=f.listFiles();if(files!=null)for(File child:files)remove(child);f.delete();}
 static File runtime(Context c,File lib)throws Exception{synchronized(LOCK){
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=c.getAssets().open("native-tools.json")){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)bytes.write(b,0,n);}JSONObject info=new JSONObject(bytes.toString("UTF-8"));
  File bundle=new File(lib,"libpythonbundle.so");String hash=sha(bundle),scriptHash=info.getString("ytDlpSha256");boolean known=false;JSONArray hashes=info.getJSONArray("bundleHashes");for(int i=0;i<hashes.length();i++)if(hash.equals(hashes.getString(i)))known=true;if(!known)throw new IOException("解析环境校验失败");
  String identity="ca2-"+hash.substring(0,16)+scriptHash.substring(0,16);File parent=new File(c.getNoBackupFilesDir(),"plugin-tools"),target=new File(parent,identity),ready=new File(target,".ready");
  if(ready.isFile()&&new File(target,"sitecustomize.py").isFile()&&new File(target,"android-ca.pem").isFile()&&sha(new File(target,"yt-dlp")).equals(scriptHash))return target;
  if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("无法准备解析环境");File stage=new File(parent,identity+".staging");remove(stage);if(!stage.mkdirs())throw new IOException("无法准备解析环境");
  try{String root=stage.getCanonicalPath()+File.separator;long total=0;int entries=0;
   try(ZipInputStream zip=new ZipInputStream(new FileInputStream(bundle))){ZipEntry e;byte[] b=new byte[32768];while((e=zip.getNextEntry())!=null){if(++entries>10000)throw new IOException("标准库文件过多");File f=new File(stage,e.getName());if(!f.getCanonicalPath().startsWith(root))throw new IOException("标准库路径无效");if(e.isDirectory()){if(!f.isDirectory()&&!f.mkdirs())throw new IOException("目录创建失败");continue;}if(!f.getParentFile().isDirectory()&&!f.getParentFile().mkdirs())throw new IOException("目录创建失败");try(OutputStream out=new FileOutputStream(f)){int n;while((n=zip.read(b))!=-1){if((total+=n)>160L*1024*1024)throw new IOException("标准库体积超过限制");out.write(b,0,n);}}}}
   copy(c.getAssets().open("plugin-sitecustomize.py"),new File(stage,"sitecustomize.py"));
   File script=new File(stage,"yt-dlp");copy(c.getAssets().open("vendor/yt-dlp"),script);if(!sha(script).equals(scriptHash))throw new IOException("解析程序校验失败");
   TrustManagerFactory factory=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());factory.init((KeyStore)null);int roots=0;
   try(Writer writer=new OutputStreamWriter(new FileOutputStream(new File(stage,"android-ca.pem")),"US-ASCII")){for(TrustManager manager:factory.getTrustManagers())if(manager instanceof X509TrustManager)for(X509Certificate cert:((X509TrustManager)manager).getAcceptedIssuers()){writer.write("-----BEGIN CERTIFICATE-----\n");writer.write(android.util.Base64.encodeToString(cert.getEncoded(),android.util.Base64.NO_WRAP).replaceAll("(.{64})","$1\n"));writer.write("\n-----END CERTIFICATE-----\n");roots++;}}
   if(roots==0)throw new IOException("系统未提供可信 TLS 证书");copy(new ByteArrayInputStream(identity.getBytes("US-ASCII")),new File(stage,".ready"));remove(target);if(!stage.renameTo(target))throw new IOException("解析环境保存失败");return target;
  }catch(Exception e){remove(stage);throw e;}
 }}
}
