/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.qs.tiles;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListView;

import com.android.internal.logging.MetricsConstants;
import com.android.internal.logging.MetricsLogger;
import com.android.systemui.R;
import com.android.systemui.qs.QSDetailItems;
import com.android.systemui.qs.QSTile;
import com.android.systemui.statusbar.policy.KeyguardMonitor;
import com.android.systemui.statusbar.policy.LocationController;
import com.android.systemui.statusbar.policy.LocationController.LocationSettingsChangeCallback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Quick settings tile: Location **/
public class LocationTile extends QSTile<QSTile.BooleanState> {
    private static final Intent LOCATION_SETTINGS_INTENT
            = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
    public static final Integer[] LOCATION_SETTINGS = new Integer[]{
            Settings.Secure.LOCATION_MODE_BATTERY_SAVING,
            Settings.Secure.LOCATION_MODE_SENSORS_ONLY,
            Settings.Secure.LOCATION_MODE_HIGH_ACCURACY
    };

    private final AnimationIcon mEnable =
            new AnimationIcon(R.drawable.ic_signal_location_enable_animation);
    private final AnimationIcon mDisable =
            new AnimationIcon(R.drawable.ic_signal_location_disable_animation);

    private final LocationController mController;
    private final LocationDetailAdapter mDetailAdapter;
    private final KeyguardMonitor mKeyguard;
    private final Callback mCallback = new Callback();
    private final List<Integer> mLocationList = new ArrayList<Integer>();

    public LocationTile(Host host) {
        super(host);
        mController = host.getLocationController();
        mDetailAdapter = new LocationDetailAdapter();
        mKeyguard = host.getKeyguardMonitor();
    }

    @Override
    public DetailAdapter getDetailAdapter() {
        return mDetailAdapter;
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void setListening(boolean listening) {
        if (listening) {
            mController.addSettingsChangedCallback(mCallback);
            mKeyguard.addCallback(mCallback);
        } else {
            mController.removeSettingsChangedCallback(mCallback);
            mKeyguard.removeCallback(mCallback);
        }
    }

    @Override
    protected void handleClick() {
        boolean wasEnabled = mController.isLocationEnabled();
        mController.setLocationEnabled(!wasEnabled);
        MetricsLogger.action(mContext, getMetricsCategory(), !wasEnabled);
        refreshState();
        mEnable.setAllowAnimation(true);
        mDisable.setAllowAnimation(true);
    }

    @Override
    protected void handleLongClick() {
        boolean wasEnabled = mController.isLocationEnabled();
        if(mController.isLocationEnabled()) {
            showDetail(true);
        } else {
            mHost.startActivityDismissingKeyguard(LOCATION_SETTINGS_INTENT);
        }
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final int currentState = mController.getLocationCurrentState();

        // Work around for bug 15916487: don't show location tile on top of lock screen. After the
        // bug is fixed, this should be reverted to only hiding it on secure lock screens:
        // state.visible = !(mKeyguard.isSecure() && mKeyguard.isShowing());
        state.visible = !mKeyguard.isShowing();
        state.label = mContext.getString(getStateLabelRes(currentState));

        switch (currentState) {
            case Settings.Secure.LOCATION_MODE_OFF:
                state.contentDescription = mContext.getString(
                        R.string.accessibility_quick_settings_location_off);
                state.icon = mDisable;
                break;
            case Settings.Secure.LOCATION_MODE_BATTERY_SAVING:
                state.contentDescription = mContext.getString(
                        R.string.accessibility_quick_settings_location_battery_saving);
                state.icon = ResourceIcon.get(R.drawable.ic_qs_location_battery_saving);
                break;
            case Settings.Secure.LOCATION_MODE_SENSORS_ONLY:
                state.contentDescription = mContext.getString(
                        R.string.accessibility_quick_settings_location_gps_only);
                state.icon = mEnable;
                break;
            case Settings.Secure.LOCATION_MODE_HIGH_ACCURACY:
                state.contentDescription = mContext.getString(
                        R.string.accessibility_quick_settings_location_high_accuracy);
                state.icon = mEnable;
                break;
            default:
                state.contentDescription = mContext.getString(
                        R.string.accessibility_quick_settings_location_on);
                state.icon = mEnable;
        }
    }

    private int getStateLabelRes(int currentState) {
        switch (currentState) {
            case Settings.Secure.LOCATION_MODE_OFF:
                return R.string.quick_settings_location_off_label;
            case Settings.Secure.LOCATION_MODE_BATTERY_SAVING:
                return R.string.quick_settings_location_battery_saving_label;
            case Settings.Secure.LOCATION_MODE_SENSORS_ONLY:
                return R.string.quick_settings_location_gps_only_label;
            case Settings.Secure.LOCATION_MODE_HIGH_ACCURACY:
                return R.string.quick_settings_location_high_accuracy_label;
            default:
                return R.string.quick_settings_location_label;
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsLogger.QS_LOCATION;
    }

    @Override
    protected String composeChangeAnnouncement() {
        switch (mController.getLocationCurrentState()) {
            case Settings.Secure.LOCATION_MODE_OFF:
                return mContext.getString(
                        R.string.accessibility_quick_settings_location_changed_off);
            case Settings.Secure.LOCATION_MODE_BATTERY_SAVING:
                return mContext.getString(
                        R.string.accessibility_quick_settings_location_changed_battery_saving);
            case Settings.Secure.LOCATION_MODE_SENSORS_ONLY:
                return mContext.getString(
                        R.string.accessibility_quick_settings_location_changed_gps_only);
            case Settings.Secure.LOCATION_MODE_HIGH_ACCURACY:
                return mContext.getString(
                        R.string.accessibility_quick_settings_location_changed_high_accuracy);
            default:
                return mContext.getString(
                        R.string.accessibility_quick_settings_location_changed_on);
        }
    }

    private final class Callback implements LocationSettingsChangeCallback,
            KeyguardMonitor.Callback {
        @Override
        public void onLocationSettingsChanged(boolean enabled) {
            mDetailAdapter.setLocationEnabled(enabled);
            mDetailAdapter.setLocationMode(mController.getLocationCurrentState());
            refreshState();
        }

        @Override
        public void onKeyguardChanged() {
            refreshState();
        }
    };

    private class LocationDetailAdapter implements DetailAdapter {
        private LocationDetailView mLocationDetailView;

        @Override
        public int getTitle() {
            return R.string.quick_settings_location_detail_title;
        }

        @Override
        public Boolean getToggleState() {
            return mController.getLocationCurrentState() != Settings.Secure.LOCATION_MODE_OFF;
        }

        @Override
        public Intent getSettingsIntent() {
            return LOCATION_SETTINGS_INTENT;
        }
  
        @Override
        public void setToggleState(boolean state) {
            mController.setLocationEnabled(state);
            fireToggleStateChanged(state);
        }

        @Override
        public int getMetricsCategory() {
            return MetricsLogger.QS_LOCATION;
        }

        @Override
        public View createDetailView(Context context, View convertView, ViewGroup parent) {
            final LocationDetailView v = (LocationDetailView) (LayoutInflater.from(mContext).inflate(R.layout.location, parent, false));
            mLocationDetailView = v;
            mLocationDetailView.setLocationController(mController);
            setLocationMode(mController.getLocationCurrentState());
            return v;
        }

        public void setLocationEnabled(boolean enabled) {
            fireToggleStateChanged(enabled);
        }

        public void setLocationMode(int mode) {
            if(mLocationDetailView != null)
                mLocationDetailView.setLocationMode(mode);
        }
    }
}
