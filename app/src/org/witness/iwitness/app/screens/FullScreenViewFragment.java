package org.witness.iwitness.app.screens;

import info.guardianproject.odkparser.FormWrapper.ODKFormListener;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.witness.informacam.InformaCam;
import org.witness.informacam.models.media.IMedia;
import org.witness.informacam.models.media.IRegion;
import org.witness.informacam.models.media.IRegionBounds;
import org.witness.informacam.ui.IRegionDisplay;
import org.witness.informacam.ui.IRegionDisplay.IRegionDisplayListener;
import org.witness.informacam.utils.Constants.Models.IMedia.MimeType;
import org.witness.iwitness.R;
import org.witness.iwitness.app.EditorActivity;
import org.witness.iwitness.app.screens.editors.FullScreenVideoViewFragment;
import org.witness.iwitness.app.screens.forms.TagFormFragment;
import org.witness.iwitness.app.screens.popups.WaitPopup;
import org.witness.iwitness.utils.Constants.App;
import org.witness.iwitness.utils.Constants.Codes;
import org.witness.iwitness.utils.Constants.EditorActivityListener;
import org.witness.iwitness.utils.actions.ContextMenuAction;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class FullScreenViewFragment extends Fragment implements OnClickListener, OnTouchListener, IRegionDisplayListener, ODKFormListener  {
	protected View rootView;
	protected Activity a;

	protected InformaCam informaCam;

	protected ImageButton toggleControls;
	protected LinearLayout controlsHolder;
	protected RelativeLayout mediaHolder;
	protected ScrollView scrollRoot;

	protected boolean controlsAreShowing = false;

	protected FrameLayout formHolder;
	protected Fragment tagFormFragment;
	protected RelativeLayout mediaHolderParent;

	protected int[] dims;
	protected int scrollTarget;

	protected IMedia media;
	protected IRegion currentRegion = null;

	public int DEFAULT_REGION_WIDTH, DEFAULT_REGION_HEIGHT;

	protected boolean isEditingForm = false;
	protected Handler h = new Handler();

	private OnTouchListener noScroll = new OnTouchListener() {

		@Override
		public boolean onTouch(View v, MotionEvent event) {
			return true;
		}

	};

	private OnTouchListener withScroll = new OnTouchListener() {

		@Override
		public boolean onTouch(View v, MotionEvent event) {
			//Log.d(LOG, "scrolling to: " + scrollRoot.getScrollY() + " (from scrollTarget " + scrollTarget + ")");

			if(scrollRoot.getScrollY() < scrollTarget/2) {
				if(isEditingForm) {
					((TagFormFragment) tagFormFragment).saveTagFormData(currentRegion);
					isEditingForm = false;
				}
			}

			if(scrollRoot.getScrollY() == 0) {
				scrollRoot.setOnTouchListener(noScroll);
				scrollRoot.scrollTo(0, 0);
				return true;
			}

			return false;
		}

	};

	protected List<ContextMenuAction> controls = new ArrayList<ContextMenuAction>();
	protected final static String LOG = App.Editor.LOG;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public void onDestroy() {
		if(isEditingForm) {
			((TagFormFragment) tagFormFragment).saveTagFormData(currentRegion);
			isEditingForm = false;
		}


		super.onDestroy();
	}

	@Override
	public View onCreateView(LayoutInflater li, ViewGroup container, Bundle savedInstanceState) {
		super.onCreateView(li, container, savedInstanceState);
		dims = InformaCam.getInstance().getDimensions();

		rootView = li.inflate(R.layout.fragment_editor_fullscreen_view, null);

		scrollRoot = (ScrollView) rootView.findViewById(R.id.scroll_root);
		scrollRoot.setOnTouchListener(noScroll);

		toggleControls = (ImageButton) rootView.findViewById(R.id.toggle_controls);
		toggleControls.setOnClickListener(this);

		int controlHolder = R.id.controls_holder_portrait;

		scrollTarget = dims[1];
		DEFAULT_REGION_WIDTH = (int) (dims[0] * 0.2);
		DEFAULT_REGION_HEIGHT = (int) (dims[1] * 0.3);

		if(getArguments().getInt(Codes.Extras.SET_ORIENTATION) == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
			controlHolder = R.id.controls_holder_landscape;

			scrollTarget = dims[0];
			DEFAULT_REGION_WIDTH = (int) (dims[0] * 0.3);
			DEFAULT_REGION_HEIGHT = (int) (dims[1] * 0.2);
		}

		controlsHolder = (LinearLayout) rootView.findViewById(controlHolder);

		mediaHolderParent = (RelativeLayout) rootView.findViewById(R.id.media_holder_parent);

		mediaHolder = (RelativeLayout) rootView.findViewById(R.id.media_holder);
		mediaHolder.setOnTouchListener(this);

		formHolder = (FrameLayout) rootView.findViewById(R.id.fullscreen_form_holder);

		return rootView;
	}

	protected void initLayout() {
		mediaHolderParent.setLayoutParams(new LinearLayout.LayoutParams(dims[0], dims[1]));
		showForms();
		registerControls();
		toggleControls();
	}

	protected void deleteTag() {
		media.removeRegion(currentRegion);
		mediaHolder.removeView(currentRegion.getRegionDisplay());
		currentRegion = null;
	}

	protected void initRegions() {
		mediaHolder.setOnTouchListener(this);
		mediaHolder.setOnClickListener(this);
		toggleControls.setOnClickListener(this);
		
		if(media.associatedRegions != null) {
			for(IRegion r : media.associatedRegions) {
				Log.d(LOG, "setting old region: " + r.asJson().toString());
				if(r.bounds.displayWidth != 0 && r.bounds.displayHeight != 0) {
					r.init(r.bounds, false);
					r.getRegionDisplay().setOnTouchListener(this);
					mediaHolder.addView(r.getRegionDisplay());
				} else {
					Log.d(LOG, "skipping this one, it is top-level");
				}
			}

			updateRegionDisplay();
		}
		
		h.post(new Runnable() {
			@Override
			public void run() {
				((EditorActivityListener) a).waiter(false);
			}
		});
		
	}

	protected void updateRegionDisplay() {
		for(IRegion r : media.associatedRegions) {
			
			if(!r.equals(currentRegion) && r.getRegionDisplay() != null) {
				r.getRegionDisplay().setStatus(false);
			}
		}

		if(currentRegion == null) {
			h.post(new Runnable() {
				@Override
				public void run() {
					toggleControls.setVisibility(View.INVISIBLE);
				}
			});
		} else {
			h.post(new Runnable() {
				@Override
				public void run() {
					toggleControls.setVisibility(View.VISIBLE);
				}
			});
		}
	}

	protected void registerControls() {
		controls.clear();

		ContextMenuAction action = new ContextMenuAction();
		action.label = a.getString(R.string.notes);
		action.iconResource = R.drawable.ic_edit_notes;
		action.ocl = new OnClickListener() {

			@Override
			public void onClick(View v) {
				if(currentRegion != null) {
					toggleControls();
					showForm();
				}
			}
		};
		controls.add(action);

		action = new ContextMenuAction();
		action.label = a.getString(R.string.delete_tag);
		action.iconResource = R.drawable.ic_edit_delete;
		action.ocl = new OnClickListener() {

			@Override
			public void onClick(View v) {
				if(currentRegion != null) {
					toggleControls();
					deleteTag();
					updateRegionDisplay();
				}
			}
		};
		controls.add(action);

		for(ContextMenuAction cma : controls) {
			View control = LayoutInflater.from(a).inflate(R.layout.adapter_context_menu_editor, null);
			control.setLayoutParams(toggleControls.getLayoutParams());

			ImageView icon = (ImageView) control.findViewById(R.id.context_menu_item_icon);
			icon.setImageDrawable(a.getResources().getDrawable(cma.iconResource));

			TextView label = (TextView) control.findViewById(R.id.context_menu_item_label);
			label.setText(cma.label);

			control.setOnClickListener(cma.ocl);
			controlsHolder.addView(control);
		}

	}

	public void doScroll() {
		scrollRoot.scrollTo(0, scrollTarget);
		scrollRoot.setOnTouchListener(withScroll);
		isEditingForm = true;
	}

	protected void showForm() {
		if(((TagFormFragment) tagFormFragment).initTag(currentRegion)) {
			doScroll();
		}
	}

	protected void showForms() {
		tagFormFragment = Fragment.instantiate(a, TagFormFragment.class.getName());

		FragmentTransaction ft = ((EditorActivity) a).fm.beginTransaction();
		ft.add(R.id.fullscreen_form_holder, tagFormFragment);
		ft.addToBackStack(null);
		ft.commit();
	}

	protected void setCurrentRegion(IRegion region) {
		setCurrentRegion(region, false);
	}

	protected void setCurrentRegion(IRegion region, boolean isNew) {

		Log.d(LOG, "this region: " + region.asJson().toString());
		
		currentRegion = region;
		currentRegion.getRegionDisplay().setOnTouchListener(this);
		scrollRoot.setOnTouchListener(noScroll);

		if(isNew) {
			mediaHolder.addView(currentRegion.getRegionDisplay());
		}

		updateRegionDisplay();

		toggleControls(true);
	}

	@Override
	public void onAttach(Activity a) {
		super.onAttach(a);
		this.a = a;

		informaCam = InformaCam.getInstance();
		media = ((EditorActivity) a).media;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		if(getArguments().getInt(Codes.Extras.SET_ORIENTATION) == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
			controlsHolder = (LinearLayout) rootView.findViewById(R.id.controls_holder_landscape);
		} else {
			controlsHolder = (LinearLayout) rootView.findViewById(R.id.controls_holder_portrait);
		}
		
		h.post(new Runnable() {
			@Override
			public void run() {
				((EditorActivityListener) a).waiter(true);
			}
		});

		initLayout();
	}

	protected void toggleControls() {
		toggleControls(false);
	}

	protected void toggleControls(boolean forceShow) {
		int d = R.drawable.ic_edit_show_tags;
		if(forceShow) {
			controlsAreShowing = false;
		}

		if(controlsAreShowing) {
			controlsHolder.setVisibility(View.GONE);
			d = R.drawable.ic_edit_hide_tags;
			controlsAreShowing = false;
		} else {
			controlsHolder.setVisibility(View.VISIBLE);
			controlsAreShowing = true;
		}

		toggleControls.setImageDrawable(a.getResources().getDrawable(d));
	}

	@Override
	public void onClick(View v) {
		if(v == toggleControls) {
			toggleControls();
		}
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		//Log.d(LOG, "on touch called at " + event.getX() + "," + event.getY() + "\n on view: " + v.getClass().getName() + " (id " + v.getId() + ")\naction: " + event.getAction());
		v.getParent().requestDisallowInterceptTouchEvent(true);

		if(v instanceof IRegionDisplay) {
			float lastX = 0;
			float lastY = 0;

			switch(event.getAction()) {
			case MotionEvent.ACTION_DOWN:

				lastX = event.getX();
				lastY = event.getY();
				break;
			case MotionEvent.ACTION_MOVE:

				final float x = event.getX();
				final float y = event.getY();

				final float dX = (x - lastX) - (DEFAULT_REGION_WIDTH/2);
				final float dY = (y - lastY) - (DEFAULT_REGION_HEIGHT/2);

				IRegionBounds bounds = ((IRegionDisplay) v).bounds;
				bounds.displayLeft += dX;
				bounds.displayTop += dY;

				lastX = x;
				lastY = y;

				((IRegionDisplay) v).update();


				break;
			case MotionEvent.ACTION_UP:
				v.performClick();
				break;
			}
		} else {
			currentRegion = null;
			if(event.getAction() == MotionEvent.ACTION_UP){
				try {
					if(media.dcimEntry.mediaType.equals(MimeType.IMAGE)) {
						setCurrentRegion(media.addRegion((int) event.getY() - (DEFAULT_REGION_HEIGHT/2), (int) event.getX() - (DEFAULT_REGION_WIDTH/2), DEFAULT_REGION_WIDTH, DEFAULT_REGION_HEIGHT), true);
					} else if(media.dcimEntry.mediaType.equals(MimeType.VIDEO)) {
						setCurrentRegion(media.addRegion((int) event.getY() - (DEFAULT_REGION_HEIGHT/2), (int) event.getX() - (DEFAULT_REGION_WIDTH/2), DEFAULT_REGION_WIDTH, DEFAULT_REGION_HEIGHT, ((FullScreenVideoViewFragment) this).getCurrentPosition(), ((FullScreenVideoViewFragment) this).getDuration()), true);
					}

				} catch (JSONException e) {
					Log.e(LOG, e.toString());
					e.printStackTrace();
				}

			}

		}

		return false;
	}

	@Override
	public void onSelected(IRegionDisplay regionDisplay) {
		setCurrentRegion(regionDisplay.parent);
	}

	@Override
	public boolean saveForm() {

		return true;
	}

}