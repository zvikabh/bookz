package zvikabh.bookz;

import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.os.AsyncTask;
import android.util.Log;

import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.google.gdata.data.spreadsheet.CustomElementCollection;
import com.google.gdata.data.spreadsheet.ListEntry;
import com.google.gdata.data.spreadsheet.ListFeed;
import com.google.gdata.data.spreadsheet.SpreadsheetEntry;
import com.google.gdata.data.spreadsheet.SpreadsheetFeed;
import com.google.gdata.data.spreadsheet.WorksheetEntry;
import com.google.gdata.data.spreadsheet.WorksheetFeed;
import com.google.gdata.util.ServiceException;

public class GoogleSheetsAccessor implements Serializable {

	private static final long serialVersionUID = 1952623605676050453L;
	
	public GoogleSheetsAccessor(String authToken) {
		mInitStatus = InitStatus.UNINITIALIZED;
		mAuthToken = authToken;
	}
	
	/**
	 * Choose a spreadsheet to read from and write to, based on its feed URL.
	 */
	public void initFromSpreadsheetUrl(String spreadsheetUrl) {
		mSpreadsheetUrl = spreadsheetUrl;
		synchronized (this) {
			mInitStatus = InitStatus.INITIALIZED;
			notifyAll();
		}
	}

	/**
	 * Choose a spreadsheet to read from and write to, based on its title.
	 * The first matching spreadsheet will be used.
	 */
	public void initFromSpreadsheetTitle(String spreadsheetTitle) {
		Log.i("GoogleSheetsAccessor", "initFromSpreadsheetTitleOrCreate");
		synchronized (this) {
			mInitStatus = InitStatus.INITIALIZING;
		}
		new SpreadsheetFinder().execute(spreadsheetTitle);
		// When the spreadsheet is found or created, the async task will set 
		// mInitStatus to INITIALIZED and then call this.notifyAll().
	}

    public void updateAuthToken(String newAuthToken) {
        mAuthToken = newAuthToken;
    }
    
    /**
     * Interface for receiving a notification when an async task has completed.
     */
    public interface CompletionListener {
        /**
         * Called on the caller thread when the async task has completed.
         */
        void done(boolean success);
    }

    /**
	 * Starts an AsyncTask which reads all items in the Google Sheet
	 * and saves them in the specified target map.
	 */
	public void asyncLoadDataInto(Map<String, Book> target, CompletionListener completionListener) {
		new DataGetter(new MapDataGetterTarget(target), completionListener).execute();
	}
	
	/**
	 * Starts an AsyncTask which updates the specified books within the Google Sheet.
	 */
	public void asyncUpdateBooks(CompletionListener completionListener, Book... books) {
		new DataSetter(completionListener).execute(books);
	}
	
	private SpreadsheetService getSpreadsheetService() {
		SpreadsheetService service = new SpreadsheetService("GoogleSheetsAccessor");
		service.setAuthSubToken(mAuthToken);
		return service;
	}
	
	private ListFeed getListFeed() throws MalformedURLException, IOException, ServiceException, InterruptedException {
		SpreadsheetService service = getSpreadsheetService();
		
		// Verify that we are initialized, or wait for initialization to complete.
		switch (mInitStatus) {
		case UNINITIALIZED:
			throw new IllegalArgumentException("Attempting to load an uninitialized spreadsheet.");
		case INITIALIZING:
			Log.w("GoogleSheetsAccessor", "Waiting for initialization to complete...");
			wait();
			Log.w("GoogleSheetsAccessor", "Initialization is now complete!");
			break;
		case INITIALIZED:
			// we're fine, do nothing.
			break;
		}
		
		ListFeed listFeed;
		Log.i("GoogleSheetsAccessor", "mSpreadsheetUrl: [" + mSpreadsheetUrl + "]");
		listFeed = service.getFeed(new URL(mSpreadsheetUrl), ListFeed.class);
		
		Log.i(TAG, "Got list feed");
		
		return listFeed;
	}
	
	/**
	 * Interface for receiving data being read from the spreadsheet.
	 */
	private interface DataGetterTarget {
		abstract void add(Book book);
	}

	/**
	 * Implementation of DataGetterTarget which stores all loaded data in a barcode->Book map.
	 */
	private class MapDataGetterTarget implements DataGetterTarget {

		private Map<String, Book> mTarget;
		
		public MapDataGetterTarget(Map<String, Book> target) {
			mTarget = target;
		}

		@Override
		public void add(Book book) {
			mTarget.put(book.getBarcode(), book);
		}
		
	}
	
	/**
	 * Loads all books from the Google Sheet.
	 */
	private class DataGetter extends AsyncTask<Void, Void, List<Book>> {

	    public DataGetter(DataGetterTarget target, CompletionListener completionListener) {
			mTarget = target;
			mCompletionListener = completionListener;
		}
		
        private CompletionListener mCompletionListener;
		private DataGetterTarget mTarget;
		
		@Override
		protected List<Book> doInBackground(Void... params) {
			List<Book> books = new ArrayList<Book>();
			
			try {
				ListFeed bookList = getListFeed();
				Log.i("GoogleSheetsAccessor", "Successfully loaded barcode worksheet");
				for (ListEntry bookEntry : bookList.getEntries()) {
					CustomElementCollection elements = bookEntry.getCustomElements();
					Book book = Book.fromSpreadsheetRow(elements);
					books.add(book);
				}
				Log.i("GoogleSheetsAccessor", "Loaded " + books.size() + " products.");
			} catch (Exception e) {
				Log.e("GoogleSheetsAccessor", "Failed to load book list", e);
				return null;
			}
			
			return books;
		}

		@Override
		protected void onPostExecute(List<Book> products) {
			if (products == null) {
				// TODO: Handle loading errors
	            if (mCompletionListener != null) {
	                mCompletionListener.done(false);
	            }
				return;
			}
			
			for (Book book : products) {
				mTarget.add(book);
			}
			
			if (mCompletionListener != null) {
			    mCompletionListener.done(true);
			}
		}

	}
	
	/**
	 * Updates the given books in the Google Sheet.
	 */
	private class DataSetter extends AsyncTask<Book, Void, Boolean> {
	    
        public DataSetter(CompletionListener completionListener) {
	        mCompletionListener = completionListener;
	    }

		@Override
		protected Boolean doInBackground(Book... books) {
			// Build a map from barcode to Book.
			Map<String, Book> booksToUpdate = new HashMap<String, Book>();
			for (Book book : books) {
				booksToUpdate.put(book.getBarcode(), book);
			}
			
			// Go over the spreadsheet and update whatever needs updating.
			try {
				ListFeed bookList = getListFeed();
				for (ListEntry bookRow : bookList.getEntries()) {
					CustomElementCollection elements = bookRow.getCustomElements();
					String barcode = elements.getValue("barcode");
					if (booksToUpdate.containsKey(barcode)) {
						booksToUpdate.remove(barcode).toSpreadsheetRow(elements);
						bookRow.update();
					}
				}
			} catch (Exception e) {
				Log.e("GoogleSheetsAccessor", "Failed to update book spreadsheet", e);
				return false;
			}
			
			// Verify all books were saved.
			if (booksToUpdate.size() > 0) {
				StringBuilder sb = new StringBuilder();
				sb.append("The following barcodes were not found in the spreadsheet:");
				for (String barcode : booksToUpdate.keySet()) {
					sb.append(barcode + " ");
				}
				Log.e("GoogleSheetsAccessor", sb.toString());
				return false;
			}
			
			return true;
		}

        @Override
        protected void onPostExecute(Boolean result) {
            if (mCompletionListener != null) {
                mCompletionListener.done(result);
            }
        }
		
        private CompletionListener mCompletionListener;

	}
	
	private class SpreadsheetFinder extends AsyncTask<String, Void, Void> {

		@Override
		protected Void doInBackground(String... params) {
			String spreadsheetTitle = params[0];

			SpreadsheetService service = getSpreadsheetService();

			SpreadsheetFeed feed;
			try {
				feed = service.getFeed(new URL(SPREADSHEET_FEED_URL), SpreadsheetFeed.class);
			} catch (Exception e) {
				Log.e("SpreadsheetFinder", "Failed to load spreadsheet list", e);
				return null;
			}

			for (SpreadsheetEntry spreadsheet : feed.getEntries()) {
				String actualTitle = spreadsheet.getTitle().getPlainText();
				if (actualTitle.equals(spreadsheetTitle)) {
					try {
						WorksheetFeed worksheetFeed = service.getFeed(
						        spreadsheet.getWorksheetFeedUrl(), WorksheetFeed.class);
						List<WorksheetEntry> worksheets = worksheetFeed.getEntries();
						WorksheetEntry worksheet = worksheets.get(0);
						foundSpreadsheet(worksheet.getListFeedUrl());
					} catch (Exception e) {
						Log.e("SpreadsheetFinder", "Failed to load spreadsheet", e);
					}
					return null;
				}
			}

			// TODO: Spreadsheet not found; must create a new one, then call foundSpreadsheet on it.
			Log.e("SpreadsheetFinder", "Spreadsheet not found: [" + spreadsheetTitle + "]");
			
			return null;
		}
		
		private void foundSpreadsheet(URL listFeedUrl) {
			mSpreadsheetUrl = listFeedUrl.toString();
			Log.i("SpreadsheetFinder", "Found spreadsheet: " + mSpreadsheetUrl);
			synchronized (this) {
				mInitStatus = InitStatus.INITIALIZED;
				notifyAll();
			}
		}
		
		private static final String SPREADSHEET_FEED_URL = "https://spreadsheets.google.com/feeds/spreadsheets/private/full";
		
	}
	
	private String mSpreadsheetUrl;
	private String mAuthToken;
	
	private enum InitStatus { UNINITIALIZED, INITIALIZING, INITIALIZED };
	/**
	 * Indicates whether mSpreadsheetUrl points to a valid spreadsheet URL.
	 * Finding the correct URL may take a long time, in which case it is performed on
	 * a separate thread.
	 * this.notifyAll() is called when the state is changed to INITIALIZED.
	 */
	private InitStatus mInitStatus;
	
	private static final String TAG = "GoogleSheetsAccessor";
}
