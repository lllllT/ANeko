package org.tamanegi.aneko;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;

public class ANekoActivity extends PreferenceActivity
{
    @Override
    public void onCreate(Bundle savedState)
    {
        super.onCreate(savedState);
        addPreferencesFromResource(R.xml.pref);

        getPreferenceManager().findPreference("motion.enable")
            .setOnPreferenceClickListener(new OnEnableClickListener());

        startAnimationService();
    }

    private void startAnimationService()
    {
        startService(new Intent(this, AnimationService.class)
                     .setAction(AnimationService.ACTION_START));
    }

    private class OnEnableClickListener
        implements Preference.OnPreferenceClickListener
    {
        @Override
        public boolean onPreferenceClick(Preference pref)
        {
            startAnimationService();
            return true;
        }
    }
}
