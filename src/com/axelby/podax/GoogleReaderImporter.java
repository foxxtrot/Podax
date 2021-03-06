package com.axelby.podax;

import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;
import java.util.Vector;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.sax.Element;
import android.sax.ElementListener;
import android.sax.RootElement;
import android.sax.TextElementListener;
import android.util.Xml;
import android.widget.Toast;

import com.google.api.client.googleapis.GoogleHeaders;
import com.google.api.client.googleapis.GoogleTransport;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpTransport;

public class GoogleReaderImporter {

	static class ReaderFeed {
		public String url = "";
		public String title = "";
		public Vector<String> categories = new Vector<String>();
	}

	public static boolean doImport(final Activity activity, String authToken) {
		HttpTransport transport = GoogleTransport.create();

		GoogleHeaders headers = (GoogleHeaders) transport.defaultHeaders;
		headers.setApplicationName("Podax");
		headers.gdataVersion = "2";
		headers.setGoogleLogin(authToken);

		HttpRequest request = transport.buildGetRequest();
		request.url = new GenericUrl("http://www.google.com/reader/api/0/subscription/list");
		try {
			final Hashtable<String, Vector<ReaderFeed>> categories = new Hashtable<String, Vector<ReaderFeed>>();
			final Vector<ReaderFeed> nofolder = new Vector<ReaderFeed>();
			// put these in an array to we can use them in the inner class
			final ReaderFeed[] feed = new ReaderFeed[] { new ReaderFeed() };

			// set up the sax parser
			RootElement root = new RootElement("object");
			Element object = root.getChild("list").getChild("object");
			object.setElementListener(new ElementListener() {
				public void start(Attributes attrs) {
					feed[0] = new ReaderFeed();
				}
				public void end() {
					if (feed[0].categories.size() == 0)
						nofolder.add(feed[0]);
				}
			});

			object.getChild("string").setTextElementListener(new TextElementListener() {
				String name = null;
				public void start(Attributes attributes) {
					name = attributes.getValue("name");
				}

				public void end(String text) {
					if (name == null)
						return;
					if (name.equals("id")) {
						if (text != null && text.startsWith("feed/"))
							text = text.substring(5);
						feed[0].url = text;
					}
					else if (name.equals("title")) {
						feed[0].title = text;
					}
				}
			});

			final String[] listName = { "" };
			object.getChild("list").setElementListener(new ElementListener() {
				public void start(Attributes attributes) {
					if (attributes.getValue("name").equals("categories"))
						listName[0] = "categories";
				}
				public void end() {
					listName[0] = "";
				}
			});
			object.getChild("list").getChild("object").getChild("string").setTextElementListener(new TextElementListener() {
				boolean isLabel = false;
				public void start(Attributes attributes) {
					// make sure we're in a category and have a string name
					if (!listName[0].equals("categories") || attributes.getValue("name") == null)
						return;
					if (attributes.getValue("name").equals("label"))
						isLabel = true;
				}
				public void end(String text) {
					if (isLabel) {
						feed[0].categories.add(text);
						if (!categories.containsKey(text))
							categories.put(text, new Vector<ReaderFeed>());
						categories.get(text).add(feed[0]);
					}
					isLabel = false;
				}
			});

			InputStream response = request.execute().getContent();
			Xml.parse(response, Xml.Encoding.UTF_8, root.getContentHandler());

			/*
			for (ReaderFeed f : feeds) {
				Log.d("Podax", "feed title: " + f.title + ", url: " + f.url + ", categories: " + f.categories.size());
				for (String cat : f.categories)
					Log.d("Podax", cat);
			}
			*/

			Vector<String> options = new Vector<String>();
			// add folders to list
			for (String cat : categories.keySet())
				if (!options.contains(cat))
					options.add(cat);
			// add podcasts not in a folder
			for (ReaderFeed f : nofolder)
				options.add(f.title);

			final String[] itemText = options.toArray(new String[] { });

			final boolean[] checkedItems = new boolean[itemText.length];
			for (int i = 0; i < itemText.length; ++i)
				checkedItems[i] = true;

			AlertDialog.Builder builder = new AlertDialog.Builder(activity);
			builder.setTitle("Google Reader Folders");
			builder.setMultiChoiceItems(itemText, checkedItems, new DialogInterface.OnMultiChoiceClickListener() {
				public void onClick(DialogInterface dialog, int which, boolean isChecked) {
					checkedItems[which] = isChecked;
				}
			});
			builder.setPositiveButton("Import", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					for (int i = 0; i < itemText.length; ++i) {
						if (checkedItems[i]) {
							if (i < categories.keySet().size()) {
								for (ReaderFeed feed : categories.get(itemText[i]))
									addReaderFeedToDB(activity, feed);
							} else {
								for (ReaderFeed feed : nofolder)
									if (feed.title.equals(itemText[i]))
										addReaderFeedToDB(activity, feed);
							}
						}
					}

					// close the activity and go to the subscription list
					Toast.makeText(activity, "Google Reader subscriptions imported", Toast.LENGTH_LONG).show();
					activity.finish();
					activity.startActivity(new Intent(activity, SubscriptionListActivity.class));
				}
			});
			builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					dialog.cancel();
				}
			});

			AlertDialog dialog = builder.create();
			dialog.show();

		} catch (IOException e) {
			e.printStackTrace();
			return false;
		} catch (SAXException e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}

	private static void addReaderFeedToDB(Context context, ReaderFeed feed) {
		ContentValues values = new ContentValues();
		values.put(SubscriptionProvider.COLUMN_TITLE, feed.title);
		values.put(SubscriptionProvider.COLUMN_URL, feed.url);
		Uri uri = context.getContentResolver().insert(SubscriptionProvider.URI, values);
		int subscriptionId = Integer.valueOf(uri.getLastPathSegment());
		UpdateService.updateSubscription(context, subscriptionId);
	}

}
