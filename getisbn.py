#!/usr/bin/python

import csv
import json
import time
import urllib
import urllib2


def GetBookData(query, retry_attempts=2):
  """Queries Google Books and returns the search results.
  
  Returns:
    JSON object containing the search results.
  """
  time.sleep(1)  
  url = ('https://www.googleapis.com/books/v1/volumes?q=' +
         urllib.quote_plus(query))
  print '    URL: %s' % url
  try:
    stream = urllib2.urlopen(url)
    response = stream.read()
  except urllib2.HTTPError:
    if retry_attempts == 0:
      print '=== Too many HTTP failures, returning empty JSON object'
      return {}
    else:
      print '=== Received HTTP error, waiting 10 seconds and then retrying ==='
      time.sleep(10)
      return GetBookData(query, retry_attempts - 1)

  return json.loads(response)


def main():
  with open('booklist.csv') as fin:
    reader = csv.reader(fin)
    books = [book for book in reader]

  for book in books:
    if len(book) > 3 and book[3]:
      print 'Title "%s" already has ISBN %s' % (book[1], book[3])
      continue

    print 'Looking up title "%s"...' % book[1]
    book_data = GetBookData('"' + book[1] + '"')

    if ('items' not in book_data or len(book_data['items']) == 0 or
        'volumeInfo' not in book_data['items'][0]):
      print '=== Invalid response received:\n%s' % (
          json.dumps(book_data, indent=2))
      continue

    volume_data = book_data['items'][0]['volumeInfo']

    if 'industryIdentifiers' not in volume_data:
      print '=== Invalid volumeInfo response received:\n%s' % (
          json.dumps(book_data, indent=2, sort_keys=True))
      continue

    #retrieved_title = volume_data['title']
    #retrieved_authors = ', '.join(volume_data['authors'])
    retrieved_ids = volume_data['industryIdentifiers']
    retrieved_id_dict = {}
    for retrieved_id in retrieved_ids:
      retrieved_id_dict[retrieved_id['type']] = retrieved_id['identifier']
    if 'ISBN_13' in retrieved_id_dict:
      book[3] = retrieved_id_dict['ISBN_13']
    elif 'ISBN_10' in retrieved_id_dict:
      book[3] = retrieved_id_dict['ISBN_10']
    else:
      print '    Could not find ISBN in Google Books.'
      continue

    print '    Found ISBN "%s"' % book[3]


  with open('booklist_withisbn.csv', 'w') as fout:
    writer = csv.writer(fout)
    for book in books:
      writer.writerow(book)


if __name__ == '__main__':
  main()
