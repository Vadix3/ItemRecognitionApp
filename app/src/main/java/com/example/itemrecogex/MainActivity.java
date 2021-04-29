package com.example.itemrecogex;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.example.itemrecogex.ml.MobilenetV110224Quant;
import com.example.itemrecogex.ml.SsdMobilenetV11Metadata1;
import com.example.itemrecogex.models.ObjectClassification;
import com.example.itemrecogex.models.ObjectDetection;
import com.example.itemrecogex.models.ObjectLocation;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;

public class MainActivity extends AppCompatActivity {

    //Statics
    private static final String TAG = "myTag";
    public static final int STORAGE_PICTURE_REQUEST = 200;
    public static final int STORAGE_PERMISSION_SETTINGS_REQUEST = 300;
    public static final int IMAGE_SIZE_FOR_CLASSIFICATION = 300;
    public static final double STROKE_FACTOR = 0.00001126;
    public static final double FONT_FACTOR = 0.0000179;
    public static final String FILE_NAME1 = "labels1.txt";
    public static final String FILE_NAME2 = "labels2.txt";

    //Views
    private ShapeableImageView mainImage;
    private TextView resultText;
    private MaterialButton idButton;
    private ListView resultList;

    //Variables
    private ArrayList<String> objectDetectionLabels;// List of object detection labels
    private ArrayList<String> objectClassificationLabels;// List of labels
    private ArrayList<ObjectDetection> objectDetections;// List of possible objects in image
    private ArrayList<ObjectClassification> objectClassifications;// List of possible objects that the image is
    private Bitmap myImage;
    private int imageX; // The original image x size
    private int imageY; // The original image y size

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews(); // initialize the views
        objectClassificationLabels = populateLabelsList(FILE_NAME1); // populate the labels list with data from "Labels1" file
        objectDetectionLabels = populateLabelsList(FILE_NAME2); // populate the labels list with data from "Labels2" file
    }

    /**
     * A function to get string data from a file and put it in an arraylist of strings
     *
     * @param fileName = the filename with the data in assets folder
     * @return an array list with the data from the file
     */
    private ArrayList<String> populateLabelsList(String fileName) {
        ArrayList<String> labels = new ArrayList<>();
        Log.d(TAG, "populateLabelsList: ");
        try { // Read lables from file and add them to
            BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open(fileName)));
            String mLine = null;
            while ((mLine = reader.readLine()) != null) { // read from file until EOF
                labels.add(mLine);
            }
        } catch (IOException e) {
            Log.e(TAG, "onCreate: Exception: " + e.getMessage());
        }
        return labels;
    }

    /**
     * A method to initialize the views of the layout
     */
    private void initViews() {
        Log.d(TAG, "initViews: initializing views");
        mainImage = findViewById(R.id.main_IMG_mainImage);
        resultText = findViewById(R.id.main_EDT_resultText);
        resultList = findViewById(R.id.main_LST_resultList);
        idButton = findViewById(R.id.main_BTN_idButton);
        idButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                idButton.setEnabled(false);
                identifyPicture();
                findObjectsInPicture();
            }
        });
        mainImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "onClick: Click");
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "onClick: User already given permission, moving straight to storage");
                    openStorage();
                } else {
                    checkForStoragePermissions();
                }
            }
        });
        resultList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                // Open google search page
                String searchUrl = "https://www.google.com/search?q=" + objectClassifications.get(i).getName();
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl));
                MainActivity.this.startActivity(browserIntent);
            }
        });
    }

    /**
     * A method to find objects in the picture with the object detection model
     */
    private void findObjectsInPicture() {
        Log.d(TAG, "identifyPicture: ");
        try {

            Bitmap resize = Bitmap.createScaledBitmap(myImage
                    , IMAGE_SIZE_FOR_CLASSIFICATION
                    , IMAGE_SIZE_FOR_CLASSIFICATION
                    , true);
            SsdMobilenetV11Metadata1 model = SsdMobilenetV11Metadata1.newInstance(this);

            // Creates inputs for reference.
            TensorImage image = TensorImage.fromBitmap(resize);

            // Runs model inference and gets result.
            SsdMobilenetV11Metadata1.Outputs outputs = model.process(image);

            /**Multidimensional array of [N][4] floating point values between 0 and 1
             * , the inner arrays representing bounding boxes in the form [top, left, bottom, right]*/
            float[] locationsArray = outputs.getLocationsAsTensorBuffer().getFloatArray();
            ArrayList<ObjectLocation> objectLocations = getLocationsMatrix(locationsArray);

            /** Array of N integers (output as floating point values)
             * each indicating the index of a class label from the labels file*/
            int[] classesArray = outputs.getClassesAsTensorBuffer().getIntArray();

            /**    Array of N floating point values between 0 and 1 representing probability that a
             * class was detected*/
            float[] scoresArray = outputs.getScoresAsTensorBuffer().getFloatArray();

            /** Integer value of N*/
            int numberOfDetections = outputs.getNumberOfDetectionsAsTensorBuffer().getIntArray()[0];

            objectDetections = getObjectsFromData(objectLocations, classesArray, scoresArray, numberOfDetections);
            paintObjectsOnBitmap();

            // Releases model resources if no longer used.
            model.close();
        } catch (IOException e) {
            // TODO Handle the exception
        }
    }

    /**
     * A method to paint the detected objects on the bitmap
     */
    private void paintObjectsOnBitmap() {
        Log.d(TAG, "paintObjectsOnBitmap: ");

        /** Calculate the scale of the font and the rectangle stroke according to image size*/
        float strokeSize = 0;
        float fontSize = 0;
        if (imageX * imageY > 10000000) {
            strokeSize = (float) (((imageX * imageY) / 3) * STROKE_FACTOR);
            fontSize = (float) (((imageX * imageY) / 3) * FONT_FACTOR);
        } else {
            strokeSize = (float) (((imageX * imageY) * 2) * STROKE_FACTOR);
            fontSize = (float) (((imageX * imageY) * 2) * FONT_FACTOR);
        }

        Bitmap temp = myImage.copy(myImage.getConfig(), true); // copy original immutable bitmap

        // Rectangle stroke
        Paint myRectangle = new Paint();
        myRectangle.setColor(Color.RED);
        myRectangle.setStyle(Paint.Style.STROKE);
        myRectangle.setStrokeWidth(strokeSize);

        // Object name
        Paint myTitle = new Paint();
        float textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP
                , fontSize
                , getResources().getDisplayMetrics());
        myTitle.setTextSize(textSize);
        myTitle.setColor(Color.RED);

        Canvas c = new Canvas(temp);

        float left, top, right, bottom;

        for (ObjectDetection d : objectDetections) {
            top = d.getLocation().getTop() * imageY;
            left = d.getLocation().getLeft() * imageX;
            bottom = d.getLocation().getBottom() * imageY;
            right = d.getLocation().getRight() * imageX;
            c.drawText(d.getName() + ":" + String.format("%.02f", d.getScore() * 100) + "%"
                    , left
                    , (top + textSize)
                    , myTitle);
            c.drawRect(left, top, right, bottom, myRectangle);
        }
        mainImage.setImageBitmap(temp);
    }

    /**
     * A method to convert the locations array to an array of object locations
     *
     * @param locationsArray = an array of floats
     * @return an array list of object locations
     */
    private ArrayList<ObjectLocation> getLocationsMatrix(float[] locationsArray) {
        Log.d(TAG, "getLocationsMatrix: ");
        ArrayList<ObjectLocation> locations = new ArrayList<>();
        for (int i = 0; i < locationsArray.length; i += 4) {
            ObjectLocation temp = new ObjectLocation(locationsArray[i], locationsArray[i + 1]
                    , locationsArray[i + 2], locationsArray[i + 3]);
            locations.add(temp);
        }
        return locations;
    }

    /**
     * A method to extract detected objects from data
     *
     * @param objectLocations    = An array of object locations
     * @param classesArray       = An array of classes for all detected objects
     * @param scoresArray        = An array of scores for the detected objects
     * @param numberOfDetections = An int that represents the number of detections
     * @return An array of detected objects
     */
    private ArrayList<ObjectDetection> getObjectsFromData(ArrayList<ObjectLocation> objectLocations
            , int[] classesArray, float[] scoresArray, int numberOfDetections) {
        Log.d(TAG, "getObjectsFromData: ");
        ArrayList<ObjectDetection> list = new ArrayList<>();
        for (int i = 0; i < numberOfDetections; i++) {
            if (scoresArray[i] >= 0.5) {
                ObjectDetection temp = new ObjectDetection(objectDetectionLabels.get(classesArray[i])
                        , scoresArray[i], objectLocations.get(i));
                list.add(temp);
            }
        }
        return list;
    }

    /**
     * A method to identify the classification of the given image
     */
    private void identifyPicture() {
        Log.d(TAG, "identifyPicture: ");
        Bitmap resize = Bitmap.createScaledBitmap(((BitmapDrawable) mainImage
                        .getDrawable())
                        .getBitmap()
                , 224
                , 224
                , true);

        try {

            /** Predictions list */
            MobilenetV110224Quant model = MobilenetV110224Quant.newInstance(this);
            // Creates inputs for reference.
            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 224, 224, 3}, DataType.UINT8);
            inputFeature0.loadBuffer(TensorImage.fromBitmap(resize).getBuffer());

            // Runs model inference and gets result.
            MobilenetV110224Quant.Outputs outputs = model.process(inputFeature0);
            TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer(); // scores
            objectClassifications = getMaxHundredPredictions(outputFeature0.getFloatArray());
            displayResults(); // display the results in the list
            // Releases model resources if no longer used.

            model.close();
        } catch (IOException e) {
            Log.e(TAG, "identifyPicture: Exception:" + e.getMessage());
        }
    }

    /**
     * A method to display the results in the list
     */
    private void displayResults() {
        Log.d(TAG, "displayResults: ");
        idButton.setText("Select image to try again");
        resultList.setVisibility(View.VISIBLE);
        resultText.setVisibility(View.INVISIBLE);
        ArrayList<String> results = new ArrayList<>();
        for (int i = 0; i < objectClassifications.size(); i++) {
            results.add((i + 1) + ")  " + objectClassifications.get(i).toString());
        }
        ArrayAdapter<String> itemsAdapter =
                new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, results);
        resultList.setAdapter(itemsAdapter);
    }

    /**
     * A method to get the max 100 objectClassifications for the given image, omit the 0's
     *
     * @param floatArray = an array of chances
     * @return an array of objectClassifications
     */
    private ArrayList<ObjectClassification> getMaxHundredPredictions(float[] floatArray) {
        Log.d(TAG, "getMaxHundredPredictions: ");
        ArrayList<ObjectClassification> list = new ArrayList<>();

        for (int i = 0; i < floatArray.length; i++) { // add valid items to the array
            if (floatArray[i] != 0) { // if there is a chance
                float chance = (floatArray[i] / 255) * 100;
                list.add(new ObjectClassification(chance, objectClassificationLabels.get(i))); // add the prediction to the list
            }
        }
        Collections.sort(list); // sort the list (highest is index 0)
        if (list.size() > 100) {
            list.subList(100, list.size()).clear(); // leave only the first 100 elements
        }
        return list;
    }

    /**
     * A method to open the storage to fetch a photo
     */
    private void openStorage() {
        Log.d(TAG, "openStorage: opening storage");
        Intent photoPicker = new Intent(Intent.ACTION_PICK);
        photoPicker.setType("image/*");
        startActivityForResult(photoPicker, MainActivity.STORAGE_PICTURE_REQUEST);
    }

    /**
     * A method to check for storage permissions
     */
    private void checkForStoragePermissions() {
        Log.d(TAG, "checkingForStoragePermissions: checking for storage permissions");

        Dexter.withContext(MainActivity.this)
                .withPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                .withListener(new PermissionListener() {
                    @Override
                    public void onPermissionGranted(PermissionGrantedResponse response) {
                        Log.d(TAG, "onPermissionGranted: User given permission");
                        openStorage();
                    }

                    @Override
                    public void onPermissionDenied(PermissionDeniedResponse response) {
                        if (response.isPermanentlyDenied()) {
                            Log.d(TAG, "onPermissionDenied: User denied permission permanently!");
                            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                            builder.setTitle("Permission Denied!")
                                    .setMessage("Please enable storage permissions in settings!")
                                    .setNegativeButton("Cancel", null)
                                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            Log.d(TAG, "onClick: Opening settings activity");
                                            Intent intent = new Intent();
                                            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                            intent.setData(Uri.fromParts("package", MainActivity.this.getPackageName(), null));
                                            startActivityForResult(intent, MainActivity.STORAGE_PERMISSION_SETTINGS_REQUEST);
                                        }
                                    }).show();
                        } else {
                            Log.d(TAG, "onPermissionDenied: User denied permission!");
                        }
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(PermissionRequest permission, PermissionToken token) {
                        token.continuePermissionRequest();
                    }
                }).check();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case MainActivity.STORAGE_PERMISSION_SETTINGS_REQUEST:
                Log.d(TAG, "onActivityResult: I came from app settings: storage");
                break;
            case MainActivity.STORAGE_PICTURE_REQUEST:
                Log.d(TAG, "onActivityResult: I came from storage with picture");
                if (data != null) {
                    Uri selectedImage = data.getData();
                    try {
                        InputStream imageStream = getContentResolver().openInputStream(selectedImage);
                        myImage = BitmapFactory.decodeStream(imageStream);
                        imageX = myImage.getWidth();
                        imageY = myImage.getHeight();
                        mainImage.setStrokeWidth(30);
                        mainImage.setImageBitmap(myImage);
                        mainImage.setStrokeColor(ColorStateList.valueOf(getColor(R.color.design_default_color_primary)));
                        idButton.setEnabled(true);
                        idButton.setText("Identify");
                    } catch (IOException e) {
                        Log.i("TAG", "Some exception " + e);
                    }
                }
                break;
        }
    }
}