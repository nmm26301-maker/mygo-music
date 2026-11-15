package com.mygo.music;

import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.*;

/** Per-instance JSON secrets. Keys never leave Android Keystore; ciphertext is bound to both instance and entry. */
final class PluginSecrets {
 private static final Object LOCK=new Object();
 private final SharedPreferences records;private final String identity,alias;
 PluginSecrets(Context context,String instance)throws Exception{identity=instance;byte[] digest=MessageDigest.getInstance("SHA-256").digest(instance.getBytes(StandardCharsets.UTF_8));StringBuilder hash=new StringBuilder();for(byte b:digest)hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));alias="mygo.plugin.secret.v1."+hash;records=context.getSharedPreferences("plugin_secrets_"+hash,Context.MODE_PRIVATE);}
 private void validate(String name)throws IOException{if(name==null||name.isEmpty()||name.length()>512||name.indexOf('\0')>=0)throw new IOException("无效的安全存储键");}
 private SecretKey key(boolean create)throws Exception{KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias(alias))return (SecretKey)store.getKey(alias,null);if(!create)throw new IOException("安全存储密钥不可用，请重新登录此音乐源");KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return generator.generateKey();}
 JSONObject lookup(String name)throws Exception{synchronized(LOCK){validate(name);boolean found=records.contains(name);return new JSONObject().put("found",found).put("value",found?get(name):JSONObject.NULL);}}
 Object get(String name)throws Exception{synchronized(LOCK){validate(name);String encoded=records.getString(name,"");if(encoded.isEmpty())return JSONObject.NULL;try{JSONObject value=new JSONObject(encoded);if(value.getInt("v")!=1)throw new IOException("安全存储格式不兼容");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(false),new GCMParameterSpec(128,Base64.decode(value.getString("iv"),Base64.NO_WRAP)));cipher.updateAAD((identity+"\0"+name).getBytes(StandardCharsets.UTF_8));return new JSONTokener(new String(cipher.doFinal(Base64.decode(value.getString("cipher"),Base64.NO_WRAP)),StandardCharsets.UTF_8)).nextValue();}catch(Exception e){throw new IOException("安全凭据无法读取，请重新登录此音乐源",e);}}}
 void set(String name,String raw)throws Exception{synchronized(LOCK){validate(name);if(raw==null||raw.length()>256*1024)throw new IOException("安全凭据超过大小限制");new JSONTokener(raw).nextValue();Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(true));cipher.updateAAD((identity+"\0"+name).getBytes(StandardCharsets.UTF_8));byte[] encrypted=cipher.doFinal(raw.getBytes(StandardCharsets.UTF_8));String encoded=new JSONObject().put("v",1).put("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).put("cipher",Base64.encodeToString(encrypted,Base64.NO_WRAP)).toString();if(!records.edit().putString(name,encoded).commit())throw new IOException("安全凭据保存失败，原有登录数据保持不变");}}
 void remove(String name)throws Exception{synchronized(LOCK){validate(name);if(!records.edit().remove(name).commit())throw new IOException("安全凭据移除失败");}}
}
