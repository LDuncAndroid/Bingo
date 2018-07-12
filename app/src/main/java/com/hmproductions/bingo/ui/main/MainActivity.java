package com.hmproductions.bingo.ui.main;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;
import com.hmproductions.bingo.BingoActionServiceGrpc;
import com.hmproductions.bingo.BingoStreamServiceGrpc;
import com.hmproductions.bingo.R;
import com.hmproductions.bingo.dagger.ContextModule;
import com.hmproductions.bingo.dagger.DaggerBingoApplicationComponent;
import com.hmproductions.bingo.data.Player;
import com.hmproductions.bingo.ui.GameActivity;
import com.hmproductions.bingo.ui.SplashActivity;
import com.hmproductions.bingo.ui.settings.SettingsActivity;
import com.hmproductions.bingo.utils.ConnectionUtils;
import com.hmproductions.bingo.utils.Constants;
import com.hmproductions.bingo.utils.Miscellaneous;
import com.hmproductions.bingo.views.CustomPicker;

import java.util.ArrayList;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;

import static com.hmproductions.bingo.ui.main.RoomFragment.ROOM_NAME_BUNDLE_KEY;
import static com.hmproductions.bingo.ui.main.RoomFragment.TIME_LIMIT_BUNDLE_KEY;
import static com.hmproductions.bingo.utils.Constants.CLASSIC_TAG;
import static com.hmproductions.bingo.utils.Constants.FIRST_TIME_OPENED_KEY;

public class MainActivity extends AppCompatActivity implements
        ConnectionUtils.OnNetworkDownHandler,
        Miscellaneous.OnFragmentChangeRequest,
        HomeFragment.GetDetails,
        Miscellaneous.OnSnackBarRequest {

    public static final String PLAYER_LEFT_ID = "player-left-id";

    private static final String PLAYER_NAME_KEY = "player-name-key";
    private static final String PLAYER_COLOR_KEY = "player-color-key";

    @Inject
    BingoActionServiceGrpc.BingoActionServiceBlockingStub actionServiceBlockingStub;

    @Inject
    BingoStreamServiceGrpc.BingoStreamServiceStub streamServiceStub;

    @Inject
    SharedPreferences preferences;

    @BindView(R.id.playerName_editText)
    EditText playerNameEditText;

    @BindView(R.id.color_picker)
    CustomPicker colorPicker;

    @BindView(R.id.main_toolbar)
    Toolbar mainToolbar;

    // TODO : what if another player loses connection
    static int currentPlayerId = -1, currentRoomId = -1;
    static ArrayList<Player> playersList = new ArrayList<>();

    AlertDialog helpDialog;
    private ConnectivityManager.NetworkCallback networkCallback;
    private ConnectivityManager connectivityManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);
        DaggerBingoApplicationComponent.builder().contextModule(new ContextModule(this)).build().inject(this);

        setupColorPicker();
        setupNetworkCallback();

        mainToolbar.setTitle("");
        setSupportActionBar(mainToolbar);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        if (getIntent().getAction() != null && getIntent().getAction().equals(Constants.QUIT_GAME_ACTION)) {
            if (!getIntent().getBooleanExtra(PLAYER_LEFT_ID, true)) {
                currentPlayerId = getIntent().getIntExtra(GameActivity.PLAYER_ID, currentPlayerId);
                currentRoomId = getIntent().getIntExtra(GameActivity.ROOM_ID, currentRoomId);

                RoomFragment roomFragment = new RoomFragment();

                Bundle bundle = new Bundle();
                bundle.putInt(TIME_LIMIT_BUNDLE_KEY, getIntent().getIntExtra(TIME_LIMIT_BUNDLE_KEY, 2));
                bundle.putString(ROOM_NAME_BUNDLE_KEY, getIntent().getStringExtra(GameActivity.ROOM_NAME_EXTRA_KEY));
                roomFragment.setArguments(bundle);

                getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, roomFragment).commit();
                return;
            }
        }

        currentPlayerId = currentRoomId = -1;

        startTapTargetSequence();
        preferences.edit().putBoolean(FIRST_TIME_OPENED_KEY, false).apply();
    }

    private void setupColorPicker() {
        colorPicker.setMinValue(0);
        colorPicker.setMaxValue(getResources().getStringArray(R.array.colorsName).length - 1);
        colorPicker.setDisplayedValues(getResources().getStringArray(R.array.colorsName));
        colorPicker.setWrapSelectorWheel(true);
    }

    private void setupNetworkCallback() {
        networkCallback = new ConnectivityManager.NetworkCallback() {
            
            @Override
            public void onLost(Network network) {
                super.onLost(network);
                Log.v(CLASSIC_TAG, network.toString());
            }
        };

        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_activity_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == R.id.settings_action)
            startActivity(new Intent(this, SettingsActivity.class));
        else if (item.getItemId() == R.id.help_action) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder
                    .setCustomTitle(getLayoutInflater().inflate(R.layout.help_title, null))
                    .setView(getLayoutInflater().inflate(R.layout.help_box, null))
                    .setCancelable(true)
                    .show();
            helpDialog = builder.create();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        playerNameEditText.setText(preferences.getString(PLAYER_NAME_KEY, ""));
        colorPicker.setValue(preferences.getInt(PLAYER_COLOR_KEY, 0));

        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .build();

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
    }

    @Override
    protected void onPause() {
        super.onPause();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(PLAYER_NAME_KEY, playerNameEditText.getText().toString());
        editor.putInt(PLAYER_COLOR_KEY, colorPicker.getValue());
        editor.apply();
        connectivityManager.unregisterNetworkCallback(networkCallback);
    }

    @Override
    public void changeFragment(String roomName, int timeLimit, View view) {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);

        if (currentFragment instanceof RoomFragment) {
            getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, getHomeFragment(false)).commit();
            playerNameEditText.setEnabled(true);
            colorPicker.setEnabled(true);

        } else {
            RoomFragment roomFragment = new RoomFragment();

            if (roomName != null) {
                Bundle bundle = new Bundle();
                bundle.putString(RoomFragment.ROOM_NAME_BUNDLE_KEY, roomName);
                bundle.putInt(TIME_LIMIT_BUNDLE_KEY, timeLimit);
                roomFragment.setArguments(bundle);
            }

            if (view != null) {
                getSupportFragmentManager().beginTransaction().addSharedElement(view, getString(R.string.room_name_transition))
                        .replace(R.id.fragment_container, roomFragment).commit();
            } else {
                getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, roomFragment).commit();
            }

            playerNameEditText.setEnabled(false);
            colorPicker.setEnabled(false);

            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        }
    }

    @Override
    public void finishCurrentActivity() {
        finish();
    }

    @Override
    public void onNetworkDownError() {
        TextView bingoTextView = mainToolbar.findViewById(R.id.toolbarName_textView);

        startActivity(new Intent(this, SplashActivity.class), ActivityOptionsCompat
                .makeSceneTransitionAnimation(this, bingoTextView, bingoTextView.getTransitionName()).toBundle());
        finish();
    }

    @Nullable
    @Override
    public Player getUserDetails() {

        if (playerNameEditText.getText().toString().isEmpty() || playerNameEditText.getText().toString().equals("")) {
            playerNameEditText.setError("A-Z, 0-9");
            Toast.makeText(this, "Please enter the name", Toast.LENGTH_SHORT).show();
            return null;
        } else if (currentRoomId != -1) {
            Toast.makeText(this, "Player already joined", Toast.LENGTH_SHORT).show();
            return null;
        }

        return new Player(playerNameEditText.getText().toString(),
                getResources().getStringArray(R.array.colorsName)[colorPicker.getValue()], -1, false);
    }

    private HomeFragment getHomeFragment(boolean showSnackbar) {

        Bundle bundle = new Bundle();
        bundle.putBoolean(SplashActivity.SHOW_SNACKBAR_KEY, showSnackbar && getIntent().getBooleanExtra(SplashActivity.SHOW_SNACKBAR_KEY, false));

        HomeFragment homeFragment = new HomeFragment();
        homeFragment.setArguments(bundle);

        return homeFragment;
    }

    private void startTapTargetSequence() {

        if (preferences.getBoolean(FIRST_TIME_OPENED_KEY, true)) {

            HomeFragment homeFragment = getHomeFragment(false);

            getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, homeFragment).commit();

            new Handler().postDelayed(() -> {
                        if (homeFragment.getView() != null) {
                            new TapTargetSequence(this).targets(
                                    TapTarget
                                            .forToolbarMenuItem(mainToolbar, R.id.settings_action, "Settings", "Toggle settings to call numbers instead of tapping on them")
                                            .targetRadius(50)
                                            .drawShadow(true),
                                    TapTarget
                                            .forView(homeFragment.getView().findViewById(R.id.noRoomsFound_textView), "Refresh Rooms", "Swipe downwards to refresh rooms list anytime")
                                            .targetRadius(120).cancelable(true).icon(getDrawable(R.drawable.swipe_icon)).drawShadow(true)
                            ).listener(new TapTargetSequence.Listener() {
                                @Override
                                public void onSequenceFinish() {
                                    homeFragment.onRefresh();
                                }

                                @Override
                                public void onSequenceStep(TapTarget lastTarget, boolean targetClicked) {

                                }

                                @Override
                                public void onSequenceCanceled(TapTarget lastTarget) {

                                }
                            })
                                    .continueOnCancel(true)
                                    .start();
                        }
                    }
                    , 450);


            preferences.edit().putBoolean(FIRST_TIME_OPENED_KEY, false).apply();

        } else
            getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, getHomeFragment(true)).commit();

    }

    @Override
    public void showSnackBar(String message, int duration) {
        Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content), message, duration);

        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);

        if (message.equals("No rooms available"))
            snackbar.setAction("Host", view -> ((HomeFragment) currentFragment).onHostButtonClick());
        else if (message.equals("Room does not exist"))
            snackbar.setAction("Refresh", view -> ((HomeFragment) currentFragment).onRefresh());

        if (currentFragment.getView() != null) {
            FloatingActionButton hostFab = currentFragment.getView().findViewById(R.id.host_fab);

            hostFab.hide();
            new Handler().postDelayed(hostFab::show, duration == Snackbar.LENGTH_LONG ? 3500 : 2000);
        }

        snackbar.show();
    }

    /* Copyright 2016 Keepsafe Software Inc.

     * Licensed under the Apache License, Version 2.0 (the "License");
     * you may not use this file except in compliance with the License.
     * You may obtain a copy of the License at
     * http://www.apache.org/licenses/LICENSE-2.0
     * Unless required by applicable law or agreed to in writing, software
     * distributed under the License is distributed on an "AS IS" BASIS,
     * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     * See the License for the specific language governing permissions and
     * limitations under the License.

     */
}
