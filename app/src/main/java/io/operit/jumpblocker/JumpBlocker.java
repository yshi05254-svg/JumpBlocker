package io.operit.jumpblocker;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Blocks outbound activity launches in the process selected by LSPosed. */
public final class JumpBlocker implements IXposedHookLoadPackage {
 private static final String MODULE="io.operit.jumpblocker";
 private static final String BLOCKED_COMPONENT_SUFFIX=".JumpBlockerBlockedActivity";
 private static final String[] HARD_BLOCKED_PACKAGES={"com.heytap.browser"};
 private static final Set<PendingIntent> BLOCKED_PI=Collections.newSetFromMap(new WeakHashMap<PendingIntent,Boolean>());
 private final Set<String> hookedMethods=Collections.synchronizedSet(new HashSet<String>());
 private String ownPackage;
 private Context context;

 @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) {
  if (MODULE.equals(p.packageName) || "android".equals(p.packageName)) return;
  ownPackage=p.packageName;
  hookApplication();
  hookLaunchMethods(Instrumentation.class);
  hookLaunchMethods(Activity.class);
  hookLaunchMethods(findClass("android.content.ContextWrapper"));
  hookLaunchMethods(findClass("android.app.ContextImpl"));
  hookActivityIntentSender();
  hookIntentSenderSend();
  hookPendingIntentCreation();
  hookPendingIntentSend();
  hookTaskManagerProxies();
  hookIntentLeaveProcess();
  log("active in "+p.packageName);
 }

 private void hookApplication(){
  try {
   XposedHelpers.findAndHookMethod("android.app.Application",null,"attach",Context.class,new XC_MethodHook(){
    protected void afterHookedMethod(MethodHookParam p){context=(Context)p.args[0]; hookTaskManagerProxies();}
   });
  } catch(Throwable t){log("Application.attach hook failed: "+t);}
 }

 private void hookLaunchMethods(Class<?> c){
  if(c==null)return;
  for(Method m:c.getDeclaredMethods()){
   String n=m.getName();
   if(n.startsWith("execStartActivity") || n.startsWith("execStartActivities") ||
      n.startsWith("startActivity") || n.startsWith("startActivities")) hookIntentMethod(m);
  }
 }

 private void hookIntentMethod(final Method m){
  if(!markHooked(m))return;
  try {XposedBridge.hookMethod(m,new XC_MethodHook(){
   protected void beforeHookedMethod(MethodHookParam p){
    Intent bad=findExternalIntent(p.args);
    if(bad!=null){log("blocked "+describe(bad)+" at "+m.getDeclaringClass().getName()+"#"+m.getName()); setCanceledResult(p,m.getReturnType());}
   }
  });}catch(Throwable t){log("hook failed "+m+": "+t);}
 }

 private void hookActivityIntentSender(){
  for(Method m:Activity.class.getDeclaredMethods())if(m.getName().startsWith("startIntentSender"))hookSenderMethod(m);
 }
 private void hookIntentSenderSend(){
  for(Method m:IntentSender.class.getDeclaredMethods())if(m.getName().equals("sendIntent"))hookSenderMethod(m);
 }
 private void hookSenderMethod(final Method m){
  if(!markHooked(m))return;
  try {XposedBridge.hookMethod(m,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){
   IntentSender s=p.thisObject instanceof IntentSender?(IntentSender)p.thisObject:null; Intent fill=null;
   for(Object a:p.args){if(a instanceof IntentSender)s=(IntentSender)a; else if(a instanceof Intent)fill=(Intent)a;}
   String creator=null; try{creator=s==null?null:s.getCreatorPackage();}catch(Throwable ignored){}
   if((creator!=null&&!ownPackage.equals(creator))||(fill!=null&&isExternal(fill))){log("blocked IntentSender creator="+creator); denySender(p,creator);}
  }});}catch(Throwable t){log("IntentSender hook failed: "+t);}
 }

 /* Replace the payload before system_server receives a token; recording alone cannot stop system-delivered PI. */
 private void hookPendingIntentCreation(){
  for(final Method m:PendingIntent.class.getDeclaredMethods()){
   String n=m.getName();
   if(!(n.equals("getActivity")||n.equals("getActivities")||n.equals("getService")||n.equals("getForegroundService")||n.equals("getBroadcast")))continue;
   if(!markHooked(m))continue;
   try {XposedBridge.hookMethod(m,new XC_MethodHook(){
    boolean block;
    protected void beforeHookedMethod(MethodHookParam p){
     block=false; boolean activity=m.getName().equals("getActivity")||m.getName().equals("getActivities");
     for(int x=0;x<p.args.length;x++){
      Object a=p.args[x];
      if(a instanceof Intent){Intent i=(Intent)a; if((activity&&isExternal(i))||(!activity&&isExplicitExternalOrHard(i))){p.args[x]=blockedIntent(); block=true; log("sanitized "+m.getName()+" "+describe(i));}}
      else if(a instanceof Intent[]){Intent[] copy=((Intent[])a).clone(); boolean changed=false; for(int y=0;y<copy.length;y++)if(copy[y]!=null&&((activity&&isExternal(copy[y]))||(!activity&&isExplicitExternalOrHard(copy[y])))){log("sanitized "+m.getName()+"["+y+"] "+describe(copy[y])); copy[y]=blockedIntent(); changed=block=true;} if(changed)p.args[x]=copy;}
     }
    }
    protected void afterHookedMethod(MethodHookParam p){if(block&&p.getResult() instanceof PendingIntent)synchronized(BLOCKED_PI){BLOCKED_PI.add((PendingIntent)p.getResult());}}
   });}catch(Throwable t){log("PendingIntent creation hook failed: "+t);}
  }
 }

 private void hookPendingIntentSend(){
  for(final Method m:PendingIntent.class.getDeclaredMethods())if(m.getName().equals("send")){
   if(!markHooked(m))continue;
   try {XposedBridge.hookMethod(m,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){
    PendingIntent pi=(PendingIntent)p.thisObject; boolean bad; synchronized(BLOCKED_PI){bad=BLOCKED_PI.contains(pi);}
    String creator=null; try{creator=pi.getCreatorPackage();}catch(Throwable ignored){} Intent fill=null; for(Object a:p.args)if(a instanceof Intent)fill=(Intent)a;
    if(bad||(creator!=null&&!ownPackage.equals(creator))||(fill!=null&&isExternal(fill))){log("blocked PendingIntent.send creator="+creator); denyPending(p);}
   }});}catch(Throwable t){log("PendingIntent.send hook failed: "+t);}
  }
 }

 private void hookTaskManagerProxies(){
  String[] classes={"android.app.IActivityTaskManager$Stub$Proxy","android.app.IActivityManager$Stub$Proxy","android.app.ActivityTaskManager","android.app.ActivityManager"};
  for(String cn:classes){Class<?> c=findClass(cn); if(c==null)continue; for(Method m:c.getDeclaredMethods()){String n=m.getName(); if(n.startsWith("startActivity")||n.startsWith("startActivities"))hookIntentMethod(m);}}
 }

 /* Last line of defence for an exact HeyTap target immediately before Binder IPC. */
 private void hookIntentLeaveProcess(){
  for(final Method m:Intent.class.getDeclaredMethods())if(m.getName().equals("prepareToLeaveProcess")){
   if(!markHooked(m))continue;
   try {XposedBridge.hookMethod(m,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){Intent i=(Intent)p.thisObject; if(resolvesToHardBlocked(i)){log("blocked hard target at prepareToLeaveProcess: "+describe(i)); p.setThrowable(new SecurityException("Blocked "+describe(i)+" by JumpBlocker"));}}});}catch(Throwable t){log("Intent leave-process hook failed: "+t);}
  }
 }

 private Intent findExternalIntent(Object[] args){
  for(Object a:args){if(a instanceof Intent&&isExternal((Intent)a))return (Intent)a; if(a instanceof Intent[])for(Intent i:(Intent[])a)if(isExternal(i))return i;} return null;
 }
 private boolean isExternal(Intent original){
  if(original==null)return false; if(isBlockedPackage(original))return true; Intent selector=original.getSelector(); if(selector!=null&&isBlockedPackage(selector))return true; Intent i=selector!=null?selector:original;
  if(Intent.ACTION_CHOOSER.equals(i.getAction()))return true; ComponentName c=i.getComponent(); if(c!=null)return !ownPackage.equals(c.getPackageName()); String pkg=i.getPackage(); if(pkg!=null)return !ownPackage.equals(pkg);
  Context ctx=context; if(ctx==null)return true;
  try{ResolveInfo r=ctx.getPackageManager().resolveActivity(i,PackageManager.MATCH_DEFAULT_ONLY); ActivityInfo ai=r==null?null:r.activityInfo; return ai==null||!ownPackage.equals(ai.packageName);}catch(Throwable t){return true;}
 }
 private boolean isExplicitExternalOrHard(Intent original){
  if(original==null)return false; Intent selector=original.getSelector(); Intent i=selector!=null?selector:original; if(isBlockedPackage(original)||(selector!=null&&isBlockedPackage(selector)))return true; ComponentName c=i.getComponent(); if(c!=null)return !ownPackage.equals(c.getPackageName()); String pkg=i.getPackage(); return pkg!=null&&!ownPackage.equals(pkg);
 }
 private boolean resolvesToHardBlocked(Intent original){
  if(original==null)return false; if(isBlockedPackage(original))return true; Intent selector=original.getSelector(); if(selector!=null&&isBlockedPackage(selector))return true; Intent i=selector!=null?selector:original; Context ctx=context; if(ctx==null)return false;
  try{ResolveInfo r=ctx.getPackageManager().resolveActivity(i,PackageManager.MATCH_DEFAULT_ONLY); ActivityInfo ai=r==null?null:r.activityInfo; return ai!=null&&isHardBlocked(ai.packageName);}catch(Throwable ignored){return false;}
 }
 private boolean isBlockedPackage(Intent i){ComponentName c=i.getComponent(); if(c!=null&&isHardBlocked(c.getPackageName()))return true; String p=i.getPackage(); return p!=null&&isHardBlocked(p);}
 private boolean isHardBlocked(String p){for(String x:HARD_BLOCKED_PACKAGES)if(x.equals(p))return true; return false;}
 private Intent blockedIntent(){return new Intent().setComponent(new ComponentName(ownPackage,ownPackage+BLOCKED_COMPONENT_SUFFIX));}

 private void setCanceledResult(XC_MethodHook.MethodHookParam p,Class<?> type){
  if(type==Boolean.TYPE)p.setResult(false); else if(type==Byte.TYPE)p.setResult((byte)0); else if(type==Short.TYPE)p.setResult((short)0); else if(type==Integer.TYPE)p.setResult(0); else if(type==Long.TYPE)p.setResult(0L); else if(type==Float.TYPE)p.setResult(0f); else if(type==Double.TYPE)p.setResult(0d); else if(type==Character.TYPE)p.setResult((char)0); else p.setResult(null);
 }
 private boolean markHooked(Method m){StringBuilder b=new StringBuilder(m.getDeclaringClass().getName()).append('#').append(m.getName()).append('('); for(Class<?> t:m.getParameterTypes())b.append(t.getName()).append(';'); return hookedMethods.add(b.append(')').toString());}
 private Class<?> findClass(String name){try{return XposedHelpers.findClass(name,null);}catch(Throwable ignored){return null;}}
 private String describe(Intent i){if(i==null)return "null"; ComponentName c=i.getComponent(); return "Intent{action="+i.getAction()+", package="+i.getPackage()+", component="+(c==null?"null":c.flattenToShortString())+"}";}
 private void denySender(XC_MethodHook.MethodHookParam p,String creator){p.setThrowable(new IntentSender.SendIntentException("Blocked external IntentSender (creator="+creator+") by JumpBlocker"));}
 private void denyPending(XC_MethodHook.MethodHookParam p){p.setThrowable(new PendingIntent.CanceledException("Blocked external PendingIntent by JumpBlocker"));}
 private void log(String s){XposedBridge.log("JumpBlocker: "+s);}
}