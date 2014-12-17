package zvikabh.bookz;

import java.io.Serializable;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.gdata.data.spreadsheet.CustomElementCollection;

public class Book implements Serializable {
	
	private Book() { }

	/**
	 * Creates a new Book object by loading the relevant columns from the given spreadsheet row.
	 */
	public static Book fromSpreadsheetRow(CustomElementCollection elements) {
		Book book = new Book();
		book.mAuthor = elements.getValue("author");
		book.mTitle = elements.getValue("title");
		book.mYear = elements.getValue("year");
		book.mISBN = elements.getValue("isbn");
		book.mBarcode = elements.getValue("barcode");
		book.mLocation = elements.getValue("location");
		book.mOwner = elements.getValue("owner");
		book.mNotes = elements.getValue("notes");
		return book;
	}
	
	/**
	 * Stores the Book object to a spreadsheet row.
	 */
	public void toSpreadsheetRow(CustomElementCollection elements) {
		elements.setValueLocal("author", mAuthor);
		elements.setValueLocal("title", mTitle);
		elements.setValueLocal("year", mYear);
		elements.setValueLocal("isbn", mISBN);
		elements.setValueLocal("barcode", mBarcode);
		elements.setValueLocal("location", mLocation);
		elements.setValueLocal("owner", mOwner);
		elements.setValueLocal("notes", mNotes);
	}

	public Map<String, String> toKeyValueMap() {
		return ImmutableMap.<String, String>builder()
				.put("author", mAuthor) 
				.put("title", mTitle)
				.put("year", mYear)
				.put("isbn", mISBN)
				.put("barcode", mBarcode)
				.put("location", mLocation)
				.put("owner", mOwner)
				.put("notes", mNotes)
				.build();
	}
	
	public String getAuthor() {
		return mAuthor;
	}

	public String getTitle() {
		return mTitle;
	}

	public String getYear() {
		return mYear;
	}
	
	public String getISBN() {
		return mISBN;
	}

	public String getBarcode() {
		return mBarcode;
	}

	public String getLocation() {
		return mLocation;
	}

	public String getOwner() {
		return mOwner;
	}

	public String getNotes() {
		return mNotes;
	}

	private String mAuthor;
	private String mTitle;
	private String mYear;
	private String mISBN;
	private String mBarcode;
	private String mLocation;
	private String mOwner;
	private String mNotes;

	private static final long serialVersionUID = -5127285917553084405L;

}
