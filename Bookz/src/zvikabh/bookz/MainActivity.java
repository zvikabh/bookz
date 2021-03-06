package zvikabh.bookz;

import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.HashMap;

import android.accounts.AccountManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.common.AccountPicker;
import com.google.common.collect.ImmutableList;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;


public class MainActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent intent = getIntent();
        updateShelfFromIntent(intent);
        
        mTextViewProgress = (TextView) findViewById(R.id.textViewProgress);
        
        Button buttonScan = (Button) findViewById(R.id.buttonScan);
        buttonScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new IntentIntegrator(MainActivity.this).initiateScan(BARCODE_TYPES);
            }
        });
        
        Button buttonStore = (Button) findViewById(R.id.buttonStore);
        buttonStore.setOnClickListener(new View.OnClickListener() {
            
            @Override
            public void onClick(View view) {
                storeChanges();
            }
        });

        if (savedInstanceState == null) {
            mTextViewProgress.setText("Authenticating...");
            getAuthToken();
        } else {
            mCurrentBook = (Book) savedInstanceState.getSerializable("mCurrentBook");
            mSheetsAccessor = (GoogleSheetsAccessor) savedInstanceState.getSerializable("mSheetsAccessor");
            mAuthToken = savedInstanceState.getString("mAuthToken");
            mUserEmail = savedInstanceState.getString("mUserEmail");
            
            @SuppressWarnings("unchecked")
            HashMap<String, Book> bookList = (HashMap<String, Book>) savedInstanceState.getSerializable("mBookList");
            mBookList = bookList;
        }
    }

    @Override
    protected void onSaveInstanceState (Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("mCurrentBook", mCurrentBook);
        outState.putSerializable("mBookList", mBookList);
        outState.putSerializable("mSheetsAccessor", mSheetsAccessor);
        outState.putString("mAuthToken", mAuthToken);
        outState.putString("mUserEmail", mUserEmail);
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

        EditText edittextCurrentShelf = (EditText) findViewById(R.id.editNewLocation);

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
                    edittextCurrentShelf.setText(decodedPayload);
                    return;
                }
            }
        }

        // Could not find the required payload.
        Toast.makeText(this, "Could not find the required payload in the NFC tag.", Toast.LENGTH_LONG).show();
    }
    
    private void authTokenReceived() {
        mTextViewProgress.setText("Authenticated!");
        
        if (mSheetsAccessor == null) {
            mSheetsAccessor = new GoogleSheetsAccessor(mAuthToken);
            mSheetsAccessor.initFromSpreadsheetTitle("Bookz Book List");
        } else {
            mSheetsAccessor.updateAuthToken(mAuthToken);
        }
        
        if (mBookList.size() == 0) {
            mTextViewProgress.setText("Authenticated, loading book list...");
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
            
        case IntentIntegrator.REQUEST_CODE:
            IntentResult scanResult = IntentIntegrator.parseActivityResult(
                    requestCode, resultCode, intent);
            if (resultCode == RESULT_OK && scanResult != null) {
                loadBookFromBarcode(scanResult.getContents());
            } else {
                Toast.makeText(this, "Scan failed.", Toast.LENGTH_SHORT).show();
            }
            break;
            
        default:
            Log.e(TAG, "Unexpected activity request code: " + requestCode);
            break;
        }
    }

    private void loadBookFromBarcode(String barcode) {
        // Remove checksum from barcode, as it is not listed in the spreadsheet.
        barcode = barcode.substring(0, barcode.length() - 1);
        
        if (!mBookList.containsKey(barcode)) {
            Log.w(TAG, "Barcode not found: '" + barcode + "'");
            Toast.makeText(this, "Barcode not found in database", Toast.LENGTH_LONG).show();
            mCurrentBook = null;
            populateField(R.id.editBookAuthor, "");
            populateField(R.id.editBookTitle, "");
            populateField(R.id.editBookYear, "");
            populateField(R.id.editBookISBN, "");
            populateField(R.id.editBookOwner, "");
            populateField(R.id.editBookNote, "");
            populateField(R.id.textviewBookOldLocation, "");
            return;
        }
        
        // Populate activity from loaded book.
        mCurrentBook = mBookList.get(barcode);
        populateField(R.id.editBookAuthor, mCurrentBook.getAuthor());
        populateField(R.id.editBookTitle, mCurrentBook.getTitle());
        populateField(R.id.editBookYear, mCurrentBook.getYear());
        populateField(R.id.editBookISBN, mCurrentBook.getISBN());
        populateField(R.id.editBookOwner, mCurrentBook.getOwner());
        populateField(R.id.editBookNote, mCurrentBook.getNotes());
        populateField(R.id.textviewBookOldLocation, mCurrentBook.getLocation());
    }

    private void populateField(int id, String newValue) {
        View view = findViewById(id);
        if (view instanceof EditText) {
            EditText editText = (EditText) view;
            editText.setText(newValue);
        } else if (view instanceof TextView) {
            TextView textView = (TextView) view;
            textView.setText(newValue);
        } else {
            throw new IllegalArgumentException("populateField received an invalid id");
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
    
    private void storeChanges() {
        if (mSheetsAccessor == null) {
            Toast.makeText(this, "Book list must be loaded in order to update it.", Toast.LENGTH_LONG).show();
            return;
        }
        if (mBookList == null) {
            Toast.makeText(this, "Book list must be loaded in order to update it.", Toast.LENGTH_LONG).show();
            return;
        }
        if (mCurrentBook == null) {
            Toast.makeText(this, "First scan a book in order to update it.", Toast.LENGTH_LONG).show();
            return;
        }

        mCurrentBook.setAuthor(getEditTextString(R.id.editBookAuthor));
        mCurrentBook.setISBN(getEditTextString(R.id.editBookISBN));
        mCurrentBook.setLocation(getEditTextString(R.id.editNewLocation));
        mCurrentBook.setNotes(getEditTextString(R.id.editBookNote));
        mCurrentBook.setOwner(getEditTextString(R.id.editBookOwner));
        mCurrentBook.setTitle(getEditTextString(R.id.editBookTitle));
        mCurrentBook.setYear(getEditTextString(R.id.editBookYear));
        
        mTextViewProgress.setText("Saving changes...");
        
        GoogleSheetsAccessor.CompletionListener completionListener = new GoogleSheetsAccessor.CompletionListener() {
            @Override
            public void done(boolean success) {
                if (success) {
                    Toast.makeText(MainActivity.this, "Database updated successfully", Toast.LENGTH_SHORT).show();
                    mTextViewProgress.setText("Database updated successfully.");
                } else {
                    Toast.makeText(MainActivity.this, "Database failed to update, try again", Toast.LENGTH_LONG).show();
                    mTextViewProgress.setText("Database update failed, please try again.");
                }
            }
        };
        mSheetsAccessor.asyncUpdateBooks(completionListener, mCurrentBook);
    }

    private String getEditTextString(int id) {
        EditText edittext = (EditText) findViewById(id);
        return edittext.getEditableText().toString();
    }

    public final static int REQUEST_AUTHORIZATION = 998;
    public final static int REQUEST_PICK_ACCOUNT = 999;

    private String mAuthToken = null;
    private String mUserEmail = null;
    private boolean mIsAuthTokenRetrievalInProgress = false;
    
    private GoogleSheetsAccessor mSheetsAccessor = null;
    
    private HashMap<String, Book> mBookList = new HashMap<String, Book>();
    private Book mCurrentBook;
    
    private TextView mTextViewProgress;

    private static final String TAG = "MainActivity";
    private static final Collection<String> BARCODE_TYPES = 
            new ImmutableList.Builder<String>().add("UPC_A").add("EAN_13").build();
}
