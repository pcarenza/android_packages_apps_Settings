/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.settings;

import com.android.internal.widget.LockPatternUtils;

import android.app.Activity;
import android.app.Fragment;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceActivity;
import android.speech.RecognizerIntent;
import android.speech.RecognitionListener;
import android.text.InputType;
import android.text.Selection;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.TextView;

public class ChooseLockCommand extends PreferenceActivity {
    private static final int REQUEST_CODE=1234;
    private ListView ResultList;
    

    @Override
    public Intent getIntent() {
        Intent modIntent = new Intent(super.getIntent());
        modIntent.putExtra(EXTRA_SHOW_FRAGMENT, ChooseLockPasswordFragment.class.getName());
        modIntent.putExtra(EXTRA_NO_HEADERS, true);
        return modIntent;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CharSequence msg = getText(R.string.lockcommand_choose_your_password_header);
        showBreadCrumbs(msg, msg);
    }

    public static class ChooseLockPasswordFragment extends Fragment
            implements OnClickListener, RecognitionListener {
        private static final String KEY_FIRST_PIN = "first_pin";
        private static final String KEY_UI_STAGE = "ui_stage";
        private TextView mPasswordEntry;
        private int mPasswordMinLength = 4;
        private int mPasswordMaxLength = 16;
     
        private LockPatternUtils mLockPatternUtils;
        private int mRequestedQuality = DevicePolicyManager.PASSWORD_QUALITY_NUMERIC;
        private ChooseLockSettingsHelper mChooseLockSettingsHelper;
        private Stage mUiStage = Stage.Introduction;
        private TextView mHeaderText;
	private Button mSpeakButton;
        private String mFirstPin;
        private boolean mIsAlphaMode;
        private Button mCancelButton;
        private Button mNextButton;
        private static final int CONFIRM_EXISTING_REQUEST = 58;
        static final int RESULT_FINISHED = RESULT_FIRST_USER;
        private static final long ERROR_MESSAGE_TIMEOUT = 3000;
        private static final int MSG_SHOW_ERROR = 1;

        private Handler mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MSG_SHOW_ERROR) {
                    updateStage((Stage) msg.obj);
                }
            }
        };

        /**
         * Keep track internally of where the user is in choosing a pattern.
         */
        protected enum Stage {

            Introduction(R.string.lockpassword_choose_your_command_header,
                    R.string.lockpassword_continue_label),

            NeedToConfirm(R.string.lockpassword_confirm_your_command_header,
                    R.string.lockpassword_ok_label),

            ConfirmWrong(R.string.lockpassword_confirm_passwords_dont_match,
                    R.string.lockpassword_continue_label);

            /**
             * @param headerMessage The message displayed at the top.
             */
            Stage(int hintInAlpha, int nextButtonText) {
                this.alphaHint = hintInAlpha;
                this.buttonText = nextButtonText;
            }

            public final int alphaHint;
            public final int buttonText;
        }

        // required constructor for fragments
        public ChooseLockCommandFragment() {

        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mLockPatternUtils = new LockPatternUtils(getActivity());
            Intent intent = getActivity().getIntent();

            mChooseLockSettingsHelper = new ChooseLockSettingsHelper(getActivity());
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {

            View view = inflater.inflate(R.layout.choose_lock_command, null);
	    mSpeakButton = (Button) view.findViewById(R.id.speak_button);
	    mSpeakButton.setOnClickListener(this);
            mCancelButton = (Button) view.findViewById(R.id.cancel_button);
            mCancelButton.setOnClickListener(this);
            mNextButton = (Button) view.findViewById(R.id.next_button);
            mNextButton.setOnClickListener(this);


            PackageManager pm = getPackageManager();
            List<ResolveInfo> activities = pm.queryIntentActivities(
                new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);
            if (activities.size() == 0)
            {
            	mSpeakButton.setEnabled(false);
            	mSpeakButton.setText("Recognizer not present");
            }
            final Activity activity = getActivity();

            mHeaderText = (TextView) view.findViewById(R.id.headerText);


            Intent intent = getActivity().getIntent();
            final boolean confirmCredentials = intent.getBooleanExtra("confirm_credentials", true);
            if (savedInstanceState == null) {
                updateStage(Stage.Introduction);
                if (confirmCredentials) {
                    mChooseLockSettingsHelper.launchConfirmationActivity(CONFIRM_EXISTING_REQUEST,
                            null, null);
                }
            } else {
                mFirstPin = savedInstanceState.getString(KEY_FIRST_PIN);
                final String state = savedInstanceState.getString(KEY_UI_STAGE);
                if (state != null) {
                    mUiStage = Stage.valueOf(state);
                    updateStage(mUiStage);
                }
            }
            // Update the breadcrumb (title) if this is embedded in a PreferenceActivity
            if (activity instanceof PreferenceActivity) {
                final PreferenceActivity preferenceActivity = (PreferenceActivity) activity;
                int id = mIsAlphaMode ? R.string.lockpassword_choose_your_command_header
                        : R.string.lockpassword_choose_your_pin_header;
                CharSequence title = getText(id);
                preferenceActivity.showBreadCrumbs(title, title);
            }

            return view;
        }
	
	public void mSpeakButtonClicked(View v) {
		startVoiceRecognitionActivity();
	}

/**
* Fire an intent to start the voice recognition activity.
*/
	private void startVoiceRecognitionActivity()
	{
        	Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        	intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                	RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        	intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Voice recognition Demo...");
        	startActivityForResult(intent, REQUEST_CODE);
    	}


        @Override
        public void onResume() {
            super.onResume();
            updateStage(mUiStage);
        }

        @Override
        public void onPause() {
            mHandler.removeMessages(MSG_SHOW_ERROR);

            super.onPause();
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putString(KEY_UI_STAGE, mUiStage.name());
            outState.putString(KEY_FIRST_PIN, mFirstPin);
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode,
                Intent data) {
            if (requestCode == REQUEST_CODE && resultCode == RESULT_OK)
            {
            // Populate the wordsList with the String values the recognition engine thought it heard
                ArrayList<String> matches = data.getStringArrayListExtra(
                        RecognizerIntent.EXTRA_RESULTS);
                wordsList.setAdapter(new ArrayAdapter<String>(this,        
		android.R.layout.simple_list_item_1,
                matches));
            }
           super.onActivityResult(requestCode, resultCode, data);
            switch (requestCode) {
                case CONFIRM_EXISTING_REQUEST:
                    if (resultCode != Activity.RESULT_OK) {
                        getActivity().setResult(RESULT_FINISHED);
                        getActivity().finish();
                    }
                    break;
            }
        }

        protected void updateStage(Stage stage) {
            final Stage previousStage = mUiStage;
            mUiStage = stage;
            updateUi();

            // If the stage changed, announce the header for accessibility. This
            // is a no-op when accessibility is disabled.
            if (previousStage != stage) {
                mHeaderText.announceForAccessibility(mHeaderText.getText());
            }
        }


        private void handleNext() {
            final String pin = mPasswordEntry.getText().toString();
            if (TextUtils.isEmpty(pin)) {
                return;
            }
            String errorMsg = null;
            if (mUiStage == Stage.Introduction) {
                    mFirstPin = pin;
                    mPasswordEntry.setText("");
                    updateStage(Stage.NeedToConfirm);
                }
            } else if (mUiStage == Stage.NeedToConfirm) {
                if (mFirstPin.equals(pin)) {
                    final boolean isFallback = getActivity().getIntent().getBooleanExtra(
                            LockPatternUtils.LOCKSCREEN_BIOMETRIC_WEAK_FALLBACK, false);
                    mLockPatternUtils.clearLock(isFallback);
                    mLockPatternUtils.saveLockPassword(pin, mRequestedQuality, isFallback);
                    getActivity().finish();
                } else {
                    CharSequence tmp = mPasswordEntry.getText();
                    if (tmp != null) {
                        Selection.setSelection((Spannable) tmp, 0, tmp.length());
                    }
                    updateStage(Stage.ConfirmWrong);
                }
            }
            if (errorMsg != null) {
                showError(errorMsg, mUiStage);
            }
        }

        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.next_button:
                    handleNext();
                    break;

                case R.id.cancel_button:
                    getActivity().finish();
                    break;
            }
        }

        private void showError(String msg, final Stage next) {
            mHeaderText.setText(msg);
            mHeaderText.announceForAccessibility(mHeaderText.getText());
            Message mesg = mHandler.obtainMessage(MSG_SHOW_ERROR, next);
            mHandler.removeMessages(MSG_SHOW_ERROR);
            mHandler.sendMessageDelayed(mesg, ERROR_MESSAGE_TIMEOUT);
        }


        /**
         * Update the hint based on current Stage and length of password entry
         */
        private void updateUi() {
            String password = mPasswordEntry.getText().toString();
            final int length = password.length();
            if (mUiStage == Stage.Introduction && length > 0) {
                        mHeaderText.setText(R.string.lockpassword_press_continue);
                        mNextButton.setEnabled(true);
                }
            } else {
                mHeaderText.setText(mUiStage.alphaHint);
                mNextButton.setEnabled(length > 0);
            }
            mNextButton.setText(mUiStage.buttonText);
        }


    }
}
