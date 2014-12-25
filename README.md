bookz
=====

Android app for maintaining a Google Sheets-backed database of book locations.

Books are identified by their barcode (which equals the ISBN), while locations (shelves)
are identified using an NFC sticker placed on the shelf.

The app can be started by placing the Android device near an NFC sticker, which launches
the app and populates the Location field. Scanning a book barcode and then clicking on Save
will update that book's location to the chosen shelf.
