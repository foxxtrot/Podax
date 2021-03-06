package com.axelby.podax;

import java.io.File;

import android.app.ListActivity;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

public class SubscriptionListActivity extends ListActivity {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.subscription_list);

		Intent intent = getIntent();
		// check if this was opened by android to save an RSS feed
		if (intent.getDataString() != null) {
			ContentValues values = new ContentValues();
			values.put(SubscriptionProvider.COLUMN_URL, intent.getDataString());
			Uri savedSubscription = getContentResolver().insert(SubscriptionProvider.URI, values);
			UpdateService.updateSubscription(this, Integer.valueOf(savedSubscription.getLastPathSegment()));
		}

		String[] projection = {
				SubscriptionProvider.COLUMN_ID,
				SubscriptionProvider.COLUMN_TITLE,
				SubscriptionProvider.COLUMN_URL
		};
		Cursor c = managedQuery(SubscriptionProvider.URI, projection, null, null, null);
		setListAdapter(new SubscriptionAdapter(this, c));
		registerForContextMenu(getListView());

		// remove any subscription update errors
		String ns = Context.NOTIFICATION_SERVICE;
		NotificationManager notificationManager = (NotificationManager) getSystemService(ns);
		notificationManager.cancel(Constants.SUBSCRIPTION_UPDATE_ERROR);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.subscription_list_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case R.id.refresh_subscriptions:
			UpdateService.updateSubscriptions(this);
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		menu.add(0, 0, 0, R.string.unsubscribe);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case 0:
			AdapterView.AdapterContextMenuInfo menuInfo = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
			Cursor cursor = (Cursor) getListAdapter().getItem(menuInfo.position);
			SubscriptionCursor subscription = new SubscriptionCursor(this, cursor);
			getContentResolver().delete(subscription.getContentUri(), null, null);
			break;
		default:
			return super.onContextItemSelected(item);
		}
		return true;
	}

	@Override
	protected void onListItemClick(ListView list, View view, int position, long id) {
		Intent intent = new Intent(this, PodcastListActivity.class);
		SubscriptionCursor sub = new SubscriptionCursor(this, (Cursor)list.getItemAtPosition(position));
		intent.putExtra("subscriptionId", (int)(long)sub.getId());
		startActivity(intent);
	}

	private class SubscriptionAdapter extends ResourceCursorAdapter {
		public SubscriptionAdapter(Context context, Cursor cursor) {
			super(context, R.layout.subscription_list_item, cursor);
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor) {
			SubscriptionCursor subscription = new SubscriptionCursor(context, cursor);

			TextView text = (TextView)view.findViewById(R.id.text);
			ImageView thumbnail = (ImageView)view.findViewById(R.id.thumbnail);

			text.setText(subscription.getTitle());

			File thumbnailFile = new File(subscription.getThumbnailFilename());
			if (!thumbnailFile.exists())
				thumbnail.setImageDrawable(null);
			else
			{
				thumbnail.setImageBitmap(BitmapFactory.decodeFile(subscription.getThumbnailFilename()));
				thumbnail.setVisibility(1);
			}
		}
	}
}