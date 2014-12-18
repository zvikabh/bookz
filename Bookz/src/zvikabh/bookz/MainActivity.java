package zvikabh.bookz;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import android.accounts.AccountManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Looper;
import android.os.Parcelable;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.common.AccountPicker;


public class MainActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.w(TAG, "onCreate");

        Intent intent = getIntent();
        updateShelfFromIntent(intent);
        
        mTextViewProgress = (TextView) findViewById(R.id.textViewProgress);
        mTextViewProgress.setText("Authenticating...");
        
        getAuthToken();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        updateShelfFromIntent(intent);
        setIntent(intent);
    }

    private void updateShelfFromIntent(Intent intent) {
        if (!intent.getAction().equals(NfcAdapter.ACTION_NDEF_DISCOVERED)) {
            Toast.makeText(this, "This application must be started using an NFC tag.", Toast.LENGTH_LONG).show();
            return;
        }

        TextView textviewCurrentShelf = (TextView) findViewById(R.id.textViewCurrentShelf);

        Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        for (Parcelable rawMsg : rawMsgs) {
            NdefMessage msg = (NdefMessage) rawMsg;
            for (NdefRecord record : msg.getRecords()) {
                byte[] payload = record.getPayload();
                byte[] payloadType = record.getType();
                String decodedPayload;
                String decodedType;
                try {
                    decodedPayload = new String(payload, "UTF-8");
                    decodedType = new String(payloadType, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    Log.e("MainActivity", "Invalid payload or type: " + e.getMessage());
                    continue;
                }

                if (decodedType.equals("text/bookz")) {
                    // Found our payload.
                    textviewCurrentShelf.setText(decodedPayload);
                    return;
                }
            }
        }

        // Could not find the required payload.
        Toast.makeText(this, "Could not find the required payload in the NFC tag.", Toast.LENGTH_LONG).show();
    }
    
    private void authTokenReceived() {
        mTextViewProgress.setText("Authenticated, loading book list...");
        
        if (mSheetsAccessor == null) {
            mSheetsAccessor = new GoogleSheetsAccessor(mAuthToken);
            mSheetsAccessor.initFromSpreadsheetTitle("Bookz Book List");
        } else {
            mSheetsAccessor.updateAuthToken(mAuthToken);
        }
        
        mSheetsAccessor.asyncLoadDataInto(mBookList, new GoogleSheetsAccessor.CompletionListener() {
            
            @Override
            public void done(boolean success) {
                if (success) {
                    mTextViewProgress.setText("Ready! " + mBookList.size() + " books loaded.");
                } else {
                    mTextViewProgress.setText("Error loading book list.");
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
        case REQUEST_PICK_ACCOUNT:
            mIsAuthTokenRetrievalInProgress = false;
            if (resultCode == RESULT_OK) {
                setUserEmail(intent.getStringExtra(AccountManager.KEY_ACCOUNT_NAME));
                // With the account name acquired, go get the auth token.
                getAuthToken();
            } else {
                // The account picker dialog closed without selecting an
                // account.
                // Notify users that they must pick an account to proceed.
                Toast.makeText(this, "Error: Must pick an account",
                        Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Error: must pick an account");
            }
            return;
            
        case REQUEST_AUTHORIZATION:
            mIsAuthTokenRetrievalInProgress = false;
            if (resultCode == RESULT_OK) {
                Log.i(TAG, "User authorized. Trying to get auth token again.");
                getAuthToken();
            } else {
                Toast.makeText(this, "Error: Access request was not authorized", Toast.LENGTH_LONG).show();
            }
            break;
            
        default:
            Log.e(TAG, "Unexpected activity request code: " + requestCode);
            break;
        }
    }

    private void setUserEmail(String userEmail) {
        mUserEmail = userEmail;
        
        // Store user's choice in shared preferences, for use next time the app is run.
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        prefs.edit().putString("mUserEmail", userEmail).commit();    
    }
    
    void clearAuthToken() {
        if (mIsAuthTokenRetrievalInProgress) {
            Log.w(TAG, "Token not cleared as we are in the process of getting a new token.");
            return;  // We are anyway in the process of getting a new token.
        }
        
        try {
            GoogleAuthUtil.clearToken(this, mAuthToken);
        } catch (Exception e) {
            Log.e(TAG, "Could not clear token", e);
        }
        
        mAuthToken = null;
    }

    String getAuthToken() {
        if (mIsAuthTokenRetrievalInProgress) {
            // Currently retrieving auth token, but it is not ready yet.
            return null;
        }

        if (mAuthToken == null) {
            // Begin offline process for receiving a new auth token.
            mIsAuthTokenRetrievalInProgress = true;
            if (mUserEmail == null) {
                pickUserAccount();
                return null;
            }

            AsyncTask<Void, Void, String> asyncTask = new AsyncTask<Void, Void, String>() {

                @Override
                protected String doInBackground(Void... params) {
                    final String AUTH_SCOPES = "oauth2:https://spreadsheets.google.com/feeds https://docs.google.com/feeds";
                    try {
                        String authToken = GoogleAuthUtil.getToken(MainActivity.this, mUserEmail, AUTH_SCOPES);
                        return authToken;
                    } catch(UserRecoverableAuthException e) {
                        startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION);
                    } catch(Exception e) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "Error while getting auth token", Toast.LENGTH_SHORT).show();
                            }
                        });
                        
                        Log.e(TAG, "Error while getting auth token", e);
                        return FAILURE;
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(String authToken) {
                    if (authToken == null) {
                        // Retrieval still in progress. Do nothing.
                        return;
                    }
                    
                    if (authToken.equals(FAILURE)) {
                        // Retrieval failed.
                        mIsAuthTokenRetrievalInProgress = false;
                        return;
                    }
                    
                    if (authToken.equals(mAuthToken)) {
                        // No change in auth token.
                        mIsAuthTokenRetrievalInProgress = false;
                        return;
                    }
                    
                    // New auth token received.
                    Log.i(TAG, "New auth token received");
                    mAuthToken = authToken;
                    mIsAuthTokenRetrievalInProgress = false;
                    authTokenReceived();
                }
                
                private static final String FAILURE = "books:Failed to authenticate";
                
            };
            asyncTask.execute((Void) null);
            return null;
        }

        // Token is available. Return it.
        return mAuthToken;
    }
    
    private void pickUserAccount() {
        // First see if the user email was saved in a previous run of the app.
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        mUserEmail = prefs.getString("mUserEmail", null);
        if (mUserEmail != null) {
            Log.i(TAG, "Loaded user email from prefs: [" + mUserEmail + "]");
            
            // Continue the process of getting the auth token.
            mIsAuthTokenRetrievalInProgress = false;
            getAuthToken();
            return;
        }
        
        // No user selection. Show the account picker activity.
        String[] accountTypes = new String[] { "com.google" };
        Intent intent = AccountPicker.newChooseAccountIntent(null, null,
                accountTypes, false, null, null, null, null);
        startActivityForResult(intent, REQUEST_PICK_ACCOUNT);
        
        // Getting auth token will continue after the user chooses an account.
    }

    public final static int REQUEST_AUTHORIZATION = 998;
    public final static int REQUEST_PICK_ACCOUNT = 999;

    private String mAuthToken = null;
    private String mUserEmail = null;
    private boolean mIsAuthTokenRetrievalInProgress = false;
    
    private GoogleSheetsAccessor mSheetsAccessor = null;
    
    private Map<String, Book> mBookList = new HashMap<String, Book>();
    
    private TextView mTextViewProgress;

    private static final String TAG = "MainActivity";
}
