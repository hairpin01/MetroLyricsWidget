package io.github.hairpin01.metrolistlyricswidget;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookEntry implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (!Constants.TARGET_PACKAGE.equals(lpparam.packageName)) return;

        try {
            XposedHelpers.findAndHookMethod(
                    Constants.TARGET_SERVICE,
                    lpparam.classLoader,
                    "onCreate",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            MetroBridge.attach(param.thisObject, lpparam.classLoader);
                        }
                    }
            );

            XposedHelpers.findAndHookMethod(
                    Constants.TARGET_SERVICE,
                    lpparam.classLoader,
                    "onDestroy",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            MetroBridge.detach(param.thisObject);
                        }
                    }
            );

            XposedBridge.log("[MetroLyrics] hooks installed for " + lpparam.packageName);
        } catch (Throwable error) {
            XposedBridge.log("[MetroLyrics] failed to install hooks: " + error);
            XposedBridge.log(error);
        }
    }
}