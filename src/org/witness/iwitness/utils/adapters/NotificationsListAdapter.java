package org.witness.iwitness.utils.adapters;

import java.util.List;

import org.witness.informacam.InformaCam;
import org.witness.informacam.models.notifications.INotification;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.TimeUtility;
import org.witness.iwitness.R;

import android.app.Activity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class NotificationsListAdapter extends BaseAdapter {
	List<INotification> notifications;
	
	private static final String LOG = App.LOG;
	
	public NotificationsListAdapter(List<INotification> notifications) {
		this.notifications = notifications;
		
	}
	@Override
	public int getCount() {
		return notifications.size();
	}

	@Override
	public Object getItem(int position) {
		return notifications.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		INotification notification = notifications.get(position);
		
		convertView = LayoutInflater.from(InformaCam.getInstance().a).inflate(R.layout.adapter_notification_list_item, null);
		
		TextView metadata = (TextView) convertView.findViewById(R.id.notification_metadata);
		StringBuffer sb = new StringBuffer();
		sb.append(TimeUtility.millisecondsToDatestamp(notification.timestamp));
		if(notification.from != null) {
			sb.append(System.getProperty("line.separator")).append(notification.from);
		}
		metadata.setText(sb.toString());
		
		if(notification.label != null) {
			TextView label = (TextView) convertView.findViewById(R.id.notification_label);
			label.setText(notification.label);
		}
		
		if(notification.content != null) {
			TextView content = (TextView) convertView.findViewById(R.id.notification_content);
			content.setText(notification.content);
		}
		
		return convertView;
	}

}