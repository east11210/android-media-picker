package vn.tungdx.mediapicker.activities;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.view.ContextThemeWrapper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.edmodo.cropper.CropImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;

import vn.tungdx.mediapicker.CropListener;
import vn.tungdx.mediapicker.MediaItem;
import vn.tungdx.mediapicker.MediaOptions;
import vn.tungdx.mediapicker.R;
import vn.tungdx.mediapicker.utils.MediaUtils;
import vn.tungdx.mediapicker.utils.Utils;
/**
 * @author TUNGDX
 */

/**
 * For crop photo. Only crop one item at same time.
 */
public class PhotoCropFragment extends BaseFragment implements OnClickListener {
    private static final String EXTRA_MEDIA_SELECTED = "extra_media_selected";
    private static final String EXTRA_MEDIA_OPTIONS = "extra_media_options";
    private static final int [] ASPECT_X = new int[] {
            1, 3, 3, 4, 4, 4, 5, 16
    };
    private static final int [] ASPECT_Y = new int[] {
            1, 2, 5, 3, 5, 6, 7, 9
    };

    private CropListener mCropListener;
    private MediaOptions mMediaOptions;
    private MediaItem mMediaItemSelected;
    private CropImageView mCropImageView;
    private ProgressDialog mDialog;
    private SaveFileCroppedTask mSaveFileCroppedTask;
    private int imageWidth;
    private int imageHeight;

    public static PhotoCropFragment newInstance(MediaItem item,
                                                 MediaOptions options) {
        Bundle bundle = new Bundle();
        bundle.putParcelable(EXTRA_MEDIA_SELECTED, item);
        bundle.putParcelable(EXTRA_MEDIA_OPTIONS, options);
        PhotoCropFragment cropFragment = new PhotoCropFragment();
        cropFragment.setArguments(bundle);
        return cropFragment;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mCropListener = (CropListener) activity;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mMediaItemSelected = savedInstanceState
                    .getParcelable(EXTRA_MEDIA_SELECTED);
            mMediaOptions = savedInstanceState
                    .getParcelable(EXTRA_MEDIA_OPTIONS);
        } else {
            Bundle bundle = getArguments();
            mMediaItemSelected = bundle.getParcelable(EXTRA_MEDIA_SELECTED);
            mMediaOptions = bundle.getParcelable(EXTRA_MEDIA_OPTIONS);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(EXTRA_MEDIA_OPTIONS, mMediaOptions);
        outState.putParcelable(EXTRA_MEDIA_SELECTED, mMediaItemSelected);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_mediapicker_crop,
                container, false);
        init(root);
        return root;
    }

    private void init(View view) {
        mCropImageView = (CropImageView) view.findViewById(R.id.crop);
        view.findViewById(R.id.rotate_left).setOnClickListener(this);
        view.findViewById(R.id.rotate_right).setOnClickListener(this);
        view.findViewById(R.id.cancel).setOnClickListener(this);
        view.findViewById(R.id.save).setOnClickListener(this);
        view.findViewById(R.id.aspect_ratio).setOnClickListener(this);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mCropImageView.setFixedAspectRatio(mMediaOptions.isFixAspectRatio());
        mCropImageView.setAspectRatio(mMediaOptions.getAspectX(),
                mMediaOptions.getAspectY());
        String filePath = null;
        String scheme = mMediaItemSelected.getUriOrigin().getScheme();
        if (scheme.equals(ContentResolver.SCHEME_CONTENT)) {
            filePath = MediaUtils.getRealImagePathFromURI(getActivity()
                    .getContentResolver(), mMediaItemSelected.getUriOrigin());
        } else if (scheme.equals(ContentResolver.SCHEME_FILE)) {
            filePath = mMediaItemSelected.getUriOrigin().getPath();
        }
        if (TextUtils.isEmpty(filePath)) {
            Log.e("PhotoCrop", "not found file path");
            getFragmentManager().popBackStack();
            return;
        }
        int width = getResources().getDisplayMetrics().widthPixels / 3 * 2;
        Bitmap bitmap = MediaUtils.decodeSampledBitmapFromFile(filePath, width,
                width);
        imageWidth = bitmap.getWidth();
        imageHeight = bitmap.getHeight();
        try {
            ExifInterface exif = new ExifInterface(filePath);
            mCropImageView.setImageBitmap(bitmap, exif);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.rotate_left) {// must catch exception, maybe bitmap in CropImage null
            try {
                mCropImageView.rotateImage(-90);
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else if (i == R.id.rotate_right) {
            try {
                mCropImageView.rotateImage(90);
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else if (i == R.id.cancel) {
            getFragmentManager().popBackStack();

        } else if (i == R.id.save) {
            Bitmap croppedImage = mCropImageView.getCroppedImage();
            if (null != croppedImage) {
                mSaveFileCroppedTask = new SaveFileCroppedTask(getActivity(), croppedImage);
                mSaveFileCroppedTask.execute();
            }

        } else if (i == R.id.aspect_ratio) {
            LinearLayout title = (LinearLayout) LayoutInflater.from(getContext()).inflate(
                    R.layout.title_alert, null, false);
            ((TextView) title.findViewById(android.R.id.text1)).setText(R.string.title_aspect_ratio);
            new AlertDialog.Builder(new ContextThemeWrapper(getContext(), R.style.MediaPickerAlertDialog))
                    .setCustomTitle(title)
                    .setItems(R.array.items_aspect_ratio,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                    if (0 == which) {
                                        mCropImageView.setFixedAspectRatio(false);
                                        mCropImageView.setAspectRatio(imageWidth, imageHeight);
                                    } else {
                                        mCropImageView.setFixedAspectRatio(true);
                                        mCropImageView.setAspectRatio(
                                                ASPECT_X[which - 1], ASPECT_Y[which - 1]
                                        );
                                    }
                                }
                            })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }
    }

    private Uri saveBitmapCropped(Bitmap bitmap) {
        if (bitmap == null)
            return null;
        try {
            File file;
            if (mMediaOptions.getCroppedFile() != null) {
                file = mMediaOptions.getCroppedFile();
            } else {
                file = Utils.createTempFile(mContext);
            }
            boolean success = bitmap.compress(CompressFormat.JPEG, 100,
                    new FileOutputStream(file));
            if (success) {
                return Uri.fromFile(file);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private class SaveFileCroppedTask extends AsyncTask<Void, Void, Uri> {
        private WeakReference<Activity> reference;
        private Bitmap croppedImage;

        public SaveFileCroppedTask(Activity activity, Bitmap croppedImage) {
            reference = new WeakReference<Activity>(activity);
            this.croppedImage = croppedImage;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (reference.get() != null && mDialog == null
                    || !mDialog.isShowing()) {
                mDialog = ProgressDialog.show(reference.get(), null, reference
                        .get().getString(R.string.waiting), false, false);
            }
        }

        @Override
        protected Uri doInBackground(Void... params) {
            Uri uri = null;
            // must try-catch, maybe getCroppedImage() method crash because not
            // set bitmap in mCropImageView
            try {
                uri = saveBitmapCropped(croppedImage);
                if (croppedImage != null) {
                    croppedImage.recycle();
                    croppedImage = null;
                }
            } catch (Exception exception) {
                exception.printStackTrace();
            }
            return uri;
        }

        @Override
        protected void onPostExecute(Uri result) {
            super.onPostExecute(result);
            if (mDialog != null) {
                mDialog.dismiss();
                mDialog = null;
            }
            mMediaItemSelected.setUriCropped(result);
            mCropListener.onSuccess(mMediaItemSelected);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mSaveFileCroppedTask != null) {
            mSaveFileCroppedTask.cancel(true);
            mSaveFileCroppedTask = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mCropImageView = null;
        mDialog = null;
    }
}