/*
 * Copyright 2012 Google Inc. All Rights Reserved.
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

package io.csabatechnology.android.omnipresent;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import io.csabatechnology.android.omnipresent.util.IabBroadcastReceiver;
import io.csabatechnology.android.omnipresent.util.IabBroadcastReceiver.IabBroadcastListener;
import io.csabatechnology.android.omnipresent.util.IabHelper;
import io.csabatechnology.android.omnipresent.util.IabHelper.IabAsyncInProgressException;
import io.csabatechnology.android.omnipresent.util.IabResult;
import io.csabatechnology.android.omnipresent.util.Inventory;
import io.csabatechnology.android.omnipresent.util.Purchase;

/**
 * Example using in-app billing version 3.
 */
public class MainActivity extends Activity implements IabBroadcastListener,
        OnClickListener {
    // Debug tag, for logging
    static final String TAG = "Omnipresent";

    // SKUs for our products
    static final String SKU_HAND = "hand";
    static final String SKU_FLOWER = "flower";
    static final String SKU_FLOWERS = "flowers";
    static final String SKU_BELLA = "bella";
    static final String SKU_LOTUS = "lotus";

    // (arbitrary) request code for the purchase flow
    static final int RC_REQUEST = 10001;

    // The helper object
    IabHelper mHelper;

    // Provides purchase notification while this app is running
    IabBroadcastReceiver mBroadcastReceiver;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        /* base64EncodedPublicKey should be YOUR APPLICATION'S PUBLIC KEY
         * (that you got from the Google Play developer console). This is not your
         * developer public key, it's the *app-specific* public key.
         *
         * Instead of just storing the entire literal string here embedded in the
         * program,  construct the key at runtime from pieces or
         * use bit manipulation (for example, XOR with some other string) to hide
         * the actual key.  The key itself is not secret information, but we don't
         * want to make it easy for an attacker to replace the public key with one
         * of their own and then fake messages from the server.
         */
        String base64EncodedPublicKey = "BAQADIw2K0+AB8fE4XPTUVdVaXR3XxB5jy7DIPhRHMtP7TECA8AInYtK+Q0PEddSxONPW9ONdnemQ3ppy7tfeQp9vPB6uuHyzzuOKI/V4vK8Dvbm2SembCMPMJcOCQEbrtojhYrzyq2v43sgMdI5wxpOrqnjUuOATp+ml9kduJMzj1VWHjAKX45PubIrWG6NPIHDD+BFlRK+1kF+Xb4i+zKAYVHexqU588kD8oIK8wrFNGkLBNMOFsqkTn/BoQfSeOMbfudpM/gRFi2NNl7LR7aZfkhoX1/FKZvYhgRku9R/3HjicnkvYBB5P8UXlfghbvT+AhZ1c/0C3YYRH062zImeHzgoAEQACKgCBIIMA8QACOAAFEQAB0w9GikhqkgBNAjIBIIM";
        String parameter = new StringBuffer(base64EncodedPublicKey).reverse().toString();

        // Create the helper, passing it our context and the public key to verify signatures with
        Log.d(TAG, "Creating IAB helper.");
        mHelper = new IabHelper(this, parameter);

        // enable debug logging (for a production application, you should set this to false).
        mHelper.enableDebugLogging(true);

        // Start setup. This is asynchronous and the specified listener
        // will be called once setup completes.
        Log.d(TAG, "Starting setup.");
        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result) {
                Log.d(TAG, "Setup finished.");

                if (!result.isSuccess()) {
                    // Oh noes, there was a problem.
                    complain("Problem setting up in-app billing: " + result);
                    return;
                }

                // Have we been disposed of in the meantime? If so, quit.
                if (mHelper == null) return;

                // Important: Dynamically register for broadcast messages about updated purchases.
                // We register the receiver here instead of as a <receiver> in the Manifest
                // because we always call getPurchases() at startup, so therefore we can ignore
                // any broadcasts sent while the app isn't running.
                // Note: registering this listener in an Activity is a bad idea, but is done here
                // because this is a SAMPLE. Regardless, the receiver must be registered after
                // IabHelper is setup, but before first call to getPurchases().
                mBroadcastReceiver = new IabBroadcastReceiver(MainActivity.this);
                IntentFilter broadcastFilter = new IntentFilter(IabBroadcastReceiver.ACTION);
                registerReceiver(mBroadcastReceiver, broadcastFilter);

                // IAB is fully set up. Now, let's get an inventory of stuff we own.
                Log.d(TAG, "Setup successful. Querying inventory.");
                try {
                    mHelper.queryInventoryAsync(mGotInventoryListener);
                } catch (IabAsyncInProgressException e) {
                    complain("Error querying inventory. Another async operation in progress.");
                }
            }
        });
    }

    // Listener that's called when we finish querying the items and subscriptions we own
    IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
        public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
            Log.d(TAG, "Query inventory finished.");

            // Have we been disposed of in the meantime? If so, quit.
            if (mHelper == null) return;

            // Is it a failure?
            if (result.isFailure()) {
                complain("Failed to query inventory: " + result);
                return;
            }

            Log.d(TAG, "Query inventory was successful.");

            /*
             * Check for items we own. Notice that for each purchase, we check
             * the developer payload to see if it's correct! See
             * verifyDeveloperPayload().
             */

            String[] skus = {SKU_HAND, SKU_FLOWER, SKU_FLOWERS, SKU_BELLA, SKU_LOTUS};
            for (String sku: skus) {
                Purchase consumablePurchase = inventory.getPurchase(sku);
                if (consumablePurchase != null && verifyDeveloperPayload(consumablePurchase)) {
                    Log.d(TAG, "Purchasing " + sku + ", consuming it.");
                    try {
                        mHelper.consumeAsync(inventory.getPurchase(sku),
                                mConsumeFinishedListener);
                    } catch (IabAsyncInProgressException e) {
                        complain("Error consuming " + sku +
                                ". Another async operation in progress.");
                    }
                }
            }

            setWaitScreen(false);
            Log.d(TAG, "Initial inventory query finished; enabling main UI.");
        }
    };

    @Override
    public void receivedBroadcast() {
        // Received a broadcast notification that the inventory of items has changed
        Log.d(TAG, "Received broadcast notification. Querying inventory.");
        try {
            mHelper.queryInventoryAsync(mGotInventoryListener);
        } catch (IabAsyncInProgressException e) {
            complain("Error querying inventory. Another async operation in progress.");
        }
    }

    // User clicked on one fo the consumable buttons
    public void onBuyConsumableButtonClicked(View arg0, String sku) {
        Log.d(TAG, "Buy " + sku + " button clicked.");

        // launch the purchase UI flow.
        // We will be notified of completion via mPurchaseFinishedListener
        setWaitScreen(true);
        Log.d(TAG, "Launching purchase flow for consumable.");

        /* TODO: for security, generate your payload here for verification. See the comments on
         *        verifyDeveloperPayload() for more info. Since this is a SAMPLE, we just use
         *        an empty string, but on a production app you should carefully generate this. */
        String payload = "";

        try {
            mHelper.launchPurchaseFlow(this, sku, RC_REQUEST,
                    mPurchaseFinishedListener, payload);
        } catch (IabAsyncInProgressException e) {
            complain("Error launching purchase flow. Another async operation in progress.");
            setWaitScreen(false);
        }
    }

    public void onBuyHandButtonClicked(View arg0) {
        onBuyConsumableButtonClicked(arg0, SKU_HAND);
    }

    public void onBuyFlowerButtonClicked(View arg0) {
        onBuyConsumableButtonClicked(arg0, SKU_FLOWER);
    }

    public void onBuyFlowersButtonClicked(View arg0) {
        onBuyConsumableButtonClicked(arg0, SKU_FLOWERS);
    }

    public void onBuyBellaButtonClicked(View arg0) {
        onBuyConsumableButtonClicked(arg0, SKU_BELLA);
    }

    public void onBuyLotusButtonClicked(View arg0) {
        onBuyConsumableButtonClicked(arg0, SKU_LOTUS);
    }

    public void onAttributionsButtonClicked(View arg0) {
        alert(getString(R.string.attributions_text));
    }

    @Override
    public void onClick(DialogInterface dialog, int id) {
        if (id == DialogInterface.BUTTON_POSITIVE /* continue button */) {
            /* TODO: for security, generate your payload here for verification. See the comments on
             *        verifyDeveloperPayload() for more info. Since this is a SAMPLE, we just use
             *        an empty string, but on a production app you should carefully generate
             *        this. */
            String payload = "";
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult(" + requestCode + "," + resultCode + "," + data);
        if (mHelper == null) return;

        // Pass on the activity result to the helper for handling
        if (!mHelper.handleActivityResult(requestCode, resultCode, data)) {
            // not handled, so handle it ourselves (here's where you'd
            // perform any handling of activity results not related to in-app
            // billing...
            super.onActivityResult(requestCode, resultCode, data);
        }
        else {
            Log.d(TAG, "onActivityResult handled by IABUtil.");
        }
    }

    /** Verifies the developer payload of a purchase. */
    boolean verifyDeveloperPayload(Purchase p) {
        String payload = p.getDeveloperPayload();

        /*
         * TODO: verify that the developer payload of the purchase is correct. It will be
         * the same one that you sent when initiating the purchase.
         *
         * WARNING: Locally generating a random string when starting a purchase and
         * verifying it here might seem like a good approach, but this will fail in the
         * case where the user purchases an item on one device and then uses your app on
         * a different device, because on the other device you will not have access to the
         * random string you originally generated.
         *
         * So a good developer payload has these characteristics:
         *
         * 1. If two different users purchase an item, the payload is different between them,
         *    so that one user's purchase can't be replayed to another user.
         *
         * 2. The payload must be such that you can verify it even when the app wasn't the
         *    one who initiated the purchase flow (so that items purchased by the user on
         *    one device work on other devices owned by the user).
         *
         * Using your own server to store and verify developer payloads across app
         * installations is recommended.
         */

        return true;
    }

    // Callback for when a purchase is finished
    IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
        public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
            Log.d(TAG, "Purchase finished: " + result + ", purchase: " + purchase);

            // if we were disposed of in the meantime, quit.
            if (mHelper == null) return;

            if (result.isFailure()) {
                complain("Error purchasing: " + result);
                setWaitScreen(false);
                return;
            }
            if (!verifyDeveloperPayload(purchase)) {
                complain("Error purchasing. Authenticity verification failed.");
                setWaitScreen(false);
                return;
            }

            Log.d(TAG, "Purchase successful.");

            String sku = purchase.getSku();
            Log.d(TAG, "Purchase is " + sku + ". Starting " + sku + " consumption.");
            try {
                mHelper.consumeAsync(purchase, mConsumeFinishedListener);
            } catch (IabAsyncInProgressException e) {
                complain("Error consuming " + sku + ". Another async operation in progress.");
                setWaitScreen(false);
            }
        }
    };

    // Called when consumption is complete
    IabHelper.OnConsumeFinishedListener mConsumeFinishedListener = new IabHelper.OnConsumeFinishedListener() {
        public void onConsumeFinished(Purchase purchase, IabResult result) {
            Log.d(TAG, "Consumption finished. Purchase: " + purchase + ", result: " + result);

            // if we were disposed of in the meantime, quit.
            if (mHelper == null) return;

            if (result.isSuccess()) {
                // successfully consumed, so we apply the effects
                Log.d(TAG, "Consumption successful. Provisioning.");
                // TODO: play music here depending on the consumed resource
                int rid = 0;
                switch(purchase.getSku()) {
                    case SKU_HAND: rid = R.raw.ommm; break;
                    case SKU_FLOWER: rid = R.raw.gong_soft; break;
                    case SKU_FLOWERS: rid = R.raw.gong_hits_soft; break;
                    case SKU_BELLA: rid = R.raw.loud_gong; break;
                    case SKU_LOTUS: rid = R.raw.golden_temple; break;
                }
                new AudioPlayer().play(getBaseContext(), rid);
            }
            else {
                complain("Error while consuming: " + result);
            }

            setWaitScreen(false);
            alert("Namaste! Thank You for your support!");  // TODO
            Log.d(TAG, "End consumption flow.");
        }
    };

    // We're being destroyed. It's important to dispose of the helper here!
    @Override
    public void onDestroy() {
        super.onDestroy();

        // very important:
        if (mBroadcastReceiver != null) {
            unregisterReceiver(mBroadcastReceiver);
        }

        // very important:
        Log.d(TAG, "Destroying helper.");
        if (mHelper != null) {
            mHelper.disposeWhenFinished();
            mHelper = null;
        }
    }

    // Enables or disables the "please wait" screen.
    void setWaitScreen(boolean set) {
        findViewById(R.id.screen_main).setVisibility(set ? View.GONE : View.VISIBLE);
        findViewById(R.id.screen_wait).setVisibility(set ? View.VISIBLE : View.GONE);
    }

    void complain(String message) {
        Log.e(TAG, "**** Omnipresent Error: " + message);
        alert("Error: " + message);
    }

    void alert(String message) {
        AlertDialog.Builder bld = new AlertDialog.Builder(this);
        bld.setMessage(message);
        bld.setNeutralButton("OK", null);
        Log.d(TAG, "Showing alert dialog: " + message);
        bld.create().show();
    }
}
