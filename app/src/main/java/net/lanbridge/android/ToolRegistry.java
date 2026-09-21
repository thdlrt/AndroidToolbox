package net.lanbridge.android;
import android.app.Activity;
import java.util.List;

/** Add a tool here; the home/catalog share the same registration. */
public final class ToolRegistry {
    private ToolRegistry() {}
    public static final class Tool {
        public final String id,name,description;
        public final Class<? extends Activity> activity;
        Tool(String id,String name,String description,Class<? extends Activity> activity){this.id=id;this.name=name;this.description=description;this.activity=activity;}
    }
    public static final List<Tool> TOOLS=List.of(new Tool("vpn","回家 VPN","通过 FN Connect 访问局域网，或使用家中 TCP 出口。",VpnActivity.class));
}
