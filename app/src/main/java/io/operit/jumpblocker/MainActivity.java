package io.operit.jumpblocker;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
 @Override public void onCreate(Bundle b) { super.onCreate(b);
  int p=(int)(24*getResources().getDisplayMetrics().density);
  LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(p,p,p,p); box.setBackgroundColor(Color.WHITE);
  TextView title=t("跨应用跳转阻断器",26,Color.rgb(36,52,71)); box.addView(title);
  TextView state=t("LSPosed 模块 · 本应用内无需设置",15,Color.rgb(21,101,192)); state.setPadding(0,p/2,0,p); box.addView(state);
  box.addView(t("使用方法\n\n1. 打开 LSPosed → 模块\n2. 启用“跨应用跳转阻断器”\n3. 在作用域中只勾选需要限制的 App\n4. 强制停止并重新打开被限制 App（必要时重启）\n\n生效规则\n\n✓ 应用自身 Activity 正常跳转\n✓ 正常运行与联网不受影响\n✕ 浏览器、应用商店、分享、文件选择器、系统设置及任意其他 App 的 Activity 跳转\n✕ 显式、隐式、IntentSender 与常见 PendingIntent 外跳\n\n隐私：模块无联网权限，不读取应用列表，不保存日志。\n\n注意：不要把“系统框架”或 LSPosed 管理器勾入作用域。极少数通过 native Binder、root shell 或厂商私有接口发起的跳转不属于常规 Android Intent 链路。",16,Color.rgb(45,45,45)));
  setContentView(box);
 }
 private TextView t(String s,int size,int c){ TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(c);v.setLineSpacing(0,1.15f);v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));return v; }
}
