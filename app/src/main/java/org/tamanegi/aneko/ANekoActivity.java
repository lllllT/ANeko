package org.tamanegi.aneko;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ANekoActivity extends AppCompatActivity {

    private static final String KEY_ICON = "icon";
    private static final String KEY_LABEL = "label";
    private static final String KEY_COMPONENT = "component";

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setContentView(R.layout.activity_neko);
        init();
    }

    private void init() {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.neko_prefs, new SettingsFragment())
                .commit();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 0 && Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(getApplicationContext(), "Storage Permission wasn't granted!!", Toast.LENGTH_SHORT).show();
            this.finish();
        } else {
            init();
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {

        Context context;

        @Override
        public void onAttach(@NonNull Context context) {
            super.onAttach(context);
            this.context = context;
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.neko_prefs, rootKey);
            SharedPreferences prefs = context.getSharedPreferences(context.getPackageName() + "preferences", MODE_PRIVATE);
            Preference Service_Enable = findPreference(AnimationService.PREF_KEY_ENABLE);
            ListPreference Skin = findPreference("motion.skin");

            Service_Enable.setOnPreferenceChangeListener((preference, newValue) -> {
                if ((Boolean) newValue) startAnimationService();
                return true;
            });

            Skin.setEntries(getEntries("Neko (Default)", "(Storage)", "(Installed)"));
            Skin.setEntryValues(getEntries("", "", ""));
            if (Skin.getEntries().length < 2) Skin.setValueIndex(0);
            if (prefs.getBoolean(Service_Enable.getKey(), false)) startAnimationService();
        }

        @Override
        public boolean onPreferenceTreeClick(Preference preference) {
            String key = preference.getKey();
            if (key != null) {
                switch (key) {
                    case AnimationService.PREF_KEY_ENABLE:
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            if (!Settings.canDrawOverlays(getContext())) {
                                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getContext().getPackageName()));
                                getContext().startActivity(intent);
                                context.getSharedPreferences(context.getPackageName() + "_preferences", MODE_PRIVATE).edit().putBoolean(AnimationService.PREF_KEY_ENABLE, false).apply();
                                getActivity().getSupportFragmentManager()
                                        .beginTransaction()
                                        .replace(R.id.neko_prefs, new SettingsFragment())
                                        .commit();
                            }
                        }
                        break;

                    case "get.skin":
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse(context.getString(R.string.skin_search_uri)));
                        try {
                            context.startActivity(intent);
                        } catch (ActivityNotFoundException e) {
                            Toast.makeText(context, R.string.msg_market_not_found, Toast.LENGTH_SHORT).show();
                        }
                }
            }
            return super.onPreferenceTreeClick(preference);
        }

        private void startAnimationService() {
            SharedPreferences.Editor edit = context.getSharedPreferences(context.getPackageName() + "_preferences", MODE_PRIVATE).edit();
            edit.putBoolean(AnimationService.PREF_KEY_VISIBLE, true).apply();
            context.startService(new Intent(context, AnimationService.class).setAction(AnimationService.ACTION_START));
        }

        private CharSequence[] getEntries(String PreValue, String internal, String external) {
            List<Map<String, Object>> InstalledList = createListData_apk();
            List<Map<String, Object>> StoredList = createListData_dir();

            CharSequence[] list = new CharSequence[InstalledList.size() + StoredList.size()];
            list[0] = PreValue;
            int count = 1;

            for (Map<String, Object> map : StoredList) {
                String component = (map.get(KEY_COMPONENT) + "").replace("ComponentInfo{", "").replace("}", "");
                String name = map.get(KEY_LABEL) + " " + internal;
                list[count++] = PreValue.equals("") ? component : name;
            }

            for (Map<String, Object> map : InstalledList) {
                String component = (map.get(KEY_COMPONENT) + "").replace("ComponentInfo{", "").replace("}", "");
                String name = map.get(KEY_LABEL) + " " + external;
                if (component.equals("org.tamanegi.aneko/org.tamanegi.aneko.NekoSkin")) continue;
                list[count++] = PreValue.equals("") ? component : name;
            }
            return list;
        }

        private List<Map<String, Object>> createListData_apk() {
            PackageManager pm = context.getPackageManager();

            Intent[] internals = {new Intent(context, NekoSkin.class),};
            Intent intent = new Intent(AnimationService.ACTION_GET_SKIN);
            List<ResolveInfo> activities = pm.queryIntentActivityOptions(null, internals, intent, 0);

            List<Map<String, Object>> list = new ArrayList<>();

            for (ResolveInfo info : activities) {
                ComponentName comp = new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
                Map<String, Object> data = new HashMap<>();
                data.put(KEY_ICON, info.loadIcon(pm));
                data.put(KEY_LABEL, info.loadLabel(pm));
                data.put(KEY_COMPONENT, comp);
                list.add(data);
            }
            return list;
        }

        private List<Map<String, Object>> createListData_dir() {
            List<Map<String, Object>> list = new ArrayList<>();
            try {
                File externalStorageDirectory = Environment.getExternalStorageDirectory();
                File skinsRootDir = new File(externalStorageDirectory, AnimationService.ANeko_SKINS);
                if (!skinsRootDir.exists()) {
                    return list;
                }

                File[] skinDirs = skinsRootDir.listFiles(File::isDirectory);
                for (File skinDir : skinDirs) {
                    String[] xmlns = skinDir.list((dir, filename) -> filename.endsWith(".xml"));
                    String dirName = skinDir.getName();
                    for (String xml : xmlns) {
                        String n = dirName + "/" + xml;
                        Map<String, Object> data = new HashMap<>();
                        data.put(KEY_LABEL, n.replace(".xml", ""));
                        data.put(KEY_COMPONENT, n);
                        list.add(data);
                    }
                }
            } catch (Exception e) {
                Toast.makeText(getContext(), "" + e.getMessage(), Toast.LENGTH_LONG).show();
                e.printStackTrace();
            }
            return list;
        }
    }
}
