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
		setValue(elements, "author", mAuthor);
		setValue(elements, "title", mTitle);
		setValue(elements, "year", mYear);
		setValue(elements, "isbn", mISBN);
		setValue(elements, "barcode", mBarcode);
		setValue(elements, "location", mLocation);
		setValue(elements, "owner", mOwner);
		setValue(elements, "notes", mNotes);
	}
	
	private static void setValue(CustomElementCollection elements, String key, String value) {
	    elements.setValueLocal(key, value == null ? "" : value);
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
	
    public void setAuthor(String author) {
        this.mAuthor = author;
    }

    public void setTitle(String title) {
        this.mTitle = title;
    }

    public void setYear(String year) {
        this.mYear = year;
    }

    public void setISBN(String isbn) {
        this.mISBN = isbn;
    }

    public void setBarcode(String barcode) {
        this.mBarcode = barcode;
    }

    public void setLocation(String location) {
        this.mLocation = location;
    }

    public void setOwner(String owner) {
        this.mOwner = owner;
    }

    public void setNotes(String notes) {
        this.mNotes = notes;
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
