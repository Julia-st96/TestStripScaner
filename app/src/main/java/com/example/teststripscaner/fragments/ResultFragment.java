package com.example.teststripscaner.fragments;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.teststripscaner.R;
import com.example.teststripscaner.models.ImageViewModel;
import com.example.teststripscaner.models.TestStrip;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static android.app.Activity.RESULT_OK;


public class ResultFragment extends Fragment implements View.OnClickListener {

    private ImageViewModel mImageViewModel;
    private String mUrl;

    private TextView mResultTextView;
    private ImageView mResultImageView;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.setRetainInstance(true);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_result, container, false);

        mResultTextView = view.findViewById(R.id.resultTextView);
        mResultImageView = view.findViewById(R.id.resultImageView);

        view.findViewById(R.id.takePhotoButton).setOnClickListener(this);
        view.findViewById(R.id.selectPhotoButton).setOnClickListener(this);

        mUrl = getResources().getString(R.string.url);

        mImageViewModel = new ViewModelProvider(requireActivity()).get(ImageViewModel.class);

        if (mImageViewModel.bitmap != null) {
            mResultImageView.setImageBitmap(mImageViewModel.bitmap);
            if (mImageViewModel.isNotCalculated) {
                connectServer();
            } else {
                mResultTextView.setText(String.valueOf(mImageViewModel.result));
            }
        }

        return view;
    }

    @Override
    public void onClick(final View v) {
        switch (v.getId()) {
            case R.id.takePhotoButton:
                onTakePhotoClick();
                break;
            case R.id.selectPhotoButton:
                onSelectPhotoClick();
                break;
            default:
        }
    }

    private void onTakePhotoClick() {
        replaceFragment(new PreviewFragment());
    }

    private void onSelectPhotoClick() {
        Intent selectPhotoIntent = new Intent(Intent.ACTION_PICK);
        selectPhotoIntent.setType("image/*");
        startActivityForResult(selectPhotoIntent, 0);
    }

    @Override
    public void onActivityResult(int reqCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK && data != null) {
            try {
                final Uri imageUri = data.getData();
                final InputStream imageStream = getActivity().getContentResolver().openInputStream(imageUri);
                Bitmap bitmap = BitmapFactory.decodeStream(imageStream);

                mImageViewModel.bitmap = bitmap;
                mImageViewModel.isNotCalculated = true;
                mResultImageView.setImageBitmap(bitmap);
                connectServer();
            } catch (Exception e) {
                Toast.makeText(getActivity(), R.string.smth_is_wrong, Toast.LENGTH_LONG).show();
            }

        } else {
            Toast.makeText(getActivity(), R.string.image_not_selected, Toast.LENGTH_LONG).show();
        }
    }

    private void connectServer() {
        mResultTextView.setText(R.string.please_wait);

        Bitmap bitmap = mImageViewModel.bitmap;

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        byte[] byteArray = stream.toByteArray();

        RequestBody postBodyImage = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", "androidFlask.jpg", RequestBody.create(MediaType.parse("image/*jpg"), byteArray))
                .build();

        postRequestAsync(mUrl, postBodyImage);
    }

    private void postRequestAsync(String postUrl, RequestBody postBody) {
        OkHttpClient okHttpClient = new OkHttpClient();

        Request request = new Request.Builder()
                .url(postUrl)
                .post(postBody)
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(final Call call, final IOException e) {
                call.cancel();

                if (isAdded()) {
                    getActivity().runOnUiThread(() -> {
                        mResultTextView.setText("Failed to Connect to Server");
                    });
                }

            }

            @Override
            public void onResponse(final Call call, final Response response) throws IOException {
                if (response.isSuccessful()) {
                    if (isAdded()) {
                        final TestStrip testStrip = parseTestStripFromJson(response.body().string());

                        if (testStrip != null) {
                            mImageViewModel.isNotCalculated = false;
                            mImageViewModel.result = testStrip.result;
                            getActivity().runOnUiThread(() -> {
                                mResultTextView.setText(String.valueOf(testStrip.result));
                            });
                        }
                    }
                }
            }
        });
    }


    private TestStrip parseTestStripFromJson(@NonNull final String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            TestStrip testStrip = new TestStrip();
            testStrip.result = jsonObject.getDouble("result");
            return testStrip;
        } catch (JSONException e) {
            return null;
        }
    }

    private void replaceFragment(Fragment fragment) {
        getActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

}
