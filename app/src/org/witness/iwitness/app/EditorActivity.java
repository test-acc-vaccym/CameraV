package org.witness.iwitness.app;

import info.guardianproject.odkparser.FormWrapper.ODKFormListener;

import java.util.List;

import org.witness.informacam.InformaCam;
import org.witness.informacam.models.forms.IForm;
import org.witness.informacam.models.media.IImage;
import org.witness.informacam.models.media.IMedia;
import org.witness.informacam.models.media.IRegion;
import org.witness.informacam.models.media.IVideo;
import org.witness.informacam.models.utils.IRegionDisplay;
import org.witness.informacam.storage.FormUtility;
import org.witness.informacam.utils.Constants.IRegionDisplayListener;
import org.witness.informacam.utils.Constants.Models;
import org.witness.informacam.utils.Constants.Models.IMedia.MimeType;
import org.witness.iwitness.R;
import org.witness.iwitness.app.screens.FullScreenViewFragment;
import org.witness.iwitness.app.screens.editors.FullScreenImageViewFragment;
import org.witness.iwitness.app.screens.editors.FullScreenVideoViewFragment;
import org.witness.iwitness.app.screens.forms.OverviewFormFragment;
import org.witness.iwitness.app.screens.forms.TagFormFragment;
import org.witness.iwitness.app.screens.popups.SharePopup;
import org.witness.iwitness.utils.Constants;
import org.witness.iwitness.utils.Constants.Codes;
import org.witness.iwitness.utils.Constants.EditorActivityListener;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

public class EditorActivity extends SherlockFragmentActivity implements EditorActivityListener, IRegionDisplayListener
{
	Intent init;

	View rootMain, rootForm;
	Fragment fullscreenView, formView;
	OverviewFormFragment detailsView;
	public FragmentManager fm;

	View toolbarBottom;
	boolean toolbarBottomEnabled;

	ActionBar actionBar;
	ImageButton abNavigationBack, abShareMedia;

	private final static String LOG = Constants.App.Editor.LOG;

	private InformaCam informaCam;
	public IMedia media;
	private String mediaId;
	public List<IForm> availableForms;

	public enum ActivityActionMode
	{
		Normal, Edit, AddTags, EditForm
	}

	private ActivityActionMode mActionMode = ActivityActionMode.Normal;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		informaCam = (InformaCam) getApplication();

		initData();

		if (media.bitmapPreview != null)
		{

			setContentView(R.layout.activity_editor);

			rootMain = findViewById(R.id.root_main);
			rootForm = findViewById(R.id.root_form);
			toolbarBottom = findViewById(R.id.toolbar_bottom);
			toolbarBottom.setVisibility(View.GONE);

			actionBar = getSupportActionBar();
			actionBar.setDisplayShowCustomEnabled(true);
			actionBar.setDisplayShowHomeEnabled(false);
			actionBar.setDisplayShowTitleEnabled(false);

			fm = getSupportFragmentManager();

			initToolbar();
		}
		else
		{
			Toast.makeText(this, "Could not open image", Toast.LENGTH_LONG).show();
			finish();
		}
	}

	@Override
	public void onResume()
	{
		super.onResume();
		initLayout();
	}

	@Override
	public void onPause()
	{
		super.onPause();
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
	}

	private void initData()
	{
		if (!getIntent().hasExtra(Codes.Extras.EDIT_MEDIA))
		{
			setResult(Activity.RESULT_CANCELED);
			finish();
		}

		mediaId = getIntent().getStringExtra(Codes.Extras.EDIT_MEDIA);
		media = informaCam.mediaManifest.getById(mediaId);
		if (media == null)
		{
			setResult(Activity.RESULT_CANCELED);
			finish();
		}

		if (media.dcimEntry.mediaType.equals(MimeType.IMAGE))
		{
			IImage image = new IImage(media);
			media = image;
		}
		else if (media.dcimEntry.mediaType.equals(MimeType.VIDEO))
		{
			IVideo video = new IVideo(media);
			media = video;
		}
		informaCam.informaService.associateMedia(media);

		availableForms = FormUtility.getAvailableForms();
		// Log.d(LOG, "INITING MEDIA FOR EDIT:\n" + media.asJson().toString());
	}

	private void initLayout()
	{
		Bundle fullscreenViewArgs = new Bundle();
		Bundle detailsViewArgs = new Bundle();

		fullscreenViewArgs.putInt(Codes.Extras.SET_ORIENTATION, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		fullscreenViewArgs.putString("mediaId", mediaId);
		detailsViewArgs.putInt(Codes.Extras.SET_ORIENTATION, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		detailsViewArgs.putString("mediaId", mediaId);

		if (media.dcimEntry.mediaType.equals(Models.IMedia.MimeType.IMAGE))
		{
			fullscreenView = Fragment.instantiate(this, FullScreenImageViewFragment.class.getName(), fullscreenViewArgs);
		}
		else if (media.dcimEntry.mediaType.equals(Models.IMedia.MimeType.VIDEO))
		{
			fullscreenView = Fragment.instantiate(this, FullScreenVideoViewFragment.class.getName(), fullscreenViewArgs);
		}

		detailsView = (OverviewFormFragment) Fragment.instantiate(this, OverviewFormFragment.class.getName(), detailsViewArgs);

		formView = Fragment.instantiate(this, TagFormFragment.class.getName());

		actionBar.setDisplayShowHomeEnabled(true);
		actionBar.setDisplayHomeAsUpEnabled(false);
		actionBar.setHomeButtonEnabled(true);
		actionBar.setLogo(this.getResources().getDrawable(R.drawable.ic_action_up));
		actionBar.setDisplayUseLogoEnabled(true);

		FragmentTransaction ft = fm.beginTransaction();
		ft.replace(R.id.media_holder, fullscreenView);
		ft.replace(R.id.details_form_holder, detailsView);
		ft.replace(R.id.root_form, formView);
		ft.addToBackStack(null);
		ft.commit();

		updateUIBasedOnActionMode();
	}

	private void initToolbar()
	{
		toolbarBottom.findViewById(R.id.btnWriteText).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				if (toolbarBottomEnabled)
					detailsView.editNotes();
			}
		});

		toolbarBottom.findViewById(R.id.btnAddTags).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				if (toolbarBottomEnabled && mActionMode == ActivityActionMode.Edit)
					setActionMode(ActivityActionMode.AddTags);
			}
		});
	}

	private void saveStateAndFinish()
	{
		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				if (((ODKFormListener) fullscreenView).saveForm() && ((ODKFormListener) detailsView).saveForm())
				{
					media.save();
				}
			}
		}).start();

		setResult(Activity.RESULT_OK);
		finish();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		super.onCreateOptionsMenu(menu);
		if (mActionMode == ActivityActionMode.Edit)
			getSupportMenuInflater().inflate(R.menu.activity_edit_edit, menu);
		else
			getSupportMenuInflater().inflate(R.menu.activity_edit_normal, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
		case android.R.id.home:
		{
			if (mActionMode == ActivityActionMode.Edit)
				setActionMode(ActivityActionMode.Normal);
			else
				saveStateAndFinish();
			return true;
		}
		case R.id.menu_share:
		{
			new SharePopup(this, media);
			return true;
		}
		case R.id.menu_edit:
		{
			setActionMode(ActivityActionMode.Edit);
			return true;
		}
		case R.id.menu_save:
		{
			setActionMode(ActivityActionMode.Normal);
			return true;
		}
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onBackPressed()
	{
		saveStateAndFinish();
	}

	@Override
	public void onMediaScanned(Uri uri)
	{
		((EditorActivityListener) fullscreenView).onMediaScanned(uri);
	}

	@Override
	public void onSelected(IRegionDisplay regionDisplay)
	{
		((IRegionDisplayListener) fullscreenView).onSelected(regionDisplay);
	}

	@Override
	public void waiter(boolean show)
	{

	}

	@Override
	public IMedia media()
	{
		return media;
	}

	@Override
	public int[] getSpecs()
	{
		return ((IRegionDisplayListener) fullscreenView).getSpecs();
	}

	private final ActionMode.Callback mActionModeEditTags = new ActionMode.Callback()
	{

		// Called when the action mode is created; startActionMode() was called
		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu)
		{
			mode.setTitle(R.string.editor_tags_add);

			// Inflate a menu resource providing context menu items
			// MenuInflater inflater = mode.getMenuInflater();
			// inflater.inflate(R.menu.context_menu, menu);
			menu.add(Menu.NONE, R.string.menu_done, 0, R.string.menu_done);
			return true;
		}

		// Called each time the action mode is shown. Always called after
		// onCreateActionMode, but
		// may be called multiple times if the mode is invalidated.
		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu)
		{
			return false; // Return false if nothing is done
		}

		// Called when the user selects a contextual menu item
		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item)
		{
			switch (item.getItemId())
			{
			case R.string.menu_done:
				mode.finish(); // Action picked, so close the CAB
				return true;
			default:
				return false;
			}
		}

		// Called when the user exits the action mode
		@Override
		public void onDestroyActionMode(ActionMode mode)
		{
			setActionMode(ActivityActionMode.Edit);
		}
	};

	protected class EditFormActionMode implements ActionMode.Callback
	{
		private IRegion mRegion = null;

		public void setEditedRegion(IRegion region)
		{
			mRegion = region;
		}

		// Called when the action mode is created; startActionMode() was called
		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu)
		{
			mode.setTitle(R.string.editor_form_edit);
			menu.add(Menu.NONE, R.string.cancel, 0, R.string.cancel);
			menu.add(Menu.NONE, R.string.save, 1, R.string.save);
			return true;
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu)
		{
			return false; // Return false if nothing is done
		}

		// Called when the user selects a contextual menu item
		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item)
		{
			switch (item.getItemId())
			{
			case R.string.cancel:
				mode.finish();
				return true;

			case R.string.save:
				if (mRegion != null)
					((TagFormFragment) formView).saveTagFormData(mRegion);
				mode.finish(); // Action picked, so close the CAB
				return true;
			default:
				return false;
			}
		}

		// Called when the user exits the action mode
		@Override
		public void onDestroyActionMode(ActionMode mode)
		{
			setEditedRegion(null);
			setActionMode(ActivityActionMode.Edit);
		}
	};

	protected final EditFormActionMode mActionModeEditForm = new EditFormActionMode();

	private boolean setActionMode(ActivityActionMode mode)
	{
		// Already in action mode
		if (mActionMode == mode)
			return false;
		else if (mActionMode == ActivityActionMode.Normal && mode != ActivityActionMode.Edit)
			return false; // Invalid state
		else if (mActionMode == ActivityActionMode.AddTags && mode == ActivityActionMode.EditForm)
			return false;
		else if (mActionMode == ActivityActionMode.EditForm && mode == ActivityActionMode.AddTags)
			return false;

		mActionMode = mode;
		if (mActionMode == ActivityActionMode.AddTags)
		{
			((FullScreenViewFragment) fullscreenView).setCurrentMode(FullScreenViewFragment.Mode.AddTags);
			startActionMode(this.mActionModeEditTags);
		}
		else if (mActionMode == ActivityActionMode.EditForm)
		{
			startActionMode(this.mActionModeEditForm);
		}
		else if (mActionMode == ActivityActionMode.Edit)
		{
			((FullScreenViewFragment) fullscreenView).setCurrentMode(FullScreenViewFragment.Mode.Edit);
			supportInvalidateOptionsMenu();
		}
		else if (mActionMode == ActivityActionMode.Normal)
		{
			((FullScreenViewFragment) fullscreenView).setCurrentMode(FullScreenViewFragment.Mode.Normal);
			supportInvalidateOptionsMenu();
		}

		updateUIBasedOnActionMode();
		return true;
	}

	private void updateUIBasedOnActionMode()
	{
		switch (mActionMode)
		{
		case EditForm:
			rootMain.setVisibility(View.GONE);
			rootForm.setVisibility(View.VISIBLE);
			showToolbar(true);
			break;
		case AddTags:
			rootForm.setVisibility(View.GONE);
			rootMain.setVisibility(View.VISIBLE);
			showToolbar(true);
			break;
		case Edit:
			rootForm.setVisibility(View.GONE);
			rootMain.setVisibility(View.VISIBLE);
			showToolbar(true);
			break;
		default:
			rootForm.setVisibility(View.GONE);
			rootMain.setVisibility(View.VISIBLE);
			showToolbar(false);
			break;
		}
	}

	private void showToolbar(boolean show)
	{
		if (show)
		{
			toolbarBottom.startAnimation(AnimationUtils.loadAnimation(this, R.anim.toolbar_slide_in));
			toolbarBottom.setVisibility(View.VISIBLE);
			toolbarBottomEnabled = true;
		}
		else
		{
			toolbarBottomEnabled = false;
			toolbarBottom.startAnimation(AnimationUtils.loadAnimation(this, R.anim.toolbar_slide_out));
		}
	}

	public void showTagForm(IRegion region)
	{
		if (setActionMode(ActivityActionMode.EditForm))
		{
			mActionModeEditForm.setEditedRegion(region);
			((TagFormFragment) formView).initTag(region);
		}
	}
}