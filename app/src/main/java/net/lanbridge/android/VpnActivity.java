package net.lanbridge.android;

/** Compatibility entry point; all tool UI lives in the shared window. */
public final class VpnActivity extends ToolEntryActivity {
    @Override String page() { return "vpn"; }
}
