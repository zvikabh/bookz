package zvikabh.bookz;

import java.io.UnsupportedEncodingException;

import android.support.v7.app.ActionBarActivity;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        Log.w(TAG, "onCreate");
        
        Intent intent = getIntent();
        updateShelfFromIntent(intent);
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
    
    @Override
    protected void onNewIntent(Intent intent) {
    	super.onNewIntent(intent);
    	updateShelfFromIntent(intent);
    	setIntent(intent);
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
    
    private static final String TAG = "MainActivity";
}
