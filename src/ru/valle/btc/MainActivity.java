/**
 The MIT License (MIT)

 Copyright (c) 2013 Valentin Konovalov

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.*/

package ru.valle.btc;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MainActivity extends Activity {

    private static final int REQUEST_SCAN_PRIVATE_KEY = 0;
    private static final int REQUEST_SCAN_RECIPIENT_ADDRESS = 1;
    private EditText addressView;
    private TextView privateKeyTypeView;
    private EditText privateKeyTextEdit;
    private View sendLayout;
    private TextView rawTxDescriptionView;
    private EditText rawTxToSpendEdit;
    private TextView recipientAddressView;
    private EditText amountEdit;
    private TextView spendTxDescriptionView;
    private TextView spendTxEdit;
    private View generateButton;

    private boolean insertingPrivateKeyProgrammatically, insertingAddressProgrammatically, changingTxProgrammatically;
    private AsyncTask<Void, Void, KeyPair> addressGenerateTask;
    private AsyncTask<Void, Void, GenerateTransactionResult> generateTransactionTask;
    private AsyncTask<Void, Void, KeyPair> switchingCompressionTypeTask;
    private AsyncTask<String, Void, KeyPair> decodePrivateKeyTask;

    private KeyPair currentKeyPair;
    private View scanPrivateKeyButton, scanRecipientAddressButton;
    private View enterPrivateKeyAck;
    private View rawTxToSpendPasteButton;
    private ClipboardManager.OnPrimaryClipChangedListener clipboardListener;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        addressView = (EditText) findViewById(R.id.address_label);
        generateButton = findViewById(R.id.generate_button);
        privateKeyTypeView = (TextView) findViewById(R.id.private_key_type_label);
        privateKeyTypeView.setMovementMethod(LinkMovementMethod.getInstance());
        privateKeyTextEdit = (EditText) findViewById(R.id.private_key_label);

        sendLayout = findViewById(R.id.send_layout);
        rawTxToSpendPasteButton = findViewById(R.id.paste_tx_button);
        rawTxToSpendEdit = (EditText) findViewById(R.id.raw_tx);
        recipientAddressView = (TextView) findViewById(R.id.recipient_address);
        amountEdit = (EditText) findViewById(R.id.amount);
        rawTxDescriptionView = (TextView) findViewById(R.id.raw_tx_description);
        spendTxDescriptionView = (TextView) findViewById(R.id.spend_tx_description);
        spendTxEdit = (TextView) findViewById(R.id.spend_tx);
        scanPrivateKeyButton = findViewById(R.id.scan_private_key_button);
        scanRecipientAddressButton = findViewById(R.id.scan_recipient_address_button);
        enterPrivateKeyAck = findViewById(R.id.enter_private_key_to_spend_desc);

        wireListeners();
        generateNewAddress();
    }


    @Override
    protected void onResume() {
        super.onResume();

        CharSequence textInClipboard = getTextInClipboard();
        boolean hasTextInClipboard = !TextUtils.isEmpty(textInClipboard);
        if (Build.VERSION.SDK_INT >= 11) {
            if (!hasTextInClipboard) {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.addPrimaryClipChangedListener(clipboardListener = new ClipboardManager.OnPrimaryClipChangedListener() {
                    @Override
                    public void onPrimaryClipChanged() {
                        rawTxToSpendPasteButton.setEnabled(!TextUtils.isEmpty(getTextInClipboard()));
                    }
                });
            }
            rawTxToSpendPasteButton.setEnabled(hasTextInClipboard);
        } else {
            rawTxToSpendPasteButton.setVisibility(hasTextInClipboard ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (Build.VERSION.SDK_INT >= 11 && clipboardListener != null) {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.removePrimaryClipChangedListener(clipboardListener);
        }
    }


    @SuppressWarnings("deprecation")
    private String getTextInClipboard() {
        CharSequence textInClipboard = "";
        if (Build.VERSION.SDK_INT >= 11) {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) && clipboard.getPrimaryClip().getItemCount() > 0) {
                ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
                textInClipboard = item.getText();
            }
        } else {
            android.text.ClipboardManager clipboard = (android.text.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard.hasText()) {
                textInClipboard = clipboard.getText();
            }
        }
        return textInClipboard == null ? "" : textInClipboard.toString();
    }

    private void wireListeners() {
        addressView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!insertingAddressProgrammatically) {
                    insertingPrivateKeyProgrammatically = true;
                    privateKeyTextEdit.setText("");
                    insertingPrivateKeyProgrammatically = false;
                    cancelAllRunningTasks();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        generateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                generateNewAddress();
            }
        });
        privateKeyTextEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!insertingPrivateKeyProgrammatically) {
                    cancelAllRunningTasks();
                    insertingAddressProgrammatically = true;
                    setTextWithoutJumping(addressView, getString(R.string.decoding));
                    insertingAddressProgrammatically = false;
                    decodePrivateKeyTask = new AsyncTask<String, Void, KeyPair>() {
                        @Override
                        protected KeyPair doInBackground(String... params) {
                            try {
                                BTCUtils.PrivateKeyInfo privateKeyInfo = BTCUtils.decodePrivateKey(params[0]);
                                if (privateKeyInfo != null) {
                                    return new KeyPair(privateKeyInfo);
                                }
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                            return null;
                        }

                        @Override
                        protected void onPostExecute(KeyPair key) {
                            super.onPostExecute(key);
                            decodePrivateKeyTask = null;
                            onKeyPairModify(key);
                        }
                    };
                    decodePrivateKeyTask.execute(s.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        TextWatcher generateTransactionOnInputChangeTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!changingTxProgrammatically) {
                    generateSpendingTransaction(rawTxToSpendEdit.getText().toString(), recipientAddressView.getText().toString(), amountEdit.getText().toString(), currentKeyPair);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        };
        rawTxToSpendPasteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rawTxToSpendEdit.setText(getTextInClipboard());
            }
        });
        rawTxToSpendEdit.addTextChangedListener(generateTransactionOnInputChangeTextWatcher);
        recipientAddressView.addTextChangedListener(generateTransactionOnInputChangeTextWatcher);
        amountEdit.addTextChangedListener(generateTransactionOnInputChangeTextWatcher);
        scanPrivateKeyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(MainActivity.this, ScanActivity.class), REQUEST_SCAN_PRIVATE_KEY);
            }
        });
        scanRecipientAddressButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(MainActivity.this, ScanActivity.class), REQUEST_SCAN_RECIPIENT_ADDRESS);
            }
        });

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            scanPrivateKeyButton.setVisibility(View.GONE);
            scanRecipientAddressButton.setVisibility(View.GONE);
        }
    }

    private void onNewKeyPairGenerated(KeyPair key) {
        insertingAddressProgrammatically = true;
        if (key != null) {
            addressView.setText(key.address);
            privateKeyTypeView.setVisibility(View.VISIBLE);
            privateKeyTypeView.setText(getPrivateKeyTypeLabel(key));
            insertingPrivateKeyProgrammatically = true;
            privateKeyTextEdit.setText(key.privateKey.privateKeyEncoded);
            insertingPrivateKeyProgrammatically = false;
        } else {
            privateKeyTypeView.setVisibility(View.GONE);
            addressView.setText(getString(R.string.generating_failed));
        }
        insertingAddressProgrammatically = false;
        showSpendPanelForKeyPair(null);//generated address does not have funds to spend yet
    }

    private void onKeyPairModify(KeyPair keyPair) {
        insertingAddressProgrammatically = true;
        if (keyPair != null) {
            addressView.setText(keyPair.address);
            privateKeyTypeView.setVisibility(View.VISIBLE);
            privateKeyTypeView.setText(getPrivateKeyTypeLabel(keyPair));
        } else {
            privateKeyTypeView.setVisibility(View.GONE);
            addressView.setText(getString(R.string.bad_private_key));
        }
        insertingAddressProgrammatically = false;
        showSpendPanelForKeyPair(keyPair);
    }

    private void cancelAllRunningTasks() {
        if (addressGenerateTask != null) {
            addressGenerateTask.cancel(true);
            addressGenerateTask = null;
        }
        if (generateTransactionTask != null) {
            generateTransactionTask.cancel(true);
            generateTransactionTask = null;
        }
        if (switchingCompressionTypeTask != null) {
            switchingCompressionTypeTask.cancel(false);
            switchingCompressionTypeTask = null;
        }
        if (decodePrivateKeyTask != null) {
            decodePrivateKeyTask.cancel(true);
            decodePrivateKeyTask = null;
        }
    }

    static class GenerateTransactionResult {
        static final int ERROR_SOURCE_UNKNOWN = 0;
        static final int ERROR_SOURCE_INPUT_TX_FIELD = 1;
        static final int ERROR_SOURCE_ADDRESS_FIELD = 2;
        static final int HINT_FOR_ADDRESS_FIELD = 3;
        static final int ERROR_SOURCE_AMOUNT_FIELD = 4;

        final Transaction tx;
        final String errorMessage;
        final int errorSource;
        private final long availableAmountToSend;
        final long fee;

        public GenerateTransactionResult(String errorMessage, int errorSource, long availableAmountToSend) {
            tx = null;
            this.errorMessage = errorMessage;
            this.errorSource = errorSource;
            this.availableAmountToSend = availableAmountToSend;
            fee = -1;
        }

        public GenerateTransactionResult(Transaction tx, long fee) {
            this.tx = tx;
            errorMessage = null;
            errorSource = ERROR_SOURCE_UNKNOWN;
            availableAmountToSend = -1;
            this.fee = fee;
        }
    }

    private void generateSpendingTransaction(final String baseTxStr, final String outputAddress, final String requestedAmountToSendStr, final KeyPair keyPair) {
        rawTxToSpendEdit.setError(null);
        recipientAddressView.setError(null);
        spendTxDescriptionView.setVisibility(View.GONE);
        spendTxEdit.setVisibility(View.GONE);
        cancelAllRunningTasks();
        if (!(TextUtils.isEmpty(baseTxStr) && TextUtils.isEmpty(outputAddress)) && keyPair != null && keyPair.privateKey != null) {

            generateTransactionTask = new AsyncTask<Void, Void, GenerateTransactionResult>() {

                @Override
                protected GenerateTransactionResult doInBackground(Void... voids) {
                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                    long fee = (long) (Double.parseDouble(preferences.getString(PreferencesActivity.PREF_FEE, Double.toString(FeePreference.PREF_FEE_DEFAULT))) * 1e8);
                    Transaction baseTx = null;
                    int indexOfOutputToSpend = -1;
                    long availableAmountToSend = -1;
                    long requestedAmountToSend = -1;
                    if (!TextUtils.isEmpty(baseTxStr)) {
                        try {
                            byte[] rawTx = BTCUtils.fromHex(baseTxStr);
                            baseTx = new Transaction(rawTx);
                            byte[] rawTxReconstructed = baseTx.getBytes();
                            if (!Arrays.equals(rawTxReconstructed, rawTx)) {
                                throw new IllegalArgumentException("Unable to decode given transaction");
                            }
                        } catch (Exception e) {
                            return new GenerateTransactionResult(getString(R.string.error_unable_to_decode_transaction), GenerateTransactionResult.ERROR_SOURCE_INPUT_TX_FIELD, availableAmountToSend);
                        }

                        try {
                            indexOfOutputToSpend = BTCUtils.findSpendableOutput(baseTx, keyPair.address, fee);
                            if (indexOfOutputToSpend >= 0) {
                                availableAmountToSend = baseTx.outputs[indexOfOutputToSpend].value - fee;
                                if (TextUtils.isEmpty(requestedAmountToSendStr)) {
                                    requestedAmountToSend = availableAmountToSend;
                                } else {
                                    try {
                                        requestedAmountToSend = (long) (Double.parseDouble(requestedAmountToSendStr) * 1e8);
                                    } catch (Exception e) {
                                        return new GenerateTransactionResult(getString(R.string.error_amount_parsing), GenerateTransactionResult.ERROR_SOURCE_AMOUNT_FIELD, availableAmountToSend);
                                    }
                                }
                                if (requestedAmountToSend > availableAmountToSend) {
                                    return new GenerateTransactionResult(getString(R.string.error_not_enough_funds), GenerateTransactionResult.ERROR_SOURCE_AMOUNT_FIELD, availableAmountToSend);
                                }
                                if (requestedAmountToSend <= fee) {
                                    return new GenerateTransactionResult(getString(R.string.error_amount_to_send_less_than_fee), GenerateTransactionResult.ERROR_SOURCE_AMOUNT_FIELD, availableAmountToSend);
                                }
                            }
                        } catch (Exception e) {
                            return new GenerateTransactionResult(e.getMessage(), GenerateTransactionResult.ERROR_SOURCE_INPUT_TX_FIELD, availableAmountToSend);
                        }
                    }
                    if (TextUtils.isEmpty(outputAddress)) {
                        if (indexOfOutputToSpend >= 0 && baseTx != null) {
                            return new GenerateTransactionResult(getString(R.string.enter_address_to_spend), GenerateTransactionResult.HINT_FOR_ADDRESS_FIELD, availableAmountToSend);
                        } else {
                            return null;
                        }
                    }
                    if (!BTCUtils.verifyBitcoinAddress(outputAddress)) {
                        return new GenerateTransactionResult(getString(R.string.invalid_address), GenerateTransactionResult.ERROR_SOURCE_ADDRESS_FIELD, availableAmountToSend);
                    }
                    if (baseTx == null || indexOfOutputToSpend == -1) {
                        return new GenerateTransactionResult(getString(R.string.error_no_transaction), GenerateTransactionResult.ERROR_SOURCE_INPUT_TX_FIELD, availableAmountToSend);
                    }
                    final Transaction spendTx;
                    try {
                        spendTx = BTCUtils.createTransaction(baseTx, indexOfOutputToSpend, outputAddress, keyPair.address, requestedAmountToSend, fee, keyPair.publicKey, keyPair.privateKey);
                        BTCUtils.verify(baseTx.outputs[indexOfOutputToSpend].script, spendTx);
                    } catch (Exception e) {
                        return new GenerateTransactionResult(getString(R.string.error_failed_to_create_transaction), GenerateTransactionResult.ERROR_SOURCE_UNKNOWN, availableAmountToSend);
                    }
                    return new GenerateTransactionResult(spendTx, fee);
                }

                @Override
                protected void onPostExecute(GenerateTransactionResult result) {
                    super.onPostExecute(result);
                    generateTransactionTask = null;
                    if (result != null) {
                        if (result.tx != null) {
                            String amount = null;
                            Transaction.Script out = Transaction.Script.buildOutput(outputAddress);
                            if (result.tx.outputs[0].script.equals(out)) {
                                amount = BTCUtils.formatValue(result.tx.outputs[0].value);
                            }
                            if (amount == null) {
                                rawTxToSpendEdit.setError(getString(R.string.error_unknown));
                            } else {
                                changingTxProgrammatically = true;
                                amountEdit.setText(amount);
                                changingTxProgrammatically = false;
                                if (result.tx.outputs.length == 1) {
                                    spendTxDescriptionView.setText(getString(R.string.spend_tx_description,
                                            amount,
                                            keyPair.address,
                                            outputAddress,
                                            BTCUtils.formatValue(result.fee)
                                    ));
                                } else if (result.tx.outputs.length == 2) {
                                    spendTxDescriptionView.setText(getString(R.string.spend_tx_with_change_description,
                                            amount,
                                            keyPair.address,
                                            outputAddress,
                                            BTCUtils.formatValue(result.fee),
                                            BTCUtils.formatValue(result.tx.outputs[1].value)
                                    ));
                                } else {
                                    throw new RuntimeException();
                                }
                                spendTxDescriptionView.setVisibility(View.VISIBLE);
                                spendTxEdit.setText(BTCUtils.toHex(result.tx.getBytes()));
                                spendTxEdit.setVisibility(View.VISIBLE);
                            }
                        } else if (result.errorSource == GenerateTransactionResult.ERROR_SOURCE_INPUT_TX_FIELD) {
                            rawTxToSpendEdit.setError(result.errorMessage);
                        } else if (result.errorSource == GenerateTransactionResult.ERROR_SOURCE_ADDRESS_FIELD ||
                                result.errorSource == GenerateTransactionResult.HINT_FOR_ADDRESS_FIELD) {
                            recipientAddressView.setError(result.errorMessage);
                        }

                        if (result.errorSource == GenerateTransactionResult.ERROR_SOURCE_AMOUNT_FIELD) {
                            amountEdit.setError(result.errorMessage);
                        } else {
                            amountEdit.setError(null);
                        }

                        if (result.availableAmountToSend > 0 && amountEdit.getText().length() == 0) {
                            changingTxProgrammatically = true;
                            amountEdit.setText(BTCUtils.formatValue(result.availableAmountToSend));
                            changingTxProgrammatically = false;
                        }
                    }
                }
            }.execute();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        addressView.setMinLines(1);
        privateKeyTextEdit.setMinLines(1);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ?
                    PreferencesActivity.class : PreferencesActivityForOlderDevices.class));
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            String scannedResult = data.getStringExtra("data");
            String address = scannedResult;
            String privateKey = scannedResult;
            String amount = null;
            String message = "";
            if (scannedResult.startsWith(SCHEME_BITCOIN)) {
                scannedResult = scannedResult.substring(SCHEME_BITCOIN.length());
                privateKey = "";
                int queryStartIndex = scannedResult.indexOf('?');
                if (queryStartIndex == -1) {
                    address = scannedResult;
                } else {
                    address = scannedResult.substring(0, queryStartIndex);
                    String queryStr = scannedResult.substring(queryStartIndex + 1);
                    Map<String, String> query = splitQuery(queryStr);
                    String amountStr = query.get("amount");
                    if (!TextUtils.isEmpty(amountStr)) {
                        try {
                            amount = BTCUtils.formatValue(BTCUtils.parseValue(amountStr));
                        } catch (NumberFormatException e) {
                            Log.e("PaperWallet", "unable to parse " + amountStr);
                        }
                    }
                    StringBuilder messageSb = new StringBuilder();
                    String label = query.get("label");
                    if (!TextUtils.isEmpty(label)) {
                        messageSb.append(label);
                    }
                    String messageParam = query.get("message");
                    if (!TextUtils.isEmpty(messageParam)) {
                        if (messageSb.length() > 0) {
                            messageSb.append(": ");
                        }
                        messageSb.append(messageParam);
                    }
                    message = messageSb.toString();
                }
            }
            if (requestCode == REQUEST_SCAN_PRIVATE_KEY) {
                if (!TextUtils.isEmpty(privateKey)) {
                    privateKeyTextEdit.setText(privateKey);
                }
            } else if (requestCode == REQUEST_SCAN_RECIPIENT_ADDRESS) {
                recipientAddressView.setText(address);
                if (!TextUtils.isEmpty(amount)) {
                    amountEdit.setText(amount);
                }
                if (!TextUtils.isEmpty(message)) {
                    Toast.makeText(MainActivity.this, message, message.length() > 20 ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private static final String SCHEME_BITCOIN = "bitcoin:";

    private static Map<String, String> splitQuery(String query) {
        Map<String, String> query_pairs = new LinkedHashMap<String, String>();
        String[] pairs = query.split("&");
        try {
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                query_pairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"), URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return query_pairs;
    }

    private void generateNewAddress() {
        if (addressGenerateTask == null) {
            cancelAllRunningTasks();
            insertingPrivateKeyProgrammatically = true;
            setTextWithoutJumping(privateKeyTextEdit, "");
            insertingPrivateKeyProgrammatically = false;
            insertingAddressProgrammatically = true;
            setTextWithoutJumping(addressView, getString(R.string.generating));
            insertingAddressProgrammatically = false;
            addressGenerateTask = new AsyncTask<Void, Void, KeyPair>() {
                @Override
                protected KeyPair doInBackground(Void... params) {
                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                    String privateKeyType = preferences.getString(PreferencesActivity.PREF_PRIVATE_KEY, PreferencesActivity.PREF_PRIVATE_KEY_MINI);
                    if (PreferencesActivity.PREF_PRIVATE_KEY_WIF_COMPRESSED.equals(privateKeyType)) {
                        return BTCUtils.generateWifKey(true);
                    } else {
                        return BTCUtils.generateMiniKey();
                    }
                }

                @Override
                protected void onPostExecute(final KeyPair key) {
                    addressGenerateTask = null;
                    onNewKeyPairGenerated(key);
                }
            }.execute();
        }
    }

    private void setTextWithoutJumping(EditText editText, String text) {
        int lineCountBefore = editText.getLineCount();
        editText.setText(text);
        if (editText.getLineCount() < lineCountBefore) {
            editText.setMinLines(lineCountBefore);
        }
    }

    private CharSequence getPrivateKeyTypeLabel(final KeyPair keyPair) {
        int typeWithCompression = keyPair.privateKey.type == BTCUtils.PrivateKeyInfo.TYPE_BRAIN_WALLET && keyPair.privateKey.isPublicKeyCompressed ? keyPair.privateKey.type + 1 : keyPair.privateKey.type;
        CharSequence keyType = getResources().getTextArray(R.array.private_keys_types)[typeWithCompression];
        SpannableString keyTypeLabel = new SpannableString(getString(R.string.private_key_type, keyType));

        if (keyPair.privateKey.type == BTCUtils.PrivateKeyInfo.TYPE_BRAIN_WALLET) {
            String compressionStrToSpan = keyType.toString().substring(keyType.toString().indexOf(',') + 2);
            int start = keyTypeLabel.toString().indexOf(compressionStrToSpan);
            if (start >= 0) {

                ClickableSpan switchPublicKeyCompressionSpan = new ClickableSpan() {
                    @Override
                    public void onClick(View widget) {
                        cancelAllRunningTasks();
                        switchingCompressionTypeTask = new AsyncTask<Void, Void, KeyPair>() {

                            @Override
                            protected KeyPair doInBackground(Void... params) {
                                return new KeyPair(new BTCUtils.PrivateKeyInfo(keyPair.privateKey.type, keyPair.privateKey.privateKeyEncoded, keyPair.privateKey.privateKeyDecoded, !keyPair.privateKey.isPublicKeyCompressed));
                            }

                            @Override
                            protected void onPostExecute(KeyPair keyPair) {
                                switchingCompressionTypeTask = null;
                                onKeyPairModify(keyPair);
                            }
                        };
                        switchingCompressionTypeTask.execute();
                    }
                };
                keyTypeLabel.setSpan(switchPublicKeyCompressionSpan, start, start + compressionStrToSpan.length(), SpannableStringBuilder.SPAN_INCLUSIVE_INCLUSIVE);
            }
        }
        return keyTypeLabel;
    }

    private void showSpendPanelForKeyPair(KeyPair keyPair) {
        currentKeyPair = keyPair;
        if (keyPair == null) {
            rawTxToSpendEdit.setText("");
        } else {
            String descStr = getString(R.string.raw_tx_description, keyPair.address);
            final SpannableStringBuilder builder = new SpannableStringBuilder(descStr);
            int spanBegin = descStr.indexOf(keyPair.address);
            if (spanBegin >= 0) {
                ForegroundColorSpan addressColorSpan = new ForegroundColorSpan(getResources().getColor(R.color.dark_orange));
                builder.setSpan(addressColorSpan, spanBegin, spanBegin + keyPair.address.length(), SpannableStringBuilder.SPAN_INCLUSIVE_INCLUSIVE);
            }
            setUrlSpanForAddress("blockexplorer.com", keyPair.address, builder);
            setUrlSpanForAddress("blockchain.info", keyPair.address, builder);
            rawTxDescriptionView.setText(builder);
            rawTxDescriptionView.setMovementMethod(LinkMovementMethod.getInstance());
        }
        sendLayout.setVisibility(keyPair != null ? View.VISIBLE : View.GONE);
        enterPrivateKeyAck.setVisibility(keyPair == null ? View.VISIBLE : View.GONE);
    }

    private static void setUrlSpanForAddress(String domain, String address, SpannableStringBuilder builder) {
        int spanBegin = builder.toString().indexOf(domain);
        if (spanBegin >= 0) {
            URLSpan urlSpan = new URLSpan("http://" + domain + "/address/" + address);
            builder.setSpan(urlSpan, spanBegin, spanBegin + domain.length(), SpannableStringBuilder.SPAN_INCLUSIVE_INCLUSIVE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelAllRunningTasks();
    }
}
